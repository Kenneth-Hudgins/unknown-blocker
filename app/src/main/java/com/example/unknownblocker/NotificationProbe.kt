package com.example.unknownblocker

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Append-only notification listener log written to app private storage.
 * Opened externally via FileProvider (text viewer / browser / share sheet).
 *
 * Caps: trims to [MAX_LINES] newest lines, and **deletes** the file if it
 * reaches [MAX_BYTES] (2 MB) so it cannot grow without bound.
 */
object NotificationProbe {
    const val LOG_FILE_NAME = "notification_listener_log.txt"
    private const val MAX_LINES = 400
    /** Delete and start fresh when the log hits this size. */
    private const val MAX_BYTES: Long = 2L * 1024L * 1024L // 2 MB

    private val lock = ReentrantLock()

    private val stampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun logFile(context: Context): File = File(context.filesDir, LOG_FILE_NAME)

    fun record(
        context: Context,
        pkg: String,
        channel: String,
        text: String,
        action: String
    ) {
        val stamp = stampFormat.format(Date())
        val pkgSafe = pkg.take(120).replace('\n', ' ')
        val chSafe = channel.take(80).replace('\n', ' ').ifBlank { "(none)" }
        val textSafe = text.replace('\n', ' ').take(240).ifBlank { "(no text)" }
        val line = "$stamp | $action | pkg=$pkgSafe | ch=$chSafe | $textSafe"
        appendLine(context, line)
    }

    fun clear(context: Context) {
        lock.withLock {
            val f = logFile(context)
            if (f.exists()) {
                f.delete()
            }
        }
    }

    fun existsAndNonEmpty(context: Context): Boolean {
        val f = logFile(context)
        return f.exists() && f.length() > 0L
    }

    fun lineCount(context: Context): Int {
        val f = logFile(context)
        if (!f.exists() || f.length() == 0L) return 0
        return try {
            f.useLines { it.count() }
        } catch (_: Exception) {
            0
        }
    }

    /** Short status for the main screen (not the full log body). */
    fun statusSummary(context: Context): String {
        val f = logFile(context)
        if (!f.exists() || f.length() == 0L) {
            return "Log file empty — no listener events yet (or log was cleared)."
        }
        val n = lineCount(context)
        val kb = f.length() / 1024L
        return "Log file has $n line(s), ~${kb} KB (auto-deletes at 2 MB). Tap Open to view."
    }

    private fun appendLine(context: Context, line: String) {
        lock.withLock {
            try {
                val f = logFile(context)
                // Hit size cap → delete entire log, then write this event fresh.
                if (f.exists() && f.length() >= MAX_BYTES) {
                    f.delete()
                    val stamp = stampFormat.format(Date())
                    f.appendText(
                        "$stamp | INFO | pkg= | ch=(none) | " +
                            "log deleted after reaching 2 MB size limit\n"
                    )
                }
                f.appendText(line + "\n")
                // Safety: single huge write somehow still over cap
                if (f.exists() && f.length() >= MAX_BYTES) {
                    f.delete()
                    val stamp = stampFormat.format(Date())
                    f.appendText(
                        "$stamp | INFO | pkg= | ch=(none) | " +
                            "log deleted after reaching 2 MB size limit\n"
                    )
                    f.appendText(line + "\n")
                }
                trimIfNeeded(f)
            } catch (_: Exception) {
                // Best-effort diagnostics; never crash the listener.
            }
        }
    }

    private fun trimIfNeeded(file: File) {
        if (!file.exists()) return
        // Prefer size delete over line trim when already huge
        if (file.length() >= MAX_BYTES) {
            try {
                file.delete()
            } catch (_: Exception) {
            }
            return
        }
        val lines = try {
            file.readLines()
        } catch (_: Exception) {
            return
        }
        if (lines.size <= MAX_LINES) return
        val keep = lines.takeLast(MAX_LINES)
        try {
            file.writeText(keep.joinToString("\n") + "\n")
        } catch (_: Exception) {
        }
    }
}
