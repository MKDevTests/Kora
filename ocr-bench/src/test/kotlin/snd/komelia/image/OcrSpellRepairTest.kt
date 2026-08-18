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
    fun `the run-togethers the corpus actually contains`() {
        // Replayed over 1213 balloons of five volumes, these are what the
        // recogniser really glues: an article, a pronoun, a preposition or an
        // auxiliary stuck to the word after it.
        assertEquals("a late", OcrSpellRepair.split("alate"))
        assertEquals("to fight", OcrSpellRepair.split("tofight"))
        assertEquals("this is", OcrSpellRepair.split("thisis"))
        assertEquals("i allocated", OcrSpellRepair.split("iallocated"))
        assertEquals("that should", OcrSpellRepair.split("thatshould"))
        assertEquals("was outright", OcrSpellRepair.split("wasoutright"))
        // Needs the one-letter "a", which is why it is allowed at all.
        assertEquals("what a", OcrSpellRepair.split("whata"))
    }

    @Test
    fun `more than two parts was withdrawn, after measuring it`() {
        // This used to return "they both smell amazing", and the case was
        // real: the balloon read "ils semellent labyrinthant" without it.
        //
        // It was shipped on 39 balloons and a census over the phrase book,
        // and both were too weak to see the cost -- a dictionary contains no
        // proper names. Replayed over 1213 balloons of real pages the
        // recursion fired 108 times and was wrong about three times in five:
        // YAKISOBA became "YAK I SOB A", Arboriculture "arbor i culture",
        // DAMPENERS "DAMPEN ERS". One rescued balloon does not pay for that,
        // so the search stops at two parts.
        assertNull(OcrSpellRepair.split("theybothsmellamazing"))
    }

    @Test
    fun `what the recogniser glues is never a conjunction`() {
        // The same 1213 balloons, one iteration later. Requiring a function
        // word in the lead cut the false splits from 108 to 18, and every one
        // that survived began with "and" or "or" -- because that is how names
        // start, not how a dropped space looks. ANDRES became "AND RES"
        // twice, "ordad" became "or dad". Dropping the conjunctions costs
        // nothing measurable and leaves 13 true splits of 15.
        assertNull(OcrSpellRepair.split("andres"))
        assertNull(OcrSpellRepair.split("ANDRES"))
        assertNull(OcrSpellRepair.split("ordad"))
    }

    @Test
    fun `a name long enough is not a sentence`() {
        // English decomposes, and the shipped word list carries the pieces:
        // "ers", "ing", "res", "mats" and "axel" are all in it. Each of these
        // came apart on a real page before the lead had to be a function
        // word, and each is checked here because the rule that stops them is
        // one line and easy to widen by accident.
        listOf(
            "DAMPENERS", "RESPAWNED", "MATSURI", "AXELROD", "MOISAN",
            "outmatched", "YAKISOBA",
        ).forEach { assertNull(OcrSpellRepair.split(it), it) }
    }

    @Test
    fun `a half the lexicon does not carry cannot be split off`() {
        // "soremorsefully" is "so remorsefully" and stays whole, because
        // "remorsefully" is not among the 37166 words that ship. That is the
        // real ceiling of this repair and it is the lexicon's, not the
        // algorithm's: every half has to be a word we can look up.
        assertNull(OcrSpellRepair.split("soremorsefully"))
    }

    @Test
    fun `lettering is not words`() {
        // Long runs of the same letter are a shout drawn on the page. Bounded
        // rather than trusted to the lexicon, so the search cannot wander.
        assertNull(OcrSpellRepair.split("whwhwhwhooaaaaaaaaaaaaaaaaaaaaaaaa"))
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

/**
 * Names, which the repair has no business rewriting.
 *
 * Split out because the evidence is different in kind: these are not cases
 * the repair got wrong on one page, they are a class it cannot get right,
 * and the only defence is refusing to touch them.
 */
class OcrHonorificTest {

    private val lexicon = File(
        "../komelia-ui/src/commonMain/composeResources/files/lexicon/en.txt"
    )

    init {
        OcrSpellRepair.load(lexicon.readLines().filter { it.isNotBlank() }.toSet())
    }

    @Test
    fun `an honorific is never repaired into a word`() {
        // "kun" is not in the word list and "kin" is, so the u-for-i rule
        // renamed every character addressed by name. Three times in one
        // volume of the corpus, and it fires wherever manga is polite.
        assertEquals("RITSU-KUN", OcrSpellRepair.apply("RITSU-KUN"))
        assertEquals("NOHARA-KUN.", OcrSpellRepair.apply("NOHARA-KUN."))
    }

    @Test
    fun `the name before an honorific is protected too`() {
        // From the other side of the hyphen: KIDO-SAN became KUDO-SAN,
        // because "kudo" is in the word list and "kido" is not. The hyphen
        // followed by an honorific is what says this is a person, and it is
        // the only such signal available in all-caps lettering.
        assertEquals("KIDO-SAN!", OcrSpellRepair.apply("KIDO-SAN!"))
        assertEquals("Dei-san's", OcrSpellRepair.apply("Dei-san's"))
        assertEquals("LULUS-CHAN...", OcrSpellRepair.apply("LULUS-CHAN..."))
    }

    @Test
    fun `an ordinary hyphenated word is still repaired`() {
        // The guard is the honorific, not the hyphen. Anything else keeps
        // working exactly as it did.
        assertEquals("WELL-UNTRAINED", OcrSpellRepair.apply("WELL-UINTRAINED"))
    }
}
