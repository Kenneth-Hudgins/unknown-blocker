package com.example.unknownblocker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Short-lived memory of recently blocked **calls** so the notification listener
 * can dismiss voicemail alerts that arrive shortly afterward.
 *
 * Does not delete carrier voicemail — only helps silence the notification ping.
 */
object RecentCallBlocks {
    private const val PREFS_NAME = "blocker_prefs"
    private const val KEY_RECENT = "recent_call_blocks"
    private const val MAX_ENTRIES = 30

    /** How long after a blocked call we still treat VM notifications as related. */
    const val WINDOW_MS: Long = 15 * 60 * 1000L // 15 minutes

    data class Entry(val number: String, val timestampMs: Long)

    fun mark(context: Context, number: String) {
        val cleaned = number.ifBlank { "Unknown" }
        val now = System.currentTimeMillis()
        val entries = load(context)
            .filter { now - it.timestampMs <= WINDOW_MS * 2 }
            .toMutableList()
        entries.add(0, Entry(cleaned, now))
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
                out.add(
                    Entry(
                        number = obj.optString("n", "Unknown"),
                        timestampMs = obj.optLong("ts", 0L)
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun hasRecentWithinWindow(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        return load(context).any { nowMs - it.timestampMs in 0..WINDOW_MS }
    }

    /**
     * True if [text] contains a digit sequence that matches a recently blocked number
     * (last 7+ digits, to tolerate formatting / country code differences).
     */
    fun textMentionsRecentNumber(context: Context, text: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val haystack = text.filter { it.isDigit() }
        if (haystack.length < 7) return false
        for (entry in load(context)) {
            if (nowMs - entry.timestampMs !in 0..WINDOW_MS) continue
            val needle = entry.number.filter { it.isDigit() }
            if (needle.length < 7) continue
            val tail = needle.takeLast(10)
            if (haystack.contains(tail) || haystack.contains(needle.takeLast(7))) {
                return true
            }
        }
        return false
    }

    private fun save(context: Context, entries: List<Entry>) {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(JSONObject().put("n", e.number).put("ts", e.timestampMs))
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENT, arr.toString())
            .apply()
    }
}
