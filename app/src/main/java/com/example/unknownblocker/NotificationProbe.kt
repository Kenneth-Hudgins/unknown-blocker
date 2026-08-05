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
 */
object NotificationProbe {
    const val LOG_FILE_NAME = "notification_listener_log.txt"
    private const val MAX_LINES = 400
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
                f.writeText("")
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
        val n = lineCount(context)
        return if (n == 0) {
            "Log file empty — no listener events yet (or log was cleared)."
        } else {
            "Log file has $n line(s). Tap Open to view in another app."
        }
    }

    private fun appendLine(context: Context, line: String) {
        lock.withLock {
            try {
                val f = logFile(context)
                f.appendText(line + "\n")
                trimIfNeeded(f)
            } catch (_: Exception) {
                // Best-effort diagnostics; never crash the listener.
            }
        }
    }

    private fun trimIfNeeded(file: File) {
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
