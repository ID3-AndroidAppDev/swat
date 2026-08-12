package com.murimgod.kuas_cafeteria_app

import com.murimgod.kuas_cafeteria_app.ui.dayview.DayViewViewModel
import org.junit.Assert.assertEquals
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Test

/**
 * Pure-JVM tests for the date/week math that drives which week the app opens on.
 * These guard the "open the nearest serving day" rule and the ISO-week
 * navigation helpers against regressions.
 */
class DayViewWeekLogicTest {

    // ── defaultFocusDate: nearest serving weekday ────────────────────────────

    @Test
    fun weekday_focus_is_today() {
        val wed = LocalDate.of(2026, 6, 10) // Wednesday
        assertEquals(wed, DayViewViewModel.defaultFocusDate(wed))
    }

    @Test
    fun saturday_focus_is_previous_friday_same_week() {
        val sat = LocalDate.of(2026, 6, 13) // Saturday
        val focus = DayViewViewModel.defaultFocusDate(sat)
        assertEquals(DayOfWeek.FRIDAY, focus.dayOfWeek)
        assertEquals(LocalDate.of(2026, 6, 12), focus)
    }

    @Test
    fun sunday_focus_is_next_monday() {
        val sun = LocalDate.of(2026, 6, 14) // Sunday
        val focus = DayViewViewModel.defaultFocusDate(sun)
        assertEquals(DayOfWeek.MONDAY, focus.dayOfWeek)
        assertEquals(LocalDate.of(2026, 6, 15), focus)
    }

    @Test
    fun sunday_focus_week_is_following_week() {
        val sun = LocalDate.of(2026, 6, 14)
        val focus = DayViewViewModel.defaultFocusDate(sun)
        // Sunday is ISO week 24; the Monday we jump to is ISO week 25.
        assertEquals("2026-W24", DayViewViewModel.isoWeekOf(sun))
        assertEquals("2026-W25", DayViewViewModel.isoWeekOf(focus))
    }

    // ── ISO week helpers ─────────────────────────────────────────────────────

    @Test
    fun isoWeekOf_formats_with_padding() {
        assertEquals("2026-W01", DayViewViewModel.isoWeekOf(LocalDate.of(2026, 1, 1)))
        assertEquals("2026-W24", DayViewViewModel.isoWeekOf(LocalDate.of(2026, 6, 10)))
    }

    @Test
    fun mondayOfWeek_returns_monday() {
        val monday = DayViewViewModel.mondayOfWeek("2026-W25")
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)
        assertEquals(LocalDate.of(2026, 6, 15), monday)
    }

    @Test
    fun next_then_previous_week_is_identity() {
        val start = "2026-W24"
        val next = DayViewViewModel.nextWeek(start)
        assertEquals("2026-W25", next)
        assertEquals(start, DayViewViewModel.previousWeek(next))
    }

    @Test
    fun week_navigation_crosses_year_boundary() {
        // 2026 starts on a Thursday, so it has 53 ISO weeks. W53 -> 2027-W01.
        assertEquals("2026-W53", DayViewViewModel.nextWeek("2026-W52"))
        assertEquals("2027-W01", DayViewViewModel.nextWeek("2026-W53"))
    }
}
