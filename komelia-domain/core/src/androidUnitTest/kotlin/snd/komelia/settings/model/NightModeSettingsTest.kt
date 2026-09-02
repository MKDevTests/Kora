package snd.komelia.settings.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NightModeSettingsTest {

    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test
    fun `master switch off means never on, schedule or not`() {
        val settings = NightModeSettings(enabled = false, scheduleEnabled = true)
        assertFalse(settings.isActiveAt(at(23)))
        assertFalse(settings.isActiveAt(at(3)))
        assertNull(settings.minutesUntilNextTransition(at(23)))
    }

    @Test
    fun `no schedule means on for the whole day`() {
        val settings = NightModeSettings(enabled = true, scheduleEnabled = false)
        assertTrue(settings.isActiveAt(at(0)))
        assertTrue(settings.isActiveAt(at(12)))
        assertTrue(settings.isActiveAt(at(23, 59)))
        assertNull(settings.minutesUntilNextTransition(at(12)))
    }

    /**
     * The ordinary case, and the one a naive `start <= now && now < end`
     * would get exactly backwards: a night range runs past midnight, so its
     * end is numerically *before* its start.
     */
    @Test
    fun `a range that crosses midnight covers both sides of it`() {
        val settings = NightModeSettings(
            enabled = true,
            scheduleEnabled = true,
            startMinute = at(22),
            endMinute = at(7),
        )
        assertTrue(settings.isActiveAt(at(22)))
        assertTrue(settings.isActiveAt(at(23, 30)))
        assertTrue(settings.isActiveAt(at(0)))
        assertTrue(settings.isActiveAt(at(6, 59)))
        assertFalse(settings.isActiveAt(at(7)))
        assertFalse(settings.isActiveAt(at(12)))
        assertFalse(settings.isActiveAt(at(21, 59)))
    }

    @Test
    fun `a range inside one day stays inside it`() {
        val settings = NightModeSettings(
            enabled = true,
            scheduleEnabled = true,
            startMinute = at(1),
            endMinute = at(7),
        )
        assertFalse(settings.isActiveAt(at(0, 59)))
        assertTrue(settings.isActiveAt(at(1)))
        assertTrue(settings.isActiveAt(at(6, 45)))
        assertFalse(settings.isActiveAt(at(7)))
    }

    @Test
    fun `the next transition is the far end of wherever we are`() {
        val settings = NightModeSettings(
            enabled = true,
            scheduleEnabled = true,
            startMinute = at(22),
            endMinute = at(7),
        )
        // Inside the range: wait until it ends.
        assertEquals(9 * 60, settings.minutesUntilNextTransition(at(22)))
        assertEquals(7 * 60, settings.minutesUntilNextTransition(at(0)))
        // Outside it: wait until it starts.
        assertEquals(15 * 60, settings.minutesUntilNextTransition(at(7)))
        assertEquals(30, settings.minutesUntilNextTransition(at(21, 30)))
    }

    /**
     * A zero-length wait would spin the watcher: it would wake, find itself on
     * the boundary again, and ask for another zero-length wait.
     */
    @Test
    fun `sitting on a boundary waits a full day rather than zero`() {
        val settings = NightModeSettings(
            enabled = true,
            scheduleEnabled = true,
            startMinute = at(22),
            endMinute = at(22),
        )
        assertNull(settings.minutesUntilNextTransition(at(22)))

        val normal = NightModeSettings(
            enabled = true,
            scheduleEnabled = true,
            startMinute = at(22),
            endMinute = at(7),
        )
        assertTrue((normal.minutesUntilNextTransition(at(22)) ?: 0) > 0)
        assertTrue((normal.minutesUntilNextTransition(at(7)) ?: 0) > 0)
    }

    @Test
    fun `a degenerate range covers the day instead of nothing`() {
        val settings = NightModeSettings(
            enabled = true,
            scheduleEnabled = true,
            startMinute = at(3),
            endMinute = at(3),
        )
        assertTrue(settings.isActiveAt(at(3)))
        assertTrue(settings.isActiveAt(at(15)))
    }
}
