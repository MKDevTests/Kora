package snd.komelia.image

/**
 * Recognises the credits — translator, letterer, editor, copyright — so they are
 * left in the language they were printed in.
 *
 * They are not dialogue and nobody reads them for meaning, but the translator
 * treats them as prose and takes the names apart: "By Kouji Miura" came back
 * "B y you ji Miur a", "Christine Dashiell" as "de laine Dashiell", "Lettering:
 * Mark McMurray" as "Lettrage: Mark McMurra y". A name is exactly what a small
 * model has no business rewriting.
 *
 * 58 of the 1 803 balloons in the bench corpus, 3.2 %, and all of them on the
 * pages a reader passes through twice — the front matter and the colophon.
 *
 * A hit is NOT removed. The block is dropped from the translation set, which
 * leaves it with no panel painted over it, so the original line stays on the
 * page exactly as it was printed. That is what makes a false positive cheap: at
 * worst a line the reader could have had in French stays in English, next to a
 * drawing they can see anyway. Nothing this returns can hide anything.
 *
 * Measured on the 41 credit-word lines of the corpus, each read by hand:
 *
 *     keyword alone                   83 %  (34/41)
 *     + aspect ratio                  90 %
 *     + page zone                     93 %
 *     + no closing !? , no numbering  100 % (28/28)
 *
 * The last row is not a real 100 %. Those two conditions were added while
 * looking at the two cases that were left, which is fitting to 41 examples; the
 * first three come from properties of the page and should travel. What the
 * ratio and the page zone buy is measured and worth having either way.
 *
 * Known miss: a credit line carrying no credit word at all. Blue Box prints its
 * chapter title and its author on one band with neither ("#139: I've Done My
 * Fair Share", "By Kouji Miura" — 'by' is too common a word to key on), and
 * nothing here reaches them.
 */
object CreditLine {

    /**
     * One recognised block of text, in image pixels.
     *
     * [lineCount] is what the ratio is measured against, and getting it wrong is
     * what made this miss its first real page: the shape that identifies a
     * credit belongs to ONE line of type, but a block can hold several stacked
     * on top of each other. Akane-banashi prints its story and translation
     * credits one above the other and they merge into a single block, whose
     * height is twice a line's and whose ratio is therefore half -- 4.5 against
     * the 9.1 and 9.8 the lines have on their own.
     */
    data class Line(
        val index: Int,
        val text: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val lineCount: Int = 1,
    )

    /**
     * The indices of [lines] that are credits.
     *
     * [pageNumber] is 1-based. [pageCount] may be 0 when the book length is not
     * known yet, in which case only the opening pages are considered — never
     * knowing the length must not turn the whole book into front matter.
     */
    fun detect(lines: List<Line>, pageNumber: Int, pageCount: Int): Set<Int> {
        if (lines.isEmpty() || !inFrontOrBackMatter(pageNumber, pageCount)) return emptySet()

        val seeds = lines.filter { isCreditLine(it.text, aspectRatio(it)) }
        if (seeds.isEmpty()) return emptySet()

        // The recogniser cuts a long credit band into pieces, and the piece
        // without the keyword is the one that gets mangled: "Translation:
        // Christine Dashiell" is read as itself AND as "tine Dashiell", and only
        // the second became "de laine Dashiell". A fragment sitting on the same
        // band as a credit is part of it.
        val hits = seeds.map { it.index }.toMutableSet()
        for (line in lines) {
            if (line.index in hits) continue
            if (seeds.any { sharesBand(it, line) }) hits += line.index
        }
        return hits
    }

    /** Testable on its own: one line, no neighbours, no page. */
    fun isCreditLine(text: String, aspectRatio: Float): Boolean {
        if (aspectRatio <= MIN_ASPECT) return false
        val trimmed = text.trim()
        // "TRANSLATION NOTE!", "Support the Author!" — a credit is a label, it
        // does not exclaim and it does not ask.
        if (trimmed.endsWith('!') || trimmed.endsWith('?')) return false
        val match = KEYWORD.find(trimmed) ?: return false
        val after = trimmed.substring(match.range.last + 1).trimStart()
        // "STORY 180: I WANTED TO MEASURE" is a chapter number, not a byline.
        if (after.firstOrNull()?.isDigit() == true) return false
        // A translator's note is the opposite of a credit: it is written to be
        // read. Scanlations print them by the page — "Translation note! Dei
        // matsuri's speaking style is based on the tokyo shitamachi dialect,
        // which these days is essentially historical..." runs sixty words and
        // never reaches a closing !, so the exclamation guard above cannot see
        // it. Notes are a genre, not one stray line.
        if (after.startsWith("note", ignoreCase = true)) return false
        return true
    }

    /** Width against the height of a SINGLE line -- see [Line.lineCount]. */
    private fun aspectRatio(line: Line): Float {
        val width = line.right - line.left
        val height = (line.bottom - line.top) / line.lineCount.coerceAtLeast(1)
        return if (width <= 0f || height <= 0f) 0f else width / height
    }

    /**
     * Two lines are on the same band when their vertical extents overlap by at
     * least half of the shorter one. Horizontal position is deliberately not
     * considered: the pieces of a credit band sit side by side across the width
     * of the page.
     */
    private fun sharesBand(seed: Line, other: Line): Boolean {
        val overlap = minOf(seed.bottom, other.bottom) - maxOf(seed.top, other.top)
        if (overlap <= 0f) return false
        val shorter = minOf(seed.bottom - seed.top, other.bottom - other.top)
        return shorter > 0f && overlap >= shorter / 2f
    }

    private fun inFrontOrBackMatter(pageNumber: Int, pageCount: Int): Boolean {
        if (pageNumber <= FRONT_MATTER_PAGES) return true
        if (pageCount <= 0) return false
        return pageNumber > pageCount - BACK_MATTER_PAGES
    }

    /**
     * A credit line is a ribbon: one line of small type running across the page.
     * A speech balloon is lettered several lines deep, and each of ITS lines is
     * short — the corpus medians are 8.9 against 3.5, both measured per line.
     * Six sits between them and is what takes "STORY!" (2.5) and "STORY OR..."
     * (5.2), both dialogue, out of the set.
     */
    private const val MIN_ASPECT = 6f
    private const val FRONT_MATTER_PAGES = 5
    private const val BACK_MATTER_PAGES = 3

    /**
     * Deliberately short. Every word here names a craft or a legal notice; none
     * of them is ordinary dialogue on its own — 'by' and 'a story' were tried
     * and are far too common. Anything this misses simply stays translated,
     * which is where it started.
     */
    private val KEYWORD = Regex(
        "\\b(story|art|artwork|lettering|letters|translation|translator|design|designer|" +
                "editor|edited|author|copyright|all rights|published|serialized|" +
                "typesetting|proofread|cleaner|redraw|colors|colours|script|created)\\b",
        RegexOption.IGNORE_CASE,
    )
}
