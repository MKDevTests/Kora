package snd.komelia.image

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Checks the repair against the word list that actually ships, not a stub.
 *
 * The cases below are real OCR output, taken from the box captures of Ramen Aka
 * Neko 164 and 167. UINTRAINED is the one that started this: it turned "an
 * untrained cat" into "un chat entraîné" on the tablet, which says the opposite.
 */
class OcrSpellRepairTest {

    private val lexicon = File(
        "../komelia-ui/src/commonMain/composeResources/files/lexicon/en.txt"
    )

    init {
        assertTrue(lexicon.isFile, "shipped lexicon missing at ${lexicon.absolutePath}")
        OcrSpellRepair.load(lexicon.readLines().filter { it.isNotBlank() }.toSet())
    }

    @Test
    fun `repairs the misreads found on real pages`() {
        // Every one of these is 'u' lettered in a comic and read back as two
        // thin strokes. They are the whole of what the repair caught over 28
        // pages, and each one had gone to the translator as a non-word.
        assertEquals("UNTRAINED", OcrSpellRepair.repair("UINTRAINED"))
        assertEquals("PUDDING", OcrSpellRepair.repair("PUIDDING"))
        assertEquals("UNIQUE", OcrSpellRepair.repair("UINIQUE"))
        assertEquals("AMATEUR", OcrSpellRepair.repair("AMATEUIR"))
        assertEquals("USUALLY", OcrSpellRepair.repair("UISUALLY"))
        assertEquals("MUCH", OcrSpellRepair.repair("MLICH"))
    }

    @Test
    fun `leaves real words alone`() {
        // Checked before anything else is tried, so a word in the lexicon can
        // never be rewritten however many candidates it has.
        listOf("CAT", "UNTRAINED", "REALLY", "PUDDING", "MEOW", "TABLES")
            .forEach { assertNull(OcrSpellRepair.repair(it), "$it was rewritten") }
    }

    @Test
    fun `abstains rather than guessing`() {
        // Too short to tell a misread from a short word: a bare M became "rn"
        // and "0e" became "oe" while this guard was missing.
        assertNull(OcrSpellRepair.repair("M"))
        assertNull(OcrSpellRepair.repair("0e"))
        // A name is not in the lexicon and must survive it untouched.
        assertNull(OcrSpellRepair.repair("TETRA"))
        assertNull(OcrSpellRepair.repair("BERNARDELLI"))
    }

    @Test
    fun `keeps the lettering's capitals`() {
        // toSentenceCase decides what to lower by looking for a lowercase
        // letter, so a repair that answered in lowercase would tell it the
        // balloon was mixed case and stop it lowering the rest.
        val balloon = "TETRA-CHAN USED TO PRETEND SHE WAS AN UINTRAINED CAT."
        val repaired = OcrSpellRepair.apply(balloon)
        assertEquals("TETRA-CHAN USED TO PRETEND SHE WAS AN UNTRAINED CAT.", repaired)
        assertEquals(
            "Tetra-chan used to pretend she was an untrained cat.",
            TranslationTextUtils.toSentenceCase(repaired),
        )
    }

    @Test
    fun `leaves punctuation and spacing where it was`() {
        assertEquals("...WITH THAT STRANGE MEOWING JUST NOW?",
            OcrSpellRepair.apply("...WITH THAT STRANGE MEOWING JUST NOW?"))
        assertEquals("DON'T", OcrSpellRepair.apply("DON'T"))
    }
}
