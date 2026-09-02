package com.example.unknownblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Best-effort SMS filter using the same [AllowRules] as calls.
 *
 * **Android limitation:** non-default SMS apps cannot fully block delivery.
 * `abortBroadcast()` may suppress some OEM notifications, but the message still
 * typically appears in the default Messages app. Full SMS blocking would require
 * becoming the default SMS app (out of scope).
 *
 * Still logs blocked senders to [BlockLog] as type `"sms"` for history (UI may
 * hide SMS rows and show calls-only — see [BlockLog.loadCallDays]).
 */
class SmsBlockerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("blocker_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        for (sms in messages) {
            val sender = sms.displayOriginatingAddress ?: continue
            if (!AllowRules.shouldAllow(context, sender)) {
                BlockLog.add(context, sender, "sms")
                // Best-effort: can suppress notification for some OEMs, but message
                // still lands in the default SMS app on modern Android.
                abortBroadcast()
            }
        }
    }
}
