package snd.komelia.image.processing

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
import snd.komelia.image.BubbleBands
import snd.komelia.image.BubbleDetector
import snd.komelia.image.KomeliaImage
import snd.komelia.image.ReaderImage.PageId

private val logger = KotlinLogging.logger {}

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

    init {
        // This step is constructed whether or not inversion is enabled, so this
        // is also what points the smart scroll's fallback detection at the model.
        BubbleDetector.configure(modelPath)
    }

    actual override suspend fun process(pageId: PageId, image: KomeliaImage): KomeliaImage? {
        if (!enabled.first()) return null
        if (!openCvLoaded) return null

        return withContext(Dispatchers.Default) {
            runCatching { invertBubbles(pageId, image) }
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
    private fun invertBubbles(pageId: PageId, image: KomeliaImage): KomeliaImage? {
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
            val boxes = BubbleDetector.boxes(source)
            // Hand the boxes to the smart scroll: it needs exactly these, and
            // re-running an 11 MB model for them would be pure waste.
            val h = source.height.toFloat()
            BubbleBands.publish(pageId, boxes.map { (it.y / h)..((it.y + it.height) / h) })
            if (boxes.isEmpty()) return null
            return invertInsideBoxes(source, boxes)?.let { AndroidBitmapBackedImage(it) }
        } finally {
            if (ownsSource) source.recycle()
            if (ownsDecoded) decoded.recycle()
        }
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
