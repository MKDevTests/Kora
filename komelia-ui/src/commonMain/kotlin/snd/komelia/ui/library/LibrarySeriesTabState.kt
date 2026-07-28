package snd.komelia.ui.library

import snd.komelia.perf.PerfTrace
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import snd.komelia.ui.common.encodeNullReadingDirectionAsBlank
import snd.komelia.ui.common.komgaCacheJson
import snd.komelia.AppNotifications
import snd.komelia.hidden.HIDDEN_TAG
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaReferentialApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.offline.tasks.OfflineTaskEmitter
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.ui.LoadState
import snd.komelia.ui.common.cards.defaultCardWidth
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.menus.SeriesMenuActions
import snd.komelia.ui.series.SeriesFilter
import snd.komelia.ui.series.SeriesNavigationContext
import snd.komelia.ui.series.SeriesFilterState
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.common.KomgaSort
import snd.komga.client.common.KomgaSort.Direction.ASC
import snd.komga.client.common.KomgaSort.KomgaSeriesSort
import snd.komga.client.common.Page
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeries
import snd.komga.client.sse.KomgaEvent

private val logger = KotlinLogging.logger { }

private const val SERIES_RANDOM_SORT = "random"

/** Random draws that added nothing new before we stop trying to fill a page. */
private const val MAX_BARREN_RANDOM_DRAWS = 3

/**
 * Reserved key under which the genre drill-down persists its single,
 * shared-across-all-genres filter + sort, reusing the per-library filters table.
 * It is an opaque sentinel that cannot collide with a real Komga library id.
 */
private val GENRE_FILTER_STORAGE_KEY = KomgaLibraryId("__kora_genre_filter__")

/**
 * Cache of each library's first series page, keyed by library id (or
 * library|genre-tag for a drill-down). The library screen is rebuilt on every
 * library switch, so without this each return re-fetches the grid behind a
 * spinner. With it the cached grid shows instantly and refreshes silently. Only
 * the default (non genre-locked) series tab populates it; a filter signature
 * guards against painting a page that no longer matches the active filter.
 *
 * Persisted to disk (mirrors [snd.komelia.ui.nextreleases.NextReleasesCache] and
 * the genre catalog): the in-memory map alone dies with the process, so a COLD
 * start re-paid the full server cost — measured at 6.9-9.4s for a single first
 * page on the user's server. With the snapshot the grid paints instantly from
 * disk while the network load runs silently behind it.
 */
private object LibrarySeriesPageCache {
    data class Snapshot(
        val series: List<KomgaSeries>,
        val downloadedSeriesIds: Set<KomgaSeriesId>,
        val totalSeriesPages: Int,
        val totalSeriesCount: Int,
        val filterSignature: String,
    )

    private val byLibrary = mutableMapOf<String, Snapshot>()
    fun get(libraryId: String): Snapshot? = byLibrary[libraryId]
    fun put(libraryId: String, snapshot: Snapshot) {
        byLibrary[libraryId] = snapshot
    }

    private fun cacheDir() = FileKit.filesDir / "library_grid"

    /**
     * Cache keys embed a library id and, for a drill-down, an arbitrary genre tag
     * — which may contain characters that are illegal in a filename. Sanitize for
     * readability and append the key's hash so two different keys can never
     * collapse onto the same file.
     */
    private fun cacheFile(key: String) =
        cacheDir() / (key.map { if (it.isLetterOrDigit() || it == '-') it else '_' }
            .joinToString("").take(60) + "_" + key.hashCode() + ".json")

    private val gridJson = komgaCacheJson

    /**
     * Disk snapshot (survives process restart). Null if absent or unreadable.
     * A miss is never fatal, but it MUST be visible: a silently disabled cache is
     * indistinguishable from a slow server.
     */
    suspend fun loadPersisted(key: String): Snapshot? = runCatching {
        val raw = cacheFile(key).readBytes().decodeToString()
        val p = gridJson.decodeFromString(PersistedGrid.serializer(), raw)
        Snapshot(
            series = p.series,
            downloadedSeriesIds = p.downloadedSeriesIds.map { KomgaSeriesId(it) }.toSet(),
            totalSeriesPages = p.totalSeriesPages,
            totalSeriesCount = p.totalSeriesCount,
            filterSignature = p.filterSignature,
        )
    }.onFailure { logger.warn(it) { "Library grid snapshot unreadable for $key; falling back to a network load" } }.getOrNull()

    /** Best-effort write — logged loudly, because a silent failure costs every cold start. */
    suspend fun persist(key: String, snapshot: Snapshot) {
        runCatching {
            cacheDir().createDirectories()
            val p = PersistedGrid(
                series = snapshot.series,
                downloadedSeriesIds = snapshot.downloadedSeriesIds.map { it.value },
                totalSeriesPages = snapshot.totalSeriesPages,
                totalSeriesCount = snapshot.totalSeriesCount,
                filterSignature = snapshot.filterSignature,
            )
            // See encodeNullReadingDirectionAsBlank: a null readingDirection is
            // written by komga-client in a form it cannot read back.
            val tree = gridJson.encodeToJsonElement(PersistedGrid.serializer(), p)
                .encodeNullReadingDirectionAsBlank()
            val encoded = gridJson.encodeToString(JsonElement.serializer(), tree)
            cacheFile(key).write(encoded.encodeToByteArray())
        }.onFailure { logger.warn(it) { "Library grid snapshot write failed for $key; next cold start will hit the network" } }
    }
}

@Serializable
private data class PersistedGrid(
    val series: List<KomgaSeries>,
    val downloadedSeriesIds: List<String>,
    val totalSeriesPages: Int,
    val totalSeriesCount: Int,
    val filterSignature: String,
)

class LibrarySeriesTabState(
    private val bookApi: KomgaBookApi,
    private val seriesApi: KomgaSeriesApi,
    referentialApi: KomgaReferentialApi,
    private val notifications: AppNotifications,
    private val komgaEvents: SharedFlow<KomgaEvent>,
    private val settingsRepository: CommonSettingsRepository,
    libraryFlow: Flow<KomgaLibrary?>,
    private val libraryId: KomgaLibraryId?,
    private val taskEmitter: OfflineTaskEmitter,
    private val librarySeriesFiltersRepository: snd.komelia.libraryfilters.LibrarySeriesFiltersRepository,
    private val baseTagFilter: String? = null,
) : StateScreenModel<LoadState<Unit>>(LoadState.Uninitialized) {
    val cardWidth: StateFlow<Dp> = settingsRepository.getCardWidth()
        .map { Dp(it.toFloat()) }
        .stateIn(screenModelScope, SharingStarted.Eagerly, defaultCardWidth.dp)
    private val library: StateFlow<KomgaLibrary?> =
        libraryFlow.stateIn(screenModelScope, SharingStarted.Eagerly, null)
    val pageLoadSize = MutableStateFlow(50)
    var series by mutableStateOf<List<KomgaSeries>>(emptyList())
        private set
    var downloadedSeriesIds by mutableStateOf<Set<KomgaSeriesId>>(emptySet())
        private set
    var totalSeriesPages by mutableStateOf(1)
        private set
    var totalSeriesCount by mutableStateOf(0)
        private set
    var currentSeriesPage by mutableStateOf(1)
        private set

    val isInEditMode = MutableStateFlow(false)
    var selectedSeries by mutableStateOf<List<KomgaSeries>>(emptyList())
        private set

    val filterState: SeriesFilterState = SeriesFilterState(
        // Genre drill-down defaults to Title Ascending; the regular series tab
        // keeps Date-added Descending.
        defaultSort = if (baseTagFilter != null) SeriesSort.TITLE_ASC else SeriesSort.DATE_ADDED_DESC,
        library = library,
        referentialApi = referentialApi,
        appNotifications = notifications,
    )

    private val reloadEventsEnabled = MutableStateFlow(true)
    private val reloadJobsFlow = MutableSharedFlow<Unit>(1, 0, BufferOverflow.DROP_OLDEST)

    fun initialize(filter: SeriesScreenFilter? = null) {
        if (state.value !is LoadState.Uninitialized) return

        screenModelScope.launch {
            // Restore the persisted per-library filter unless an explicit filter
            // was provided. Lightweight (a DB read + JSON parse), so it stays
            // ahead of the load — it changes the query.
            if (filter != null) {
                filterState.applyFilter(filter)
            } else {
                // Genre drill-down restores ONE shared filter (default Title Asc);
                // the regular series tab restores its per-library filter.
                val storageKey = if (baseTagFilter != null) GENRE_FILTER_STORAGE_KEY else libraryId
                storageKey?.let { key ->
                    runCatching {
                        librarySeriesFiltersRepository.get(key)?.let { json ->
                            kotlinx.serialization.json.Json.decodeFromString<SeriesFilterDto>(json).toDomain()
                        }
                    }.getOrNull()?.let { restored -> filterState.restore(restored) }
                }
            }

            pageLoadSize.value = settingsRepository.getSeriesPageLoadSize().first()
            // Paint the cached first page instantly, then load. The grid must not
            // wait on the filter panel's referential data (genres / tags /
            // publishers / languages / …): those six lookups are only needed when
            // the filter panel is opened, so they run in the background here. This
            // was the dominant per-library-switch latency.
            showCachedFirstPageIfAny()
            loadSeriesPage(1)
            screenModelScope.launch { filterState.initialize() }

            settingsRepository.getSeriesPageLoadSize()
                .onEach {
                    if (pageLoadSize.value != it) {
                        pageLoadSize.value = it
                        loadSeriesPage(1)
                    }
                }.launchIn(screenModelScope)

            // Subscribe to filter changes only AFTER the restore + initial load.
            // The restored filter is already applied and loaded, so drop(1) won't
            // re-fire for it — this avoids a second redundant page load per open.
            filterState.state.drop(1).onEach { current ->
                loadSeriesPage(1)
                // Persist user-modified filters: per library normally, or under the
                // shared genre key when this is a genre drill-down.
                val storageKey = if (baseTagFilter != null) GENRE_FILTER_STORAGE_KEY else libraryId
                storageKey?.let { key ->
                    runCatching {
                        val json = kotlinx.serialization.json.Json.encodeToString(SeriesFilterDto.from(current))
                        librarySeriesFiltersRepository.put(key, json)
                    }
                }
            }.launchIn(screenModelScope)
        }
        startKomgaEventListener()

        reloadJobsFlow.onEach {
            reloadEventsEnabled.first { it }
            loadSeriesPage(currentSeriesPage)
            delay(1000)
        }.launchIn(screenModelScope)
    }

    fun reload() {
        // An explicit refresh is the one moment a random order SHOULD change.
        resetRandomPool()
        screenModelScope.launch {
            loadSeriesPage(1)
        }
    }

    fun registerSeriesListContext(selectedSeries: KomgaSeries) {
        SeriesNavigationContext.put(
            selectedSeries.id,
            SeriesNavigationContext.SeriesListContext(
                libraryId = libraryId,
                filter = filterState.state.value,
                pageSize = pageLoadSize.value,
                currentPage = currentSeriesPage,
                seriesIndexInPage = series
                    .indexOfFirst { it.id == selectedSeries.id }
                    .coerceAtLeast(0)
            )
        )
    }

    /**
     * Opens a random series from the current filtered list.
     *
     * Picks a random *offset* under the list's own sort rather than asking the
     * server for `sort=RANDOM`. A randomly-sorted result has no knowable position,
     * so it could not be given a [SeriesNavigationContext] — and every sibling
     * move (next/previous series, and the reader's "next series with the current
     * filters" when a book ends) is computed from that position. Random was
     * therefore a dead end: you landed on one series and had to roll again.
     * Drawing an offset instead makes the pick indistinguishable from having
     * browsed to it.
     */
    fun openRandomSeries(onSeriesSelected: (KomgaSeries) -> Unit) {
        val total = totalSeriesCount
        if (total == 0) return
        notifications.runCatchingToNotifications(screenModelScope) {
            val filter = filterState.state.value
            val condition = allOfSeries {
                libraryId?.let { library { isEqualTo(it) } }
                baseTagFilter?.let { tag { isEqualTo(it) } }
                tag { isNotEqualTo(HIDDEN_TAG) }
                filter.addConditionTo(this)
            }
            // An offset is only meaningful under a stable order, so when the list
            // is itself sorted randomly we anchor the draw on the title instead.
            val sort = if (filter.sortOrder == SeriesSort.RANDOM) SeriesSort.TITLE_ASC else filter.sortOrder
            val index = Random.nextInt(total)
            val page = seriesApi.getSeriesList(
                conditionBuilder = condition,
                fulltextSearch = filter.searchTerm.ifBlank { null },
                pageRequest = KomgaPageRequest(
                    size = 1,
                    pageIndex = index,
                    sort = sort.komgaSort
                )
            )
            page.content.firstOrNull()?.let { picked ->
                // pageSize = 1 makes the global index simply `index`, which is
                // exactly what the sibling navigation reads back.
                SeriesNavigationContext.put(
                    picked.id,
                    SeriesNavigationContext.SeriesListContext(
                        libraryId = libraryId,
                        filter = filter.copy(sortOrder = sort),
                        pageSize = 1,
                        currentPage = index + 1,
                        seriesIndexInPage = 0,
                    )
                )
                onSeriesSelected(picked)
            }
        }
    }

    fun seriesMenuActions() = SeriesMenuActions(seriesApi, notifications, taskEmitter, screenModelScope)
    fun bookMenuActions() = BookMenuActions(bookApi, notifications, screenModelScope, taskEmitter)

    fun onPageSizeChange(pageSize: Int) {
        pageLoadSize.value = pageSize
        screenModelScope.launch { settingsRepository.putSeriesPageLoadSize(pageSize) }
        notifications.runCatchingToNotifications(screenModelScope) {
            loadSeriesPage(1)
        }
    }

    fun onPageChange(pageNumber: Int) {
        onEditModeChange(false)
        screenModelScope.launch { loadSeriesPage(pageNumber) }
    }

    fun onEditModeChange(editMode: Boolean) {
        this.isInEditMode.value = editMode
        if (!editMode) {
            selectedSeries = emptyList()
        }

    }

    fun onSeriesSelect(series: KomgaSeries) {
        if (selectedSeries.any { it.id == series.id }) {
            selectedSeries = selectedSeries.filter { it.id != series.id }
        } else this.selectedSeries += series

        if (selectedSeries.isNotEmpty() && !isInEditMode.value) onEditModeChange(true)
    }

    private suspend fun loadSeriesPage(page: Int) {
        notifications.runCatchingToNotifications {
            val loadStateDelay = delayLoadState()
            currentSeriesPage = page
            val currentFilter = filterState.state.value

            if (currentFilter.sortOrder == SeriesSort.RANDOM) {
                val size = pageLoadSize.value
                val lastDrawn = fillRandomPool(upTo = page * size, filter = currentFilter)
                loadStateDelay.cancel()

                currentSeriesPage = page
                lastDrawn?.let { totalSeriesCount = it.totalElements }
                val poolPages = if (size > 0) (randomPool.size + size - 1) / size else 1
                // Keep one page of headroom while more can still be drawn, so the
                // user can page on; once exhausted the pool IS the whole result.
                totalSeriesPages = if (randomPoolExhausted) poolPages else maxOf(poolPages, page + 1)
                series = randomPool.drop((page - 1) * size).take(size)
                downloadedSeriesIds = bookApi.getDownloadedSeriesIds(series.map { it.id })
                mutableState.value = LoadState.Success(Unit)
                cacheFirstPage(page)
                return@runCatchingToNotifications
            }

            val seriesPage = getAllSeries(page, currentFilter)

            loadStateDelay.cancel()

            currentSeriesPage = seriesPage.number + 1
            totalSeriesPages = seriesPage.totalPages
            totalSeriesCount = seriesPage.totalElements
            series = seriesPage.content
            downloadedSeriesIds = bookApi.getDownloadedSeriesIds(seriesPage.content.map { it.id })
            mutableState.value = LoadState.Success(Unit)
            cacheFirstPage(page)
        }.onFailure { mutableState.value = LoadState.Error(it) }
    }

    /**
     * Cache key: per library, or per (library + genre tag) for a genre
     * drill-down, so reopening the SAME genre paints its cached first page
     * instantly instead of waiting on the (server-bound) tag query every time.
     */
    private val seriesCacheKey: String? =
        libraryId?.value?.let { lib -> if (baseTagFilter != null) "$lib|$baseTagFilter" else lib }

    /** Snapshot the first page so a return to this library/genre paints instantly. */
    private fun cacheFirstPage(page: Int) {
        if (page != 1) return
        val key = seriesCacheKey ?: return
        val snapshot = LibrarySeriesPageCache.Snapshot(
            series = series,
            downloadedSeriesIds = downloadedSeriesIds,
            totalSeriesPages = totalSeriesPages,
            totalSeriesCount = totalSeriesCount,
            filterSignature = filterSignature(filterState.state.value),
        )
        LibrarySeriesPageCache.put(key, snapshot)
        // Fire-and-forget: the disk write must never delay the grid.
        screenModelScope.launch { LibrarySeriesPageCache.persist(key, snapshot) }
    }

    /**
     * Paint the cached first page (if it matches the active filter) before the
     * network load. Memory first, then the disk snapshot — the latter is what
     * makes a COLD start instant instead of waiting several seconds on the server.
     */
    private suspend fun showCachedFirstPageIfAny() {
        if (state.value is LoadState.Success) return
        val key = seriesCacheKey ?: return
        val fromMemory = LibrarySeriesPageCache.get(key)
        val snapshot = fromMemory
            ?: LibrarySeriesPageCache.loadPersisted(key)?.also { LibrarySeriesPageCache.put(key, it) }
            ?: return
        if (snapshot.filterSignature != filterSignature(filterState.state.value)) return
        logger.debug {
            "painted library grid $key from ${if (fromMemory != null) "memory" else "disk"} " +
                "(${snapshot.series.size} series)"
        }
        series = snapshot.series
        downloadedSeriesIds = snapshot.downloadedSeriesIds
        totalSeriesPages = snapshot.totalSeriesPages
        totalSeriesCount = snapshot.totalSeriesCount
        currentSeriesPage = 1
        mutableState.value = LoadState.Success(Unit)
    }

    // ---- Stable random ordering -------------------------------------------
    // Komga reshuffles on every request, so asking it for "page 2" of a random
    // sort actually drew from a NEW shuffle: the same series could appear twice
    // across pages while others were unreachable, and any reload re-rolled the
    // lot. Draw into a session pool instead and page through that, so the order
    // only changes when the user asks for it.
    private val randomPool = mutableListOf<KomgaSeries>()
    private var randomPoolSignature: String? = null
    private var randomPoolExhausted = false

    /** Forget the drawn order; the next random load reshuffles. */
    private fun resetRandomPool() {
        randomPool.clear()
        randomPoolSignature = null
        randomPoolExhausted = false
    }

    /**
     * Draws until the pool holds [upTo] series, keeping only ones not already
     * drawn. Returns the last server page (for the counts), or null when the
     * pool already sufficed — i.e. paging back costs nothing.
     *
     * Overlap grows as the pool approaches the library size, so give up after a
     * few barren draws rather than looping: the last page is then simply short.
     */
    private suspend fun fillRandomPool(upTo: Int, filter: SeriesFilter): Page<KomgaSeries>? {
        val signature = filterSignature(filter)
        if (randomPoolSignature != signature) {
            randomPool.clear()
            randomPoolExhausted = false
            randomPoolSignature = signature
        }

        var lastPage: Page<KomgaSeries>? = null
        var barrenDraws = 0
        while (randomPool.size < upTo && !randomPoolExhausted && barrenDraws < MAX_BARREN_RANDOM_DRAWS) {
            val drawn = getAllSeries(1, filter)
            lastPage = drawn
            val known = randomPool.mapTo(HashSet(randomPool.size)) { it.id.value }
            val fresh = drawn.content.filter { it.id.value !in known }
            if (fresh.isEmpty()) barrenDraws++ else {
                barrenDraws = 0
                randomPool += fresh
            }
            if (randomPool.size >= drawn.totalElements) randomPoolExhausted = true
        }
        return lastPage
    }

    private fun filterSignature(filter: SeriesFilter): String =
        runCatching {
            kotlinx.serialization.json.Json.encodeToString(SeriesFilterDto.from(filter))
        }.getOrElse { filter.toString() }

    private suspend fun getAllSeries(
        page: Int,
        filter: SeriesFilter
    ): Page<KomgaSeries> {
        val condition = allOfSeries {
            libraryId?.let { library { isEqualTo(it) } }
            baseTagFilter?.let { tag { isEqualTo(it) } }
            // Exclude admin-hidden series (kora:hidden) server-side so each page
            // comes back genuinely full. Otherwise the client-side ignore filter
            // strips them AFTER fetching, leaving a 100-item page as 97-98 items
            // -> ragged last row + page/element counts that overstate reality.
            tag { isNotEqualTo(HIDDEN_TAG) }
            filter.addConditionTo(this)
        }

        // Letter filter is applied by SeriesFilter.addConditionTo above
        // via titleSort.beginsWith — accurate, indexed, server-side.
        return PerfTrace.measure("library.series page=$page", { it.content.size }) {
            seriesApi.getSeriesList(
                conditionBuilder = condition,
                fulltextSearch = filter.searchTerm.ifBlank { null },
                pageRequest = KomgaPageRequest(
                    size = pageLoadSize.value,
                    pageIndex = page - 1,
                    sort = filter.sortOrder.komgaSort
                )
            )
        }
    }

    private fun delayLoadState(): Deferred<Unit> {
        return screenModelScope.async {
            delay(200)
            // Only show the spinner before the first successful load. Once a
            // page (or a cached snapshot) is on screen, reloads / pagination /
            // filter changes refresh silently instead of flashing the loader.
            if (state.value is LoadState.Uninitialized) mutableState.value = LoadState.Loading
        }
    }


    fun stopKomgaEventHandler() {
        reloadEventsEnabled.value = false
    }

    fun startKomgaEventHandler() {
        reloadEventsEnabled.value = true
    }

    private fun startKomgaEventListener() {
        komgaEvents.onEach { event ->
            when (event) {
                is KomgaEvent.SeriesChanged -> onSeriesChange(event)
                is KomgaEvent.SeriesAdded -> onSeriesChange(event)
                is KomgaEvent.SeriesDeleted -> onSeriesChange(event)
                is KomgaEvent.ReadProgressSeriesChanged -> onReadProgressChange(event)
                is KomgaEvent.ReadProgressSeriesDeleted -> onReadProgressChange(event)
                else -> {}
            }
        }.launchIn(screenModelScope)
    }

    private fun onSeriesChange(event: KomgaEvent.SeriesEvent) {
        if (library.value == null || event.libraryId == library.value?.id) {
            reloadJobsFlow.tryEmit(Unit)
        }
    }

    private fun onReadProgressChange(event: KomgaEvent.ReadProgressSeriesEvent) {
        if (series.any { it.id == event.seriesId }) {
            reloadJobsFlow.tryEmit(Unit)
        }
    }


    enum class SeriesSort(val komgaSort: KomgaSeriesSort) {
        UPDATED_DESC(KomgaSeriesSort.byLastModifiedDateDesc()),
        UPDATED_ASC(KomgaSeriesSort.byLastModifiedDateAsc()),
        RELEASE_DATE_DESC(KomgaSeriesSort.byReleaseDateDesc()),
        RELEASE_DATE_ASC(KomgaSeriesSort.byReleaseDateAsc()),
        TITLE_ASC(KomgaSeriesSort.byTitleAsc()),
        TITLE_DESC(KomgaSeriesSort.byTitleDesc()),
        DATE_ADDED_DESC(KomgaSeriesSort.byCreatedDateDesc()),
        DATE_ADDED_ASC(KomgaSeriesSort.byCreatedDateAsc()),
        RANDOM(KomgaSeriesSort(listOf(KomgaSort.Order(SERIES_RANDOM_SORT, ASC)))),
        //        FOLDER_NAME_ASC(KomgaSeriesSort.byFolderNameAsc()),
//        FOLDER_NAME_DESC(KomgaSeriesSort.byFolderNameDesc()),
//        BOOKS_COUNT_ASC(KomgaSeriesSort.byBooksCountAsc()),
//        BOOKS_COUNT_DESC(KomgaSeriesSort.byBooksCountDesc())
    }

}