package com.example.unknownblocker

import android.content.Context

/**
 * Single place for “should this number ring / notify while blocking is on?”
 *
 * Used by both [ScreeningService] (calls) and [SmsBlockerReceiver] (SMS) so
 * call and SMS policy cannot drift apart.
 *
 * **Decision order (blocking ON):**
 * 1. Blank number → deny (treat as unknown)
 * 2. In contacts → allow
 * 3. NANP area code on user allowlist → allow
 * 4. Else → block
 */
object AllowRules {
    /**
     * @return true if the number should be treated as allowed while blocking is enabled.
     */
    fun shouldAllow(context: Context, phoneNumber: String): Boolean {
        if (phoneNumber.isBlank()) return false
        if (ContactUtils.isNumberInContacts(context, phoneNumber)) return true
        if (AreaCodeAllowlist.matches(context, phoneNumber)) return true
        return false
    }
}
