package com.example.unknownblocker

import android.content.Context

/** Shared allow/block decision used by call screening and SMS receiver. */
object AllowRules {
    /**
     * Returns true if the number should ring/notify while blocking is enabled.
     * Order: contacts → allowed area code → block.
     */
    fun shouldAllow(context: Context, phoneNumber: String): Boolean {
        if (phoneNumber.isBlank()) return false
        if (ContactUtils.isNumberInContacts(context, phoneNumber)) return true
        if (AreaCodeAllowlist.matches(context, phoneNumber)) return true
        return false
    }
}
