package snd.komelia.image

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Rewrites the katakana a manga letterer uses for emphasis back into ordinary
 * Japanese, before the sentence reaches the translator.
 *
 * Japanese prose writes 魚, 小便 and ほど; a manga writes サカナ, ションベン and
 * ホド to sound rough or to shout. The translation model has never seen the
 * second spelling, so it transliterates it and the result reads as a character
 * name: 狙うサカナは？ came back "Qui est la cible, Sakana ?". Rewritten to
 * 獲物 the same engine returns "Qui est la cible ?".
 *
 * This is a rewrite, never a translation. The replacement is Japanese and goes
 * into the Japanese sentence; putting French there would hand the engine a
 * sentence in two languages. Measured over 20 entries on the shipped pivot: 9
 * clear gains, 2 losses, and both losses came from choosing hiragana where the
 * word has a common kanji — ちんぽ and よだれ vanish from the output where 陰茎
 * and 鼻血 survive.
 *
 * ## Why the match must be aligned to a katakana run
 *
 * A plain "contains" search is wrong, and by a lot. Over 457 recognised lines
 * it fired 122 times, of which 25 were false: リーマン inside サラリーマン (8
 * times), ケッ inside ケッコウ turning "assez joli" into "pff", タラ inside
 * デタラメ, スラ inside スライド, トカ inside ナントカ.
 *
 * So a key matches only where a katakana run begins, and only when the key's
 * own katakana head covers that entire run. ナイ cannot fire inside ナイフ
 * because ナイ does not reach the フ; アンタら still fires because アンタ is the
 * whole run and ら follows it in hiragana. That left 97 firings and no false
 * one.
 */
object JapaneseKatakanaGlossary {

    private var entries: List<Rewrite> = emptyList()

    private class Rewrite(val key: String, val replacement: String) {
        /** How much of [key] is katakana, counted from the front. */
        val head: Int = key.takeWhile { it.isKatakana() }.length
    }

    val isLoaded: Boolean get() = entries.isNotEmpty()

    val size: Int get() = entries.size

    /**
     * Reads the shipped glossary.
     *
     * `ja_normal` is the only field consulted. `translation_fr` is a French
     * gloss kept in the file for review and is deliberately ignored here: an
     * earlier version of the file carried the rewrite in that field for one
     * category, and reading it put the French word "je" inside four Japanese
     * sentences.
     *
     * An entry with a null or absent `ja_normal` is one where no Japanese form
     * was found that the engine reads better — キンタマ and ヨダレ were tested
     * against every candidate and none worked. Leaving them alone is the point,
     * not an omission.
     */
    fun load(json: String) {
        if (entries.isNotEmpty()) return
        val parsed = format.decodeFromString<GlossaryFile>(json)
        entries = parsed.entries
            .mapNotNull { entry ->
                val replacement = entry.jaNormal?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (entry.expression.isBlank() || replacement == entry.expression) null
                else Rewrite(entry.expression, replacement)
            }
            // Longest first, so コノヤロウ wins over ヤロウ and the shorter key
            // never gets to claim part of a longer one.
            .sortedByDescending { it.key.length }
    }

    /**
     * Test seam: the same rules, without a file.
     *
     * Replaces rather than adds, because [load] is a no-op once something is
     * loaded and tests in one class would otherwise inherit each other's table.
     */
    internal fun loadForTest(pairs: List<Pair<String, String>>) {
        entries = pairs.map { Rewrite(it.first, it.second) }.sortedByDescending { it.key.length }
    }

    /** Test seam: back to nothing loaded, so [load] takes effect again. */
    internal fun resetForTest() {
        entries = emptyList()
    }

    fun apply(text: String): String {
        if (entries.isEmpty() || text.isEmpty()) return text
        val runStarts = katakanaRunEnds(text)
        if (runStarts.isEmpty()) return text

        val claimed = BooleanArray(text.length)
        // Sorted on the way out rather than kept sorted: keys are tried longest
        // first, so the matches arrive in length order, not in page order.
        val edits = ArrayList<Edit>()
        for (entry in entries) {
            var from = 0
            while (true) {
                val at = text.indexOf(entry.key, from)
                if (at < 0) break
                from = at + 1
                val runEnd = runStarts[at] ?: continue
                // The key's katakana head must be exactly the run: shorter and
                // it is a fragment of a longer word, longer and it would have
                // eaten kana the run does not contain.
                if (at + entry.head != runEnd) continue
                val end = at + entry.key.length
                if (end > text.length) continue
                if ((at until end).any { claimed[it] }) continue
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

    private class Edit(val start: Int, val end: Int, val replacement: String)

    /** Start index of every katakana run, mapped to where that run ends. */
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

    /**
     * Katakana proper plus the長音 mark. ー is included because the spellings
     * that actually break the translator are the drawn-out ones — the file
     * carries バカヤロウ, the page says バカヤロー — and excluding it would end
     * the run one character early on exactly those.
     */
    private fun Char.isKatakana(): Boolean = this in 'ァ'..'ヺ' || this == 'ー'

    private val format = Json { ignoreUnknownKeys = true }

    @Serializable
    private class GlossaryFile(val entries: List<Entry> = emptyList())

    @Serializable
    private class Entry(
        val expression: String = "",
        @SerialName("ja_normal") val jaNormal: String? = null,
    )
}
