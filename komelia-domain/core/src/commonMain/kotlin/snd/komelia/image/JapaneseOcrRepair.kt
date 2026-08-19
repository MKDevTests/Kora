package snd.komelia.image

/**
 * Puts back the kanji the recogniser replaced with a katakana that is drawn the
 * same way.
 *
 * The Japanese counterpart of [OcrSpellRepair], and it exists for the same
 * reason: a misread word cannot be translated into the right one. 二丁 is the
 * name of a character in the volume this was measured on, and the recogniser
 * returned ニ丁 — katakana ni, which is two strokes in the same place as the
 * kanji for two. The translator then had a word it had never seen and answered
 * "Nicho", "deux cho", or nothing at all, on eight different pages.
 *
 * ## Why the table is two entries and not nine
 *
 * The obvious pairs are ロ/口, カ/力, タ/夕, ハ/八, エ/工, ト/卜 and ス/又. All
 * seven were tried over 722 recognised lines from two volumes. Between them
 * they produced no correct repair and one wrong one: 3カ月 ("three months",
 * where カ is a counter and is meant to be katakana) came out 3力月. So they are
 * not here. ニ→二 fired eight times, all eight correct, and ー→一 once.
 *
 * ## The two rules that survived
 *
 * A katakana next to another katakana belongs to a katakana word and is left
 * alone — that is what keeps ミニ, アニキ and ニュース intact. What is left is a
 * lone katakana with a kanji beside it, which is a kanji context.
 *
 * ー needs one more condition. It is the long-vowel mark far more often than it
 * is the numeral, and it lengthens a hiragana kana as happily as a katakana one:
 * じゃー became じゃ一. What separates the numeral is what follows, because 一
 * before a counter is a number and counters are a closed class.
 */
object JapaneseOcrRepair {

    private val homoglyphs = mapOf('ニ' to '二', 'ー' to '一')

    private const val COUNTERS = "丁人本回個度発杯枚匹台冊軒階年月日時分秒歳番件"

    fun apply(text: String): String {
        if (text.isEmpty()) return text

        var out: StringBuilder? = null
        for (i in text.indices) {
            val kanji = homoglyphs[text[i]] ?: continue
            val before = text.getOrNull(i - 1)
            val after = text.getOrNull(i + 1)
            // Part of a katakana word: not a misread kanji.
            if (before?.isKatakana() == true || after?.isKatakana() == true) continue
            // Needs a kanji beside it — that is what makes this a kanji context.
            if (before?.isKanji() != true && after?.isKanji() != true) continue
            if (text[i] == 'ー' && (after == null || after !in COUNTERS)) continue

            val builder = out ?: StringBuilder(text).also { out = it }
            builder[i] = kanji
        }
        return out?.toString() ?: text
    }

    private fun Char.isKatakana(): Boolean = this in 'ァ'..'ヺ' || this == 'ー'

    private fun Char.isKanji(): Boolean = this in '一'..'鿿'
}
