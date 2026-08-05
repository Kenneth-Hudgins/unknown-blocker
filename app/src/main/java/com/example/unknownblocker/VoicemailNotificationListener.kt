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
 * Sticky mode (feature toggle ON):
 * - A **blocked call** ARMs mute (no 15‑minute expiry).
 * - While ARMED, VM-looking alerts are dismissed unless they look like a
 *   **non-blocked / allowed** caller — then we KEEP the alert and DISARM.
 * - Ambiguous VMs (no usable number) stay muted while armed.
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
            BlockerSettings.isVmSuppressArmed(this)
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

            if (!BlockerSettings.isSuppressVmNotificationsEnabled(this)) return
            if (!looksLikeVoicemailOrPhoneMessage(sbn, text, channel, pkg)) return

            val decision = decide(sbn, text, channel, pkg)
            NotificationProbe.record(
                this,
                pkg,
                channel,
                text,
                when (decision) {
                    Decision.DISMISS -> "DISMISS"
                    Decision.KEEP -> "KEEP"
                    Decision.KEEP_AND_DISARM -> "KEEP+DISARM"
                    Decision.IGNORE -> "IGNORE"
                }
            )

            when (decision) {
                Decision.DISMISS -> {
                    dismissHard(sbn)
                    Log.i(TAG, "dismissed $pkg | $text")
                }
                Decision.KEEP_AND_DISARM -> {
                    BlockerSettings.disarmVmSuppress(this, "allowed-looking VM: $text")
                    Log.i(TAG, "kept+disarmed $pkg | $text")
                }
                Decision.KEEP, Decision.IGNORE -> {
                    if (fromPosted) Log.d(TAG, "keep $pkg | $text")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "evaluate failed", e)
        }
    }

    private enum class Decision {
        /** Not a candidate / feature off path handled earlier */
        IGNORE,
        /** Show the notification; leave arm state alone */
        KEEP,
        /** Show the notification and clear sticky mute */
        KEEP_AND_DISARM,
        /** Hide the notification; stay armed */
        DISMISS
    }

    private fun decide(
        sbn: StatusBarNotification,
        text: String,
        channel: String,
        pkg: String
    ): Decision {
        if (!BlockerSettings.isBlockingEnabled(this)) return Decision.KEEP
        if (!BlockerSettings.isSuppressVmNotificationsEnabled(this)) return Decision.KEEP
        if (!BlockerSettings.isVmSuppressArmed(this)) {
            // Not armed — do not mute anything (wait for next blocked call).
            return Decision.KEEP
        }

        val now = System.currentTimeMillis()

        // Allowed / contact / area-code number in the VM text → real person.
        if (RecentScreenedCalls.textMentionsRecent(
                this, text, RecentScreenedCalls.Kind.ALLOWED, now
            )
        ) {
            return Decision.KEEP_AND_DISARM
        }
        if (RecentScreenedCalls.textMentionsAllowlistedNumber(this, text)) {
            return Decision.KEEP_AND_DISARM
        }

        // Explicit recently-blocked number → spam VM, stay armed.
        if (RecentScreenedCalls.textMentionsRecent(
                this, text, RecentScreenedCalls.Kind.BLOCKED, now
            )
        ) {
            return Decision.DISMISS
        }

        // Ambiguous: no trustworthy number in the text.
        // If a non-blocked call was screened *after* the last block, treat the
        // next VM as likely theirs and unlock (covers VMs with no caller ID text).
        val lastAllowed = RecentScreenedCalls.mostRecentAllowedAt(this)
        val lastBlocked = RecentScreenedCalls.mostRecentBlockedAt(this)
        if (lastAllowed != null && lastBlocked != null && lastAllowed >= lastBlocked) {
            return Decision.KEEP_AND_DISARM
        }

        // Still armed, no allowed signal → keep muting.
        return Decision.DISMISS
    }

    private fun dismissHard(sbn: StatusBarNotification) {
        try {
            cancelNotification(sbn.key)
        } catch (e: Exception) {
            Log.w(TAG, "cancelNotification key failed", e)
        }
        try {
            @Suppress("DEPRECATION")
            cancelNotification(sbn.packageName, sbn.tag, sbn.id)
        } catch (_: Exception) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                snoozeNotification(sbn.key, 2 * 60 * 1000L)
            } catch (_: Exception) {
            }
        }
    }

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
        if (!pkgPhoneLike) return false

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

        if (category == Notification.CATEGORY_MESSAGE ||
            category == Notification.CATEGORY_EVENT ||
            category == Notification.CATEGORY_SOCIAL
        ) {
            return true
        }

        // While sticky-armed, treat short generic phone-package alerts as candidates.
        if (BlockerSettings.isVmSuppressArmed(this) &&
            !text.contains("missed call") &&
            !text.contains("calling") &&
            !text.contains("ringing") &&
            text.length <= 80
        ) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
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
            "com.sec.android.app.clockpackage",
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
            "new message from",
            "1 new message",
            "new messages"
        )
    }
}
