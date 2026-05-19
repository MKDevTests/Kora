package snd.komelia.stats

import snd.komga.client.series.KomgaSeriesId
import kotlin.time.Instant

/**
 * Snapshot of computed reading statistics for the Stats screen / Home card.
 * All values are derived on-demand from Komga API counts (lifetime metrics)
 * and the local [ReadingEvent] log (time-bounded metrics).
 *
 * Temporal metrics (last 7/30 days, streak, monthly chart) start at zero
 * when the user first installs the Stats feature; there is no backfill of
 * past completions because Komga does not expose per-book completion
 * timestamps via filterable API. Lifetime metrics are exact and reflect
 * the server's view.
 */
data class ReadingStats(
    val booksFinishedLast7Days: Int,
    val booksFinishedLast30Days: Int,
    val streakDays: Int,

    val lifetimeBooksFinished: Int,
    val lifetimeSeriesFinished: Int,
    val librariesExplored: Int,

    /** 12 entries, oldest → newest, zero-filled. Used for the bar chart. */
    val monthlyHistory: List<MonthBucket>,
    /** Up to 5 most recently active series. */
    val recentSeries: List<RecentSeriesEntry>,
    /** Achievement list; each entry includes whether it is unlocked. */
    val achievements: List<Achievement>,
) {
    /** True when there is nothing to display (fresh install, no completions yet). */
    val isEmpty: Boolean
        get() = lifetimeBooksFinished == 0 &&
                booksFinishedLast30Days == 0 &&
                recentSeries.isEmpty()
}

data class MonthBucket(
    /** ISO-like "YYYY-MM" — used both as map key and as chart x-axis label source. */
    val yearMonth: String,
    val count: Int,
)

data class RecentSeriesEntry(
    val seriesId: KomgaSeriesId,
    val seriesTitle: String,
    val lastReadAt: Instant,
)

data class Achievement(
    /** Stable identifier (e.g. "books_10", "streak_30") for analytics and i18n. */
    val id: String,
    val title: String,
    val description: String,
    val earned: Boolean,
)
