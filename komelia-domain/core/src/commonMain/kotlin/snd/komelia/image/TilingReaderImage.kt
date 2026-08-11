package snd.komelia.image

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toRect
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import snd.komelia.image.processing.ImageProcessingPipeline
import kotlin.concurrent.Volatile
import kotlin.math.ceil
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.time.TimeSource
import kotlin.time.measureTime

private const val tileThreshold1 = 2048 * 2048
private const val tileThreshold2 = 4096 * 4096
private const val tileThreshold3 = 6144 * 6144

/** Quarter steps: the decode zoom snaps to 1.25, 1.5, 1.75, 2… See quantizeDecodeZoom. */
private const val DECODE_ZOOM_STEPS = 4f


expect class RenderImage

private val logger = KotlinLogging.logger {}

abstract class TilingReaderImage(
    protected val imageSource: ImageSource,
    private val imageDecoder: KomeliaImageDecoder,
    protected val processingPipeline: ImageProcessingPipeline,
    private val stretchImages: StateFlow<Boolean>,
    protected val upsamplingMode: StateFlow<UpsamplingMode>,
    protected val downSamplingKernel: StateFlow<ReduceKernel>,
    protected val linearLightDownSampling: StateFlow<Boolean>,
    final override val pageId: ReaderImage.PageId
) : ReaderImage {
    final override val painter = MutableStateFlow<TiledPainter?>(null)
    final override val error = MutableStateFlow<Throwable?>(null)
    override val upscaleStatus: StateFlow<UpscaleStatus> = MutableStateFlow(UpscaleStatus.Idle)

    final override val originalSize = MutableStateFlow<IntSize?>(null)
    final override val displaySize = MutableStateFlow<IntSize?>(null)
    final override val currentSize = MutableStateFlow<IntSize?>(null)

    private val imageAwaitScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val animationScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())
    protected val processingScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())

    private val jobFlow = MutableSharedFlow<UpdateRequest>(1, 0, BufferOverflow.DROP_OLDEST)
    private val frameData = MutableStateFlow<FrameData?>(null)
    protected val image = MutableStateFlow<KomeliaImage?>(null)
    protected val defaultFrameDelay = 100L

    @Volatile
    protected var originalImage: KomeliaImage? = null

    @Volatile
    protected var lastUpdateRequest: UpdateRequest? = null

    @Volatile
    protected var lastUsedScaleFactor: Double? = null

    private var pendingTilesToClose: List<ReaderImageTile> = emptyList()

    data class UpdateRequest(
        val visibleDisplaySize: IntRect,
        val zoomFactor: Float,
        val maxDisplaySize: IntSize,
    )

    init {
        jobFlow.conflate()
            .onEach { request ->
                try {
                    doUpdate(request)
                    this.error.value = null
                } catch (e: Throwable) {
                    currentCoroutineContext().ensureActive()
                    logger.catching(e)
                    this.error.value = e
                }

                delay(50)
            }.launchIn(processingScope)

        processingPipeline.changeFlow.onEach {
            val currentImage = image.value
            if (currentImage !== originalImage) {
                currentImage?.close()
            }

            image.value = null
            originalSize.value = originalImage?.let { IntSize(it.width, it.pageHeight) }
            currentSize.value = null
            loadImage()
            reloadLastRequest()
        }.launchIn(processingScope)

        stretchImages.drop(1).onEach { reloadLastRequest() }.launchIn(processingScope)
        upsamplingMode.onEach { mode -> painter.update { it?.withSamplingMode(mode) } }
            .launchIn(processingScope)
        downSamplingKernel.drop(1).onEach { reloadLastRequest() }
            .launchIn(processingScope)
        linearLightDownSampling.drop(1).onEach { reloadLastRequest() }
            .launchIn(processingScope)

        frameData.onEach { data ->
            val tilesToClose = pendingTilesToClose
            pendingTilesToClose = emptyList()
            when {
                data == null -> this.painter.value = null
                data.frames.size == 1 -> {
                    this.painter.value = createTilePainter(
                        tiles = data.frames.first().tiles,
                        displaySize = data.displaySize,
                        scaleFactor = data.scaleFactor
                    )
                }

                else -> launchAnimation(data)
            }
            closeTileBitmaps(tilesToClose)
        }.launchIn(processingScope)
    }

    private fun launchAnimation(data: FrameData) {
        animationScope.coroutineContext.cancelChildren()
        animationScope.launch {
            val painters = data.frames.map {
                createTilePainter(
                    tiles = it.tiles,
                    displaySize = data.displaySize,
                    scaleFactor = data.scaleFactor
                )
            }

            while (isActive) {
                for ((index, tiledPainter) in painters.withIndex()) {
                    val frameDelay = data.frames[index].delay
                    this@TilingReaderImage.painter.value = tiledPainter
                    delay(if (frameDelay < 10) defaultFrameDelay else frameDelay)
                }
            }
        }
    }

    protected suspend fun reloadLastRequest() {
        lastUpdateRequest?.let { lastRequest ->
            lastUsedScaleFactor = null
            jobFlow.emit(lastRequest)
        }
    }

    override suspend fun getOriginalImageSize(): Result<IntSize> {
        return coroutineScope {
            select {
                async { originalSize.filterNotNull().first() }.onAwait { Result.success(it) }
                async { error.filterNotNull().first() }.onAwait { Result.failure(it) }
            }.also { coroutineContext.cancelChildren() }
        }
    }

    override suspend fun getOriginalImage(): Result<KomeliaImage> {
        return coroutineScope {
            select {
                async { image.filterNotNull().first() }.onAwait { Result.success(it) }
                async { error.filterNotNull().first() }.onAwait { Result.failure(it) }
            }.also { coroutineContext.cancelChildren() }
        }
    }

    override suspend fun getUnprocessedImage(): Result<KomeliaImage> {
        return coroutineScope {
            select {
                // Awaiting `image` also guarantees loadImage() has run, and it
                // assigns originalImage BEFORE the pipeline — so by the time the
                // processed image exists, the raw one is set. It stays alive
                // until close() (the changeFlow handler explicitly avoids
                // closing it), so handing it out is safe.
                async { image.filterNotNull().first() }.onAwait {
                    Result.success(originalImage ?: it)
                }
                async { error.filterNotNull().first() }.onAwait { Result.failure(it) }
            }.also { coroutineContext.cancelChildren() }
        }
    }

    protected fun startImageLoading() {
        processingScope.launch { loadImage() }
    }

    protected open suspend fun loadImage() {
        try {
            val originalImage = decodeImage(imageSource)
            this.originalImage = originalImage
            val processed = processingPipeline.process(pageId, originalImage)
            image.value = processed
            originalSize.value = IntSize(processed.width, processed.pageHeight)
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive()
            logger.catching(e)
            this.error.value = e
            imageAwaitScope.coroutineContext.cancelChildren()
        }
    }

    protected suspend fun decodeImage(source: ImageSource): KomeliaImage {
        val image = when (source) {
            is ImageSource.FilePathSource -> imageDecoder.decodeFromFile(source.path)
            is ImageSource.MemorySource -> imageDecoder.decode(source.data)
        }
        return if (image.pagesTotal != 1) {
            image.close()
            when (source) {
                is ImageSource.FilePathSource -> imageDecoder.decodeFromFile(source.path, -1)
                is ImageSource.MemorySource -> imageDecoder.decode(source.data, -1)
            }
        } else {
            image
        }

    }

    private suspend fun getCurrentImage(): KomeliaImage {
        return imageAwaitScope.async { image.filterNotNull().first() }.await()
    }

    override fun requestUpdate(
        maxDisplaySize: IntSize,
        zoomFactor: Float,
        visibleDisplaySize: IntRect,
    ) {
        jobFlow.tryEmit(
            UpdateRequest(
                visibleDisplaySize = visibleDisplaySize,
                zoomFactor = zoomFactor,
                maxDisplaySize = maxDisplaySize
            )
        )
    }

    /**
     * Snaps the zoom used to decode pixels onto quarter steps.
     *
     * A pinch produces a new scale factor on essentially every frame, and any
     * change makes [doTile] rebuild every tile of the page — the full-image
     * window is deliberate there, it guarantees coverage whichever way the
     * reader then navigates. With the raw factor a single pinch could rebuild
     * the page twenty times a second.
     *
     * Rounding up rather than down matters: the decoded tiles are then always
     * at least as detailed as what is on screen, so this trades a little
     * memory for never showing a softer page than before. Zoom at or below 1,
     * which is ordinary page-by-page reading, is left exactly as it was.
     */
    private fun quantizeDecodeZoom(zoom: Float): Float {
        if (zoom <= 1f) return zoom
        return ceil(zoom * DECODE_ZOOM_STEPS) / DECODE_ZOOM_STEPS
    }

    private suspend fun doUpdate(request: UpdateRequest) {
        lastUpdateRequest = request

        val image = getCurrentImage()
        val displaySize = calculateSizeForArea(request.maxDisplaySize, stretchImages.value) ?: return
        val widthRatio = displaySize.width.toDouble() / image.width
        val heightRatio = displaySize.height.toDouble() / image.pageHeight
        val displayScaleFactor = widthRatio.coerceAtMost(heightRatio)

        val zoomFactor = request.zoomFactor
        val visibleDisplaySize = request.visibleDisplaySize

        // Decode resolution only. Everything below still uses the raw zoom, so
        // the layout, the tile geometry and the scroll bounds are untouched —
        // quantizing those would move the image under the user's fingers.
        val actualScaleFactor = displayScaleFactor * quantizeDecodeZoom(zoomFactor)

        val dstWidth = displaySize.width * zoomFactor
        val dstHeight = displaySize.height * zoomFactor

        this.displaySize.value = displaySize
        this.currentSize.value = IntSize(dstWidth.roundToInt(), dstHeight.roundToInt())

        val displayPixCount = (dstWidth * dstHeight).roundToInt()
        val tileSize = when (displayPixCount) {
            in 0..tileThreshold1 -> null
            in tileThreshold1..tileThreshold2 -> 1024
            in tileThreshold2..tileThreshold3 -> 512
            else -> 256
        }

        if (image.pagesLoaded > 1 || tileSize == null) {
            doFullResize(
                image = image,
                scaleFactor = actualScaleFactor,
                displayScaleFactor = displayScaleFactor,
                displayArea = displaySize
            )
        } else {
            doTile(
                image = image,
                displayRegion = visibleDisplaySize.toRect(),
                displayScaleFactor = displayScaleFactor,
                scaleFactor = actualScaleFactor,
                displayArea = displaySize,
                tileSize = tileSize
            )
        }
    }

    private suspend fun doFullResize(
        image: KomeliaImage,
        scaleFactor: Double,
        displayScaleFactor: Double,
        displayArea: IntSize
    ) {
        if (lastUsedScaleFactor == scaleFactor) {
            error.value?.let { throw (it) }
            return
        }

        lastUsedScaleFactor = scaleFactor
        val dstWidth = (image.width * scaleFactor).roundToInt()
        val dstHeight = (image.pageHeight * scaleFactor).roundToInt()

        measureTime {
            val resizedImage = resizeImage(
                image,
                dstWidth,
                dstHeight,
            )
            val previousTiles = frameData.value?.frames?.flatMap { it.tiles } ?: emptyList()
            val frames = resizedImage.frames.mapIndexed { i, renderImage ->
                ImageFrame(
                    tiles = listOf(
                        ReaderImageTile(
                            size = IntSize(resizedImage.width, resizedImage.height),
                            displayRegion = Rect(
                                0f,
                                0f,
                                round(image.width * displayScaleFactor).toFloat(),
                                round(image.pageHeight * displayScaleFactor).toFloat()
                            ),
                            isVisible = true,
                            renderImage = renderImage
                        )
                    ),
                    delay = resizedImage.delays?.getOrNull(i)?.toLong() ?: defaultFrameDelay
                )
            }
            pendingTilesToClose = previousTiles
            frameData.value = FrameData(
                frames = frames,
                displaySize = displayArea,
                scaleFactor = scaleFactor
            )
        }.also { logger.debug { "page ${pageId.pageNumber} completed full resize to $dstWidth x $dstHeight in $it" } }

    }

    // TODO support animations
    // does not handle animated images and assumes that there's only one frame
    private suspend fun doTile(
        image: KomeliaImage,
        displayRegion: Rect,
        displayScaleFactor: Double,
        scaleFactor: Double,
        displayArea: IntSize,
        tileSize: Int,
    ) {
        val timeSource = TimeSource.Monotonic
        val start = timeSource.markNow()

        val scaleChanged = scaleFactor != lastUsedScaleFactor

        // When zoom level changes (new panel size), tile the full image to guarantee complete
        // coverage regardless of navigation direction. When zoom is unchanged, use the
        // 1.5x-expanded viewport to avoid regenerating tiles that can be reused.
        val effectiveWindow = if (scaleChanged) {
            val totalDisplayWidth = image.width * displayScaleFactor.toFloat()
            val totalDisplayHeight = image.height * displayScaleFactor.toFloat()
            Rect(0f, 0f, totalDisplayWidth, totalDisplayHeight)
        } else {
            Rect(
                left = displayRegion.left / 1.5f,
                top = displayRegion.top / 1.5f,
                right = displayRegion.right * 1.5f,
                bottom = displayRegion.bottom * 1.5f
            )
        }

        val oldTiles = frameData.value?.frames?.first()?.tiles ?: emptyList()
        val newTiles = mutableListOf<ReaderImageTile>()
        val unusedTiles = mutableListOf<ReaderImageTile>()
        var addedNewTiles = false

        var yTaken = 0
        while (yTaken != image.height) {
            var xTaken = 0
            while (xTaken != image.width) {
                val tileRegion = IntRect(
                    top = yTaken.coerceAtMost(image.height),
                    bottom = (yTaken + tileSize).coerceAtMost(image.height),
                    left = (xTaken).coerceAtMost(image.width),
                    right = (xTaken + tileSize).coerceAtMost(image.width),
                )
                val tileDisplayRegion = Rect(
                    (tileRegion.left * displayScaleFactor).toFloat(),
                    (tileRegion.top * displayScaleFactor).toFloat(),
                    (tileRegion.right * displayScaleFactor).toFloat(),
                    (tileRegion.bottom * displayScaleFactor).toFloat()
                )

                val existingTile = oldTiles.find { it.displayRegion == tileDisplayRegion }
                if (!effectiveWindow.overlaps(tileDisplayRegion)) {
                    xTaken = (xTaken + tileSize).coerceAtMost(image.width)
                    existingTile?.let { unusedTiles.add(it) }
                    continue
                }

                if (existingTile != null && !scaleChanged && existingTile.renderImage != null) {
                    newTiles.add(existingTile)
                    xTaken = (xTaken + tileSize).coerceAtMost(image.width)
                    continue
                }

                existingTile?.let { unusedTiles.add(it) }

                val tileWidth = tileRegion.right - tileRegion.left
                val tileHeight = tileRegion.bottom - tileRegion.top
                val scaledTile = getImageRegion(
                    image,
                    tileRegion,
                    (tileWidth * scaleFactor).roundToInt(),
                    (tileHeight * scaleFactor).roundToInt()
                )
                newTiles.add(
                    ReaderImageTile(
                        size = IntSize(scaledTile.width, scaledTile.height),
                        displayRegion = tileDisplayRegion,
                        isVisible = true,
                        renderImage = scaledTile.frames.first()
                    )
                )
                addedNewTiles = true

                xTaken = (xTaken + tileSize).coerceAtMost(image.width)
            }
            yTaken = (yTaken + tileSize).coerceAtMost(image.height)
        }

        if (addedNewTiles) {
            pendingTilesToClose = unusedTiles
            frameData.value = FrameData(
                frames = listOf(ImageFrame(newTiles, 0)),
                displaySize = displayArea,
                scaleFactor = scaleFactor
            )

            val end = timeSource.markNow()
            logger.debug { "page ${pageId.pageNumber} completed tiled resize in ${end - start};  ${newTiles.size} tiles" }
        }
        lastUsedScaleFactor = scaleFactor

    }

    override fun close() {
        closeTileBitmaps(pendingTilesToClose)
        pendingTilesToClose = emptyList()
        originalImage?.close()
        frameData.value?.frames
            ?.flatMap { it.tiles }
            ?.let { closeTileBitmaps(it) }
        image.value?.close()
        imageSource.close()
        processingScope.cancel()
        imageAwaitScope.cancel()
        animationScope.cancel()
    }

    protected abstract fun closeTileBitmaps(tiles: List<ReaderImageTile>)
    protected abstract fun createTilePainter(
        tiles: List<ReaderImageTile>,
        displaySize: IntSize,
        scaleFactor: Double,
    ): TiledPainter

    protected abstract suspend fun resizeImage(
        image: KomeliaImage,
        scaleWidth: Int,
        scaleHeight: Int
    ): ReaderImageData

    protected abstract suspend fun getImageRegion(
        image: KomeliaImage,
        imageRegion: IntRect,
        scaleWidth: Int,
        scaleHeight: Int
    ): ReaderImageData

    data class ReaderImageTile(
        val size: IntSize,
        val displayRegion: Rect,
        val isVisible: Boolean,
        val renderImage: RenderImage?,
    )

    data class ReaderImageData(
        val width: Int,
        val height: Int,
        val frames: List<RenderImage>,
        val delays: List<Long>?,
    )

    abstract class TiledPainter() : Painter() {
        abstract fun withSamplingMode(upsamplingMode: UpsamplingMode): TiledPainter
    }

    class FrameData(
        val frames: List<ImageFrame>,
        val displaySize: IntSize,
        val scaleFactor: Double,
    )

    data class ImageFrame(
        val tiles: List<ReaderImageTile>,
        val delay: Long
    )
}
