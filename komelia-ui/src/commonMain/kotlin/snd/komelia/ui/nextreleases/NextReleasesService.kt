package snd.komelia.ui.nextreleases

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.LocalDate
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
    data class Scan(val releases: List<UpcomingRelease>, val complete: Boolean)

    /**
     * Scans every library for `nextrelease:*` tags and resolves each to a series.
     *
     * Throws if tag discovery fails: without the tag list there is nothing to
     * resolve, and reporting an empty calendar would be a lie. Individual series
     * lookups are allowed to fail — one bad tag shouldn't blank the calendar —
     * but any failure marks the scan incomplete.
     */
    suspend fun compute(libraries: List<KomgaLibrary>): Scan = coroutineScope {
        // Not wrapped in runCatching on purpose: a failure here must propagate.
        val tagsByLibrary = libraries.map { lib ->
            async { referentialApi.getSeriesTags(lib.id) }
        }.awaitAll()

        val candidates = tagsByLibrary.flatten().distinct()
            .mapNotNull { tag -> NextReleaseLabels.upcomingRelease(listOf(tag))?.let { tag to it } }

        // One server query per tag, so a well-tagged library used to fire
        // hundreds of requests at once — which is what made the whole scan time
        // out (and drained the battery) once the tag count grew. Cap the
        // in-flight count instead; the scan takes a little longer and survives.
        val limit = Semaphore(MAX_CONCURRENT_LOOKUPS)

        // Each lookup reports its own outcome rather than flipping a shared flag,
        // so no cross-coroutine mutable state is involved.
        val outcomes: List<Result<UpcomingRelease?>> = candidates.map { (tag, nextRelease) ->
            async {
                limit.withPermit {
                    runCatching {
                        seriesApi.getSeriesList(
                            KomgaSeriesSearch(
                                condition = allOfSeries { tag { isEqualTo(tag) } }.toSeriesCondition()
                            ),
                            KomgaPageRequest(pageIndex = 0, size = 1),
                        )
                    }.map { page ->
                        page.content.firstOrNull()?.let { series ->
                            UpcomingRelease(
                                seriesId = series.id,
                                seriesTitle = series.metadata.title,
                                libraryId = series.libraryId,
                                volume = nextRelease.volume,
                                date = nextRelease.date,
                            )
                        }
                    }
                }
            }
        }.awaitAll()

        Scan(
            releases = outcomes.mapNotNull { it.getOrNull() }.sortedBy { it.date },
            complete = outcomes.none { it.isFailure },
        )
    }

    private companion object {
        /** In-flight series lookups. Low enough that a big tag set can't stampede Komga. */
        const val MAX_CONCURRENT_LOOKUPS = 4
    }
}
