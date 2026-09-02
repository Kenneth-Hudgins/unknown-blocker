package com.example.unknownblocker

import android.content.Context

/**
 * User-managed NANP (US/Canada-style) area codes that always ring while blocking is on.
 *
 * **Why:** Work help-desks / on-call ranges (e.g. `254`) should get through without
 * adding every number as a contact. Does not require the number to be in contacts.
 *
 * Storage: StringSet in shared prefs [PREFS_NAME] / [KEY_CODES], max [MAX_CODES].
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

    /** True if [phoneNumber]'s NANP area code is on the allowlist. */
    fun matches(context: Context, phoneNumber: String): Boolean {
        val codes = load(context)
        if (codes.isEmpty()) return false
        val area = extractNanpAreaCode(phoneNumber) ?: return false
        return area in codes
    }

    /**
     * Digits only; strips a leading country-code `1` when present so
     * `+1 254 555 0100` and `(254) 555-0100` both yield `254`.
     */
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

    /** UI input → exactly 3 digits, or null if invalid. */
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
