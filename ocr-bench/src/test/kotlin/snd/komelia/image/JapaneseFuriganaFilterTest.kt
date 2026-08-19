package snd.komelia.image

import androidx.compose.ui.geometry.Rect
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The geometry cases are drawn from what a vertical Japanese page actually
 * looks like: a column of body text with a narrow column of kana glued to its
 * right edge.
 *
 * The replay at the bottom is the one that matters. The filter was written for
 * 1920px volumes and must stay inert on the 1200px ones this fork was built
 * against, so it locks the measured rate on a low-resolution capture.
 */
class JapaneseFuriganaFilterTest {

    private fun box(text: String, left: Int, top: Int, right: Int, bottom: Int): OcrElementBox {
        val rect = Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        return OcrElementBox(text, rect, rect, blockIndex = 0, lineIndex = 0, elementIndex = 0)
    }

    /** A body column with a furigana column against its right edge. */
    private fun annotatedColumn(): List<OcrElementBox> = listOf(
        box("六大貴族", 200, 100, 260, 400),   // 60 wide
        box("ろくだいきぞく", 265, 105, 295, 390), // 30 wide, alongside
        box("この船から降りろ", 400, 100, 462, 500),
        box("目的地まで我慢しろ", 520, 100, 583, 520),
    )

    @Test
    fun `drops the reading beside the word it annotates`() {
        val kept = JapaneseFuriganaFilter.apply(annotatedColumn())
        assertEquals(3, kept.size)
        assertTrue(kept.none { it.text == "ろくだいきぞく" }, "the furigana survived")
        assertTrue(kept.any { it.text == "六大貴族" }, "the annotated word was dropped instead")
    }

    @Test
    fun `keeps a short all-kana balloon that stands alone`() {
        // はい satisfies kana-only and thin; what saves it is having no thicker
        // column glued to its side. This is the case the whole rule is shaped
        // around, because dropping it would silently delete dialogue.
        val boxes = listOf(
            box("はい", 100, 100, 128, 170),
            box("この船から降りろ", 400, 100, 462, 500),
            box("目的地まで我慢しろ", 520, 100, 583, 520),
            box("船内掃除だ", 640, 100, 702, 460),
        )
        assertEquals(boxes.size, JapaneseFuriganaFilter.apply(boxes).size)
    }

    @Test
    fun `keeps a kana column inside a multi-column balloon`() {
        // Neighbouring columns of one balloon are the same thickness, so the
        // companion test fails and both survive. Without that test the left
        // column of every two-column balloon would look like furigana.
        val boxes = listOf(
            box("いらねーんだよ", 400, 100, 460, 460),
            box("船員なんか", 465, 100, 525, 400),
            box("目的地まで我慢しろ", 600, 100, 660, 520),
        )
        assertEquals(boxes.size, JapaneseFuriganaFilter.apply(boxes).size)
    }

    @Test
    fun `a kana column far from any word is not a reading`() {
        // Same size as furigana, but nothing alongside it: it is a small
        // balloon, not an annotation.
        val boxes = listOf(
            box("ぼくたち", 100, 100, 130, 260),
            box("この船から降りろ", 700, 100, 762, 500),
            box("目的地まで我慢しろ", 800, 100, 863, 520),
        )
        assertEquals(boxes.size, JapaneseFuriganaFilter.apply(boxes).size)
    }

    @Test
    fun `a word in kanji is never a reading however thin it is`() {
        val boxes = annotatedColumn().map {
            if (it.text == "ろくだいきぞく") box("六大", 265, 105, 295, 390) else it
        }
        assertEquals(boxes.size, JapaneseFuriganaFilter.apply(boxes).size)
    }

    @Test
    fun `fewer than two boxes cannot annotate anything`() {
        val one = listOf(box("ろくだいきぞく", 265, 105, 295, 390))
        assertEquals(one, JapaneseFuriganaFilter.apply(one))
        assertEquals(emptyList(), JapaneseFuriganaFilter.apply(emptyList()))
    }

    @Serializable
    private data class BoxJson(val rect: List<Int>, val text: String = "", val confidence: Float = 1f)

    @Serializable
    private data class PageJson(val page: String = "", val boxes: List<BoxJson> = emptyList())

    /**
     * Locks the measured behaviour on a low-resolution volume.
     *
     * Point KORA_FURIGANA_LOWRES_DIR at a directory of `<page>.boxes.json`
     * captured from a ~1200px book (run_ocr.py writes them). Measured on 40
     * pages of Kyou kara Hitman at 835x1200: 1 box out of 601, and that one is
     * サラリー, half of サラリーマン as the detector cut it. If this ever
     * climbs, the filter has started eating dialogue on exactly the books it
     * was supposed to leave alone.
     */
    @Test
    fun `stays inert on a low resolution volume`() {
        val dir = System.getenv("KORA_FURIGANA_LOWRES_DIR")?.let(::File)
        if (dir == null || !dir.isDirectory) {
            println("KORA_FURIGANA_LOWRES_DIR not set — skipping the low-resolution replay")
            return
        }
        val json = Json { ignoreUnknownKeys = true }
        var total = 0
        var dropped = 0
        val examples = mutableListOf<String>()
        dir.listFiles { f -> f.name.endsWith(".boxes.json") }.orEmpty().sorted().forEach { file ->
            val page = json.decodeFromString<PageJson>(file.readText())
            val boxes = page.boxes.map {
                val rect = Rect(
                    it.rect[0].toFloat(), it.rect[1].toFloat(),
                    it.rect[2].toFloat(), it.rect[3].toFloat(),
                )
                OcrElementBox(it.text, rect, rect, 0, 0, 0, confidence = it.confidence)
            }
            val kept = JapaneseFuriganaFilter.apply(boxes)
            total += boxes.size
            dropped += boxes.size - kept.size
            (boxes - kept.toSet()).forEach { examples += it.text }
        }
        println("furigana filter on the low-resolution volume: $dropped of $total dropped $examples")
        assertTrue(total > 100, "not enough boxes to conclude anything: $total")
        assertTrue(
            dropped * 100 <= total * 2,
            "the filter fired on $dropped of $total boxes; it must stay near zero on 1200px scans: $examples",
        )
    }
}
