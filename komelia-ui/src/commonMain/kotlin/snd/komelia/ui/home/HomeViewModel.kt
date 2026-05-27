package snd.komelia.ui.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import snd.komelia.AppNotifications
import snd.komelia.homefilters.BooksHomeScreenFilter
import snd.komelia.homefilters.HomeScreenFilter
import snd.komelia.homefilters.HomeScreenFilterRepository
import snd.komelia.homefilters.SeriesHomeScreenFilter
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.offline.tasks.OfflineTaskEmitter
import snd.komelia.ui.LoadState
import snd.komelia.ui.LoadState.Uninitialized
import snd.komelia.ui.common.cards.defaultCardWidth
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.menus.SeriesMenuActions
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort
import snd.komga.client.common.KomgaSort.KomgaBooksSort
import snd.komga.client.common.KomgaSort.KomgaSeriesSort
import snd.komga.client.search.allOfBooks
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeriesSearch
import snd.komga.client.sse.KomgaEvent
import snd.komga.client.sse.KomgaEvent.BookEvent
import snd.komga.client.sse.KomgaEvent.ReadProgressEvent
import snd.komga.client.sse.KomgaEvent.ReadProgressSeriesEvent
import snd.komga.client.sse.KomgaEvent.SeriesEvent

private val logger = KotlinLogging.logger { }

/**
 * Process-wide cache of random-sort shelf results. Lives outside
 * [HomeViewModel] so that voyager's [rememberScreenModel] recreating
 * the viewmodel on navigation (Home → Library → Home) doesn't drop
 * the cache and refetch random shelves on every return.
 *
 * Keyed by a stable String derived from the filter (`order + label`)
 * rather than the [HomeScreenFilter] object itself: KomgaPageRequest /
 * KomgaSearchCondition come from the external komga-client library and
 * don't necessarily implement content-based equals(), so two filter
 * instances loaded from storage at different times can fail to hash as
 * equal even though the user thinks of them as "the same shelf". The
 * String key sidesteps that and survives storage round-trips intact.
 *
 * Cleared only on process restart.
 */
private object RandomShelfCache {
    private val cache = mutableMapOf<String, Entry>()
    private val ttl = 5.minutes

    fun get(key: String): HomeFilterData? {
        val entry = cache[key] ?: return null
        if ((Clock.System.now() - entry.fetchedAt) >= ttl) return null
        return entry.data
    }

    fun put(key: String, data: HomeFilterData) {
        cache[key] = Entry(data, Clock.System.now())
    }

    private data class Entry(val data: HomeFilterData, val fetchedAt: Instant)
}

class HomeViewModel(
    private val seriesApi: KomgaSeriesApi,
    val bookApi: KomgaBookApi,
    private val appNotifications: AppNotifications,
    private val komgaEvents: SharedFlow<KomgaEvent>,
    private val filterRepository: HomeScreenFilterRepository,
    private val taskEmitter: OfflineTaskEmitter,
    cardWidthFlow: Flow<Dp>,
) : StateScreenModel<LoadState<Unit>>(Uninitialized) {
    val cardWidth = cardWidthFlow.stateIn(screenModelScope, Eagerly, defaultCardWidth.dp)

    private val reloadEventsEnabled = MutableStateFlow(true)
    private val reloadJobsFlow = MutableSharedFlow<Unit>(1, 0, DROP_OLDEST)

    val currentFilters = MutableStateFlow(emptyList<HomeFilterData>())
    val activeFilterNumber = MutableStateFlow(0)

    // Random-shelf cache lives in the file-scope [RandomShelfCache]
    // object above so it survives viewmodel recreation. See the docs there.

    suspend fun initialize() {
        if (state.value !is Uninitialized) return

        load()
        startKomgaEventListener()

        reloadJobsFlow.onEach {
            reloadEventsEnabled.first { it }
            load()
            delay(5000)
        }.launchIn(screenModelScope)
    }

    /** Manual reload entry point (pull-to-refresh, etc.). Bypasses the
     *  random-shelf cache so the user always gets a fresh permutation. */
    fun reload() {
        screenModelScope.launch { load(force = true) }
    }

    private suspend fun load(force: Boolean = false) {
        appNotifications.runCatchingToNotifications {
            mutableState.value = LoadState.Loading

            currentFilters.value = filterRepository.getFilters().first()
                .map { screenModelScope.async { fetchFilterData(it, force) } }
                .awaitAll()
                .filterNotNull()

            mutableState.value = LoadState.Success(Unit)
        }.onFailure { mutableState.value = LoadState.Error(it) }
    }

    private suspend fun fetchFilterData(filter: HomeScreenFilter, force: Boolean): HomeFilterData? {
        // Random shelves: serve from the process-wide cache while fresh.
        // Event-driven reloads (Komga SSE) and viewmodel recreation on
        // Home/Library navigation both honor the cache; only force=true
        // (manual pull-to-refresh) bypasses it.
        val cacheKey = filter.randomShelfCacheKey()
        if (!force && cacheKey != null) {
            val cached = RandomShelfCache.get(cacheKey)
            if (cached != null) {
                logger.info { "RandomShelfCache HIT for $cacheKey (force=$force)" }
                return cached
            }
            logger.info { "RandomShelfCache MISS for $cacheKey (force=$force)" }
        } else if (cacheKey != null) {
            logger.info { "RandomShelfCache BYPASS for $cacheKey (force=true)" }
        }
        val fresh = fetchFilterDataFromServer(filter) ?: return null
        if (cacheKey != null) {
            RandomShelfCache.put(cacheKey, fresh)
            logger.info { "RandomShelfCache STORED $cacheKey" }
        }
        return fresh
    }

    /**
     * Returns a stable cache key for random-sort filters, or null when
     * [filter] isn't randomly sorted (so caching doesn't apply).
     *
     * Detection inspects [KomgaSort.Order.property] directly — the
     * earlier `.toString().contains("random")` approach didn't work
     * because KomgaSort's external types don't override toString(),
     * so the random shelf was never recognized and the cache stayed
     * empty.
     *
     * The key composes [HomeScreenFilter.order] and
     * [HomeScreenFilter.label]: both are scalar fields owned by Kora,
     * so two filter instances loaded from storage at different times
     * hash to the same key even if their nested KomgaPageRequest /
     * KomgaSearchCondition instances aren't `equals()`-comparable.
     */
    private fun HomeScreenFilter.randomShelfCacheKey(): String? {
        val sort = when (this) {
            is SeriesHomeScreenFilter.CustomFilter -> pageRequest?.sort
            is BooksHomeScreenFilter.CustomFilter -> pageRequest?.sort
            else -> null
        }
        val orders: List<KomgaSort.Order> = when (sort) {
            is KomgaSeriesSort -> sort.orders
            is KomgaBooksSort -> sort.orders
            else -> emptyList()
        }
        val isRandom = orders.any { it.property.equals("random", ignoreCase = true) }
        if (!isRandom) return null
        return "$order:$label"
    }

    private suspend fun fetchFilterDataFromServer(filter: HomeScreenFilter): HomeFilterData? {
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
                                library { isNotEqualTo(snd.komga.client.library.KomgaLibraryId(libId)) }
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
                                library { isNotEqualTo(snd.komga.client.library.KomgaLibraryId(libId)) }
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

    fun seriesMenuActions() = SeriesMenuActions(seriesApi, appNotifications, taskEmitter, screenModelScope)
    fun bookMenuActions() = BookMenuActions(bookApi, appNotifications, screenModelScope, taskEmitter)

    fun stopKomgaEventsHandler() {
        reloadEventsEnabled.value = false
    }

    fun startKomgaEventsHandler() {
        reloadEventsEnabled.value = true
    }

    private fun startKomgaEventListener() {
        komgaEvents.onEach { event ->
            when (event) {
                is BookEvent,
                is SeriesEvent,
                is ReadProgressEvent,
                is ReadProgressSeriesEvent -> reloadJobsFlow.tryEmit(Unit)

                else -> {}
            }
        }.launchIn(screenModelScope)
    }

    fun onFilterChange(number: Int) {
        this.activeFilterNumber.value = number
    }

}
