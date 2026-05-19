package snd.komelia.stats

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import snd.komelia.komga.api.KomgaApi
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort.KomgaBooksSort
import snd.komga.client.search.allOfBooks
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeriesSearch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Computes a [ReadingStats] snapshot by combining:
 *  - **Lifetime / aggregate** metrics via Komga API (single requests with
 *    `size=1`, so the server returns just `totalElements`).
 *  - **Time-bounded** metrics (last 7/30 days, streak, monthly chart) from
 *    the local [ReadingEventsRepository] log. These start empty when the
 *    feature is first enabled — Komga does not expose per-book completion
 *    timestamps in a filterable way, so historical data cannot be
 *    backfilled.
 *
 * Stateless: each [compute] call performs the full set of queries. The
 * caller (a view-model) is expected to cache results in memory for the
 * lifetime of the Stats screen.
 *
 * Every Komga API call is wrapped in best-effort error handling. A failed
 * call (offline, server unreachable) degrades that specific metric to
 * zero rather than failing the whole page — local-event-derived metrics
 * still work without network.
 */
class ReadingStatsService(
    private val readingEvents: ReadingEventsRepository,
    private val komgaApi: StateFlow<KomgaApi>,
    private val clock: Clock = Clock.System,
) {

    suspend fun compute(): ReadingStats {
        val now = clock.now()
        val api = komgaApi.value

        val booksLast7 = readingEvents.countSince(ReadingEvent.Type.COMPLETED, now - 7.days)
        val booksLast30 = readingEvents.countSince(ReadingEvent.Type.COMPLETED, now - 30.days)
        val streak = computeStreak(now)
        val monthly = computeMonthlyHistory(now)

        val lifetimeBooks = fetchLifetimeBooksFinished(api)
        val lifetimeSeries = fetchLifetimeSeriesFinished(api)
        val librariesExplored = fetchLibrariesCount(api)
        val recent = fetchRecentSeries(api)

        return ReadingStats(
            booksFinishedLast7Days = booksLast7,
            booksFinishedLast30Days = booksLast30,
            streakDays = streak,
            lifetimeBooksFinished = lifetimeBooks,
            lifetimeSeriesFinished = lifetimeSeries,
            librariesExplored = librariesExplored,
            monthlyHistory = monthly,
            recentSeries = recent,
            achievements = computeAchievements(
                lifetimeBooks = lifetimeBooks,
                lifetimeSeries = lifetimeSeries,
                streak = streak,
            ),
        )
    }

    // ---------------------------------------------------------------- streak

    /**
     * Consecutive calendar days ending today (or yesterday — the grace day
     * — so the streak doesn't reset before the user reads today) that have
     * at least one COMPLETED event.
     */
    private suspend fun computeStreak(now: Instant): Int {
        val tz = TimeZone.currentSystemDefault()
        val dates = readingEvents
            .distinctDates(ReadingEvent.Type.COMPLETED, limit = 365)
            .toSet()
        if (dates.isEmpty()) return 0

        // Date walking via Instant arithmetic — we rely on Instant.minus(Duration)
        // which is a member function (kotlin.time), so no fragile extension
        // imports are needed. The conversion to LocalDate happens only for
        // formatting the date key into the "YYYY-MM-DD" form already in `dates`.
        val today = now.toLocalDateTime(tz).date.toString()
        val yesterday = (now - 1.days).toLocalDateTime(tz).date.toString()

        var cursor: Instant = when {
            today in dates -> now
            yesterday in dates -> now - 1.days
            else -> return 0
        }

        var streak = 0
        while (streak < 365) {
            val dateStr = cursor.toLocalDateTime(tz).date.toString()
            if (dateStr !in dates) break
            streak++
            cursor = cursor - 1.days
        }
        return streak
    }

    // ------------------------------------------------------- monthly history

    /**
     * 12 buckets oldest→newest, zero-filled. Generates the canonical
     * "YYYY-MM" labels for the past 12 months relative to [now], then
     * fills with the per-month counts from the event log.
     */
    private suspend fun computeMonthlyHistory(now: Instant): List<MonthBucket> {
        val tz = TimeZone.currentSystemDefault()
        val raw = readingEvents.monthlyBuckets(ReadingEvent.Type.COMPLETED, now - 365.days)

        val current = now.toLocalDateTime(tz).date
        val labels = generateMonthLabels(current.year, current.monthNumber, count = 12)
        return labels.map { MonthBucket(it, raw[it] ?: 0) }
    }

    // --------------------------------------------------------- lifetime API

    private suspend fun fetchLifetimeBooksFinished(api: KomgaApi): Int =
        runCatching {
            val condition = allOfBooks {
                readStatus { isEqualTo(KomgaReadStatus.READ) }
            }.toBookCondition()
            api.bookApi.getBookList(
                search = KomgaBookSearch(condition = condition),
                pageRequest = KomgaPageRequest(size = 1),
            ).totalElements.toInt()
        }.onFailure {
            logger.warn(it) { "fetchLifetimeBooksFinished failed" }
        }.getOrDefault(0)

    private suspend fun fetchLifetimeSeriesFinished(api: KomgaApi): Int =
        runCatching {
            val condition = allOfSeries {
                readStatus { isEqualTo(KomgaReadStatus.READ) }
            }.toSeriesCondition()
            api.seriesApi.getSeriesList(
                search = KomgaSeriesSearch(condition = condition),
                pageRequest = KomgaPageRequest(size = 1),
            ).totalElements.toInt()
        }.onFailure {
            logger.warn(it) { "fetchLifetimeSeriesFinished failed" }
        }.getOrDefault(0)

    /**
     * Number of libraries known to the Komga server. We surface this as
     * "libraries explored" for the achievements row; precise per-library
     * "has the user touched it" would require N+1 API calls and isn't
     * worth the latency for an aggregate badge.
     */
    private suspend fun fetchLibrariesCount(api: KomgaApi): Int =
        runCatching {
            api.libraryApi.getLibraries().size
        }.onFailure {
            logger.warn(it) { "fetchLibrariesCount failed" }
        }.getOrDefault(0)

    // ---------------------------------------------------------- recent series

    /**
     * Top 5 distinct series, ordered by most recent read activity. We
     * fetch up to 25 recently-read books server-side and dedupe by
     * seriesId client-side — cheap and resilient to oneshots / series
     * with many books read on the same day.
     */
    private suspend fun fetchRecentSeries(api: KomgaApi): List<RecentSeriesEntry> =
        runCatching {
            val condition = allOfBooks {
                anyOf {
                    readStatus { isEqualTo(KomgaReadStatus.READ) }
                    readStatus { isEqualTo(KomgaReadStatus.IN_PROGRESS) }
                }
            }.toBookCondition()
            val page = api.bookApi.getBookList(
                search = KomgaBookSearch(condition = condition),
                pageRequest = KomgaPageRequest(
                    size = 25,
                    sort = KomgaBooksSort.byReadDateDesc(),
                ),
            )
            val seen = linkedMapOf<String, RecentSeriesEntry>()
            for (book in page.content) {
                val key = book.seriesId.value
                if (seen.containsKey(key)) continue
                val readDate = book.readProgress?.lastModified ?: continue
                seen[key] = RecentSeriesEntry(
                    seriesId = book.seriesId,
                    seriesTitle = book.seriesTitle,
                    lastReadAt = readDate,
                )
                if (seen.size >= 5) break
            }
            seen.values.toList()
        }.onFailure {
            logger.warn(it) { "fetchRecentSeries failed" }
        }.getOrDefault(emptyList())

    // ----------------------------------------------------------- achievements

    private fun computeAchievements(
        lifetimeBooks: Int,
        lifetimeSeries: Int,
        streak: Int,
    ): List<Achievement> = listOf(
        achievement("books_1", lifetimeBooks >= 1, "First book", "Finished your first book"),
        achievement("books_10", lifetimeBooks >= 10, "10 books", "Finished 10 books"),
        achievement("books_50", lifetimeBooks >= 50, "50 books", "Finished 50 books"),
        achievement("books_100", lifetimeBooks >= 100, "100 books", "Finished 100 books"),
        achievement("books_250", lifetimeBooks >= 250, "250 books", "Finished 250 books"),
        achievement("books_500", lifetimeBooks >= 500, "500 books", "Finished 500 books"),
        achievement("books_1000", lifetimeBooks >= 1000, "1000 books", "Finished 1000 books"),
        achievement("series_1", lifetimeSeries >= 1, "First series", "Finished your first series"),
        achievement("series_5", lifetimeSeries >= 5, "5 series", "Finished 5 series"),
        achievement("series_25", lifetimeSeries >= 25, "25 series", "Finished 25 series"),
        achievement("series_100", lifetimeSeries >= 100, "100 series", "Finished 100 series"),
        achievement("streak_7", streak >= 7, "7-day streak", "Read every day for a week"),
        achievement("streak_30", streak >= 30, "30-day streak", "Read every day for a month"),
        achievement("streak_100", streak >= 100, "100-day streak", "Read every day for 100 days"),
    )

    private fun achievement(id: String, earned: Boolean, title: String, description: String) =
        Achievement(id = id, title = title, description = description, earned = earned)
}

// -- helpers ---------------------------------------------------------------

private fun generateMonthLabels(year: Int, monthNumber: Int, count: Int): List<String> {
    val labels = mutableListOf<String>()
    var y = year
    var m = monthNumber
    // Walk backward `count - 1` months, then reverse so labels are oldest→newest.
    for (i in 0 until count) {
        labels.add("%04d-%02d".format(y, m))
        m -= 1
        if (m == 0) {
            m = 12
            y -= 1
        }
    }
    return labels.reversed()
}
