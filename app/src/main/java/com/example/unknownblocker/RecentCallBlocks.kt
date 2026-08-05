package com.example.unknownblocker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Short-lived memory of recently screened calls (blocked **and** allowed) so the
 * voicemail notification listener can decide whether an alert is likely spam-VM
 * vs a real person who was allowed through.
 */
object RecentScreenedCalls {
    private const val PREFS_NAME = "blocker_prefs"
    private const val KEY_RECENT = "recent_screened_calls"
    private const val MAX_ENTRIES = 40

    /** How long we correlate VM notifications with recent screening decisions. */
    const val WINDOW_MS: Long = 15 * 60 * 1000L // 15 minutes

    enum class Kind { BLOCKED, ALLOWED }

    data class Entry(
        val number: String,
        val kind: Kind,
        val timestampMs: Long
    )

    fun markBlocked(context: Context, number: String) = mark(context, number, Kind.BLOCKED)

    fun markAllowed(context: Context, number: String) = mark(context, number, Kind.ALLOWED)

    fun mark(context: Context, number: String, kind: Kind) {
        val cleaned = number.ifBlank { "Unknown" }
        val now = System.currentTimeMillis()
        val entries = load(context)
            .filter { now - it.timestampMs <= WINDOW_MS * 2 }
            .toMutableList()
        entries.add(0, Entry(cleaned, kind, now))
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(entries.lastIndex)
        }
        save(context, entries)
    }

    fun load(context: Context): List<Entry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Entry>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val kind = when (obj.optString("k", "B")) {
                    "A" -> Kind.ALLOWED
                    else -> Kind.BLOCKED
                }
                out.add(
                    Entry(
                        number = obj.optString("n", "Unknown"),
                        kind = kind,
                        timestampMs = obj.optLong("ts", 0L)
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun hasRecentBlocked(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        return load(context).any {
            it.kind == Kind.BLOCKED && nowMs - it.timestampMs in 0..WINDOW_MS
        }
    }

    fun hasRecentAllowed(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        return load(context).any {
            it.kind == Kind.ALLOWED && nowMs - it.timestampMs in 0..WINDOW_MS
        }
    }

    fun mostRecentBlockedAt(context: Context): Long? =
        load(context).filter { it.kind == Kind.BLOCKED }.maxOfOrNull { it.timestampMs }

    fun mostRecentAllowedAt(context: Context): Long? =
        load(context).filter { it.kind == Kind.ALLOWED }.maxOfOrNull { it.timestampMs }

    fun textMentionsRecent(
        context: Context,
        text: String,
        kind: Kind,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val haystack = text.filter { it.isDigit() }
        if (haystack.length < 7) return false
        for (entry in load(context)) {
            if (entry.kind != kind) continue
            if (nowMs - entry.timestampMs !in 0..WINDOW_MS) continue
            if (digitsMatch(haystack, entry.number)) return true
        }
        return false
    }

    /**
     * Pull plausible phone digit-runs from notification text and see if any
     * would be allowed by contacts / area-code rules right now.
     */
    fun textMentionsAllowlistedNumber(context: Context, text: String): Boolean {
        for (candidate in extractPhoneCandidates(text)) {
            if (AllowRules.shouldAllow(context, candidate)) return true
        }
        return false
    }

    fun textMentionsBlockedOnlyNumber(context: Context, text: String): Boolean {
        val candidates = extractPhoneCandidates(text)
        if (candidates.isEmpty()) return false
        // Every extracted number is a non-allow number, and at least one matches a recent block.
        var matchedBlock = false
        for (candidate in candidates) {
            if (AllowRules.shouldAllow(context, candidate)) return false
            if (textMentionsRecent(context, candidate, Kind.BLOCKED)) matchedBlock = true
        }
        return matchedBlock
    }

    fun digitsMatch(haystackDigits: String, number: String): Boolean {
        val needle = number.filter { it.isDigit() }
        if (needle.length < 7 || haystackDigits.length < 7) return false
        return haystackDigits.contains(needle.takeLast(10)) ||
            haystackDigits.contains(needle.takeLast(7))
    }

    /**
     * Extract digit sequences that look like phone fragments (7–15 digits).
     */
    fun extractPhoneCandidates(text: String): List<String> {
        val matches = Regex("""\d[\d\s().+\-]{5,}\d""").findAll(text).map { it.value }.toList()
        val out = linkedSetOf<String>()
        for (m in matches) {
            val d = m.filter { it.isDigit() }
            if (d.length in 7..15) out += d
        }
        // Also raw continuous runs
        Regex("""\d{7,15}""").findAll(text.filter { it.isDigit() || it.isWhitespace() || it in "()- +" })
        val continuous = Regex("""\d{7,15}""").findAll(text).map { it.value }
        out.addAll(continuous)
        return out.toList()
    }

    private fun save(context: Context, entries: List<Entry>) {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject()
                    .put("n", e.number)
                    .put("k", if (e.kind == Kind.ALLOWED) "A" else "B")
                    .put("ts", e.timestampMs)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENT, arr.toString())
            // Must be visible immediately to NotificationListenerService
            .commit()
    }
}

/**
 * Back-compat facade used by older call sites / mental model "recent blocks".
 * Prefer [RecentScreenedCalls] for new code.
 */
@Deprecated("Use RecentScreenedCalls", ReplaceWith("RecentScreenedCalls"))
object RecentCallBlocks {
    const val WINDOW_MS = RecentScreenedCalls.WINDOW_MS

    fun mark(context: Context, number: String) = RecentScreenedCalls.markBlocked(context, number)

    fun hasRecentWithinWindow(context: Context, nowMs: Long = System.currentTimeMillis()) =
        RecentScreenedCalls.hasRecentBlocked(context, nowMs)

    fun textMentionsRecentNumber(context: Context, text: String, nowMs: Long = System.currentTimeMillis()) =
        RecentScreenedCalls.textMentionsRecent(context, text, RecentScreenedCalls.Kind.BLOCKED, nowMs)
}
