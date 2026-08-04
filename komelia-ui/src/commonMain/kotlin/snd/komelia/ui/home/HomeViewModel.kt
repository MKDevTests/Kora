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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import snd.komga.client.common.KomgaSort
import snd.komga.client.common.KomgaSort.KomgaBooksSort
import snd.komga.client.common.KomgaSort.KomgaSeriesSort
import snd.komga.client.series.KomgaSeries
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
    favoriteIdsFlow: Flow<Set<String>>,
    excludedLibraryIdsFlow: Flow<Set<String>> = flowOf(emptySet()),
    /** Same pipeline as the library "For you" tab; null where it isn't available. */
    private val forYouSuggester: snd.komelia.ui.suggestions.ForYouSuggester? = null,
    lastSelectedLibraryIdFlow: Flow<String?> = flowOf(null),
) : StateScreenModel<LoadState<Unit>>(Uninitialized) {
    val cardWidth = cardWidthFlow.stateIn(screenModelScope, Eagerly, defaultCardWidth.dp)
    private val favoriteIds = favoriteIdsFlow.stateIn(screenModelScope, Eagerly, emptySet())
    private val excludedLibraryIds = excludedLibraryIdsFlow.stateIn(screenModelScope, Eagerly, emptySet())
    private val lastSelectedLibraryId = lastSelectedLibraryIdFlow.stateIn(screenModelScope, Eagerly, null)
    private val shelfResolver = HomeShelfResolver(
        seriesApi = seriesApi,
        bookApi = bookApi,
        favoriteIds = { favoriteIds.value },
        excludedLibraryIds = { excludedLibraryIds.value },
        forYouSuggester = forYouSuggester,
        lastSelectedLibraryId = { lastSelectedLibraryId.value },
    )

    private val reloadEventsEnabled = MutableStateFlow(true)
    private val reloadJobsFlow = MutableSharedFlow<Unit>(1, 0, DROP_OLDEST)

    val currentFilters = MutableStateFlow(emptyList<HomeFilterData>())
    val activeFilterNumber = MutableStateFlow(0)

    // Multi-selection across the Home series shelves (long-press -> Select).
    val isInEditMode = MutableStateFlow(false)
    val selectedSeries = MutableStateFlow<List<KomgaSeries>>(emptyList())

    // Random-shelf cache lives in the file-scope [RandomShelfCache]
    // object above so it survives viewmodel recreation. See the docs there.

    suspend fun initialize() {
        if (state.value !is Uninitialized) return

        load()
        startKomgaEventListener()

        // The Favorites shelf reads the favorite ids at resolve time, so it used
        // to depend on the global screen-reload that favoriting broadcast. That
        // broadcast is gone (it also re-rolled randomly-sorted library listings
        // for nothing), so this shelf now watches the ids itself — and only
        // bothers when such a shelf is actually on screen.
        favoriteIds.drop(1).onEach {
            if (currentFilters.value.any { data -> data.filter is SeriesHomeScreenFilter.Favorites }) {
                load(force = false)
            }
        }.launchIn(screenModelScope)

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

    /**
     * Silent refresh used when coming back from the reader: server-backed
     * shelves ("Keep reading", "Recently read", …) re-query so read progress
     * shows immediately, while random "Discover" shelves keep their current
     * picks (they hit RandomShelfCache, unlike [reload] which forces a re-roll).
     *
     * Nothing blanks: the screen is already in LoadState.Success, so load()
     * neither repaints the disk snapshot nor flips to Loading — the new data
     * just replaces the old when it arrives.
     */
    fun refreshAfterReading() {
        screenModelScope.launch {
            // The reader flushes the final read-progress to Komga fire-and-forget
            // on dispose, so querying immediately can race it and read back the
            // OLD progress. A short grace period makes the refresh reliable; the
            // Komga read-progress SSE event is the backstop if it still lands late.
            delay(600)
            load(force = false)
        }
    }

    private suspend fun load(force: Boolean = false) {
        appNotifications.runCatchingToNotifications {
            // Keep the FULL list (enabled + disabled) here: the home-shelf editor
            // is seeded from currentFilters, so dropping disabled shelves would
            // make them vanish from the editor and get wiped on the next save.
            // Disabled shelves are filtered out at render time (see HomeScreen).
            // Ensure a Favorites shelf is present so it shows up + is editable
            // (reorder/disable) like any other; the user can disable it but it is
            // re-added if missing. Once the editor saves, the persisted copy wins.
            val persisted = filterRepository.getFilters().first()
            val withFavorites =
                if (persisted.any { it is SeriesHomeScreenFilter.Favorites }) persisted
                else listOf(
                    SeriesHomeScreenFilter.Favorites(order = -1, label = "Favoris", enabled = true, pageSize = 20)
                ) + persisted
            // Same treatment for the suggestions shelf: it only earns its keep on
            // the screen people actually open, and it stays editable (reorder,
            // rename, disable) like any other.
            val withForYou =
                if (withFavorites.any { it is SeriesHomeScreenFilter.ForYou }) withFavorites
                else withFavorites + SeriesHomeScreenFilter.ForYou(
                    order = withFavorites.size,
                    label = "For you",
                    enabled = true,
                    pageSize = 12,
                )

            // Paint the last known shelves from disk BEFORE touching the network.
            // Every shelf costs a server round-trip and the awaitAll below waits on
            // the slowest, so without this a cold start stares at an empty screen
            // for the full duration (measured: 7.3s for 11 shelves).
            if (!force) paintPersistedShelves(withForYou)
            // Only spin when there was nothing to paint — otherwise the refresh
            // stays silent behind the snapshot instead of blanking it.
            if (state.value !is LoadState.Success) mutableState.value = LoadState.Loading

            // Publish each shelf the moment IT resolves instead of waiting on the
            // slowest one. The shelves already load in parallel, but a single
            // assignment after awaitAll meant a fast shelf ("Keep reading" after
            // closing the reader) stayed stale until the slowest of them all came
            // back — measured 7.3s for 11 shelves.
            //
            // Slots start from what is currently on screen (the disk snapshot, or
            // the previous load's data) so a shelf never blanks while its refresh
            // is in flight; each one is swapped in place, keeping shelf order.
            val slots = withForYou.map { filter ->
                currentFilters.value.find {
                    HomeShelfCache.shelfKey(it.filter) == HomeShelfCache.shelfKey(filter)
                }
            }.toMutableList()
            // Guards both the slot write and the publish: the shelves resolve on
            // different threads and would otherwise race on the list.
            val publishLock = Mutex()

            withForYou.mapIndexed { index, filter ->
                screenModelScope.async {
                    val data = fetchFilterData(filter, force) ?: return@async
                    publishLock.withLock {
                        slots[index] = data
                        currentFilters.value = slots.filterNotNull()
                        // First shelf home wins the spinner: with no disk snapshot
                        // to paint (first ever start) the screen would otherwise
                        // stay on Loading until every shelf had answered.
                        mutableState.value = LoadState.Success(Unit)
                    }
                }
            }.awaitAll()

            val fresh = slots.filterNotNull()
            currentFilters.value = fresh

            mutableState.value = LoadState.Success(Unit)
            // Fire-and-forget: the disk write must never delay the screen.
            screenModelScope.launch { HomeShelfCache.save(fresh) }
        }.onFailure { mutableState.value = LoadState.Error(it) }
    }

    /**
     * Paints the disk snapshot, pairing each persisted shelf with its live filter
     * (re-read from the database every load). A shelf the user has since renamed,
     * reordered or deleted simply won't match its key and is skipped — the fresh
     * load right after is what reconciles everything.
     */
    private suspend fun paintPersistedShelves(filters: List<HomeScreenFilter>) {
        if (state.value is LoadState.Success) return
        val cached = HomeShelfCache.load() ?: return
        val data = filters.mapNotNull { filter ->
            cached[HomeShelfCache.shelfKey(filter)]?.let { HomeShelfCache.toFilterData(it, filter) }
        }
        if (data.isEmpty()) return

        // Seed the random-shelf cache with the picks we just painted. Without
        // this, the fresh load starting right after finds an empty (process-new)
        // RandomShelfCache, re-rolls every random shelf, and swaps the content out
        // from under the user seconds after it appeared — re-downloading ~80 covers
        // that were never seen before. Discover shelves now keep their picks until
        // an explicit refresh.
        //
        // put() stamps fetchedAt = now, so the entries stay valid for the cache's
        // 5-minute TTL, exactly as if they had just been fetched. Pull-to-refresh
        // passes force=true, which bypasses this cache entirely and rolls a genuinely
        // new pick — that path is deliberately left untouched.
        data.forEach { filterData ->
            filterData.filter.randomShelfCacheKey()?.let { key ->
                RandomShelfCache.put(key, filterData)
            }
        }

        currentFilters.value = data
        mutableState.value = LoadState.Success(Unit)
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

    /** The queries themselves live in [HomeShelfResolver], shared with the
     *  shelf-detail screen so both surfaces resolve a shelf identically. */
    private suspend fun fetchFilterDataFromServer(filter: HomeScreenFilter): HomeFilterData? =
        shelfResolver.resolve(filter)

    fun seriesMenuActions() = SeriesMenuActions(seriesApi, appNotifications, taskEmitter, screenModelScope)

    fun onSeriesSelect(series: KomgaSeries) {
        val current = selectedSeries.value
        selectedSeries.value = if (current.any { it.id == series.id })
            current.filterNot { it.id == series.id }
        else current + series
        isInEditMode.value = selectedSeries.value.isNotEmpty()
    }

    fun toggleSelectAll(all: List<KomgaSeries>) {
        selectedSeries.value = if (selectedSeries.value.size >= all.size && all.isNotEmpty()) emptyList() else all
        isInEditMode.value = selectedSeries.value.isNotEmpty()
    }

    fun clearSelection() {
        selectedSeries.value = emptyList()
        isInEditMode.value = false
    }
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
