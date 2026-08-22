package snd.komelia.ui.nextreleases

import snd.komelia.perf.PerfTrace
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import snd.komelia.komga.api.KomgaReferentialApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.ui.library.NextReleaseLabels
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.series.KomgaSeriesSearch

/**
 * Cross-library "upcoming releases" calendar built entirely from the user's
 * external classifier's `nextrelease:<volume>-<dd.mm.yyyy>` series tags (see
 * [NextReleaseLabels]) — no server-side concept of a release date exists in
 * Komga, this is purely a local convention Kora reads.
 *
 * Discovery mirrors the Genre tab: [KomgaReferentialApi.getSeriesTags] lists a
 * library's distinct tags in one cheap call, so scanning every library for
 * `nextrelease:*` values costs one call per library, not one per series. Each
 * matching tag value already encodes a specific volume+date, so it maps to
 * exactly one series; that series is resolved with a page-size-1 tag search.
 */
class NextReleasesService(
    private val seriesApi: KomgaSeriesApi,
    private val referentialApi: KomgaReferentialApi,
) {
    /**
     * Deliberately NOT a full KomgaSeries: keeping this lightweight lets the
     * whole list be persisted to disk cheaply (see [NextReleasesCache]) and
     * [snd.komelia.ui.common.images.SeriesThumbnail] / navigation
     * ([snd.komelia.ui.series.SeriesScreen] takes just an id) both work
     * from the id alone, no re-fetch needed to build or render this list.
     */
    data class UpcomingRelease(
        val seriesId: KomgaSeriesId,
        val seriesTitle: String,
        val libraryId: KomgaLibraryId,
        val volume: String,
        val date: LocalDate,
    )

    /**
     * Outcome of a scan. [complete] is false when at least one query failed, so
     * callers can tell "you genuinely have no upcoming release" apart from "the
     * server didn't answer" — an empty list used to mean both, and the caller
     * would happily persist the empty one over a perfectly good cache.
     */
    data class Scan(
        val releases: List<UpcomingRelease>,
        val complete: Boolean,
        /**
         * How many tag lookups were attempted, and how many answered.
         *
         * [complete] demands a clean sweep, which a scan of this size does not
         * get: measured on the tablet 2026-08-21, 179 lookups ran and 178
         * answered -- the one failure came from the server starting to return
         * 5xx under the load of this very scan. Requiring perfection there
         * means the scan is never recorded as done and re-runs on every launch,
         * saturating the server again: it feeds itself.
         *
         * The ratio lets a caller say "enough of it worked to be worth
         * keeping" without losing the distinction [complete] exists for.
         * Free to compute -- both numbers are already in hand.
         */
        val attempted: Int = 0,
        val resolved: Int = 0,
        /**
         * `nextrelease:*` tags whose date is already past. Collected for free
         * during discovery (no extra server call) so the admin maintenance
         * screen can offer to purge them — the user tags by hand in Komga and
         * expired tags otherwise pile up silently.
         */
        val expiredTags: List<String> = emptyList(),
    )

    /**
     * Discovery ONLY: one getSeriesTags call per library, no series resolution.
     * This is what the admin Maintenance screen needs — [compute] additionally
     * resolves every FUTURE tag to build the calendar (one query per tag), which
     * on a slow server takes minutes and is pure waste when all we want is the
     * expired list. Libraries that fail contribute nothing; throws only if every
     * library failed.
     */
    suspend fun findExpiredTags(libraries: List<KomgaLibrary>): List<String> = coroutineScope {
        val tagResults = libraries.map { lib ->
            async { runCatching { referentialApi.getSeriesTags(lib.id) } }
        }.awaitAll()
        if (libraries.isNotEmpty() && tagResults.all { it.isFailure }) {
            throw tagResults.first().exceptionOrNull()
                ?: IllegalStateException("tag discovery failed for every library")
        }
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        tagResults.mapNotNull { it.getOrNull() }.flatten().distinct()
            .mapNotNull { tag -> NextReleaseLabels.parseTag(tag)?.let { tag to it } }
            .filter { (_, release) -> release.date < today }
            .map { it.first }
    }

    /**
     * Scans every library for `nextrelease:*` tags and resolves each to a series.
     *
     * Throws if tag discovery fails: without the tag list there is nothing to
     * resolve, and reporting an empty calendar would be a lie. Individual series
     * lookups are allowed to fail — one bad tag shouldn't blank the calendar —
     * but any failure marks the scan incomplete.
     */
    suspend fun compute(libraries: List<KomgaLibrary>): Scan = coroutineScope {
        // Per-library tolerance: `/api/v1/tags/series` can take longer than the
        // 30s socket timeout on a large library, and one slow library must not
        // cost the whole calendar. A library that fails contributes nothing and
        // marks the scan incomplete — which is what stops the (partial) result
        // from overwriting a better cached one.
        val tagResults = libraries.map { lib ->
            async { runCatching { referentialApi.getSeriesTags(lib.id) } }
        }.awaitAll()

        // Every library failing means we learned nothing at all — that is a
        // failure, not an empty calendar.
        if (libraries.isNotEmpty() && tagResults.all { it.isFailure }) {
            throw tagResults.first().exceptionOrNull()
                ?: IllegalStateException("tag discovery failed for every library")
        }
        val discoveryComplete = tagResults.none { it.isFailure }
        // Kept per library, not flattened: the resolution below asks each
        // library for its OWN tags in one query, and flattening would lose
        // which library to ask.
        val tagsByLibrary = libraries.zip(tagResults).mapNotNull { (library, result) ->
            result.getOrNull()?.let { library to it }
        }

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val allParsed = tagsByLibrary.flatMap { it.second }.distinct()
            .mapNotNull { tag -> NextReleaseLabels.parseTag(tag)?.let { tag to it } }
        val (candidates, expired) = allParsed.partition { (_, release) -> release.date >= today }
        val futureTags = candidates.map { it.first }.toSet()

        // ONE query per library, asking for every one of its future tags at
        // once, instead of one query per tag.
        //
        // Measured on the tablet 2026-08-21, the per-tag version: 179 count
        // queries, ~3s of server each, four at a time. Nearly five minutes
        // during which the server answered everything else badly or not at all
        // -- 18 connection failures, and a book list that curl gets in 1 154ms
        // took 10 414ms in the app. Worse, five minutes is longer than a
        // tablet's screen timeout: the device dozed at 00:28 and the pending
        // requests died on wake with total=1000006ms, so the scan never
        // finished, was never recorded as done, and started over on the next
        // launch. It fed itself.
        //
        // The reversal that makes one query enough: a series comes back
        // carrying its own tags, so instead of asking the server which series
        // holds each tag, we ask once for the series holding any of them and
        // read the tag off each one locally. A series carrying several
        // nextrelease tags was queried once per tag before, and is now
        // returned once.
        //
        // `tag` only accepts equality (KomgaSearchCondition.Tag takes an
        // EqualityNullable), so this cannot be a single "begins with
        // nextrelease:" query -- hence the anyOf over the known tags, the same
        // shape the book filter already uses.
        val limit = Semaphore(MAX_CONCURRENT_LOOKUPS)
        val outcomes: List<Result<List<UpcomingRelease>>> = tagsByLibrary.mapNotNull { (library, tags) ->
            val libraryTags = tags.filter { it in futureTags }
            if (libraryTags.isEmpty()) null
            else async {
                limit.withPermit {
                    runCatching {
                        PerfTrace.measure(
                            label = "nextreleases.library tags=${libraryTags.size}",
                            count = { it: List<UpcomingRelease> -> it.size },
                        ) { resolveLibrary(library.id, libraryTags, today) }
                    }
                }
            }
        }.awaitAll()

        Scan(
            releases = outcomes.mapNotNull { it.getOrNull() }.flatten().sortedBy { it.date },
            complete = discoveryComplete && outcomes.none { it.isFailure },
            attempted = outcomes.size,
            resolved = outcomes.count { it.isSuccess },
            expiredTags = expired.map { it.first },
        )
    }

    /**
     * Every series in [libraryId] carrying one of [tags], turned into the
     * releases those tags announce.
     *
     * Paginated rather than asked for with one large size: a well-tagged
     * library can hold hundreds of these, and a size chosen large enough to
     * "never paginate" is the shortcut that made the collections tab
     * expensive. [PAGE_SIZE] pages are the unit of work; the loop stops on the
     * page the server marks last.
     */
    private suspend fun resolveLibrary(
        libraryId: KomgaLibraryId,
        tags: List<String>,
        today: LocalDate,
    ): List<UpcomingRelease> =
        // In chunks, because one query for all of them is a query the server
        // will not answer. Measured 2026-08-21: this library holds 174 future
        // tags, and asking for all 174 in a single anyOf timed out at 60s
        // without a byte of response -- the per-tag storm was replaced by one
        // query that never returns, which is not an improvement.
        //
        // Distinct at the end: a series carrying tags from two different
        // chunks comes back in both, and each time it yields all of its
        // releases. UpcomingRelease is a data class, so the duplicates are
        // equal and collapse.
        tags.chunked(TAGS_PER_QUERY)
            .flatMap { chunk -> resolveChunk(libraryId, chunk, today) }
            .distinct()

    /** One page-walk over the series carrying any of [tags]. */
    private suspend fun resolveChunk(
        libraryId: KomgaLibraryId,
        tags: List<String>,
        today: LocalDate,
    ): List<UpcomingRelease> {
        val condition = allOfSeries {
            library { isEqualTo(libraryId) }
            anyOf { tags.forEach { tag { isEqualTo(it) } } }
        }.toSeriesCondition()

        val releases = mutableListOf<UpcomingRelease>()
        var pageIndex = 0
        while (true) {
            val page = seriesApi.getSeriesList(
                KomgaSeriesSearch(condition = condition),
                KomgaPageRequest(pageIndex = pageIndex, size = PAGE_SIZE),
            )
            page.content.forEach { series ->
                // The tag is read off the series we already have, not asked for
                // again. A series announcing two volumes yields two entries.
                series.metadata.tags
                    .mapNotNull { tag -> NextReleaseLabels.parseTag(tag) }
                    .filter { it.date >= today }
                    .forEach { release ->
                        releases += UpcomingRelease(
                            seriesId = series.id,
                            seriesTitle = series.metadata.title,
                            libraryId = series.libraryId,
                            volume = release.volume,
                            date = release.date,
                        )
                    }
            }
            if (page.last || page.content.isEmpty()) break
            pageIndex++
        }
        return releases
    }

    private companion object {
        /** In-flight series lookups. Low enough that a big tag set can't stampede Komga. */
        const val MAX_CONCURRENT_LOOKUPS = 4

        /** Series per page when resolving a library's nextrelease tags. */
        const val PAGE_SIZE = 200

        /**
         * Tags per query. 174 in one anyOf never answered; this many keeps each
         * query small while still turning 179 requests into single digits.
         */
        const val TAGS_PER_QUERY = 20
    }
}
