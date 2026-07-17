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
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import snd.komelia.image.AndroidBitmap.toBitmap
import snd.komelia.image.AndroidBitmapBackedImage
import snd.komelia.image.KomeliaImage
import snd.komelia.image.ReaderImage.PageId

private val logger = KotlinLogging.logger {}

// --- Detection tuning -------------------------------------------------------
// A heuristic; the only way to tune it is against real pages. The hard part is
// NOT finding bright blobs with dark specks in them — a white face with eyes, a
// white sky with birds, a white shirt with buttons all qualify. The
// discriminator that actually works is that a speech bubble's holes are TEXT:
// many of them, small, and all about the same height (one font size). Artwork
// features are few and wildly different sizes.
//
// NOTE: tuning by reasoning alone failed twice (a per-page relative white
// point, and counting ink components instead of enclosed holes — both regressed
// real pages and were reverted). Every threshold change MUST now go through the
// offline bench first: C:\Users\mathi\Downloads\Dev\_bubble-bench\bench.py is a
// faithful Python port of this file (validated against on-device screenshots),
// run it over full volumes and diff detections before touching Kotlin.

/** Pixels at/above this (0..255) count as "bright" when isolating blobs. */
private const val BRIGHT_THRESHOLD = 200.0

/** Pixels at/above this count as untouched paper white (used for purity). */
private const val PURE_WHITE_THRESHOLD = 240.0

private const val MIN_AREA_RATIO = 0.002
private const val MAX_AREA_RATIO = 0.10

/** area / boundingRect area. Cheap pre-filter that kills thin slivers. */
private const val MIN_FILL_RATIO = 0.55

/**
 * area / convexHull area. A bubble is a smooth convex blob (~0.9+, a little
 * less with its tail). Faces-with-hair, clothing and scenery silhouettes are
 * ragged and score well below.
 */
private const val MIN_SOLIDITY = 0.88

/**
 * Share of the blob that must be pure paper white. Text costs ~10-20%, so a
 * real bubble still lands high. Screentone and gradient shading — very common
 * inside artwork — fall far below.
 */
private const val MIN_WHITE_PURITY = 0.75

/**
 * Text means *several* glyphs. A face has ~3-6 features (eyes, nose, mouth), so
 * this alone removes a lot. Cost: single-glyph bubbles ("!", "?") are skipped.
 */
private const val MIN_GLYPH_COUNT = 4
private const val MAX_GLYPH_COUNT = 500

/** A single hole bigger than this share of the blob is a drawing, not a glyph. */
private const val MAX_HOLE_RATIO = 0.06

/** Total hole area / blob area. Text is sparse ink on paper. */
private const val MIN_INK_RATIO = 0.015
private const val MAX_INK_RATIO = 0.35

/**
 * Glyphs of one font share a height. Holes within
 * [median*MIN, median*MAX] count as consistent, and at least
 * [MIN_UNIFORM_SHARE] of them must be — this is what separates "a line of text"
 * from "an eye, a nostril and a mouth".
 */
private const val GLYPH_HEIGHT_MIN_FACTOR = 0.45
private const val GLYPH_HEIGHT_MAX_FACTOR = 2.0
private const val MIN_UNIFORM_SHARE = 0.65

/**
 * Two-tier solidity. Bubbles with a long tail (BD) or a spiky outline (manga
 * shouts) score 0.78-0.88 solidity — the SAME range as some artwork (a brick
 * wall measured 0.871, a hatched arm 0.805), so lowering MIN_SOLIDITY alone
 * re-introduces the "inverted skull" class of false positive. What separates
 * them, measured on the offline bench (301 pages, DB Perfect t8 + Wunderwaffen
 * t3): glyph-height uniformity. Real text in the low-solidity range scored
 * 0.81-0.95; the confirmed false positives 0.71 and 0.78.
 *
 * Rule: below MIN_SOLIDITY a candidate may still pass IF solidity >=
 * [TIER2_MIN_SOLIDITY] AND uniformity >= [TIER2_MIN_UNIFORM]. Purely additive —
 * every v2 detection still passes tier 1 unchanged. Bench result: +219
 * detections, 0 regressions, page-220 audit 12/12 bubbles with 0 false
 * positives.
 */
private const val TIER2_MIN_SOLIDITY = 0.78
private const val TIER2_MIN_UNIFORM = 0.80

private val openCvLoaded: Boolean by lazy {
    runCatching { OpenCVLoader.initLocal() }
        .onFailure { logger.warn(it) { "OpenCV failed to load, bubble inversion disabled" } }
        .getOrDefault(false)
}

actual class BubbleInvertStep actual constructor(
    private val enabled: Flow<Boolean>,
) : ProcessingStep {
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    actual override suspend fun process(pageId: PageId, image: KomeliaImage): KomeliaImage? {
        if (!enabled.first()) return null
        if (!openCvLoaded) return null

        return withContext(Dispatchers.Default) {
            runCatching { invertBubbles(image) }
                .onFailure { logger.warn(it) { "bubble inversion failed for $pageId, leaving page untouched" } }
                .getOrNull()
        }
    }

    actual override suspend fun addChangeListener(callback: () -> Unit) {
        enabled.drop(1).onEach { callback() }.launchIn(coroutineScope)
    }

    /**
     * Returns a new image with bubble pixels inverted, or null to leave the page
     * as-is (nothing detected, or nothing we're confident about).
     *
     * Never returns the input instance: [ImageProcessingPipeline] closes the
     * previous image once a step returns a new one, so handing back the same
     * object would have it closed out from under us.
     */
    private fun invertBubbles(image: KomeliaImage): KomeliaImage? {
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
            return detectAndInvert(source)?.let { AndroidBitmapBackedImage(it) }
        } finally {
            if (ownsSource) source.recycle()
            if (ownsDecoded) decoded.recycle()
        }
    }

    private fun detectAndInvert(source: Bitmap): Bitmap? {
        val rgba = Mat()
        val gray = Mat()
        val binary = Mat()
        val pureWhite = Mat()
        val hierarchy = Mat()
        var mask: Mat? = null
        val contours = ArrayList<MatOfPoint>()

        try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.threshold(gray, binary, BRIGHT_THRESHOLD, 255.0, Imgproc.THRESH_BINARY)
            Imgproc.threshold(gray, pureWhite, PURE_WHITE_THRESHOLD, 255.0, Imgproc.THRESH_BINARY)

            // RETR_CCOMP gives a 2-level hierarchy: top level = outer edges of
            // bright blobs, second level = the holes inside them. That hole
            // relationship is the foundation — everything below judges whether
            // those holes look like text.
            Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE)
            if (contours.isEmpty()) return null

            val pageArea = (gray.rows() * gray.cols()).toDouble()
            val bubbleMask = Mat.zeros(gray.size(), CvType.CV_8UC1)
            mask = bubbleMask
            var found = 0

            for (i in contours.indices) {
                if (isBubble(contours, hierarchy, i, pageArea, pureWhite)) {
                    Imgproc.drawContours(bubbleMask, contours, i, Scalar(255.0), -1)
                    found++
                }
            }
            if (found == 0) return null

            // Invert R, G and B under the mask but leave alpha alone — a plain
            // bitwise_not over RGBA would invert alpha too and punch the bubbles
            // out of the page.
            val channels = ArrayList<Mat>()
            try {
                Core.split(rgba, channels)
                for (c in 0 until minOf(3, channels.size)) {
                    Core.bitwise_not(channels[c], channels[c], bubbleMask)
                }
                Core.merge(channels, rgba)
            } finally {
                channels.forEach { it.release() }
            }

            val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgba, out)
            logger.debug { "inverted $found bubble(s)" }
            return out
        } finally {
            contours.forEach { it.release() }
            mask?.release()
            hierarchy.release()
            pureWhite.release()
            binary.release()
            gray.release()
            rgba.release()
        }
    }

    /**
     * Checks are ordered cheapest-first — most candidates die on area or shape
     * long before the per-blob mask work at the end.
     *
     * hierarchy row layout is [next, previous, firstChild, parent].
     */
    private fun isBubble(
        contours: List<MatOfPoint>,
        hierarchy: Mat,
        index: Int,
        pageArea: Double,
        pureWhite: Mat,
    ): Boolean {
        val node = hierarchy.get(0, index) ?: return false
        if (node[3].toInt() != -1) return false // a hole, not a blob
        val firstChild = node[2].toInt()
        if (firstChild == -1) return false // nothing inside => no text => not a bubble

        val contour = contours[index]
        val area = Imgproc.contourArea(contour)
        if (area < pageArea * MIN_AREA_RATIO) return false
        if (area > pageArea * MAX_AREA_RATIO) return false

        val bounds = Imgproc.boundingRect(contour)
        val boundsArea = (bounds.width * bounds.height).toDouble()
        if (boundsArea <= 0.0) return false
        if (area / boundsArea < MIN_FILL_RATIO) return false

        val uniformShare = holesLookLikeText(contours, hierarchy, firstChild, area) ?: return false
        val sol = solidity(contour)
        if (sol < MIN_SOLIDITY &&
            !(sol >= TIER2_MIN_SOLIDITY && uniformShare >= TIER2_MIN_UNIFORM)
        ) return false
        return whitePurity(contour, bounds, pureWhite) >= MIN_WHITE_PURITY
    }

    /**
     * The heart of the filter. Text is many small glyphs of a shared height;
     * artwork inside a bright region is a handful of features of wildly
     * different sizes.
     *
     * Returns the glyph-height uniformity share when the holes look like text,
     * or null when they don't. The share feeds the two-tier solidity rule: a
     * less-solid outline is only trusted with strong text evidence.
     */
    private fun holesLookLikeText(
        contours: List<MatOfPoint>,
        hierarchy: Mat,
        firstChild: Int,
        blobArea: Double,
    ): Double? {
        val heights = ArrayList<Int>()
        var inkArea = 0.0
        var child = firstChild

        while (child != -1) {
            val holeArea = Imgproc.contourArea(contours[child])
            if (holeArea > blobArea * MAX_HOLE_RATIO) return null // a drawing, not a glyph
            inkArea += holeArea
            heights.add(Imgproc.boundingRect(contours[child]).height)
            if (heights.size > MAX_GLYPH_COUNT) return null
            child = hierarchy.get(0, child)?.get(0)?.toInt() ?: -1
        }

        if (heights.size < MIN_GLYPH_COUNT) return null
        val inkRatio = inkArea / blobArea
        if (inkRatio < MIN_INK_RATIO || inkRatio > MAX_INK_RATIO) return null

        heights.sort()
        val median = heights[heights.size / 2].toDouble()
        if (median <= 0.0) return null
        val uniform = heights.count {
            it >= median * GLYPH_HEIGHT_MIN_FACTOR && it <= median * GLYPH_HEIGHT_MAX_FACTOR
        }
        val share = uniform.toDouble() / heights.size
        return if (share >= MIN_UNIFORM_SHARE) share else null
    }

    /** area / convexHull area — how blob-like (vs ragged) the outline is. */
    private fun solidity(contour: MatOfPoint): Double {
        val hullIndices = MatOfInt()
        var hull: MatOfPoint? = null
        try {
            Imgproc.convexHull(contour, hullIndices)
            val points = contour.toArray()
            val indices = hullIndices.toArray()
            if (indices.size < 3) return 0.0
            hull = MatOfPoint(*Array(indices.size) { points[indices[it]] })
            val hullArea = Imgproc.contourArea(hull)
            return if (hullArea > 0.0) Imgproc.contourArea(contour) / hullArea else 0.0
        } finally {
            hull?.release()
            hullIndices.release()
        }
    }

    /**
     * Share of the blob's pixels that are pure paper white. Kills screentoned
     * and softly shaded artwork, which is bright enough to threshold but is
     * nowhere near uniform.
     */
    private fun whitePurity(contour: MatOfPoint, bounds: Rect, pureWhite: Mat): Double {
        val localMask = Mat.zeros(bounds.size(), CvType.CV_8UC1)
        val intersection = Mat()
        var region: Mat? = null
        try {
            // Draw the blob into a bbox-local mask by offsetting it to the origin.
            Imgproc.drawContours(
                localMask,
                listOf(contour),
                -1,
                Scalar(255.0),
                -1,
                Imgproc.LINE_8,
                Mat(),
                Int.MAX_VALUE,
                Point(-bounds.x.toDouble(), -bounds.y.toDouble())
            )
            val blobPixels = Core.countNonZero(localMask)
            if (blobPixels == 0) return 0.0

            region = pureWhite.submat(bounds)
            Core.bitwise_and(region, localMask, intersection)
            return Core.countNonZero(intersection).toDouble() / blobPixels
        } finally {
            region?.release()
            intersection.release()
            localMask.release()
        }
    }
}
