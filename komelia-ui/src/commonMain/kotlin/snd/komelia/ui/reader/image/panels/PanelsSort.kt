package snd.komelia.ui.reader.image.panels

import androidx.compose.ui.unit.IntSize
import snd.komelia.image.ImageRect
import snd.komelia.settings.model.PagedReadingDirection
import snd.komelia.settings.model.PagedReadingDirection.LEFT_TO_RIGHT
import snd.komelia.settings.model.PagedReadingDirection.RIGHT_TO_LEFT
import kotlin.math.max
import kotlin.math.min

/**
 * Reading order for detected panels: a recursive XY-cut with a tolerant
 * horizontal split.
 *
 * This replaces ~200 lines of pairwise insertion heuristics. That code decided
 * each panel's position by comparing it to its neighbours — closest-left,
 * closest-right, closest-top, six overlap branches — and the result depended on
 * the order panels arrived in. Measured against the shipped version on 440
 * pages of three volumes (Gintama, Dragon Ball, Wunderwaffen), this changes the
 * order on 27 of them, and every page inspected by eye came out better or equal.
 *
 * The bench that produced those numbers lives in `_bubble-bench`
 * (`xycut2.py`, `tolcompare.py`, `panelvs2.py`); its first metric — counting
 * backward vertical jumps — was **discarded as invalid**: descending a whole
 * column produces no backward jump, so it rewarded the exact failure being
 * hunted. Only page-by-page inspection settled it.
 */
fun sortPanels(
    panels: List<ImageRect>,
    imageSize: IntSize,
    readingDirection: PagedReadingDirection
): List<ImageRect> {
    if (panels.size <= 1) return panels

    // The algorithm always reads right-to-left. Left-to-right is the same
    // problem mirrored, so flip the coordinates going in and the panels coming
    // out, rather than threading a direction through every recursion.
    val working = when (readingDirection) {
        RIGHT_TO_LEFT -> panels
        LEFT_TO_RIGHT -> panels.map { it.flipX(imageSize.width) }
    }

    val ordered = order(dedup(working))

    return when (readingDirection) {
        RIGHT_TO_LEFT -> ordered
        LEFT_TO_RIGHT -> ordered.map { it.flipX(imageSize.width) }
    }
}

private fun ImageRect.flipX(imageWidth: Int) = ImageRect(
    left = imageWidth - right,
    top = top,
    right = imageWidth - left,
    bottom = bottom,
)

/**
 * Largest fraction of a panel's own height that a horizontal cut at [position]
 * would slice off. 0 when the cut crosses nothing.
 *
 * A fraction of the panel, not of the page: two hundred pixels taken off a tall
 * panel and off a short one are not the same mistake.
 */
private const val MAX_CLIP_FRACTION = 0.30f

/** Two boxes overlapping this much are the same panel emitted twice. */
private const val DUPLICATE_IOU = 0.6f

/**
 * Drops near-duplicate boxes, keeping the larger.
 *
 * The detector emits some panels twice, once per class, and nothing downstream
 * ran NMS — so a duplicated panel was visited twice in a row. Rare in practice
 * (one page in 492 measured) but free to prevent.
 */
private fun dedup(panels: List<ImageRect>): List<ImageRect> {
    val kept = mutableListOf<ImageRect>()
    for (panel in panels.sortedByDescending { it.width.toLong() * it.height }) {
        if (kept.none { iou(it, panel) >= DUPLICATE_IOU }) kept.add(panel)
    }
    return kept
}

private fun iou(a: ImageRect, b: ImageRect): Float {
    val w = min(a.right, b.right) - max(a.left, b.left)
    val h = min(a.bottom, b.bottom) - max(a.top, b.top)
    if (w <= 0 || h <= 0) return 0f
    val intersection = w.toLong() * h
    val union = a.width.toLong() * a.height + b.width.toLong() * b.height - intersection
    return if (union <= 0) 0f else intersection.toFloat() / union
}

private fun order(panels: List<ImageRect>): List<ImageRect> {
    if (panels.size <= 1) return panels

    horizontalCut(panels)?.let { position ->
        val top = panels.filter { it.centerY < position }
        val bottom = panels.filter { it.centerY >= position }
        if (top.isNotEmpty() && bottom.isNotEmpty()) return order(top) + order(bottom)
    }

    cleanGap(panels.map { it.left to it.right })?.let { position ->
        val left = panels.filter { it.centerX < position }
        val right = panels.filter { it.centerX >= position }
        if (left.isNotEmpty() && right.isNotEmpty()) return order(right) + order(left)
    }

    // Panels overlap on both axes and no cut separates them. Fall back to a
    // stable positional order: banded by top edge, right-to-left inside a band.
    // The band is a third of the median height so slightly staggered panels
    // still group as one row.
    val medianHeight = panels.map { it.height }.sorted()[panels.size / 2]
    val band = max(medianHeight / 3, 1)
    return panels.sortedWith(compareBy({ it.top / band }, { -it.left }))
}

/**
 * Where to split the group into an upper and a lower half, or null.
 *
 * Prefers a clean gutter. Failing that — and this is the whole point — accepts
 * a cut that clips panels, as long as no panel loses more than
 * [MAX_CLIP_FRACTION] of its height to the wrong side. Without that tolerance a
 * single panel hanging slightly below its row blocks the row split entirely and
 * the group falls through to a vertical cut, which reads a whole column
 * top-to-bottom before moving across. Measured on Gintama page 011: the
 * middle-left panel hangs 211 px into the row below, and the strict cut turned a
 * plain three-row page into two columns.
 */
private fun horizontalCut(panels: List<ImageRect>): Float? {
    cleanGap(panels.map { it.top to it.bottom })?.let { return it }

    // Candidates are panel bottoms: cutting there keeps the panel above whole.
    var best: Float? = null
    var bestClip = Float.MAX_VALUE
    for (candidate in panels.map { it.bottom }.distinct().sorted()) {
        val position = candidate.toFloat()
        if (panels.none { it.bottom <= candidate } || panels.none { it.top >= candidate }) continue

        var worstClip = 0f
        for (panel in panels) {
            if (panel.top >= candidate || panel.bottom <= candidate) continue
            val smallerSide = min(candidate - panel.top, panel.bottom - candidate)
            worstClip = max(worstClip, smallerSide.toFloat() / panel.height)
        }
        if (worstClip >= MAX_CLIP_FRACTION) continue
        if (worstClip < bestClip) {
            bestClip = worstClip
            best = position
        }
    }
    return best
}

/** Midpoint of the widest band covered by no interval, or null if there is none. */
private fun cleanGap(intervals: List<Pair<Int, Int>>): Float? {
    if (intervals.size < 2) return null
    val sorted = intervals.sortedBy { it.first }
    var runningEnd = sorted.first().second
    var bestGap = 0
    var bestPosition: Float? = null
    for ((start, end) in sorted.drop(1)) {
        if (start > runningEnd) {
            val gap = start - runningEnd
            if (gap > bestGap) {
                bestGap = gap
                bestPosition = (runningEnd + start) / 2f
            }
        }
        runningEnd = max(runningEnd, end)
    }
    return bestPosition
}

private val ImageRect.centerX get() = left + width / 2f
private val ImageRect.centerY get() = top + height / 2f
