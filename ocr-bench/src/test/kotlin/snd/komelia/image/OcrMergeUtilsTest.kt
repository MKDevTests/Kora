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
    fun `a sound effect touching a bubble is peeled back off it`() {
        // Batman Arkham City page 062: SKRREEE is drawn across the panel and its
        // box stops two pixels above the bubble under it, so the two merge. The
        // block then fills 80% of its rect — big letters cover a lot — and the
        // sparse rule cannot see anything wrong with it.
        val boxes = listOf(
            line("SKRREEE", 611, 1740, 1562, 2112, 0),
            line("QUAND", 837, 2114, 930, 2145, 1),
            line("ON TE RAMÈ-", 799, 2140, 976, 2173, 2),
            line("NERA AVEC LES", 784, 2166, 990, 2202, 3),
            line("ARMES.", 834, 2194, 939, 2230, 4),
        )

        val blocks = blocksOf(boxes)

        assertEquals(2, blocks.size, "the sound effect stayed welded to the bubble: $blocks")
        assertTrue("SKRREEE" in blocks, "the sound effect was not left on its own: $blocks")
        assertTrue(
            "QUAND ON TE RAMÈ- NERA AVEC LES ARMES." in blocks,
            "the bubble came apart with it: $blocks",
        )
    }

    @Test
    fun `a bubble with one emphasised line is left whole`() {
        // Lettering one line larger for emphasis is normal inside a bubble, and
        // peeling it would translate the sentence in two pieces.
        val boxes = listOf(
            line("I DONT KNOW", 145, 128, 386, 165, 0),
            line("IF YOURE A GOD,", 144, 169, 444, 213, 1),
            line("BUT YOURE A", 146, 216, 389, 253, 2),
            line("MONSTER!", 146, 259, 337, 320, 3),
        )

        val blocks = blocksOf(boxes)

        assertEquals(1, blocks.size, "an emphasised last line broke the bubble apart: $blocks")
    }

    @Test
    fun `what is left after peeling a title off is judged again`() {
        // Gunslinger Girl 02 page 005. The volume title is tall enough that the
        // block all four lines share fills 111% of it, so the wide-and-sparse
        // rule rightly leaves it alone. Peel the title and the three that remain
        // fill 30% of a rect 46% of the page wide — which is only visible if
        // that rule runs after the peel, not before it.
        val boxes = listOf(
            line("GUNSLINGERGIRL.", 136, 166, 1423, 369, 0),
            line("The girl has a mechanical body.", 285, 190, 1034, 220, 1),
            line("ガンスリンガ", 314, 339, 679, 375, 2),
            line("ガ一ル", 818, 338, 987, 378, 3),
        )

        val merged = mergeOcrBoxes(boxes, ReadingDirection.LTR, pageWidth = 1600)

        assertEquals(
            4,
            merged.map { it.blockIndex }.distinct().size,
            "lines scattered over the artwork were left under one panel",
        )
    }

    @Test
    fun `lettering drawn beside a bubble never lands inside the sentence`() {
        // 100 Girlfriends 01 page 017. QUIVER is lettered twice down the right
        // of the bubble to show the character shaking. Both boxes lap over the
        // dialogue by a tenth to a quarter of their own width, which was enough
        // to count as the same stack, and sorting by top edge then dropped them
        // into the middle of the sentence.
        val boxes = listOf(
            line("I'M THE", 481, 2386, 652, 2445, 0),
            line("WORST!", 482, 2429, 649, 2480, 1),
            line("I'VE", 519, 2467, 615, 2513, 2),
            line("ALREADY", 474, 2501, 660, 2552, 3),
            line("HURT MY", 471, 2535, 661, 2590, 4),
            line("PRECIOUS", 461, 2572, 672, 2624, 5),
            line("SOULMATE!", 453, 2611, 679, 2663, 6),
            line("QUIVER", 661, 2610, 833, 2719, 7),
            line("GET", 352, 2661, 446, 2715, 8),
            line("YOUR ACT", 299, 2703, 499, 2751, 9),
            line("TOGETHER,", 289, 2738, 499, 2789, 10),
            line("QUIVER", 631, 2714, 813, 2838, 11),
            line("DANG IT!!", 298, 2774, 495, 2825, 12),
        )

        val blocks = blocksOf(boxes)

        assertTrue(
            blocks.none { "QUIVER" in it && "SOULMATE" in it },
            "the gesture lettering is still inside the dialogue: $blocks",
        )
        assertTrue(
            blocks.any { it == "I'M THE WORST! I'VE ALREADY HURT MY PRECIOUS SOULMATE!" },
            "the first sentence did not come out clean: $blocks",
        )
        assertTrue(
            blocks.any { it == "GET YOUR ACT TOGETHER, DANG IT!!" },
            "the second sentence did not come out clean: $blocks",
        )
    }

    @Test
    fun `a wide caption does not bridge the two bubbles beside it`() {
        // Trigun 01 page 51, every box as the detector returned them. One block
        // of 542x946 filling 53%, painted as an opaque panel over a quarter of
        // the page, holding three bubbles.
        //
        // The caption is lettered at 86-94px and the two bubbles at 34-41px.
        // The bubbles do not overlap each other on x at all — 1258-1431 against
        // 889-1016 — so the column pass would separate them on its own. It is
        // the caption, spanning 894-1427, that overlaps both and joins them.
        val boxes = listOf(
            line("MERYL STRIFE,", 894, 923, 1424, 1009, 0),
            line("AND I REPRESENT", 905, 997, 1427, 1091, 1),
            line("THE BERNARDELLI", 903, 1080, 1423, 1169, 2),
            line("INSURANCE", 957, 1159, 1374, 1249, 3),
            line("SOCIETY.", 1000, 1241, 1311, 1329, 4),
            line("FREAKIN'", 1264, 1375, 1422, 1413, 5),
            line("IDIOT!", 1288, 1408, 1395, 1443, 6),
            line("THAT MIGHT", 1258, 1439, 1431, 1473, 7),
            line("HAVE GOT", 1267, 1469, 1422, 1506, 8),
            line("HIM, BUT", 1277, 1502, 1415, 1536, 9),
            line("IT'D BLOW", 1259, 1531, 1419, 1567, 10),
            line("HIM TO", 1290, 1563, 1400, 1598, 11),
            line("BITS!", 1293, 1591, 1394, 1630, 12),
            line("DO YOU", 892, 1615, 1012, 1653, 13),
            line("THINK", 906, 1647, 997, 1683, 14),
            line("WE'D", 911, 1678, 993, 1713, 15),
            line("GET ANY", 889, 1710, 1016, 1744, 16),
            line("MONEY", 899, 1741, 1006, 1775, 17),
            line("FOR A", 902, 1768, 1005, 1809, 18),
            line("PILE OF", 893, 1803, 1012, 1838, 19),
            line("MEAT?", 900, 1832, 1005, 1869, 20),
        )

        val blocks = blocksOf(boxes)

        assertTrue(
            blocks.any { it == "MERYL STRIFE, AND I REPRESENT THE BERNARDELLI INSURANCE SOCIETY." },
            "the caption did not come out whole: $blocks",
        )
        assertTrue(
            blocks.any { it == "FREAKIN' IDIOT! THAT MIGHT HAVE GOT HIM, BUT IT'D BLOW HIM TO BITS!" },
            "the right-hand bubble did not come out whole: $blocks",
        )
        assertTrue(
            blocks.any { it == "DO YOU THINK WE'D GET ANY MONEY FOR A PILE OF MEAT?" },
            "the left-hand bubble did not come out whole: $blocks",
        )
    }

    @Test
    fun `lines of one bubble are not split by ordinary size variation`() {
        // The same bubble's own line heights, which vary by up to 1.1x between
        // a line of capitals and one with a descender. Measured over 5756
        // healthy blocks: 95% stay within 1.3x and 99% within 1.6x, which is
        // where the split threshold sits.
        val boxes = listOf(
            line("MERYL STRIFE,", 894, 923, 1424, 1009, 0),
            line("AND I REPRESENT", 905, 997, 1427, 1091, 1),
            line("THE BERNARDELLI", 903, 1080, 1423, 1169, 2),
            line("INSURANCE", 957, 1159, 1374, 1249, 3),
            line("SOCIETY.", 1000, 1241, 1311, 1329, 4),
        )

        assertEquals(
            listOf("MERYL STRIFE, AND I REPRESENT THE BERNARDELLI INSURANCE SOCIETY."),
            blocksOf(boxes),
        )
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
