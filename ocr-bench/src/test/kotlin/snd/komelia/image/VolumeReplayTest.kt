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
        // Several directories, comma-separated, are replayed in one run. Not a
        // convenience: sweeping a threshold means replaying every volume for
        // every value, and paying Gradle's start-up once per volume turns a
        // four-value sweep into half an hour of waiting rather than two minutes
        // of measuring.
        val dirs = System.getenv("KORA_BENCH_DIR")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.map(::File)
            ?.filter { it.isDirectory }
            .orEmpty()
        if (dirs.isEmpty()) {
            println("KORA_BENCH_DIR not set — skipping the volume replay")
            return
        }
        // Both tables the reader loads at scan time. Without them the repair
        // silently does nothing and the phrase book falls back to its forty
        // curated entries -- so the bench would report a pipeline weaker than
        // the one that ships, and every measurement taken from it would be
        // pessimistic by an unknown amount.
        val lexicon = File("../komelia-ui/src/commonMain/composeResources/files/lexicon/en.txt")
        assertTrue(lexicon.isFile, "shipped lexicon missing at ${lexicon.absolutePath}")
        OcrSpellRepair.load(lexicon.readLines().filter { it.isNotBlank() }.toSet())

        val table = File("../komelia-ui/src/commonMain/composeResources/files/phrasebook/en-fr.json")
        assertTrue(table.isFile, "shipped phrase book missing at ${table.absolutePath}")
        PhraseBook.load(Json.decodeFromString<Map<String, String>>(table.readText()))

        // The same three for Japanese, and for the same reason. Loaded
        // unconditionally rather than behind KORA_BENCH_VERTICAL: a missing
        // table does not fail, it quietly answers nothing, and a bench that
        // measures a glossary which was never loaded reports zero and looks
        // like a finding.
        val japanesePhrases = File("../komelia-ui/src/commonMain/composeResources/files/phrasebook/ja-fr.json")
        assertTrue(japanesePhrases.isFile, "shipped japanese phrase book missing at ${japanesePhrases.absolutePath}")
        JapanesePhraseBook.load(Json.decodeFromString<Map<String, String>>(japanesePhrases.readText()))

        val katakana = File("../komelia-ui/src/commonMain/composeResources/files/japanese/katakana.json")
        assertTrue(katakana.isFile, "shipped katakana glossary missing at ${katakana.absolutePath}")
        JapaneseKatakanaGlossary.load(katakana.readText())

        val kansai = File("../komelia-ui/src/commonMain/composeResources/files/japanese/kansai.json")
        assertTrue(kansai.isFile, "shipped kansai table missing at ${kansai.absolutePath}")
        JapaneseKansaiNormaliser.load(kansai.readText())

        dirs.forEach { replay(it) }
    }

    private fun replay(dir: File) {
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
        // The phrase book's answer for each sentence, or blank. Written beside
        // sentences.txt rather than folded into it, so the bench can show what
        // the engine WOULD have said next to what the table says instead --
        // which is the only way to tell a useful entry from a harmful one.
        val answers = StringBuilder()
        // What OcrSpellRepair changed, one "before -> after" a line. Reported
        // by the real code rather than recomputed in Python: the repair is
        // exactly the kind of rule a reimplementation gets subtly wrong, and a
        // census built on a wrong copy would be worse than no census.
        val repairs = StringBuilder()
        // Which Japanese table rewrote what, "table<TAB>before<TAB>after". The
        // dialect and glossary tables run one after the other on the same
        // sentence, so a single before/after would hide which of the two moved
        // it -- and they are judged separately.
        val rewrites = StringBuilder()
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

            // KORA_BENCH_VERTICAL replays a Japanese volume: columns read
            // right to left, which is a different merge and a different page
            // order, not a variant of the Latin one.
            val vertical = System.getenv("KORA_BENCH_VERTICAL") != null
            // Before the merge, exactly where ReaderState.performScan puts it.
            // Without it the credits and advertising pages of a scanlation --
            // which are Japanese -- reach the translator, and the bench reports
            // bubbles the reader would never have seen.
            //
            // The furigana filter belongs to the same step and in the same
            // order -- ReaderState builds `rawBoxes` as
            // JapaneseFuriganaFilter.apply(keepCjk(scanned)). Leaving it out
            // here fed the merge the phonetic gloss printed beside the kanji,
            // which is exactly the noise it was written to remove.
            val kept = if (vertical) JapaneseFuriganaFilter.apply(OcrScriptFilter.keepCjk(boxes))
            else OcrScriptFilter.keepLatin(boxes)
            val merged = mergeOcrBoxes(
                kept,
                if (vertical) ReadingDirection.RTL else ReadingDirection.LTR,
                vertical = vertical,
                pageWidth = page.width,
            )
            val shortName = page.page.substringAfterLast(" - ").substringBefore(" [")
            report.appendLine("== $shortName  ${page.width}x${page.height}  ${boxes.size} lines")

            val prepared = mutableListOf<String>()
            merged.groupBy { it.blockIndex }.forEach { (index, lines) ->
                blockCount++
                val rect = lines.first().blockRect
                // Japanese does not separate words with spaces, and inserting
                // them between the columns of a bubble splits words the
                // detector happened to cut in two.
                val text = lines.sortedBy { it.lineIndex }
                    .joinToString(if (vertical) "" else " ") { it.text }
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

                // The rest of ReaderState.translateBlocks, in its order and
                // down the same branch. The repair sits between the rejoin and
                // the casing because it needs whole words and puts the capitals
                // back itself; leaving it out meant the word splitter was not
                // exercised by the bench at all, which is how a bench comes to
                // disagree with the app.
                //
                // Everything on the Latin side is Latin-only in the app too,
                // and running it on Japanese is not a small inaccuracy: the
                // sound-effect test and EnglishTextCleaner are written against
                // an alphabet, and pointed at a Japanese volume they threw away
                // 203 of 210 blocks -- 第01話 and メゲー世界は came out as
                // sound effects. A bench that keeps 3% of the page cannot
                // measure anything about the page.
                val ready: String
                val letters: Int
                val effect: String
                if (vertical) {
                    // Homoglyphs rather than spelling, so no word list, and it
                    // runs before anything else looks at the text: the phrase
                    // book, the dialect table and the glossary all match on what
                    // the page says, and ニ丁 is not what it says.
                    ready = JapaneseOcrRepair.apply(text)
                    if (ready != text) repairs.appendLine("$text	$ready")
                    letters = ready.count { it.isLetter() }
                    effect = ""
                } else {
                    val rejoined = TranslationTextUtils.rejoinLineBreaks(text)
                    val repaired = OcrSpellRepair.apply(rejoined)
                    if (repaired != rejoined) repairs.appendLine("$rejoined	$repaired")
                    ready = TranslationTextUtils.toSentenceCase(repaired)
                    letters = ready.count { it.isLetter() }
                    effect = if (TranslationTextUtils.isSoundEffect(ready)) " SFX" else ""
                }
                // A whole Japanese bubble can be two characters ("はい"), where
                // three would already be a sentence -- hence the lower bar.
                val keep = if (vertical) letters >= 2
                else letters >= 2 && ready.length >= 3 && effect.isEmpty() &&
                        EnglishTextCleaner.isTranslatable(ready)
                if (keep) prepared += ready
                report.appendLine(
                    "   block %-3d [%4d,%4d %4dx%4d] w=%2d%% fill=%3d%% lines=%-2d h=%.1fx%s  %s".format(
                        index, rect.left.toInt(), rect.top.toInt(),
                        rect.width.toInt(), rect.height.toInt(),
                        (widthShare * 100).toInt(), fill, lines.size, ratio, effect, text,
                    )
                )

                // Holds by construction: splitIntoColumns leaves every block as
                // one group of lines that overlap each other horizontally.
                //
                // Mirrored for vertical Japanese, where the columns of one
                // bubble sit side by side and are meant NOT to overlap
                // horizontally -- it is on the other axis that they must.
                // Asserting the Latin shape there fails on every correct block,
                // which is what it did the first time this was pointed at a
                // Japanese volume.
                val sorted = lines.map { it.imageRect }
                    .sortedBy { if (vertical) it.top else it.left }
                var reach = if (vertical) sorted.first().bottom else sorted.first().right
                sorted.drop(1).forEach { line ->
                    val axis = if (vertical) "vertical" else "horizontal"
                    assertTrue(
                        (if (vertical) line.top else line.left) < reach,
                        "$shortName block $index holds lines with no $axis overlap: $text",
                    )
                    reach = maxOf(reach, if (vertical) line.bottom else line.right)
                }
            }

            // A sentence lettered across two or three balloons goes to the
            // translator whole, and the engine's answer depends on getting it
            // whole -- so the bench has to group before it translates, or it
            // measures a pipeline nobody ships.
            //
            // Japanese does not group: the assembler keys on ellipses and on a
            // Latin sentence not having ended, neither of which says anything
            // about a Japanese page. One bubble, one sentence.
            val groups = if (vertical) prepared.indices.map { listOf(it) }
            else BubbleAssembler.group(prepared)
            groups.forEach { positions ->
                val joined = BubbleAssembler.join(positions.map { prepared[it] })
                // Looked up on the joined text, before the rewrites: both tables
                // are written the way a page is lettered, so a balloon that is
                // an entry matches as it was read.
                val answer = if (vertical) JapanesePhraseBook.lookup(joined)
                else PhraseBook.lookup(joined)
                // Dialect first, then the katakana glossary -- ReaderState's
                // order, and it matters: a Kansai form can itself be katakana.
                val out: String
                if (vertical) {
                    val dialect = JapaneseKansaiNormaliser.apply(joined)
                    out = JapaneseKatakanaGlossary.apply(dialect)
                    // One line per table that fired, named. Counting how many
                    // sentences changed is not enough to decide anything: the
                    // question a table has to answer is which rule of it earns
                    // its place, and a rewrite nobody can attribute cannot be
                    // removed with any confidence.
                    if (dialect != joined) rewrites.appendLine("kansai	$joined	$dialect")
                    if (out != dialect) rewrites.appendLine("katakana	$dialect	$out")
                } else out = joined
                sentences.appendLine(out)
                answers.appendLine(answer.orEmpty())
            }
        }

        val out = File(dir, "report.txt")
        out.writeText(report.toString())
        File(dir, "sentences.txt").writeText(sentences.toString())
        File(dir, "answers.txt").writeText(answers.toString())
        File(dir, "repairs.txt").writeText(repairs.toString())
        File(dir, "rewrites.txt").writeText(rewrites.toString())
        println("${pages.size} pages, $blockCount blocks -> ${out.absolutePath}")
        println("widest block: ${(widest * 100).toInt()}% of the page, at $widestWhere")
    }
}
