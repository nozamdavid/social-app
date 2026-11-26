package am.noz.caelum.core.model

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Shared timestamp parsing and relative/absolute formatting helpers.
 */
object TimeFormatter {
    const val POST_EDIT_WINDOW_MS: Long = 10 * 60 * 1000L

    fun parseInstantOrNull(iso: String?): Instant? {
        if (iso.isNullOrBlank()) return null
        return runCatching { Instant.parse(iso) }.getOrNull()
    }

    fun formatEditCountdown(createdAtIso: String?, now: Instant = Instant.now()): String? {
        val created = parseInstantOrNull(createdAtIso) ?: return null
        val elapsedMs = ChronoUnit.MILLIS.between(created, now)
        val remainingMs = POST_EDIT_WINDOW_MS - elapsedMs
        if (remainingMs <= 0L) return null
        val totalSec = (remainingMs / 1000L).toInt()
        val mins = totalSec / 60
        val secs = totalSec % 60
        return String.format(Locale.US, "%d:%02d", mins, secs)
    }

    fun isWithinEditWindow(createdAtIso: String?, now: Instant = Instant.now()): Boolean {
        val created = parseInstantOrNull(createdAtIso) ?: return false
        val elapsedMs = ChronoUnit.MILLIS.between(created, now)
        return elapsedMs in 0 until POST_EDIT_WINDOW_MS
    }
}
