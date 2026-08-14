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
    pageWidth: Int = 0,
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
        else currentSegments
            .flatMap { splitIntoColumns(it) }
            .flatMap { peelOversizedLines(it) }
            // A caption in large lettering can be wide enough to overlap two
            // small bubbles either side of it, and a line that bridges two
            // groups joins them — right for a bubble with one long line, wrong
            // here. Separating the sizes gives the column pass below two groups
            // it can actually tell apart.
            .flatMap { splitByLineSize(it) }
            // Again, because a sound effect drawn across the page is wide enough
            // to be the only thing two bubbles had in common: taking it out
            // leaves them as two groups that no longer touch.
            .flatMap { splitIntoColumns(it) }
            // Last of the five, because each of the steps above takes something
            // out of a block, and what is left can be wide and mostly empty when
            // the whole of it was not. A volume title across the top of a page,
            // with a line of blurb and two stray runs of katakana under it,
            // fills 111% of the block all four share; peel the title off and the
            // three that remain fill 30% of a rect nearly half the page wide.
            .flatMap { undoIfWideAndSparse(it, pageWidth) }

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
    // Narrowest first, so the columns exist before anything wide is offered to
    // them. This ordering is what makes the cap below mean anything: without a
    // cap the accumulation is a union-find and the order cannot matter, and
    // sorted by left edge — as it was — the wide line arrives before the groups
    // it would bridge exist, so it never reaches more than one and the cap
    // never fires. Measured over the eight replayed volumes: sorting by width
    // with no cap gives exactly the old numbers, and capping under the old sort
    // gave exactly the old numbers too. Only the two together move anything.
    for (rect in lines.keys.sortedBy { it.width }) {
        // Every group this line reaches, not just the first: a wide line can
        // bridge two groups that do not touch each other, and they are then one
        // bubble. Taking only the first would leave the far side split off.
        val hits = groups.filter { group -> group.any { sharesColumn(it, rect) } }
        if (hits.isEmpty() || hits.size > MAX_BRIDGED_GROUPS) {
            // Past the limit the line is not part of any of them, it is lying
            // across all of them. Page 16 of Servant x Service volume 4: three
            // columns of dialogue at 91-251, 271-347 and 389-490, none of them
            // touching each other, and one caption spanning 130-456 that joins
            // all three into a block filling 51% of a quarter of the page.
            // Unlike the Trigun case the lettering is all one size, so nothing
            // separates them except how many groups the line reaches.
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

/**
 * How much of the narrower of two lines has to sit under the wider one before
 * they count as belonging to the same stack.
 *
 * Swept over a 203-page volume: 0.20 splits 36 blocks, 0.30 splits 51, 0.35 and
 * 0.40 both split 58, 0.50 splits 66. No cliff anywhere, so the exact value is
 * not load-bearing.
 */
private const val MIN_COLUMN_OVERLAP = 0.35f

/**
 * How many separate groups one line may join before it counts as lying across
 * them rather than belonging to them.
 *
 * Two is the bubble with one long line, which is why bridging exists at all: a
 * short centred line and a wide one under it are one bubble and have to stay
 * whole. Three is a caption over columns that have nothing else in common.
 *
 * One is tempting and wrong. Over the eight volumes it looks far better on the
 * fault counts — the big sparse blocks drop from 15 to 2 and the many-lined
 * ones from 83 to 14 — but it adds 901 blocks, and 836 of those are single
 * lines. That is not unwelding bubbles, it is stranding their lines. Two adds
 * 56 blocks of which 38 hold three lines or more.
 */
private const val MAX_BRIDGED_GROUPS = 2

/**
 * Whether two lines are stacked over each other rather than merely touching.
 *
 * Sharing a single column of pixels used to be enough, and that is how drawn
 * lettering next to a bubble gets read as a word of the sentence. On page 17 of
 * 100 Girlfriends, QUIVER is lettered twice down the right of a bubble to show
 * the character shaking. Its box laps over the widest line of dialogue by 18
 * pixels out of its own 172, which was enough to count as the same stack, and
 * sorting by top edge then dropped it into the middle of the sentence:
 *
 *     I'M THE WORST! I'VE ALREADY HURT MY PRECIOUS QUIVER SOULMATE!
 *     GET YOUR ACT QUIVER TOGETHER, DANG IT!!
 *
 * No translator recovers that, and quiver in French becomes the thing you keep
 * arrows in. Lettering that illustrates a gesture is not part of the dialogue
 * and must never reach the translator.
 *
 * Four pixels of overlap out of 135 was also enough to join 'SORRY.' to
 * 'NOTHING, REALLY.' — two separate bubbles, translated as one sentence.
 */
private fun sharesColumn(a: Rect, b: Rect): Boolean {
    val overlap = min(a.right, b.right) - max(a.left, b.left)
    if (overlap <= 0f) return false
    return overlap >= min(a.width, b.width) * MIN_COLUMN_OVERLAP
}

/** Below this share of the page width, a block is small enough to trust. */
private const val WIDE_BLOCK_RATIO = 0.40f

/** Fill a wide block has to reach to be believed. */
private const val WIDE_BLOCK_MIN_FILL = 0.70f

/**
 * Takes apart a block that ended up spanning much of the page while its own
 * lettering covers little of it.
 *
 * Measured over 2019 blocks of a real volume: the twelve wide blocks that are
 * genuinely one piece of text — covers, the contents page, a full-width shout
 * like "SHE'S IN A REALM FAR BEYOND MY REACH!!" — all fill 73% or more, and the
 * eight that are several bubbles or a chain of sound effects welded together
 * all fill 67% or less. Narrow blocks are left alone at any fill: a normal
 * bubble runs 47% to 61% and splitting those would break far more than this
 * fixes.
 *
 * Undone all the way back to single lines rather than to something in between:
 * 'SKITTER SKITTER SITTER SKITTER' becomes four one-word blocks, which the
 * sound-effect rule then leaves on the artwork, and that is the outcome wanted.
 */
private fun undoIfWideAndSparse(segment: Segment, pageWidth: Int): List<Segment> {
    if (pageWidth <= 0 || segment.elements.size < 2) return listOf(segment)
    if (segment.rect.width < pageWidth * WIDE_BLOCK_RATIO) return listOf(segment)
    if (fillRatio(segment.elements, segment.rect) >= WIDE_BLOCK_MIN_FILL) return listOf(segment)

    return segment.elements.groupBy { it.blockRect }
        .map { (rect, elements) -> Segment(rect, elements.toMutableList()) }
}

/** How much taller than its neighbours a line has to be to be drawn lettering. */
private const val OVERSIZED_LINE_RATIO = 4.0f

/**
 * Pulls a sound effect back off the bubble it was welded to.
 *
 * [undoIfWideAndSparse] cannot see this one: 'eRRRRAATT eRRRAATTT' drawn across
 * a panel next to a caption fills 71% of its block, because letters that big
 * cover a lot of ground. What gives it away is that dialogue in a bubble is all
 * set at one size, so a line towering over its neighbours was not lettered with
 * them.
 *
 * Measured over 2841 multi-line blocks of four volumes: 96% of them have their
 * tallest line within 1.5x of the median of the others, and only 34 reach 4x.
 * Of those 34 nearly every one is a sound effect stuck to a caption — FWOOSH,
 * KLANG, THWACK, WHOOOM, BLOF BLOP — and the rest are title pages, where taking
 * the logo off the credits is right anyway.
 *
 * Only the oversized lines leave. Undoing the whole block the way the sparse
 * rule does would chop the dialogue into one block per line and translate it a
 * fragment at a time.
 */
private fun peelOversizedLines(segment: Segment): List<Segment> {
    val lines = segment.elements.groupBy { it.blockRect }
    if (lines.size < 2) return listOf(segment)

    val rects = lines.keys.toList()
    val oversized = rects.filterIndexed { index, rect ->
        val others = rects.filterIndexed { i, _ -> i != index }.map { it.height }.sorted()
        rect.height >= others[others.size / 2] * OVERSIZED_LINE_RATIO
    }
    if (oversized.isEmpty() || oversized.size == rects.size) return listOf(segment)

    val kept = rects.toSet() - oversized.toSet()
    val keptSegment = Segment(
        rect = Rect(
            left = kept.minOf { it.left },
            top = kept.minOf { it.top },
            right = kept.maxOf { it.right },
            bottom = kept.maxOf { it.bottom },
        ),
        elements = kept.flatMap { lines.getValue(it) }.toMutableList(),
    )
    return oversized.map { Segment(it, lines.getValue(it).toMutableList()) } + keptSegment
}

/**
 * How much taller one line has to be than the next shorter one before they
 * count as two different pieces of lettering rather than one.
 */
private const val SIZE_BREAK_RATIO = 1.6f

/**
 * Splits a block into groups of lines set at the same size.
 *
 * Page 51 of Trigun 01 is the case this was written for. One block, 542x946,
 * filling 53%, painted as an opaque panel over a quarter of the page, holding
 * three separate bubbles:
 *
 *     MERYL STRIFE, ... SOCIETY.   x  894-1427   line height 86-94
 *     FREAKIN' ... BITS!           x 1258-1431   line height 34-39
 *     DO YOU ... MEAT?             x  889-1016   line height 34-41
 *
 * The last two sit side by side and [splitIntoColumns] would have separated
 * them on its own — but the first is wide enough to overlap both, and a line
 * that bridges two groups joins them, deliberately, because that is how a
 * bubble with one long line stays whole. Nothing on the x axis can tell the two
 * situations apart.
 *
 * Nor can anything on the y axis: the lines of all three overlap vertically,
 * every interval between them is negative, and there is exactly one positive
 * gap in the whole block. Bubbles drawn touching leave no gap to find.
 *
 * What does separate them is that a bubble is lettered at one size. Splitting
 * on the biggest jump in line height leaves the caption whole and hands the two
 * small bubbles back to the column pass, which then does see them apart.
 *
 * Unlike [peelOversizedLines] this keeps each group together, because a group
 * here is a bubble rather than a sound effect: peeling these five lines apart
 * would translate one caption as five fragments.
 */
private fun splitByLineSize(segment: Segment): List<Segment> {
    val lines = segment.elements.groupBy { it.blockRect }
    if (lines.size < 2) return listOf(segment)

    val bySize = lines.keys.sortedBy { it.height }
    // The first jump big enough to be a change of lettering, taken from the
    // smallest up. One cut, not many: a block holding three sizes is rare, and
    // whatever is left over comes back through here on the next pass anyway.
    val breakAt = (1 until bySize.size).firstOrNull { index ->
        val below = bySize[index - 1].height
        below > 0f && bySize[index].height >= below * SIZE_BREAK_RATIO
    } ?: return listOf(segment)

    return listOf(bySize.subList(0, breakAt), bySize.subList(breakAt, bySize.size))
        .map { group ->
            Segment(
                rect = Rect(
                    left = group.minOf { it.left },
                    top = group.minOf { it.top },
                    right = group.maxOf { it.right },
                    bottom = group.maxOf { it.bottom },
                ),
                elements = group.flatMap { lines.getValue(it) }.toMutableList(),
            )
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
