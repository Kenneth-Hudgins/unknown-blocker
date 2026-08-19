package com.example.unknownblocker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

object BlockerSettings {
    const val PREFS_NAME = "blocker_prefs"
    const val KEY_ENABLED = "enabled"
    const val KEY_SUPPRESS_VM_NOTIFICATIONS = "suppress_vm_notifications"
    /** Sticky: after a blocked call, keep muting VM alerts until an allowed-looking VM. */
    const val KEY_VM_SUPPRESS_ARMED = "vm_suppress_armed"
    /**
     * Continuous mute: while ON, dismiss all voicemail-looking notifications
     * (spam and contacts) until the user turns it off. Default OFF.
     */
    const val KEY_MUTE_ALL_VM_NOTIFICATIONS = "mute_all_vm_notifications"
    /** Diagnostic listener log file — default OFF. */
    const val KEY_PROBE_LOGGING = "probe_logging_enabled"

    const val KEY_FIRST_OPEN_MS = "first_open_ms"

    fun isBlockingEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun setBlockingEnabled(context: Context, enabled: Boolean) {
        val ed = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
        if (!enabled) {
            // Blocking off → stop sticky VM mute
            ed.putBoolean(KEY_VM_SUPPRESS_ARMED, false)
        }
        ed.commit()
    }

    fun isSuppressVmNotificationsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SUPPRESS_VM_NOTIFICATIONS, false)
    }

    fun setSuppressVmNotificationsEnabled(context: Context, enabled: Boolean) {
        val ed = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SUPPRESS_VM_NOTIFICATIONS, enabled)
        if (!enabled) {
            ed.putBoolean(KEY_VM_SUPPRESS_ARMED, false)
        }
        ed.commit()
    }

    /** Continuous mute of all voicemail-looking alerts. Default OFF. */
    fun isMuteAllVmNotificationsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_MUTE_ALL_VM_NOTIFICATIONS, false)
    }

    fun setMuteAllVmNotificationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MUTE_ALL_VM_NOTIFICATIONS, enabled)
            .commit()
        NotificationProbe.record(
            context,
            context.packageName,
            "",
            if (enabled) "mute-all VM ON" else "mute-all VM OFF",
            if (enabled) "MUTE_ALL_ON" else "MUTE_ALL_OFF"
        )
    }

    fun isVmSuppressArmed(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_VM_SUPPRESS_ARMED, false)
    }

    /** Call when we block a call — start muting VM notifications. */
    fun armVmSuppress(context: Context) {
        if (!isSuppressVmNotificationsEnabled(context)) return
        if (!isBlockingEnabled(context)) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VM_SUPPRESS_ARMED, true)
            .commit()
        NotificationProbe.record(context, context.packageName, "", "vm suppress ARMED (blocked call)", "ARM")
    }

    /** Call when we keep a VM that looks like a real/allowed caller. */
    fun disarmVmSuppress(context: Context, reason: String) {
        if (!isVmSuppressArmed(context)) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VM_SUPPRESS_ARMED, false)
            .commit()
        NotificationProbe.record(
            context,
            context.packageName,
            "",
            "vm suppress DISARMED: $reason",
            "DISARM"
        )
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val expected = ComponentName(context, VoicemailNotificationListener::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        if (TextUtils.isEmpty(enabled)) return false
        return enabled.split(':').any { piece ->
            val cn = ComponentName.unflattenFromString(piece)
            cn != null && cn.packageName == expected.packageName && cn.className == expected.className
        }
    }

    fun notificationListenerSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    /** Diagnostic file logging for the notification listener. Defaults to OFF. */
    fun isProbeLoggingEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PROBE_LOGGING, false)
    }

    fun setProbeLoggingEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PROBE_LOGGING, enabled)
            .commit()
    }

    /**
     * Records first open if missing. Uses earliest blocked-log timestamp when
     * available so upgrades inherit a sensible "install" day; else now.
     */
    fun ensureFirstOpenRecorded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_FIRST_OPEN_MS) && prefs.getLong(KEY_FIRST_OPEN_MS, 0L) > 0L) {
            return
        }
        val fromLog = BlockLog.load(context).minOfOrNull { it.timestampMs }?.takeIf { it > 0L }
        val ms = fromLog ?: System.currentTimeMillis()
        prefs.edit().putLong(KEY_FIRST_OPEN_MS, ms).commit()
    }

    fun getFirstOpenMs(context: Context): Long {
        ensureFirstOpenRecorded(context)
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_FIRST_OPEN_MS, System.currentTimeMillis())
    }
}
