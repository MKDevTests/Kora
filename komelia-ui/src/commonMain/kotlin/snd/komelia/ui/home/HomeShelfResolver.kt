package snd.komelia.ui.home

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import snd.komelia.perf.PerfTrace
import snd.komelia.homefilters.BooksHomeScreenFilter
import snd.komelia.homefilters.HomeScreenFilter
import snd.komelia.homefilters.SeriesHomeScreenFilter
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.allOfBooks
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.series.KomgaSeriesSearch

/**
 * Resolves a [HomeScreenFilter] into the actual series/books it stands for.
 *
 * Extracted out of HomeViewModel so the shelf-detail screen can reuse the exact
 * same queries instead of duplicating them. Only three dependencies, none of
 * them stateful beyond the favorite ids, so the resolver is cheap to build
 * anywhere a viewmodel can reach the Komga APIs.
 *
 * [favoriteIds] is a lambda rather than a value because the local favorites are
 * a live setting — reading it at resolve time keeps a long-lived resolver from
 * serving a stale set.
 */
class HomeShelfResolver(
    private val seriesApi: KomgaSeriesApi,
    private val bookApi: KomgaBookApi,
    private val favoriteIds: () -> Set<String>,
    private val excludedLibraryIds: () -> Set<String> = { emptySet() },
    /** Same pipeline as the library's "For you" tab; null on platforms without it. */
    private val forYouSuggester: snd.komelia.ui.suggestions.ForYouSuggester? = null,
    /**
     * Libraries the "For you" shelf can draw from. Suspending on purpose: the
     * list arrives from the server after the first emission, and Home resolves
     * its shelves immediately at startup — reading a seeded StateFlow value
     * returned nothing and left the shelf empty.
     */
    private val allLibraryIds: suspend () -> List<String> = { emptyList() },
) {

    suspend fun resolve(filter: HomeScreenFilter): HomeFilterData? =
        PerfTrace.measure("home.shelf '${filter.label}'", { data ->
            when (data) {
                is SeriesFilterData -> data.series.size
                is BookFilterData -> data.books.size
                else -> null
            }
        }) { resolveInner(filter) }

    private suspend fun resolveInner(filter: HomeScreenFilter): HomeFilterData? {
        return when (filter) {
            is BooksHomeScreenFilter.CustomFilter -> {
                val books = bookApi.getBookList(
                    search = KomgaBookSearch(filter.filter, filter.textSearch),
                    pageRequest = filter.pageRequest
                ).content

                BookFilterData(books = books, filter = filter)
            }

            is BooksHomeScreenFilter.OnDeck -> {
                val books = bookApi.getBooksOnDeck(pageRequest = KomgaPageRequest(size = filter.pageSize)).content
                BookFilterData(books, filter)
            }

            is SeriesHomeScreenFilter.CustomFilter -> {
                val series = seriesApi.getSeriesList(
                    search = KomgaSeriesSearch(filter.filter, filter.textSearch),
                    pageRequest = filter.pageRequest
                ).content

                SeriesFilterData(series = series, filter = filter)
            }

            is SeriesHomeScreenFilter.RecentlyAdded -> {
                val series = seriesApi.getNewSeries(
                    oneshot = false,
                    pageRequest = KomgaPageRequest(size = filter.pageSize)
                ).content
                SeriesFilterData(
                    series = series,
                    filter = filter
                )
            }

            is SeriesHomeScreenFilter.RecentlyUpdated -> {
                val series = seriesApi.getUpdatedSeries(
                    oneshot = false,
                    pageRequest = KomgaPageRequest(size = filter.pageSize)
                ).content
                SeriesFilterData(
                    series = series,
                    filter = filter
                )
            }

            is SeriesHomeScreenFilter.ForYou -> {
                val suggester = forYouSuggester
                val libraries = allLibraryIds().filterNot { it in filter.excludedLibraryIds }
                if (suggester == null || libraries.isEmpty()) SeriesFilterData(emptyList(), filter)
                else {
                    // Each library is scored on its own index — cosine scores
                    // are not comparable between two different vocabularies —
                    // so the lists are interleaved rather than merged by score.
                    // Otherwise the library with the denser tags would take the
                    // whole shelf.
                    //
                    // No index building from Home: that is a one-off burst of
                    // requests the user did not ask for by scrolling past a
                    // shelf. A library stays out until its own tab indexed once.
                    val perLibrary = libraries.map { id ->
                        suggester.suggest(
                            libraryId = KomgaLibraryId(id),
                            limit = filter.pageSize,
                            indexIfMissing = false,
                        ).results
                    }.filter { it.isNotEmpty() }
                    val interleaved = buildList {
                        var index = 0
                        while (size < filter.pageSize && perLibrary.any { index < it.size }) {
                            perLibrary.forEach { list ->
                                if (size < filter.pageSize && index < list.size) add(list[index].series)
                            }
                            index++
                        }
                    }
                    SeriesFilterData(series = interleaved, filter = filter)
                }
            }

            is SeriesHomeScreenFilter.Favorites -> {
                // Local favorites: resolve a bounded sample by id (getOneSeries is
                // not filtered), sorted by title. Resolve concurrently — one call
                // at a time meant pageSize round-trips in series on the home
                // screen. Bounded to four in flight for the same reason as the
                // favorites screen: unbounded fan-out saturates Komga's pool.
                val limit = Semaphore(4)
                val resolved = coroutineScope {
                    favoriteIds().take(filter.pageSize).map { id ->
                        async {
                            limit.withPermit {
                                runCatching { seriesApi.getOneSeries(KomgaSeriesId(id)) }.getOrNull()
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
                    // Filter on the RESOLVED library, not the id->library cache:
                    // a just-added favorite isn't in that cache yet, so it would
                    // slip onto the shelf until some other screen resolved it.
                    .filter { it.libraryId.value !in excludedLibraryIds() }
                    .sortedBy { it.metadata.title.lowercase() }
                SeriesFilterData(series = resolved, filter = filter)
            }

            is BooksHomeScreenFilter.ForgottenBooks -> {
                // Mirror of "Keep reading": same IN_PROGRESS query, but
                // sorted ASCENDING by read date so the oldest activity
                // surfaces first. The label is what the user named the
                // shelf in the home config — defaults to "Forgotten".
                //
                // Library exclusions are applied server-side via repeated
                // `library { isNotEqualTo(...) }` AND-ed conditions —
                // cheaper than fetching everything and filtering in
                // Kotlin.
                val excludedIds = filter.excludedLibraryIds
                val books = bookApi.getBookList(
                    search = KomgaBookSearch(
                        allOfBooks {
                            readStatus { isEqualTo(KomgaReadStatus.IN_PROGRESS) }
                            excludedIds.forEach { libId ->
                                library { isNotEqualTo(KomgaLibraryId(libId)) }
                            }
                        }.toBookCondition()
                    ),
                    pageRequest = KomgaPageRequest(
                        sort = KomgaSort.KomgaBooksSort.byReadDate(KomgaSort.Direction.ASC),
                        size = filter.pageSize,
                    ),
                ).content
                BookFilterData(books, filter)
            }

            is SeriesHomeScreenFilter.AlmostFinished -> {
                // Komga doesn't expose a server-side filter on the
                // booksRead / total ratio. Pull a wider window of
                // IN_PROGRESS series and filter client-side. Cap the
                // window at 5x the requested pageSize so big libraries
                // don't pull thousands of series for a 20-item shelf.
                // Library exclusions go server-side via the search DSL.
                val excludedIds = filter.excludedLibraryIds
                val poolSize = (filter.pageSize * 5).coerceAtMost(100)
                val pool = seriesApi.getSeriesList(
                    search = KomgaSeriesSearch(
                        allOfSeries {
                            readStatus { isEqualTo(KomgaReadStatus.IN_PROGRESS) }
                            excludedIds.forEach { libId ->
                                library { isNotEqualTo(KomgaLibraryId(libId)) }
                            }
                        }.toSeriesCondition()
                    ),
                    pageRequest = KomgaPageRequest(size = poolSize),
                ).content
                val threshold = filter.progressThresholdPercent / 100f
                val almost = pool
                    .mapNotNull { series ->
                        val total = series.booksCount
                        if (total <= 0) return@mapNotNull null
                        val ratio = series.booksReadCount.toFloat() / total
                        if (ratio < threshold) null else series to ratio
                    }
                    .sortedByDescending { it.second }
                    .take(filter.pageSize)
                    .map { it.first }
                SeriesFilterData(series = almost, filter = filter)
            }
        }
    }
}

/**
 * Returns a copy of the filter asking the server for [size] items instead of
 * the shelf-sized handful. Every variant carries its own page-size field (a
 * plain `pageSize`, or the `size` inside a [KomgaPageRequest]), so widening a
 * shelf into a full screen needs no new query — just a wider filter.
 */
fun HomeScreenFilter.withPageSize(size: Int): HomeScreenFilter = when (this) {
    is SeriesHomeScreenFilter.RecentlyAdded -> copy(pageSize = size)
    is SeriesHomeScreenFilter.RecentlyUpdated -> copy(pageSize = size)
    is SeriesHomeScreenFilter.AlmostFinished -> copy(pageSize = size)
    is SeriesHomeScreenFilter.Favorites -> copy(pageSize = size)
    is SeriesHomeScreenFilter.ForYou -> copy(pageSize = size)
    is SeriesHomeScreenFilter.CustomFilter ->
        copy(pageRequest = (pageRequest ?: KomgaPageRequest()).copy(size = size))

    is BooksHomeScreenFilter.OnDeck -> copy(pageSize = size)
    is BooksHomeScreenFilter.ForgottenBooks -> copy(pageSize = size)
    is BooksHomeScreenFilter.CustomFilter ->
        copy(pageRequest = (pageRequest ?: KomgaPageRequest()).copy(size = size))
}
