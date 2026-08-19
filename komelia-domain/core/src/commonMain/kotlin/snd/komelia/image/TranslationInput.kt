package snd.komelia.image

/**
 * Everything between the recogniser's boxes and the sentences the engine is
 * asked to translate — for both languages, in one place.
 *
 * This exists because it was in two places and they drifted. The reader had it,
 * the bench had a copy, and the copy ran the Latin rules over Japanese: the
 * sound-effect test and the English cleaner threw away 203 of 210 blocks, and
 * nobody noticed for as long as Japanese had been in the bench. The replay ran,
 * the report was full, the tests passed.
 *
 * So the rule is not "keep them in step". The rule is that there is one of them.
 * A guardrail can catch a collapse after the fact; only shared code stops the
 * next rule from being added to one side alone.
 *
 * Deliberately pure and non-suspending. What stays outside is what needs the
 * database or the network: the per-series glossary, the tables loaded from
 * resources, the engine itself. Those come after, and they come after for the
 * same reason they always did — [joined] is what they consume.
 *
 * The caller must have loaded the English lexicon before calling this when
 * [prepare] is given `japanese = false`: the spelling repair runs here and does
 * nothing without it.
 */
object TranslationInput {

    /**
     * What a page turns into once the deterministic work is done.
     *
     * @property blocks the text of every block that survived, by block index
     * @property order those indices in reading order
     * @property groups positions within [order]; one group is one sentence
     * @property joined a sentence per group, ready for the glossary and engine
     * @property repairs what the spelling repair rewrote, by block index, for
     *   the diagnostic log — a rule that reads a token wrong shows its damage
     *   two stages later, in French, and without this nothing says which fired
     * @property seams why the assembler joined or refused each boundary; empty
     *   for Japanese, which does not group
     */
    data class Prepared(
        val blocks: Map<Int, String>,
        val order: List<Int>,
        val groups: List<List<Int>>,
        val joined: List<String>,
        val repairs: Map<Int, List<OcrSpellRepair.Change>>,
        val seams: List<BubbleAssembler.Seam>,
    ) {
        val isEmpty: Boolean get() = blocks.isEmpty()

        /** The surviving blocks in reading order, which is what [groups] indexes. */
        val ordered: List<String> get() = order.map { blocks.getValue(it) }

        /** The source balloons of each group, in order. */
        val groupSources: List<List<String>>
            get() = groups.map { positions -> positions.map { blocks.getValue(order[it]) } }
    }

    /**
     * [pageNumber] is 1-based and [pageCount] may be 0 when the book length is
     * not known yet; both go to [CreditLine] and nowhere else.
     */
    fun prepare(
        boxes: List<OcrElementBox>,
        japanese: Boolean,
        pageNumber: Int,
        pageCount: Int,
    ): Prepared {
        if (boxes.isEmpty()) return EMPTY

        val repairs = mutableMapOf<Int, List<OcrSpellRepair.Change>>()
        val blocks = boxes
            .groupBy { it.blockIndex }
            .mapValues { (blockIndex, elements) ->
                elements
                    .sortedWith(compareBy({ it.lineIndex }, { it.elementIndex }))
                    // Japanese does not separate words with spaces, and inserting
                    // them between the columns of a bubble splits words that the
                    // detector happened to cut in two.
                    .joinToString(if (japanese) "" else " ") { it.text }
                    .trim()
                    .let {
                        // The Japanese repair is homoglyphs rather than
                        // spelling, so it needs no word list and runs before
                        // anything else looks at the text: the phrase book, the
                        // dialect table and the katakana glossary all match on
                        // what the page says, and ニ丁 is not what it says.
                        if (japanese) JapaneseOcrRepair.apply(it)
                        // Both cleanups are about Latin lettering: a word the
                        // letterer broke across two lines, and the full caps
                        // comics are drawn in. Neither exists in Japanese.
                        else TranslationTextUtils.rejoinLineBreaks(it)
                            // After the line breaks are rejoined, so the repair
                            // sees whole words: "PLID- DING" is two fragments
                            // until then and neither one is repairable. Before
                            // the sentence casing, which is why the repair puts
                            // the capitals back itself.
                            .let { text ->
                                val repaired = OcrSpellRepair.applyTraced(text)
                                if (repaired.changes.isNotEmpty()) repairs[blockIndex] = repaired.changes
                                repaired.text
                            }
                            .let { text -> TranslationTextUtils.toSentenceCase(text) }
                    }
            }
            // Single letters and bare digits are artwork the OCR mistook for
            // text ('R', 'n' at 20x5px, 'e' at 8x7px, '1', 'V'). Translating them
            // is meaningless, and painting an opaque panel over them puts black
            // squares on the drawing.
            //
            // Japanese needs a lower bar: a whole bubble can be two characters
            // ("はい"), where three would already be a sentence.
            .filterValues { text ->
                val letters = text.count { it.isLetter() }
                val longEnough = if (japanese) letters >= 2 else letters >= 2 && text.length >= 3
                // Sound effects are drawn onto the artwork, so a panel over one
                // hides the drawing to say 'Table de Ping'. Latin only; Japanese
                // sound effects are a different problem and it is on hold.
                longEnough && (japanese || !TranslationTextUtils.isSoundEffect(text)) &&
                        // Recognition over artwork returns confident nonsense
                        // rather than nothing, and translating it paints an
                        // opaque panel over the drawing it came from.
                        (japanese || EnglishTextCleaner.isTranslatable(text))
            }
            // Dropped, not blanked: a block that never reaches the translator
            // has no panel painted over it, so the credits stay on the page as
            // they were printed instead of coming back as "B y you ji Miur a".
            .let { candidates -> candidates - creditBlocks(candidates, boxes, pageNumber, pageCount) }

        if (blocks.isEmpty()) return EMPTY

        // Block indices are already in reading order, so consecutive here means
        // consecutive on the page — which is what lets the balloons of one
        // sentence be recognised as consecutive at all.
        val order = blocks.keys.toList()
        val ordered = order.map { blocks.getValue(it) }
        // A sentence lettered across two or three balloons goes to the
        // translator whole. Measured over Ramen Aka Neko 167: 52 of 142 blocks
        // were two words or shorter, and a translator given two words of a
        // sentence returns two words of nonsense.
        //
        // Japanese does not group: the assembler keys on ellipses and on a Latin
        // sentence not having ended, neither of which says anything about a
        // Japanese page. One bubble, one sentence.
        val groups = if (japanese) ordered.indices.map { listOf(it) }
        else BubbleAssembler.group(ordered)

        return Prepared(
            blocks = blocks,
            order = order,
            groups = groups,
            joined = groups.map { positions -> BubbleAssembler.join(positions.map { ordered[it] }) },
            repairs = repairs,
            seams = if (japanese) emptyList() else BubbleAssembler.explain(ordered),
        )
    }

    /**
     * Which of [candidates] are the book's credits. See [CreditLine]; this half
     * is only the geometry, taken from the boxes the blocks were built from.
     */
    private fun creditBlocks(
        candidates: Map<Int, String>,
        boxes: List<OcrElementBox>,
        pageNumber: Int,
        pageCount: Int,
    ): Set<Int> {
        if (candidates.isEmpty()) return emptySet()
        val byBlock = boxes.groupBy { it.blockIndex }
        val lines = candidates.mapNotNull { (blockIndex, text) ->
            val elements = byBlock[blockIndex] ?: return@mapNotNull null
            if (elements.isEmpty()) return@mapNotNull null
            val rects = elements.map { it.blockRect }
            CreditLine.Line(
                index = blockIndex,
                text = text,
                left = rects.minOf { it.left },
                top = rects.minOf { it.top },
                right = rects.maxOf { it.right },
                bottom = rects.maxOf { it.bottom },
                // Two credits printed one above the other merge into one block,
                // and its ratio is then half a line's. Measured wrong once: the
                // Akane-banashi colophon was translated because of it.
                lineCount = elements.map { it.lineIndex }.distinct().size,
            )
        }
        return CreditLine.detect(lines, pageNumber, pageCount)
    }

    private val EMPTY = Prepared(emptyMap(), emptyList(), emptyList(), emptyList(), emptyMap(), emptyList())
}
