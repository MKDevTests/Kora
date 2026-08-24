package snd.komelia.stats

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.StateFlow
import snd.komga.client.library.KomgaLibrary
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

/** How long a server-provided count of finished books is trusted before re-asking. */
private val BOOKS_BASELINE_TTL = 7.days

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
    /**
     * The library list the session already holds. Read from here rather than
     * asked for again: the stats card was the second `GET /api/v1/libraries` of
     * every cold start, for a number the app had in memory.
     */
    private val libraries: StateFlow<List<KomgaLibrary>>,
    private val clock: Clock = Clock.System,
) {

    /**
     * Thirteen steps, every one of them awaited before the next starts. Measured
     * on the tablet, the ten SQL steps alone spanned 2.66 s of the window in
     * which the home screen paints, on a connection pool of size one — and the
     * three API steps compete with the shelves for the same server.
     *
     * Each step is timed separately as `stats.*` so "the stats card is slow"
     * can be answered with "this one is", rather than by guessing. One trace
     * per card load, which is the granularity PerfTrace is for.
     */
    suspend fun compute(): ReadingStats = snd.komelia.perf.PerfTrace.measure("stats.total") {
        val now = clock.now()
        val api = komgaApi.value

        val booksLast7 = trace("stats.booksLast7") { readingEvents.countSince(ReadingEvent.Type.COMPLETED, now - 7.days) }
        val booksLast30 = trace("stats.booksLast30") { readingEvents.countSince(ReadingEvent.Type.COMPLETED, now - 30.days) }
        val pagesLast7 = trace("stats.pagesLast7") { readingEvents.sumPagesSince(ReadingEvent.Type.COMPLETED, now - 7.days) }
        val pagesLast30 = trace("stats.pagesLast30") { readingEvents.sumPagesSince(ReadingEvent.Type.COMPLETED, now - 30.days) }
        // Lifetime = pages from COMPLETED events still in the local log +
        // pages carried over from older events trimmed by past backup
        // exports (LIFETIME_CARRYOVER sentinel rows). Without the carryover
        // term the total would silently reset after a 365-day cliff.
        val pagesLifetime = trace("stats.pagesLifetime") {
            readingEvents.sumPagesLifetime(ReadingEvent.Type.COMPLETED) +
                readingEvents.sumPagesLifetimeCarryover()
        }
        val streak = trace("stats.streak") { computeStreak(now) }
        val monthly = trace("stats.monthly") { computeMonthlyHistory(now) }
        val daily30 = trace("stats.daily30") { computeDailyHistory(now, days = 30) }
        val daily7 = trace("stats.daily7") { computeDailyHistory(now, days = 7) }

        val lifetimeBooks = trace("stats.lifetimeBooks") { lifetimeBooksFinished(api, now) }
        val lifetimeSeries = trace("stats.api.lifetimeSeries") { fetchLifetimeSeriesFinished(api) }
        val librariesExplored = fetchLibrariesCount()
        val recent = trace("stats.api.recentSeries") { fetchRecentSeries(api) }

        ReadingStats(
            booksFinishedLast7Days = booksLast7,
            booksFinishedLast30Days = booksLast30,
            streakDays = streak,
            lifetimeBooksFinished = lifetimeBooks,
            lifetimeSeriesFinished = lifetimeSeries,
            librariesExplored = librariesExplored,
            pagesReadLast7Days = pagesLast7,
            pagesReadLast30Days = pagesLast30,
            pagesReadLifetime = pagesLifetime,
            monthlyHistory = monthly,
            dailyHistory30d = daily30,
            dailyHistory7d = daily7,
            recentSeries = recent,
        )
    }

    private suspend inline fun <T> trace(label: String, crossinline block: suspend () -> T): T =
        snd.komelia.perf.PerfTrace.measure(label) { block() }

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

    /**
     * [days] buckets oldest→newest, zero-filled. Pulls the per-day counts
     * from the event log for the last [days] calendar days (including
     * today) and zero-fills any missing days so the chart x-axis stays
     * stable. Used by the 7-day and 30-day chart options (v1.0.12+).
     */
    private suspend fun computeDailyHistory(now: Instant, days: Int): List<DayBucket> {
        require(days > 0) { "days must be positive" }
        val tz = TimeZone.currentSystemDefault()
        // `since` covers exactly `days` calendar days back from today —
        // subtracting `days - 1` keeps today in the window.
        val raw = readingEvents.dailyBuckets(
            ReadingEvent.Type.COMPLETED,
            now - (days - 1).days,
        )

        val today = now.toLocalDateTime(tz).date
        val labels = mutableListOf<String>()
        for (i in (days - 1) downTo 0) {
            val date = (now - i.days).toLocalDateTime(tz).date
            labels += "%04d-%02d-%02d".format(date.year, date.monthNumber, date.dayOfMonth)
        }
        return labels.map { DayBucket(it, raw[it] ?: 0) }
    }

    // --------------------------------------------------------- lifetime API

    /**
     * How many books this account has ever finished.
     *
     * Asking Komga is the honest answer and the expensive one: it is a count
     * over every book on the server, and it was measured at 5558 and 7474 ms —
     * 80 to 89% of the entire statistics card, for a number that changes by one
     * when you finish a volume.
     *
     * So the server is asked at most once per [BOOKS_BASELINE_TTL]. The answer
     * is stored with the moment it was given, and between two askings the total
     * is that baseline plus the COMPLETED events Kora has recorded since. Those
     * events are exactly the books you finished in Kora, so a volume closed a
     * minute ago is counted a minute ago — the number is live, not stale, and
     * this path costs one indexed local query instead of a full server count.
     *
     * What it cannot see is a book marked read somewhere else — the Komga web
     * UI, another client — since the last baseline. That drift is bounded by
     * the TTL, and corrects itself the next time the baseline is refreshed.
     *
     * A COMPLETED event landing in the very millisecond the baseline was taken
     * would be counted twice ([ReadingEventsRepository.countSince] is
     * inclusive). One millisecond wide, worth one book, self-correcting at the
     * next refresh: left alone rather than papered over.
     */
    private suspend fun lifetimeBooksFinished(api: KomgaApi, now: Instant): Int {
        val baseline = runCatching { readingEvents.getLifetimeBooksBaseline() }
            .onFailure { logger.warn(it) { "reading the lifetime books baseline failed" } }
            .getOrNull()

        suspend fun fromBaseline(known: LifetimeBooksBaseline): Int =
            known.count + runCatching {
                readingEvents.countSince(ReadingEvent.Type.COMPLETED, known.takenAt)
            }.getOrDefault(0)

        if (baseline != null && now - baseline.takenAt < BOOKS_BASELINE_TTL) {
            return fromBaseline(baseline)
        }

        val fromServer = trace("stats.api.lifetimeBooks") { fetchLifetimeBooksFinished(api) }
        // 0 is also what the fetch returns when it fails. A stale baseline beats
        // showing zero to someone who has finished hundreds of books.
        if (fromServer <= 0) return baseline?.let { fromBaseline(it) } ?: 0

        runCatching { readingEvents.upsertLifetimeBooksBaseline(fromServer, now) }
            .onFailure { logger.warn(it) { "storing the lifetime books baseline failed" } }
        return fromServer
    }

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
     * Number of libraries known to the Komga server. Exposed as
     * `librariesExplored` on [ReadingStats] for UI consumers that
     * want a quick scope count.
     */
    private fun fetchLibrariesCount(): Int = libraries.value.size

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
