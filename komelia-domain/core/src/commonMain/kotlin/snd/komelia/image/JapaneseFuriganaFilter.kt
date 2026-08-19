package snd.komelia.image

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Drops the furigana — the small kana printed alongside a kanji to give its
 * reading — before anything downstream sees it.
 *
 * Furigana is not text the reader wants translated: it spells out a word that
 * is already on the page in kanji, right next to it. Sent to the translator it
 * arrives as its own balloon of pure phonetics and comes back as nonsense, and
 * the reader gets a panel of gibberish painted beside the line it belongs to.
 *
 * ## Why this only started mattering now
 *
 * The detector has to resolve characters roughly half the size of the body text
 * to see furigana at all. On the 835x1200 scans this fork was developed against
 * it simply never did — measured over 601 boxes of Kyou kara Hitman, this
 * filter fires **once**, on `サラリー`, and that is a piece of サラリーマン the
 * detector had already cut in two. On a 1351x1920 volume the same 25 pages
 * yield 405 boxes of which 105 are furigana. So the problem arrives with the
 * better scan, and a filter written for the better scan must stay inert on the
 * worse one — which is what that 1-in-601 number is there to guarantee.
 *
 * ## The rule, and which part of it does the work
 *
 * Three conditions, all required:
 *
 *  1. the text is kana only,
 *  2. the box is thinner than [THIN_OF_MEDIAN] of the page's median box,
 *  3. a box at least [COMPANION_RATIO] times thicker runs alongside it, in the
 *     same orientation, within [GAP_OF_WIDTH] of its own width, overlapping it
 *     by [OVERLAP_MIN] along the reading axis.
 *
 * The third is the one that matters. A short balloon that happens to be all
 * kana — はい, うん, そうか — satisfies the first two and would be thrown away
 * without it; what it does not have is a thicker column glued to its side.
 * Furigana always does, because that column is the word it is annotating.
 *
 * Measured against boxes recovered from a real volume: 82% of the furigana
 * caught. The 29 boxes a first pass counted as false positives turned out on
 * inspection to be furigana too — ろくだいきぞく for 六大貴族, しゅじんこう
 * for 主人公, りゅうがくさき for 留学先 — so the ground truth was wrong, not
 * the rule.
 *
 * Applied BEFORE [mergeOcrBoxes], for the same reason [OcrScriptFilter] is:
 * once the merge has welded a furigana column into the balloon beside it, there
 * is nothing left to drop.
 */
object JapaneseFuriganaFilter {

    /** A furigana box against the median box of its own page. */
    private const val THIN_OF_MEDIAN = 0.75f

    /** How much thicker the annotated word has to be. */
    private const val COMPANION_RATIO = 1.6f

    /** How far the companion may sit, counted in furigana widths. */
    private const val GAP_OF_WIDTH = 1.8f

    /** How much of the shorter side the two must share along the reading axis. */
    private const val OVERLAP_MIN = 0.45f

    /**
     * Punctuation and the long vowel mark ride along with kana rather than
     * disqualifying a box: furigana carries ー constantly, and a trailing 、is
     * common enough that requiring pure kana would miss a fifth of them.
     */
    private const val KANA_PUNCTUATION = "…‥・ー～。、！？!?「」"

    fun apply(boxes: List<OcrElementBox>): List<OcrElementBox> {
        if (boxes.size < 2) return boxes
        val median = medianThickness(boxes)
        if (median <= 0f) return boxes
        return boxes.filterNot { isFurigana(it, boxes, median) }
    }

    private fun isFurigana(box: OcrElementBox, all: List<OcrElementBox>, median: Float): Boolean {
        if (!isKanaOnly(box.text)) return false
        val thin = box.imageRect.thin()
        if (thin <= 0f || thin >= median * THIN_OF_MEDIAN) return false
        return all.any { other -> other !== box && annotates(other.imageRect, box.imageRect, thin) }
    }

    /** Whether [candidate] is the word [furigana] is the reading of. */
    private fun annotates(candidate: Rect, furigana: Rect, thin: Float): Boolean {
        if (candidate.thin() < thin * COMPANION_RATIO) return false
        if (candidate.isVertical() != furigana.isVertical()) return false
        val gap: Float
        val shared: Float
        if (furigana.isVertical()) {
            gap = min(abs(furigana.left - candidate.right), abs(candidate.left - furigana.right))
            shared = overlap(furigana.top, furigana.bottom, candidate.top, candidate.bottom)
        } else {
            gap = min(abs(furigana.top - candidate.bottom), abs(candidate.top - furigana.bottom))
            shared = overlap(furigana.left, furigana.right, candidate.left, candidate.right)
        }
        if (gap > thin * GAP_OF_WIDTH) return false
        val shorter = min(furigana.long(), candidate.long())
        return shorter > 0f && shared / shorter >= OVERLAP_MIN
    }

    private fun isKanaOnly(text: String): Boolean {
        val core = text.filterNot { it in KANA_PUNCTUATION || it.isWhitespace() }
        return core.isNotEmpty() && core.all { it.code in 0x3040..0x30FF }
    }

    /**
     * The median rather than the mean: one full-width shout is thicker than
     * everything else on the page and would drag a mean far enough that real
     * columns start looking thin.
     */
    private fun medianThickness(boxes: List<OcrElementBox>): Float {
        val sorted = boxes.map { it.imageRect.thin() }.filter { it > 0f }.sorted()
        if (sorted.isEmpty()) return 0f
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }

    private fun overlap(aStart: Float, aEnd: Float, bStart: Float, bEnd: Float): Float =
        max(0f, min(aEnd, bEnd) - max(aStart, bStart))

    private fun Rect.thin(): Float = min(width, height)
    private fun Rect.long(): Float = max(width, height)
    private fun Rect.isVertical(): Boolean = height >= width
}
