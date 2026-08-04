package snd.komelia.ui.book

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaReadListApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.tasks.OfflineTaskEmitter
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.LoadState
import snd.komelia.ui.LoadState.Error
import snd.komelia.ui.LoadState.Loading
import snd.komelia.ui.LoadState.Success
import snd.komelia.ui.LoadState.Uninitialized
import snd.komelia.ui.common.cards.defaultCardWidth
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.readlist.BookReadListsState
import snd.komga.client.book.KomgaBookId
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort.KomgaBooksSort
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.search.allOfBooks
import snd.komga.client.sse.KomgaEvent
import snd.komga.client.sse.KomgaEvent.BookAdded
import snd.komga.client.sse.KomgaEvent.BookChanged
import snd.komga.client.sse.KomgaEvent.ReadProgressChanged
import snd.komga.client.sse.KomgaEvent.ReadProgressDeleted

class BookViewModel(
    book: KomeliaBook?,
    bookId: KomgaBookId,
    private val bookSiblingsContext: BookSiblingsContext,
    private val bookApi: KomgaBookApi,
    private val seriesApi: KomgaSeriesApi,
    private val notifications: AppNotifications,
    private val komgaEvents: SharedFlow<KomgaEvent>,
    private val libraries: StateFlow<List<KomgaLibrary>>,
    private val taskEmitter: OfflineTaskEmitter,
    settingsRepository: CommonSettingsRepository,
    readListApi: KomgaReadListApi,
) : StateScreenModel<LoadState<Unit>>(Uninitialized) {

    var library by mutableStateOf<KomgaLibrary?>(null)
        private set
    val book = MutableStateFlow(book)
    private val currentBookId = MutableStateFlow(bookId)
    var isExpanded by mutableStateOf(false)
    val publisher = MutableStateFlow<String?>(null)

    private val reloadEventsEnabled = MutableStateFlow(true)
    private val reloadJobsFlow = MutableSharedFlow<Unit>(1, 0, DROP_OLDEST)

    val readListsState = BookReadListsState(
        book = this.book,
        bookApi = bookApi,
        readListApi = readListApi,
        notifications = notifications,
        komgaEvents = komgaEvents,
        stateScope = screenModelScope,
    )
    val cardWidth = settingsRepository.getCardWidth().map { it.dp }
        .stateIn(screenModelScope, Eagerly, defaultCardWidth.dp)

    val siblingBooks = MutableStateFlow<List<KomeliaBook>>(emptyList())

    // seriesApi passed on purpose: the volume menu offers series-level actions
    // (favorite / to read / delete the whole series), which is where the user
    // actually decides those things.
    val bookMenuActions = BookMenuActions(bookApi, notifications, screenModelScope, taskEmitter, seriesApi)

    suspend fun initialize() {
        if (state.value != Uninitialized) return

        if (book.value == null) loadBook()
        else mutableState.value = Success(Unit)
        loadLibrary()
        readListsState.initialize()
        loadSiblingBooks()
        loadPublisher()
        startKomgaEventListener()

        reloadJobsFlow.onEach {
            reloadEventsEnabled.first { it }
            reload()
        }.launchIn(screenModelScope)
    }

    fun reload() {
        screenModelScope.launch {
            loadBook()
            loadLibrary()
            readListsState.reload()
        }
    }

    fun loadSiblingBooks() {
        screenModelScope.launch {
            val seriesId = book.value?.seriesId ?: return@launch
            notifications.runCatchingToNotifications {
                val page = bookApi.getBookList(
                    conditionBuilder = allOfBooks {
                        seriesId { isEqualTo(seriesId) }
                        if (bookSiblingsContext is BookSiblingsContext.Series) {
                            bookSiblingsContext.filter?.addConditionTo(this)
                        }
                    },
                    pageRequest = KomgaPageRequest(
                        unpaged = true,
                        sort = (bookSiblingsContext as? BookSiblingsContext.Series)?.filter?.sortOrder?.komgaSort
                            ?: KomgaBooksSort.byNumberAsc()
                    )
                )
                siblingBooks.value = page.content
            }
        }
    }

    private fun loadPublisher() {
        screenModelScope.launch {
            val seriesId = book.value?.seriesId ?: return@launch
            runCatching {
                publisher.value = seriesApi.getOneSeries(seriesId).metadata.publisher
            }
        }
    }

    private suspend fun loadBook() {
        notifications.runCatchingToNotifications {
            mutableState.value = Loading
            val loadedBook = bookApi.getOne(currentBookId.value)
            book.value = loadedBook
        }
            .onSuccess { mutableState.value = Success(Unit) }
            .onFailure { mutableState.value = Error(it) }
    }

    private fun loadLibrary() {
        val book = requireNotNull(book.value)
        library = libraries.value.firstOrNull { library -> library.id == book.libraryId }
    }

    fun stopKomgaEventHandler() {
        reloadEventsEnabled.value = false
        readListsState.stopKomgaEventHandler()
    }

    fun startKomgaEventsHandler() {
        reloadEventsEnabled.value = true
        readListsState.startKomgaEventHandler()
    }

    fun setCurrentBook(book: KomeliaBook) {
        this.book.value = book
        this.currentBookId.value = book.id
        loadLibrary()
    }

    fun onBookDownload() {
        screenModelScope.launch {
            book.value?.let { taskEmitter.downloadBook(it.id) }
        }
    }

    fun onBookDownloadDelete() {
        screenModelScope.launch {
            book.value?.let { taskEmitter.deleteBook(it.id) }
        }
    }

    private fun startKomgaEventListener() {
        komgaEvents.onEach { event ->
            val bookId = currentBookId.value
            when (event) {
                is BookChanged, is BookAdded ->
                    if (event.bookId == bookId) reloadJobsFlow.tryEmit(Unit)

                is ReadProgressChanged, is ReadProgressDeleted ->
                    if (event.bookId == bookId) reloadJobsFlow.tryEmit(Unit)

                else -> {}
            }
        }.launchIn(screenModelScope)
    }

}