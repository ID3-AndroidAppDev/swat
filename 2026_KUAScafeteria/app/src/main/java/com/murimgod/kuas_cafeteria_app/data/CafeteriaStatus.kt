package com.murimgod.kuas_cafeteria_app.data

import com.murimgod.kuas_cafeteria_app.data.model.Hours
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Open / closed status of a cafeteria for *today*, derived from its serving
 * hours. Hours are Japan times, so comparisons use Asia/Tokyo regardless of the
 * device timezone.
 */
object CafeteriaStatus {

    private val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")

    /** Show a live second-by-second countdown only inside this window. */
    const val COUNTDOWN_WINDOW_MS = 10 * 60 * 1000L

    enum class Phase { BEFORE_OPEN, OPEN, AFTER_CLOSE, UNKNOWN }

    data class Status(
        val phase: Phase,
        /** Epoch millis of the next transition (open or close), null if none today. */
        val nextEventMs: Long?,
        /** True if the next event opens the cafeteria, false if it closes it. */
        val nextOpens: Boolean,
    ) {
        fun msUntilNext(nowMs: Long): Long? = nextEventMs?.let { it - nowMs }
    }

    fun compute(hours: Hours?, now: ZonedDateTime = ZonedDateTime.now(TOKYO)): Status {
        val open = parse(hours?.open)
        val close = parse(hours?.close)
        if (open == null || close == null) return Status(Phase.UNKNOWN, null, false)

        val openMs = now.with(open).toInstant().toEpochMilli()
        val closeMs = now.with(close).toInstant().toEpochMilli()
        val nowMs = now.toInstant().toEpochMilli()

        return when {
            nowMs < openMs -> Status(Phase.BEFORE_OPEN, openMs, true)
            nowMs < closeMs -> Status(Phase.OPEN, closeMs, false)
            else -> Status(Phase.AFTER_CLOSE, null, false)
        }
    }

    private fun parse(s: String?): LocalTime? = s?.let {
        runCatching { LocalTime.parse(it.trim()) }.getOrNull()
    }

    /** "MM:SS" for a positive duration, used by the live countdown. */
    fun formatCountdown(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return "%02d:%02d".format(total / 60, total % 60)
    }
}
