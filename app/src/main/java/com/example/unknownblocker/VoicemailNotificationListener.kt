package com.example.unknownblocker

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Best-effort dismissal of **voicemail-looking** notifications after a call we blocked.
 *
 * Limitations (honest):
 * - Does NOT delete carrier voicemail messages — only clears the heads-up/status alert.
 * - OEM/carrier notification text varies; we use heuristics + a recent-block time window.
 * - User must grant Notification access in system settings.
 * - Real contact voicemails that arrive in the same window could theoretically be dismissed
 *   if they look like VM alerts; we try to prefer number match + recent blocked-call window.
 */
class VoicemailNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            activeNotifications?.forEach { maybeCancel(it) }
        } catch (e: SecurityException) {
            Log.w(TAG, "activeNotifications failed", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        maybeCancel(sbn)
    }

    private fun maybeCancel(sbn: StatusBarNotification) {
        try {
            if (!shouldCancel(sbn)) return
            cancelNotification(sbn.key)
            Log.i(TAG, "Dismissed possible VM notification from ${sbn.packageName}")
        } catch (e: Exception) {
            Log.w(TAG, "cancel failed", e)
        }
    }

    private fun shouldCancel(sbn: StatusBarNotification): Boolean {
        // Never touch our own notifications
        if (sbn.packageName == packageName) return false

        if (!BlockerSettings.isBlockingEnabled(this)) return false
        if (!BlockerSettings.isSuppressVmNotificationsEnabled(this)) return false

        if (!looksLikeVoicemailNotification(sbn)) return false

        val text = notificationText(sbn)
        val mentionsBlocked = RecentCallBlocks.textMentionsRecentNumber(this, text)
        val recentBlock = RecentCallBlocks.hasRecentWithinWindow(this)

        // Prefer number match; otherwise only within the post-block window.
        return mentionsBlocked || recentBlock
    }

    private fun looksLikeVoicemailNotification(sbn: StatusBarNotification): Boolean {
        val pkg = sbn.packageName.orEmpty().lowercase()
        val text = notificationText(sbn).lowercase()
        val channel = notificationChannelId(sbn)?.lowercase().orEmpty()

        // Package heuristics (dialer / phone / carrier VM apps)
        val pkgHit = PKG_HINTS.any { pkg.contains(it) }

        // Text / channel heuristics
        val textHit = VM_TEXT_HINTS.any { text.contains(it) } ||
            VM_TEXT_HINTS.any { channel.contains(it) }

        // Category
        val category = sbn.notification?.category
        val categoryHit = category == Notification.CATEGORY_MESSAGE && textHit

        // Many OEMs use phone package with "voicemail" in title only
        if (textHit) return true
        if (pkgHit && (textHit || channel.contains("voice") || text.contains("message"))) {
            // Phone package alone is too broad (would kill SMS/call notifs).
            // Require some voice/mail signal in text or channel when package matches.
            return textHit || channel.contains("voice") || channel.contains("vm") ||
                text.contains("voice") || text.contains("mail")
        }
        return categoryHit
    }

    private fun notificationText(sbn: StatusBarNotification): String {
        val n = sbn.notification ?: return ""
        val extras = n.extras ?: return n.tickerText?.toString().orEmpty()
        val parts = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString(),
            n.tickerText?.toString()
        )
        // EXTRA_TEXT_LINES
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        val lineText = lines?.joinToString(" ") { it?.toString().orEmpty() }.orEmpty()
        return (parts.joinToString(" ") + " " + lineText).trim()
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

        private val PKG_HINTS = listOf(
            "voicemail",
            "visualvoicemail",
            "vvm",
            "dialer",
            "telecom",
            ".phone",
            "telephony",
            "com.android.server.telecom",
            "com.samsung.android.incallui",
            "com.samsung.android.phone",
            "com.google.android.dialer",
            "com.android.phone",
            "com.verizon",
            "com.att",
            "com.tmobile",
            "com.sprint"
        )

        private val VM_TEXT_HINTS = listOf(
            "voicemail",
            "voice mail",
            "voice message",
            "new voice",
            "visual voicemail",
            "left a message",
            "left you a message",
            "unread message",
            "vmessage",
            "vvm"
        )
    }
}
