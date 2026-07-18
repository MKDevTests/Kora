package snd.komelia.image.processing

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import snd.komelia.image.AndroidBitmap.toBitmap
import snd.komelia.image.AndroidBitmapBackedImage
import snd.komelia.image.KomeliaImage
import snd.komelia.image.ReaderImage.PageId
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

private val logger = KotlinLogging.logger {}

// --- Model contract (ogkalu/comic-text-and-bubble-detector, RT-DETR, Apache-2.0)
private const val INPUT_SIZE = 640
private const val CLASS_BUBBLE = 0
private const val MIN_SCORE = 0.5f

/**
 * `orig_target_sizes` for THIS export is [width, height].
 * Verified on the bench: passing [height, width] pushes the X coordinates past
 * the image width (boxes landing at x=2435 on a 1920-wide page).
 */
private fun sizeTensorValues(width: Int, height: Int) = longArrayOf(width.toLong(), height.toLong())

// --- Local mask refinement inside a detected box ------------------------------
/** A pixel at/above this is "bubble paper" when refining the mask. */
private const val BRIGHT_THRESHOLD = 200.0
/** Mean luminance above which a bubble counts as light (so worth inverting). */
private const val LIGHT_BUBBLE_MEAN = 140.0

private val openCvLoaded: Boolean by lazy {
    runCatching { OpenCVLoader.initLocal() }
        .onFailure { logger.warn(it) { "OpenCV failed to load, bubble inversion disabled" } }
        .getOrDefault(false)
}

actual class BubbleInvertStep actual constructor(
    private val enabled: Flow<Boolean>,
    private val modelPath: () -> String?,
) : ProcessingStep {
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val ortEnv: OrtEnvironment? by lazy {
        runCatching { OrtEnvironment.getEnvironment() }
            .onFailure { logger.warn(it) { "ONNX runtime unavailable, bubble inversion disabled" } }
            .getOrNull()
    }

    /**
     * Built once, on first use. Null when the model file isn't on disk — the
     * step then does nothing, which is why a missing model degrades to "no
     * inversion" rather than an error.
     */
    private val session: OrtSession? by lazy {
        val env = ortEnv ?: return@lazy null
        val path = modelPath()
        if (path == null || !File(path).isFile) {
            logger.info { "bubble detection model not found ($path); inversion disabled" }
            return@lazy null
        }
        runCatching { env.createSession(path, OrtSession.SessionOptions()) }
            .onFailure { logger.warn(it) { "failed to open bubble detection model at $path" } }
            .getOrNull()
    }

    actual override suspend fun process(pageId: PageId, image: KomeliaImage): KomeliaImage? {
        if (!enabled.first()) return null
        if (!openCvLoaded) return null
        val session = session ?: return null

        return withContext(Dispatchers.Default) {
            runCatching { invertBubbles(image, session) }
                .onFailure { logger.warn(it) { "bubble inversion failed for $pageId, leaving page untouched" } }
                .getOrNull()
        }
    }

    actual override suspend fun addChangeListener(callback: () -> Unit) {
        enabled.drop(1).onEach { callback() }.launchIn(coroutineScope)
    }

    /**
     * Never returns the input instance: [ImageProcessingPipeline] closes the
     * previous image once a step returns a new one.
     */
    private fun invertBubbles(image: KomeliaImage, session: OrtSession): KomeliaImage? {
        // toBitmap() goes through vips and yields a HARDWARE bitmap on API 29+,
        // whose pixels can't be read back — same copy dance as OcrService.
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
            val boxes = detectBubbles(source, session)
            if (boxes.isEmpty()) return null
            return invertInsideBoxes(source, boxes)?.let { AndroidBitmapBackedImage(it) }
        } finally {
            if (ownsSource) source.recycle()
            if (ownsDecoded) decoded.recycle()
        }
    }

    /** Runs the detector and returns bubble boxes in ORIGINAL pixel coordinates. */
    private fun detectBubbles(source: Bitmap, session: OrtSession): List<Rect> {
        val env = ortEnv ?: return emptyList()
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

    /**
     * Inverts the bubble pixels inside each detected box.
     *
     * The box alone is not enough: filling it would invert the artwork sitting in
     * the corners around an oval bubble. So inside each box we rebuild the actual
     * bubble mask — bright pixels, largest blob, the one covering the box centre —
     * and invert only those. Localisation comes from the model, precision from a
     * threshold that is trivially reliable once we already know a bubble is here.
     *
     * A bubble that is already dark (white text on black) is skipped: inverting it
     * would defeat the purpose.
     */
    private fun invertInsideBoxes(source: Bitmap, boxes: List<Rect>): Bitmap? {
        val rgba = Mat()
        val gray = Mat()
        val mask = Mat()
        try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            mask.create(gray.size(), CvType.CV_8UC1)
            mask.setTo(Scalar(0.0))

            var inverted = 0
            for (box in boxes) {
                if (box.width < 4 || box.height < 4) continue
                val roiGray = Mat(gray, box)
                try {
                    if (Core.mean(roiGray).`val`[0] < LIGHT_BUBBLE_MEAN) continue // already dark

                    val binary = Mat()
                    val contours = ArrayList<MatOfPoint>()
                    try {
                        Imgproc.threshold(roiGray, binary, BRIGHT_THRESHOLD, 255.0, Imgproc.THRESH_BINARY)
                        Imgproc.findContours(
                            binary, contours, Mat(),
                            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
                        )
                        // The bubble is the blob covering the box centre; fall back
                        // to the largest one when the centre sits on a glyph.
                        val cx = box.width / 2.0
                        val cy = box.height / 2.0
                        val chosen = contours.firstOrNull {
                            Imgproc.pointPolygonTest(
                                org.opencv.core.MatOfPoint2f(*it.toArray()),
                                org.opencv.core.Point(cx, cy), false
                            ) >= 0
                        } ?: contours.maxByOrNull { Imgproc.contourArea(it) } ?: continue

                        // Draw it into the page-level mask, offset back to page space.
                        Imgproc.drawContours(
                            mask, listOf(chosen), -1, Scalar(255.0), -1,
                            Imgproc.LINE_8, Mat(), Int.MAX_VALUE,
                            org.opencv.core.Point(box.x.toDouble(), box.y.toDouble())
                        )
                        inverted++
                    } finally {
                        contours.forEach { it.release() }
                        binary.release()
                    }
                } finally {
                    roiGray.release()
                }
            }
            if (inverted == 0) return null

            // Invert R, G and B under the mask but leave alpha alone — a plain
            // bitwise_not over RGBA would invert alpha and punch the bubbles out.
            val channels = ArrayList<Mat>()
            try {
                Core.split(rgba, channels)
                for (c in 0 until minOf(3, channels.size)) {
                    Core.bitwise_not(channels[c], channels[c], mask)
                }
                Core.merge(channels, rgba)
            } finally {
                channels.forEach { it.release() }
            }

            val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgba, out)
            logger.debug { "inverted $inverted bubble(s)" }
            return out
        } finally {
            mask.release()
            gray.release()
            rgba.release()
        }
    }
}
