package snd.komelia.ui.series

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaCollectionsApi
import snd.komelia.komga.api.KomgaReferentialApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.offline.tasks.OfflineTaskEmitter
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.ui.LoadState
import snd.komelia.ui.LoadState.Error
import snd.komelia.ui.LoadState.Loading
import snd.komelia.ui.LoadState.Success
import snd.komelia.ui.LoadState.Uninitialized
import snd.komelia.ui.collection.SeriesCollectionsState
import snd.komelia.ui.common.cards.defaultCardWidth
import snd.komelia.ui.common.menus.SeriesMenuActions
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.sse.KomgaEvent
import snd.komelia.ui.library.LibrarySeriesTabState
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.search.allOfSeries
import snd.komelia.ui.series.SeriesNavigationContext

class SeriesViewModel(
    series: KomgaSeries?,
    private val libraries: StateFlow<List<KomgaLibrary>>,
    private val seriesId: KomgaSeriesId,
    private val notifications: AppNotifications,
    private val events: SharedFlow<KomgaEvent>,
    private val seriesApi: KomgaSeriesApi,
    private val taskEmitter: OfflineTaskEmitter,
    bookApi: KomgaBookApi,
    collectionApi: KomgaCollectionsApi,
    referentialApi: KomgaReferentialApi,
    settingsRepository: CommonSettingsRepository,
    defaultTab: SeriesTab,
) : StateScreenModel<LoadState<Unit>>(Uninitialized) {

    private val reloadEventsEnabled = MutableStateFlow(true)
    private val reloadJobsFlow = MutableSharedFlow<Unit>(1, 0, BufferOverflow.DROP_OLDEST)

    val series = MutableStateFlow(series?.withSortedTags())
    val hasNextSiblingSeries = MutableStateFlow(false)
    val hasPreviousSiblingSeries = MutableStateFlow(false)
    val library = MutableStateFlow<KomgaLibrary?>(null)
    var currentTab by mutableStateOf(defaultTab)
    var isExpanded by mutableStateOf(false)
    val cardWidth = settingsRepository.getCardWidth().map { it.dp }
        .stateIn(screenModelScope, Eagerly, defaultCardWidth.dp)

    val booksState = SeriesBooksState(
        series = this.series,
        settingsRepository = settingsRepository,
        notifications = notifications,
        bookApi = bookApi,
        events = events,
        screenModelScope = screenModelScope,
        cardWidth = cardWidth,
        referentialApi = referentialApi,
        taskEmitter = taskEmitter
    )
    val collectionsState = SeriesCollectionsState(
        series = this.series,
        notifications = notifications,
        seriesApi = seriesApi,
        collectionApi = collectionApi,
        events = events,
        screenModelScope = screenModelScope,
        cardWidth = cardWidth,
    )

    suspend fun initialize() {
        if (state.value !is Uninitialized) return

        val providedSeries = series.value
        if (providedSeries == null) loadSeries()
        else {
            runCatching {
                library.value = getLibraryOrThrow(providedSeries)
                mutableState.value = Success(Unit)
            }.onFailure { mutableState.value = Error(it) }
        }

        series.filterNotNull()
            .combine(libraries) { series, libraries ->
                val newLibrary = libraries.firstOrNull { it.id == series.libraryId }
                library.value = newLibrary
            }.launchIn(screenModelScope)

        booksState.initialize()
        collectionsState.initialize()
        screenModelScope.launch { updateSiblingAvailability() }
        startKomgaEventListener()

        reloadJobsFlow.onEach {
            reloadEventsEnabled.first { it }
            loadSeries()
        }.launchIn(screenModelScope)
    }

    fun reload() {
        screenModelScope.launch {
            mutableState.value = Loading
            loadSeries()
            booksState.reload()
        }
    }

    fun openRandomSiblingSeries(onSeriesSelected: (KomgaSeries) -> Unit) {
        val currentSeries = series.value ?: return
        notifications.runCatchingToNotifications(screenModelScope) {
            val condition = allOfSeries {
                library { isEqualTo(currentSeries.libraryId) }
            }
            val page = seriesApi.getSeriesList(
                conditionBuilder = condition,
                fulltextSearch = null,
                pageRequest = KomgaPageRequest(
                    size = 1,
                    pageIndex = 0,
                    sort = LibrarySeriesTabState.SeriesSort.RANDOM.komgaSort
                )
            )
            page.content.firstOrNull()?.let(onSeriesSelected)
        }
    }

    private suspend fun updateSiblingAvailability() {
        val currentSeries = series.value ?: return
        val ctx = SeriesNavigationContext.get(currentSeries.id)
        if (ctx == null) {
            // Pas de contexte de liste filtree : on tente une recherche library + sort par titre
            try {
                val page = seriesApi.getSeriesList(
                    conditionBuilder = allOfSeries {
                        library { isEqualTo(currentSeries.libraryId) }
                    },
                    fulltextSearch = null,
                    pageRequest = snd.komga.client.common.KomgaPageRequest(
                        size = 1,
                        pageIndex = 0,
                        sort = snd.komga.client.common.KomgaSort.KomgaSeriesSort.byTitleAsc()
                    )
                )
                hasNextSiblingSeries.value = page.totalElements > 1
                hasPreviousSiblingSeries.value = page.totalElements > 1
            } catch (_: Throwable) {
                hasNextSiblingSeries.value = false
                hasPreviousSiblingSeries.value = false
            }
            return
        }
        // Avec contexte : on a deja la position, donc on peut deduire les flags
        try {
            val totalNeeded = (ctx.currentPage - 1) * ctx.pageSize + ctx.seriesIndexInPage
            hasPreviousSiblingSeries.value = totalNeeded > 0
            // Pour le next, il faut connaitre le total : 1 requete legere
            val page = seriesApi.getSeriesList(
                conditionBuilder = allOfSeries {
                    ctx.libraryId?.let { library { isEqualTo(it) } }
                    ctx.filter.addConditionTo(this)
                },
                fulltextSearch = ctx.filter.searchTerm.ifBlank { null },
                pageRequest = snd.komga.client.common.KomgaPageRequest(
                    size = 1,
                    pageIndex = 0,
                    sort = ctx.filter.sortOrder.komgaSort
                )
            )
            hasNextSiblingSeries.value = totalNeeded < (page.totalElements - 1)
        } catch (_: Throwable) {
            hasNextSiblingSeries.value = false
            hasPreviousSiblingSeries.value = false
        }
    }

    fun openNextSiblingSeries(onSeriesSelected: (KomgaSeries) -> Unit) {
        val currentSeries = series.value ?: return
        val ctx = SeriesNavigationContext.get(currentSeries.id)
        notifications.runCatchingToNotifications(screenModelScope) {
            val targetIndex = if (ctx != null) {
                (ctx.currentPage - 1) * ctx.pageSize + ctx.seriesIndexInPage + 1
            } else 1
            val pageSize = ctx?.pageSize ?: 50
            val pageIndex = targetIndex / pageSize
            val withinPage = targetIndex % pageSize

            val page = seriesApi.getSeriesList(
                conditionBuilder = allOfSeries {
                    if (ctx != null) {
                        ctx.libraryId?.let { library { isEqualTo(it) } }
                        ctx.filter.addConditionTo(this)
                    } else {
                        library { isEqualTo(currentSeries.libraryId) }
                    }
                },
                fulltextSearch = ctx?.filter?.searchTerm?.ifBlank { null },
                pageRequest = snd.komga.client.common.KomgaPageRequest(
                    size = pageSize,
                    pageIndex = pageIndex,
                    sort = ctx?.filter?.sortOrder?.komgaSort ?: snd.komga.client.common.KomgaSort.KomgaSeriesSort.byTitleAsc()
                )
            )
            val target = page.content.getOrNull(withinPage) ?: return@runCatchingToNotifications
            SeriesNavigationContext.put(target.id, SeriesNavigationContext.SeriesListContext(
                libraryId = ctx?.libraryId ?: currentSeries.libraryId,
                filter = ctx?.filter ?: SeriesFilter(),
                pageSize = pageSize,
                currentPage = pageIndex + 1,
                seriesIndexInPage = withinPage
            ))
            onSeriesSelected(target)
        }
    }

    fun openPreviousSiblingSeries(onSeriesSelected: (KomgaSeries) -> Unit) {
        val currentSeries = series.value ?: return
        val ctx = SeriesNavigationContext.get(currentSeries.id) ?: return
        notifications.runCatchingToNotifications(screenModelScope) {
            val currentGlobalIndex = (ctx.currentPage - 1) * ctx.pageSize + ctx.seriesIndexInPage
            if (currentGlobalIndex <= 0) return@runCatchingToNotifications
            val targetIndex = currentGlobalIndex - 1
            val pageIndex = targetIndex / ctx.pageSize
            val withinPage = targetIndex % ctx.pageSize

            val page = seriesApi.getSeriesList(
                conditionBuilder = allOfSeries {
                    ctx.libraryId?.let { library { isEqualTo(it) } }
                    ctx.filter.addConditionTo(this)
                },
                fulltextSearch = ctx.filter.searchTerm.ifBlank { null },
                pageRequest = snd.komga.client.common.KomgaPageRequest(
                    size = ctx.pageSize,
                    pageIndex = pageIndex,
                    sort = ctx.filter.sortOrder.komgaSort
                )
            )
            val target = page.content.getOrNull(withinPage) ?: return@runCatchingToNotifications
            SeriesNavigationContext.put(target.id, ctx.copy(
                currentPage = pageIndex + 1,
                seriesIndexInPage = withinPage
            ))
            onSeriesSelected(target)
        }
    }

    fun seriesMenuActions() = SeriesMenuActions(seriesApi, notifications, taskEmitter, screenModelScope)

    fun onTabChange(tab: SeriesTab) {
        this.currentTab = tab
    }

    fun onDownload() {
        screenModelScope.launch {
            series.value?.let { taskEmitter.downloadSeries(it.id) }
        }
    }

    private suspend fun loadSeries() {
        notifications.runCatchingToNotifications {
            mutableState.value = Loading
            val series = seriesApi.getOneSeries(seriesId)
            this.series.value = series.withSortedTags()
            this.library.value = getLibraryOrThrow(series)

            mutableState.value = Success(Unit)

        }.onFailure { mutableState.value = Error(it) }
    }

    private fun getLibraryOrThrow(series: KomgaSeries): KomgaLibrary {
        val library = this.libraries.value.firstOrNull { it.id == series.libraryId }
        if (library == null) {
            throw IllegalStateException("Failed to find library for series ${series.metadata.title}")
        }
        return library

    }

    fun stopKomgaEventHandler() {
        reloadEventsEnabled.value = false
        booksState.stopKomgaEventHandler()
        collectionsState.stopKomgaEventHandler()
    }

    fun startKomgaEventHandler() {
        reloadEventsEnabled.value = true
        booksState.startKomgaEventHandler()
        collectionsState.startKomgaEventHandler()
    }

    private fun startKomgaEventListener() {
        events.onEach { event ->
            when (event) {
                is KomgaEvent.SeriesChanged -> if (event.seriesId == seriesId) reloadJobsFlow.tryEmit(Unit)
                else -> {}
            }
        }.launchIn(screenModelScope)
    }

    enum class SeriesTab {
        BOOKS,
        COLLECTIONS
    }

    private fun KomgaSeries.withSortedTags() = this.copy(
        metadata = this.metadata.copy(
            tags = this.metadata.tags.sorted(),
            genres = this.metadata.genres.sorted()
        )
    )
}
