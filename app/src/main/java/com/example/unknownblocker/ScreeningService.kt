package com.example.unknownblocker

import android.telecom.Call
import android.telecom.CallScreeningService

/**
 * System call-screening entry point (user must grant the Call Screening role).
 *
 * **Why this exists:** Android only lets a designated screening app reject/silence
 * inbound calls without becoming the full dialer.
 *
 * **Flow when blocking is ON:**
 * 1. Read number from [Call.Details.handle]
 * 2. [AllowRules.shouldAllow] → contact or area-code allowlist
 * 3. Allow: remember number in [RecentScreenedCalls] (helps sticky VM logic)
 * 4. Block: log to [BlockLog], mark blocked, [BlockerSettings.armVmSuppress],
 *    then disallow + reject + silence
 *
 * **Important:** Rejecting a call does **not** stop the carrier from taking a
 * voicemail. VM notification mute is a separate best-effort path
 * ([VoicemailNotificationListener]).
 *
 * Prefs key `"enabled"` matches [BlockerSettings.KEY_ENABLED].
 */
class ScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val prefs = getSharedPreferences("blocker_prefs", MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("enabled", false)

        // Master toggle off → never interfere with ringing.
        if (!isEnabled) {
            respondToCall(callDetails, CallResponse.Builder().setDisallowCall(false).build())
            return
        }

        val number = callDetails.handle?.schemeSpecificPart.orEmpty()
        if (AllowRules.shouldAllow(this, number)) {
            // Sticky VM mute may later disarm if a VM looks like this caller.
            if (number.isNotBlank()) {
                RecentScreenedCalls.markAllowed(this, number)
            }
            respondToCall(callDetails, CallResponse.Builder().setDisallowCall(false).build())
        } else {
            val blockedLabel = number.ifBlank { "Private/Unknown" }
            BlockLog.add(this, blockedLabel, "call")
            RecentScreenedCalls.markBlocked(this, blockedLabel)
            // Arms sticky "hide VM after block" if that feature toggle is on.
            BlockerSettings.armVmSuppress(this)
            val response = CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSilenceCall(true)
                // Keep system call log / missed-call UX honest for the user.
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
            respondToCall(callDetails, response)
        }
    }
}
