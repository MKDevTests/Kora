package snd.komelia.image

import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min

enum class ReadingDirection {
    LTR, RTL
}

/**
 * Groups the boxes a detector returns into bubbles, and puts them in reading
 * order.
 *
 * [vertical] is for Japanese lettering, which runs in columns read right to
 * left. Ordering those by their top edge first — the rule for lines of Latin
 * text — decides between two side-by-side columns on the pixel or two by which
 * their tops differ, which shuffles a bubble into nonsense: 「おいおい何で知ら
 * ないんだよ！？」 came out as 「だよ！？おいおい何で知らないん」.
 */
fun mergeOcrBoxes(
    boxes: List<OcrElementBox>,
    direction: ReadingDirection,
    vertical: Boolean = false,
): List<OcrElementBox> {
    if (boxes.isEmpty()) return boxes

    // Measured once, over the detected lines of the whole page, and never
    // again. It used to be recomputed from the two candidates on every pass,
    // which meant a segment that had swallowed one tall sound effect raised
    // its own tolerance and kept swallowing: on a 2025x2885 page one block
    // ended up 1758x1126, and the overlay paints an opaque panel over the
    // block rect — that is the giant black square over the artwork.
    val lineSize = calculateMedianShortSide(boxes)

    var currentSegments = boxes.groupBy { it.blockRect }
        .map { (rect, elements) -> Segment(rect, elements.toMutableList()) }
        .toMutableList()

    var hasMerged = true
    while (hasMerged) {
        hasMerged = false
        val nextSegments = mutableListOf<Segment>()
        val mergedIndices = mutableSetOf<Int>()

        for (i in currentSegments.indices) {
            if (i in mergedIndices) continue
            var segmentA = currentSegments[i]

            for (j in i + 1 until currentSegments.size) {
                if (j in mergedIndices) continue
                val segmentB = currentSegments[j]

                val horizontalGap = max(0f, max(segmentA.rect.left, segmentB.rect.left) - min(segmentA.rect.right, segmentB.rect.right))
                val verticalGap = max(0f, max(segmentA.rect.top, segmentB.rect.top) - min(segmentA.rect.bottom, segmentB.rect.bottom))

                // A bubble is a stack of lines: consecutive lines overlap on x
                // and are a line apart on y. Allowing the same slack on both
                // axes glued neighbouring bubbles together, and the block was
                // then read top-to-bottom across both of them at once — which
                // is how 'First I am year, in... Class my it name is I'm hmph!'
                // came out of three separate bubbles. Sideways is only for a
                // line the detector cut in two, so it gets much less room.
                val xOverlap = horizontalGap == 0f
                val yOverlap = verticalGap == 0f
                val shouldMerge = when {
                    xOverlap && yOverlap -> true
                    xOverlap -> verticalGap <= lineSize * STACKED_GAP_RATIO
                    yOverlap -> horizontalGap <= lineSize * INLINE_GAP_RATIO
                    // Diagonal neighbours are two different bubbles, or a
                    // bubble and a sound effect drawn over the artwork.
                    else -> false
                }

                if (shouldMerge) {
                    val newRect = Rect(
                        left = min(segmentA.rect.left, segmentB.rect.left),
                        top = min(segmentA.rect.top, segmentB.rect.top),
                        right = max(segmentA.rect.right, segmentB.rect.right),
                        bottom = max(segmentA.rect.bottom, segmentB.rect.bottom)
                    )
                    val elements = segmentA.elements + segmentB.elements
                    // Backstop for the chains the rules above still allow. Real
                    // lettering fills most of the box that contains it; a rect
                    // that is mostly empty is a panel of artwork with a few
                    // words scattered over it, and painting it opaque hides the
                    // drawing.
                    if (fillRatio(elements, newRect) < MIN_FILL_RATIO) continue

                    segmentA = Segment(newRect, elements.toMutableList())
                    mergedIndices.add(j)
                    hasMerged = true
                }
            }
            nextSegments.add(segmentA)
        }
        currentSegments = nextSegments
    }

    // Bubbles that still ended up in one segment are split back apart here.
    //
    // A threshold on how full the block is cannot do this — measured on real
    // pages, a correct bubble runs 47% to 61% full and the three-bubble block
    // that read as gibberish was 40%, so any cut-off costs more than it saves.
    // What does separate them is structure: the lines of one bubble all overlap
    // each other horizontally, and three bubbles side by side fall into three
    // groups that never touch.
    //
    // Not for vertical Japanese, where the columns of a single bubble are
    // exactly the disjoint groups this looks for.
    val finalSegments =
        if (vertical) currentSegments
        else currentSegments.flatMap { splitIntoColumns(it) }

    return finalSegments.flatMap { segment ->
        val unifiedBlockIndex = segment.elements.firstOrNull()?.blockIndex ?: 0
        
        // Group elements by their original segments to maintain internal order
        val originalSegments = segment.elements.groupBy { it.blockRect }
            .map { (rect, elements) -> 
                // Sort internal elements by line and element index
                rect to elements.sortedWith(compareBy({ it.lineIndex }, { it.elementIndex }))
            }
            .sortedWith { a, b ->
                val rectA = a.first
                val rectB = b.first

                if (vertical) {
                    // Columns first, rightmost one first; only then top to
                    // bottom, for a column the detector split in two.
                    val columnComparison = rectB.right.compareTo(rectA.right)
                    if (columnComparison != 0) return@sortedWith columnComparison
                    return@sortedWith rectA.top.compareTo(rectB.top)
                }

                // 1. Vertical order (higher is first)
                val verticalComparison = rectA.top.compareTo(rectB.top)
                if (verticalComparison != 0) return@sortedWith verticalComparison

                // 2. Horizontal order based on direction
                if (direction == ReadingDirection.RTL) {
                    rectB.right.compareTo(rectA.right) // Larger right is first
                } else {
                    rectA.left.compareTo(rectB.left) // Smaller left is first
                }
            }

        var currentLineOffset = 0
        originalSegments.flatMap { (_, elements) ->
            val maxLineIndex = elements.maxOfOrNull { it.lineIndex } ?: 0
            val updatedElements = elements.map { 
                it.copy(
                    blockRect = segment.rect, 
                    blockIndex = unifiedBlockIndex,
                    lineIndex = it.lineIndex + currentLineOffset
                )
            }
            currentLineOffset += maxLineIndex + 1
            updatedElements
        }
    }
}

/** Vertical room between two stacked lines of the same bubble, in line heights. */
private const val STACKED_GAP_RATIO = 1.0f

/** Horizontal room between two halves of one line the detector split. */
private const val INLINE_GAP_RATIO = 0.6f

/** How much of a merged block its own lettering has to cover. */
private const val MIN_FILL_RATIO = 0.30f

private data class Segment(
    val rect: Rect,
    val elements: MutableList<OcrElementBox>
)

/**
 * Splits a segment into groups of lines that overlap each other horizontally.
 *
 * One bubble is one group, whatever its shape, because its lines are stacked
 * over each other. Several bubbles that the merge chained together are several
 * groups, and come back out as separate blocks with their own panels — which is
 * also what stops the reading order interleaving them.
 */
private fun splitIntoColumns(segment: Segment): List<Segment> {
    val lines = segment.elements.groupBy { it.blockRect }
    if (lines.size < 2) return listOf(segment)

    // Transitive: A overlaps B and B overlaps C puts all three together, even
    // when A and C do not touch. A bubble with one short centred line still
    // comes out whole.
    val groups = mutableListOf<MutableList<Rect>>()
    for (rect in lines.keys.sortedBy { it.left }) {
        // Every group this line reaches, not just the first: a wide line can
        // bridge two groups that do not touch each other, and they are then one
        // bubble. Taking only the first would leave the far side split off.
        val hits = groups.filter { group ->
            group.any { it.left < rect.right && rect.left < it.right }
        }
        if (hits.isEmpty()) {
            groups.add(mutableListOf(rect))
        } else {
            val target = hits.first()
            target.add(rect)
            hits.drop(1).forEach { other ->
                target.addAll(other)
                groups.remove(other)
            }
        }
    }
    if (groups.size < 2) return listOf(segment)

    return groups.map { group ->
        val elements = group.flatMap { lines.getValue(it) }
        val rect = Rect(
            left = group.minOf { it.left },
            top = group.minOf { it.top },
            right = group.maxOf { it.right },
            bottom = group.maxOf { it.bottom },
        )
        Segment(rect, elements.toMutableList())
    }
}

/** Share of [rect] covered by the boxes of [elements], overlaps counted twice. */
private fun fillRatio(elements: List<OcrElementBox>, rect: Rect): Float {
    val area = rect.width * rect.height
    if (area <= 0f) return 1f
    val covered = elements.sumOf { (it.imageRect.width * it.imageRect.height).toDouble() }
    return (covered / area).toFloat()
}

private fun calculateMedianShortSide(elements: List<OcrElementBox>): Float {
    if (elements.isEmpty()) return 0f
    val shortSides = elements.map { min(it.imageRect.width, it.imageRect.height) }.sorted()
    return if (shortSides.size % 2 == 0) {
        (shortSides[shortSides.size / 2 - 1] + shortSides[shortSides.size / 2]) / 2
    } else {
        shortSides[shortSides.size / 2]
    }
}
