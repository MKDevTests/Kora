package snd.komelia.image

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks the rewrite against the glossary that actually ships.
 *
 * The false positives below are real: every one of them fired on the box
 * captures of Kyou kara Hitman volumes 1 and 2 when the rule was a plain
 * "contains" search, and each changed the meaning of a line. They are the
 * reason the match has to be aligned to a katakana run, so they are the cases
 * worth protecting.
 */
class JapaneseKatakanaGlossaryTest {

    private val shipped = File(
        "../komelia-ui/src/commonMain/composeResources/files/japanese/katakana.json"
    )

    /**
     * The glossary is an object and [JapaneseKatakanaGlossary.load] is a no-op
     * once something is loaded, so every test starts from nothing rather than
     * inheriting whichever one ran first.
     */
    private fun loadShipped() {
        assertTrue(shipped.isFile, "the shipped glossary is missing at ${shipped.absolutePath}")
        JapaneseKatakanaGlossary.resetForTest()
        JapaneseKatakanaGlossary.load(shipped.readText())
    }

    @Test
    fun `rewrites the katakana that makes the engine invent names`() {
        loadShipped()
        assertEquals("小便か！？", JapaneseKatakanaGlossary.apply("ションベンか！？"))
        assertEquals("ほんの2時間ほど前まで", JapaneseKatakanaGlossary.apply("ほんの2時間ホド前まで"))
        assertEquals("あそこだ…", JapaneseKatakanaGlossary.apply("アソコだ…"))
    }

    @Test
    fun `a key never fires inside a longer katakana word`() {
        // リーマン in サラリーマン, ケッ in ケッコウ, タラ in デタラメ, スラ in
        // スライド, トカ in ナントカ. All five fired before the run rule.
        JapaneseKatakanaGlossary.loadForTest(
            listOf(
                "リーマン" to "会社員",
                "ケッ" to "けっ",
                "タラ" to "たら",
                "スラ" to "すら",
                "トカ" to "とか",
            )
        )
        for (text in listOf("平凡なサラリーマンに", "ケッコウ美人", "デタラメだ", "スライドさせる", "ナントカする")) {
            assertEquals(text, JapaneseKatakanaGlossary.apply(text), "fired inside a longer word: $text")
        }
    }

    @Test
    fun `a key may carry trailing kana past the run`() {
        // アンタ is the whole katakana run and ら follows it in hiragana, so the
        // longer key has to be reachable — otherwise only the shorter one ever
        // matches and the plural is lost.
        JapaneseKatakanaGlossary.loadForTest(listOf("アンタら" to "あんたら", "アンタ" to "あんた"))
        assertEquals("あんたらが悪い", JapaneseKatakanaGlossary.apply("アンタらが悪い"))
        assertEquals("あんたが悪い", JapaneseKatakanaGlossary.apply("アンタが悪い"))
    }

    @Test
    fun `the long vowel mark belongs to the run`() {
        // The file carries バカヤロウ; the page says バカヤロー. If ー did not
        // end the run, バカヤロ would look like a fragment and nothing would
        // fire at all.
        JapaneseKatakanaGlossary.loadForTest(listOf("バカヤロー" to "馬鹿野郎", "バカ" to "ばか"))
        assertEquals("馬鹿野郎が…", JapaneseKatakanaGlossary.apply("バカヤローが…"))
    }

    @Test
    fun `longest key wins`() {
        JapaneseKatakanaGlossary.loadForTest(listOf("ヤロウ" to "野郎", "コノヤロウ" to "この野郎"))
        assertEquals("この野郎", JapaneseKatakanaGlossary.apply("コノヤロウ"))
    }

    @Test
    fun `never puts anything but Japanese into the sentence`() {
        loadShipped()
        // The one regression this file has already produced: a French gloss
        // read as if it were the rewrite put "je" inside four Japanese
        // sentences. Nothing the glossary inserts may be Latin.
        val latin = Regex("[A-Za-z\\u00C0-\\u017F]")
        val samples = listOf(
            "俺が運転してるウチは殺されないで済むよな",
            "ウチ明日は行かない！",
            "目的のタメなら何にでもなれる",
            "テメーがいい加減な事ぬかすな",
        )
        for (text in samples) {
            val out = JapaneseKatakanaGlossary.apply(text)
            assertTrue(!latin.containsMatchIn(out), "latin text injected into Japanese: $out")
        }
    }
}
