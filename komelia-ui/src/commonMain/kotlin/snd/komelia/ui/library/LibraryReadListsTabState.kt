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
import snd.komelia.komga.api.KomgaReadListApi
import snd.komelia.ui.LoadState
import snd.komelia.ui.LoadState.Loading
import snd.komelia.ui.LoadState.Uninitialized
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.readlist.KomgaReadList
import snd.komga.client.readlist.KomgaReadListId
import snd.komga.client.sse.KomgaEvent

class LibraryReadListsTabState(
    private val readListApi: KomgaReadListApi,
    private val appNotifications: AppNotifications,
    private val komgaEvents: SharedFlow<KomgaEvent>,
    val library: StateFlow<KomgaLibrary?>?,
    val cardWidth: StateFlow<Dp>,
) : StateScreenModel<LoadState<Unit>>(Uninitialized) {

    private var fetchedReadLists: List<KomgaReadList> = emptyList()
    var readLists: List<KomgaReadList> by mutableStateOf(emptyList())
        private set
    var totalPages by mutableStateOf(1)
        private set
    var totalReadLists by mutableStateOf(0)
        private set
    var currentPage by mutableStateOf(1)
        private set
    var pageSize by mutableStateOf(50)
        private set

    val searchQuery = MutableStateFlow("")
    val sortOrder = MutableStateFlow(CollectionReadListSortOrder.NAME_ASC)

    val progressByReadList = mutableStateMapOf<KomgaReadListId, Float>()
    private val progressInFlight = mutableSetOf<KomgaReadListId>()

    private val reloadEventsEnabled = MutableStateFlow(true)
    private val readListsReloadJobsFlow = MutableSharedFlow<Unit>(1, 0, BufferOverflow.DROP_OLDEST)

    fun loadProgressFor(readListId: KomgaReadListId) {
        if (readListId in progressByReadList || readListId in progressInFlight) return
        progressInFlight += readListId
        screenModelScope.launch {
            try {
                val books = readListApi.getBooksForReadList(
                    readListId,
                    pageRequest = KomgaPageRequest(unpaged = true),
                ).content
                if (books.isNotEmpty()) {
                    val read = books.count { it.readProgress?.completed == true }
                    progressByReadList[readListId] = read.toFloat() / books.size.toFloat()
                }
            } catch (_: Exception) {
                // Silent fail — no progress bar shown for this card
            } finally {
                progressInFlight -= readListId
            }
        }
    }

    @OptIn(FlowPreview::class)
    fun initialize() {
        if (state.value !is Uninitialized) return
        screenModelScope.launch { loadReadLists(1) }
        startKomgaEventListener()

        readListsReloadJobsFlow.onEach {
            reloadEventsEnabled.first { it }
            loadReadLists(currentPage)
            delay(1000)
        }.launchIn(screenModelScope)

        searchQuery.drop(1).debounce(300).onEach {
            loadReadLists(1)
        }.launchIn(screenModelScope)

        sortOrder.drop(1).onEach { applySort() }.launchIn(screenModelScope)
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onSortOrderChange(order: CollectionReadListSortOrder) {
        sortOrder.value = order
    }

    private fun applySort() {
        readLists = when (sortOrder.value) {
            CollectionReadListSortOrder.NAME_ASC -> fetchedReadLists.sortedBy { it.name.lowercase() }
            CollectionReadListSortOrder.NAME_DESC -> fetchedReadLists.sortedByDescending { it.name.lowercase() }
            CollectionReadListSortOrder.CREATED_DESC -> fetchedReadLists.sortedByDescending { it.createdDate }
            CollectionReadListSortOrder.CREATED_ASC -> fetchedReadLists.sortedBy { it.createdDate }
            CollectionReadListSortOrder.COUNT_DESC -> fetchedReadLists.sortedByDescending { it.bookIds.size }
            CollectionReadListSortOrder.COUNT_ASC -> fetchedReadLists.sortedBy { it.bookIds.size }
        }
    }

    fun reload() {
        screenModelScope.launch { loadReadLists(1) }
    }

    fun onReadListDelete(readListId: KomgaReadListId) {
        appNotifications.runCatchingToNotifications(screenModelScope) {
            readListApi.deleteOne(readListId)
        }
    }

    fun onPageChange(pageNumber: Int) {
        screenModelScope.launch { loadReadLists(pageNumber) }
    }

    fun onPageSizeChange(pageSize: Int) {
        this.pageSize = pageSize
        screenModelScope.launch { loadReadLists(1) }
    }

    private suspend fun loadReadLists(page: Int) {
        appNotifications.runCatchingToNotifications {

            if (totalReadLists > pageSize) mutableState.value = Loading

            val library = this.library?.value
            val libraryIds = if (library != null) listOf(library.id) else emptyList()
            val pageRequest = KomgaPageRequest(pageIndex = page - 1, size = pageSize)
            val search = searchQuery.value.takeIf { it.isNotBlank() }
            val readListsPage = readListApi.getAll(
                search = search,
                libraryIds = libraryIds,
                pageRequest = pageRequest,
            )

            currentPage = readListsPage.number + 1
            totalPages = readListsPage.totalPages
            totalReadLists = readListsPage.totalElements
            fetchedReadLists = readListsPage.content
            applySort()
            mutableState.value = LoadState.Success(Unit)

        }.onFailure { mutableState.value = LoadState.Error(it) }
    }

    fun stopKomgaEventHandler() {
        reloadEventsEnabled.value = false
    }

    fun startKomgaEventHandler() {
        reloadEventsEnabled.value = true
    }

    private fun startKomgaEventListener() {
        komgaEvents.onEach {
            when (it) {
                is KomgaEvent.ReadListEvent -> readListsReloadJobsFlow.tryEmit(Unit)
                else -> {}
            }
        }.launchIn(screenModelScope)
    }
}