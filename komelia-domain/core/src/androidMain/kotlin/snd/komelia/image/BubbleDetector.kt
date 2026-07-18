package snd.komelia.image

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Rect
import snd.komelia.image.AndroidBitmap.toBitmap
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

private val logger = KotlinLogging.logger {}

// --- Model contract (ogkalu/comic-text-and-bubble-detector, RT-DETR, Apache-2.0)
private const val INPUT_SIZE = 640
private const val CLASS_BUBBLE = 0
private const val MIN_SCORE = 0.5f

// --- Tall-strip slicing -------------------------------------------------------
/** Pages taller than this many widths are detected in slices. */
private const val MIN_SLICE_ASPECT = 2

/**
 * Slice height, in page widths. Swept on the bench against fine w×1.5 slices:
 * w×4 keeps 72-79% of the boxes correctly placed (mean IoU 0.75-0.83), while
 * w×8 collapses to 47-56% (IoU 0.63-0.66) — the bubble *count* stays flat there,
 * so counting detections would have hidden the drift. Fewer, taller slices are
 * cheaper (a 15160px page costs 6 inferences at w×4), so this is the largest
 * slice that still places boxes accurately enough to invert pixels inside them.
 */
private const val SLICE_ASPECT = 4

/** Slices overlap by this many page widths so a bubble on a seam is caught whole. */
private const val SLICE_OVERLAP_ASPECT = 0.5f

/** Two boxes this close are the same bubble seen by two overlapping slices. */
private const val DUPLICATE_IOU = 0.5

/** A remaining sliver too short to hold a bubble isn't worth an inference. */
private const val MIN_SLICE_PX = 64

/**
 * The one speech-bubble detector for the whole app: the invert step and the
 * webtoon smart scroll both need the same boxes, and an ONNX session holds an
 * 11 MB model — opening a second one would double that for identical results.
 */
object BubbleDetector {
    private var modelPath: (() -> String?)? = null

    private val ortEnv: OrtEnvironment? by lazy {
        runCatching { OrtEnvironment.getEnvironment() }
            .onFailure { logger.warn(it) { "ONNX runtime unavailable, bubble detection disabled" } }
            .getOrNull()
    }

    /**
     * Built once, on first use. Null when the model file isn't on disk — callers
     * then do nothing, which is why a missing model degrades to "no bubbles"
     * rather than an error.
     */
    private val session: OrtSession? by lazy {
        val env = ortEnv ?: return@lazy null
        val path = modelPath?.invoke()
        if (path == null || !File(path).isFile) {
            logger.info { "bubble detection model not found ($path); bubble features disabled" }
            return@lazy null
        }
        runCatching { env.createSession(path, OrtSession.SessionOptions()) }
            .onFailure { logger.warn(it) { "failed to open bubble detection model at $path" } }
            .getOrNull()
    }

    fun configure(path: () -> String?) {
        modelPath = path
    }

    val isAvailable: Boolean get() = session != null

    /**
     * `orig_target_sizes` for THIS export is [width, height].
     * Verified on the bench: passing [height, width] pushes the X coordinates past
     * the image width (boxes landing at x=2435 on a 1920-wide page).
     */
    private fun sizeTensorValues(width: Int, height: Int) = longArrayOf(width.toLong(), height.toLong())

    /**
     * Runs the detector and returns bubble boxes in ORIGINAL pixel coordinates.
     *
     * A tall strip is detected in horizontal slices, because the model input is a
     * fixed 640x640: squashing a 720x15160 webtoon page into it leaves each bubble
     * a few pixels tall and the detector finds almost nothing. Measured on a real
     * page: **5 bubbles whole-page against 44 sliced** — which is why speech-bubble
     * inversion silently did nothing on webtoons while working fine on manga.
     */
    fun boxes(source: Bitmap): List<Rect> {
        val sliceHeight = source.width * SLICE_ASPECT
        if (source.height <= source.width * MIN_SLICE_ASPECT) return detect(source)

        val step = (sliceHeight - source.width * SLICE_OVERLAP_ASPECT).toInt().coerceAtLeast(1)
        val all = ArrayList<Rect>()
        var top = 0
        while (top < source.height) {
            val height = minOf(sliceHeight, source.height - top)
            if (height < MIN_SLICE_PX) break
            val slice = Bitmap.createBitmap(source, 0, top, source.width, height)
            try {
                for (box in detect(slice)) {
                    val moved = Rect(box.x, box.y + top, box.width, box.height)
                    // Slices overlap so a bubble on a seam is caught whole by one
                    // of them; drop the duplicate the neighbour also reported.
                    if (all.none { iou(it, moved) > DUPLICATE_IOU }) all.add(moved)
                }
            } finally {
                if (slice !== source) slice.recycle()
            }
            top += step
        }
        return all
    }

    private fun iou(a: Rect, b: Rect): Double {
        val x0 = maxOf(a.x, b.x)
        val y0 = maxOf(a.y, b.y)
        val x1 = minOf(a.x + a.width, b.x + b.width)
        val y1 = minOf(a.y + a.height, b.y + b.height)
        if (x1 <= x0 || y1 <= y0) return 0.0
        val inter = (x1 - x0).toDouble() * (y1 - y0)
        return inter / (a.width.toDouble() * a.height + b.width.toDouble() * b.height - inter)
    }

    private fun detect(source: Bitmap): List<Rect> {
        val env = ortEnv ?: return emptyList()
        val session = session ?: return emptyList()
        val width = source.width
        val height = source.height

        val scaled = Bitmap.createScaledBitmap(source, INPUT_SIZE, INPUT_SIZE, true)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        if (scaled !== source) scaled.recycle()

        // CHW, [0,1]. This model's preprocessor sets do_normalize=false, so there
        // is deliberately NO ImageNet mean/std here (unlike the panel detector).
        val plane = INPUT_SIZE * INPUT_SIZE
        val chw = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val p = pixels[i]
            chw[i] = ((p shr 16) and 0xFF) / 255f
            chw[plane + i] = ((p shr 8) and 0xFF) / 255f
            chw[2 * plane + i] = (p and 0xFF) / 255f
        }

        val out = ArrayList<Rect>()
        OnnxTensor.createTensor(
            env, FloatBuffer.wrap(chw), longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        ).use { imagesTensor ->
            OnnxTensor.createTensor(
                env, LongBuffer.wrap(sizeTensorValues(width, height)), longArrayOf(1, 2)
            ).use { sizesTensor ->
                session.run(mapOf("images" to imagesTensor, "orig_target_sizes" to sizesTensor))
                    .use { results ->
                        // This export decodes internally: labels[1,300],
                        // boxes[1,300,4] already in original pixels (xyxy),
                        // scores[1,300]. No sigmoid / top-k / NMS to do here.
                        @Suppress("UNCHECKED_CAST")
                        val labels = results.get("labels").get().value as Array<LongArray>
                        @Suppress("UNCHECKED_CAST")
                        val boxes = results.get("boxes").get().value as Array<Array<FloatArray>>
                        @Suppress("UNCHECKED_CAST")
                        val scores = results.get("scores").get().value as Array<FloatArray>

                        for (i in labels[0].indices) {
                            if (scores[0][i] < MIN_SCORE) continue
                            if (labels[0][i].toInt() != CLASS_BUBBLE) continue
                            val b = boxes[0][i]
                            val x0 = b[0].toInt().coerceIn(0, width - 1)
                            val y0 = b[1].toInt().coerceIn(0, height - 1)
                            val x1 = b[2].toInt().coerceIn(x0 + 1, width)
                            val y1 = b[3].toInt().coerceIn(y0 + 1, height)
                            out.add(Rect(x0, y0, x1 - x0, y1 - y0))
                        }
                    }
            }
        }
        return out
    }
}

actual fun configureBubbleDetector(modelPath: () -> String?) = BubbleDetector.configure(modelPath)

actual suspend fun detectBubbleBands(
    image: KomeliaImage,
): List<ClosedFloatingPointRange<Float>> = withContext(Dispatchers.Default) {
    if (!BubbleDetector.isAvailable) return@withContext emptyList()

    runCatching {
        val decoded: Bitmap = if (image is AndroidBitmapBackedImage) image.bitmap else image.toBitmap()
        val ownsDecoded = image !is AndroidBitmapBackedImage
        val source: Bitmap
        val ownsSource: Boolean
        if (decoded.config == Bitmap.Config.HARDWARE) {
            source = decoded.copy(Bitmap.Config.ARGB_8888, false)
            ownsSource = true
        } else {
            source = decoded
            ownsSource = false
        }
        try {
            val h = source.height.toFloat()
            BubbleDetector.boxes(source).map { (it.y / h)..((it.y + it.height) / h) }
        } finally {
            if (ownsSource) source.recycle()
            if (ownsDecoded) decoded.recycle()
        }
    }.onFailure { logger.warn(it) { "bubble band detection failed" } }.getOrDefault(emptyList())
}
