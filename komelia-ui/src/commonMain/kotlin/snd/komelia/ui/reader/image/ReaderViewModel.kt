package snd.komelia.ui.reader.image

import androidx.compose.ui.unit.IntSize
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.Navigator
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import snd.komelia.AppNotifications
import snd.komelia.ManagedKomgaEvents
import snd.komelia.color.repository.BookColorCorrectionRepository
import snd.komelia.image.BookImageLoader
import snd.komelia.image.KomeliaPanelDetector
import snd.komelia.image.KomeliaUpscaler
import snd.komelia.image.ReaderImage
import snd.komelia.image.ReaderImageFactory
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaReadListApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.onnxruntime.OnnxRuntime
import snd.komelia.reader.SeriesReaderOverridesRepository
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.ImageReaderSettingsRepository
import snd.komelia.image.ReadingDirection
import snd.komelia.settings.model.ContinuousReadingDirection
import snd.komelia.settings.model.PagedReadingDirection
import snd.komelia.settings.model.OcrSettings
import snd.komelia.settings.model.ReaderType.CONTINUOUS
import snd.komelia.settings.model.ReaderType.PAGED
import snd.komelia.settings.model.ReaderType.PANELS
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.LoadState
import snd.komelia.audiobook.AudioPositionRepository
import snd.komelia.ui.reader.image.continuous.ContinuousReaderState
import snd.komelia.ui.reader.image.paged.PagedReaderState
import snd.komelia.ui.reader.image.panels.PanelsReaderState
import snd.komelia.ui.settings.imagereader.ncnn.NcnnSettingsState
import snd.komelia.ui.settings.imagereader.onnxruntime.OnnxRuntimeSettingsState
import snd.komelia.ui.strings.AppStrings
import snd.komelia.updates.OnnxModelDownloader
import snd.komga.client.book.KomgaBookId
import snd.komga.client.series.KomgaSeries

private val cleanupScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
private val logger = KotlinLogging.logger { }

class ReaderViewModel(
    bookApi: KomgaBookApi,
    seriesApi: KomgaSeriesApi,
    readListApi: KomgaReadListApi,
    navigator: Navigator,
    appNotifications: AppNotifications,
    readerSettingsRepository: ImageReaderSettingsRepository,
    private val commonSettingsRepository: CommonSettingsRepository,
    imageLoader: BookImageLoader,
    appStrings: Flow<AppStrings>,
    readerImageFactory: ReaderImageFactory,
    markReadProgress: Boolean,
    currentBookId: MutableStateFlow<KomgaBookId?>,
    bookSiblingsContext: BookSiblingsContext,
    colorCorrectionRepository: BookColorCorrectionRepository,
    private val bookAnnotationRepository: snd.komelia.annotations.BookAnnotationRepository,
    private val epubBookmarkRepository: snd.komelia.bookmarks.EpubBookmarkRepository,
    private val audioBookmarkRepository: snd.komelia.audiobook.AudioBookmarkRepository,
    private val audioPositionRepository: snd.komelia.audiobook.AudioPositionRepository,
    private val readerSyncService: snd.komelia.sync.ReaderSyncService,
    private val komgaEvents: ManagedKomgaEvents,
    private val onnxRuntime: OnnxRuntime?,
    private val panelDetector: KomeliaPanelDetector?,
    private val upscaler: KomeliaUpscaler?,
    private val onnxModelDownloader: OnnxModelDownloader?,
    private val ocrService: snd.komelia.image.OcrService,
    private val translationService: snd.komelia.image.TranslationEngine,
    private val translationGlossaryRepository: snd.komelia.translation.TranslationGlossaryRepository,
    private val translationModelDownloader: snd.komelia.image.TranslationModelDownloader?,
    val colorCorrectionIsActive: Flow<Boolean>,
    onBookChange: () -> Unit = {},
    private val seriesReaderOverridesRepository: SeriesReaderOverridesRepository,
    private val blankPageDetector: snd.komelia.image.processing.BlankPageDetector,
) : ScreenModel {
    val screenScaleState = ScreenScaleState()
    private val pageChangeFlow = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val onnxRuntimeSettingsState = upscaler?.let {
        OnnxRuntimeSettingsState(
            onnxRuntimeInstaller = null,
            onnxModelDownloader = onnxModelDownloader,
            onnxRuntime = onnxRuntime,
            upscaler = upscaler,
            panelDetector = panelDetector,
            settingsRepository = readerSettingsRepository,
            coroutineScope = screenModelScope,
        )
    }

    val ncnnSettingsState = NcnnSettingsState(
        onnxModelDownloader = onnxModelDownloader,
        settingsRepository = readerSettingsRepository,
        coroutineScope = screenModelScope,
    )

    val readerState: ReaderState = ReaderState(
        imageLoader = imageLoader,
        bookApi = bookApi,
        seriesApi = seriesApi,
        readListApi = readListApi,
        navigator = navigator,
        appNotifications = appNotifications,
        readerSettingsRepository = readerSettingsRepository,
        commonSettingsRepository = commonSettingsRepository,
        currentBookId = currentBookId,
        markReadProgress = markReadProgress,
        stateScope = screenModelScope,
        bookSiblingsContext = bookSiblingsContext,
        colorCorrectionRepository = colorCorrectionRepository,
        bookAnnotationRepository = bookAnnotationRepository,
        epubBookmarkRepository = epubBookmarkRepository,
        audioBookmarkRepository = audioBookmarkRepository,
        audioPositionRepository = audioPositionRepository,
        readerSyncService = readerSyncService,
        komgaEvents = komgaEvents,
        pageChangeFlow = pageChangeFlow,
        ocrService = ocrService,
        translationService = translationService,
        translationGlossaryRepository = translationGlossaryRepository,
        translationModelDownloader = translationModelDownloader,
        panelsAvailable = { panelDetector != null && panelDetector.isAvailable.value },
    )

    val pagedReaderState = PagedReaderState(
        cleanupScope = cleanupScope,
        readerState = readerState,
        appNotifications = appNotifications,
        settingsRepository = readerSettingsRepository,
        imageLoader = imageLoader,
        appStrings = appStrings,
        pageChangeFlow = pageChangeFlow,
        screenScaleState = screenScaleState,
        onBookChange = onBookChange,
        seriesReaderOverridesRepository = seriesReaderOverridesRepository,
        blankPageDetector = blankPageDetector,
    )
    val panelsReaderState = panelDetector?.let { panelDetector ->
        if (!panelDetector.isAvailable.value) null
        else
            PanelsReaderState(
                cleanupScope = cleanupScope,
                readerState = readerState,
                appNotifications = appNotifications,
                settingsRepository = readerSettingsRepository,
                imageLoader = imageLoader,
                appStrings = appStrings,
                pageChangeFlow = pageChangeFlow,
                screenScaleState = screenScaleState,
                onnxRuntimeRfDetr = panelDetector,
                seriesReaderOverridesRepository = seriesReaderOverridesRepository,
            )
    }
    val continuousReaderState = ContinuousReaderState(
        cleanupScope = cleanupScope,
        readerState = readerState,
        imageLoader = imageLoader,
        settingsRepository = readerSettingsRepository,
        notifications = appNotifications,
        appStrings = appStrings,
        readerImageFactory = readerImageFactory,
        pageChangeFlow = pageChangeFlow,
        screenScaleState = screenScaleState,
    )

    init {
        combine(
            readerState.readerType,
            pagedReaderState.readingDirection,
            continuousReaderState.readingDirection
        ) { type, pagedDir, continuousDir ->
            when (type) {
                PAGED -> if (pagedDir == PagedReadingDirection.RIGHT_TO_LEFT) ReadingDirection.RTL else ReadingDirection.LTR
                CONTINUOUS -> if (continuousDir == ContinuousReadingDirection.RIGHT_TO_LEFT) ReadingDirection.RTL else ReadingDirection.LTR
                PANELS -> ReadingDirection.LTR
            }
        }.onEach { readerState.readingDirection.value = it }
            .launchIn(screenModelScope)

        // Page translation needs the same scan OCR does, so either switch arms it.
        combine(readerState.ocrSettings, readerState.translationSettings) { ocr, translation ->
            ocr.enabled || translation.enabled
        }
            .distinctUntilChanged()
            .flatMapLatest { scanEnabled ->
                if (scanEnabled) {
                    readerState.readerType.flatMapLatest { readerType ->
                        when (readerType) {
                            PAGED -> pagedReaderState.currentSpread.map { it.pages.firstOrNull()?.imageResult?.image }
                            CONTINUOUS -> continuousReaderState.currentPageImage
                            PANELS -> panelsReaderState?.currentPage?.map { it?.imageResult?.image } ?: flowOf(null)
                        }
                    }.debounce(200)
                } else {
                    readerState.ocrResults.value = emptyList()
                    readerState.ocrPageId.value = null
                    readerState.translatedBlocks.value = emptyMap()
                    flowOf(null)
                }
            }
            .onEach { image ->
                image?.let {
                    readerState.scanCurrentPageForText(it)
                    prepareNextPage(it)
                }
            }.launchIn(screenModelScope)
    }

    private var prefetchJob: Job? = null

    /**
     * Starts the next page's scan once the page on screen has finished its own.
     *
     * The wait is the point. Both scans take the same lock, and a prefetch that
     * reached it first would put four seconds in front of the page the reader is
     * actually looking at — which is the one failure mode that would make this
     * worse than not prefetching at all. Waiting for the current page to appear
     * in the scan cache makes the order certain instead of likely.
     *
     * Paged only for now. The continuous reader has no equivalent of "the next
     * page" that is settled while the current one is on screen — it scrolls, so
     * the answer changes under you — and panels is a different problem again.
     */
    private fun prepareNextPage(current: ReaderImage) {
        prefetchJob?.cancel()
        if (readerState.readerType.value != PAGED) return
        prefetchJob = screenModelScope.launch {
            readerState.scannedPages.first { it.containsKey(current.pageId) }
            pagedReaderState.nextSpreadFirstImage()
                ?.let { readerState.prefetchNextPageForText(it) }
        }
    }

    suspend fun initialize(
        bookId: KomgaBookId,
        seedBook: KomeliaBook? = null,
        seedSeries: KomgaSeries? = null,
    ) {
        val currentState = readerState.state.value
        if (currentState is LoadState.Success || currentState == LoadState.Loading) return

        onnxRuntimeSettingsState?.initialize()
        ncnnSettingsState.initialize()
        readerState.initialize(bookId, seedBook, seedSeries)
        screenScaleState.areaSize.takeWhile { it == IntSize.Zero }.collect()

        readerState.readerType.onEach {
            stopAllReaderModeStates()
            when (it) {
                PAGED -> pagedReaderState.initialize()
                CONTINUOUS -> continuousReaderState.initialize()
                PANELS -> {
                    if (panelsReaderState == null) {
                        logger.warn { "onnx runtime was not provided. Falling back to paged reader" }
                        readerState.onReaderTypeChange(PAGED)
                    } else {
                        panelsReaderState.initialize()
                    }
                }
            }
        }.launchIn(screenModelScope)
    }

    private fun stopAllReaderModeStates() {
        pagedReaderState.stop()
        continuousReaderState.stop()
        panelsReaderState?.stop()

    }

    override fun onDispose() {
        stopAllReaderModeStates()
        readerState.onDispose()
        // A loaded translation pair holds its models in memory; nothing outside
        // the reader uses them.
        translationService.release()
        panelDetector?.closeCurrentSession()
        upscaler?.closeCurrentSession()
    }
}