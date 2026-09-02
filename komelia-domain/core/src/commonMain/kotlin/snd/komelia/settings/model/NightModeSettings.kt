package snd.komelia.settings.model

import kotlinx.serialization.Serializable

/**
 * Reader night mode: a warm tint over the page, to cut the blue a white manga
 * page throws at you in a dark room.
 *
 * Scope is the image reader only. The app chrome is already dark and the epub
 * reader has its own themes, so neither is touched here.
 *
 * [enabled] is the master switch. [scheduleEnabled] does not turn the feature
 * on by itself — it restricts an already-enabled tint to [startMinute]..
 * [endMinute]. So: master off = never; master on, schedule off = whenever the
 * reader is open; both on = inside the range only.
 */
@Serializable
data class NightModeSettings(
    val enabled: Boolean = false,
    /** 0f = no tint at all, 1f = the warmest the filter goes. */
    val intensity: Float = 0.5f,
    val scheduleEnabled: Boolean = false,
    /** Minutes since midnight. */
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 7 * 60,
) {
    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}

/**
 * Whether the tint should be on at [minuteOfDay].
 *
 * A range whose end is at or before its start crosses midnight — which is the
 * ordinary case here, 22:00 to 07:00 — so the two orderings are separate
 * tests rather than one comparison.
 */
fun NightModeSettings.isActiveAt(minuteOfDay: Int): Boolean {
    if (!enabled) return false
    if (!scheduleEnabled) return true
    if (startMinute == endMinute) return true
    return if (startMinute < endMinute) minuteOfDay >= startMinute && minuteOfDay < endMinute
    else minuteOfDay >= startMinute || minuteOfDay < endMinute
}

/**
 * Minutes from [minuteOfDay] until the tint next switches on or off, so the
 * watcher can sleep exactly that long instead of waking up every minute to
 * compare two integers.
 *
 * Returns null when nothing will ever change on its own: no schedule, or a
 * degenerate range that covers the whole day.
 */
fun NightModeSettings.minutesUntilNextTransition(minuteOfDay: Int): Int? {
    if (!enabled || !scheduleEnabled) return null
    if (startMinute == endMinute) return null
    val target = if (isActiveAt(minuteOfDay)) endMinute else startMinute
    val day = NightModeSettings.MINUTES_PER_DAY
    // Not Math.floorMod: this is common code and has to compile for wasm too.
    val delta = ((target - minuteOfDay) % day + day) % day
    // Sitting exactly on a boundary must not produce a zero-length wait, or the
    // watcher spins. A full day away is the correct answer there.
    return if (delta == 0) day else delta
}
