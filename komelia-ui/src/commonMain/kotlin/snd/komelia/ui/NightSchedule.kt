package snd.komelia.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * True while the "dark at night" window is open. The clock is sampled on
 * the minute so the switch flips within sixty seconds of the boundary,
 * and a disabled schedule still remembers its state so the composable
 * keeps a stable call order when it is turned on.
 */
@Composable
fun rememberIsNight(enabled: Boolean, startMinutes: Int, endMinutes: Int): Boolean {
    var now by remember { mutableIntStateOf(currentMinuteOfDay()) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            now = currentMinuteOfDay()
            val second = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).second
            delay((61 - second) * 1000L)
        }
    }
    return enabled && isNightMinute(now, startMinutes, endMinutes)
}

/** Window [start, end) in minutes of the day, wrapping past midnight when start > end. */
fun isNightMinute(now: Int, start: Int, end: Int): Boolean =
    if (start <= end) now >= start && now < end
    else now >= start || now < end

private fun currentMinuteOfDay(): Int {
    val t = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return t.hour * 60 + t.minute
}
