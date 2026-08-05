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

    fun isBlockingEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun setBlockingEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .commit()
    }

    fun isSuppressVmNotificationsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SUPPRESS_VM_NOTIFICATIONS, false)
    }

    fun setSuppressVmNotificationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SUPPRESS_VM_NOTIFICATIONS, enabled)
            .commit()
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
}
