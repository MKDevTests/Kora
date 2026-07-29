package snd.komelia.similarity

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import snd.komelia.komga.api.KomgaApi
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort.KomgaSeriesSort
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId
import kotlin.time.Clock

private val logger = KotlinLogging.logger {}

/**
 * Builds and maintains the local term index one library at a time.
 *
 * The whole feature hangs on one property of the Komga API: the **paginated
 * series list already carries the metadata** (tags, publisher, and the
 * `booksMetadata` aggregate holding the authors). Indexing a library is
 * therefore `N / PAGE_SIZE` requests — about 30 for 3000 series — not one
 * request per series, which is what would have made this unaffordable.
 *
 * Two rules keep it safe on a tablet:
 *
 *  - **A page is turned into terms and dropped immediately.** `SeriesDto`
 *    carries two summaries (series and books), which are the bulk of the
 *    payload; holding a few thousand of them is a real memory spike, and we
 *    score none of it.
 *  - **At most [MAX_CONCURRENT_PAGES] requests in flight.** Unbounded bursts
 *    against Komga's connection pool are what made other screens feel slow.
 *
 * Hidden and ignored series are indexed like any other: they are excluded when
 * results are read, so hiding or unhiding a series doesn't invalidate the index.
 */
class SimilarityIndexBuilder(
    private val rawApi: StateFlow<KomgaApi>,
    private val repository: SimilarityIndexRepository,
) {

    /**
     * Rebuilds [libraryId] from the server and returns the resulting state.
     *
     * [onProgress] is called with (indexed so far, total) so a settings screen
     * can show a bar; it fires per page, not per series.
     *
     * Rows are written first and the leftovers deleted after, so an interrupted
     * build leaves a stale-but-usable index instead of an empty one.
     */
    suspend fun build(
        libraryId: KomgaLibraryId,
        onProgress: (indexed: Int, total: Int) -> Unit = { _, _ -> },
    ): SimilarityIndexState {
        val seriesApi = rawApi.value.seriesApi
        val condition = allOfSeries { library { isEqualTo(libraryId) } }

        // First page does double duty: it tells us how many there are.
        val firstPage = seriesApi.getSeriesList(
            conditionBuilder = condition,
            fulltextSearch = null,
            pageRequest = pageRequest(0),
        )
        val total = firstPage.totalElements
        val entries = ArrayList<SimilarityIndexEntry>(total.coerceAtLeast(firstPage.content.size))
        entries += firstPage.content.map { it.toEntry() }
        onProgress(entries.size, total)

        val remainingPages = (1 until firstPage.totalPages)
        if (!remainingPages.isEmpty()) {
            val limit = Semaphore(MAX_CONCURRENT_PAGES)
            coroutineScope {
                remainingPages.map { pageIndex ->
                    async {
                        limit.withPermit {
                            seriesApi.getSeriesList(
                                conditionBuilder = condition,
                                fulltextSearch = null,
                                pageRequest = pageRequest(pageIndex),
                            ).content.map { it.toEntry() }
                        }
                    }
                }.awaitAll().forEach { pageEntries ->
                    entries += pageEntries
                    onProgress(entries.size, total)
                }
            }
        }

        repository.upsertAll(entries)

        // Series deleted on the server (or moved to another library) would
        // otherwise linger and keep being suggested.
        val indexed = entries.mapTo(HashSet(entries.size)) { it.seriesId }
        val stale = repository.entriesOf(libraryId.value)
            .map { it.seriesId }
            .filterNot { it in indexed }
        repository.deleteSeries(stale)

        val state = SimilarityIndexState(
            libraryId = libraryId.value,
            builtAt = Clock.System.now(),
            seriesCount = entries.size,
        )
        repository.putState(state)
        logger.info { "Similarity index built for ${libraryId.value}: ${entries.size} series, ${stale.size} dropped" }
        return state
    }

    /**
     * Re-reads [seriesIds] one by one and updates their rows — the incremental
     * path, driven by the Komga change events. Cheap enough to run on a handful
     * of ids; a large batch means the library changed a lot, and [build] is the
     * right answer there.
     *
     * Best-effort per series: a failed lookup leaves the old row in place rather
     * than dropping the series out of the index.
     */
    suspend fun refreshSeries(seriesIds: Collection<KomgaSeriesId>) {
        if (seriesIds.isEmpty()) return
        val seriesApi = rawApi.value.seriesApi
        val limit = Semaphore(MAX_CONCURRENT_PAGES)
        val updated = coroutineScope {
            seriesIds.distinct().map { id ->
                async {
                    limit.withPermit {
                        try {
                            seriesApi.getOneSeries(id).toEntry()
                        } catch (t: Throwable) {
                            currentCoroutineContext().ensureActive()
                            logger.debug { "Similarity index refresh skipped ${id.value}: ${t::class.simpleName}" }
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        repository.upsertAll(updated)
    }

    /** Drops series the server no longer has. */
    suspend fun removeSeries(seriesIds: Collection<KomgaSeriesId>) {
        repository.deleteSeries(seriesIds.map { it.value })
    }

    private fun pageRequest(pageIndex: Int) = KomgaPageRequest(
        size = PAGE_SIZE,
        pageIndex = pageIndex,
        // Page offsets are only meaningful under a stable order, and pages are
        // fetched concurrently here — an unsorted list could hand us the same
        // series twice and miss another.
        sort = KomgaSeriesSort.byTitleAsc(),
    )
}

private fun KomgaSeries.toEntry() = SimilarityIndexEntry(
    seriesId = id.value,
    libraryId = libraryId.value,
    titleSort = metadata.titleSort,
    terms = toSimilarityTerms(),
)

/** 100 is Komga's comfortable page size; larger pages mostly ship more summary. */
private const val PAGE_SIZE = 100

/** Same ceiling as the other bulk paths — four in flight, never a burst. */
private const val MAX_CONCURRENT_PAGES = 4
