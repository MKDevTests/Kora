package snd.komelia.image

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The repairs and the non-repairs both come from the same 722 recognised lines.
 * The non-repairs matter more: seven other homoglyph pairs were tried on that
 * corpus and every one of them was removed for producing no correct repair, so
 * the tests that assert nothing changes are what keeps them out.
 */
class JapaneseOcrRepairTest {

    @Test
    fun `katakana ni next to a kanji is the numeral`() {
        assertEquals("二丁さんの標的と彼女がいる", JapaneseOcrRepair.apply("ニ丁さんの標的と彼女がいる"))
        assertEquals("ヒットマンの二重生活！", JapaneseOcrRepair.apply("ヒットマンのニ重生活！"))
    }

    @Test
    fun `katakana inside a katakana word is left alone`() {
        // The run rule: ニ has a katakana neighbour in all three.
        for (text in listOf("ニュースを見る", "アニキが来た", "ミニだな")) {
            assertEquals(text, JapaneseOcrRepair.apply(text), "broke a katakana word: $text")
        }
    }

    @Test
    fun `the long vowel mark is the numeral only before a counter`() {
        assertEquals("もう一丁はオナカに", JapaneseOcrRepair.apply("もうー丁はオナカに"))
        // じゃー lengthens じゃ. Repaired blindly it became じゃ一俺.
        assertEquals("クククク♡じゃー俺", JapaneseOcrRepair.apply("クククク♡じゃー俺"))
    }

    @Test
    fun `the pairs that were measured wrong are not in the table`() {
        // 3カ月: カ is a counter and is meant to be katakana. カ→力 turned it
        // into 3力月, which is why that pair was dropped along with six others.
        assertEquals("3カ月ホド前まで", JapaneseOcrRepair.apply("3カ月ホド前まで"))
        assertEquals("ムダロきいてねえで", JapaneseOcrRepair.apply("ムダロきいてねえで"))
    }

    @Test
    fun `a lone katakana with no kanji beside it is left alone`() {
        assertEquals("ニ", JapaneseOcrRepair.apply("ニ"))
        assertEquals("そうニね", JapaneseOcrRepair.apply("そうニね"))
    }
}
