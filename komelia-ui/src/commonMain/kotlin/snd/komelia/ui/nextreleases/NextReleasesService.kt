package snd.komelia.ui.nextreleases

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    suspend fun compute(libraries: List<KomgaLibrary>): List<UpcomingRelease> = coroutineScope {
        val tagsByLibrary = libraries.map { lib ->
            async { runCatching { referentialApi.getSeriesTags(lib.id) }.getOrDefault(emptyList()) }
        }.awaitAll()

        val candidates = tagsByLibrary.flatten().distinct()
            .mapNotNull { tag -> NextReleaseLabels.upcomingRelease(listOf(tag))?.let { tag to it } }

        candidates.map { (tag, nextRelease) ->
            async {
                val page = runCatching {
                    seriesApi.getSeriesList(
                        KomgaSeriesSearch(
                            condition = allOfSeries { tag { isEqualTo(tag) } }.toSeriesCondition()
                        ),
                        KomgaPageRequest(pageIndex = 0, size = 1),
                    )
                }.getOrNull()
                page?.content?.firstOrNull()?.let { series ->
                    UpcomingRelease(
                        seriesId = series.id,
                        seriesTitle = series.metadata.title,
                        libraryId = series.libraryId,
                        volume = nextRelease.volume,
                        date = nextRelease.date,
                    )
                }
            }
        }.awaitAll().filterNotNull().sortedBy { it.date }
    }
}
