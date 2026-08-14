package snd.komelia.image

import androidx.compose.ui.geometry.Rect
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Replays a volume captured by scripts/ocr-bench/run_ocr.py through the real
 * merge, and writes a report of every block it produced.
 *
 *     KORA_BENCH_DIR=/path/to/out ./gradlew :ocr-bench:test
 *
 * Skipped when the variable is unset, so it costs nothing in a normal run.
 *
 * Deliberately light on assertions. The one thing checked is structural and
 * holds by construction, so a break means the merge changed shape. Everything
 * else is reported rather than thresholded: the last time a threshold was
 * guessed at, the numbers it was meant to catch turned out to sit on the wrong
 * side of it.
 */
class VolumeReplayTest {

    @Serializable
    private data class BoxJson(
        val rect: List<Int>,
        val text: String,
        val confidence: Float = 1f,
        val background: String = "#000000",
    )

    @Serializable
    private data class PageJson(
        val page: String,
        val width: Int,
        val height: Int,
        val detector: String = "small",
        val boxes: List<BoxJson>,
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `replay the captured volume`() {
        val dir = System.getenv("KORA_BENCH_DIR")?.let(::File)
        if (dir == null || !dir.isDirectory) {
            println("KORA_BENCH_DIR not set — skipping the volume replay")
            return
        }

        val pages = dir.listFiles { f -> f.name.endsWith(".boxes.json") }
            ?.sortedBy { it.name }
            ?: emptyList()
        assertTrue(pages.isNotEmpty(), "no .boxes.json in $dir")

        val report = StringBuilder()
        // Exactly the strings the reader hands the translator: line breaks
        // rejoined, sentence-cased, sound effects dropped. Written from here
        // rather than reconstructed from the report, so the translation bench
        // cannot drift from what ships the way a Python copy of these rules
        // would.
        val sentences = StringBuilder()
        var blockCount = 0
        var widest = 0f
        var widestWhere = ""

        pages.forEach { file ->
            val page = json.decodeFromString<PageJson>(file.readText())
            val boxes = page.boxes.mapIndexed { index, box ->
                val rect = Rect(
                    box.rect[0].toFloat(), box.rect[1].toFloat(),
                    box.rect[2].toFloat(), box.rect[3].toFloat(),
                )
                OcrElementBox(
                    text = box.text,
                    imageRect = rect,
                    blockRect = rect,
                    blockIndex = index,
                    lineIndex = 0,
                    elementIndex = 0,
                    confidence = box.confidence,
                )
            }

            val merged = mergeOcrBoxes(boxes, ReadingDirection.LTR, pageWidth = page.width)
            val shortName = page.page.substringAfterLast(" - ").substringBefore(" [")
            report.appendLine("== $shortName  ${page.width}x${page.height}  ${boxes.size} lines")

            merged.groupBy { it.blockIndex }.forEach { (index, lines) ->
                blockCount++
                val rect = lines.first().blockRect
                val text = lines.sortedBy { it.lineIndex }.joinToString(" ") { it.text }
                val covered = lines.sumOf { (it.imageRect.width * it.imageRect.height).toDouble() }
                val fill = (covered / (rect.width * rect.height) * 100).toInt()
                val widthShare = rect.width / page.width

                if (widthShare > widest) {
                    widest = widthShare
                    widestWhere = "$shortName block $index"
                }

                // Tallest line over the median of all the others. Dialogue is
                // set at one size, so a block whose tallest line towers over the
                // rest is lettering welded to a bubble — the defect Batman
                // turned up, which fill alone does not see because big letters
                // cover a lot of area.
                //
                // Against the median of the whole block this reads 1.0x for
                // every two-line block, the tallest line being its own upper
                // median; and one giant line among ten drags nothing.
                val heights = lines.map { it.imageRect.height }.sorted()
                val others = heights.dropLast(1)
                val ratio = if (others.isEmpty()) 1f else heights.last() / others[others.size / 2]

                val effect = if (TranslationTextUtils.isSoundEffect(text)) " SFX" else ""
                if (effect.isEmpty()) {
                    val ready = TranslationTextUtils.toSentenceCase(
                        TranslationTextUtils.rejoinLineBreaks(text)
                    )
                    sentences.appendLine(ready)
                }
                report.appendLine(
                    "   block %-3d [%4d,%4d %4dx%4d] w=%2d%% fill=%3d%% lines=%-2d h=%.1fx%s  %s".format(
                        index, rect.left.toInt(), rect.top.toInt(),
                        rect.width.toInt(), rect.height.toInt(),
                        (widthShare * 100).toInt(), fill, lines.size, ratio, effect, text,
                    )
                )

                // Holds by construction: splitIntoColumns leaves every block as
                // one group of lines that overlap each other horizontally.
                val sorted = lines.map { it.imageRect }.sortedBy { it.left }
                var reach = sorted.first().right
                sorted.drop(1).forEach { line ->
                    assertTrue(
                        line.left < reach,
                        "$shortName block $index holds lines with no horizontal overlap: $text",
                    )
                    reach = maxOf(reach, line.right)
                }
            }
        }

        val out = File(dir, "report.txt")
        out.writeText(report.toString())
        File(dir, "sentences.txt").writeText(sentences.toString())
        println("${pages.size} pages, $blockCount blocks -> ${out.absolutePath}")
        println("widest block: ${(widest * 100).toInt()}% of the page, at $widestWhere")
    }
}
