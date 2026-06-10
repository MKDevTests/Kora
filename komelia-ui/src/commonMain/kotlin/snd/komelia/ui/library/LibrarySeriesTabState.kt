package snd.komelia.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
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
import snd.komelia.AppNotifications
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

private const val SERIES_RANDOM_SORT = "random"

/**
 * Process-wide cache of each library's first series page, keyed by library id.
 * The library screen is rebuilt on every library switch, so without this each
 * return re-fetches the grid behind a spinner. With it the cached grid shows
 * instantly and refreshes silently. Only the default (non genre-locked) series
 * tab populates it; a filter signature guards against painting a page that no
 * longer matches the active filter. Cleared on process restart.
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
}

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
        defaultSort = SeriesSort.DATE_ADDED_DESC,
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
            } else if (baseTagFilter == null) {
                libraryId?.let { libId ->
                    runCatching {
                        librarySeriesFiltersRepository.get(libId)?.let { json ->
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
                // Persist user-modified filters per library (skip in a locked genre view)
                if (baseTagFilter == null) {
                    libraryId?.let { libId ->
                        runCatching {
                            val json = kotlinx.serialization.json.Json.encodeToString(SeriesFilterDto.from(current))
                            librarySeriesFiltersRepository.put(libId, json)
                        }
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

    fun openRandomSeries(onSeriesSelected: (KomgaSeries) -> Unit) {
        if (totalSeriesCount == 0) return
        notifications.runCatchingToNotifications(screenModelScope) {
            val filter = filterState.state.value
            val condition = allOfSeries {
                libraryId?.let { library { isEqualTo(it) } }
                baseTagFilter?.let { tag { isEqualTo(it) } }
                filter.addConditionTo(this)
            }
            val page = seriesApi.getSeriesList(
                conditionBuilder = condition,
                fulltextSearch = filter.searchTerm.ifBlank { null },
                pageRequest = KomgaPageRequest(
                    size = 1,
                    pageIndex = 0,
                    sort = SeriesSort.RANDOM.komgaSort
                )
            )
            page.content.firstOrNull()?.let(onSeriesSelected)
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
            val seriesPage = getAllSeries(page, filterState.state.value)

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

    /** Snapshot the first page so a return to this library paints instantly. */
    private fun cacheFirstPage(page: Int) {
        if (page != 1 || baseTagFilter != null) return
        val libKey = libraryId?.value ?: return
        LibrarySeriesPageCache.put(
            libKey,
            LibrarySeriesPageCache.Snapshot(
                series = series,
                downloadedSeriesIds = downloadedSeriesIds,
                totalSeriesPages = totalSeriesPages,
                totalSeriesCount = totalSeriesCount,
                filterSignature = filterSignature(filterState.state.value),
            )
        )
    }

    /** Paint the cached first page (if it matches the active filter) before the network load. */
    private fun showCachedFirstPageIfAny() {
        if (baseTagFilter != null || state.value is LoadState.Success) return
        val libKey = libraryId?.value ?: return
        val snapshot = LibrarySeriesPageCache.get(libKey) ?: return
        if (snapshot.filterSignature != filterSignature(filterState.state.value)) return
        series = snapshot.series
        downloadedSeriesIds = snapshot.downloadedSeriesIds
        totalSeriesPages = snapshot.totalSeriesPages
        totalSeriesCount = snapshot.totalSeriesCount
        currentSeriesPage = 1
        mutableState.value = LoadState.Success(Unit)
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
            filter.addConditionTo(this)
        }

        // Letter filter is applied by SeriesFilter.addConditionTo above
        // via titleSort.beginsWith — accurate, indexed, server-side.
        return seriesApi.getSeriesList(
            conditionBuilder = condition,
            fulltextSearch = filter.searchTerm.ifBlank { null },
            pageRequest = KomgaPageRequest(
                size = pageLoadSize.value,
                pageIndex = page - 1,
                sort = filter.sortOrder.komgaSort
            )
        )
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