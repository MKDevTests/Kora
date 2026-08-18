package snd.komelia.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every case here is a line that actually came off a page, kept so the rule that
 * fixed it cannot be undone by the next one.
 */
class TranslationTextUtilsTest {

    @Test
    fun `bare lettering is a sound effect`() {
        listOf(
            "Ping", "Whack", "Swoop", "Sob", "Seizure", "Touch", "Blush",
            "Throb", "Snatch", "Shine", "Clench", "Rummage", "Swish", "Clink",
            "Jolt", "Twitch twitch", "Rattle rattle", "Shake shake",
        ).forEach {
            assertTrue(TranslationTextUtils.isSoundEffect(it), "'$it' should be left on the artwork")
        }
    }

    @Test
    fun `short dialogue is not a sound effect`() {
        // These all carry punctuation, which is what separates them from
        // lettering drawn onto the page.
        listOf(
            "Correct.", "Ever.", "Quite.", "Wait.", "Here.", "Ditto.", "Huh?",
            "Huh?!", "Uh...?!", "Eh?", "It...!", "Oh, right.",
        ).forEach {
            assertFalse(TranslationTextUtils.isSoundEffect(it), "'$it' is dialogue")
        }
    }

    @Test
    fun `two different words are a sentence fragment, not a sound effect`() {
        // Bubbles that split one sentence across panels: 'Romantic match',
        // 'So happy', 'I knew'. Only a repeated word is lettering.
        listOf("Romantic match", "So happy", "I knew", "One hundred").forEach {
            assertFalse(TranslationTextUtils.isSoundEffect(it), "'$it' is part of a sentence")
        }
    }

    @Test
    fun `a word broken across two lines is rejoined`() {
        assertEquals(
            "A BUSINESS CARD?",
            TranslationTextUtils.rejoinLineBreaks("A BUSI- NESS CARD?"),
        )
    }

    @Test
    fun `an honorific keeps its hyphen`() {
        assertTrue(
            "MAMA-SAN" in TranslationTextUtils.rejoinLineBreaks("MAMA- SAN"),
            "the hyphen before an honorific must survive, or 'Maman-san' comes back",
        )
    }

    @Test
    fun `an animal cry in a balloon is a sound effect despite its punctuation`() {
        listOf("Meow!", "MEOW!", "Woof!", "Purr...", "Nyaa~!", "Meow meow!", "Ribbit.")
            .forEach { assertTrue(TranslationTextUtils.isSoundEffect(it), it) }
    }

    @Test
    fun `the same word inside a sentence is still dialogue`() {
        listOf(
            "Did the cat meow?",
            "I heard a meow.",
            "Meow is what she said.",
            "Don't growl at me!",
        ).forEach { assertTrue(!TranslationTextUtils.isSoundEffect(it), it) }
    }

}

/** Names keep their capital when the balloon is lowered. */
class HonorificCaseTest {

    @Test
    fun `a name before an honorific keeps its capital`() {
        // "Mais le ritsu-kun a raison" -- lowered to a common noun, the
        // translator gave it an article. Measured on a real page.
        assertEquals(
            "But Ritsu-kun is right...",
            TranslationTextUtils.toSentenceCase("BUT RITSU-KUN IS RIGHT..."),
        )
        assertEquals(
            "Get in, Nohara-kun.",
            TranslationTextUtils.toSentenceCase("GET IN, NOHARA-KUN."),
        )
        assertEquals(
            "I like you too, Dei-san",
            TranslationTextUtils.toSentenceCase("I LIKE YOU TOO, DEI-SAN"),
        )
    }

    @Test
    fun `an ordinary hyphenated word is left lowered`() {
        // The narrow half of the honorific list only: these are English
        // words, and nothing may read a person into them.
        assertEquals("He has a sun-tan.", TranslationTextUtils.toSentenceCase("HE HAS A SUN-TAN."))
        assertEquals("A double-chin.", TranslationTextUtils.toSentenceCase("A DOUBLE-CHIN."))
    }

    @Test
    fun `a balloon that was not all caps is untouched`() {
        assertEquals("but ritsu-kun is right", TranslationTextUtils.toSentenceCase("but ritsu-kun is right"))
    }
}

/** Names spoken to, which the lowering pass used to bury. */
class AddressedByNameTest {

    private val lexicon = java.io.File(
        "../komelia-ui/src/commonMain/composeResources/files/lexicon/en.txt"
    )

    init {
        OcrSpellRepair.load(lexicon.readLines().filter { it.isNotBlank() }.toSet())
    }

    @Test
    fun `a name after a comma keeps its capital`() {
        // All three measured on real pages: the name was read as a common noun
        // and given an article, or eaten outright.
        assertEquals(
            "You trying to say something about women, Rankin?",
            TranslationTextUtils.toSentenceCase("YOU TRYING TO SAY SOMETHING ABOUT WOMEN, RANKIN?"),
        )
        assertEquals("Let's go, Olto!", TranslationTextUtils.toSentenceCase("LET'S GO, OLTO!"))
        assertEquals("Focus, Takeshi.", TranslationTextUtils.toSentenceCase("FOCUS, TAKESHI."))
    }

    @Test
    fun `an ordinary word after a comma is left alone`() {
        // The lexicon is what separates the two, and it is checked first.
        assertEquals(
            "Well, maybe, but not today.",
            TranslationTextUtils.toSentenceCase("WELL, MAYBE, BUT NOT TODAY."),
        )
    }

    @Test
    fun `a name anywhere else is not touched`() {
        // Measured and rejected: applied to the whole sentence this stopped
        // ordinary English being translated at all, because the shipped list
        // does not carry every word.
        assertEquals(
            "Its turbid waters.",
            TranslationTextUtils.toSentenceCase("ITS TURBID WATERS."),
        )
    }
}

class SurnameAfterGivenNameTest {

    private val lexicon = java.io.File(
        "../komelia-ui/src/commonMain/composeResources/files/lexicon/en.txt"
    )

    init {
        OcrSpellRepair.load(lexicon.readLines().filter { it.isNotBlank() }.toSet())
    }

    @Test
    fun `a surname behind a given name keeps its capital`() {
        // Measured: the surname was read as a common noun once the balloon
        // was lowered, and only the first word of a sentence gets a capital
        // back, so the given name stands alone and the family name does not.
        assertEquals(
            "That's me. Takeshi Kovacs. Formerly employed as an envoy.",
            TranslationTextUtils.toSentenceCase(
                "THAT'S ME. TAKESHI KOVACS. FORMERLY EMPLOYED AS AN ENVOY."
            ),
        )
    }

    @Test
    fun `two ordinary words are left alone`() {
        // The lexicon carries both, which is what keeps this off English.
        assertEquals(
            "Never mind that.",
            TranslationTextUtils.toSentenceCase("NEVER MIND THAT."),
        )
    }

    @Test
    fun `a known word behind an unknown one is left alone`() {
        // Half a pair is not a person: "Kovacs said" must not become
        // "Kovacs Said".
        assertEquals(
            "Kovacs said nothing.",
            TranslationTextUtils.toSentenceCase("KOVACS SAID NOTHING."),
        )
    }
}
