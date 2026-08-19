package snd.komelia.image

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Rewrites Kansai dialect into standard Japanese before the sentence reaches
 * the translator.
 *
 * The engine was trained on standard Japanese. A page whose cast speaks Kansai
 * does not come back slightly off, it comes back reversed: 見つからへんど
 * ("you won't find one") returned "peut être trouvé", and 知っとんねん ("how do
 * you know") returned "pourquoi ne connais-tu pas". Those are not stylistic
 * losses, they are the opposite of the line.
 *
 * Measured over the 231 distinct balloons of a 50-page reading log: 19 balloons
 * touched, 9 clearly better, no clear regression, and four of the nine were
 * reversals of meaning put back the right way round.
 *
 * ## Why it is a table and not a set of regexes
 *
 * Kansai negation is ~へん, and ~へん attaches to a verb. Written as one rule it
 * is fine; written as a rule that fires anywhere it eats the へ of ヘタレ. The
 * table carries the verbs the file's author listed, plus a generic ~へん as a
 * last resort, and longest-first ordering makes the specific entry win.
 *
 * ## The one failure this class exists to avoid
 *
 * A first version applied the rules as plain substring replacements. やな→だな
 * fired inside やない and turned a negation into "da nai"; われ→お前 fired
 * inside 言われた and produced 言お前た. Both are the same bug the katakana
 * glossary already had, and the fix is the same: longest key first, every match
 * claims its characters, and the keys that need a boundary carry a [Guard].
 */
object JapaneseKansaiNormaliser {

    private var entries: List<Rewrite> = emptyList()

    /**
     * What a key needs around it before it may fire.
     *
     * Each one is here because the unguarded form was measured wrong on the
     * reading log, not because it looked risky.
     */
    private enum class Guard {
        /** Anywhere. */
        NONE,

        /**
         * ~はん is さん after a name, and the last two morae of ごはん otherwise.
         * What separates them is what comes before: a name ends in kanji or
         * katakana, ご does not. What comes after may be the end of the clause
         * or a particle — 二丁はんが went untouched while the guard demanded a
         * terminal, and the balloon came back "deux-cho han".
         */
        PERSON_SUFFIX,

        /** ~ど is ぞ only at the end of a line, and only after ん. */
        SENTENCE_FINAL_DO,

        /**
         * ちゃう is 違う at the head of a balloon and after ん (んちゃうか). In
         * the middle of a word it is the contraction of ~てしまう: 見えちゃう
         * became 見え違う and the line came back "Voyez-vous cela différemment ?".
         */
        AFTER_N_OR_START,

        /**
         * A key that starts in katakana must cover a whole katakana run, so ワシ
         * cannot fire inside ワシントン. Same rule as [JapaneseKatakanaGlossary],
         * and it is derived from the key rather than written in the file.
         */
        KATAKANA_RUN,

        /**
         * ~へん is the Kansai negation, and a negation attaches to the 未然形 --
         * the verb stem ending in an -a kana. 見つから-へん qualifies; そのへん
         * ("around there") does not, and became そのない on a real page.
         */
        NEGATIVE_STEM,

        /**
         * やろ is the copula や plus ろ, and it is NOT the last two morae of the
         * volitive ~やろう. 触ってやろうか became 触ってだろううか -- which is
         * not Japanese at all -- and 一生懸命やろうって the same way. The う is
         * the whole difference.
         */
        NOT_VOLITIVE,

        /**
         * A sentence-final particle, and only that. かいな closes a question;
         * inside しかいない it cut 四人しかいない ("there are only four of
         * them") down to 四人しかない, changing the verb of existence.
         */
        CLAUSE_FINAL,
    }

    private class Rewrite(val key: String, val replacement: String, val guard: Guard) {
        /** How much of [key] is katakana, counted from the front. */
        val katakanaHead: Int = key.takeWhile { it.isKatakana() }.length
    }

    val isLoaded: Boolean get() = entries.isNotEmpty()

    val size: Int get() = entries.size

    /**
     * Reads the shipped table.
     *
     * `ja_normal` is the only field consulted, for the reason spelled out in
     * [JapaneseKatakanaGlossary]: what goes back into the sentence is Japanese,
     * never a French gloss.
     */
    fun load(json: String) {
        if (entries.isNotEmpty()) return
        val parsed = format.decodeFromString<TableFile>(json)
        entries = parsed.entries.mapNotNull { it.toRewrite() }.sortedByDescending { it.key.length }
    }

    /** Test seam: the same rules without a file. Replaces rather than adds. */
    internal fun loadForTest(pairs: List<Pair<String, String>>) {
        entries = pairs
            .map { Rewrite(it.first, it.second, guardFor(it.first, null)) }
            .sortedByDescending { it.key.length }
    }

    /** Test seam: back to nothing loaded, so [load] takes effect again. */
    internal fun resetForTest() {
        entries = emptyList()
    }

    fun apply(text: String): String {
        if (entries.isEmpty() || text.isEmpty()) return text

        val runEnds = katakanaRunEnds(text)
        val claimed = BooleanArray(text.length)
        val edits = ArrayList<Edit>()
        for (entry in entries) {
            var from = 0
            while (true) {
                val at = text.indexOf(entry.key, from)
                if (at < 0) break
                from = at + 1
                val end = at + entry.key.length
                if ((at until end).any { claimed[it] }) continue
                if (!entry.allowedAt(text, at, runEnds)) continue
                for (i in at until end) claimed[i] = true
                edits.add(Edit(at, end, entry.replacement))
            }
        }
        if (edits.isEmpty()) return text

        edits.sortBy { it.start }
        val out = StringBuilder(text.length)
        var last = 0
        for (edit in edits) {
            out.append(text, last, edit.start)
            out.append(edit.replacement)
            last = edit.end
        }
        out.append(text, last, text.length)
        return out.toString()
    }

    private fun Rewrite.allowedAt(text: String, at: Int, runEnds: Map<Int, Int>): Boolean {
        val end = at + key.length
        return when (guard) {
            Guard.NONE -> true

            Guard.PERSON_SUFFIX -> {
                val before = text.getOrNull(at - 1)
                val after = text.getOrNull(end)
                before != null && (before.isKanji() || before.isKatakana()) &&
                        (after == null || after.isTerminal() || after in PARTICLES)
            }

            Guard.SENTENCE_FINAL_DO ->
                text.getOrNull(at - 1) == 'ん' &&
                        text.substring(end).all { it.isTerminal() }

            Guard.AFTER_N_OR_START -> at == 0 || text[at - 1] == 'ん'

            Guard.KATAKANA_RUN -> runEnds[at] == at + katakanaHead

            Guard.NEGATIVE_STEM -> text.getOrNull(at - 1)?.let { it in A_ROW } == true

            Guard.NOT_VOLITIVE -> text.getOrNull(end) != 'う'

            Guard.CLAUSE_FINAL -> text.substring(end).all { it.isTerminal() }
        }
    }

    private class Edit(val start: Int, val end: Int, val replacement: String)

    private fun katakanaRunEnds(text: String): Map<Int, Int> {
        val ends = HashMap<Int, Int>()
        var i = 0
        while (i < text.length) {
            if (!text[i].isKatakana()) {
                i++
                continue
            }
            var end = i
            while (end < text.length && text[end].isKatakana()) end++
            ends[i] = end
            i = end
        }
        return ends
    }

    private fun Char.isKatakana(): Boolean = this in 'ァ'..'ヺ' || this == 'ー'

    private fun Char.isKanji(): Boolean = this in '一'..'鿿'

    /** Everything a balloon may end with: punctuation, the marks, whitespace. */
    private fun Char.isTerminal(): Boolean = this in TERMINAL || isWhitespace()

    private const val TERMINAL = "！？!?。、…‥・♪♡"

    /**
     * The particles that may follow an honorific. ごはん is already excluded by
     * what precedes はん, so this list does not have to protect it.
     */
    private const val PARTICLES = "がはをにでともへやのねよかなぞぜさ"

    /**
     * The 未然形 endings a Kansai negation may attach to: the -a column, plus
     * the voiced and semi-voiced rows.
     */
    private const val A_ROW = "あかがさざただなはばぱまやらわ"

    private fun guardFor(expression: String, declared: String?): Guard = when (declared) {
        "negative_stem" -> Guard.NEGATIVE_STEM
        "not_volitive" -> Guard.NOT_VOLITIVE
        "clause_final" -> Guard.CLAUSE_FINAL
        "person_suffix" -> Guard.PERSON_SUFFIX
        "sentence_final_do" -> Guard.SENTENCE_FINAL_DO
        "after_n_or_start" -> Guard.AFTER_N_OR_START
        else -> if (expression.firstOrNull()?.isKatakana() == true) Guard.KATAKANA_RUN else Guard.NONE
    }

    private fun Entry.toRewrite(): Rewrite? {
        val replacement = jaNormal?.takeIf { it.isNotBlank() } ?: return null
        if (expression.isBlank() || replacement == expression) return null
        return Rewrite(expression, replacement, guardFor(expression, guard))
    }

    private val format = Json { ignoreUnknownKeys = true }

    @Serializable
    private class TableFile(val entries: List<Entry> = emptyList())

    @Serializable
    private class Entry(
        val expression: String = "",
        @SerialName("ja_normal") val jaNormal: String? = null,
        val guard: String? = null,
    )
}
