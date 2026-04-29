package snd.komelia.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaCollectionsApi
import snd.komelia.komga.api.KomgaLibraryApi
import snd.komelia.komga.api.KomgaReadListApi
import snd.komelia.komga.api.KomgaReferentialApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.tasks.OfflineTaskEmitter
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.ui.LoadState
import snd.komelia.ui.LoadState.Error
import snd.komelia.ui.LoadState.Loading
import snd.komelia.ui.LoadState.Success
import snd.komelia.ui.LoadState.Uninitialized
import snd.komelia.ui.common.cards.defaultCardWidth
import snd.komelia.ui.common.menus.LibraryMenuActions
import snd.komelia.ui.library.LibraryTab.COLLECTIONS
import snd.komelia.ui.library.LibraryTab.READ_LISTS
import snd.komelia.ui.library.LibraryTab.SERIES
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort.KomgaBooksSort
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.search.allOfBooks
import snd.komga.client.sse.KomgaEvent
import snd.komga.client.sse.KomgaEvent.CollectionAdded
import snd.komga.client.sse.KomgaEvent.CollectionDeleted
import snd.komga.client.sse.KomgaEvent.ReadListAdded
import snd.komga.client.sse.KomgaEvent.ReadListDeleted

class LibraryViewModel(
    private val libraryApi: KomgaLibraryApi,
    private val collectionApi: KomgaCollectionsApi,
    private val readListsApi: KomgaReadListApi,
    private val taskEmitter: OfflineTaskEmitter,
    private val bookApi: KomgaBookApi,
    seriesApi: KomgaSeriesApi,
    referentialApi: KomgaReferentialApi,

    private val appNotifications: AppNotifications,
    private val komgaEvents: SharedFlow<KomgaEvent>,
    libraryFlow: Flow<KomgaLibrary?>,
    private val settingsRepository: CommonSettingsRepository,
    private val librarySeriesFiltersRepository: snd.komelia.libraryfilters.LibrarySeriesFiltersRepository,
) : StateScreenModel<LoadState<Unit>>(Uninitialized) {
    val library = libraryFlow.onEach { settingsRepository.putLastSelectedLibraryId(it?.id) }
        .stateIn(screenModelScope, SharingStarted.Eagerly, null)
    val cardWidth = settingsRepository.getCardWidth().map { Dp(it.toFloat()) }
        .stateIn(screenModelScope, SharingStarted.Eagerly, defaultCardWidth.dp)
    val showContinueReading = settingsRepository.getShowContinueReading()
        .stateIn(screenModelScope, SharingStarted.Eagerly, true)

    var currentTab by mutableStateOf(SERIES)
    var collectionsCount by mutableStateOf(0)
        private set
    var readListsCount by mutableStateOf(0)
        private set

    var keepReadingBooks by mutableStateOf<List<KomeliaBook>>(emptyList())
        private set

    private val reloadEventsEnabled = MutableStateFlow(true)
    private val reloadJobsFlow = MutableSharedFlow<Unit>(1, 0, DROP_OLDEST)

    val seriesTabState = LibrarySeriesTabState(
        bookApi = bookApi,
        seriesApi = seriesApi,
        referentialApi = referentialApi,
        notifications = appNotifications,
        komgaEvents = komgaEvents,
        settingsRepository = settingsRepository,
        library = library,
        cardWidth = cardWidth,
        taskEmitter = taskEmitter,
        librarySeriesFiltersRepository = librarySeriesFiltersRepository,
    )
    val collectionsTabState = LibraryCollectionsTabState(
        collectionApi = collectionApi,
        appNotifications = appNotifications,
        events = komgaEvents,
        library = library,
        cardWidth = cardWidth
    )
    val readListsTabState = LibraryReadListsTabState(
        readListApi = readListsApi,
        appNotifications = appNotifications,
        komgaEvents = komgaEvents,
        library = library,
        cardWidth = cardWidth
    )
    val showToolbar = seriesTabState.isInEditMode.map { !it }
        .stateIn(screenModelScope, SharingStarted.Eagerly, true)

    fun initialize(seriesFilter: SeriesScreenFilter? = null) {
        if (state.value !is Uninitialized) return

        if (seriesFilter != null) toBrowseTab()

        screenModelScope.launch {
            loadItemCounts()
            loadKeepReadingBooks()
        }
        startKomgaEventListener()

        reloadJobsFlow.onEach {
            reloadEventsEnabled.first { it }
            loadItemCounts()
            loadKeepReadingBooks()
            delay(1000)
        }.launchIn(screenModelScope)
    }

    fun reload() {
        mutableState.value = Loading
        screenModelScope.launch {
            loadItemCounts()
            loadKeepReadingBooks()
            when (currentTab) {
                SERIES -> seriesTabState.reload()
                COLLECTIONS -> collectionsTabState.reload()
                READ_LISTS -> readListsTabState.reload()
            }
        }
    }

    private suspend fun loadItemCounts() {
        if (state.value is Error) return

        appNotifications.runCatchingToNotifications {
            mutableState.value = Loading
            val pageRequest = KomgaPageRequest(size = 0)
            val libraryIds = listOfNotNull(library.value?.id)
            collectionsCount = collectionApi.getAll(libraryIds = libraryIds, pageRequest = pageRequest).totalElements
            readListsCount = readListsApi.getAll(libraryIds = libraryIds, pageRequest = pageRequest).totalElements

            if (collectionsCount == 0 && currentTab == COLLECTIONS) currentTab = SERIES
            if (readListsCount == 0 && currentTab == READ_LISTS) currentTab = SERIES
            mutableState.value = Success(Unit)
        }.onFailure { mutableState.value = Error(it) }
    }

    private suspend fun loadKeepReadingBooks() {
        val lib = library.value ?: return
        appNotifications.runCatchingToNotifications {
            keepReadingBooks = bookApi.getBookList(
                conditionBuilder = allOfBooks {
                    library { isEqualTo(lib.id) }
                    readStatus { isEqualTo(KomgaReadStatus.IN_PROGRESS) }
                },
                pageRequest = KomgaPageRequest(
                    sort = KomgaBooksSort.byReadDateDesc(),
                    size = 20
                )
            ).content
        }
    }

    fun toggleContinueReading() {
        screenModelScope.launch {
            settingsRepository.putShowContinueReading(!showContinueReading.value)
        }
    }

    fun toBrowseTab() {
        currentTab = SERIES
    }

    fun toCollectionsTab() {
        currentTab = COLLECTIONS
    }

    fun toReadListsTab() {
        currentTab = READ_LISTS
    }

    fun libraryActions() = LibraryMenuActions(libraryApi, appNotifications, taskEmitter, screenModelScope)

    fun stopKomgaEventHandler() {
        reloadEventsEnabled.value = false
    }

    fun startKomgaEventHandler() {
        reloadEventsEnabled.value = true

    }

    private fun startKomgaEventListener() {
        komgaEvents.onEach { event ->
            when (event) {
                is ReadListAdded, is ReadListDeleted -> reloadJobsFlow.tryEmit(Unit)
                is CollectionAdded, is CollectionDeleted -> reloadJobsFlow.tryEmit(Unit)
                is KomgaEvent.ReadProgressSeriesChanged,
                is KomgaEvent.ReadProgressSeriesDeleted -> reloadJobsFlow.tryEmit(Unit)

                else -> {}
            }
        }.launchIn(screenModelScope)
    }
}

enum class LibraryTab {
    SERIES,
    COLLECTIONS,
    READ_LISTS
}

