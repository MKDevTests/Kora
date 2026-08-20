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

    @Test
    fun `the other two chinese forms are put back as well`() {
        // 统 is simplified-only; 齡 is the traditional form, and the Japanese
        // kyujitai, so folding it to 齢 cannot change what a work meant.
        assertEquals("武勇：16統率：42知力359", JapaneseOcrRepair.apply("武勇：16统率：42知力359"))
        assertEquals("年齢：18", JapaneseOcrRepair.apply("年齡：18"))
    }

    @Test
    fun `句 and 自 between two い are 匂`() {
        assertEquals("いい匂い", JapaneseOcrRepair.apply("いい句い"))
        assertEquals("いい匂いかする", JapaneseOcrRepair.apply("いい自いかする"))
    }

    @Test
    fun `句 keeps its meaning when it is not between two い`() {
        // Offering someone a verse. Without the leading い this would become
        // 一匂いかがですか, which is not a sentence.
        assertEquals("一句いかがですか", JapaneseOcrRepair.apply("一句いかがですか"))
        assertEquals("俳句", JapaneseOcrRepair.apply("俳句"))
        assertEquals("自分", JapaneseOcrRepair.apply("自分"))
    }

    @Test
    fun `a bracket inside a chapter number is dropped`() {
        assertEquals("第4話", JapaneseOcrRepair.apply("第()4話"))
        assertEquals("第3話", JapaneseOcrRepair.apply("第(0)3話"))
    }

    @Test
    fun `brackets outside that one shape are left alone`() {
        assertEquals("第4話", JapaneseOcrRepair.apply("第4話"))
        // No number, so nothing to rescue.
        assertEquals("第(略)話", JapaneseOcrRepair.apply("第(略)話"))
        // A bracket that has nothing to do with a chapter heading.
        assertEquals("百鬼 光太郎(享年32歳)", JapaneseOcrRepair.apply("百鬼 光太郎(享年32歳)"))
    }
}
