package com.example.unknownblocker

import android.app.Notification
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Best-effort dismissal of **voicemail** notifications (not SMS/chat).
 *
 * Two modes (independent):
 * 1) **Mute all VM alerts** (toggle): while ON, every voicemail-looking notification is
 *    canceled until the user turns it off (no re-enable on contact calls/texts).
 * 2) **Sticky after blocked call** (toggle): a blocked call ARMs mute; while
 *    ARMED, VM alerts are dismissed unless they look allowed — then KEEP+DISARM.
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
        val shouldRetry =
            BlockerSettings.isMuteAllVmNotificationsEnabled(this) ||
                (BlockerSettings.isSuppressVmNotificationsEnabled(this) &&
                    BlockerSettings.isVmSuppressArmed(this))
        if (shouldRetry) {
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

            val muteAll = BlockerSettings.isMuteAllVmNotificationsEnabled(this)
            val stickyOn = BlockerSettings.isSuppressVmNotificationsEnabled(this)
            if (!muteAll && !stickyOn) return
            if (!looksLikeVoicemail(sbn, text, channel, pkg)) return

            val decision = decide(sbn, text, channel, pkg)
            NotificationProbe.record(
                this,
                pkg,
                channel,
                text,
                when (decision) {
                    Decision.DISMISS -> if (muteAll) "DISMISS-ALL" else "DISMISS"
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
        IGNORE,
        KEEP,
        KEEP_AND_DISARM,
        DISMISS
    }

    private fun decide(
        sbn: StatusBarNotification,
        text: String,
        channel: String,
        pkg: String
    ): Decision {
        // Continuous mute wins: hide every VM alert until user turns it off.
        if (BlockerSettings.isMuteAllVmNotificationsEnabled(this)) {
            return Decision.DISMISS
        }

        if (!BlockerSettings.isBlockingEnabled(this)) return Decision.KEEP
        if (!BlockerSettings.isSuppressVmNotificationsEnabled(this)) return Decision.KEEP
        if (!BlockerSettings.isVmSuppressArmed(this)) {
            return Decision.KEEP
        }

        val now = System.currentTimeMillis()

        if (RecentScreenedCalls.textMentionsRecent(
                this, text, RecentScreenedCalls.Kind.ALLOWED, now
            )
        ) {
            return Decision.KEEP_AND_DISARM
        }
        if (RecentScreenedCalls.textMentionsAllowlistedNumber(this, text)) {
            return Decision.KEEP_AND_DISARM
        }

        if (RecentScreenedCalls.textMentionsRecent(
                this, text, RecentScreenedCalls.Kind.BLOCKED, now
            )
        ) {
            return Decision.DISMISS
        }

        val lastAllowed = RecentScreenedCalls.mostRecentAllowedAt(this)
        val lastBlocked = RecentScreenedCalls.mostRecentBlockedAt(this)
        if (lastAllowed != null && lastBlocked != null && lastAllowed >= lastBlocked) {
            return Decision.KEEP_AND_DISARM
        }

        return Decision.DISMISS
    }

    private fun dismissHard(sbn: StatusBarNotification) {
        // Clear the shade now, then ask the system not to show *this* notification
        // again for SNOOZE_MS. Long duration avoids the old 2‑minute media-duck loop.
        // New VM posts (new keys) can still appear; mute-all/sticky will handle those too.
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
                snoozeNotification(sbn.key, SNOOZE_MS)
            } catch (e: Exception) {
                Log.w(TAG, "snoozeNotification failed", e)
            }
        }
    }

    /**
     * True only for voicemail / visual-voicemail style alerts.
     * Must NOT match ordinary SMS/MMS/chat from contacts or strangers.
     */
    private fun looksLikeVoicemail(
        sbn: StatusBarNotification,
        textRaw: String,
        channelRaw: String,
        pkgRaw: String
    ): Boolean {
        val pkg = pkgRaw.lowercase()
        val text = textRaw.lowercase()
        val channel = channelRaw.lowercase()
        val category = sbn.notification?.category

        // Never touch SMS / messaging apps — that was muting contact texts.
        if (isMessagingPackage(pkg)) return false

        // Active call UI
        if (sbn.isOngoing && (
                text.contains("calling") ||
                    text.contains("mobile network") ||
                    category == Notification.CATEGORY_CALL
                )
        ) {
            return false
        }

        // Missed-call banners are not voicemail
        if (text.contains("missed call") || channel.contains("missed")) {
            return false
        }

        // Strong voicemail wording anywhere (title/body/channel)
        if (hasStrongVoicemailHint(text) || hasStrongVoicemailHint(channel)) {
            return true
        }

        // Phone/dialer/carrier package + VM channel or voice-ish channel+text
        if (!isPhoneOrCarrierPackage(pkg)) return false

        if (hasVoicemailChannelHint(channel)) return true

        // Soft: phone package + both channel and text lean "voice" (not bare "message")
        val voiceishText =
            text.contains("voice") ||
                text.contains("vm") ||
                text.contains("vvm") ||
                text.contains("left a message") ||
                text.contains("left you a message")
        val voiceishChannel =
            channel.contains("voice") ||
                channel.contains("vm") ||
                channel.contains("vvm") ||
                channel.contains("mail") // voicemail channels often include "mail"
        if (voiceishText && (voiceishChannel || channel.isBlank())) {
            // Avoid plain SMS-style "new message" on a phone package
            if (looksLikePlainSmsWording(text) && !hasStrongVoicemailHint(text)) {
                return false
            }
            return true
        }

        // Sticky-armed only: short generic alerts from phone package (OEM VM with little text)
        if (BlockerSettings.isVmSuppressArmed(this) &&
            !text.contains("calling") &&
            !text.contains("ringing") &&
            !looksLikePlainSmsWording(text) &&
            text.length <= 80
        ) {
            if (hasVoicemailChannelHint(channel)) return true
            if (channel.contains("phone") || channel.contains("status")) {
                // Only if not clearly a chat notification
                if (category != Notification.CATEGORY_MESSAGE &&
                    category != Notification.CATEGORY_SOCIAL
                ) {
                    return text.isBlank() ||
                        text.contains("new") ||
                        text.contains("voice") ||
                        text.contains("mail")
                }
            }
        }

        return false
    }

    private fun hasStrongVoicemailHint(s: String): Boolean {
        return VM_STRONG_HINTS.any { s.contains(it) }
    }

    private fun hasVoicemailChannelHint(channel: String): Boolean {
        if (channel.isBlank()) return false
        return channel.contains("voicemail") ||
            channel.contains("voice_mail") ||
            channel.contains("voice-mail") ||
            channel.contains("vvm") ||
            channel.contains("visual_voicemail") ||
            channel.contains("visualvoicemail") ||
            (channel.contains("voice") && channel.contains("mail"))
    }

    private fun looksLikePlainSmsWording(text: String): Boolean {
        if (hasStrongVoicemailHint(text)) return false
        // Typical SMS preview patterns without "voice"
        return (text.contains("message") || text.contains("msg") || text.contains("text")) &&
            !text.contains("voice") &&
            !text.contains("voicemail") &&
            !text.contains("vvm")
    }

    private fun isMessagingPackage(pkg: String): Boolean {
        return MESSAGING_PKG_HINTS.any { pkg.contains(it) }
    }

    private fun isPhoneOrCarrierPackage(pkg: String): Boolean {
        return PHONE_OR_CARRIER_PKG_HINTS.any { pkg.contains(it) }
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
        // Do NOT treat EXTRA_MESSAGES as a voicemail signal (that's SMS/chat style).
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

        /** Packages that deliver SMS/MMS/chat — never dismiss as "voicemail". */
        private val MESSAGING_PKG_HINTS = listOf(
            "apps.messaging",
            "android.messaging",
            "samsung.android.messaging",
            "samsung.android.mms",
            "com.android.mms",
            "com.google.android.apps.messaging",
            "com.android.messaging",
            "messages",
            ".mms",
            "whatsapp",
            "telegram",
            "signal",
            "facebook.orca",
            "instagram",
            "discord",
            "snapchat",
            "rcs"
        )

        /** Dialer / phone / telecom / carrier visual voicemail hosts. */
        private val PHONE_OR_CARRIER_PKG_HINTS = listOf(
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
            "com.google.android.dialer",
            "com.verizon",
            "com.vzw",
            "com.att",
            "com.tmobile",
            "com.sprint"
        )

        /** Phrases that clearly mean voicemail (not ordinary SMS). */
        private val VM_STRONG_HINTS = listOf(
            "voicemail",
            "voice mail",
            "voice message",
            "voice msg",
            "new voice",
            "visual voicemail",
            "visual voice",
            "left a message",
            "left you a message",
            "unread voicemail",
            "vmessage",
            "vvm",
            "voicenote",
            "voice note",
            "has a new voicemail",
            "new voicemail",
            "voicemails"
        )
    }
}
