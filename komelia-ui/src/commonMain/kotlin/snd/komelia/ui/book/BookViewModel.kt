package snd.komelia.ui.book

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.delay
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
    private val similarityIndexRepository: snd.komelia.similarity.SimilarityIndexRepository,
    readListApi: KomgaReadListApi,
) : StateScreenModel<LoadState<Unit>>(Uninitialized) {

    var library by mutableStateOf<KomgaLibrary?>(null)
        private set
    val book = MutableStateFlow(book)

    /**
     * The genres of the series this book belongs to.
     *
     * They are series metadata, and the book screen never loads its series —
     * asking the server for it would be a request for three words. The local
     * term index already holds them, so this is a primary-key read. Empty when
     * the library was never indexed, which is a silence rather than a wait.
     */
    var seriesGenres by mutableStateOf<List<String>>(emptyList())
        private set
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

    private suspend fun loadSeriesGenres() {
        val seriesId = book.value?.seriesId?.value ?: return
        val entry = runCatching { similarityIndexRepository.entryOf(seriesId) }.getOrNull() ?: return
        seriesGenres = entry.terms.genres.toList()
    }

    suspend fun initialize() {
        if (state.value != Uninitialized) return

        if (book.value == null) loadBook()
        else mutableState.value = Success(Unit)
        loadLibrary()
        readListsState.initialize()
        loadSiblingBooks()
        loadPublisher()
        loadSeriesGenres()
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

    /**
     * Re-reads the book on the way out of the reader, silently.
     *
     * Not [reload]: that one flips the screen to Loading and would blank a page
     * the user is already looking at. Here the book is on screen and correct
     * apart from its progress, so the new copy simply replaces the old one.
     *
     * The 600ms is the same race Home pays: the reader flushes its final read
     * progress fire-and-forget on dispose, so asking immediately can read back
     * the value from before the session.
     */
    fun refreshAfterReading() {
        screenModelScope.launch {
            delay(600)
            runCatching { bookApi.getOne(currentBookId.value) }
                .onSuccess {
                    book.value = it
                    loadLibrary()
                }
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