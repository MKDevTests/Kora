package snd.komelia.ui.library

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Parser for the "next release" series tag written by the user's external
 * classifier: `nextrelease:<volume>-<dd.mm.yyyy>`.
 *
 * Example: `nextrelease:24-12.01.2027` means volume 24 ships on 12 Jan 2027.
 * Kora only ever reads it. Same convention as [GenreLabels] (`kora:genre:*`).
 */
object NextReleaseLabels {
    const val PREFIX = "nextrelease:"

    data class NextRelease(val volume: String, val date: LocalDate)

    /**
     * The upcoming release to display on the series screen, or `null` when there
     * is no parseable `nextrelease:*` tag or its date is already in the past.
     * If several valid future tags exist, the nearest one wins.
     */
    fun upcomingRelease(
        tags: List<String>,
        today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    ): NextRelease? =
        tags.asSequence()
            .filter { it.startsWith(PREFIX) }
            .mapNotNull { parse(it.removePrefix(PREFIX)) }
            .filter { it.date >= today }
            .minByOrNull { it.date }

    /** Parse `24-12.01.2027` -> NextRelease("24", 2027-01-12); `null` if malformed. */
    private fun parse(value: String): NextRelease? {
        val dash = value.indexOf('-')
        if (dash <= 0 || dash == value.length - 1) return null
        val volume = value.substring(0, dash).trim()
        if (volume.isEmpty()) return null

        val dateParts = value.substring(dash + 1).trim().split('.')
        if (dateParts.size != 3) return null
        val day = dateParts[0].toIntOrNull() ?: return null
        val month = dateParts[1].toIntOrNull() ?: return null
        val year = dateParts[2].toIntOrNull() ?: return null

        return try {
            NextRelease(volume, LocalDate(year, month, day))
        } catch (e: IllegalArgumentException) {
            null // out-of-range day/month/year
        }
    }

    /** `2027-01-12` -> `12/01/2027` for display. */
    fun formatDate(date: LocalDate): String {
        val day = date.dayOfMonth.toString().padStart(2, '0')
        val month = date.monthNumber.toString().padStart(2, '0')
        return "$day/$month/${date.year}"
    }
}
