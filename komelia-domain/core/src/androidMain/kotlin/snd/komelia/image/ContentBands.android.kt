package snd.komelia.image

import android.graphics.Bitmap
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import snd.komelia.image.AndroidBitmap.toBitmap
import kotlin.math.abs

private val logger = KotlinLogging.logger {}

/**
 * Width the page is squashed to before profiling. A row's ink fraction doesn't
 * need horizontal detail, and 64px makes even a 2752px-tall strip trivial to
 * scan.
 */
private const val PROFILE_WIDTH = 64

/** A pixel counts as ink when it differs from the background by more than this. */
private const val BG_TOLERANCE = 28

/** A row is "empty" below this fraction of ink pixels. */
private const val EMPTY_ROW_SCORE = 0.02f

/**
 * Empty runs shorter than this fraction of the page WIDTH are NOT gutters —
 * they're the blank rows between lines of text. Filling them first (a vertical
 * closing) is what stops a bubble floating in a white gutter from being shredded
 * into slivers, which would make a skip-the-gutter scroll jump straight over it.
 *
 * Scaled by width, NOT height: a gutter is a design element sized relative to the
 * strip's width, while the height of a webtoon page is arbitrary (a single page
 * can be 2000px or 15000px). Keying it to height made the threshold grow with the
 * strip until the closing swallowed every real gutter, collapsing the page to one
 * block — which silently disabled the whole feature.
 *
 * Calibrated on real strips (720px wide): intra-content gaps cluster at 30-100px
 * while true panel gutters measured 112-1748px, so ~110px on 720px ≈ 0.15.
 */
private const val MIN_GUTTER_WIDTH_FRACTION = 0.15f

/** Blocks thinner than this fraction of the page width are absorbed into the previous one. */
private const val MIN_BLOCK_WIDTH_FRACTION = 0.08f

actual suspend fun detectContentBands(
    image: KomeliaImage,
): List<ClosedFloatingPointRange<Float>> = withContext(Dispatchers.Default) {
    runCatching { analyse(image) }
        .onFailure { android.util.Log.w("BANDS", "detection failed; falling back to fixed scroll", it) }
        .getOrDefault(emptyList())
}

private fun analyse(image: KomeliaImage): List<ClosedFloatingPointRange<Float>> {
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
        val height = source.height
        if (height < 8) return emptyList()

        // Squash horizontally only: every row keeps its own ink, so the profile
        // is unchanged while the scan gets ~10x cheaper.
        val small = Bitmap.createScaledBitmap(source, PROFILE_WIDTH, height, true)
        val pixels = IntArray(PROFILE_WIDTH * height)
        small.getPixels(pixels, 0, PROFILE_WIDTH, 0, 0, PROFILE_WIDTH, height)
        if (small !== source) small.recycle()

        val luma = IntArray(PROFILE_WIDTH * height) { i ->
            val p = pixels[i]
            // Rec. 601 luma, integer maths.
            (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        }

        // Background = median of the left/right edge columns. Works whether the
        // strip is white, black or a flat colour.
        val edge = ArrayList<Int>(height * 4)
        for (y in 0 until height) {
            val row = y * PROFILE_WIDTH
            edge.add(luma[row])
            edge.add(luma[row + 1])
            edge.add(luma[row + PROFILE_WIDTH - 2])
            edge.add(luma[row + PROFILE_WIDTH - 1])
        }
        edge.sort()
        val background = edge[edge.size / 2]

        val minInk = (PROFILE_WIDTH * EMPTY_ROW_SCORE).coerceAtLeast(1f)
        val empty = BooleanArray(height) { y ->
            var ink = 0
            val row = y * PROFILE_WIDTH
            for (x in 0 until PROFILE_WIDTH) {
                if (abs(luma[row + x] - background) > BG_TOLERANCE) ink++
            }
            ink < minInk
        }

        // Vertical closing: an empty run too short to be a gutter is content.
        val minGutter = (source.width * MIN_GUTTER_WIDTH_FRACTION).toInt().coerceAtLeast(2)
        var y = 0
        while (y < height) {
            if (empty[y]) {
                val start = y
                while (y < height && empty[y]) y++
                if (y - start < minGutter) java.util.Arrays.fill(empty, start, y, false)
            } else y++
        }

        // Content blocks = what is left between the (now genuine) gutters.
        val minBlock = (source.width * MIN_BLOCK_WIDTH_FRACTION).toInt().coerceAtLeast(2)
        val blocks = ArrayList<IntRange>()
        y = 0
        while (y < height) {
            if (!empty[y]) {
                val start = y
                while (y < height && !empty[y]) y++
                val last = blocks.lastOrNull()
                if (y - start < minBlock && last != null) {
                    blocks[blocks.size - 1] = last.first..(y - 1)   // absorb sliver
                } else {
                    blocks.add(start..(y - 1))
                }
            } else y++
        }

        val h = height.toFloat()
        android.util.Log.i(
            "BANDS",
            "page ${source.width}x$height bg=$background minGutter=$minGutter blocks=${blocks.size} " +
                blocks.take(12).joinToString { "${it.first}-${it.last}" }
        )

        // A single block spanning the page tells the scroller nothing.
        if (blocks.size < 2) return emptyList()

        return blocks.map { (it.first / h)..((it.last + 1) / h) }
    } finally {
        if (ownsSource) source.recycle()
        if (ownsDecoded) decoded.recycle()
    }
}
