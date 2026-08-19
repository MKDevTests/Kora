package snd.komelia.image

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every sentence here is a real bubble from the corpus, and the French quoted
 * in the comments is what the shipped pivot actually returned for it. The point
 * of the test is the measurement: three entries earned their place and three
 * more were refused, and an entry added later without the same measurement is
 * the thing this file exists to make awkward.
 */
class JapaneseDomainGlossaryTest {

    @Test
    fun `the hero is a title, not an adjective`() {
        // "les courageux ont vaincu le roi démon" -> "Il était une fois, un
        // héros a vaincu le roi démon".
        assertEquals(
            "その昔英雄が魔王を倒して世界が平和になったらしい",
            JapaneseDomainGlossary.apply("その昔勇者が魔王を倒して世界が平和になったらしい"),
        )
    }

    @Test
    fun `the adventuring party is a team, not a celebration`() {
        // "Il y a d'autres fêtes dans cette ville" -> "d'autres équipes".
        assertEquals(
            "この街には他に条件の合うチームは",
            JapaneseDomainGlossary.apply("この街には他に条件の合うパーティーは"),
        )
    }

    @Test
    fun `ateji are rewritten to the reading they stand for`() {
        // 流石 is spelled with characters chosen for sound, not meaning, so the
        // model reads them: "C'est un rocher, M. Allen" -> "C'est vrai".
        assertEquals("さすがですアレン様", JapaneseDomainGlossary.apply("流石ですアレン様"))
    }

    @Test
    fun `a term inside a longer word is rewritten with it`() {
        // 勇者パーティー carries both terms and both are meant to fire.
        assertEquals(
            "お前には英雄チームを抜けてもらう",
            JapaneseDomainGlossary.apply("お前には勇者パーティーを抜けてもらう"),
        )
        // パーティーメンバー keeps its tail: the rewrite is of the term, not of
        // the run it sits in.
        assertEquals("チームメンバー", JapaneseDomainGlossary.apply("パーティーメンバー"))
    }

    @Test
    fun `the terms that were measured and refused are not in the table`() {
        // ハーレム -> 後宮 turned "La guerre de Harlem" into "Gogusenki1".
        assertEquals("成り上がるハーレム戦記", JapaneseDomainGlossary.apply("成り上がるハーレム戦記"))
        // 亜人 is a demi-human; 獣人 is a beast-person, a different creature,
        // and it did not even win on the sentences.
        assertEquals("人類と亜人が混在する世界", JapaneseDomainGlossary.apply("人類と亜人が混在する世界"))
        // ラスボス -> 魔王 measured 3 gains and 0 losses and is still refused:
        // a last boss is not always the demon king, and one of those three
        // lines is about the second game in a series.
        assertEquals("二作目のラスボスは…", JapaneseDomainGlossary.apply("二作目のラスボスは…"))
    }

    @Test
    fun `a sentence with none of the terms is untouched`() {
        for (text in listOf("おい起きろ", "何言ってるの！", "はい", "")) {
            assertEquals(text, JapaneseDomainGlossary.apply(text))
        }
    }
}
