package snd.komelia.ui.reader.epub

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.Navigator
import coil3.PlatformContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import snd.komelia.AppNotifications
import snd.komelia.AppWindowState
import snd.komelia.ManagedKomgaEvents
import snd.komelia.fonts.UserFontsRepository
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaReadListApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.EpubReaderSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import snd.komelia.settings.model.EpubReaderType
import snd.komelia.settings.model.EpubReaderType.EPUB3_READER
import snd.komelia.settings.model.EpubReaderType.KOMGA_EPUB
import snd.komelia.settings.model.EpubReaderType.TTSU_EPUB
import snd.komelia.sync.ReaderSyncService
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.LoadState
import snd.komelia.ui.platform.PlatformType
import snd.komga.client.book.KomgaBookId
import snd.webview.KomeliaWebview

class EpubReaderViewModel(
    private val bookId: KomgaBookId,
    private val book: KomeliaBook?,
    private val markReadProgress: Boolean,
    private val bookApi: KomgaBookApi,
    private val seriesApi: KomgaSeriesApi,
    private val readListApi: KomgaReadListApi,
    private val settingsRepository: CommonSettingsRepository,
    private val epubSettingsRepository: EpubReaderSettingsRepository,
    private val epubBookmarkRepository: snd.komelia.bookmarks.EpubBookmarkRepository,
    private val bookAnnotationRepository: snd.komelia.annotations.BookAnnotationRepository,
    private val readerSyncService: ReaderSyncService,
    private val komgaEvents: ManagedKomgaEvents,
    private val fontsRepository: UserFontsRepository,
    private val notifications: AppNotifications,
    private val windowState: AppWindowState,
    private val platformType: PlatformType,
    private val platformContext: PlatformContext,
    private val bookSiblingsContext: BookSiblingsContext,
    private val onExit: (KomeliaBook) -> Unit,
) : StateScreenModel<LoadState<EpubReaderState>>(LoadState.Uninitialized) {

    val readerType = MutableStateFlow<EpubReaderType?>(null)

    private val _pendingReaderState = MutableStateFlow<EpubReaderState?>(null)
    val pendingReaderState = _pendingReaderState.asStateFlow()

    suspend fun initialize(navigator: Navigator) {
        if (settingsRepository.getKeepReaderScreenOn().first()) {
            windowState.setKeepScreenOn(true)
        }
        when (val state = state.value) {
            LoadState.Loading, is LoadState.Error -> {}
            is LoadState.Success<EpubReaderState> -> state.value.initialize(navigator)
            LoadState.Uninitialized -> {

                val selectedType = epubSettingsRepository.getReaderType().first()
                readerType.value = selectedType
                when (selectedType) {
                    KOMGA_EPUB -> {
                        val komgaState = KomgaEpubReaderState(
                            bookId = bookId,
                            book = book,
                            bookApi = bookApi,
                            seriesApi = seriesApi,
                            readListApi = readListApi,
                            settingsRepository = settingsRepository,
                            notifications = notifications,
                            markReadProgress = markReadProgress,
                            epubSettingsRepository = epubSettingsRepository,
                            windowState = windowState,
                            platformType = platformType,
                            coroutineScope = screenModelScope,
                            bookSiblingsContext = bookSiblingsContext,
                            onExit = onExit,
                        )
                        komgaState.initialize(navigator)
                        when (val res = komgaState.state.value) {
                            is LoadState.Error -> mutableState.value = LoadState.Error(res.exception)
                            is LoadState.Success<Unit> -> mutableState.value = LoadState.Success(komgaState)
                            LoadState.Loading, LoadState.Uninitialized -> LoadState.Loading
                        }
                    }

                    TTSU_EPUB -> {
                        val ttsuState = TtsuReaderState(
                            bookId = bookId,
                            book = book,
                            bookApi = bookApi,
                            notifications = notifications,
                            markReadProgress = markReadProgress,
                            settingsRepository = settingsRepository,
                            epubSettingsRepository = epubSettingsRepository,
                            fontsRepository = fontsRepository,
                            windowState = windowState,
                            platformType = platformType,
                            coroutineScope = screenModelScope,
                            bookSiblingsContext = bookSiblingsContext,
                            onExit = onExit,
                        )
                        ttsuState.initialize(navigator)
                        when (val res = ttsuState.state.value) {
                            is LoadState.Error -> mutableState.value = LoadState.Error(res.exception)
                            is LoadState.Success<Unit> -> mutableState.value = LoadState.Success(ttsuState)
                            LoadState.Loading, LoadState.Uninitialized -> LoadState.Loading
                        }
                    }

                    EPUB3_READER -> {
                        val epub3State = createEpub3ReaderState(
                            bookId = bookId,
                            book = book,
                            platformContext = platformContext,
                            markReadProgress = markReadProgress,
                            bookApi = bookApi,
                            seriesApi = seriesApi,
                            readListApi = readListApi,
                            settingsRepository = settingsRepository,
                            epubSettingsRepository = epubSettingsRepository,
                            epubBookmarkRepository = epubBookmarkRepository,
                            bookAnnotationRepository = bookAnnotationRepository,
                            readerSyncService = readerSyncService,
                            komgaEvents = komgaEvents,
                            fontsRepository = fontsRepository,
                            notifications = notifications,
                            windowState = windowState,
                            platformType = platformType,
                            coroutineScope = screenModelScope,
                            bookSiblingsContext = bookSiblingsContext,
                            onExit = onExit,
                        )
                        _pendingReaderState.value = epub3State
                        epub3State.initialize(navigator)
                        _pendingReaderState.value = null
                        when (val res = epub3State.state.value) {
                            is LoadState.Error -> mutableState.value = LoadState.Error(res.exception)
                            is LoadState.Success<Unit> -> mutableState.value = LoadState.Success(epub3State)
                            LoadState.Loading, LoadState.Uninitialized -> LoadState.Loading
                        }
                    }
                }
            }
        }
    }

    override fun onDispose() {
        windowState.setKeepScreenOn(false)
    }
}

interface EpubReaderState {
    val state: StateFlow<LoadState<Unit>>
    val book: StateFlow<KomeliaBook?>
    val loadingSteps: StateFlow<List<EpubLoadingStep>>
    val onExit: (KomeliaBook) -> Unit
    suspend fun initialize(navigator: Navigator)
    fun onWebviewCreated(webview: KomeliaWebview)
    fun onBackButtonPress()
    fun closeWebview()
}
