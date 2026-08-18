package snd.komelia.ui.reader.image

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import cafe.adriel.voyager.navigator.Navigator
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.*
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.http.HttpStatusCode.Companion.NotFound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
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
import snd.komelia.perf.PerfTrace
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.KomgaBookReadProgressUpdateRequest
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaReadingDirection
import snd.komga.client.search.allOfBooks
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId

typealias SpreadIndex = Int

private val logger = KotlinLogging.logger {}

/** Own logger so a page's translation blocks can be grepped on their own. */
private val translationLogger = KotlinLogging.logger("KoraTranslate")

/**
 * Pages whose OCR and translation are kept. Twenty covers backtracking within
 * a chapter, which is what the cache is for; re-reading a page from much
 * further back is rare enough to pay for again.
 */
private const val SCAN_CACHE_SIZE = 20

/**
 * Minimum height/width ratio for a page to count as "webtoon-tall".
 *
 * Was 4.0, which missed real webtoons: "The Hole is Open" ships 720x2752 tiles
 * = **3.82**, just under the bar, so it never auto-detected. 3.0 catches those
 * while keeping a 1.5x margin over the tallest normal manga page (~2:1).
 * Double-page spreads are irrelevant here — being wider than tall, they score
 * ~0.7 and sit far below any value we'd pick.
 */
private const val WEBTOON_MIN_ASPECT = 3.0f

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
    private val translationService: snd.komelia.image.TranslationEngine,
    private val translationGlossaryRepository: snd.komelia.translation.TranslationGlossaryRepository,
    private val translationModelDownloader: snd.komelia.image.TranslationModelDownloader?,
    /**
     * Whether the ONNX panel detector is loaded and usable. When false, webtoon
     * routing falls back to CONTINUOUS instead of PANELS (PANELS needs the
     * detector to render — without it the reader would collapse to PAGED).
     */
    private val panelsAvailable: () -> Boolean,
) {
    val navigationHistory = NavigationHistory()
    private val currentSyncBlob = MutableStateFlow<String?>(null)
    private val previewLoadScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())
    private val progressUpdateChannel = Channel<Int>(Channel.CONFLATED)

    /**
     * Independent fire-and-forget scope used by [onDispose] to flush the final
     * read-progress to Komga. Survives the reader screen tear-down so the last
     * page (or "completed" flag) is never lost when:
     *  - the user exits via the system back-button while a CONFLATED progress
     *    update is still queued;
     *  - the user reads in random sort order and exits without scrolling onto
     *    a sentinel "next book" page that would have committed the last page;
     *  - the continuous reader stop-at-end fix is off and the user blew past
     *    the boundary before the channel got a chance to push.
     */
    private val finalFlushScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
    val invertSpeechBubbles = MutableStateFlow(false)
    val webtoonSmartScroll = MutableStateFlow(true)
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

    val translationSettings = MutableStateFlow(snd.komelia.settings.model.TranslationSettings())
    /** Translated text per OCR block index, for the page in [ocrPageId]. */
    val translatedBlocks = MutableStateFlow<Map<Int, String>>(emptyMap())
    val isTranslating = MutableStateFlow(false)

    /**
     * Boxes and translations of every page scanned recently, keyed by page.
     *
     * The paged reader shows one page at a time, so [ocrPageId] naming the one
     * page that carries an overlay is exactly right there. The continuous
     * reader has several pages on screen at once and scans only the one the
     * reader is on: keyed off [ocrPageId] alone, a page's overlay vanished the
     * moment the scroll moved on, while the page itself was still in view.
     *
     * Reading from here instead keeps every scanned page dressed for as long as
     * it stays on screen, and costs nothing — the results were already being
     * kept for the same reason turning back a page is instant.
     */
    val scannedPages: StateFlow<Map<PageId, PageScan>> get() = scanCache

    /** Public face of a cached scan, so the cache type can stay private. */
    class PageScan(val boxes: List<OcrElementBox>, val translations: Map<Int, String>)

    /**
     * Glossary terms belonging to the series on screen, for the editor in the
     * reader settings. Global terms are deliberately not listed: this is the
     * place where a bad translation is noticed, and what is noticed there is
     * always about this series.
     */
    val glossaryTerms = MutableStateFlow<List<snd.komelia.translation.GlossaryTerm>>(emptyList())

    /**
     * State of the better engine's model for the pair being translated.
     *
     * Bergamot reads a French that ML Kit does not — measured over several
     * chapters — but its model is a 36MB download the user has to ask for. Null
     * where no such model exists for the pair, which is when there is nothing
     * to offer.
     */
    val translationModelState = MutableStateFlow<TranslationModelState?>(null)
    val readingDirection = MutableStateFlow(ReadingDirection.LTR)

    val tapNavigationMode = MutableStateFlow(ReaderTapNavigationMode.LEFT_RIGHT)
    val volumeKeysNavigation = MutableStateFlow(false)
    val keepReaderScreenOn = MutableStateFlow(false)
    /**
     * Image-reader minimal-UI-while-reading toggle (v1.0.11). Mirrors
     * the persisted [ImageReaderSettings.keepProgressBarVisibleWhileReading]
     * loaded at [initialize]. When true, the reader's hidden-controls
     * state shows a slim bottom strip instead of nothing.
     */
    val keepProgressBarVisibleWhileReading = MutableStateFlow(false)
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
            // The in-flight scan is left alone: it finishes and is cached. Only
            // what is on screen is cleared here.
            ocrResults.value = emptyList()
            ocrPageId.value = null
            translatedBlocks.value = emptyMap()
        }.launchIn(stateScope)
        stateScope.launch {
            readerSettingsRepository.getOcrSettings().collect { ocrSettings.value = it }
        }
        stateScope.launch {
            var bootstrapped = false
            readerSettingsRepository.getTranslationSettings()
                .collect {
                    translationSettings.value = it
                    // Once, on the first settings the reader sees. Turning the
                    // feature on is what normally fetches the models, and that
                    // leaves out everyone who already had it on before the
                    // better engine was fetched alongside the bundled one —
                    // they would keep reading the fallback's French and have no
                    // reason to suspect a second engine exists.
                    if (it.enabled && !bootstrapped) {
                        bootstrapped = true
                        ensureTranslationModels(it)
                    }
                }
        }
    }

    val editingComicAnnotation = MutableStateFlow<snd.komelia.annotations.BookAnnotation?>(null)
    val pendingAnnotationPage = MutableStateFlow(0)
    val pendingAnnotationX = MutableStateFlow(0f)
    val pendingAnnotationY = MutableStateFlow(0f)
    val pendingAnnotationNote = MutableStateFlow<String?>(null)
    val lastHighlightColor = MutableStateFlow(0xFFFFEB3B.toInt())

    /**
     * @param seedBook the full book the calling screen already holds, so the
     *   sibling lookups can start without waiting on our own `getOne`. The fresh
     *   `getOne` is STILL awaited: it decides which page we open on, and a stale
     *   grid must never make that call.
     * @param seedSeries the book's series, when the caller has it. Measured at
     *   **2608 ms of a 2664 ms open** — `getOneSeries` was the whole critical
     *   path, everything else finished inside it. Unlike the book, nothing about
     *   the series decides progress: it picks the reader type (webtoon
     *   detection), the reading direction and the per-series overrides. A seed a
     *   few minutes old is as good as a fresh fetch for all three, so this one
     *   is used as-is rather than re-fetched.
     */
    suspend fun initialize(
        bookId: KomgaBookId,
        seedBook: KomeliaBook? = null,
        seedSeries: KomgaSeries? = null,
    ) {
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
        invertSpeechBubbles.value = readerSettingsRepository.getInvertSpeechBubbles().first()
        webtoonSmartScroll.value = readerSettingsRepository.getWebtoonSmartScroll().first()
        loadThumbnailPreviews.value = readerSettingsRepository.getLoadThumbnailPreviews().first()
        flashOnPageChange.value = readerSettingsRepository.getFlashOnPageChange().first()
        flashDuration.value = readerSettingsRepository.getFlashDuration().first()
        flashEveryNPages.value = readerSettingsRepository.getFlashEveryNPages().first()
        flashWith.value = readerSettingsRepository.getFlashWith().first()
        tapNavigationMode.value = readerSettingsRepository.getReaderTapNavigationMode().first()
        volumeKeysNavigation.value = readerSettingsRepository.getVolumeKeysNavigation().first()
        keepReaderScreenOn.value = commonSettingsRepository.getKeepReaderScreenOn().first()
        keepProgressBarVisibleWhileReading.value =
            readerSettingsRepository.getKeepProgressBarVisibleWhileReading().first()

        appNotifications.runCatchingToNotifications {
            PerfTrace.measure("reader.open CRITICAL") {
                state.value = LoadState.Loading

                // ONE parallel wave for every server call. Each call is ~2s on the
                // user's server; serial phases add up (6-8 serial calls = 13-21s
                // measured, then 3 phases = ~6s). The books/pages are still all
                // resolved before the first paint (the continuous reader assumes a
                // fully populated BookState in a single update — deferring the
                // siblings to a second update mis-fires its navigation logic), but
                // the wall-clock is now bounded by the single slowest call.
                //
                // [seedBook] is the full book the calling screen already had. It
                // lets the sibling/series lookups start immediately instead of
                // waiting on our own getOne. The fresh getOne is STILL awaited for
                // readProgressPage — a seed from a stale grid must never decide
                // which page we open on.
                coroutineScope {
                    val freshBookDeferred = async {
                        PerfTrace.measure("reader.open getOne") { bookApi.getOne(bookId) }
                    }
                    val pagesDeferred = async {
                        // Needs the book for its fileHash (cache key). With a seed
                        // this resolves without any await; the seedless restore
                        // path waits on getOne first — rare, and usually a cache
                        // hit right after anyway.
                        val base = seedBook ?: freshBookDeferred.await()
                        PerfTrace.measure("reader.open currentPages", { it.size }) { loadBookPages(base) }
                    }
                    // The PREVIOUS book stays in the wave: measured at 443ms,
                    // 961ms and 2506ms over three opens, always inside the
                    // window of something else. Two serial calls, but cheap ones
                    // — getBookSiblingPrevious either answers or 404s.
                    //
                    // The NEXT book is gone from it entirely, and that is the
                    // whole point of this wave now. Measured over the same three
                    // opens at 2788ms, 11940ms and 14354ms — the critical path
                    // every single time, by an order of magnitude. It is the only
                    // lookup that can miss: on the last volume of a series
                    // getBookSiblingNext 404s and getNextSeriesFirstBook then
                    // walks the series list AND every book page of the series
                    // that follows. Nobody needs that answer to read page one, so
                    // it now runs after the paint — see prefetchNextBook.
                    val prevDeferred = async {
                        PerfTrace.measure("reader.open prev") {
                            val pb = getPreviousBook(bookId)
                            pb to (if (pb != null) loadBookPages(pb) else emptyList())
                        }
                    }
                    val seriesDeferred = async {
                        val seriesId = (seedBook ?: freshBookDeferred.await()).seriesId
                        when {
                            // Seeded and still the right series: no call at all.
                            // This is the whole point — see the seedSeries doc.
                            seedSeries != null && seedSeries.id == seriesId -> seedSeries
                            seriesId.value.startsWith("local") -> null
                            else -> runCatching {
                                PerfTrace.measure("reader.open getOneSeries") { seriesApi.getOneSeries(seriesId) }
                            }.getOrNull()
                        }
                    }

                    val newBook = freshBookDeferred.await()
                    val bookPages = pagesDeferred.await()
                    val (prevBook, prevBookPages) = prevDeferred.await()
                    val prefetchedSeries = seriesDeferred.await()

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
                        nextBook = null,
                        nextBookPages = emptyList(),
                    )
                    preloadFirstPage(prevBook)
                    currentBookId.value = bookId
                    prefetchNextBook()
                    updateCurrentSeriesAndReaderType(newBook, prefetchedSeries)
                    state.value = LoadState.Success(Unit)
                }
            }

            // The sync-blob reconciliation touches no BookState invariant and is
            // never needed for the first page, so it runs after paint.
            stateScope.launch { runCatching { initialSync() } }
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

    /**
     * Page list via [BookPagesCache]: a hit costs zero server round-trips, a
     * miss fetches and persists. Keyed by the book's fileHash so a replaced
     * CBZ can never be served stale pages.
     */
    private suspend fun loadBookPages(book: KomeliaBook): List<PageMetadata> {
        BookPagesCache.get(book.id, book.fileHash)?.let { return it }
        return loadBookPagesFromServer(book.id).also {
            BookPagesCache.put(book.id, book.fileHash, it)
        }
    }

    private suspend fun loadBookPagesFromServer(bookId: KomgaBookId): List<PageMetadata> {
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

    /**
     * [prefetchedSeries] lets initialize() overlap the series fetch with the
     * other open-path calls; null (the default, used by the next/previous-book
     * transitions) keeps the original fetch-here behaviour.
     */
    private suspend fun updateCurrentSeriesAndReaderType(
        book: KomeliaBook,
        prefetchedSeries: KomgaSeries? = null,
    ) {
        // Reset the per-book webtoon flag; we'll set it back below if the new
        // book also qualifies.
        detectedAsWebtoon.value = false

        val baseReaderType = if (!book.seriesId.value.startsWith("local")) {
            val currentSeries = prefetchedSeries ?: seriesApi.getOneSeries(book.seriesId)
            series.value = currentSeries
            when (currentSeries.metadata.readingDirection) {
                KomgaReadingDirection.LEFT_TO_RIGHT -> ReaderType.PAGED
                KomgaReadingDirection.RIGHT_TO_LEFT -> ReaderType.PAGED
                // Webtoons read best in PANELS mode (auto-zoom on each detected
                // panel). But PANELS needs the ONNX panel detector to render;
                // when it isn't loaded, fall back to CONTINUOUS (vertical
                // scroll) rather than PANELS — otherwise the reader collapses to
                // PAGED, which is the worst option for a tall strip.
                KomgaReadingDirection.WEBTOON -> webtoonReaderType()
                KomgaReadingDirection.VERTICAL, null -> readerSettingsRepository.getReaderType().first()
            }
        } else {
            readerSettingsRepository.getReaderType().first()
        }
        readerType.value = baseReaderType

        // Local webtoon auto-detect override. Only applies when:
        //  - the setting is ON
        //  - the user hasn't manually flipped readerType already this session
        //  - the first-5-pages heuristic (>=3 tall) classifies it as a webtoon
        // Same fallback as the metadata branch above: PANELS when the detector
        // is loaded, otherwise CONTINUOUS.
        val autoDetectOn = readerSettingsRepository.getPagedAutoDetectWebtoon().first()
        val pages = booksState.value?.currentBookPages ?: emptyList()
        if (!userOverrodeReaderType && autoDetectOn && isWebtoonLikely(pages)) {
            detectedAsWebtoon.value = true
            readerType.value = webtoonReaderType()
        }
    }

    /**
     * Webtoons always read in CONTINUOUS.
     *
     * PANELS is deliberately NOT used here any more: on a tall strip its panel
     * ordering falls apart — reading starts mid- or end-of-page and skips 4-5
     * image zones (the same ordering weakness measured at ~13% of normal manga
     * pages, amplified by strip geometry). Plain vertical scrolling is strictly
     * better for the format. PAGED is never an option either: a tall strip in
     * paged mode is unreadable.
     *
     * [webtoonSmartScroll] therefore no longer picks the reader type; it selects
     * how a screen tap advances (currently a fixed ~80% of the viewport, soon a
     * snap to the next content block).
     */
    private fun webtoonReaderType(): ReaderType = ReaderType.CONTINUOUS

    /**
     * Heuristic: of the first 5 pages, at least 3 must be at least
     * [WEBTOON_MIN_ASPECT] times taller than they are wide. Looking at 5 (not 3)
     * and requiring 3 (not all) tolerates a normal-ratio first page (a cover) or
     * an occasional short page without losing the detection. Uses the raw image
     * dimensions from Komga metadata, BEFORE any pipeline processing (crop
     * borders etc.), so the ratio reflects the file itself.
     *
     * The test is strictly HEIGHT / WIDTH, so a wide double-page spread can never
     * trigger it: those come out around 0.7 (twice as wide as tall), i.e. below
     * 1 — the opposite end of the scale from any threshold we'd set here.
     */
    private fun isWebtoonLikely(pages: List<PageMetadata>): Boolean {
        val tall = pages.take(5).count { page ->
            val size = page.size ?: return@count false
            size.width > 0 && size.height.toFloat() / size.width.toFloat() >= WEBTOON_MIN_ASPECT
        }
        return tall >= 3
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

    /**
     * The lookup of the book AFTER the one just opened, running in the
     * background — see [prefetchNextBook]. Joined before anything reads the
     * `nextBook` it fills in.
     */
    private var nextBookPrefetch: Job? = null

    /**
     * Waits for the next-book lookup started by the last forward move, if any.
     *
     * For readers that snapshot `nextBook` into their end-of-book page: the
     * snapshot can be taken before the lookup lands (a two-page extra, or a jump
     * straight to the last page), and would then claim the series ends here.
     */
    suspend fun awaitNextBook() {
        nextBookPrefetch?.join()
    }

    /**
     * Fills in the `nextBook` that [initialize] and [loadNextBook] deliberately
     * leave empty.
     *
     * Off the critical path on purpose. The reader has everything it needs to
     * paint the volume the user asked for; making them wait on a lookahead they
     * will not reach for another twenty minutes is what made both opening a book
     * and changing volume feel slow.
     *
     * How slow was measured, not guessed. Three consecutive opens: 2788ms,
     * 11940ms and 14354ms — the critical path every time, ten times anything
     * else in the wave. It is the one lookup that can MISS: on the last volume
     * of a series `getBookSiblingNext` 404s, and [getNextSeriesFirstBook] then
     * walks the series list and every book page of the series that follows.
     *
     * Forward only. The backward move keeps its synchronous fetch: it measured
     * 443-2506ms, never the wall, and it is the one that has to restore a page
     * position.
     *
     * This publishes a SECOND booksState emission for the same book, which is
     * why the readers key their "book changed" work on the current book's id.
     *
     * Runs on [stateScope], so leaving the reader cancels it.
     */
    private fun prefetchNextBook() {
        val anchor = booksState.value?.currentBook ?: return
        nextBookPrefetch?.cancel()
        nextBookPrefetch = stateScope.launch {
            // Still measured, just no longer waited on: this is the number that
            // used to BE the open time, and it is worth knowing when it drifts.
            val (next, pages) = PerfTrace.measure("reader.nextBook background") {
                val nb = getNextBook(anchor)
                nb to (if (nb != null) loadBookPages(nb) else emptyList())
            }

            // Only if the reader is still on the book we started from: a fast
            // second page-turn has already moved on, and its own prefetch owns
            // the state now.
            val current = booksState.value ?: return@launch
            if (current.currentBook.id != anchor.id) return@launch
            booksState.value = current.copy(nextBook = next, nextBookPages = pages)
            preloadFirstPage(next)
        }
    }

    suspend fun loadNextBook() {
        nextBookPrefetch?.join()
        val booksState = requireNotNull(booksState.value)
        if (booksState.nextBook != null) {
            val outgoingBook = booksState.currentBook

            // Swap the book state and reset the page in one uninterrupted block
            // (no suspension between). Setting the page BEFORE the swap left a
            // window where a concurrent progress push saw the OLD book paired
            // with page 1 and wiped the finished book's progress. See
            // updateCacheAndPush.
            //
            // Everything this swap needs is already in hand — the incoming book
            // and its pages were prefetched when the CURRENT one was opened. The
            // book after it is not, and looking it up here is what the user was
            // waiting on: one sibling call when there is a next volume, but a
            // walk through the series list and every book page of the following
            // series when there isn't. See prefetchNextBook.
            this.booksState.value = BookState(
                currentBook = booksState.nextBook,
                currentBookPages = booksState.nextBookPages,
                previousBook = booksState.currentBook,
                previousBookPages = booksState.currentBookPages,

                nextBook = null,
                nextBookPages = emptyList()
            )
            readProgressPage.value = 1
            currentBookId.value = booksState.nextBook.id
            prefetchNextBook()
            // Moving on to the next book means we're done with the one we're
            // leaving — mark it read. This covers the "next volume" skip button
            // from mid-book (user's choice: skipping = finished); when we got
            // here by reading to the last page it's already complete, so the
            // call is idempotent. Runs AFTER the swap so a queued progress push
            // can't retarget the outgoing book, and off the critical path.
            if (markReadProgress) {
                finalFlushScope.launch {
                    runCatching {
                        bookApi.markReadProgress(
                            outgoingBook.id,
                            KomgaBookReadProgressUpdateRequest(completed = true),
                        )
                    }
                }
            }
            updateCurrentSeriesAndReaderType(booksState.nextBook)
            onProgressChange(1)
        } else {
            // LAST volume of the series: there is nothing to move on to, but
            // moving on is exactly what the user just did — same rule as above,
            // and the only place it was missing. Without this, finishing a
            // series' last volume left the book "in progress" forever: it stayed
            // on the Keep-reading shelf and the series never counted as read,
            // and it had to be marked by hand.
            if (markReadProgress) {
                finalFlushScope.launch {
                    runCatching {
                        bookApi.markReadProgress(
                            booksState.currentBook.id,
                            KomgaBookReadProgressUpdateRequest(completed = true),
                        )
                    }
                }
            }
            navigator replace MainScreen(
                if (booksState.currentBook.oneshot) OneshotScreen(booksState.currentBook, bookSiblingsContext)
                else SeriesScreen(booksState.currentBook.seriesId)
            )
        }
    }

    suspend fun loadPreviousBook(fromStart: Boolean = false) {
        nextBookPrefetch?.join()
        val booksState = requireNotNull(booksState.value)
        if (booksState.previousBook != null) {
            val outgoingBook = booksState.currentBook
            // Deliberately NOT deferred, unlike loadNextBook. Going back a
            // volume is rare and was never the slow path, so it keeps the one
            // shape that is known to restore the right page — the deferred
            // version emitted the book state twice and the second emission
            // re-seeked the volume you had just landed in.
            val previousBook = getPreviousBook(booksState.previousBook.id)
            val previousBookPages =
                if (previousBook != null) loadBookPages(previousBook) else emptyList()

            // Swap first, THEN set the page — no suspension between — so a
            // concurrent progress push never pairs the outgoing book with the
            // incoming page. See loadNextBook / updateCacheAndPush.
            val restoredPage = if (fromStart) 1 else booksState.previousBookPages.size
            this.booksState.value = BookState(
                currentBook = booksState.previousBook,
                currentBookPages = booksState.previousBookPages,
                nextBook = booksState.currentBook,
                nextBookPages = booksState.currentBookPages,

                previousBook = previousBook,
                previousBookPages = previousBookPages,
            )
            readProgressPage.value = restoredPage
            currentBookId.value = booksState.previousBook.id
            // Mirror of loadNextBook: backing out of a book means we're NOT done
            // with it — mark it unread. Symmetric with the "next volume" skip
            // marking the volume we leave as read. Runs AFTER the swap so a
            // queued progress push can't retarget the outgoing book.
            if (markReadProgress) {
                finalFlushScope.launch {
                    runCatching { bookApi.deleteReadProgress(outgoingBook.id) }
                }
            }
        } else
            appNotifications.add(AppNotification.Normal("You're at the beginning of the book"))
        return
    }

    fun onProgressChange(page: Int) {
        // Capture the latest page SYNCHRONOUSLY. The conflated channel + its
        // consumer run on stateScope, which is cancelled on exit BEFORE the last
        // send is drained — so onDispose / flushProgressNow would otherwise push
        // a stale readProgressPage and silently lose the last page turn.
        readProgressPage.value = page
        logger.debug { "[ReadProgress] onProgressChange page=$page book=${booksState.value?.currentBook?.id?.value}" }
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

    fun onInvertSpeechBubblesChange(invert: Boolean) {
        invertSpeechBubbles.value = invert
        // The pipeline's BubbleInvertStep observes the repository flow, so the
        // write is what actually re-runs processing on the visible pages.
        stateScope.launch { readerSettingsRepository.putInvertSpeechBubbles(invert) }
    }

    fun onWebtoonSmartScrollChange(enabled: Boolean) {
        webtoonSmartScroll.value = enabled
        stateScope.launch { readerSettingsRepository.putWebtoonSmartScroll(enabled) }
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

    fun onKeepProgressBarVisibleWhileReadingChange(enabled: Boolean) {
        keepProgressBarVisibleWhileReading.value = enabled
        stateScope.launch {
            readerSettingsRepository.putKeepProgressBarVisibleWhileReading(enabled)
        }
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
        clearScanCache()
        stateScope.launch { readerSettingsRepository.putOcrSettings(newSettings) }
    }

    /**
     * One page scanned at a time. Recognition is a blocking native call with no
     * suspension point, so nothing else can stop one that has started. Without
     * this lock every page turn began another — six concurrent RapidOCR
     * inferences were measured, ending in an OutOfMemoryError.
     */
    private val ocrMutex = Mutex()

    /**
     * Pages already scanned, so turning back to one is instant.
     *
     * Recognition measured 2.2-4.7 s per page (mean 3.9 s over a chapter) and
     * the result cannot change: same image, same engine, same target language.
     * Without this, going back one page paid the full cost again.
     *
     * Bounded, and ordered by insertion so the oldest entry goes first — a
     * long book would otherwise keep every page's boxes alive for a reader
     * session.
     *
     * Held in a flow rather than a plain map because a queued scan reads it
     * from [Dispatchers.Default] while page turns write it from the state
     * thread. Replacing an immutable map wholesale keeps that safe; the worst
     * a lost race can do is drop one entry, costing a rescan.
     */
    private val scanCache = MutableStateFlow<Map<PageId, PageScan>>(emptyMap())

    private fun cacheScan(pageId: PageId, result: PageScan) {
        val next = LinkedHashMap(scanCache.value)
        next.remove(pageId) // re-inserted at the end: touched entries age last
        next[pageId] = result
        while (next.size > SCAN_CACHE_SIZE) next.remove(next.keys.first())
        scanCache.value = next
    }

    /**
     * Dropped whenever the engine or the languages change: the cached boxes and
     * translations were produced by the old settings.
     */
    private fun clearScanCache() {
        scanCache.value = emptyMap()
    }

    fun scanCurrentPageForText(image: ReaderImage) {
        val pageId = image.pageId
        val cached = scanCache.value[pageId]
        if (cached != null) {
            cacheScan(pageId, cached) // touched: keep it from ageing out
            translationLogger.info {
                "scan cache hit $pageId " +
                        "(${cached.boxes.size} boxes, ${cached.translations.size} translated)"
            }
            ocrPageId.value = pageId
            ocrResults.value = cached.boxes
            translatedBlocks.value = cached.translations
            isOcrLoading.value = false
            isTranslating.value = false
            return
        }
        // Deliberately NOT cancelled on a page turn. Recognition is a blocking
        // native call that cancellation cannot interrupt, so cancelling it only
        // threw away a result that had already cost its 4 seconds and was still
        // valid for its own page — which is why turning back to a page rescanned
        // it from scratch. A scan nobody wants any more skips itself when it
        // reaches the front of the queue, before doing any work.
        stateScope.launch {
            ocrPageId.value = pageId
            isOcrLoading.value = true
            try {
                // Measured (KoraPerf) even though it is per-page: OCR only runs when
                // the user has explicitly enabled it, so this is not on the normal
                // reading path. The engine is part of the label so ML Kit and
                // RapidOCR can be compared from the same log.
                val storedSettings = ocrSettings.value
                // The OCR language and the translation source are two persisted
                // fields, and the first only followed the second when the picker
                // was actually touched — so an install left on Japanese kept
                // scanning English pages with the orientation classifier on. That
                // classifier is trained on Chinese text and flips short Latin
                // crops end over end: "I'M" came back as "W,I", "OH!" as "iHO",
                // "DON'T" as ",noa". When translating, the source language is the
                // only one that can be right, so it wins.
                val translationSetting = translationSettings.value
                val settings = if (translationSetting.enabled) {
                    val language =
                        if (translationSetting.source == snd.komelia.settings.model.TranslationLanguage.JAPANESE) OcrLanguage.JAPANESE
                        else OcrLanguage.LATIN
                    storedSettings.copy(selectedLanguage = language)
                } else storedSettings
                // The whole thing, queue included. Every stage below is already
                // timed on its own, but they all start after the mutex is taken:
                // a page that waited four seconds behind another scan looks
                // instant in each of them and slow to the reader, and that gap
                // is exactly what the document calls TimeToReady. Subtracting
                // the stages from this is how much was spent waiting.
                val result = performScan(
                    image = image,
                    pageId = pageId,
                    settings = settings,
                    translationSetting = translationSetting,
                    label = "reader.page.total",
                    foreground = true,
                    stillWanted = { ocrPageId.value == pageId },
                )
                if (result == null) return@launch
                cacheScan(pageId, result)
                // A scan that outlived its page must not publish: its boxes would be
                // drawn over a different image, which is the "blue boxes land in the
                // wrong place for a few seconds" symptom. It is still cached above —
                // that is the whole point.
                if (ocrPageId.value == pageId) {
                    ocrResults.value = result.boxes
                    translatedBlocks.value = result.translations
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                appNotifications.add(AppNotification.Error("OCR failed: ${e.message}"))
            } catch (e: OutOfMemoryError) {
                // Normally not something to catch, but the allocation that fails is
                // the engine's own tensors and bitmap, all released by unwinding
                // here. The alternative is measured: the process dies.
                appNotifications.add(AppNotification.Error("OCR ran out of memory on this page"))
            } finally {
                if (ocrPageId.value == pageId) isOcrLoading.value = false
            }
        }
    }


    /**
     * The scan itself, shared by the page on screen and the one being prepared
     * behind it.
     *
     * [stillWanted] is checked once the lock is held rather than before queuing:
     * a scan can sit behind another for seconds, and by the time it starts the
     * reader may have moved somewhere else entirely. [label] separates the two
     * callers in the timings, because a prefetch costs the same milliseconds and
     * none of the waiting — averaging them together would hide exactly the thing
     * the prefetch exists to change.
     */
    private suspend fun performScan(
        image: ReaderImage,
        pageId: PageId,
        settings: OcrSettings,
        translationSetting: snd.komelia.settings.model.TranslationSettings,
        label: String,
        foreground: Boolean,
        stillWanted: () -> Boolean,
    ): PageScan? = snd.komelia.perf.PerfTrace.measure(label) {
        withContext(Dispatchers.Default) {
            ocrMutex.withLock {
                // Queued behind an in-flight scan. Two things can have
                // happened while waiting: the reader moved on, and this
                // page is nobody's business any more; or the page came
                // back and was scanned by another job, whose result is
                // now in the cache.
                scanCache.value[pageId] ?: if (!stillWanted()) null else {
                    // Diagnostic, not decoration. Reading logs show every page
                    // recognised twice, five seconds apart, for byte-identical
                    // results — and never a "scan cache hit" to explain it.
                    // Which page each scan is for, and which caller asked, is
                    // the one thing the log does not say, so nothing can be
                    // concluded without it. Remove once that is settled.
                    translationLogger.info {
                        "scan start $pageId via $label, cached=${scanCache.value.keys}"
                    }
                    val scanned = snd.komelia.perf.PerfTrace.measure(
                        "reader.ocr.${settings.engine}",
                        count = { it.size }
                    ) { ocrService.recognizeText(image, settings) }

                    // Only when translating, and before the merge: see
                    // OcrScriptFilter. Plain OCR keeps every script, so
                    // reading a Japanese page with text selection still works.
                    val japanese = translationSetting.enabled &&
                            translationSetting.source == snd.komelia.settings.model.TranslationLanguage.JAPANESE
                    val rawBoxes = if (translationSetting.enabled) {
                        if (japanese) snd.komelia.image.OcrScriptFilter.keepCjk(scanned)
                        else snd.komelia.image.OcrScriptFilter.keepLatin(scanned)
                    } else scanned

                    // Counted in blocks, not words — one block is one
                    // translation call.
                    val boxes = if (settings.mergeBoxes) {
                        snd.komelia.perf.PerfTrace.measure(
                            "reader.ocr.merge",
                            count = { merged -> merged.distinctBy { it.blockIndex }.size }
                        ) {
                            // The page width is what tells a full-width
                            // shout apart from several bubbles welded
                            // together; without it that check is off.
                            mergeOcrBoxes(
                                boxes = rawBoxes,
                                direction = readingDirection.value,
                                vertical = japanese,
                                pageWidth = image.getOriginalImageSize().getOrNull()?.width ?: 0,
                            )
                        }
                    } else rawBoxes

                    PageScan(boxes, translateBlocks(boxes, foreground))
                }
            }
        }
    }

    /**
     * The page being prepared ahead of the reader, if any.
     *
     * Separate from [ocrPageId] on purpose: that one says what the overlay is
     * drawn against, and a prefetch must never claim it.
     */
    private val prefetchPageId = MutableStateFlow<PageId?>(null)
    private var prefetchJob: Job? = null

    /**
     * Scans the page after this one while the reader is still on this one.
     *
     * This is the only lever left against the four seconds a page costs. The
     * work itself is irreducible — recognition is 93% of it, and the input size,
     * the thread counts and the runtime options were each measured and each gave
     * single digits at best. What can be changed is *when* it happens: a reader
     * spends longer looking at a page than the scan takes, and that time is
     * otherwise spent idle.
     *
     * Deliberately one page, not two. Beyond N+1 the reader is guessing about a
     * page the user may never reach, and it pays for the guess in battery and
     * heat on a device that has neither to spare.
     *
     * Takes the same lock as the page on screen, so the two never run at once:
     * OCR and translation are both CPU, and overlapping them on a tablet buys a
     * few seconds of throughput and then loses them to throttling. A page turn
     * that lands on the page being prefetched costs nothing extra — the
     * foreground scan queues on the lock and finds the result already cached,
     * which is what the cache check inside the lock is for.
     */
    fun prefetchNextPageForText(image: ReaderImage) {
        val pageId = image.pageId
        if (pageId == ocrPageId.value) return
        if (scanCache.value.containsKey(pageId)) return
        if (prefetchPageId.value == pageId && prefetchJob?.isActive == true) return
        // Only when it is for somewhere else. Cancelling a prefetch of the page
        // the reader is about to turn to would throw away the very work that
        // makes the turn instant.
        prefetchJob?.cancel()
        prefetchPageId.value = pageId
        prefetchJob = stateScope.launch {
            val storedSettings = ocrSettings.value
            val translationSetting = translationSettings.value
            val settings = if (translationSetting.enabled) {
                val language =
                    if (translationSetting.source == snd.komelia.settings.model.TranslationLanguage.JAPANESE) OcrLanguage.JAPANESE
                    else OcrLanguage.LATIN
                storedSettings.copy(selectedLanguage = language)
            } else storedSettings
            try {
                val result = performScan(
                    image = image,
                    pageId = pageId,
                    settings = settings,
                    translationSetting = translationSetting,
                    label = "reader.page.prefetch",
                    // Nothing on screen belongs to this page, so none of the
                    // reader's progress indicators may move for it.
                    foreground = false,
                    stillWanted = { prefetchPageId.value == pageId },
                ) ?: return@launch
                cacheScan(pageId, result)
                translationLogger.info {
                    "prefetched $pageId (${result.boxes.size} boxes, " +
                            "${result.translations.size} translated)"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Silent by design: nobody asked for this page yet. If it is
                // reached, the foreground scan will run and report properly.
                logger.debug(e) { "prefetch of $pageId failed" }
            } catch (e: OutOfMemoryError) {
                logger.warn { "prefetch of $pageId ran out of memory" }
            }
        }
    }

    /** Looks at what is on disk for the current pair. */
    fun refreshTranslationModel() {
        val downloader = translationModelDownloader
        val settings = translationSettings.value
        if (downloader == null || !downloader.supports(settings.source, settings.target)) {
            translationModelState.value = null
            return
        }
        translationModelState.value =
            if (downloader.isDownloaded(settings.source, settings.target)) TranslationModelState.Ready
            else TranslationModelState.Missing
    }

    /** The settings panel's button. The work itself is shared with the automatic path. */
    fun downloadTranslationModel() {
        stateScope.launch { fetchBetterModel(translationSettings.value) }
    }

    /**
     * Fetches the better engine's model. Reports bytes as they arrive, because
     * 36MB over a phone connection is long enough that a silent button reads as
     * a broken one.
     *
     * Suspends rather than launching, so the automatic path can wait for it and
     * report once at the end instead of racing its own notification.
     */
    private suspend fun fetchBetterModel(
        settings: snd.komelia.settings.model.TranslationSettings,
    ): Boolean {
        val downloader = translationModelDownloader ?: return false
        if (!downloader.supports(settings.source, settings.target)) return false
        if (downloader.isDownloaded(settings.source, settings.target)) return true
        return try {
            downloader.download(settings.source, settings.target).collect { progress ->
                translationModelState.value = TranslationModelState.Downloading(
                    completed = progress.completed,
                    total = progress.total,
                    what = progress.description.orEmpty(),
                )
            }
            translationModelState.value = TranslationModelState.Ready
            // The scan cache holds pages translated by the other engine.
            clearScanCache()
            true
        } catch (e: CancellationException) {
            refreshTranslationModel()
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "translation model download failed" }
            appNotifications.add(AppNotification.Error("Model download failed: ${e.message}"))
            refreshTranslationModel()
            false
        }
    }

    fun deleteTranslationModel() {
        val downloader = translationModelDownloader ?: return
        val settings = translationSettings.value
        downloader.delete(settings.source, settings.target)
        clearScanCache()
        refreshTranslationModel()
    }

    /** Reloads [glossaryTerms] for the series on screen. */
    fun refreshGlossaryTerms() {
        stateScope.launch {
            val seriesId = booksState.value?.currentBook?.seriesId
            glossaryTerms.value = try {
                translationGlossaryRepository.list(seriesId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "could not list the translation glossary" }
                emptyList()
            }
        }
    }

    /**
     * Adds or replaces a term for the series on screen. A blank [target] means
     * "leave this word alone", which is what a name needs.
     *
     * Does not re-scan the current page: the scan cache still holds the old
     * translation, and re-running OCR to repaint one bubble costs seconds. The
     * term applies from the next page on.
     */
    fun addGlossaryTerm(source: String, target: String) {
        if (source.isBlank()) return
        stateScope.launch {
            val seriesId = booksState.value?.currentBook?.seriesId
            try {
                translationGlossaryRepository.put(seriesId, source, target)
                glossaryTerms.value = translationGlossaryRepository.list(seriesId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                appNotifications.add(AppNotification.Error("Could not save the term: ${e.message}"))
            }
        }
    }

    fun removeGlossaryTerm(source: String) {
        stateScope.launch {
            val seriesId = booksState.value?.currentBook?.seriesId
            try {
                translationGlossaryRepository.delete(seriesId, source)
                glossaryTerms.value = translationGlossaryRepository.list(seriesId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                appNotifications.add(AppNotification.Error("Could not remove the term: ${e.message}"))
            }
        }
    }

    /**
     * The terms in force for the book on screen: the global ones, overridden by
     * the ones its own series defines.
     *
     * Read from [booksState] rather than from [series], because a read list
     * walks across series and [series] follows the book — but only once it has
     * loaded, and a scan can start before that.
     */
    private suspend fun glossaryForCurrentSeries(): snd.komelia.image.TermGlossary {
        val seriesId = booksState.value?.currentBook?.seriesId
        val terms = try {
            translationGlossaryRepository.termsFor(seriesId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A glossary that cannot be read is not worth losing the page's
            // translation over: without it the text is merely as good as it was
            // before the feature existed.
            logger.warn(e) { "could not read the translation glossary" }
            emptyMap()
        }
        return if (terms.isEmpty()) snd.komelia.image.TermGlossary.EMPTY
        else snd.komelia.image.TermGlossary(terms)
    }

    /**
     * Translates the OCR blocks of a page that was just scanned, when page
     * translation is on. Returns what should be painted rather than publishing
     * it: the caller decides, because a scan is also worth caching for a page
     * the reader has already left.
     *
     * Blocks are translated as whole sentences, never word by word: [ocrResults]
     * holds one entry per word, and a bubble only makes sense reassembled.
     */
    /**
     * Reads the shipped expression table into [snd.komelia.image.PhraseBook].
     *
     * Two thousand entries live in a resource rather than in the source because
     * a `mapOf` literal that size does not survive the 255-argument limit. Read
     * on the first translation rather than at startup: the reader opens far more
     * often than translation is switched on, and 83KB of JSON is not worth
     * parsing for someone who never turns it on.
     *
     * A failure here is not fatal. The curated table is compiled in and keeps
     * working on its own, so the reader loses the general expressions and
     * nothing else.
     */
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    private suspend fun loadPhraseBook() {
        if (snd.komelia.image.PhraseBook.isLoaded) return
        runCatching {
            val bytes = io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
                .readBytes("files/phrasebook/en-fr.json")
            val table = kotlinx.serialization.json.Json
                .decodeFromString<Map<String, String>>(bytes.decodeToString())
            snd.komelia.image.PhraseBook.load(table)
            translationLogger.info { "phrase book loaded, ${table.size} expressions" }
        }.onFailure {
            logger.warn(it) { "could not read the phrase book — the curated table still applies" }
        }
    }

    /**
     * Reads the shipped word list into [snd.komelia.image.OcrSpellRepair].
     *
     * Same shape and same reasoning as the phrase book above: a resource rather
     * than source, read on the first translation rather than at startup. A
     * failure leaves the repair switched off, which is the behaviour it had
     * before it existed.
     */
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    private suspend fun loadLexicon() {
        if (snd.komelia.image.OcrSpellRepair.isLoaded) return
        runCatching {
            val bytes = io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
                .readBytes("files/lexicon/en.txt")
            val words = bytes.decodeToString().lineSequence()
                .filter { it.isNotBlank() }
                .toSet()
            snd.komelia.image.OcrSpellRepair.load(words)
            translationLogger.info { "lexicon loaded, ${words.size} words" }
        }.onFailure {
            logger.warn(it) { "could not read the lexicon — OCR misreads go through unrepaired" }
        }
    }

    /**
     * Reads the katakana glossary, once. The Japanese counterpart of
     * [loadLexicon], and non-fatal for the same reason: without it the page
     * still translates, it just keeps the invented character names.
     */
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    private suspend fun loadKatakanaGlossary() {
        if (snd.komelia.image.JapaneseKatakanaGlossary.isLoaded) return
        runCatching {
            val bytes = io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
                .readBytes("files/japanese/katakana.json")
            snd.komelia.image.JapaneseKatakanaGlossary.load(bytes.decodeToString())
            translationLogger.info {
                "katakana glossary loaded, ${snd.komelia.image.JapaneseKatakanaGlossary.size} rewrites"
            }
        }.onFailure {
            logger.warn(it) { "could not read the katakana glossary — emphatic katakana goes through as-is" }
        }
    }

    /** The Japanese phrase book, same contract as [loadPhraseBook]. */
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    private suspend fun loadJapanesePhraseBook() {
        if (snd.komelia.image.JapanesePhraseBook.isLoaded) return
        runCatching {
            val bytes = io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
                .readBytes("files/phrasebook/ja-fr.json")
            val table = kotlinx.serialization.json.Json
                .decodeFromString<Map<String, String>>(bytes.decodeToString())
            snd.komelia.image.JapanesePhraseBook.load(table)
            translationLogger.info { "japanese phrase book loaded, ${table.size} expressions" }
        }.onFailure {
            logger.warn(it) { "could not read the japanese phrase book — every balloon goes to the engine" }
        }
    }

    /**
     * @param foreground false when this page is being prepared behind the one on
     *   screen. The spinner belongs to the page the reader is looking at, and a
     *   prefetch that lit it up would have it turning while nothing is waiting.
     */
    private suspend fun translateBlocks(
        boxes: List<OcrElementBox>,
        foreground: Boolean,
    ): Map<Int, String> {
        val settings = translationSettings.value
        if (!settings.enabled || boxes.isEmpty()) return emptyMap()
        if (settings.source == settings.target) return emptyMap()

        val japanese = settings.source == snd.komelia.settings.model.TranslationLanguage.JAPANESE

        // Read on every scan rather than held per book: the query is a handful
        // of rows on a primary key, next to nothing beside the OCR that just
        // ran, and it means a term added from the glossary screen applies to
        // the very next page instead of after leaving the reader.
        val glossary = glossaryForCurrentSeries()
        // Before the blocks are built, because the repair is part of reading the
        // page rather than part of translating it: the boxes it fixes are what
        // the sentence casing, the sound-effect test and the phrase book all
        // work from.
        if (!japanese) loadLexicon()
        val blocks = boxes
            .groupBy { it.blockIndex }
            .mapValues { (_, elements) ->
                elements
                    .sortedWith(compareBy({ it.lineIndex }, { it.elementIndex }))
                    // Japanese does not separate words with spaces, and inserting
                    // them between the columns of a bubble splits words that the
                    // detector happened to cut in two.
                    .joinToString(if (japanese) "" else " ") { it.text }
                    .trim()
                    // Both cleanups are about Latin lettering: a word the
                    // letterer broke across two lines, and the full caps comics
                    // are drawn in. Neither exists in Japanese.
                    .let {
                        if (japanese) it
                        else snd.komelia.image.TranslationTextUtils.rejoinLineBreaks(it)
                            // After the line breaks are rejoined, so the repair
                            // sees whole words: "PLID- DING" is two fragments
                            // until then and neither one is repairable. Before
                            // the sentence casing, which is why the repair puts
                            // the capitals back itself.
                            .let { text -> snd.komelia.image.OcrSpellRepair.apply(text) }
                            .let { text -> snd.komelia.image.TranslationTextUtils.toSentenceCase(text) }
                    }
            }
            // Single letters and bare digits are artwork the OCR mistook for
            // text ('R', 'n' at 20x5px, 'e' at 8x7px, '1', 'V'). Translating them
            // is meaningless, and painting an opaque panel over them puts black
            // squares on the drawing.
            //
            // Japanese needs a lower bar: a whole bubble can be two characters
            // ("はい"), where three would already be a sentence.
            .filterValues { text ->
                val letters = text.count { it.isLetter() }
                val longEnough = if (japanese) letters >= 2 else letters >= 2 && text.length >= 3
                // Sound effects are drawn onto the artwork, so a panel over one
                // hides the drawing to say 'Table de Ping'. Latin only; Japanese
                // sound effects are a different problem and it is on hold.
                longEnough && (japanese || !snd.komelia.image.TranslationTextUtils.isSoundEffect(text)) &&
                        // Recognition over artwork returns confident nonsense
                        // rather than nothing, and translating it paints an
                        // opaque panel over the drawing it came from.
                        (japanese || snd.komelia.image.EnglishTextCleaner.isTranslatable(text))
            }
        if (blocks.isEmpty()) return emptyMap()

        if (foreground) isTranslating.value = true
        try {
            // Block indices are already in reading order, so consecutive here
            // means consecutive on the page — which is what lets the balloons of
            // one sentence be recognised as consecutive at all.
            val indices = blocks.keys.toList()
            val ordered = indices.map { blocks.getValue(it) }
            // A sentence lettered across two or three balloons goes to the
            // translator whole. Measured over Ramen Aka Neko 167: 52 of 142
            // blocks were two words or shorter, and a translator given two words
            // of a sentence returns two words of nonsense.
            val groups = if (japanese) ordered.indices.map { listOf(it) }
            else snd.komelia.image.BubbleAssembler.group(ordered)
            val groupSources = groups.map { positions -> positions.map { ordered[it] } }
            // Glossary terms leave as placeholders and come back as themselves.
            // Capitalising them and hoping was tried first and measured on the
            // tablet not to work: "Meryl Strife" went out spelled right and came
            // back "Meryl Conflife". Applied to the joined sentence, so a term
            // split across two balloons is still one term.
            if (japanese) {
                loadKatakanaGlossary()
                loadJapanesePhraseBook()
            } else loadPhraseBook()
            val joined = groupSources.map { snd.komelia.image.BubbleAssembler.join(it) }
            // After protect, not before: a series glossary term can itself be
            // katakana, and rewriting it first would leave protect nothing to
            // find. The placeholders are Latin ("Xqz0"), so the katakana rules
            // cannot reach them.
            val protectedGroups = joined.map { glossary.protect(it) }
                .map { protectedText ->
                    if (!japanese) protectedText
                    else protectedText.mapText { snd.komelia.image.JapaneseKatakanaGlossary.apply(it) }
                }
            // Idioms the engine reliably mangles are answered before it sees
            // them: "Something the matter?" came back "Quelque chose la
            // matière ?". A small model has no idiomatic reading to reach for,
            // so this is not something tuning or a bigger model fixes.
            // Looked up on `joined`, before the katakana rewrite: the table is
            // written the way a page is lettered, so a balloon that is an
            // entry matches as it was read, not as it was normalised.
            val known = if (japanese) joined.map { snd.komelia.image.JapanesePhraseBook.lookup(it) }
            else joined.map { snd.komelia.image.PhraseBook.lookup(it) }
            val pending = known.indices.filter { known[it] == null }
            val engineOutput = if (pending.isEmpty()) emptyList() else {
                withContext(Dispatchers.Default) {
                    snd.komelia.perf.PerfTrace.measure(
                        "reader.translate",
                        count = { it.size }
                    ) {
                        translationService.translate(
                            texts = pending.map { protectedGroups[it].text },
                            source = settings.source,
                            target = settings.target,
                        )
                    }
                }
            }
            // Back into group order. The engine was only given the groups the
            // phrase book had no answer for, so its results line up with
            // `pending` rather than with every group.
            val translated = known.toMutableList()
            pending.forEachIndexed { position, groupIndex ->
                translated[groupIndex] = engineOutput.getOrNull(position)
            }
            val published = buildMap {
                groups.forEachIndexed { groupIndex, positions ->
                    val answer = translated[groupIndex] ?: return@forEachIndexed
                    // Terms first: everything downstream compares against the
                    // original sentence, and a placeholder matches nothing in
                    // it. A phrase book answer never went out with placeholders
                    // in it, so restoring is only for what the engine returned.
                    val restored = if (known[groupIndex] != null) answer
                    else protectedGroups[groupIndex].restore(answer)
                    val pieces = snd.komelia.image.BubbleAssembler
                        .distribute(restored, groupSources[groupIndex])
                    positions.forEachIndexed { pieceIndex, position ->
                        // Honorifics survive translation, the name in front of
                        // them does not: "MAMA-SAN" came back as "Maman-san".
                        val named = snd.komelia.image.TranslationTextUtils
                            .restoreNames(ordered[position], pieces[pieceIndex])
                        put(indices[position], named)
                    }
                }
            }
                // Sound effects translate to themselves. Painting a panel over
                // one hides the drawing to display the same word — and a blank
                // result (「はっ」 came back empty) paints an empty one.
                .filter { (blockIndex, text) ->
                    text.isNotBlank() && !snd.komelia.image.TranslationTextUtils
                        .isUnchanged(blocks.getValue(blockIndex), text)
                }

            // Diagnostic dump: which blocks exist, where they are and what came
            // back. Bubbles that look untranslated on screen are either a block
            // the OCR split, a rect smaller than the bubble, or a translation
            // that came back unchanged — and only this tells the three apart.
            //     adb logcat | Select-String "KoraTranslate"
            indices.forEach { blockIndex ->
                val blockBoxes = boxes.filter { it.blockIndex == blockIndex }
                val rect = blockBoxes.first().blockRect
                // Shows what is actually painted, so a block dropped as a sound
                // effect is visible as "(dropped)" rather than silently absent.
                val shown = published[blockIndex] ?: "(dropped)"
                // Lowest confidence in the block. A bad line reads the same on
                // screen whether the OCR misread it or the translator mangled
                // it; this number is what tells the two apart without guessing.
                val worst = blockBoxes.minOf { it.confidence }
                // Share of the painted panel its own lettering covers, and how
                // many detected lines went into it. A block that reads as two
                // bubbles interleaved is low fill and many lines; a real bubble
                // is high fill. Logged rather than guessed at, so the merge
                // threshold can be set from real pages instead of intuition.
                val blockArea = rect.width * rect.height
                val fill = if (blockArea <= 0f) 1f
                else blockBoxes.sumOf { (it.imageRect.width * it.imageRect.height).toDouble() }
                    .toFloat() / blockArea
                translationLogger.info {
                    "block $blockIndex rect=[${rect.left.toInt()},${rect.top.toInt()} " +
                            "${rect.width.toInt()}x${rect.height.toInt()}] " +
                            "conf=${(worst * 100).toInt()}% " +
                            "fill=${(fill * 100).toInt()}% lines=${blockBoxes.size} " +
                            "src='${blocks.getValue(blockIndex)}' " +
                            // Only when the block was not translated on its own,
                            // so the ordinary line stays readable. This is what
                            // tells a bad translation apart from a balloon that
                            // was given the wrong share of a joined sentence.
                            (groups.firstOrNull { group ->
                                group.size > 1 && group.any { indices[it] == blockIndex }
                            }?.let { group ->
                                "sent='${protectedGroups[groups.indexOf(group)].text}' "
                            } ?: "") +
                            "-> '$shown'"
                }
            }
            return published
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            appNotifications.add(AppNotification.Error("Translation failed: ${e.message}"))
            return emptyMap()
        } finally {
            if (foreground) isTranslating.value = false
        }
    }

    fun onTranslationSettingsChange(newSettings: snd.komelia.settings.model.TranslationSettings) {
        translationSettings.value = newSettings
        translatedBlocks.value = emptyMap()
        clearScanCache()
        // The OCR has to be looking for the script we are about to translate.
        // Leaving them out of step is a silent failure: the engine reads Latin
        // on a Japanese page, finds nothing, and translation looks broken.
        val ocrLanguage = when (newSettings.source) {
            snd.komelia.settings.model.TranslationLanguage.JAPANESE -> OcrLanguage.JAPANESE
            else -> OcrLanguage.LATIN
        }
        if (ocrSettings.value.selectedLanguage != ocrLanguage) {
            onOcrSettingsChange(ocrSettings.value.copy(selectedLanguage = ocrLanguage))
        }
        stateScope.launch { readerSettingsRepository.putTranslationSettings(newSettings) }
        if (newSettings.enabled) ensureTranslationModels(newSettings)
    }

    /**
     * Fetches the language models the first time a pair is used. Without this
     * every translate call fails until the user finds a settings screen to
     * download from, which reads as "translation is broken".
     *
     * Both engines, in one go. The better one used to be a separate button in
     * the settings panel, and the cost of that was measured the hard way: a
     * fresh install — a new tablet, or the debug build, which has its own
     * storage — fell back to the bundled engine without a word, and a page of
     * its output was read as a quality regression in Kora. The engine that
     * reads better is not an option to opt into; it is what this feature is.
     *
     * The bundled one is still fetched, and first: it is the fallback while the
     * 36MB arrives, and it is the only one that covers a pair Bergamot has no
     * model for.
     *
     * Not restricted to Wi-Fi: the download only happens when the user turns the
     * feature on, and a switch that silently does nothing off Wi-Fi is worse
     * than one that says how big the download is.
     */
    private fun ensureTranslationModels(settings: snd.komelia.settings.model.TranslationSettings) {
        stateScope.launch {
            val betterMissing = translationModelDownloader
                ?.let {
                    it.supports(settings.source, settings.target) &&
                            !it.isDownloaded(settings.source, settings.target)
                } == true
            val bundledMissing = !translationService.isReady(settings.source, settings.target)
            if (!betterMissing && !bundledMissing) return@launch

            // The bundled engine is ~30MB per language and does not report its
            // size; the better one does, and it is the one that varies —
            // Japanese pulls two packs because it goes through English.
            val betterSize = translationModelDownloader
                ?.estimatedMegabytes(settings.source, settings.target) ?: 0
            appNotifications.add(
                AppNotification.Normal(
                    if (betterMissing) "Downloading translation models (~${30 + betterSize}MB)"
                    else "Downloading translation model (~30MB per language)"
                )
            )
            try {
                if (bundledMissing) {
                    snd.komelia.perf.PerfTrace.measure("reader.translate.download") {
                        translationService.downloadModels(
                            source = settings.source,
                            target = settings.target,
                            requireWifi = false,
                        )
                    }
                }
                // Failures here already notify and are not fatal: the bundled
                // engine translates the page either way, which is the whole
                // point of keeping it.
                if (betterMissing) fetchBetterModel(settings)
                appNotifications.add(AppNotification.Success("Translation model ready"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                appNotifications.add(AppNotification.Error("Model download failed: ${e.message}"))
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
        // Belt-and-braces: flush the latest progress to Komga before tearing
        // down. Without this, books read via random sort (or quickly past the
        // end in continuous mode) often stay "in progress" because the
        // CONFLATED progressUpdateChannel never drained its final value.
        if (markReadProgress && booksState.value != null) {
            logger.debug { "[ReadProgress] onDispose flush page=${readProgressPage.value} book=${booksState.value?.currentBook?.id?.value}" }
            finalFlushScope.launch {
                runCatching { updateCacheAndPush() }
                markCompletedIfOnLastPage()
            }
        }
        currentBookId.value = null
        previewLoadScope.cancel()
    }

    /**
     * Synchronously push the current read-progress to Komga and wait for the
     * server to acknowledge it before returning. Used by the in-reader
     * "return to series" / "return to library" exit buttons: those buttons
     * post a navigation intent that causes the SeriesScreen / LibraryScreen
     * to be pushed immediately on the inner navigator while the reader pops
     * on the outer one. The destination screen then fetches its data from
     * Komga; without an awaited push, the fetch races the async flush from
     * onDispose and frequently wins, showing stale progress.
     *
     * Safe to call multiple times — it's just an HTTP PUT with the current
     * page; idempotent on the server side.
     */
    suspend fun flushProgressNow() {
        if (!markReadProgress) return
        if (booksState.value == null) return
        runCatching { updateCacheAndPush() }
        // Awaited here on purpose: onDispose marks completion too, but
        // asynchronously, and the destination screen re-reads Komga first — so
        // leaving a finished volume through "return to series" / "return to
        // library" showed it as still in progress.
        markCompletedIfOnLastPage()
    }

    /**
     * Marks the current book completed when the reader is left ON its last page.
     *
     * Says it explicitly rather than letting the server infer completion from
     * the pushed progression. It is the only thing that covers a series' LAST
     * volume: the "moving on means done" rule lives in [loadNextBook], which
     * needs a next book to run, so finishing a last volume used to leave it
     * in progress on the Keep-reading shelf and the series never counted as
     * read. Idempotent, and a no-op anywhere but the last page.
     */
    private suspend fun markCompletedIfOnLastPage() {
        if (!markReadProgress) return
        val state = booksState.value ?: return
        val totalPages = state.currentBookPages.size
        logger.debug {
            "[ReadProgress] completion check page=${readProgressPage.value}/$totalPages " +
                "book=${state.currentBook.id.value}"
        }
        if (totalPages == 0 || readProgressPage.value < totalPages) return
        markCurrentBookCompleted()
    }

    /**
     * Marks the book being read completed, now.
     *
     * Called by the readers the moment the end-of-book page is reached, so the
     * server (and every screen listening to its events) knows the volume is
     * finished BEFORE the user leaves — instead of depending on which exit path
     * they take. Also called on exit as a safety net. Idempotent.
     */
    suspend fun markCurrentBookCompleted() {
        if (!markReadProgress) return
        val state = booksState.value ?: return
        val totalPages = state.currentBookPages.size
        logger.debug { "[ReadProgress] mark completed book=${state.currentBook.id.value} pages=$totalPages" }
        // Pin the page to the last one FIRST. Komga recomputes "completed" from
        // every progression push, and the reader's last reachable page is not
        // always the book's last page — a blank final page is skipped, and a
        // trailing page can be grouped into the previous spread. On a 276-page
        // volume the reader sits at 275, so the pushes that follow this mark
        // (the conflated channel draining, the onDispose flush) were sending
        // 0.996 and silently UN-completing the book a second after we marked it.
        if (totalPages > 0) readProgressPage.value = totalPages
        runCatching {
            bookApi.markReadProgress(
                state.currentBook.id,
                KomgaBookReadProgressUpdateRequest(completed = true),
            )
        }.onFailure { logger.warn(it) { "[ReadProgress] mark completed FAILED" } }
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
        // Capture book + page + total as ONE consistent snapshot BEFORE any
        // suspension. loadNextBook swaps booksState and resets the page to 1;
        // without this, the page/total re-read further down (after the repository
        // awaits below) could pair the OLD, just-finished book with the NEW
        // page=1 and push "page 1" onto it — silently wiping its progress, which
        // also un-completes it so getNextBook re-serves the same volume.
        val bookState = booksState.value ?: return
        val currentBook = bookState.currentBook
        val snapshotTotalPages = bookState.currentBookPages.size.coerceAtLeast(1)
        val snapshotPage = readProgressPage.value.coerceIn(1, snapshotTotalPages)
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
        val page = snapshotPage
        val r2Prog = R2Progression(
            modified = Clock.System.now(),
            device = R2Device("komelia-android", "Komelia"),
            locator = R2Locator(
                href = "p$page",
                type = "image/jpeg",
                locations = R2Location(
                    position = page,
                    progression = page.toFloat() / snapshotTotalPages
                ),
                koboSpan = encoded
            )
        )
        logger.debug {
            "[ReadProgress] push page=$page/$snapshotTotalPages " +
                "progression=${r2Prog.locator.locations?.progression} book=${currentBook.id.value}"
        }
        runCatching { bookApi.updateReadiumProgression(currentBook.id, r2Prog) }
            .onFailure {
                logger.warn(it) { "[ReadProgress] push FAILED page=$page book=${currentBook.id.value}" }
                appNotifications.runCatchingToNotifications { throw it }
            }
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

/**
 * Whether the better translation engine can be used for the pair on screen.
 *
 * Absent from the UI entirely when null — no model exists for that pair, so
 * there is nothing to offer and nothing to explain.
 */
sealed interface TranslationModelState {
    data object Missing : TranslationModelState
    data class Downloading(val completed: Long, val total: Long, val what: String) : TranslationModelState {
        val percent: Int get() = if (total > 0) (completed * 100 / total).toInt() else 0
    }

    data object Ready : TranslationModelState
}

data class BookState(
    val currentBook: KomeliaBook,
    val currentBookPages: List<PageMetadata>,
    val previousBook: KomeliaBook?,
    val previousBookPages: List<PageMetadata>,
    val nextBook: KomeliaBook?,
    val nextBookPages: List<PageMetadata>,
)
