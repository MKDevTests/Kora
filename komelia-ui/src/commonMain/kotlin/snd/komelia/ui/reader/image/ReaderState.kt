package snd.komelia.ui.reader.image

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import cafe.adriel.voyager.navigator.Navigator
import io.ktor.client.plugins.*
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.http.HttpStatusCode.Companion.NotFound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import snd.komelia.AppNotification
import snd.komelia.AppNotifications
import snd.komelia.ManagedKomgaEvents
import snd.komelia.sync.CompactAnnotation
import snd.komelia.sync.CompactAudioBookmark
import snd.komelia.sync.CompactBookmark
import snd.komelia.sync.CompactAudioPosition
import snd.komelia.audiobook.AudioPosition
import snd.komelia.sync.ReaderSyncService
import snd.komelia.sync.SyncBlob
import snd.komga.client.book.R2Device
import snd.komga.client.book.R2Location
import snd.komga.client.book.R2Locator
import snd.komga.client.book.R2Progression
import snd.komga.client.sse.KomgaEvent
import kotlin.time.Clock
import snd.komelia.annotations.AnnotationLocation
import snd.komelia.annotations.BookAnnotation
import snd.komelia.bookmarks.EpubBookmark
import snd.komelia.audiobook.AudioBookmark
import snd.komelia.ui.platform.imageExtension
import snd.komelia.ui.platform.sanitizeFilename
import snd.komelia.ui.platform.saveImageToDownloads
import snd.komelia.color.repository.BookColorCorrectionRepository
import snd.komelia.image.BookImageLoader
import snd.komelia.image.OcrElementBox
import snd.komelia.image.OcrService
import snd.komelia.image.ReadingDirection
import snd.komelia.image.mergeOcrBoxes
import snd.komelia.image.ReaderImage
import snd.komelia.image.ReaderImage.PageId
import snd.komelia.image.ReduceKernel
import snd.komelia.settings.model.OcrLanguage
import snd.komelia.settings.model.OcrSettings
import snd.komelia.image.UpsamplingMode
import snd.komelia.image.availableReduceKernels
import snd.komelia.image.availableUpsamplingModes
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaReadListApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.ImageReaderSettingsRepository
import snd.komelia.settings.model.ReaderFlashColor
import snd.komelia.settings.model.ReaderTapNavigationMode
import snd.komelia.settings.model.ReaderType
import snd.komelia.ui.book.BookFilter
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.LoadState
import snd.komelia.ui.MainScreen
import snd.komelia.ui.oneshot.OneshotScreen
import snd.komelia.ui.platform.CommonParcelable
import snd.komelia.ui.platform.CommonParcelize
import snd.komelia.ui.platform.CommonParcelizeRawValue
import snd.komelia.ui.series.SeriesScreen
import snd.komelia.ui.reader.common.NavigationHistory
import snd.komelia.ui.series.SeriesNavigationContext
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.KomgaBookReadProgressUpdateRequest
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaReadingDirection
import snd.komga.client.search.allOfBooks
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId

typealias SpreadIndex = Int

class ReaderState(
    private val bookApi: KomgaBookApi,
    private val seriesApi: KomgaSeriesApi,
    private val readListApi: KomgaReadListApi,
    private val navigator: Navigator,
    private val appNotifications: AppNotifications,
    private val readerSettingsRepository: ImageReaderSettingsRepository,
    private val commonSettingsRepository: CommonSettingsRepository,
    private val currentBookId: MutableStateFlow<KomgaBookId?>,
    private val markReadProgress: Boolean,
    private val stateScope: CoroutineScope,
    private val bookSiblingsContext: BookSiblingsContext,
    private val colorCorrectionRepository: BookColorCorrectionRepository,
    private val bookAnnotationRepository: snd.komelia.annotations.BookAnnotationRepository,
    private val epubBookmarkRepository: snd.komelia.bookmarks.EpubBookmarkRepository,
    private val audioBookmarkRepository: snd.komelia.audiobook.AudioBookmarkRepository,
    private val audioPositionRepository: snd.komelia.audiobook.AudioPositionRepository,
    private val readerSyncService: ReaderSyncService,
    private val komgaEvents: ManagedKomgaEvents,
    val pageChangeFlow: SharedFlow<Unit>,
    private val imageLoader: BookImageLoader,
    private val ocrService: OcrService,
) {
    val navigationHistory = NavigationHistory()
    private val currentSyncBlob = MutableStateFlow<String?>(null)
    private val previewLoadScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())
    private val progressUpdateChannel = Channel<Int>(Channel.CONFLATED)

    val state = MutableStateFlow<LoadState<Unit>>(LoadState.Uninitialized)
    val serverUnavailableDialogVisible = MutableStateFlow(false)
    val expandImageSettings = MutableStateFlow(false)

    val booksState = MutableStateFlow<BookState?>(null)
    val series = MutableStateFlow<KomgaSeries?>(null)

    val readerType = MutableStateFlow(ReaderType.PAGED)

    /**
     * True when the current book's first pages look like a webtoon (very tall
     * pages, height/width >= 4) and the auto-detect setting is on. Exposed
     * so [ContinuousReaderState] can force TOP_TO_BOTTOM direction
     * in-memory without persisting it to the user's global setting.
     */
    val detectedAsWebtoon = MutableStateFlow(false)

    /**
     * Set the first time the user manually changes [readerType] after a book
     * has loaded (i.e. via [onReaderTypeChange]). Prevents the webtoon
     * auto-detect from re-asserting CONTINUOUS on subsequent books within
     * the same reader session — the user's manual choice is respected for
     * the lifetime of this state.
     */
    private var userOverrodeReaderType: Boolean = false
    val imageStretchToFit = MutableStateFlow(true)
    val cropBorders = MutableStateFlow(false)
    val loadThumbnailPreviews = MutableStateFlow(true)
    val showCarousel = MutableStateFlow(false)
    val readProgressPage = MutableStateFlow(1)

    val upsamplingMode = MutableStateFlow(UpsamplingMode.NEAREST)
    val downsamplingKernel = MutableStateFlow(ReduceKernel.NEAREST)
    val linearLightDownsampling = MutableStateFlow(false)
    val availableUpsamplingModes = availableUpsamplingModes()
    val availableDownsamplingKernels = availableReduceKernels()

    val flashOnPageChange = MutableStateFlow(false)
    val flashDuration = MutableStateFlow(100L)
    val flashEveryNPages = MutableStateFlow(1)
    val flashWith = MutableStateFlow(ReaderFlashColor.BLACK)

    val ocrSettings = MutableStateFlow(OcrSettings())
    val ocrResults = MutableStateFlow<List<OcrElementBox>>(emptyList())
    val ocrPageId = MutableStateFlow<PageId?>(null)
    val isOcrLoading = MutableStateFlow(false)
    private var ocrJob: Job? = null
    val readingDirection = MutableStateFlow(ReadingDirection.LTR)

    val tapNavigationMode = MutableStateFlow(ReaderTapNavigationMode.LEFT_RIGHT)
    val volumeKeysNavigation = MutableStateFlow(false)
    val keepReaderScreenOn = MutableStateFlow(false)
    val pixelDensity = MutableStateFlow<Density?>(null)

    val annotations = MutableStateFlow<List<snd.komelia.annotations.BookAnnotation>>(emptyList())
    val showAnnotationDialog = MutableStateFlow(false)

    init {
        stateScope.launch(Dispatchers.Main.immediate) {
            for (page in progressUpdateChannel) {
                readProgressPage.value = page
                if (markReadProgress) {
                    updateCacheAndPush()
                }
            }
        }

        pageChangeFlow.onEach {
            ocrJob?.cancel()
            ocrJob = null
            ocrResults.value = emptyList()
            ocrPageId.value = null
        }.launchIn(stateScope)
        stateScope.launch {
            readerSettingsRepository.getOcrSettings().collect { ocrSettings.value = it }
        }
    }

    val editingComicAnnotation = MutableStateFlow<snd.komelia.annotations.BookAnnotation?>(null)
    val pendingAnnotationPage = MutableStateFlow(0)
    val pendingAnnotationX = MutableStateFlow(0f)
    val pendingAnnotationY = MutableStateFlow(0f)
    val pendingAnnotationNote = MutableStateFlow<String?>(null)
    val lastHighlightColor = MutableStateFlow(0xFFFFEB3B.toInt())

    suspend fun initialize(bookId: KomgaBookId) {
        komgaEvents.events.onEach { event ->
            if (event is KomgaEvent.ReadProgressChanged && event.bookId == (booksState.value?.currentBook?.id ?: bookId)) {
                runCatching { initialSync() }
            }
        }.launchIn(stateScope)

        upsamplingMode.value = readerSettingsRepository.getUpsamplingMode().first()
        downsamplingKernel.value = readerSettingsRepository.getDownsamplingKernel().first()
        linearLightDownsampling.value = readerSettingsRepository.getLinearLightDownsampling().first()

        imageStretchToFit.value = readerSettingsRepository.getStretchToFit().first()
        cropBorders.value = readerSettingsRepository.getCropBorders().first()
        loadThumbnailPreviews.value = readerSettingsRepository.getLoadThumbnailPreviews().first()
        flashOnPageChange.value = readerSettingsRepository.getFlashOnPageChange().first()
        flashDuration.value = readerSettingsRepository.getFlashDuration().first()
        flashEveryNPages.value = readerSettingsRepository.getFlashEveryNPages().first()
        flashWith.value = readerSettingsRepository.getFlashWith().first()
        tapNavigationMode.value = readerSettingsRepository.getReaderTapNavigationMode().first()
        volumeKeysNavigation.value = readerSettingsRepository.getVolumeKeysNavigation().first()
        keepReaderScreenOn.value = commonSettingsRepository.getKeepReaderScreenOn().first()

        appNotifications.runCatchingToNotifications {
            state.value = LoadState.Loading
            val currentBooksState = booksState.value
            if (currentBooksState == null) state.value = LoadState.Loading
            val newBook = bookApi.getOne(bookId)

            val bookPages = loadBookPages(newBook.id)
            val prevBook = getPreviousBook(bookId)
            val prevBookPages = if (prevBook != null) loadBookPages(prevBook.id) else emptyList()
            val nextBook = getNextBook(newBook)
            val nextBookPages = if (nextBook != null) loadBookPages(nextBook.id) else emptyList()

            // Set readProgressPage BEFORE booksState to avoid race condition
            val bookProgress = newBook.readProgress
            readProgressPage.value = when {
                bookProgress == null || bookProgress.completed -> 1
                else -> bookProgress.page
            }
            booksState.value = BookState(
                currentBook = newBook,
                currentBookPages = bookPages,
                previousBook = prevBook,
                previousBookPages = prevBookPages,
                nextBook = nextBook,
                nextBookPages = nextBookPages
            )
            preloadFirstPage(nextBook)
            preloadFirstPage(prevBook)
            currentBookId.value = bookId

            updateCurrentSeriesAndReaderType(newBook)

            initialSync()
            state.value = LoadState.Success(Unit)
        }.onFailure { throwable ->
            state.value = LoadState.Error(throwable)
            if (throwable.isNetworkError()) serverUnavailableDialogVisible.value = true
        }

        stateScope.launch {
            currentBookId.filterNotNull().collectLatest { bookId ->
                bookAnnotationRepository.getAnnotations(bookId).collect { list ->
                    annotations.value = list
                }
            }
        }
    }

    private suspend fun loadBookPages(bookId: KomgaBookId): List<PageMetadata> {
        val pages = bookApi.getBookPages(bookId)

        return pages.map {
            val width = it.width
            val height = it.height
            PageMetadata(
                bookId = bookId,
                pageNumber = it.number,
                size = if (width != null && height != null) IntSize(width, height) else null
            )
        }
    }

    private suspend fun updateCurrentSeriesAndReaderType(book: KomeliaBook) {
        // Reset the per-book webtoon flag; we'll set it back below if the new
        // book also qualifies.
        detectedAsWebtoon.value = false

        val baseReaderType = if (!book.seriesId.value.startsWith("local")) {
            val currentSeries = seriesApi.getOneSeries(book.seriesId)
            series.value = currentSeries
            when (currentSeries.metadata.readingDirection) {
                KomgaReadingDirection.LEFT_TO_RIGHT -> ReaderType.PAGED
                KomgaReadingDirection.RIGHT_TO_LEFT -> ReaderType.PAGED
                KomgaReadingDirection.WEBTOON -> ReaderType.CONTINUOUS
                KomgaReadingDirection.VERTICAL, null -> readerSettingsRepository.getReaderType().first()
            }
        } else {
            readerSettingsRepository.getReaderType().first()
        }
        readerType.value = baseReaderType

        // Webtoon auto-detect override. Only applies when:
        //  - the setting is ON
        //  - the user hasn't manually flipped readerType already this session
        //  - the first 3 pages all look webtoon-tall (height/width >= 4)
        if (!userOverrodeReaderType
            && readerSettingsRepository.getPagedAutoDetectWebtoon().first()
            && isWebtoonLikely(booksState.value?.currentBookPages ?: emptyList())
        ) {
            detectedAsWebtoon.value = true
            readerType.value = ReaderType.CONTINUOUS
        }
    }

    /**
     * Heuristic: the first 3 pages (or fewer if the book has <3) must all
     * have a height-to-width ratio of at least 4.0 for the book to be
     * classified as a webtoon. 4× is a safe lower bound — typical manga
     * pages cap around 1.4-2:1, while webtoon panels start at 3-6:1.
     * Uses the raw image dimensions from Komga metadata, BEFORE any
     * pipeline processing (crop borders etc.), so the ratio reflects the
     * file itself.
     */
    private fun isWebtoonLikely(pages: List<PageMetadata>): Boolean {
        val sample = pages.take(3)
        if (sample.isEmpty()) return false
        return sample.all { page ->
            val size = page.size ?: return@all false
            size.width > 0 && size.height.toFloat() / size.width.toFloat() >= 4.0f
        }
    }

    private suspend fun getNextBook(currentBook: KomeliaBook): KomeliaBook? {
        val sibling = try {
            when (bookSiblingsContext) {
                is BookSiblingsContext.ReadList ->
                    readListApi.getBookSiblingNext(bookSiblingsContext.id, currentBook.id)

                is BookSiblingsContext.Series -> bookApi.getBookSiblingNext(currentBook.id)
            }
        } catch (e: ClientRequestException) {
            if (e.response.status != NotFound) throw e
            else null
        }

        if (sibling != null) return sibling

        return when (bookSiblingsContext) {
            is BookSiblingsContext.Series -> getNextSeriesFirstBook(currentBook)
            is BookSiblingsContext.ReadList -> null
        }
    }

    private suspend fun getNextSeriesFirstBook(currentBook: KomeliaBook): KomeliaBook? {
        val listContext = SeriesNavigationContext.get(currentBook.seriesId) ?: return null
        if (currentBook.seriesId.value.startsWith("local")) return null

        val bookFilter = when (val context = bookSiblingsContext) {
            is BookSiblingsContext.Series -> context.filter ?: BookFilter.DEFAULT
            is BookSiblingsContext.ReadList -> BookFilter.DEFAULT
        }
        val allowCompletedFallback = listContext.filter.readStatus.isEmpty() && bookFilter.readStatus.isEmpty()
        var pageNumber = listContext.currentPage.coerceAtLeast(1)

        while (true) {
            val page = getSeriesPage(pageNumber, listContext)
            if (page.content.isEmpty()) return null

            val currentSeriesIndex = page.content.indexOfFirst { it.id == currentBook.seriesId }
            val startIndex = when {
                currentSeriesIndex >= 0 -> currentSeriesIndex + 1
                pageNumber == listContext.currentPage -> (listContext.seriesIndexInPage + 1)
                    .coerceIn(0, page.content.size)

                else -> 0
            }

            page.content.drop(startIndex).forEachIndexed { offset, candidateSeries ->
                getFirstBookForNextSeries(
                    candidateSeriesId = candidateSeries.id,
                    bookFilter = bookFilter,
                    allowCompletedFallback = allowCompletedFallback
                )?.let { nextSeriesFirstBook ->
                    SeriesNavigationContext.put(
                        candidateSeries.id,
                        listContext.copy(
                            currentPage = pageNumber,
                            seriesIndexInPage = startIndex + offset
                        )
                    )
                    return nextSeriesFirstBook
                }
            }

            if (pageNumber >= page.totalPages) return null
            pageNumber++
        }
    }

    private suspend fun getSeriesPage(
        pageNumber: Int,
        context: SeriesNavigationContext.SeriesListContext,
    ) = seriesApi.getSeriesList(
        conditionBuilder = allOfSeries {
            context.libraryId?.let { library { isEqualTo(it) } }
            context.filter.addConditionTo(this)
        },
        fulltextSearch = context.filter.searchTerm.ifBlank { null },
        pageRequest = KomgaPageRequest(
            size = context.pageSize.coerceAtLeast(1),
            pageIndex = pageNumber - 1,
            sort = context.filter.sortOrder.komgaSort
        )
    )

    private suspend fun getFirstBookForNextSeries(
        candidateSeriesId: KomgaSeriesId,
        bookFilter: BookFilter,
        allowCompletedFallback: Boolean,
    ): KomeliaBook? {
        var firstFilteredBook: KomeliaBook? = null
        var pageIndex = 0

        while (true) {
            val page = bookApi.getBookList(
                conditionBuilder = allOfBooks {
                    seriesId { isEqualTo(candidateSeriesId) }
                    bookFilter.addConditionTo(this)
                },
                pageRequest = KomgaPageRequest(
                    pageIndex = pageIndex,
                    size = 50,
                    sort = bookFilter.sortOrder.komgaSort
                )
            )
            if (firstFilteredBook == null) firstFilteredBook = page.content.firstOrNull()
            page.content.firstOrNull { it.readProgress?.completed != true }?.let { return it }

            pageIndex++
            if (pageIndex >= page.totalPages) break
        }

        return if (allowCompletedFallback) firstFilteredBook else null
    }

    private suspend fun getPreviousBook(currentBookId: KomgaBookId): KomeliaBook? {
        return try {
            when (bookSiblingsContext) {
                is BookSiblingsContext.ReadList ->
                    readListApi.getBookSiblingPrevious(bookSiblingsContext.id, currentBookId)

                is BookSiblingsContext.Series -> bookApi.getBookSiblingPrevious(currentBookId)
            }
        } catch (e: ClientRequestException) {
            if (e.response.status != NotFound) throw e
            else null
        }

    }

    suspend fun loadNextBook() {
        val booksState = requireNotNull(booksState.value)
        if (booksState.nextBook != null) {
            val nextBook = getNextBook(booksState.nextBook)
            val nextBookPages = if (nextBook != null) loadBookPages(nextBook.id) else emptyList()
            // preload-after-loadnext
            preloadFirstPage(nextBook)

            readProgressPage.value = 1
            this.booksState.value = BookState(
                currentBook = booksState.nextBook,
                currentBookPages = booksState.nextBookPages,
                previousBook = booksState.currentBook,
                previousBookPages = booksState.currentBookPages,

                nextBook = nextBook,
                nextBookPages = nextBookPages
            )
            currentBookId.value = booksState.nextBook.id
            updateCurrentSeriesAndReaderType(booksState.nextBook)
            onProgressChange(1)
        } else {
            navigator replace MainScreen(
                if (booksState.currentBook.oneshot) OneshotScreen(booksState.currentBook, bookSiblingsContext)
                else SeriesScreen(booksState.currentBook.seriesId)
            )
        }
    }

    suspend fun loadPreviousBook(fromStart: Boolean = false) {
        val booksState = requireNotNull(booksState.value)
        if (booksState.previousBook != null) {
            val previousBook = getPreviousBook(booksState.previousBook.id)
            val previousBookPages =
                if (previousBook != null) loadBookPages(previousBook.id) else emptyList()

            readProgressPage.value = if (fromStart) 1 else booksState.previousBookPages.size
            this.booksState.value = BookState(
                currentBook = booksState.previousBook,
                currentBookPages = booksState.previousBookPages,
                nextBook = booksState.currentBook,
                nextBookPages = booksState.currentBookPages,

                previousBook = previousBook,
                previousBookPages = previousBookPages,
            )
        } else
            appNotifications.add(AppNotification.Normal("You're at the beginning of the book"))
        return
    }

    fun onProgressChange(page: Int) {
        progressUpdateChannel.trySend(page)
    }

    fun onReaderTypeChange(type: ReaderType) {
        // Any explicit user change disables webtoon auto-detect re-assertion
        // for the rest of this reader session.
        userOverrodeReaderType = true
        if (type != ReaderType.CONTINUOUS) detectedAsWebtoon.value = false
        this.readerType.value = type
        stateScope.launch { readerSettingsRepository.putReaderType(type) }
    }

    fun onStretchToFitChange(stretch: Boolean) {
        imageStretchToFit.value = stretch
        stateScope.launch { readerSettingsRepository.putStretchToFit(stretch) }
    }

    fun onStretchToFitCycle() {
        val newValue = !imageStretchToFit.value
        imageStretchToFit.value = newValue
        stateScope.launch { readerSettingsRepository.putStretchToFit(newValue) }
    }

    fun onCropBordersChange(trim: Boolean) {
        cropBorders.value = trim
        stateScope.launch { readerSettingsRepository.putCropBorders(trim) }
    }

    fun onLoadThumbnailPreviewsChange(load: Boolean) {
        loadThumbnailPreviews.value = load
        stateScope.launch { readerSettingsRepository.putLoadThumbnailPreviews(load) }
    }

    fun onToggleCarousel() {
        showCarousel.value = !showCarousel.value
    }

    fun onFlashEnabledChange(enabled: Boolean) {
        flashOnPageChange.value = enabled
        stateScope.launch { readerSettingsRepository.putFlashOnPageChange(enabled) }
    }

    fun onFlashDurationChange(duration: Long) {
        flashDuration.value = duration
        stateScope.launch { readerSettingsRepository.putFlashDuration(duration) }
    }

    fun onFlashEveryNPagesChange(pages: Int) {
        flashEveryNPages.value = pages
        stateScope.launch { readerSettingsRepository.putFlashEveryNPages(pages) }
    }

    fun onFlashWithChange(flashWith: ReaderFlashColor) {
        this.flashWith.value = flashWith
        stateScope.launch { readerSettingsRepository.putFlashWith(flashWith) }
    }

    fun onTapNavigationModeChange(mode: ReaderTapNavigationMode) {
        this.tapNavigationMode.value = mode
        stateScope.launch { readerSettingsRepository.putReaderTapNavigationMode(mode) }
    }

    fun onUpsamplingModeChange(mode: UpsamplingMode) {
        upsamplingMode.value = mode
        stateScope.launch { readerSettingsRepository.putUpsamplingMode(mode) }
    }

    fun onDownsamplingKernelChange(kernel: ReduceKernel) {
        downsamplingKernel.value = kernel
        stateScope.launch { readerSettingsRepository.putDownsamplingKernel(kernel) }
    }

    fun onLinearLightDownsamplingChange(linear: Boolean) {
        linearLightDownsampling.value = linear
        stateScope.launch { readerSettingsRepository.putLinearLightDownsampling(linear) }
    }

    fun onOcrSettingsChange(newSettings: OcrSettings) {
        ocrSettings.value = newSettings
        stateScope.launch { readerSettingsRepository.putOcrSettings(newSettings) }
    }

    fun scanCurrentPageForText(image: ReaderImage) {
        ocrJob?.cancel()
        ocrJob = stateScope.launch {
            ocrPageId.value = image.pageId
            isOcrLoading.value = true
            try {
                val rawBoxes = withContext(Dispatchers.Default) {
                    ocrService.recognizeText(image, ocrSettings.value)
                }
                ocrResults.value = if (ocrSettings.value.mergeBoxes) {
                    mergeOcrBoxes(rawBoxes, readingDirection.value)
                } else rawBoxes
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                appNotifications.add(AppNotification.Error("OCR failed: ${e.message}"))
            } finally {
                isOcrLoading.value = false
            }
        }
    }


    fun onColorCorrectionDisable() {
        stateScope.launch {
            booksState.value?.currentBook?.let { colorCorrectionRepository.deleteSettings(it.id) }
        }
    }

    fun saveCurrentPageToDownloads() {
        val bookState = booksState.value ?: return
        val pageNumber = readProgressPage.value
        val book = bookState.currentBook
        stateScope.launch {
            appNotifications.runCatchingToNotifications {
                val bytes = bookApi.getPage(book.id, pageNumber)
                val ext = bytes.imageExtension()
                val filename = "${book.name.sanitizeFilename()}_p${pageNumber.toString().padStart(3, '0')}.$ext"
                saveImageToDownloads(bytes, filename)
                appNotifications.add(AppNotification.Success("Page $pageNumber saved to Downloads"))
            }
        }
    }

    fun saveComicAnnotation(page: Int, x: Float, y: Float, color: Int, note: String?) {
        val bookId = currentBookId.value ?: return
        val annotation = snd.komelia.annotations.BookAnnotation(
            id = java.util.UUID.randomUUID().toString(),
            bookId = bookId,
            location = snd.komelia.annotations.AnnotationLocation.ComicLocation(page, x, y),
            highlightColor = color,
            note = note,
            createdAt = System.currentTimeMillis(),
        )
        stateScope.launch {
            bookAnnotationRepository.saveAnnotation(annotation)
            lastHighlightColor.value = color
            updateCacheAndPush()
        }
    }

    fun updateComicAnnotation(existing: snd.komelia.annotations.BookAnnotation, note: String?, color: Int) {
        val updated = existing.copy(highlightColor = color, note = note, updatedAt = Clock.System.now().toEpochMilliseconds())
        stateScope.launch {
            bookAnnotationRepository.deleteAnnotation(existing.id)
            bookAnnotationRepository.saveAnnotation(updated)
            lastHighlightColor.value = color
            updateCacheAndPush()
        }
    }

    fun deleteComicAnnotation(annotation: snd.komelia.annotations.BookAnnotation) {
        stateScope.launch {
            bookAnnotationRepository.deleteAnnotation(annotation.id)
            updateCacheAndPush()
        }
    }

    fun dismissServerUnavailableDialog() {
        serverUnavailableDialogVisible.value = false
    }

    fun onDispose() {
        currentBookId.value = null
        previewLoadScope.cancel()
    }

    private suspend fun initialSync() {
        val currentBook = booksState.value?.currentBook ?: return
        val r2Prog = bookApi.getReadiumProgression(currentBook.id)
        val remoteSyncBlob = readerSyncService.decode(r2Prog?.locator?.koboSpan)
        val localBookmarks = epubBookmarkRepository.getBookmarks(currentBook.id).first()
        val localAnnotations = bookAnnotationRepository.getAnnotations(currentBook.id).first()
        val localAudioBookmarks = audioBookmarkRepository.getBookmarks(currentBook.id).first()
        val localAudioPosition = audioPositionRepository.getPosition(currentBook.id)

        val currentLocalBlob = readerSyncService.decode(currentSyncBlob.value)
        val localLastSyncTime = currentLocalBlob?.lastModified ?: 0L

        val localSyncBlob = SyncBlob(
            bookmarks = localBookmarks.map {
                CompactBookmark(it.id, it.locatorJson, it.createdAt)
            },
            annotations = localAnnotations.map {
                CompactAnnotation(
                    id = it.id,
                    type = if (it.location is AnnotationLocation.EpubLocation) 0 else 1,
                    loc = when (val loc = it.location) {
                        is AnnotationLocation.EpubLocation -> loc.locatorJson
                        is AnnotationLocation.ComicLocation -> "${loc.page},${loc.x},${loc.y}"
                    },
                    color = it.highlightColor,
                    note = it.note,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                )
            },
            audioBookmarks = localAudioBookmarks.map {
                CompactAudioBookmark(it.id, it.trackIndex, it.positionSeconds, it.createdAt)
            },
            audioPosition = localAudioPosition?.let {
                CompactAudioPosition(it.trackIndex, it.positionSeconds, it.savedAt)
            },
            lastModified = localLastSyncTime
        )

        val merged = if (remoteSyncBlob != null) {
            readerSyncService.merge(localSyncBlob, remoteSyncBlob, localLastSyncTime)
        } else localSyncBlob

        // Update local repositories with merged data
        val mergedAudioPos = merged.audioPosition
        if (mergedAudioPos != null && (localAudioPosition == null || mergedAudioPos.savedAt > localAudioPosition.savedAt)) {
            audioPositionRepository.savePosition(
                AudioPosition(
                    bookId = currentBook.id,
                    trackIndex = mergedAudioPos.track,
                    positionSeconds = mergedAudioPos.pos,
                    savedAt = mergedAudioPos.savedAt
                )
            )
        }
        merged.bookmarks.forEach { compact ->
            if (localBookmarks.none { it.id == compact.id }) {
                epubBookmarkRepository.saveBookmark(
                    EpubBookmark(
                        id = compact.id,
                        bookId = currentBook.id,
                        locatorJson = compact.locatorJson,
                        createdAt = compact.createdAt
                    )
                )
            }
        }
        merged.annotations.forEach { compact ->
            val existing = localAnnotations.find { it.id == compact.id }
            if (existing == null) {
                val location = if (compact.type == 0) {
                    AnnotationLocation.EpubLocation(compact.loc, compact.selectedText)
                } else {
                    val parts = compact.loc.split(",")
                    AnnotationLocation.ComicLocation(
                        parts[0].toInt(),
                        parts[1].toFloat(),
                        parts[2].toFloat()
                    )
                }
                bookAnnotationRepository.saveAnnotation(
                    BookAnnotation(
                        id = compact.id,
                        bookId = currentBook.id,
                        location = location,
                        highlightColor = compact.color,
                        note = compact.note,
                        createdAt = compact.createdAt,
                        updatedAt = compact.updatedAt,
                    )
                )
            } else if (compact.updatedAt > existing.updatedAt) {
                // Remote edit is newer — update note/color, preserve local selectedText
                bookAnnotationRepository.deleteAnnotation(existing.id)
                bookAnnotationRepository.saveAnnotation(
                    existing.copy(
                        note = compact.note,
                        highlightColor = compact.color,
                        updatedAt = compact.updatedAt,
                    )
                )
            }
        }
        merged.audioBookmarks.forEach { compact ->
            if (localAudioBookmarks.none { it.id == compact.id }) {
                audioBookmarkRepository.saveBookmark(
                    AudioBookmark(
                        id = compact.id,
                        bookId = currentBook.id,
                        trackIndex = compact.track,
                        positionSeconds = compact.pos,
                        trackTitle = "",
                        createdAt = compact.createdAt
                    )
                )
            }
        }

        // Handle local deletions
        localBookmarks.forEach { local ->
            if (merged.bookmarks.none { it.id == local.id }) {
                epubBookmarkRepository.deleteBookmark(local.id)
            }
        }
        localAnnotations.forEach { local ->
            if (merged.annotations.none { it.id == local.id }) {
                bookAnnotationRepository.deleteAnnotation(local.id)
            }
        }
        localAudioBookmarks.forEach { local ->
            if (merged.audioBookmarks.none { it.id == local.id }) {
                audioBookmarkRepository.deleteBookmark(local.id)
            }
        }

        currentSyncBlob.value = readerSyncService.encode(merged)
    }

    private suspend fun updateCacheAndPush() {
        val currentBook = booksState.value?.currentBook ?: return
        val bookmarks = epubBookmarkRepository.getBookmarks(currentBook.id).first()
        val annotations = bookAnnotationRepository.getAnnotations(currentBook.id).first()
        val audioBookmarks = audioBookmarkRepository.getBookmarks(currentBook.id).first()
        val audioPosition = audioPositionRepository.getPosition(currentBook.id)

        val syncBlob = SyncBlob(
            bookmarks = bookmarks.map {
                CompactBookmark(it.id, it.locatorJson, it.createdAt)
            },
            annotations = annotations.map {
                CompactAnnotation(
                    id = it.id,
                    type = if (it.location is AnnotationLocation.EpubLocation) 0 else 1,
                    loc = when (val loc = it.location) {
                        is AnnotationLocation.EpubLocation -> loc.locatorJson
                        is AnnotationLocation.ComicLocation -> "${loc.page},${loc.x},${loc.y}"
                    },
                    color = it.highlightColor,
                    note = it.note,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                )
            },
            audioBookmarks = audioBookmarks.map {
                CompactAudioBookmark(it.id, it.trackIndex, it.positionSeconds, it.createdAt)
            },
            audioPosition = audioPosition?.let {
                CompactAudioPosition(it.trackIndex, it.positionSeconds, it.savedAt)
            },
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
        val encoded = readerSyncService.encode(syncBlob)
        currentSyncBlob.value = encoded

        if (!markReadProgress) return
        val page = readProgressPage.value
        val r2Prog = R2Progression(
            modified = Clock.System.now(),
            device = R2Device("komelia-android", "Komelia"),
            locator = R2Locator(
                href = "p$page",
                type = "image/jpeg",
                locations = R2Location(
                    position = page,
                    progression = page.toFloat() / (booksState.value?.currentBookPages?.size ?: 1)
                ),
                koboSpan = encoded
            )
        )
        runCatching { bookApi.updateReadiumProgression(currentBook.id, r2Prog) }
            .onFailure { appNotifications.runCatchingToNotifications { throw it } }
    }

    private fun preloadFirstPage(book: KomeliaBook?) {
        if (book == null) return
        stateScope.launch {
            runCatching { imageLoader.loadReaderImage(book.id, 1) }
        }
    }
}


private fun Throwable.isNetworkError(): Boolean =
    this is ConnectTimeoutException || this is HttpRequestTimeoutException

@CommonParcelize
data class PageMetadata(
    val bookId: @CommonParcelizeRawValue KomgaBookId,
    val pageNumber: Int,
    val size: @CommonParcelizeRawValue IntSize?,
    val half: PageHalf? = null,
) : CommonParcelable {
    fun isLandscape(): Boolean {
        if (size == null) return false
        return size.width > size.height
    }

    fun toPageId() = PageId(bookId.value, pageNumber, half?.name)
}

enum class PageHalf { LEFT, RIGHT }

data class BookState(
    val currentBook: KomeliaBook,
    val currentBookPages: List<PageMetadata>,
    val previousBook: KomeliaBook?,
    val previousBookPages: List<PageMetadata>,
    val nextBook: KomeliaBook?,
    val nextBookPages: List<PageMetadata>,
)
