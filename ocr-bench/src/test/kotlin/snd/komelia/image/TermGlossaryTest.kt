package snd.komelia.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Sentences here are real ones from the translation bench corpus, and the
 * failures they encode were measured rather than imagined.
 */
class TermGlossaryTest {

    @Test
    fun `a name never reaches the translator as a word it can translate`() {
        // Trigun 01, the case that started this: the tablet sent "Meryl Strife"
        // with its capital restored and got back "Meryl Conflife". Capitalising
        // is not protection; leaving is.
        val glossary = TermGlossary(mapOf("Strife" to "Strife", "Bernardelli" to "Bernardelli"))

        val protectedText = glossary.protect(
            "Meryl strife, and I represent the bernardelli insurance society."
        )

        assertFalse(protectedText.text.contains("strife", ignoreCase = true))
        assertFalse(protectedText.text.contains("bernardelli", ignoreCase = true))
        assertTrue(protectedText.isProtected)
    }

    @Test
    fun `the terms come back where the engine left the placeholders`() {
        val glossary = TermGlossary(mapOf("Strife" to "Strife", "Bernardelli" to "Bernardelli"))

        val protectedText = glossary.protect(
            "Meryl strife, and I represent the bernardelli insurance society."
        )
        // What Bergamot returns for this sentence, placeholders included.
        val translated = protectedText.text
            .replace(", and I represent the", ", et je représente la")
            .replace("insurance society.", "société d'assurance.")

        assertEquals(
            "Meryl Strife, et je représente la Bernardelli société d'assurance.",
            protectedText.restore(translated),
        )
    }

    @Test
    fun `a term with a settled translation comes back in its chosen wording`() {
        val glossary = TermGlossary(mapOf("The Force" to "la Force"))

        val protectedText = glossary.protect("You know nothing of The Force.")

        assertEquals(
            "Tu ne connais rien à la Force.",
            protectedText.restore("Tu ne connais rien à ${TermGlossary.placeholder(0)}."),
        )
    }

    @Test
    fun `the longest term wins`() {
        val glossary = TermGlossary(mapOf("Wayne" to "Wayne", "Wayne Manor" to "Manoir Wayne"))

        val protectedText = glossary.protect("Back to Wayne Manor.")

        // One placeholder, not two: "Wayne" must not have eaten half of it.
        assertEquals(1, protectedText.text.count { it == '⟦' })
        assertEquals(
            "Retour au Manoir Wayne.",
            protectedText.restore("Retour au ${TermGlossary.placeholder(0)}."),
        )
    }

    @Test
    fun `a term inside a longer word is left alone`() {
        // 'Force' must not touch 'forcé', and a name must not be found inside
        // another name.
        val glossary = TermGlossary(mapOf("Force" to "Force", "Ana" to "Ana"))
        val text = "Il a forcé la porte, Anastasia."

        assertEquals(text, glossary.protect(text).text)
    }

    @Test
    fun `a placeholder the engine dropped leaves the sentence readable`() {
        // Measured at 0 out of 200 on Bergamot, but ML Kit is another engine and
        // this must not put a stray bracket on the page.
        val glossary = TermGlossary(mapOf("Strife" to "Strife"))

        val protectedText = glossary.protect("I'm sorry, strife, but we are busy.")

        assertEquals(
            "Je suis désolé, mais on est occupé.",
            protectedText.restore("Je suis désolé, mais on est occupé."),
        )
    }

    @Test
    fun `a placeholder the engine duplicated is restored once and cleaned up`() {
        val glossary = TermGlossary(mapOf("Strife" to "Strife"))

        val protectedText = glossary.protect("strife, tell me.")
        val token = TermGlossary.placeholder(0)

        // The engine emitted the same placeholder twice. Restoring both would
        // put the name somewhere the sentence never had it, so the copy goes.
        val restored = protectedText.restore("$token, dis-moi. $token")

        assertEquals("Strife, dis-moi.", restored)
        assertFalse(restored.contains('⟦'))
    }

    @Test
    fun `an empty glossary changes nothing`() {
        val text = "Meryl strife, and I represent the bernardelli insurance society."

        val protectedText = TermGlossary.EMPTY.protect(text)

        assertEquals(text, protectedText.text)
        assertFalse(protectedText.isProtected)
        assertEquals(text, protectedText.restore(text))
    }
}
