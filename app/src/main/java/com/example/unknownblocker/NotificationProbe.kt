package com.example.unknownblocker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ring buffer of recent notifications seen by [VoicemailNotificationListener]
 * so the UI can show what Samsung actually posted (debug / tuning).
 */
object NotificationProbe {
    private const val PREFS = "blocker_prefs"
    private const val KEY = "notif_probe_log"
    private const val MAX = 12

    data class Row(
        val timestampMs: Long,
        val pkg: String,
        val channel: String,
        val text: String,
        val action: String // SEEN / DISMISS / KEEP
    )

    fun record(
        context: Context,
        pkg: String,
        channel: String,
        text: String,
        action: String
    ) {
        val rows = load(context).toMutableList()
        rows.add(
            0,
            Row(
                timestampMs = System.currentTimeMillis(),
                pkg = pkg.take(80),
                channel = channel.take(40),
                text = text.replace('\n', ' ').take(160),
                action = action
            )
        )
        while (rows.size > MAX) rows.removeAt(rows.lastIndex)
        val arr = JSONArray()
        for (r in rows) {
            arr.put(
                JSONObject()
                    .put("ts", r.timestampMs)
                    .put("p", r.pkg)
                    .put("c", r.channel)
                    .put("t", r.text)
                    .put("a", r.action)
            )
        }
        // commit so UI process sees it immediately
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, arr.toString())
            .commit()
    }

    fun load(context: Context): List<Row> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Row>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Row(
                        timestampMs = o.optLong("ts"),
                        pkg = o.optString("p"),
                        channel = o.optString("c"),
                        text = o.optString("t"),
                        action = o.optString("a")
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .commit()
    }

    fun formatForUi(context: Context): String {
        val rows = load(context)
        if (rows.isEmpty()) {
            return "No notifications seen yet by the listener.\n" +
                "If this stays empty after a VM arrives, Notification access is not actually bound."
        }
        return rows.joinToString("\n\n") { r ->
            val ageSec = ((System.currentTimeMillis() - r.timestampMs) / 1000).coerceAtLeast(0)
            "[$ageSec s ago] ${r.action}\npkg=${r.pkg}\nch=${r.channel.ifBlank { "(none)" }}\n${r.text.ifBlank { "(no text)" }}"
        }
    }
}
