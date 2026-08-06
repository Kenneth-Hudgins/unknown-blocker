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
    /** Diagnostic listener log file — default OFF. */
    const val KEY_PROBE_LOGGING = "probe_logging_enabled"

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
}
