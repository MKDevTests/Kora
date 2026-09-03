package snd.komelia.grid

import kotlin.math.abs

/**
 * Page sizes offered for the card grids.
 *
 * Every value is a multiple of 60, which divides by 2, 3, 4, 5 and 6 — the
 * column counts a grid actually lays out between a phone in portrait and a
 * tablet in landscape. The list this replaces (20/50/100/200/500) did not:
 * measured on the real catalogue, 50 series on the 4 columns of a 753 dp tablet
 * left two holes on the last row of EVERY page, not just the last one.
 */
val pageLoadSizes = listOf(60, 120, 240, 480)

/**
 * Nearest offered size.
 *
 * Applied when reading the stored setting, so a value written before this list
 * changed still lands on a whole row instead of keeping the ragged layout
 * forever. Ties go to the smaller size — a page that loads less is the safer
 * side of an arbitrary choice.
 */
fun snapPageLoadSize(size: Int): Int =
    pageLoadSizes.minByOrNull { abs(it - size) } ?: size

/**
 * How many columns `GridCells.Adaptive(minSize)` lays out.
 *
 * Same arithmetic as the adaptive strategy, computed ahead of the grid because
 * the count has to be known *before* composing it to decide whether it divides
 * the page size. All sizes in pixels, as the grid measures them.
 */
fun adaptiveColumnCount(availablePx: Int, minSizePx: Int, spacingPx: Int): Int {
    if (availablePx <= 0 || minSizePx <= 0) return 1
    return maxOf((availablePx + spacingPx) / (minSizePx + spacingPx), 1)
}

/**
 * The column count that leaves no hole in the last row of a full page.
 *
 * A grid draws [pageSize] items over [adaptive] columns; unless one divides the
 * other, every full page ends on a ragged row. Dropping to the nearest divisor
 * removes the hole for the cost of slightly wider cards.
 *
 * Only ever removes columns, never adds any: the adaptive count guarantees each
 * cell is at least the card width the user chose, and adding a column would
 * break that guarantee to fix a cosmetic problem. And it moves by at most
 * [maxShift] column — past that the cards change size enough that the cure is
 * worse than the hole, so the ragged row stays.
 */
fun completeRowColumnCount(adaptive: Int, pageSize: Int, maxShift: Int = 1): Int {
    if (pageSize <= 0 || adaptive <= 1) return adaptive
    if (pageSize % adaptive == 0) return adaptive
    for (shift in 1..maxShift) {
        val candidate = adaptive - shift
        if (candidate >= 1 && pageSize % candidate == 0) return candidate
    }
    return adaptive
}
