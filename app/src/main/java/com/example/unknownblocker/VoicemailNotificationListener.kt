package com.example.unknownblocker

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Best-effort dismissal of **voicemail-looking** notifications tied to calls we blocked.
 *
 * Decision order (when blocking + suppress toggle + notification access):
 * 1. Not a VM-looking notification → leave it
 * 2. Notification mentions an **allowed** number (recent allow, contacts, area code) → **keep**
 * 3. Notification mentions a **recently blocked** number → **dismiss**
 * 4. No usable number in the text:
 *    - If a more recent (or equally recent) **allowed** call exists in-window → **keep**
 *    - Else if a **blocked** call exists in-window → **dismiss**
 *    - Else → **keep**
 *
 * Limitations:
 * - Does NOT delete carrier voicemail — only the notification.
 * - OEM/carrier text varies; number often missing → step 4 is best-effort.
 * - Private/unknown blocked numbers can't match text by digits.
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
            Log.i(TAG, "Dismissed possible spam-VM notification from ${sbn.packageName}")
        } catch (e: Exception) {
            Log.w(TAG, "cancel failed", e)
        }
    }

    private fun shouldCancel(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == packageName) return false
        if (!BlockerSettings.isBlockingEnabled(this)) return false
        if (!BlockerSettings.isSuppressVmNotificationsEnabled(this)) return false
        if (!looksLikeVoicemailNotification(sbn)) return false

        val text = notificationText(sbn)
        val now = System.currentTimeMillis()

        // 2) Explicit allow signal in the notification → never suppress
        val mentionsAllowedRecent = RecentScreenedCalls.textMentionsRecent(
            this, text, RecentScreenedCalls.Kind.ALLOWED, now
        )
        if (mentionsAllowedRecent) {
            Log.d(TAG, "keep VM: mentions recent allowed number")
            return false
        }
        if (RecentScreenedCalls.textMentionsAllowlistedNumber(this, text)) {
            Log.d(TAG, "keep VM: mentions contact/area-code allowlisted number")
            return false
        }

        // 3) Explicit blocked number in the notification → suppress
        val mentionsBlocked = RecentScreenedCalls.textMentionsRecent(
            this, text, RecentScreenedCalls.Kind.BLOCKED, now
        )
        if (mentionsBlocked) {
            Log.d(TAG, "dismiss VM: mentions recent blocked number")
            return true
        }

        // 4) Ambiguous (no number we can trust in the text)
        val lastAllowed = RecentScreenedCalls.mostRecentAllowedAt(this)
        val lastBlocked = RecentScreenedCalls.mostRecentBlockedAt(this)
        val allowedInWindow =
            lastAllowed != null && now - lastAllowed in 0..RecentScreenedCalls.WINDOW_MS
        val blockedInWindow =
            lastBlocked != null && now - lastBlocked in 0..RecentScreenedCalls.WINDOW_MS

        if (!blockedInWindow) {
            return false
        }

        // If the most recent screened call in-window was ALLOWED (contact/area code),
        // keep the VM alert — likely their voicemail, not the spam one.
        if (allowedInWindow && lastAllowed!! >= lastBlocked!!) {
            Log.d(TAG, "keep VM: most recent screened call was allowed (ambiguous notif)")
            return false
        }

        Log.d(TAG, "dismiss VM: most recent screened signal is a block (ambiguous notif)")
        return true
    }

    private fun looksLikeVoicemailNotification(sbn: StatusBarNotification): Boolean {
        val pkg = sbn.packageName.orEmpty().lowercase()
        val text = notificationText(sbn).lowercase()
        val channel = notificationChannelId(sbn)?.lowercase().orEmpty()

        val pkgHit = PKG_HINTS.any { pkg.contains(it) }
        val textHit = VM_TEXT_HINTS.any { text.contains(it) } ||
            VM_TEXT_HINTS.any { channel.contains(it) }
        val category = sbn.notification?.category
        val categoryHit = category == Notification.CATEGORY_MESSAGE && textHit

        if (textHit) return true
        if (pkgHit && (textHit || channel.contains("voice") || text.contains("message"))) {
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
