package snd.komelia.image

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The protected cases matter more than the repaired ones here: 世 is an ordinary
 * kanji in six common words, and a rule that rewrote those would be far worse
 * than the gloss it removes.
 */
class JapaneseOcrRepairScriptTest {

    @Test
    fun `simplified chinese is put back into japanese`() {
        // Stat screens, which the genre is full of.
        assertEquals("年齢:21 性別:女種族:人族", JapaneseOcrRepair.apply("年龄:21 性别:女種族:人族"))
    }

    @Test
    fun `an ascii hyphen between katakana is the long vowel mark`() {
        assertEquals("127ページ", JapaneseOcrRepair.apply("127ペ-ジ"))
    }

    @Test
    fun `a hyphen anywhere else is left alone`() {
        // A real hyphen from a stat line.
        assertEquals("内政:5-魅力:31", JapaneseOcrRepair.apply("内政:5-魅力:31"))
        assertEquals("A-B", JapaneseOcrRepair.apply("A-B"))
    }

    @Test
    fun `世 surrounded by kana is the phonetic gloss`() {
        assertEquals(
            "この船…アインホルンはせんいん船員なんかいらねーんだよ",
            JapaneseOcrRepair.apply("この船…アインホルンは世んいん船員なんかいらねーんだよ"),
        )
        assertEquals("ませき魔石", JapaneseOcrRepair.apply("ま世き魔石"))
        assertEquals("せかい世界の危機", JapaneseOcrRepair.apply("世かい世界の危機"))
    }

    @Test
    fun `世 beside a kanji is itself`() {
        // Every one of these is in the corpus, and all six are common words.
        for (text in listOf("世界", "世間でも馬鹿王子", "実は前世の記憶", "何故か今世で", "出世できる人物", "異世界最強")) {
            assertEquals(text, JapaneseOcrRepair.apply(text))
        }
    }

    @Test
    fun `two 世 in a row are both kanji`() {
        // 世世俺の前世の妹 — each has the other beside it, so neither fires.
        assertEquals("世世俺の前世の妹", JapaneseOcrRepair.apply("世世俺の前世の妹"))
    }
}
