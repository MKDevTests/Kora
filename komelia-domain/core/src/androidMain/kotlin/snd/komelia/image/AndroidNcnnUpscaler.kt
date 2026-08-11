package snd.komelia.image

import android.content.Context
import android.graphics.Bitmap
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.snd_r.komelia.infra.ncnn.NcnnSharedLibraries
import io.github.snd_r.komelia.infra.ncnn.NcnnUpscaler
import io.github.snd_r.komelia.infra.ncnn.NcnnUpscaler.Companion.ENGINE_REALCUGAN
import io.github.snd_r.komelia.infra.ncnn.NcnnUpscaler.Companion.ENGINE_REALSR
import io.github.snd_r.komelia.infra.ncnn.NcnnUpscaler.Companion.ENGINE_REAL_ESRGAN
import io.github.snd_r.komelia.infra.ncnn.NcnnUpscaler.Companion.ENGINE_WAIFU2X
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import snd.komelia.image.AndroidBitmap.toSoftwareBitmap
import snd.komelia.settings.ImageReaderSettingsRepository
import snd.komelia.settings.model.NcnnEngine
import snd.komelia.settings.model.NcnnUpscalerSettings
import snd.komelia.updates.OnnxModelDownloader
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger {}

class AndroidNcnnUpscaler(
    private val context: Context,
    private val settingsRepository: ImageReaderSettingsRepository,
    private val onnxModelDownloader: OnnxModelDownloader? = null,
) : AutoCloseable {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var ncnn: NcnnUpscaler? = null
    @Volatile private var currentSettings: NcnnUpscalerSettings? = null
    private var gpuInstanceCreated = false
    val isReady = MutableStateFlow(false)
    val settingsFlow = MutableStateFlow<NcnnUpscalerSettings?>(null)
    val isEnabled: Boolean get() = settingsFlow.value != null

    companion object {
        private val jniMutex = Mutex()
        val globalUpscaleActivities: MutableStateFlow<Map<Int, UpscaleStatus>> =
            MutableStateFlow(emptyMap())
        val isDownloadedFlow = MutableStateFlow(false)

        private val generation = AtomicInteger(0)
        val currentPageNumber = AtomicInteger(-1)

        private data class UpscaleRequest(
            val generation: Int,
            val pageNumber: Int,
            val block: suspend () -> KomeliaImage?,
            val onDiscard: () -> Unit = {},
            val result: CompletableDeferred<KomeliaImage?> = CompletableDeferred()
        )

        private val requestChannel = Channel<UpscaleRequest>(Channel.UNLIMITED)
        private val workerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        init {
            workerScope.launch { runWorker() }
        }

        private suspend fun runWorker() {
            val pending = ArrayDeque<UpscaleRequest>()
            while (true) {
                if (pending.isEmpty()) pending.add(requestChannel.receive())
                while (true) { pending.add(requestChannel.tryReceive().getOrNull() ?: break) }

                val current = currentPageNumber.get()
                val idx = pending.indexOfFirst { it.pageNumber == current }
                val work = if (idx >= 0) pending.removeAt(idx) else pending.removeFirst()

                if (work.generation != generation.get()) {
                    work.onDiscard()
                    work.result.complete(null)
                    continue
                }
                val image = try {
                    withContext(NonCancellable) { work.block() }
                } catch (e: Throwable) {
                    logger.error(e) { "[NCNN] Upscale block failed for page ${work.pageNumber}" }
                    null
                }
                work.result.complete(image)
            }
        }

        fun cancelPendingRequests() {
            generation.incrementAndGet()
            globalUpscaleActivities.value = emptyMap()
        }

        internal fun registerActivity(pageNumber: Int) {
            globalUpscaleActivities.update { it + (pageNumber to UpscaleStatus.Upscaling) }
        }

        internal fun unregisterActivity(pageNumber: Int, status: UpscaleStatus) {
            globalUpscaleActivities.update { map ->
                if (status == UpscaleStatus.Upscaled) map + (pageNumber to UpscaleStatus.Upscaled)
                else map - pageNumber
            }
        }
    }

    fun initialize() {
        if (!NcnnSharedLibraries.isAvailable) {
            logger.warn { "NCNN shared libraries are not available. Upscaler will be disabled." }
            return
        }
        isDownloadedFlow.value = isDownloaded()

        onnxModelDownloader?.downloadCompletionEvents
            ?.filterIsInstance<OnnxModelDownloader.CompletionEvent.NcnnModelDownloaded>()
            ?.onEach { isDownloadedFlow.value = isDownloaded() }
            ?.launchIn(scope)

        settingsRepository.getNcnnUpscalerSettings()
            .onEach { settings ->
                try {
                    jniMutex.withLock {
                        val downloaded = isDownloaded()
                        isDownloadedFlow.value = downloaded
                        if (settings.enabled && downloaded) {
                            // The Vulkan instance used to be created here unconditionally,
                            // at DI graph build time — i.e. on every app start, even for
                            // users who never turn upscaling on. reinit() is the only thing
                            // that needs it, so create it on the way in instead.
                            ensureGpuInstance()
                            if (ncnn == null || shouldReinit(settings)) {
                                reinit(settings)
                            } else {
                                // upscaleOnLoad toggled while engine is the same — update settingsFlow
                                currentSettings = settings
                                settingsFlow.value = settings
                            }
                        } else {
                            cancelPendingRequests()
                            ncnn?.release()
                            ncnn = null
                            currentSettings = null
                            isReady.value = false
                            settingsFlow.value = null
                        }
                    }
                } catch (e: Throwable) {
                    logger.error(e) { "Failed to initialize NCNN upscaler" }
                }
            }.launchIn(scope)
    }

    /** Must be called while holding [jniMutex]. */
    private fun ensureGpuInstance() {
        if (gpuInstanceCreated) return
        val result = NcnnUpscaler().createGpuInstance()
        if (result == 0) {
            gpuInstanceCreated = true
            logger.info { "NCNN GPU instance created" }
        } else {
            logger.error { "Failed to create NCNN GPU instance: $result" }
        }
    }

    private fun isDownloaded(): Boolean {
        val ncnnDir = File(context.filesDir, "ncnn_models")
        return ncnnDir.exists() && ncnnDir.isDirectory && (ncnnDir.list()?.isNotEmpty() ?: false)
    }

    fun willUpscale(image: KomeliaImage): Boolean {
        val settings = currentSettings ?: return false
        return settings.enabled && settings.upscaleOnLoad && image.width < settings.upscaleThreshold
    }

    suspend fun upscale(image: KomeliaImage, pageNumber: Int): KomeliaImage? {
        // For VipsBackedImage: convert to an owned Android Bitmap BEFORE enqueueing.
        // The block lambda captures `image` by reference, and the underlying VipsImage*
        // can be freed (via close()) on another thread while the request is in the queue,
        // causing a SIGSEGV in vips_image_get_interpretation when the block finally runs.
        // Materialising the bitmap here, while the caller still holds a live reference
        // to `image`, eliminates the race entirely.
        val preBitmapIn: Bitmap? = when (image) {
            is VipsBackedImage -> image.vipsImage.toSoftwareBitmap()
            is AndroidBitmapBackedImage -> image.bitmap.copy(Bitmap.Config.ARGB_8888, false)
            else -> null
        }

        val request = UpscaleRequest(
            generation = generation.get(),
            pageNumber = pageNumber,
            onDiscard = { preBitmapIn?.recycle() },
            block = block@{
                val bitmapIn = preBitmapIn ?: when (image) {
                    is AndroidBitmapBackedImage -> {
                        if (image.bitmap.config == Bitmap.Config.HARDWARE) {
                            image.bitmap.copy(Bitmap.Config.ARGB_8888, false)
                        } else {
                            image.bitmap
                        }
                    }
                    else -> return@block null
                }
                var bitmapOut: Bitmap? = null
                try {
                    val result = jniMutex.withLock {
                        val upscaler = ncnn ?: return@withLock -1
                        val scale = parseModelScaleAndNoise(currentSettings?.model ?: "").first
                        bitmapOut = Bitmap.createBitmap(
                            bitmapIn.width * scale,
                            bitmapIn.height * scale,
                            Bitmap.Config.ARGB_8888
                        )
                        upscaler.process(bitmapIn, bitmapOut!!)
                    }
                    if (result != 0) {
                        logger.error { "NCNN upscaling failed with code $result" }
                        bitmapOut?.recycle()
                        null
                    } else {
                        AndroidBitmapBackedImage(bitmapOut!!)
                    }
                } finally {
                    if (preBitmapIn != null) {
                        preBitmapIn.recycle()   // we own the pre-converted bitmap
                    } else if (image !is AndroidBitmapBackedImage) {
                        bitmapIn.recycle()
                    } else if (bitmapIn != image.bitmap) {
                        bitmapIn.recycle()      // hardware→software copy
                    }
                }
            }
        )

        var sent = false
        return try {
            requestChannel.send(request)
            sent = true
            request.result.await()
        } catch (e: Throwable) {
            if (!sent) preBitmapIn?.recycle()
            logger.error(e) { "Failed to upscale image" }
            null
        }
    }

    suspend fun checkAndUpscale(image: KomeliaImage, pageNumber: Int): KomeliaImage {
        val settings = currentSettings ?: return image
        if (settings.enabled && settings.upscaleOnLoad && image.width < settings.upscaleThreshold) {
            logger.info { "[NCNN] Pre-emptive upscale triggered: ${image.width}px < ${settings.upscaleThreshold}px" }
            return upscale(image, pageNumber) ?: image
        }
        return image
    }

    private fun shouldReinit(newSettings: NcnnUpscalerSettings): Boolean {
        val current = currentSettings ?: return true
        return current.engine != newSettings.engine ||
                current.model != newSettings.model ||
                current.gpuId != newSettings.gpuId ||
                current.ttaMode != newSettings.ttaMode ||
                current.numThreads != newSettings.numThreads
    }

    private fun parseModelScaleAndNoise(modelPath: String): Pair<Int, Int> {
        val modelName = modelPath.substringAfterLast("/")
        return when {
            modelName == "scale2.0x_model" -> Pair(2, -1)
            modelName.startsWith("noise") && modelName.contains("scale2.0x") -> {
                val noise = modelName.removePrefix("noise").substringBefore("_").toIntOrNull() ?: 0
                Pair(2, noise)
            }
            modelName.startsWith("noise") -> {
                val noise = modelName.removePrefix("noise").substringBefore("_").toIntOrNull() ?: 0
                Pair(1, noise)
            }
            modelName.contains("up2x") -> Pair(2, 0)
            modelName.contains("realsr") -> Pair(4, -1)
            modelName.contains("x2") -> Pair(2, -1)
            modelName.contains("x4") -> Pair(4, -1)
            else -> Pair(2, -1)
        }
    }

    private fun reinit(settings: NcnnUpscalerSettings) {
        if (ncnn != null) cancelPendingRequests()
        ncnn?.release()
        val newNcnn = NcnnUpscaler()
        val engineType = when (settings.engine) {
            NcnnEngine.WAIFU2X -> ENGINE_WAIFU2X
            NcnnEngine.REALCUGAN -> ENGINE_REALCUGAN
            NcnnEngine.REALSR -> ENGINE_REALSR
            NcnnEngine.REAL_ESRGAN -> ENGINE_REAL_ESRGAN
        }
        newNcnn.init(engineType, settings.gpuId, settings.ttaMode, settings.numThreads)

        val (scale, noise) = parseModelScaleAndNoise(settings.model)
        newNcnn.setScale(scale)
        newNcnn.setNoise(noise)

        val modelPath = settings.model
        val paramPath: String
        val binPath: String

        if (settings.engine == NcnnEngine.REALSR || settings.engine == NcnnEngine.REAL_ESRGAN) {
            val scale = parseModelScaleAndNoise(modelPath).first
            paramPath = "$modelPath/x$scale.param"
            binPath = "$modelPath/x$scale.bin"
        } else {
            paramPath = "$modelPath.param"
            binPath = "$modelPath.bin"
        }

        val ncnnDir = File(context.filesDir, "ncnn_models")
        val absoluteParamPath = File(ncnnDir, paramPath).absolutePath
        val absoluteBinPath = File(ncnnDir, binPath).absolutePath

        val loadResult = newNcnn.load(null, absoluteParamPath, absoluteBinPath)
        if (loadResult != 0) {
            logger.error { "Failed to load NCNN model $modelPath: $loadResult" }
            newNcnn.release()
            ncnn = null
            currentSettings = null
            settingsFlow.value = null
        } else {
            ncnn = newNcnn
            currentSettings = settings
            settingsFlow.value = settings
            isReady.value = true
        }
    }

    override fun close() {
        scope.cancel()
        AndroidNcnnUpscaler.cancelPendingRequests()
        runBlocking {
            jniMutex.withLock {
                ncnn?.release()
                ncnn = null
            }
        }
        if (gpuInstanceCreated) {
            NcnnUpscaler().destroyGpuInstance()
            gpuInstanceCreated = false
        }
    }
}
