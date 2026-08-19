package com.example.unknownblocker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Lightweight persistent log of blocked calls/SMS.
 * Stored in SharedPreferences as a JSON array (newest first), capped at [MAX_ENTRIES].
 */
object BlockLog {
    private const val PREFS_NAME = "blocker_prefs"
    private const val KEY_LOG = "blocked_log"
    private const val MAX_ENTRIES = 200

    data class Entry(
        val number: String,
        val type: String, // "call" or "sms"
        val timestampMs: Long
    )

    /** One calendar day of blocked calls (newest day first when listed). */
    data class DayGroup(
        /** Local calendar day key yyyy-MM-dd for expand state. */
        val dayKey: String,
        /** Display like 8/18/2026 */
        val label: String,
        val entries: List<Entry>
    ) {
        val count: Int get() = entries.size
    }

    fun add(context: Context, number: String, type: String) {
        val cleaned = number.ifBlank { "Unknown" }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val entries = load(context).toMutableList()
        entries.add(0, Entry(cleaned, type, System.currentTimeMillis()))
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(entries.lastIndex)
        }
        prefs.edit().putString(KEY_LOG, serialize(entries)).apply()
    }

    fun load(context: Context): List<Entry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LOG, null) ?: return emptyList()
        return try {
            deserialize(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Blocked calls only (SMS omitted from history UI for now). */
    fun loadCalls(context: Context): List<Entry> {
        return load(context).filter { it.type.equals("call", ignoreCase = true) }
    }

    /**
     * Days that have at least one blocked call, newest calendar day first.
     * Entries within each day are newest-first.
     */
    fun loadCallDays(context: Context): List<DayGroup> {
        val calls = loadCalls(context)
        if (calls.isEmpty()) return emptyList()

        val zone = TimeZone.getDefault()
        val labelFmt = SimpleDateFormat("M/d/yyyy", Locale.US).apply { timeZone = zone }
        val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }

        // LinkedHashMap preserves first-seen order; calls are newest-first already.
        val buckets = linkedMapOf<String, MutableList<Entry>>()
        for (e in calls) {
            val key = keyFmt.format(Date(e.timestampMs))
            buckets.getOrPut(key) { mutableListOf() }.add(e)
        }

        return buckets.map { (key, list) ->
            val label = labelFmt.format(Date(list.first().timestampMs))
            DayGroup(dayKey = key, label = label, entries = list)
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LOG)
            .apply()
    }

    fun count(context: Context): Int = load(context).size

    fun countCalls(context: Context): Int = loadCalls(context).size

    private fun serialize(entries: List<Entry>): String {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject()
                    .put("n", e.number)
                    .put("t", e.type)
                    .put("ts", e.timestampMs)
            )
        }
        return arr.toString()
    }

    private fun deserialize(raw: String): List<Entry> {
        val arr = JSONArray(raw)
        val out = ArrayList<Entry>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            out.add(
                Entry(
                    number = obj.optString("n", "Unknown"),
                    type = obj.optString("t", "call"),
                    timestampMs = obj.optLong("ts", 0L)
                )
            )
        }
        return out
    }
}
