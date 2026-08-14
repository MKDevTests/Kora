package snd.komelia.image

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The merge decides three things that have each shipped broken: which lines
 * belong to the same bubble, what order they read in, and how big the opaque
 * panel drawn over them gets. All three are plain geometry, so they belong here
 * rather than in a volume read on the tablet.
 *
 * Coordinates are taken from real pages via scripts/ocr-bench.
 */
class OcrMergeUtilsTest {

    private fun line(text: String, left: Int, top: Int, right: Int, bottom: Int, index: Int) =
        OcrElementBox(
            text = text,
            imageRect = Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()),
            blockRect = Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()),
            blockIndex = index,
            lineIndex = 0,
            elementIndex = 0,
        )

    /** Text of each merged block, in reading order. */
    private fun blocksOf(boxes: List<OcrElementBox>): List<String> =
        mergeOcrBoxes(boxes, ReadingDirection.LTR)
            .groupBy { it.blockIndex }
            .values
            .map { block -> block.sortedBy { it.lineIndex }.joinToString(" ") { it.text } }

    @Test
    fun `two bubbles side by side stay two blocks and each reads in order`() {
        // scripts/ocr-bench, page01: the detector returns these interleaved by
        // top edge, which is how three bubbles once came out as 'I am first
        // in... Year, class my it name is I'm hmph!'.
        val boxes = listOf(
            line("I DONT KNOW", 145, 128, 386, 165, 0),
            line("IM REALLY", 724, 128, 919, 168, 1),
            line("IF YOURE A GOD,", 144, 169, 444, 213, 2),
            line("SORRY! I WILL", 726, 172, 975, 209, 3),
            line("BUT YOURE A", 146, 216, 389, 253, 4),
            line("BE MORE", 725, 216, 893, 253, 5),
            line("MONSTER!", 146, 259, 337, 296, 6),
            line("CAREFUL.", 725, 256, 909, 297, 7),
        )

        val blocks = blocksOf(boxes)

        assertEquals(2, blocks.size, "the two bubbles must not become one block")
        assertTrue(
            "I DONT KNOW IF YOURE A GOD, BUT YOURE A MONSTER!" in blocks,
            "left bubble read out of order: $blocks",
        )
        assertTrue(
            "IM REALLY SORRY! I WILL BE MORE CAREFUL." in blocks,
            "right bubble read out of order: $blocks",
        )
    }

    @Test
    fun `one tall bubble stays a single block`() {
        val boxes = listOf(
            line("THE TWO SOULMATES", 296, 1150, 683, 1186, 0),
            line("WILL SURELY MEET", 296, 1195, 639, 1231, 1),
            line("AT A SPECIFIC", 296, 1240, 550, 1276, 2),
            line("MOMENT IN", 295, 1285, 497, 1322, 3),
            line("THEIR LIVES.", 296, 1330, 526, 1366, 4),
        )

        val blocks = blocksOf(boxes)

        assertEquals(1, blocks.size, "a bubble with ragged line widths was split: $blocks")
        assertEquals("THE TWO SOULMATES WILL SURELY MEET AT A SPECIFIC MOMENT IN THEIR LIVES.", blocks.single())
    }

    @Test
    fun `a sound effect out on the artwork does not join a bubble`() {
        val boxes = listOf(
            line("MONSTER!", 146, 259, 337, 296, 0),
            line("WHACK", 297, 804, 584, 875, 1),
        )

        val merged = mergeOcrBoxes(boxes, ReadingDirection.LTR)

        assertEquals(2, merged.map { it.blockIndex }.distinct().size)
    }

    @Test
    fun `a merged block never grows past the lines it contains`() {
        // The panel is painted over blockRect, so this is the giant black
        // rectangle across the drawing, expressed as a test.
        val boxes = listOf(
            line("I DONT KNOW", 145, 128, 386, 165, 0),
            line("IM REALLY", 724, 128, 919, 168, 1),
            line("WHACK", 297, 804, 584, 875, 2),
        )

        val merged = mergeOcrBoxes(boxes, ReadingDirection.LTR)

        merged.forEach { box ->
            assertTrue(
                box.blockRect.height <= 300f,
                "block ${box.blockIndex} spans ${box.blockRect.height}px vertically: ${box.text}",
            )
        }
    }
}
