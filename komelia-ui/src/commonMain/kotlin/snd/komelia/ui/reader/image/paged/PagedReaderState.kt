package snd.komelia.ui.reader.image.paged

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import io.github.reactivecircus.cache4k.Cache
import io.github.reactivecircus.cache4k.CacheEvent.Evicted
import io.github.reactivecircus.cache4k.CacheEvent.Expired
import io.github.reactivecircus.cache4k.CacheEvent.Removed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import snd.komelia.AppNotification
import snd.komelia.AppNotifications
import snd.komelia.image.BookImageLoader
import snd.komelia.image.EdgeSampling
import snd.komelia.image.ReaderImage.PageId
import snd.komelia.image.ReaderImageResult
import snd.komelia.image.getEdgeSampling
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.settings.ImageReaderSettingsRepository
import snd.komelia.settings.model.LayoutScaleType
import snd.komelia.settings.model.PageDisplayLayout
import snd.komelia.settings.model.PageDisplayLayout.DOUBLE_PAGES
import snd.komelia.settings.model.PageDisplayLayout.DOUBLE_PAGES_NO_COVER
import snd.komelia.settings.model.PageDisplayLayout.SINGLE_PAGE
import snd.komelia.settings.model.PagedReadingDirection
import snd.komelia.settings.model.PagedReadingDirection.LEFT_TO_RIGHT
import snd.komelia.settings.model.PagedReadingDirection.RIGHT_TO_LEFT
import snd.komelia.ui.reader.image.BookState
import snd.komelia.ui.reader.image.PageHalf
import snd.komelia.ui.reader.image.PageMetadata
import snd.komelia.ui.reader.image.ReaderState
import snd.komelia.ui.reader.image.ScreenScaleState
import snd.komelia.ui.reader.image.SpreadIndex
import snd.komelia.ui.reader.image.paged.PagedReaderState.TransitionPage.BookEnd
import snd.komelia.ui.reader.image.paged.PagedReaderState.TransitionPage.BookStart
import snd.komelia.ui.strings.AppStrings
import snd.komga.client.common.KomgaReadingDirection
import kotlin.math.max
import kotlin.math.roundToInt

class PagedReaderState(
    private val cleanupScope: CoroutineScope,
    private val settingsRepository: ImageReaderSettingsRepository,
    private val appNotifications: AppNotifications,
    val readerState: ReaderState,
    private val imageLoader: BookImageLoader,
    private val appStrings: Flow<AppStrings>,
    private val pageChangeFlow: MutableSharedFlow<Unit>,
    val screenScaleState: ScreenScaleState,
    private val onBookChange: () -> Unit = {},
) {
    private val stateScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pageLoadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loadSpreadJob: kotlinx.coroutines.Job? = null

    private val imageCache = Cache.Builder<PageId, Deferred<Page>>()
        .maximumCacheSize(16)
        .eventListener {
            val value = when (it) {
                is Evicted -> it.value
                is Expired -> it.value
                is Removed -> it.value
                else -> null
            } ?: return@eventListener

            cleanupScope.launch { value.await().imageResult?.image?.close() }
        }
        .build()

    val pageSpreads = MutableStateFlow<List<List<PageMetadata>>>(emptyList())
    val currentSpreadIndex = MutableStateFlow(0)
    private var requestedSpreadIndex = 0
    val lastImageBounds = MutableStateFlow<Rect?>(null)
    val currentSpread: MutableStateFlow<PageSpread> = MutableStateFlow(PageSpread(emptyList()))
    val transitionPage: MutableStateFlow<TransitionPage?> = MutableStateFlow(null)

    val layout = MutableStateFlow(SINGLE_PAGE)
    val layoutOffset = MutableStateFlow(false)
    val scaleType = MutableStateFlow(LayoutScaleType.SCREEN)
    val readingDirection = MutableStateFlow(LEFT_TO_RIGHT)
    val tapToZoom = MutableStateFlow(true)
    val adaptiveBackground = MutableStateFlow(false)
    val splitDoublePages = MutableStateFlow(false)

    val pageNavigationEvents = MutableSharedFlow<PageNavigationEvent>(extraBufferCapacity = 1)

    suspend fun initialize() {
        layout.value = settingsRepository.getPagedReaderDisplayLayout().first()
        scaleType.value = settingsRepository.getPagedReaderScaleType().first()
        readingDirection.value = when (readerState.series.value?.metadata?.readingDirection) {
            KomgaReadingDirection.LEFT_TO_RIGHT -> LEFT_TO_RIGHT
            KomgaReadingDirection.RIGHT_TO_LEFT -> RIGHT_TO_LEFT
            else -> settingsRepository.getPagedReaderReadingDirection().first()
        }
        tapToZoom.value = settingsRepository.getPagedReaderTapToZoom().first()
        adaptiveBackground.value = settingsRepository.getPagedReaderAdaptiveBackground().first()
        splitDoublePages.value = settingsRepository.getPagedReaderSplitDoublePages().first()

        screenScaleState.setScrollState(null)
        screenScaleState.setScrollOrientation(Orientation.Vertical, false)
        screenScaleState.enableOverscrollArea(false)
        screenScaleState.edgeHandoffEnabled = true

        combine(
            screenScaleState.transformation,
            screenScaleState.areaSize,
            readerState.imageStretchToFit
        ) { }.drop(1)
            .conflate()
            .onEach {
                val spread = currentSpread.value
                updateSpreadImageState(
                    spread = spread,
                    screenScaleState = screenScaleState,
                    readingDirection = readingDirection.value,
                )
                val containerSize = screenScaleState.areaSize.value
                val maxPageSize = getMaxPageSize(spread.pages.map { it.metadata }, containerSize)
                val targetSize = fitToScreenZoom(spread.pages, maxPageSize, layout.value)
                screenScaleState.setTargetSize(targetSize.toSize())
                delay(100)
            }
            .launchIn(stateScope)

        readerState.booksState
            .filterNotNull()
            .onEach { newBook -> onNewBookLoaded(newBook) }
            .launchIn(stateScope)

        readerState.booksState
            .filterNotNull()
            .drop(1)
            .onEach { onBookChange() }
            .launchIn(stateScope)

        val strings = appStrings.first().pagedReader
        appNotifications.add(AppNotification.Normal("Paged ${strings.forReadingDirection(readingDirection.value)}"))
    }

    fun stop() {
        stateScope.coroutineContext.cancelChildren()
        pageLoadScope.coroutineContext.cancelChildren()
        screenScaleState.enableOverscrollArea(false)
        screenScaleState.edgeHandoffEnabled = false
        imageCache.invalidateAll()
    }

    private suspend fun updateSpreadImageState(
        spread: PageSpread,
        screenScaleState: ScreenScaleState,
        readingDirection: PagedReadingDirection,
    ) {
        val maxPageSize = getMaxPageSize(spread.pages.map { it.metadata }, screenScaleState.areaSize.value)
        val zoomFactor = screenScaleState.transformation.value.scale
        val offset = screenScaleState.transformation.value.offset
        val areaSize = screenScaleState.areaSize.value.toSize()
        val stretchToFit = readerState.imageStretchToFit.value


        val pages = spread.pages
        var xOffset = offset.x
        pages.forEachIndexed { index, result ->
            if (result.imageResult is ReaderImageResult.Success) {
                val image = result.imageResult.image
                val imageDisplaySize = image.calculateSizeForArea(maxPageSize, stretchToFit)?:maxPageSize

                val imageHorizontalVisibleWidth =
                    (imageDisplaySize.width * zoomFactor - areaSize.width) / 2

                val imageHorizontalDisplayOffset = when (pages.size) {
                    1 -> offset.x
                    2 -> {
                        //TODO simplify
                        when (readingDirection) {
                            LEFT_TO_RIGHT -> {
                                if (index == 0) offset.x - (imageDisplaySize.width / 2 * zoomFactor)
                                else offset.x + (imageDisplaySize.width / 2 * zoomFactor)
                            }

                            RIGHT_TO_LEFT -> {
                                if (index == 0) offset.x + (imageDisplaySize.width / 2 * zoomFactor)
                                else offset.x - (imageDisplaySize.width / 2 * zoomFactor)
                            }
                        }
                    }

                    else -> throw IllegalStateException("can't display more than 2 images")   //TODO 3 or more images?
                }

                val top =
                    ((((imageDisplaySize.height * zoomFactor - areaSize.height) / 2) - offset.y) / zoomFactor)
                        .roundToInt()
                        .coerceIn(0..imageDisplaySize.height)

                val left =
                    ((imageHorizontalVisibleWidth - imageHorizontalDisplayOffset) / zoomFactor)
                        .roundToInt()
                        .coerceIn(0..imageDisplaySize.width)

                val visibleArea = IntRect(
                    top = top,
                    left = left,
                    bottom = (top + areaSize.height / zoomFactor)
                        .roundToInt()
                        .coerceAtMost(imageDisplaySize.height),
                    right = (left + (areaSize.width) / zoomFactor)
                        .roundToInt()
                        .coerceAtMost(imageDisplaySize.width),
                )

                image.requestUpdate(
                    visibleDisplaySize = visibleArea,
                    zoomFactor = zoomFactor,
                    maxDisplaySize = maxPageSize
                )
                xOffset += maxPageSize.width

            }
        }
    }

    private fun onNewBookLoaded(bookState: BookState) {
        val pageSpreads = buildSpreadMap(bookState.currentBookPages, layout.value)
        this.pageSpreads.value = pageSpreads

        val newSpreadIndex = pageSpreads.indexOfFirst { spread ->
            spread.any { it.pageNumber == readerState.readProgressPage.value }
        }.coerceAtLeast(0)

        currentSpread.value = PageSpread(
            pages = pageSpreads[newSpreadIndex].map { Page(it, null) },
        )
        currentSpreadIndex.value = newSpreadIndex
        requestedSpreadIndex = newSpreadIndex

        jumpToPage(newSpreadIndex)
    }

    fun nextPage() {
        readerState.navigationHistory.dismissBackButton()
        val spreads = pageSpreads.value
        if (spreads.isEmpty()) return

        val currentSpreadIndex = requestedSpreadIndex.coerceIn(spreads.indices)
        val lastSpreadIndex = spreads.lastIndex
        val currentTransitionPage = transitionPage.value

        when {
            currentSpreadIndex < lastSpreadIndex -> {
                loadPage(currentSpreadIndex + 1)
            }

            currentTransitionPage == null -> {
                requestedSpreadIndex = lastSpreadIndex
                loadSpreadJob?.cancel()
                val bookState = readerState.booksState.value ?: return
                this.transitionPage.value = BookEnd(
                    currentBook = bookState.currentBook,
                    nextBook = bookState.nextBook
                )
            }

            currentTransitionPage is BookEnd && currentTransitionPage.nextBook != null -> {
                stateScope.launch {
                    currentSpread.value = PageSpread(emptyList())
                    transitionPage.value = null
                    readerState.loadNextBook()
                }
            }
        }
    }

    fun previousPage() {
        readerState.navigationHistory.dismissBackButton()
        val spreads = pageSpreads.value
        if (spreads.isEmpty()) return

        val currentSpreadIndex = requestedSpreadIndex.coerceIn(spreads.indices)
        val currentTransitionPage = transitionPage.value

        when {
            currentSpreadIndex != 0 -> {
                loadPage(currentSpreadIndex - 1)
            }

            currentTransitionPage == null -> {
                requestedSpreadIndex = 0
                loadSpreadJob?.cancel()
                val bookState = readerState.booksState.value ?: return
                this.transitionPage.value = BookStart(
                    currentBook = bookState.currentBook,
                    previousBook = bookState.previousBook
                )
            }

            currentTransitionPage is BookStart && currentTransitionPage.previousBook != null -> {
                stateScope.launch {
                    currentSpread.value = PageSpread(emptyList())
                    transitionPage.value = null
                    readerState.loadPreviousBook()
                }
            }
        }
    }

    fun onPageChange(page: Int) {
        if (page in pageSpreads.value.indices) requestedSpreadIndex = page
        if (currentSpreadIndex.value == page) return
        pageChangeFlow.tryEmit(Unit)
        loadPage(page)
    }

    fun moveToLastPage() {
        val lastPageIndex = pageSpreads.value.size - 1
        if (lastPageIndex < 0) return
        requestedSpreadIndex = lastPageIndex
        if (currentSpreadIndex.value == lastPageIndex) return
        pageChangeFlow.tryEmit(Unit)
        loadPage(lastPageIndex)
    }

    fun jumpToPage(page: Int) {
        val spreads = pageSpreads.value
        if (page !in spreads.indices) return
        requestedSpreadIndex = page
        transitionPage.value = null
        pageChangeFlow.tryEmit(Unit)
        val pageNumber = spreads[page].last().pageNumber
        readerState.onProgressChange(pageNumber)
        currentSpreadIndex.value = page
        pageNavigationEvents.tryEmit(PageNavigationEvent.Immediate(page))

        loadSpreadJob?.cancel()
        loadSpreadJob = pageLoadScope.launch { loadSpread(page) }
    }

    private fun loadPage(spreadIndex: Int) {
        val spreads = pageSpreads.value
        if (spreadIndex !in spreads.indices) return
        requestedSpreadIndex = spreadIndex

        transitionPage.value = null
        if (spreadIndex != currentSpreadIndex.value) {
            val pageNumber = spreads[spreadIndex].last().pageNumber
            readerState.onProgressChange(pageNumber)
            currentSpreadIndex.value = spreadIndex
            pageNavigationEvents.tryEmit(PageNavigationEvent.Animated(spreadIndex))
        }

        loadSpreadJob?.cancel()
        loadSpreadJob = pageLoadScope.launch { loadSpread(spreadIndex) }
    }

    sealed interface PageNavigationEvent {
        val pageIndex: Int
        data class Animated(override val pageIndex: Int) : PageNavigationEvent
        data class Immediate(override val pageIndex: Int) : PageNavigationEvent
    }

    private suspend fun loadSpread(loadSpreadIndex: Int) {
        val loadRange = getSpreadLoadRange(loadSpreadIndex)
        val currentSpreadMetadata = pageSpreads.value[loadSpreadIndex]
        val currentSpreadJob = launchSpreadLoadJob(currentSpreadMetadata)

        loadRange.filter { it != loadSpreadIndex }.forEach { spreadIndex ->
            enqueueSpreadLoadJob(pageSpreads.value[spreadIndex])
        }

        if (currentSpreadJob.isActive) {
            delay(10)
            if (currentSpreadJob.isActive) {
                currentSpread.value = PageSpread(currentSpreadMetadata.map { Page(it, null) })
                currentSpreadIndex.value = loadSpreadIndex
            }
        }

        val completedPagesJob = currentSpreadJob.await()
        currentSpread.value = completedPagesJob.spread
        currentSpreadIndex.value = loadSpreadIndex

        val newScale = completedPagesJob.scale
        screenScaleState.apply(newScale)
    }

    data class PagesLoadJob(
        val spread: PageSpread,
        val scale: ScreenScaleState
    )

    private fun launchSpreadLoadJob(pagesMeta: List<PageMetadata>): Deferred<PagesLoadJob> {
        val pages = pagesMeta.map { meta ->
            val pageId = meta.toPageId()
            val cached = imageCache.get(pageId)

            if (cached != null && !cached.isCancelled) cached
            else pageLoadScope.async {
                val imageResult = imageLoader.loadReaderImage(meta.bookId, meta.pageNumber)
                val containerSize = screenScaleState.areaSize.value
                val imageSize = if (imageResult is ReaderImageResult.Success) {
                    imageResult.image.calculateSizeForArea(containerSize, true)
                } else null

                val edgeSampling = if (adaptiveBackground.value && imageResult is ReaderImageResult.Success) {
                    val originalImage = imageResult.image.getOriginalImage().getOrNull()
                    if (originalImage != null) {
                        originalImage.getEdgeSampling()
                    } else null
                } else null
                Page(meta, imageResult, edgeSampling, imageSize)
            }.also { imageCache.put(pageId, it) }
        }

        return pageLoadScope.async {
            val completed = pages.awaitAll()
            completeLoadJob(completed)
        }
    }

    private suspend fun completeLoadJob(pages: List<Page>): PagesLoadJob {
        val containerSize = screenScaleState.areaSize.value
        val maxPageSize = getMaxPageSize(pages.map { it.metadata }, containerSize)
        val newScale = calculateScreenScale(
            pages,
            areaSize = containerSize,
            maxPageSize = maxPageSize,
            scaleType = scaleType.value,
            displayLayout = layout.value,
            stretchToFit = readerState.imageStretchToFit.value
        )
        val spread = PageSpread(pages)
        when (readingDirection.value) {
            LEFT_TO_RIGHT -> newScale.addPan(
                Offset(
                    newScale.offsetXLimits.value.endInclusive,
                    newScale.offsetYLimits.value.endInclusive
                )
            )

            RIGHT_TO_LEFT -> newScale.addPan(
                Offset(
                    newScale.offsetXLimits.value.start,
                    newScale.offsetYLimits.value.endInclusive
                )
            )
        }
        updateSpreadImageState(spread, newScale, readingDirection.value)

        return PagesLoadJob(spread, newScale)

    }

    @Suppress("DeferredResultUnused")
    private fun enqueueSpreadLoadJob(pagesMeta: List<PageMetadata>) {
        launchSpreadLoadJob(pagesMeta)
    }

    suspend fun getPage(page: PageMetadata): Page {
        val pageId = page.toPageId()
        val cached = imageCache.get(pageId)
        return if (cached != null && !cached.isCancelled) {
            cached.await()
        } else {
            val job = pageLoadScope.async {
                val imageResult = imageLoader.loadReaderImage(page.bookId, page.pageNumber)
                val containerSize = screenScaleState.areaSize.value
                val imageSize = if (imageResult is ReaderImageResult.Success) {
                    imageResult.image.calculateSizeForArea(containerSize, true)
                } else null

                val edgeSampling = if (adaptiveBackground.value && imageResult is ReaderImageResult.Success) {
                    val originalImage = imageResult.image.getOriginalImage().getOrNull()
                    if (originalImage != null) {
                        originalImage.getEdgeSampling()
                    } else null
                } else null
                Page(page, imageResult, edgeSampling, imageSize)
            }.also { imageCache.put(pageId, it) }
            job.await()
        }
    }

    private fun getMaxPageSize(pages: List<PageMetadata>, containerSize: IntSize): IntSize {
        return IntSize(
            width = containerSize.width / pages.size,
            height = containerSize.height
        )
    }

    private fun buildSpreadMap(pages: List<PageMetadata>, layout: PageDisplayLayout): List<List<PageMetadata>> {
        return when (layout) {
            SINGLE_PAGE -> {
                if (splitDoublePages.value) pages.flatMap { splitLandscapePage(it) }.map { listOf(it) }
                else pages.map { listOf(it) }
            }
            DOUBLE_PAGES -> buildSpreadMapForDoublePages(
                pages = pages,
                withCover = true,
                offset = layoutOffset.value
            )

            DOUBLE_PAGES_NO_COVER -> buildSpreadMapForDoublePages(
                pages = pages,
                withCover = false,
                offset = layoutOffset.value
            )
        }
    }

    /**
     * If the page is landscape (wider than tall), return two synthetic
     * PageMetadata for its LEFT and RIGHT halves, ordered by reading
     * direction. Otherwise return the page unchanged.
     */
    private fun splitLandscapePage(page: PageMetadata): List<PageMetadata> {
        if (!page.isLandscape()) return listOf(page)
        val left = page.copy(half = PageHalf.LEFT)
        val right = page.copy(half = PageHalf.RIGHT)
        return when (readingDirection.value) {
            LEFT_TO_RIGHT -> listOf(left, right)
            RIGHT_TO_LEFT -> listOf(right, left)
        }
    }

    data class DoublePageSegment(
        val singlePages: List<PageMetadata>,
        val landscapePage: PageMetadata?
    )

    private fun buildSpreadMapForDoublePages(
        pages: List<PageMetadata>,
        withCover: Boolean,
        offset: Boolean
    ): List<List<PageMetadata>> {
        val segments = constructDoublePageSegments(pages, withCover)

        val processedSpreads = mutableListOf<List<PageMetadata>>()
        for (segment in segments) {
            val segmentPages = segment.singlePages

            if (offset) {
                segmentPages.firstOrNull()?.let { processedSpreads.add(listOf(it)) }
                processedSpreads.addAll(segmentPages.drop(1).chunked(2))
            } else processedSpreads.addAll(segmentPages.chunked(2))

            segment.landscapePage?.let { processedSpreads.add(listOf(it)) }

        }

        return processedSpreads
    }

    private fun constructDoublePageSegments(
        pages: List<PageMetadata>,
        withCover: Boolean,
    ): List<DoublePageSegment> {
        val independentSegments = mutableListOf<DoublePageSegment>()

        val pagesToProcess = if (withCover) {
            independentSegments.add(DoublePageSegment(listOf(pages.first()), null))
            pages.drop(1)
        } else pages

        var segmentPages: MutableList<PageMetadata> = mutableListOf()
        for (page in pagesToProcess) {
            if (page.isLandscape()) {
                independentSegments.add(DoublePageSegment(segmentPages, page))
                segmentPages = mutableListOf()
            } else {
                segmentPages.add(page)
            }
        }
        if (segmentPages.isNotEmpty()) independentSegments.add(DoublePageSegment(segmentPages, null))

        return independentSegments
    }

    private fun spreadIndexOf(page: PageMetadata): SpreadIndex {
        return pageSpreads.value.indexOfFirst { spread ->
            spread.any { it.pageNumber == page.pageNumber }
        }
    }

    private fun getSpreadLoadRange(spreadIndex: Int): IntRange {
        // Direction-agnostic: spreadIndex is the reading-order index, so +N means
        // "N spreads ahead in reading order" regardless of LTR/RTL. The visual
        // mapping (which side of the screen the next page lands on) is handled
        // at the layout/gesture level.
        // Prefetch 3 spreads ahead so fast page-flipping stays smooth, plus 1
        // behind so a quick back-nav still hits the cache.
        val spreads = pageSpreads.value
        return (spreadIndex - 1).coerceAtLeast(0)..(spreadIndex + 3).coerceAtMost(spreads.size - 1)
    }

    fun onLayoutChange(layout: PageDisplayLayout) {
        this.layout.value = layout
        stateScope.launch { settingsRepository.putPagedReaderDisplayLayout(layout) }

        val pages = readerState.booksState.value?.currentBookPages ?: return
        pageSpreads.value = buildSpreadMap(pages, layout)

        val currentPage = currentSpread.value.pages.first().metadata
        loadPage(spreadIndexOf(currentPage))
    }

    fun onLayoutCycle() {
        val options = PageDisplayLayout.entries
        val newLayout = options[(layout.value.ordinal + 1) % options.size]
        onLayoutChange(newLayout)
    }

    fun onLayoutOffsetChange(offset: Boolean) {
        val currentLayout = layout.value
        if (currentLayout == SINGLE_PAGE) return
        this.layoutOffset.value = offset

        val pages = readerState.booksState.value?.currentBookPages ?: return
        pageSpreads.value = buildSpreadMap(pages, currentLayout)

        val currentPage = currentSpread.value.pages.first().metadata
        loadPage(spreadIndexOf(currentPage))
    }

    fun onScaleTypeChange(scale: LayoutScaleType) {
        this.scaleType.value = scale
        val currentPage = currentSpread.value.pages.first().metadata
        loadPage(spreadIndexOf(currentPage))
        stateScope.launch { settingsRepository.putPagedReaderScaleType(scale) }
    }

    fun onScaleTypeCycle() {
        val options = LayoutScaleType.entries
        val newScale = options[(scaleType.value.ordinal + 1) % options.size]
        onScaleTypeChange(newScale)
    }

    fun onReadingDirectionChange(readingDirection: PagedReadingDirection) {
        this.readingDirection.value = readingDirection
        screenScaleState.setScrollOrientation(Orientation.Horizontal, readingDirection == RIGHT_TO_LEFT)
        stateScope.launch { settingsRepository.putPagedReaderReadingDirection(readingDirection) }

        // If split landscape pages is on, the LEFT/RIGHT order depends on
        // reading direction, so the spread map must be rebuilt.
        if (splitDoublePages.value && layout.value == SINGLE_PAGE) {
            rebuildSpreadsKeepingPosition()
        }
    }

    fun onTapToZoomChange(enabled: Boolean) {
        this.tapToZoom.value = enabled
        stateScope.launch { settingsRepository.putPagedReaderTapToZoom(enabled) }
    }

    fun onAdaptiveBackgroundChange(enabled: Boolean) {
        this.adaptiveBackground.value = enabled
        stateScope.launch { settingsRepository.putPagedReaderAdaptiveBackground(enabled) }
    }

    fun onSplitDoublePagesChange(enabled: Boolean) {
        this.splitDoublePages.value = enabled
        stateScope.launch { settingsRepository.putPagedReaderSplitDoublePages(enabled) }
        if (layout.value == SINGLE_PAGE) {
            rebuildSpreadsKeepingPosition()
        }
    }

    /**
     * Rebuilds [pageSpreads] from the current book pages using the active
     * layout, then re-anchors the reader on the spread containing the
     * current page (matching by pageNumber, ignoring split half).
     */
    private fun rebuildSpreadsKeepingPosition() {
        val pages = readerState.booksState.value?.currentBookPages ?: return
        pageSpreads.value = buildSpreadMap(pages, layout.value)
        val currentPageNumber = currentSpread.value.pages.firstOrNull()?.metadata?.pageNumber ?: return
        val newIndex = pageSpreads.value.indexOfFirst { spread ->
            spread.any { it.pageNumber == currentPageNumber }
        }.coerceAtLeast(0)
        loadPage(newIndex)
    }

    fun getCurrentPageNumber(): Int {
        val spreads = pageSpreads.value
        val idx = currentSpreadIndex.value
        return spreads.getOrNull(idx)?.firstOrNull()?.pageNumber ?: 0
    }

    private suspend fun calculateScreenScale(
        pages: List<Page>,
        areaSize: IntSize,
        maxPageSize: IntSize,
        scaleType: LayoutScaleType,
        displayLayout: PageDisplayLayout,
        stretchToFit: Boolean,
    ): ScreenScaleState {
        val scaleState = ScreenScaleState()
        scaleState.setAreaSize(areaSize)

        val fitToScreenSize = fitToScreenZoom(pages, maxPageSize, displayLayout)
        scaleState.setTargetSize(fitToScreenSize.toSize())

        val actualSpreadSize = pages.map {
            when (val result = it.imageResult) {
                is ReaderImageResult.Error, null -> maxPageSize
                is ReaderImageResult.Success -> result.image.getOriginalImageSize().getOrDefault(maxPageSize)
            }
        }.fold(IntSize.Zero) { total, current ->
            IntSize(
                (total.width + current.width),
                max(total.height, current.height)
            )
        }

        when (scaleType) {
            LayoutScaleType.SCREEN -> scaleState.setZoom(0f, updateBase = true)
            LayoutScaleType.FIT_WIDTH -> {
                if (!stretchToFit && areaSize.width > actualSpreadSize.width) {
                    val newZoom = zoomForOriginalSize(
                        actualSpreadSize,
                        fitToScreenSize,
                        scaleState.scaleFor100PercentZoom()
                    )
                    scaleState.setZoom(newZoom.coerceAtMost(1.0f), updateBase = true)
                } else if (fitToScreenSize.width < areaSize.width) scaleState.setZoom(1f, updateBase = true)
                else scaleState.setZoom(0f, updateBase = true)
            }

            LayoutScaleType.FIT_HEIGHT -> {
                if (!stretchToFit && areaSize.height > actualSpreadSize.height) {
                    val newZoom = zoomForOriginalSize(
                        actualSpreadSize,
                        fitToScreenSize,
                        scaleState.scaleFor100PercentZoom()
                    )
                    scaleState.setZoom(newZoom.coerceAtMost(1.0f), updateBase = true)

                } else if (fitToScreenSize.height < areaSize.height) scaleState.setZoom(1f, updateBase = true)
                else scaleState.setZoom(0f, updateBase = true)
            }

            LayoutScaleType.ORIGINAL -> {
                if (actualSpreadSize.width > areaSize.width || actualSpreadSize.height > areaSize.height) {
                    val newZoom = zoomForOriginalSize(
                        actualSpreadSize,
                        fitToScreenSize,
                        scaleState.scaleFor100PercentZoom()
                    )
                    scaleState.setZoom(newZoom, updateBase = true)

                } else scaleState.setZoom(0f, updateBase = true)
            }
        }

        return scaleState
    }

    private suspend fun fitToScreenZoom(
        pages: List<Page>,
        maxPageSize: IntSize,
        displayLayout: PageDisplayLayout,
    ): IntSize {
        return when (displayLayout) {
            SINGLE_PAGE -> {
                check(pages.size == 1)
                val imageResult = pages.first().imageResult
                when (imageResult) {
                    is ReaderImageResult.Error, null -> maxPageSize
                    is ReaderImageResult.Success ->
                        imageResult.image.calculateSizeForArea(maxPageSize, true) ?: maxPageSize
                }
            }

            DOUBLE_PAGES, DOUBLE_PAGES_NO_COVER -> {
                if (pages.size == 1 && !pages.first().metadata.isLandscape()) {
                    val imageResult = pages.first().imageResult
                    val singlePageSize = when (imageResult) {
                        is ReaderImageResult.Error, null -> maxPageSize
                        is ReaderImageResult.Success ->
                            imageResult.image.calculateSizeForArea(maxPageSize, true) ?: maxPageSize
                    }
                    IntSize(singlePageSize.width * 2, singlePageSize.height)
                } else {
                    pages
                        .map {
                            when (it.imageResult) {
                                is ReaderImageResult.Error, null -> maxPageSize
                                is ReaderImageResult.Success ->
                                    it.imageResult.image.calculateSizeForArea(maxPageSize, true) ?: maxPageSize

                            }
                        }
                        .reduce { total, current ->
                            IntSize(
                                width = (total.width + current.width),
                                height = max(total.height, current.height)
                            )
                        }
                }
            }
        }

    }

    private fun zoomForOriginalSize(originalSize: IntSize, targetSize: IntSize, scaleFor100Percent: Float): Float {
        return max(
            originalSize.width.toFloat() / targetSize.width,
            originalSize.height.toFloat() / targetSize.height
        ) / scaleFor100Percent
    }


    data class PageSpread(val pages: List<Page>)

    data class Page(
        val metadata: PageMetadata,
        val imageResult: ReaderImageResult?,
        val edgeSampling: EdgeSampling? = null,
        val imageSize: IntSize? = null,
    )


    sealed interface TransitionPage {
        data class BookEnd(
            val currentBook: KomeliaBook,
            val nextBook: KomeliaBook?,
        ) : TransitionPage

        data class BookStart(
            val currentBook: KomeliaBook,
            val previousBook: KomeliaBook?,
        ) : TransitionPage
    }
}
