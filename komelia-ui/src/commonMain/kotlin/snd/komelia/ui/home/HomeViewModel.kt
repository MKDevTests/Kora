package snd.komelia.ui.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource
import snd.komelia.AppNotifications
import snd.komelia.homefilters.BooksHomeScreenFilter
import snd.komelia.homefilters.HomeScreenFilter
import snd.komelia.homefilters.HomeScreenFilterRepository
import snd.komelia.homefilters.SeriesHomeScreenFilter
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.offline.tasks.OfflineTaskEmitter
import snd.komelia.progress.ReadProgressChanges
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
) : StateScreenModel<LoadState<Unit>>(Uninitialized) {
    val cardWidth = cardWidthFlow.stateIn(screenModelScope, Eagerly, defaultCardWidth.dp)
    private val favoriteIds = favoriteIdsFlow.stateIn(screenModelScope, Eagerly, emptySet())
    private val excludedLibraryIds = excludedLibraryIdsFlow.stateIn(screenModelScope, Eagerly, emptySet())
    private val shelfResolver = HomeShelfResolver(
        seriesApi = seriesApi,
        bookApi = bookApi,
        favoriteIds = { favoriteIds.value },
        excludedLibraryIds = { excludedLibraryIds.value },
    )

    private val reloadEventsEnabled = MutableStateFlow(true)
    private val reloadJobsFlow = MutableSharedFlow<Unit>(1, 0, DROP_OLDEST)

    /**
     * Set whenever read progress moves anywhere — reader, book screen, another
     * device. Consumed when Home comes back on screen, so the progress shelves
     * are correct no matter WHERE the book was opened from. The reader's
     * onExit callback stays as the fast path, but it can't be the only one:
     * it is @Transient on the reader screen (so it is null after a process
     * restore) and it only exists on the two Home entry points.
     */
    private val progressShelvesDirty = MutableStateFlow(false)

    /**
     * Raised by events that change what a shelf contains rather than just how
     * far the user has read — those need the full reload. Read progress on its
     * own is served by [refreshProgressShelves].
     */
    private val structuralReloadPending = MutableStateFlow(false)

    /** When the last reload finished, for the staleness escape hatch below. */
    private var lastReloadMark: TimeSource.Monotonic.ValueTimeMark? = null

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

        // Local progress writes, straight from our own book API — see
        // [ReadProgressChanges]. No reload is kicked off here: Home is off
        // screen while the book is being read, and startKomgaEventsHandler
        // consumes the flag the moment it comes back. This is what makes the
        // shelves correct after reading a book opened from a series screen,
        // the search, or the widget, with the SSE stream down or slow.
        ReadProgressChanges.changes.onEach { progressShelvesDirty.value = true }
            .launchIn(screenModelScope)

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

        screenModelScope.launch {
            reloadJobsFlow.collectLatest {
                reloadEventsEnabled.first { it }

                // A real quiet period rather than the old "reload, then sleep
                // five seconds". The delay used to sit AFTER the work, so a
                // library scan — which emits an event per book — paid for a
                // full reload of every shelf every five seconds for the whole
                // scan. collectLatest cancels this wait when the next event
                // lands, so a burst now collapses into a single reload once
                // the server goes quiet.
                //
                // The staleness escape hatch keeps the screen alive during a
                // scan that never goes quiet: past that point the next event
                // reloads immediately instead of waiting for silence.
                val sinceLastReload = lastReloadMark?.elapsedNow()
                if (sinceLastReload == null || sinceLastReload < RELOAD_MAX_STALENESS) {
                    delay(RELOAD_QUIET_PERIOD)
                }

                // Read progress alone only moves the progress shelves. Falling
                // back to a full load there would undo the cheap path taken on
                // reader exit — the SSE event lands right after it and would
                // re-query every shelf a second time.
                val structural = structuralReloadPending.value
                structuralReloadPending.value = false
                if (structural) load() else refreshProgressShelves()

                progressShelvesDirty.value = false
                lastReloadMark = TimeSource.Monotonic.markNow()
            }
        }
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
            // Only the shelves that depend on read progress, not all eleven:
            // this used to be a full load(), i.e. a round-trip per non-random
            // shelf every time a book was closed.
            progressShelvesDirty.value = false
            val changed = refreshProgressShelves()
            // A single retry, and only when the server answered with exactly
            // what we already had — that means the 600ms grace lost the race
            // against the progress flush. Costs nothing in the nominal case.
            if (!changed) {
                delay(1_500)
                refreshProgressShelves()
            }
        }
    }

    /**
     * Re-resolves only the shelves whose content depends on read progress and
     * swaps them in place. Returns true when at least one shelf actually
     * changed, which is what tells [refreshAfterReading] whether it read back
     * stale data.
     */
    private suspend fun refreshProgressShelves(): Boolean {
        val current = currentFilters.value
        val targets = current.withIndex().filter { it.value.filter.dependsOnReadProgress() }
        if (targets.isEmpty()) return true

        val slots = current.toMutableList()
        val publishLock = Mutex()
        var changed = false

        targets.map { (index, existing) ->
            screenModelScope.async {
                val fresh = shelfResolver.resolve(existing.filter) ?: return@async
                if (fresh == existing) return@async
                publishLock.withLock {
                    changed = true
                    slots[index] = fresh
                    currentFilters.value = slots.toList()
                }
            }
        }.awaitAll()

        // Keep the disk snapshot in step, otherwise the next cold start repaints
        // the stale progress before the network answers.
        if (changed) {
            val snapshot = currentFilters.value
            screenModelScope.launch { HomeShelfCache.save(snapshot) }
        }
        return changed
    }

    /**
     * Shelves built from read progress. "Keep reading" is [BooksHomeScreenFilter.OnDeck].
     * Custom shelves are deliberately excluded: detecting a readStatus condition
     * inside an arbitrary search tree is fragile, and they are still covered by
     * the full reload the SSE listener triggers.
     */
    private fun HomeScreenFilter.dependsOnReadProgress(): Boolean = when (this) {
        is BooksHomeScreenFilter.OnDeck,
        is BooksHomeScreenFilter.ForgottenBooks,
        is SeriesHomeScreenFilter.AlmostFinished -> true

        else -> false
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
            // The suggestions shelf was withdrawn: it could not be reordered
            // without vanishing (the snapshot is keyed by order, and rebuilding
            // a taste profile is far too slow to arrive on a Home load), and a
            // shelf that misbehaves on the app's first screen is worse than no
            // shelf. Suggestions live in the library's "For you" tab, which is
            // opened on purpose. Any shelf left in a saved layout is dropped
            // here rather than deleted from the type, so old configurations
            // still parse.
            val withForYou = withFavorites.filterNot { it is SeriesHomeScreenFilter.ForYou }

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

            // Four at a time, not all of them at once.
            //
            // Measured 2026-08-20 on the user's own server: /api/v1/books/ondeck
            // answered curl in 1.6s with the app closed, and took 23 152, 24 090
            // and 25 643ms from inside this fan-out. Twice it went past the 30s
            // socket timeout and the shelf died with a SocketTimeoutException
            // after 31 157ms, having received not one byte of response.
            //
            // Eleven shelves launched together is eleven queries landing on the
            // server at once while it is also serving covers; the five that got
            // answered came back in ~1.5s and the rest waited behind them. Same
            // bound as the genre counts, the next-releases scan and the
            // favorites resolver, all added for the same reason.
            val shelfLimit = Semaphore(MAX_CONCURRENT_SHELVES)
            withForYou.mapIndexed { index, filter ->
                screenModelScope.async {
                    val data = shelfLimit.withPermit { fetchFilterData(filter, force) } ?: return@async
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
        // Home is back on screen. If progress moved while it was away — the book
        // was opened from a series or book screen, from the widget, or read on
        // another device — refresh the progress shelves now rather than showing
        // what was true before the reader opened.
        if (progressShelvesDirty.value && state.value is LoadState.Success) {
            progressShelvesDirty.value = false
            screenModelScope.launch { refreshProgressShelves() }
        }
    }

    private fun startKomgaEventListener() {
        komgaEvents.onEach { event ->
            when (event) {
                is ReadProgressEvent,
                is ReadProgressSeriesEvent -> {
                    progressShelvesDirty.value = true
                    reloadJobsFlow.tryEmit(Unit)
                }

                is BookEvent,
                is SeriesEvent -> {
                    structuralReloadPending.value = true
                    reloadJobsFlow.tryEmit(Unit)
                }

                else -> {}
            }
        }.launchIn(screenModelScope)
    }

    fun onFilterChange(number: Int) {
        this.activeFilterNumber.value = number
    }

    private companion object {
        /** Shelves resolved at once. See the fan-out above for the measurement. */
        const val MAX_CONCURRENT_SHELVES = 4

        /** How long the server must stay quiet before a reload is worth doing. */
        val RELOAD_QUIET_PERIOD = 1.5.seconds

        /**
         * Past this long without a reload, the next event no longer waits for
         * silence. Keeps the screen alive during a scan that never goes quiet.
         */
        val RELOAD_MAX_STALENESS = 30.seconds
    }
}
