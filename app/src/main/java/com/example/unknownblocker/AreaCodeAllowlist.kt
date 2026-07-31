package com.example.unknownblocker

import android.content.Context

/**
 * User-managed NANP area codes that are always allowed through while blocking is on.
 * Example: "254" lets 254-xxx-xxxx and +1 254 xxx xxxx ring even if not in contacts.
 */
object AreaCodeAllowlist {
    private const val PREFS_NAME = "blocker_prefs"
    private const val KEY_CODES = "allowed_area_codes"
    private const val MAX_CODES = 50

    fun load(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getStringSet(KEY_CODES, null) ?: return emptySet()
        return raw.mapNotNull { normalizeCode(it) }.toSortedSet()
    }

    fun add(context: Context, code: String): AddResult {
        val normalized = normalizeCode(code) ?: return AddResult.Invalid
        val current = load(context).toMutableSet()
        if (normalized in current) return AddResult.Duplicate
        if (current.size >= MAX_CODES) return AddResult.Full
        current += normalized
        save(context, current)
        return AddResult.Added(normalized)
    }

    fun remove(context: Context, code: String) {
        val normalized = normalizeCode(code) ?: return
        val current = load(context).toMutableSet()
        if (current.remove(normalized)) {
            save(context, current)
        }
    }

    fun matches(context: Context, phoneNumber: String): Boolean {
        val codes = load(context)
        if (codes.isEmpty()) return false
        val area = extractNanpAreaCode(phoneNumber) ?: return false
        return area in codes
    }

    /** Digits only, optional leading country code 1 stripped for NANP. */
    fun extractNanpAreaCode(phoneNumber: String): String? {
        var digits = phoneNumber.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        // +1 / leading 1 country code
        if (digits.length == 11 && digits.startsWith("1")) {
            digits = digits.substring(1)
        } else if (digits.length > 11 && digits.startsWith("1")) {
            // Unusual but try national 10 after leading 1
            digits = digits.substring(1)
        }
        if (digits.length < 10) return null
        val area = digits.substring(0, 3)
        return if (area.length == 3 && area.all { it.isDigit() } && area[0] != '0' && area[0] != '1') {
            area
        } else {
            // Still return 3 digits even if NPA rules are loose — user may test edge cases
            area
        }
    }

    fun normalizeCode(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        if (digits.length != 3) return null
        return digits
    }

    private fun save(context: Context, codes: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_CODES, codes)
            .apply()
    }

    sealed class AddResult {
        data class Added(val code: String) : AddResult()
        data object Invalid : AddResult()
        data object Duplicate : AddResult()
        data object Full : AddResult()
    }
}
