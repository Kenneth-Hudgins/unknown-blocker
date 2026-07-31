package com.example.unknownblocker

import android.telecom.Call
import android.telecom.CallScreeningService

class ScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val prefs = getSharedPreferences("blocker_prefs", MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("enabled", false)

        if (!isEnabled) {
            respondToCall(callDetails, CallResponse.Builder().setDisallowCall(false).build())
            return
        }

        val number = callDetails.handle?.schemeSpecificPart.orEmpty()
        if (ContactUtils.isNumberInContacts(this, number)) {
            respondToCall(callDetails, CallResponse.Builder().setDisallowCall(false).build())
        } else {
            BlockLog.add(this, number.ifBlank { "Private/Unknown" }, "call")
            val response = CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSilenceCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
            respondToCall(callDetails, response)
        }
    }
}
