package com.example.unknownblocker

import android.app.Notification
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Best-effort dismissal of voicemail-looking notifications after blocked calls.
 *
 * Samsung/One UI often uses packages/text that don't say "voicemail" literally.
 * When suppress is ON and we recently blocked a call, we aggressively target
 * phone/dialer/telecom packages and message-like alerts, with cancel retries.
 */
class VoicemailNotificationListener : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "listener connected")
        NotificationProbe.record(this, packageName, "", "listener connected", "INFO")
        try {
            activeNotifications?.forEach { evaluate(it, fromPosted = false) }
        } catch (e: SecurityException) {
            Log.w(TAG, "activeNotifications failed", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        evaluate(sbn, fromPosted = true)
        // Samsung sometimes mutates/republishes VM alerts; re-check shortly after.
        if (BlockerSettings.isSuppressVmNotificationsEnabled(this) &&
            RecentScreenedCalls.hasRecentBlocked(this)
        ) {
            handler.postDelayed({
                try {
                    evaluate(sbn, fromPosted = false)
                } catch (_: Exception) {
                }
            }, 750)
            handler.postDelayed({
                try {
                    evaluate(sbn, fromPosted = false)
                } catch (_: Exception) {
                }
            }, 2500)
        }
    }

    private fun evaluate(sbn: StatusBarNotification, fromPosted: Boolean) {
        try {
            if (sbn.packageName == packageName) return

            val text = notificationText(sbn)
            val channel = notificationChannelId(sbn).orEmpty()
            val pkg = sbn.packageName.orEmpty()

            // Always log when suppress feature is enabled so we can debug OEMs.
            if (BlockerSettings.isSuppressVmNotificationsEnabled(this)) {
                val decision = decide(sbn, text, channel, pkg)
                NotificationProbe.record(
                    this,
                    pkg,
                    channel,
                    text,
                    if (decision) "DISMISS" else "KEEP"
                )
                if (decision) {
                    dismissHard(sbn)
                    Log.i(TAG, "dismissed $pkg | $text")
                } else if (fromPosted) {
                    Log.d(TAG, "keep $pkg | $text")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "evaluate failed", e)
        }
    }

    private fun decide(
        sbn: StatusBarNotification,
        text: String,
        channel: String,
        pkg: String
    ): Boolean {
        if (!BlockerSettings.isBlockingEnabled(this)) return false
        if (!BlockerSettings.isSuppressVmNotificationsEnabled(this)) return false
        if (!looksLikeVoicemailOrPhoneMessage(sbn, text, channel, pkg)) return false

        val now = System.currentTimeMillis()

        // Keep if clearly an allowed/contact number
        if (RecentScreenedCalls.textMentionsRecent(
                this, text, RecentScreenedCalls.Kind.ALLOWED, now
            )
        ) {
            return false
        }
        if (RecentScreenedCalls.textMentionsAllowlistedNumber(this, text)) {
            return false
        }

        // Dismiss if mentions blocked number
        if (RecentScreenedCalls.textMentionsRecent(
                this, text, RecentScreenedCalls.Kind.BLOCKED, now
            )
        ) {
            return true
        }

        val lastAllowed = RecentScreenedCalls.mostRecentAllowedAt(this)
        val lastBlocked = RecentScreenedCalls.mostRecentBlockedAt(this)
        val allowedInWindow =
            lastAllowed != null && now - lastAllowed in 0..RecentScreenedCalls.WINDOW_MS
        val blockedInWindow =
            lastBlocked != null && now - lastBlocked in 0..RecentScreenedCalls.WINDOW_MS

        if (!blockedInWindow) return false

        // Prefer keep only when an allowed call is the newest screened event
        if (allowedInWindow && lastAllowed >= lastBlocked) {
            return false
        }

        return true
    }

    private fun dismissHard(sbn: StatusBarNotification) {
        try {
            cancelNotification(sbn.key)
        } catch (e: Exception) {
            Log.w(TAG, "cancelNotification key failed", e)
        }
        try {
            // Older overload — some OEMs behave better with it
            @Suppress("DEPRECATION")
            cancelNotification(sbn.packageName, sbn.tag, sbn.id)
        } catch (_: Exception) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Snooze 2 minutes in case cancel is ignored / reposted immediately
                snoozeNotification(sbn.key, 2 * 60 * 1000L)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Broader than "must say voicemail" — Samsung often uses dialer package +
     * "Voice message" / "1 new message" / channel ids without the word voicemail.
     */
    private fun looksLikeVoicemailOrPhoneMessage(
        sbn: StatusBarNotification,
        textRaw: String,
        channelRaw: String,
        pkgRaw: String
    ): Boolean {
        val pkg = pkgRaw.lowercase()
        val text = textRaw.lowercase()
        val channel = channelRaw.lowercase()
        val category = sbn.notification?.category

        // Ongoing call UI — never touch
        if (sbn.isOngoing && (
                text.contains("calling") ||
                    text.contains("mobile network") ||
                    category == Notification.CATEGORY_CALL
                )
        ) {
            return false
        }

        val textHit = VM_TEXT_HINTS.any { text.contains(it) } ||
            VM_TEXT_HINTS.any { channel.contains(it) }
        if (textHit) return true

        val pkgPhoneLike = PHONE_PKG_HINTS.any { pkg.contains(it) }
        if (!pkgPhoneLike) {
            // Non-phone packages: only if strong VM wording (already handled) 
            return false
        }

        // Phone/dialer package + message-ish signal
        val softText =
            text.contains("message") ||
                text.contains("mail") ||
                text.contains("voice") ||
                text.contains("vm") ||
                text.contains("msg") ||
                channel.contains("message") ||
                channel.contains("mail") ||
                channel.contains("voice") ||
                channel.contains("vm") ||
                channel.contains("vvm")

        if (softText) return true

        // Samsung often posts bare "Voicemail" icon updates with empty/minimal text
        // from dialer after a missed/rejected call. If we recently blocked, treat
        // CATEGORY_MESSAGE / CATEGORY_EVENT from phone packages as VM candidates.
        if (category == Notification.CATEGORY_MESSAGE ||
            category == Notification.CATEGORY_EVENT ||
            category == Notification.CATEGORY_SOCIAL
        ) {
            return true
        }

        // Last-resort for phone packages after a recent block: short generic
        // notifications with no clear call-in-progress wording.
        if (RecentScreenedCalls.hasRecentBlocked(this) &&
            !text.contains("missed call") &&
            !text.contains("calling") &&
            !text.contains("ringing") &&
            text.length <= 80
        ) {
            // Only if channel or extras hint at voicemail-ish system UI
            if (channel.isNotBlank() || text.isNotBlank()) {
                return channel.contains("phone") ||
                    channel.contains("call") ||
                    channel.contains("status") ||
                    text.contains("new") ||
                    text.isBlank()
            }
        }

        return false
    }

    private fun notificationText(sbn: StatusBarNotification): String {
        val n = sbn.notification ?: return ""
        val extras = n.extras ?: return n.tickerText?.toString().orEmpty()
        val parts = mutableListOf<String>()
        fun add(key: String) {
            extras.getCharSequence(key)?.toString()?.let { if (it.isNotBlank()) parts += it }
        }
        add(Notification.EXTRA_TITLE)
        add(Notification.EXTRA_TEXT)
        add(Notification.EXTRA_BIG_TEXT)
        add(Notification.EXTRA_SUB_TEXT)
        add(Notification.EXTRA_INFO_TEXT)
        add(Notification.EXTRA_SUMMARY_TEXT)
        n.tickerText?.toString()?.let { if (it.isNotBlank()) parts += it }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { cs ->
            cs?.toString()?.let { if (it.isNotBlank()) parts += it }
        }
        // People / messages style
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            @Suppress("UNCHECKED_CAST")
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            // best-effort; ignore if unavailable
            if (messages != null) {
                parts += "messages(${messages.size})"
            }
        }
        return parts.joinToString(" | ").trim()
    }

    private fun notificationChannelId(sbn: StatusBarNotification): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sbn.notification?.channelId
        } else {
            null
        }
    }

    companion object {
        private const val TAG = "VmNotifListener"

        private val PHONE_PKG_HINTS = listOf(
            "voicemail",
            "visualvoicemail",
            "vvm",
            "dialer",
            "telecom",
            "telephony",
            "telephonyui",
            ".phone",
            "com.android.phone",
            "com.android.server.telecom",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.samsung.android.app.telephonyui",
            "com.samsung.android.phone",
            "com.sec.android.app.clockpackage", // sometimes abused; keep soft
            "com.google.android.dialer",
            "com.google.android.apps.messaging",
            "com.verizon",
            "com.vzw",
            "com.att",
            "com.tmobile",
            "com.sprint",
            "com.android.mms"
        )

        private val VM_TEXT_HINTS = listOf(
            "voicemail",
            "voice mail",
            "voice message",
            "voice msg",
            "new voice",
            "visual voicemail",
            "left a message",
            "left you a message",
            "unread message",
            "unread voicemail",
            "vmessage",
            "vvm",
            "voicenote",
            "voice note",
            "new message from", // some carriers
            "1 new message",
            "new messages"
        )
    }
}
