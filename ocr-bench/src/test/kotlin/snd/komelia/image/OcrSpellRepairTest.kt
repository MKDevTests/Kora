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
        // The same 'u', read as one thin stroke instead of two.
        assertEquals("ENOUGH", OcrSpellRepair.repair("ENOIGH"))
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

/**
 * The other half of the repair: words the recogniser ran together.
 *
 * Split out of [OcrSpellRepairTest] because the interesting test here is not a
 * list of cases but a census — a splitter is only worth having if it changes
 * almost nothing it should not, and that has to be counted rather than hoped.
 */
class OcrWordSplitTest {

    private val lexicon = File(
        "../komelia-ui/src/commonMain/composeResources/files/lexicon/en.txt"
    )
    private val phrases = File(
        "../komelia-ui/src/commonMain/composeResources/files/phrasebook/en-fr.json"
    )

    init {
        assertTrue(lexicon.isFile, "shipped lexicon missing at ${lexicon.absolutePath}")
        OcrSpellRepair.load(lexicon.readLines().filter { it.isNotBlank() }.toSet())
    }

    @Test
    fun `splits the run-togethers found on real pages`() {
        // Read off the tablet on 2026-08-18. "myleg" went to the translator
        // untouched and came back "blesser myleg"; "theold" cost the sentence
        // its "vieil".
        assertEquals("my leg", OcrSpellRepair.split("myleg"))
        assertEquals("the old", OcrSpellRepair.split("theold"))
        // All caps in, all caps out — comic lettering, and toSentenceCase
        // downstream decides what to lower by looking for a lowercase letter.
        assertEquals("MY LEG", OcrSpellRepair.split("MYLEG"))
    }

    @Test
    fun `a word the lexicon knows is never pulled apart`() {
        // Each of these splits cleanly into two real words and must not.
        listOf("cannot", "anymore", "himself", "into", "nothing", "someone")
            .forEach { assertNull(OcrSpellRepair.split(it), it) }
    }

    @Test
    fun `abstains when the token reads two ways`() {
        // "therein" is "there in" and "the rein". Two readings, so neither.
        assertNull(OcrSpellRepair.split("therein"))
    }

    @Test
    fun `the lexicon's two-letter fragments are not treated as words`() {
        // "ld", "ms" and "eg" are all in the shipped word list. Against it
        // raw, "theold" reads as both "the old" and "theo ld" and the
        // splitter abstains on the case it exists for; "comms" becomes
        // "com ms".
        assertNull(OcrSpellRepair.split("comms"))
    }

    @Test
    fun `leaves contractions and short tokens alone`() {
        assertNull(OcrSpellRepair.split("you're"))
        assertNull(OcrSpellRepair.split("isit"))
    }

    /**
     * The census. Every token of the shipped phrase book is real English a
     * reader will meet, so anything the splitter touches there is a false
     * positive on ordinary text.
     */
    @Test
    fun `changes nothing in two thousand real expressions`() {
        assertTrue(phrases.isFile, "shipped phrase book missing at ${phrases.absolutePath}")
        val table = kotlinx.serialization.json.Json
            .decodeFromString<Map<String, String>>(phrases.readText())
        val tokens = table.keys
            .flatMap { Regex("""[\p{L}\p{N}']+""").findAll(it).map { m -> m.value } }
            .toSet()
        val touched = tokens.mapNotNull { token ->
            OcrSpellRepair.split(token)?.let { token to it }
        }
        println("word split census: ${tokens.size} distinct tokens, ${touched.size} split")
        touched.take(40).forEach { (from, to) -> println("  $from -> $to") }
        assertTrue(
            touched.size * 100 <= tokens.size,
            "split ${touched.size} of ${tokens.size} real tokens — over one percent: $touched"
        )
    }
}
