package snd.komelia.ui.reader.image.panels

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.unit.toSize
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.reactivecircus.cache4k.Cache
import io.github.reactivecircus.cache4k.CacheEvent.Evicted
import io.github.reactivecircus.cache4k.CacheEvent.Expired
import io.github.reactivecircus.cache4k.CacheEvent.Removed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import snd.komelia.AppNotification
import snd.komelia.AppNotifications
import snd.komelia.image.BookImageLoader
import snd.komelia.image.EdgeSampling
import snd.komelia.image.ImageRect
import snd.komelia.image.KomeliaPanelDetector
import snd.komelia.image.ReaderImage.PageId
import snd.komelia.image.ReaderImageResult
import snd.komelia.image.getEdgeSampling
import snd.komelia.onnxruntime.OnnxRuntimeException
import snd.komelia.settings.ImageReaderSettingsRepository
import snd.komelia.settings.model.PagedReadingDirection
import snd.komelia.settings.model.PagedReadingDirection.LEFT_TO_RIGHT
import snd.komelia.settings.model.PagedReadingDirection.RIGHT_TO_LEFT
import snd.komelia.settings.model.PanelsFullPageDisplayMode
import snd.komelia.ui.reader.image.BookState
import snd.komelia.ui.reader.image.PageMetadata
import snd.komelia.ui.reader.image.ReaderState
import snd.komelia.ui.reader.image.ScreenScaleState
import snd.komelia.ui.reader.image.paged.PagedReaderState.PageNavigationEvent
import snd.komelia.ui.reader.image.paged.PagedReaderState.TransitionPage
import snd.komelia.ui.reader.image.paged.PagedReaderState.TransitionPage.BookEnd
import snd.komelia.ui.reader.image.paged.PagedReaderState.TransitionPage.BookStart
import snd.komelia.ui.strings.AppStrings
import snd.komga.client.common.KomgaReadingDirection
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.measureTimedValue

private val logger = KotlinLogging.logger { }

class PanelsReaderState(
    private val cleanupScope: CoroutineScope,
    private val settingsRepository: ImageReaderSettingsRepository,
    private val appNotifications: AppNotifications,
    val readerState: ReaderState,
    private val imageLoader: BookImageLoader,
    private val appStrings: Flow<AppStrings>,
    private val pageChangeFlow: MutableSharedFlow<Unit>,
    private val onnxRuntimeRfDetr: KomeliaPanelDetector,
    val screenScaleState: ScreenScaleState,
) {
    private val stateScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pageLoadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pageLoadJob: kotlinx.coroutines.Job? = null
    private val imageCache = Cache.Builder<PageId, Deferred<PanelsPage>>()
        .maximumCacheSize(10)
        .eventListener {
            val value = when (it) {
                is Evicted -> it.value
                is Expired -> it.value
                is Removed -> it.value
                else -> null
            } ?: return@eventListener

            cleanupScope.launch {
                if (value.isCancelled) return@launch
                value.await().imageResult?.image?.close()
            }
        }
        .build()

    val pageMetadata: MutableStateFlow<List<PageMetadata>> = MutableStateFlow(emptyList())

    val currentPageIndex = MutableStateFlow(PageIndex(0, 0))
    val currentPage: MutableStateFlow<PanelsPage?> = MutableStateFlow(null)
    val transitionPage: MutableStateFlow<TransitionPage?> = MutableStateFlow(null)
    val readingDirection = MutableStateFlow(LEFT_TO_RIGHT)

    val fullPageDisplayMode = MutableStateFlow(PanelsFullPageDisplayMode.NONE)
    val tapToZoom = MutableStateFlow(true)
    val adaptiveBackground = MutableStateFlow(false)

    val pageNavigationEvents = MutableSharedFlow<PageNavigationEvent>(extraBufferCapacity = 1)

    suspend fun initialize() {
        readingDirection.value = when (readerState.series.value?.metadata?.readingDirection) {
            KomgaReadingDirection.LEFT_TO_RIGHT -> LEFT_TO_RIGHT
            KomgaReadingDirection.RIGHT_TO_LEFT -> RIGHT_TO_LEFT
            else -> settingsRepository.getPagedReaderReadingDirection().first()
        }
        fullPageDisplayMode.value = settingsRepository.getPanelsFullPageDisplayMode().first()
        tapToZoom.value = settingsRepository.getPanelReaderTapToZoom().first()
        adaptiveBackground.value = settingsRepository.getPanelReaderAdaptiveBackground().first()

        screenScaleState.setScrollState(null)
        screenScaleState.setScrollOrientation(Orientation.Vertical, false)

        var lastAreaSize = screenScaleState.areaSize.value
        screenScaleState.areaSize
            .drop(1)
            .onEach { areaSize ->
                val page = currentPage.value ?: return@onEach
                val oldSize = lastAreaSize
                lastAreaSize = areaSize
                if (areaSize == IntSize.Zero || areaSize == oldSize) return@onEach
                
                // Small delay to allow Compose layout to finish centering the content 
                // before we calculate the required transformation
                delay(100)

                val panelIdx = currentPageIndex.value.panel
                val panelData = page.panelData
                val image = (page.imageResult as? ReaderImageResult.Success)?.image
                if (panelData != null && image != null) {
                    val stretchToFit = readerState.imageStretchToFit.value
                    val imageDisplaySize = image.calculateSizeForArea(areaSize, stretchToFit)
                    if (imageDisplaySize != null) {
                        screenScaleState.setTargetSize(imageDisplaySize.toSize())
                        val panel = panelData.panels.getOrNull(panelIdx) ?: panelData.panels.first()
                        scrollToPanel(
                            imageSize = panelData.originalImageSize,
                            screenSize = areaSize,
                            targetSize = imageDisplaySize,
                            panel = panel,
                            skipAnimation = false
                        )
                    }
                }
            }
            .launchIn(stateScope)

        screenScaleState.transformation
            .drop(1)
            .conflate()
            .onEach {
                currentPage.value?.let { page ->
                    updateImageState(page, screenScaleState, currentPageIndex.value.panel)
                }
            }
            .launchIn(stateScope)

        readingDirection.drop(1).onEach { 
            // Simple page reload to ensure correct panel sequence for new direction
            launchPageLoad(currentPageIndex.value.page)
        }.launchIn(stateScope)

        readerState.booksState
            .filterNotNull()
            .onEach { newBook -> onNewBookLoaded(newBook) }
            .launchIn(stateScope)

        val strings = appStrings.first().pagedReader
        appNotifications.add(AppNotification.Normal("Panels ${strings.forReadingDirection(readingDirection.value)}"))
    }

    fun stop() {
        stateScope.coroutineContext.cancelChildren()
        pageLoadScope.coroutineContext.cancelChildren()
        screenScaleState.enableOverscrollArea(false)
        imageCache.invalidateAll()
    }

    private var density = 1f
    fun setDensity(density: Float) {
        this.density = density
    }

    suspend fun getPage(page: PageMetadata): PanelsPage {
        val pageId = page.toPageId()
        val cached = imageCache.get(pageId)
        return if (cached != null && !cached.isCancelled) {
            cached.await()
        } else {
            val job = launchDownload(page)
            job.await()
        }
    }

    suspend fun getImage(page: PageMetadata): ReaderImageResult {
        val pageId = page.toPageId()
        val cached = imageCache.get(pageId)
        return if (cached != null && !cached.isCancelled) {
            cached.await().imageResult ?: ReaderImageResult.Error(Exception("Image result is null"))
        } else {
            val job = launchDownload(page)
            job.await().imageResult ?: ReaderImageResult.Error(Exception("Image result is null"))
        }
    }

    private suspend fun updateImageState(
        page: PanelsPage,
        screenScaleState: ScreenScaleState,
        panelIdx: Int
    ) {
        val maxPageSize = screenScaleState.areaSize.value
        val zoomFactor = screenScaleState.transformation.value.scale
        val offset = screenScaleState.transformation.value.offset
        val areaSize = screenScaleState.areaSize.value.toSize()
        val stretchToFit = readerState.imageStretchToFit.value


        if (page.imageResult is ReaderImageResult.Success) {
            val image = page.imageResult.image
            val imageDisplaySize = image.calculateSizeForArea(maxPageSize, stretchToFit) ?: return
            screenScaleState.setTargetSize(imageDisplaySize.toSize())

            val visibleHeight = (imageDisplaySize.height * zoomFactor - areaSize.height) / 2
            val visibleWidth = (imageDisplaySize.width * zoomFactor - areaSize.width) / 2

            val top = ((visibleHeight - offset.y) / zoomFactor).roundToInt()
                .coerceIn(0..imageDisplaySize.height)
            val left = ((visibleWidth - offset.x) / zoomFactor).roundToInt()
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
        }
    }

    private fun onNewBookLoaded(bookState: BookState) {
        val newPages = bookState.currentBookPages
        val newPageIndex = readerState.readProgressPage.value - 1

        pageMetadata.value = bookState.currentBookPages
        currentPage.value = PanelsPage(
            metadata = newPages[newPageIndex],
            imageResult = null,
            panelData = null
        )
        currentPageIndex.value = PageIndex(newPageIndex, 0)

        jumpToPage(newPageIndex)
    }

    fun onReadingDirectionChange(readingDirection: PagedReadingDirection) {
        this.readingDirection.value = readingDirection
        stateScope.launch { settingsRepository.putPagedReaderReadingDirection(readingDirection) }
    }

    fun onFullPageDisplayModeChange(mode: PanelsFullPageDisplayMode) {
        this.fullPageDisplayMode.value = mode
        stateScope.launch { settingsRepository.putPanelsFullPageDisplayMode(mode) }
        launchPageLoad(currentPageIndex.value.page)
    }

    fun onTapToZoomChange(enabled: Boolean) {
        this.tapToZoom.value = enabled
        stateScope.launch { settingsRepository.putPanelReaderTapToZoom(enabled) }
    }

    fun onAdaptiveBackgroundChange(enabled: Boolean) {
        this.adaptiveBackground.value = enabled
        stateScope.launch { settingsRepository.putPanelReaderAdaptiveBackground(enabled) }
    }

    fun nextPanel() {
        readerState.navigationHistory.dismissBackButton()
        val pageIndex = currentPageIndex.value
        val currentPage = currentPage.value
        if (currentPage == null || currentPage.panelData == null) {
            nextPage()
            return
        }
        val panels = currentPage.panelData.panels
        val panelIndex = pageIndex.panel

        if (panelIndex + 1 < panels.size) {
            val nextPanel = panels[panelIndex + 1]
            val areaSize = screenScaleState.areaSize.value
            val targetSize = screenScaleState.targetSize.value.toIntSize()
            val imageSize = currentPage.panelData.originalImageSize
            scrollToPanel(
                imageSize = imageSize,
                screenSize = areaSize,
                targetSize = targetSize,
                panel = nextPanel
            )
            currentPageIndex.update { it.copy(panel = panelIndex + 1) }
        } else {
            nextPage()
        }
    }

    private fun nextPage() {
        val pageIdx = currentPageIndex.value.page
        val currentTransitionPage = transitionPage.value
        when {
            pageIdx < pageMetadata.value.size - 1 -> {
                if (currentTransitionPage != null) this.transitionPage.value = null
                else onPageChange(pageIdx + 1)
            }

            currentTransitionPage == null -> {
                val bookState = readerState.booksState.value ?: return
                this.transitionPage.value = BookEnd(
                    currentBook = bookState.currentBook,
                    nextBook = bookState.nextBook
                )
            }

            currentTransitionPage is BookEnd && currentTransitionPage.nextBook != null -> {
                stateScope.launch {
                    currentPage.value = null
                    transitionPage.value = null
                    readerState.loadNextBook()
                }
            }
        }
    }

    fun previousPanel() {
        readerState.navigationHistory.dismissBackButton()
        val pageIndex = currentPageIndex.value
        val currentPage = currentPage.value
        if (currentPage == null || currentPage.panelData == null) {
            previousPage()
            return
        }
        val panels = currentPage.panelData.panels
        val panelIndex = pageIndex.panel

        if (panelIndex - 1 >= 0) {
            val prevPanel = panels[panelIndex - 1]
            val areaSize = screenScaleState.areaSize.value
            val targetSize = screenScaleState.targetSize.value.toIntSize()
            val imageSize = currentPage.panelData.originalImageSize
            scrollToPanel(
                imageSize = imageSize,
                screenSize = areaSize,
                targetSize = targetSize,
                panel = prevPanel
            )
            currentPageIndex.update { it.copy(panel = panelIndex - 1) }
        } else {
            previousPage()
        }
    }

    private fun previousPage() {
        val pageIdx = currentPageIndex.value.page
        val currentTransitionPage = transitionPage.value
        when {
            pageIdx != 0 -> {
                if (currentTransitionPage != null) this.transitionPage.value = null
                else onPageChange(pageIdx - 1, startAtLast = true)
            }

            currentTransitionPage == null -> {
                val bookState = readerState.booksState.value ?: return
                this.transitionPage.value = BookStart(
                    currentBook = bookState.currentBook,
                    previousBook = bookState.previousBook
                )
            }

            currentTransitionPage is BookStart && currentTransitionPage.previousBook != null -> {
                stateScope.launch {
                    currentPage.value = null
                    transitionPage.value = null
                    readerState.loadPreviousBook()
                }
            }
        }
    }

    fun jumpToPage(page: Int) {
        if (page !in pageMetadata.value.indices) return
        pageChangeFlow.tryEmit(Unit)
        val pageNumber = page + 1
        readerState.onProgressChange(pageNumber)
        currentPageIndex.update { it.copy(page = page) }
        pageNavigationEvents.tryEmit(PageNavigationEvent.Immediate(page))
        launchPageLoad(page)
    }

    fun onPageChange(page: Int, startAtLast: Boolean = false) {
        if (page !in pageMetadata.value.indices || currentPageIndex.value.page == page) return
        pageChangeFlow.tryEmit(Unit)
        pageNavigationEvents.tryEmit(PageNavigationEvent.Animated(page))
        launchPageLoad(page, startAtLast, isAnimated = true)
    }

    private fun launchPageLoad(pageIndex: Int, startAtLast: Boolean = false, isAnimated: Boolean = false) {
        if (pageIndex !in pageMetadata.value.indices) return
        if (pageIndex != currentPageIndex.value.page) {
            val pageNumber = pageIndex + 1
            readerState.onProgressChange(pageNumber)
        }

        pageLoadJob?.cancel()
        pageLoadJob = pageLoadScope.launch { doPageLoad(pageIndex, startAtLast, isAnimated) }
    }

    private suspend fun doPageLoad(pageIndex: Int, startAtLast: Boolean = false, isAnimated: Boolean = false) {
        currentCoroutineContext().ensureActive()
        if (pageIndex >= pageMetadata.value.size) return
        val pageMeta = pageMetadata.value[pageIndex]
        val downloadJob = launchDownload(pageMeta)
        preloadImagesBetween(pageIndex)

        if (downloadJob.isActive) {
            currentPage.update {
                it?.copy(
                    metadata = pageMeta,
                    imageResult = null,
                    panelData = null,
                    edgeSampling = null,
                    imageSize = null
                ) ?: PanelsPage(
                    metadata = pageMeta,
                    imageResult = null,
                    panelData = null,
                    edgeSampling = null,
                    imageSize = null
                )
            }
            currentPageIndex.update { PageIndex(pageIndex, 0) }
            transitionPage.value = null
            screenScaleState.enableOverscrollArea(false)
            screenScaleState.setZoom(0f, updateBase = true)
        }

        val page = downloadJob.await()
        val sortedPanels = if (page.panelData != null) {
            sortPanels(
                page.panelData.panels,
                page.panelData.originalImageSize,
                readingDirection.value
            )
        } else emptyList()

        val finalPanels = mutableListOf<ImageRect>()
        if (page.panelData != null) {
            val imageSize = page.panelData.originalImageSize
            val fullPageRect = ImageRect(0, 0, imageSize.width, imageSize.height)
            
            // Avoid duplicate view if the AI already detected a full-page panel
            val alreadyHasFullPage = sortedPanels.any { it.width >= imageSize.width * 0.95f && it.height >= imageSize.height * 0.95f }

            val mode = fullPageDisplayMode.value
            val showFirst = mode == PanelsFullPageDisplayMode.BEFORE || mode == PanelsFullPageDisplayMode.BOTH
            val showLast = mode == PanelsFullPageDisplayMode.AFTER || mode == PanelsFullPageDisplayMode.BOTH

            if (sortedPanels.isEmpty()) {
                finalPanels.add(fullPageRect)
            } else if (alreadyHasFullPage && sortedPanels.size == 1) {
                // If it's a splash page (1 large panel), just show it once.
                finalPanels.addAll(sortedPanels)
            } else {
                if (showFirst && !alreadyHasFullPage) finalPanels.add(fullPageRect)
                finalPanels.addAll(sortedPanels)
                if (showLast && !alreadyHasFullPage) finalPanels.add(fullPageRect)
            }
        }

        val pageWithInjectedPanels = if (page.panelData != null) {
            page.copy(panelData = page.panelData.copy(panels = finalPanels))
        } else page

        val containerSize = screenScaleState.areaSize.value
        val initialPanelIdx = if (startAtLast) (finalPanels.size - 1).coerceAtLeast(0) else 0
        val scale = getScaleFor(pageWithInjectedPanels, containerSize, initialPanelIdx)
        
        updateImageState(pageWithInjectedPanels, scale, initialPanelIdx)
        currentPageIndex.update { PageIndex(pageIndex, initialPanelIdx) }
        transitionPage.value = null
        currentPage.value = pageWithInjectedPanels
        screenScaleState.enableOverscrollArea(true)
        if (isAnimated) {
            screenScaleState.animateTo(scale.transformation.value.offset, scale.zoom.value)
        } else {
            screenScaleState.apply(scale)
        }
    }

    private fun preloadImagesBetween(pageIndex: Int) {
        val previousPage = (pageIndex - 1).coerceAtLeast(0)
        val nextPage = (pageIndex + 1).coerceAtMost(pageMetadata.value.size - 1)
        val loadRange = (previousPage..nextPage).filter { it != pageIndex }

        for (index in loadRange) {
            val imageJob = launchDownload(pageMetadata.value[index])
            pageLoadScope.launch {
                val image = imageJob.await()
                val scale = getScaleFor(image, screenScaleState.areaSize.value, 0)
                updateImageState(image, scale, 0)
            }
        }
    }

    private fun launchDownload(meta: PageMetadata): Deferred<PanelsPage> {
        val pageId = meta.toPageId()
        val cached = imageCache.get(pageId)
        if (cached != null && !cached.isCancelled) return cached

        val loadJob: Deferred<PanelsPage> = pageLoadScope.async {
            val imageResult = imageLoader.loadReaderImage(meta.bookId, meta.pageNumber)
            val image = imageResult.image ?: return@async PanelsPage(
                metadata = meta,
                imageResult = imageResult,
                panelData = null
            )

            val originalImage = image.getOriginalImage().getOrNull()
                ?: return@async PanelsPage(
                    metadata = meta,
                    imageResult = imageResult,
                    panelData = null
                )

                        val containerSize = screenScaleState.areaSize.value
                        val fitToScreenSize = image.calculateSizeForArea(containerSize, true)
                        val originalImageSize = IntSize(originalImage.width, originalImage.height)
                        val edgeSampling = if (adaptiveBackground.value) {
                            originalImage.getEdgeSampling()
                        } else null
            
                        val (panels, duration) = measureTimedValue {
            
                try {
                    onnxRuntimeRfDetr.detect(originalImage).map { it.boundingBox }
                } catch (e: OnnxRuntimeException) {
                    return@async PanelsPage(
                        metadata = meta,
                        imageResult = ReaderImageResult.Error(e),
                        panelData = null
                    )
                }
            }
            logger.info { "page ${meta.pageNumber} panel detection completed in $duration" }

            val panelData = PanelData(
                panels = panels,
                originalImageSize = originalImageSize,
                panelCoversMajorityOfImage = false // Placeholder for Phase 2
            )

            return@async PanelsPage(
                metadata = meta,
                imageResult = imageResult,
                panelData = panelData,
                edgeSampling = edgeSampling,
                imageSize = fitToScreenSize
            )
        }
        imageCache.put(pageId, loadJob)
        return loadJob
    }

    private suspend fun getScaleFor(
        page: PanelsPage,
        containerSize: IntSize,
        panelIdx: Int
    ): ScreenScaleState {
        val defaultScale = ScreenScaleState()
        defaultScale.setAreaSize(containerSize)
        defaultScale.setZoom(0f, updateBase = true)
        val image = page.imageResult?.image ?: return defaultScale

        val scaleState = ScreenScaleState()
        val fitToScreenSize = image.calculateSizeForArea(containerSize, true) ?: return defaultScale
        scaleState.setAreaSize(containerSize)
        scaleState.setTargetSize(fitToScreenSize.toSize())
        scaleState.enableOverscrollArea(true)

        val panels = page.panelData?.panels
        if (panels.isNullOrEmpty()) {
            scaleState.setZoom(0f, updateBase = true)
        } else {
            val targetPanel = panels.getOrNull(panelIdx) ?: panels.first()
            val imageSize = page.panelData.originalImageSize
            val (offset, zoom) = getPanelOffsetAndZoom(
                imageSize = imageSize,
                areaSize = containerSize,
                targetSize = fitToScreenSize,
                panel = targetPanel
            )
            scaleState.setZoom(zoom, updateBase = true)
            scaleState.setOffset(offset)
        }

        return scaleState
    }

    private fun scrollToFit() {
        screenScaleState.animateTo(Offset(0f, 0f), 0f)
    }

    private fun scrollToPanel(
        imageSize: IntSize,
        screenSize: IntSize,
        targetSize: IntSize,
        panel: ImageRect,
        skipAnimation: Boolean = false,
    ) {
        val (offset, zoom) = getPanelOffsetAndZoom(
            imageSize = imageSize,
            areaSize = screenSize,
            targetSize = targetSize,
            panel = panel
        )
        if (skipAnimation) {
            screenScaleState.setZoom(zoom, updateBase = true)
            screenScaleState.setOffset(offset)
        } else {
            screenScaleState.animateTo(offset, zoom)
        }
    }

    private fun getPanelOffsetAndZoom(
        imageSize: IntSize,
        areaSize: IntSize,
        targetSize: IntSize,
        panel: ImageRect,
    ): Pair<Offset, Float> {
        val xScale: Float = targetSize.width.toFloat() / imageSize.width
        val yScale: Float = targetSize.height.toFloat() / imageSize.height

        val panelCenterX = (panel.left + panel.width / 2f) * xScale
        val panelCenterY = (panel.top + panel.height / 2f) * yScale
        val imageCenterX = targetSize.width / 2f
        val imageCenterY = targetSize.height / 2f

        val bboxWidth = panel.width * xScale
        val bboxHeight = panel.height * yScale

        val totalScale: Float = min(
            areaSize.width / bboxWidth,
            areaSize.height / bboxHeight
        )
        val scaleFor100PercentZoom = max(
            areaSize.width.toFloat() / targetSize.width,
            areaSize.height.toFloat() / targetSize.height
        )
        val zoom: Float = totalScale / scaleFor100PercentZoom

        val offsetX = (imageCenterX - panelCenterX) * totalScale
        val offsetY = (imageCenterY - panelCenterY) * totalScale

        return Offset(offsetX, offsetY) to zoom
    }

    data class PanelsPage(
        val metadata: PageMetadata,
        val imageResult: ReaderImageResult?,
        val panelData: PanelData?,
        val edgeSampling: EdgeSampling? = null,
        val imageSize: IntSize? = null,
    )

    data class PanelData(
        val panels: List<ImageRect>,
        val originalImageSize: IntSize,
        val panelCoversMajorityOfImage: Boolean,
    )

    data class PageIndex(
        val page: Int,
        val panel: Int,
    )

}
