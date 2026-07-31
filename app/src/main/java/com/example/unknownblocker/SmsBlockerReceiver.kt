package com.example.unknownblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsBlockerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("blocker_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        for (sms in messages) {
            val sender = sms.displayOriginatingAddress ?: continue
            if (!ContactUtils.isNumberInContacts(context, sender)) {
                BlockLog.add(context, sender, "sms")
                // Best-effort: can suppress notification for some OEMs, but message
                // still lands in the default SMS app on modern Android.
                abortBroadcast()
            }
        }
    }
}
