package snd.komelia.image

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The English cases are the important half of this file. Every one of them is a
 * real bubble that the obvious version of this rule -- collapse any word
 * repeated three times -- turned into a loss, and they are what the source
 * condition exists for.
 */
class TranslationOutputRepairTest {

    private fun repair(source: String, translated: String) =
        TranslationOutputRepair.collapseInventedRepeats(source, translated)

    @Test
    fun `a loop the decoder invented is collapsed`() {
        // Both of these had a perfectly good second half behind the loop, which
        // is why the repair collapses rather than rejects.
        assertEquals(
            "Non. Haku cherchait une source chaude",
            repair("そういやハクは呪いに効く温泉を探してたっけ", "Non, non, non, non. Haku cherchait une source chaude"),
        )
        assertEquals(
            "Non. Je ne peux pas dire ça à mes clients...",
            repair("やだなあお客様にそんなふうに言われるなんて…", "Non, non, non, non. Je ne peux pas dire ça à mes clients..."),
        )
    }

    @Test
    fun `an elongated sound is not a repetition, so it stays eligible`() {
        // だめえ～ is one word with a drawn-out vowel, not a repeated segment.
        assertEquals("Non. ♡", repair("だめえ～♡", "Non, non, non. ♡"))
    }

    @Test
    fun `repetition the letterer wrote is kept`() {
        // All four are real English bubbles, and all four are losses without
        // the source condition. The last is the title of a series.
        assertEquals("Mwa ha ha ha ha ha !", repair("Mwa ha ha ha ha ha!", "Mwa ha ha ha ha ha !"))
        assertEquals("Whoa, whoa, whoa ! Range ça.", repair("Whoa, whoa, whoa! Put that away.", "Whoa, whoa, whoa ! Range ça."))
        assertEquals("Chomp chomp chomp", repair("Chomp chomp chomp", "Chomp chomp chomp"))
        assertEquals(
            "Les 100 copines qui t'aiment vraiment, vraiment, vraiment",
            repair(
                "The 100 girlfriends who really, really, really, really, REALLY love you",
                "Les 100 copines qui t'aiment vraiment, vraiment, vraiment",
            ),
        )
    }

    @Test
    fun `a repeated segment in the source protects the output too`() {
        // 待て待て repeats a segment, so its runaway "Attends, attends..." is
        // left alone. This is the price of the source condition, paid knowingly.
        assertEquals(
            "Attends, attends, attends, attends,",
            repair("待て待て俺の方が！", "Attends, attends, attends, attends,"),
        )
    }

    @Test
    fun `french repeats some words legitimately`() {
        // Collapsing at two occurrences instead of three broke this one, and
        // every reflexive "vous vous" with it.
        val reflexive = "Jusqu'à ce que nous nous retrouvions ici"
        assertEquals(reflexive, repair("こいっと出会うまでは…", reflexive))
        assertEquals("Il faut que vous vous prépariez", repair("準備してください", "Il faut que vous vous prépariez"))
        // Two is not three.
        assertEquals("Hé, hé !", repair("おい", "Hé, hé !"))
    }

    @Test
    fun `an ordinary sentence is untouched`() {
        for (text in listOf("Je m'occupe de ma sœur.", "Oui.", "")) {
            assertEquals(text, repair("なんでもない", text))
        }
    }

    @Test
    fun `every japanese bubble the corpus run collapsed`() {
        // The complete list, so the count in the class doc can be checked
        // against something. Running the shipped pivot over the 1646 unique
        // Japanese bubbles collapsed exactly these nine.
        val cases = listOf(
            Triple("いえ…", "Non, non, non.", "Non."),
            Triple("いや…", "Non, non, non.", "Non."),
            Triple("いやあく", "Non, non, non.", "Non."),
            Triple("お…おうぼう", "oh, oh, oh, oh, oh, oh,", "oh,"),
            Triple("きょっかいいや曲解！", "Non, non, non. C'est une honte !", "Non. C'est une honte !"),
            Triple("だめえ～♡", "Non, non, non. ♡", "Non. ♡"),
            Triple("ひゃうううん！", "Non, non, non !", "Non !"),
        )
        cases.forEach { (source, engine, expected) ->
            assertEquals(expected, repair(source, engine), source)
        }
    }

    @Test
    fun `every english bubble the naive rule would have broken`() {
        // The other twelve. Not one of them may change: these are what the
        // source condition is for, and they are the reason this rule is not
        // simply "collapse a word repeated three times".
        val untouched = listOf(
            "BeeP BeeP Beep" to "BeeP BeeP Beep",
            "BeeP BeeP Beep EP" to "BeeP BeeP Beep EP",
            "Chicken & Rice Pizza Pizza Pizza Pizza" to "Poulet et riz Pizza Pizza Pizza Pizza",
            "Cofus Cofus cofus" to "Cofus Cofus cofus",
            "Focus, Takeshi. Focus focus focus" to "Concentre-toi, Takeshi. Focus focus focus focus",
            "Really, really, really," to "Vraiment, vraiment, vraiment,",
            "Whoa, whoa, whoa!" to "Whoa, whoa, whoa !",
        )
        untouched.forEach { (source, engine) ->
            assertEquals(engine, repair(source, engine), source)
        }
    }
}
