package snd.komelia.image

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks the dialect table that actually ships.
 *
 * Every case below is a line from the 50-page reading log, and every failure
 * guarded against is one the naive substring version really produced.
 */
class JapaneseKansaiNormaliserTest {

    private val shipped = File(
        "../komelia-ui/src/commonMain/composeResources/files/japanese/kansai.json"
    )

    private fun loadShipped() {
        assertTrue(shipped.isFile, "the shipped kansai table is missing at ${shipped.absolutePath}")
        JapaneseKansaiNormaliser.resetForTest()
        JapaneseKansaiNormaliser.load(shipped.readText())
    }

    @Test
    fun `hen is a negation, not the middle of sonohen`() {
        loadShipped()
        // そのへん is "around there". Unguarded, the generic ~へん rule turned
        // そのへんで into そのないで on a real page of the bench corpus.
        assertEquals(
            "そのへんで…",
            JapaneseKansaiNormaliser.apply("そのへんで…"),
        )
        // The guard is the -a column: a Kansai negation attaches to the 未然形.
        // 逃がさ-へん qualifies, and still fires.
        assertEquals(
            "逃がさない",
            JapaneseKansaiNormaliser.apply("逃がさへん"),
        )
    }

    @Test
    fun `yaro is the copula, never the tail of a volitive`() {
        loadShipped()
        // Both produced だろうう -- not a word in any language -- before the
        // guard. The う is the whole difference.
        assertEquals(
            "ほらほら触ってやろうかぁ？",
            JapaneseKansaiNormaliser.apply("ほらほら触ってやろうかぁ？"),
        )
        assertEquals(
            "せめて話だけでも聞いてやろうぜ",
            JapaneseKansaiNormaliser.apply("せめて話だけでも聞いてやろうぜ"),
        )
        // And it still fires where it is really the copula.
        assertEquals(
            "そうだろう？",
            JapaneseKansaiNormaliser.apply("そうやろ？"),
        )
    }

    @Test
    fun `kaina closes a question and does nothing inside shikainai`() {
        loadShipped()
        // 四人しかいない is "there are only four of them". The rule cut it to
        // 四人しかない, which changes the verb of existence.
        assertEquals(
            "世界に四人しかいない勇者",
            JapaneseKansaiNormaliser.apply("世界に四人しかいない勇者"),
        )
        assertEquals(
            "そうかな？",
            JapaneseKansaiNormaliser.apply("そうかいな？"),
        )
    }

    @Test
    fun `yuu alone is gone, the past and te forms stay`() {
        loadShipped()
        // そうきゆう is 早急 with its reading run together by the recogniser.
        // ゆう→言う fired inside it and produced そうき言う早急に. Measured over
        // 1686 balloons the bare key fired once, wrongly, and never usefully.
        assertEquals(
            "そうきゆう早急にお帰り頂こう",
            JapaneseKansaiNormaliser.apply("そうきゆう早急にお帰り頂こう"),
        )
        // The trailing やろ IS the copula here, at the end of the clause, so it
        // normalises too -- both rules doing their job on one line.
        assertEquals("言っただろう", JapaneseKansaiNormaliser.apply("ゆうたやろ"))
        assertEquals("言って", JapaneseKansaiNormaliser.apply("ゆうて"))
    }

    @Test
    fun `puts the negation back`() {
        loadShipped()
        // Returned "peut être trouvé" — the opposite of the line — until ~へん
        // existed as a generic rule rather than verb by verb.
        assertEquals(
            "こんな名器今時熱海でも見つからないぞ",
            JapaneseKansaiNormaliser.apply("こんな名器今時熱海でも見つからへんど"),
        )
        assertEquals(
            "高津のアニキもビビってしばらく手ェ出してきいないだろ！",
            JapaneseKansaiNormaliser.apply("高津のアニキもビビってしばらく手ェ出してきいへんやろ！"),
        )
    }

    @Test
    fun `a short key never fires inside a longer one`() {
        loadShipped()
        // やな→だな inside やない and やなくて produced "da nai" and "da nakute",
        // which the engine read as an affirmation.
        assertEquals("オホッええ反応するじゃないか♡", JapaneseKansaiNormaliser.apply("オホッええ反応するやないかい♡"))
        assertTrue(
            JapaneseKansaiNormaliser.apply("サオやなくて玉がデカイんちゃうか！").startsWith("サオじゃなくて"),
            "やな fired inside やなくて again",
        )
    }

    @Test
    fun `chau is only the verb at the head of a balloon or after n`() {
        loadShipped()
        // 見えちゃう and 落ちちゃう are the contraction of ~てしまう. Rewritten to
        // 違う the line came back "Voyez-vous cela différemment ?".
        assertEquals(
            "これじゃ見えちゃうか…じゃあズボンの中……だと落ちちゃうか",
            JapaneseKansaiNormaliser.apply("これじゃ見えちゃうか…じゃあズボンの中……だと落ちちゃうか"),
        )
        // ちゃうで is its own entry, so it carries the standard particle too.
        assertEquals("違うよ", JapaneseKansaiNormaliser.apply("ちゃうで"))
        assertEquals("違うか", JapaneseKansaiNormaliser.apply("ちゃうか"))
    }

    @Test
    fun `han is san after a name and nothing inside a word`() {
        loadShipped()
        assertEquals("二丁さん！", JapaneseKansaiNormaliser.apply("二丁はん！"))
        // ごはん is not a person.
        assertEquals("ごはん食べる", JapaneseKansaiNormaliser.apply("ごはん食べる"))
    }

    @Test
    fun `a katakana key must cover its whole run`() {
        JapaneseKansaiNormaliser.resetForTest()
        JapaneseKansaiNormaliser.loadForTest(listOf("ワシ" to "俺", "ワシャ" to "俺は"))
        assertEquals("俺は行く", JapaneseKansaiNormaliser.apply("ワシャ行く"))
        assertEquals("俺の番だ", JapaneseKansaiNormaliser.apply("ワシの番だ"))
        // ワシントン is a place, not a first-person pronoun.
        assertEquals("ワシントンに行く", JapaneseKansaiNormaliser.apply("ワシントンに行く"))
    }

    @Test
    fun `never puts anything but Japanese into the sentence`() {
        loadShipped()
        val latin = Regex("[A-Za-z\\u00C0-\\u017F]")
        val samples = listOf(
            "ワシャ“コンビニ”とパイプあるから何でも知ってるんヤド！",
            "なんでニ丁がワレみたいな三下の名前知っとんねん",
            "ポコチン見せんかい！",
            "どうゆう事やコラッ!？",
        )
        for (text in samples) {
            val out = JapaneseKansaiNormaliser.apply(text)
            assertTrue(!latin.containsMatchIn(out), "latin text injected into Japanese: $out")
        }
    }

    @Test
    fun `han is san before a particle, never inside gohan`() {
        loadShipped()
        // 二丁はんが came back "deux-cho han" while the guard demanded a
        // terminal after はん. What keeps ごはん safe is the ご before it.
        assertEquals("二丁さんがノコノコやってきた", JapaneseKansaiNormaliser.apply("二丁はんがノコノコやってきた"))
        assertEquals("二丁さん！", JapaneseKansaiNormaliser.apply("二丁はん！"))
        // 言うといて is not in the table — only 言うとく is — so it stays as it
        // is. The point of the line is the はん before it.
        assertEquals("親分さんに言うといて", JapaneseKansaiNormaliser.apply("親分はんに言うといて"))
        for (text in listOf("ごはんを食べる", "ごはんが好き", "一般的な話")) {
            assertEquals(text, JapaneseKansaiNormaliser.apply(text), "fired outside an honorific: $text")
        }
    }

    @Test
    fun `ware is the katakana pronoun and only when it is the whole run`() {
        loadShipped()
        assertEquals("お前が二丁かい", JapaneseKansaiNormaliser.apply("ワレが二丁かい"))
        // ワレワレ is one run of four, so the two-character key cannot claim it,
        // and 言われた is hiragana, which the katakana guard never reaches.
        assertEquals("ワレワレは宇宙人だ", JapaneseKansaiNormaliser.apply("ワレワレは宇宙人だ"))
        assertEquals("言われたからだまってた", JapaneseKansaiNormaliser.apply("言われたからだまってた"))
    }
}
