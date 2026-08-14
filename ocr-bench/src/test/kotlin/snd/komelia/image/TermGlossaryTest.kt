package snd.komelia.image

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Sentences here are real ones from the translation bench corpus, and the
 * failures they encode were measured rather than imagined.
 */
class TermGlossaryTest {

    @Test
    fun `a name flattened by sentence casing gets its capital back`() {
        // Trigun 01: MERYL STRIFE goes out as "Meryl strife" and comes back as
        // "Meryl discorde" — strife reads as a common noun once lowercased.
        val glossary = TermGlossary(mapOf("Strife" to "Strife", "Bernardelli" to "Bernardelli"))

        val prepared = glossary.restoreTerms(
            "Meryl strife, and I represent the bernardelli insurance society."
        )

        assertEquals(
            "Meryl Strife, and I represent the Bernardelli insurance society.",
            prepared,
        )
    }

    @Test
    fun `a term with a settled translation is put back afterwards`() {
        val glossary = TermGlossary(mapOf("The Force" to "la Force"))

        assertEquals("Tu ne connais rien à la Force.", glossary.applyTo("Tu ne connais rien à The Force."))
    }

    @Test
    fun `the longest term wins`() {
        val glossary = TermGlossary(mapOf("Wayne" to "Wayne", "Wayne Manor" to "Manoir Wayne"))

        assertEquals("Retour au Manoir Wayne.", glossary.applyTo("Retour au Wayne Manor."))
    }

    @Test
    fun `a term inside a longer word is left alone`() {
        // 'Force' must not turn 'forcement' into something else, and a name must
        // not be found inside another name.
        val glossary = TermGlossary(mapOf("Force" to "Force", "Ana" to "Ana"))

        assertEquals("Il a forcé la porte, Anastasia.", glossary.restoreTerms("Il a forcé la porte, Anastasia."))
    }

    @Test
    fun `an empty glossary changes nothing`() {
        val text = "Meryl strife, and I represent the bernardelli insurance society."

        assertEquals(text, TermGlossary.EMPTY.restoreTerms(text))
        assertEquals(text, TermGlossary.EMPTY.applyTo(text))
    }
}
