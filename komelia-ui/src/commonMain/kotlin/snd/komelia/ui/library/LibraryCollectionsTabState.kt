package snd.komelia.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.komga.api.KomgaCollectionsApi
import snd.komelia.ui.LoadState
import snd.komelia.ui.LoadState.Loading
import snd.komelia.ui.LoadState.Success
import snd.komelia.ui.LoadState.Uninitialized
import snd.komga.client.collection.KomgaCollection
import snd.komga.client.collection.KomgaCollectionId
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.sse.KomgaEvent
import snd.komga.client.sse.KomgaEvent.CollectionEvent

class LibraryCollectionsTabState(
    private val collectionApi: KomgaCollectionsApi,
    private val appNotifications: AppNotifications,
    private val events: SharedFlow<KomgaEvent>,
    private val library: StateFlow<KomgaLibrary?>,
    val cardWidth: StateFlow<Dp>,
) : StateScreenModel<LoadState<Unit>>(Uninitialized) {
    private var fetchedCollections: List<KomgaCollection> = emptyList()
    var collections: List<KomgaCollection> by mutableStateOf(emptyList())
        private set
    var totalPages by mutableStateOf(1)
        private set
    var totalCollections by mutableStateOf(0)
        private set
    var currentPage by mutableStateOf(1)
        private set
    var pageSize by mutableStateOf(50)
        private set

    val searchQuery = MutableStateFlow("")
    val sortOrder = MutableStateFlow(CollectionReadListSortOrder.NAME_ASC)

    val progressByCollection = mutableStateMapOf<KomgaCollectionId, Float>()
    private val progressInFlight = mutableSetOf<KomgaCollectionId>()

    private val reloadEventsEnabled = MutableStateFlow(true)
    private val collectionsReloadJobsFlow = MutableSharedFlow<Unit>(1, 0, BufferOverflow.DROP_OLDEST)

    fun loadProgressFor(collectionId: KomgaCollectionId) {
        if (collectionId in progressByCollection || collectionId in progressInFlight) return
        progressInFlight += collectionId
        screenModelScope.launch {
            try {
                val series = collectionApi.getSeriesForCollection(
                    collectionId,
                    pageRequest = KomgaPageRequest(unpaged = true),
                ).content
                val total = series.sumOf { it.booksCount }
                if (total > 0) {
                    val read = series.sumOf { it.booksReadCount }
                    progressByCollection[collectionId] = read.toFloat() / total.toFloat()
                }
            } catch (_: Exception) {
                // Silent fail
            } finally {
                progressInFlight -= collectionId
            }
        }
    }

    @OptIn(FlowPreview::class)
    fun initialize() {
        if (state.value !is Uninitialized) return

        screenModelScope.launch { loadCollections(1) }
        startKomgaEventListener()

        collectionsReloadJobsFlow.onEach {
            reloadEventsEnabled.first { it }
            loadCollections(currentPage)
            delay(1000)
        }.launchIn(screenModelScope)

        // Search reload (debounced, server-side)
        searchQuery.drop(1).debounce(300).onEach {
            loadCollections(1)
        }.launchIn(screenModelScope)

        // Sort reorder (client-side, no re-fetch)
        sortOrder.drop(1).onEach { applySort() }.launchIn(screenModelScope)
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onSortOrderChange(order: CollectionReadListSortOrder) {
        sortOrder.value = order
    }

    private fun applySort() {
        collections = when (sortOrder.value) {
            CollectionReadListSortOrder.NAME_ASC -> fetchedCollections.sortedBy { it.name.lowercase() }
            CollectionReadListSortOrder.NAME_DESC -> fetchedCollections.sortedByDescending { it.name.lowercase() }
            CollectionReadListSortOrder.CREATED_DESC -> fetchedCollections.sortedByDescending { it.createdDate }
            CollectionReadListSortOrder.CREATED_ASC -> fetchedCollections.sortedBy { it.createdDate }
            CollectionReadListSortOrder.COUNT_DESC -> fetchedCollections.sortedByDescending { it.seriesIds.size }
            CollectionReadListSortOrder.COUNT_ASC -> fetchedCollections.sortedBy { it.seriesIds.size }
        }
    }

    fun reload() {
        screenModelScope.launch { loadCollections(1) }
    }

    fun onCollectionDelete(collectionId: KomgaCollectionId) {
        appNotifications.runCatchingToNotifications(screenModelScope) {
            collectionApi.deleteOne(collectionId)
        }
    }

    fun onPageChange(pageNumber: Int) {
        screenModelScope.launch { loadCollections(pageNumber) }
    }

    fun onPageSizeChange(pageSize: Int) {
        this.pageSize = pageSize
        screenModelScope.launch { loadCollections(1) }
    }

    private suspend fun loadCollections(page: Int) {
        appNotifications.runCatchingToNotifications {

            if (totalCollections > pageSize) mutableState.value = Loading

            val pageRequest = KomgaPageRequest(pageIndex = page - 1, size = pageSize)
            val libraryIds = listOfNotNull(library.value?.id)
            val search = searchQuery.value.takeIf { it.isNotBlank() }
            val collectionsPage = collectionApi.getAll(
                search = search,
                libraryIds = libraryIds,
                pageRequest = pageRequest,
            )

            currentPage = collectionsPage.number + 1
            totalPages = collectionsPage.totalPages
            totalCollections = collectionsPage.totalElements
            fetchedCollections = collectionsPage.content
            applySort()
            mutableState.value = Success(Unit)

        }.onFailure {
            mutableState.value = LoadState.Error(it)
        }
    }

    fun stopKomgaEventHandler() {
        reloadEventsEnabled.value = false
    }

    fun startKomgaEventHandler() {
        reloadEventsEnabled.value = true
    }

    private fun startKomgaEventListener() {
        events.onEach {
            when (it) {
                is CollectionEvent -> collectionsReloadJobsFlow.tryEmit(Unit)
                else -> {}
            }
        }.launchIn(screenModelScope)
    }
}
