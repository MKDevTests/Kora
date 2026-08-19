package snd.komelia.image

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the traced variants against drifting from the code they describe.
 *
 * The whole point of these is to be believed later, from a log, by someone who
 * cannot re-run the page. A tracer that has quietly stopped matching its own
 * pipeline does not fail loudly -- it reports a plausible reason for the wrong
 * balloon, and the measurement built on it is wrong in a way nothing catches.
 */
class SeamFidelityTest {

    /**
     * The one that matters: `explain` says JOINED exactly where `group` joins.
     *
     * Written as a property over assorted pages rather than as a handful of
     * examples, because the failure being guarded against is a rule added to
     * one and not the other.
     */
    @Test
    fun `explain agrees with group on every boundary`() {
        for (page in PAGES) {
            val groups = BubbleAssembler.group(page)
            val joinedByGroup = mutableSetOf<Int>()
            for (group in groups) {
                for (position in 1 until group.size) joinedByGroup += group[position] - 1
            }
            val seams = BubbleAssembler.explain(page)
            assertEquals(
                maxOf(page.size - 1, 0), seams.size,
                "one decision per boundary expected for ${page.size} blocks"
            )
            val joinedByExplain = seams.withIndex()
                .filter { (_, seam) -> seam == BubbleAssembler.Seam.JOINED }
                .map { (index, _) -> index }
                .toSet()
            assertEquals(
                joinedByGroup, joinedByExplain,
                "explain and group disagree on $page"
            )
        }
    }

    @Test
    fun `a boundary with no ellipsis at all is named as such`() {
        val seams = BubbleAssembler.explain(listOf("I'm fine.", "Are you sure?"))
        assertEquals(listOf(BubbleAssembler.Seam.NO_ELLIPSIS), seams)
    }

    @Test
    fun `the two half-agreements are told apart`() {
        assertEquals(
            listOf(BubbleAssembler.Seam.NO_ELLIPSIS_START),
            BubbleAssembler.explain(listOf("I was going to...", "But never mind."))
        )
        assertEquals(
            listOf(BubbleAssembler.Seam.NO_ELLIPSIS_END),
            BubbleAssembler.explain(listOf("I was going to.", "...but never mind."))
        )
    }

    /**
     * "Really...?" is the case the finished-sentence rule was written for, and
     * it now really reaches that rule.
     *
     * It did not before: the end-of-ellipsis test anchored on whitespace, so a
     * balloon whose dots were followed by a question mark failed it and was
     * refused one step earlier. Same outcome either way -- which is why nobody
     * noticed -- but the reason was wrong, and the reason is what the log
     * reports. Tolerating punctuation after the run fixed both.
     */
    @Test
    fun `a finished question is refused, for the right reason`() {
        assertEquals(
            listOf(BubbleAssembler.Seam.SENTENCE_ENDED),
            BubbleAssembler.explain(listOf("Really...?", "...I see."))
        )
        assertEquals(
            listOf(BubbleAssembler.Seam.SENTENCE_ENDED),
            BubbleAssembler.explain(listOf("Really?...", "...I see."))
        )
        assertEquals(
            listOf(BubbleAssembler.Seam.SENTENCE_ENDED),
            BubbleAssembler.explain(listOf("Stop!...", "...I mean it."))
        )
    }

    /**
     * The refusal every design document so far has proposed to relax. Measured
     * over four series it fires once in a thousand balloons, so the test is
     * here to keep it *countable*, not because raising the cap is planned.
     */
    @Test
    fun `the cap is reported as its own reason`() {
        val four = listOf("One...", "...two...", "...three...", "...four")
        val seams = BubbleAssembler.explain(four)
        assertEquals(
            listOf(
                BubbleAssembler.Seam.JOINED,
                BubbleAssembler.Seam.JOINED,
                BubbleAssembler.Seam.CAP_REACHED,
            ),
            seams
        )
        // And the assembler really did stop there, so the reason is not a story
        // the tracer tells about a decision that went the other way.
        assertEquals(listOf(listOf(0, 1, 2), listOf(3)), BubbleAssembler.group(four))
    }

    /**
     * The ellipsis the recogniser broke, measured on the bench: five of these
     * across eight volumes, each one a sentence that went to the translator in
     * halves. A single full stop still must not open the run -- that mistake
     * once welded two speakers together.
     */
    @Test
    fun `a stray dot around the ellipsis still reads as a seam`() {
        assertEquals(
            listOf(BubbleAssembler.Seam.JOINED),
            BubbleAssembler.explain(listOf("NEITHER OF THEM...", ".….ARE LETTING UP!"))
        )
        assertEquals(
            listOf(BubbleAssembler.Seam.JOINED),
            BubbleAssembler.explain(listOf("BREAK YOUR OPPONENT'S RHYTHM...", ".…AND SET YOUR OWN."))
        )
        // The single ellipsis character on its own still works.
        assertEquals(
            listOf(BubbleAssembler.Seam.JOINED),
            BubbleAssembler.explain(listOf("ALL RIGHT, I'M READY...", "…DARK..."))
        )
        // And a plain full stop is still the end of a sentence, not a seam.
        assertEquals(
            listOf(BubbleAssembler.Seam.NO_ELLIPSIS),
            BubbleAssembler.explain(listOf("She was an untrained cat.", "With that meowing just now?"))
        )
    }

    @Test
    fun `fewer than two blocks has no boundary`() {
        assertEquals(emptyList(), BubbleAssembler.explain(emptyList()))
        assertEquals(emptyList(), BubbleAssembler.explain(listOf("Alone.")))
    }

    private companion object {
        /** Assorted pages, including the shapes that have caused regressions. */
        val PAGES = listOf(
            listOf("I'm fine.", "Are you sure?"),
            listOf("It's not...", "...quite time yet."),
            listOf("Really...?", "...I see."),
            listOf("Really?...", "...I see."),
            listOf("One...", "...two...", "...three...", "...four"),
            listOf("A...", "...B", "C...", "...D"),
            listOf("", "...anything"),
            listOf("Wait!", "...what?"),
            listOf("Hold on...", "", "...there"),
            listOf("Solo balloon."),
            emptyList(),
        )
    }
}

/** The phrase book reports which table answered, and on which key. */
class PhraseBookTraceTest {

    init {
        // The shipped table, not a stand-in: load is idempotent and shared
        // across every test class in this module, so a stub here would be the
        // table every later test sees.
        PhraseBook.load(
            kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(
                File("../komelia-ui/src/commonMain/composeResources/files/phrasebook/en-fr.json")
                    .readText()
            )
        )
    }

    @Test
    fun `a curated hit names its tier and key`() {
        val answer = PhraseBook.lookupTraced("Something the matter?")
        assertNotNull(answer)
        assertEquals(PhraseBook.Tier.CURATED, answer.tier)
        assertEquals("something the matter", answer.key)
        assertEquals("Un problème ?", answer.french)
    }

    @Test
    fun `a bulk hit is named as bulk`() {
        val answer = PhraseBook.lookupTraced("Why not?")
        assertNotNull(answer)
        assertEquals(PhraseBook.Tier.BULK, answer.tier)
        assertEquals("why not", answer.key)
    }

    @Test
    fun `a miss stays a miss`() {
        assertNull(PhraseBook.lookupTraced("The quiet house on the hill was quite empty that evening."))
    }

    /**
     * The six balloons the bench found losing their ending, once it could see
     * this table at all.
     */
    @Test
    fun `a shouted balloon keeps its exclamation`() {
        assertEquals("Fiche le camp !", PhraseBook.lookup("Shove off!"))
        assertEquals("Mon Dieu !", PhraseBook.lookup("Oh my god!"))
        assertEquals("Je suis vraiment désolé !", PhraseBook.lookup("I'm really sorry!"))
        // The whole run comes back, so a double shout stays one.
        assertEquals("Je suis tellement désolé !!", PhraseBook.lookup("I'm so sorry!!"))
    }

    @Test
    fun `an answer with its own ending is left alone`() {
        // "huh" answers "Hein ?" deliberately; "HUH?!" must not make it "Hein ??!".
        assertEquals("Hein ?", PhraseBook.lookup("Huh?!"))
        assertEquals("Hein ?", PhraseBook.lookup("Huh?"))
    }

    @Test
    fun `a quiet balloon gains nothing`() {
        assertEquals("À ma connaissance", PhraseBook.lookup("As far as I know..."))
        assertEquals("Au cas où", PhraseBook.lookup("Just in case,"))
    }

    /** The traced form must not answer differently from the plain one. */
    @Test
    fun `lookup and lookupTraced agree`() {
        for (text in listOf("Something the matter?", "Why not?", "HUH?", "Quite.", "nothing here")) {
            assertEquals(PhraseBook.lookup(text), PhraseBook.lookupTraced(text)?.french, text)
        }
    }
}

/** The OCR repair reports which rule rewrote which token. */
class OcrRepairTraceTest {

    init {
        OcrSpellRepair.load(
            File("../komelia-ui/src/commonMain/composeResources/files/lexicon/en.txt")
                .readLines().filter { it.isNotBlank() }.toSet()
        )
    }

    @Test
    fun `a number with a letter o is traced to the digit rule`() {
        val traced = OcrSpellRepair.applyTraced("rotting here for 8o years")
        assertEquals("rotting here for 80 years", traced.text)
        val change = traced.changes.single()
        assertEquals(OcrSpellRepair.Rule.DIGIT_ZERO, change.rule)
        assertEquals("8o", change.before)
        assertEquals("80", change.after)
    }

    @Test
    fun `text the repair leaves alone traces nothing`() {
        val traced = OcrSpellRepair.applyTraced("the quiet house was empty")
        assertEquals("the quiet house was empty", traced.text)
        assertTrue(traced.changes.isEmpty(), "traced ${traced.changes}")
    }

    /** Same guard as the phrase book: the traced form is the plain one. */
    @Test
    fun `apply and applyTraced agree`() {
        for (text in listOf("rotting here for 8o years", "the empty", "", "1O5 the")) {
            assertEquals(OcrSpellRepair.apply(text), OcrSpellRepair.applyTraced(text).text, text)
        }
    }
}

/**
 * Where one translation gets cut when it goes back onto its balloons.
 *
 * Every case here was read off the bench corpus: twenty-four multi-balloon
 * groups, eleven of them ending a balloon on a word that announces something
 * and was not there to announce it.
 */
class SentenceDistributionTest {

    // The real strings, lettering and all: the cut is chosen from how long each
    // source balloon is, so a shortened stand-in cuts somewhere else and tests
    // nothing. Both of these were read out of the bench corpus.
    private val infoOnHim = listOf(
        "SINCE I HAVE NO INFO ON HIM...",
        "..IT'LL BE FUN TO SEE HOW HE GROWS!",
    )
    private val infoOnHimFr =
        "Puisque je n'ai pas d'infos sur lui Ce sera amusant de voir comment il grandit!"

    @Test
    fun `a cut is nudged off a preposition`() {
        val pieces = BubbleAssembler.distribute(infoOnHimFr, infoOnHim)
        assertEquals("Puisque je n'ai pas d'infos…", pieces[0])
        assertTrue(pieces[1].startsWith("…sur lui"), pieces[1])
    }

    /** The same call with no list is the old behaviour, and it hangs on "sur". */
    @Test
    fun `an empty list disables the nudging entirely`() {
        val raw = BubbleAssembler.distribute(infoOnHimFr, infoOnHim, avoidEndingOn = emptySet())
        assertEquals("Puisque je n'ai pas d'infos sur…", raw[0])
    }

    @Test
    fun `a cut is nudged off an article`() {
        val pieces = BubbleAssembler.distribute(
            "Et quand il s'agit du rythme du jeu Le plus souvent, c'est Haryu qui le dicte.",
            listOf(
                "AND WHEN IT COMES TO THE PACE OF THE GAME...",
                "...MORE OFTEN THAN NOT, HARYU'S THE ONE WHO DICTATES IT.",
            ),
        )
        assertTrue(pieces[0].endsWith("rythme…"), "cut after an article: ${pieces[0]}")
    }

    @Test
    fun `a cut already on a real word is left where it was`() {
        val pieces = BubbleAssembler.distribute(
            "Brisez le rythme de votre adversaire et définissez le vôtre.",
            listOf("BREAK YOUR OPPONENT'S RHYTHM...", ".…AND SET YOUR OWN."),
        )
        assertEquals("Brisez le rythme de votre adversaire…", pieces[0])
        assertEquals("…et définissez le vôtre.", pieces[1])
    }

    /** A run of function words has nowhere better to go; it must not loop or throw. */
    @Test
    fun `a sentence of nothing but function words still splits`() {
        val pieces = BubbleAssembler.distribute("de la le les des du", listOf("AAAA...", "...BBBB"))
        assertEquals(2, pieces.size)
        assertTrue(pieces.all { it.isNotBlank() })
    }

    @Test
    fun `fewer words than balloons keeps the sentence whole`() {
        assertEquals(listOf("Oui", ""), BubbleAssembler.distribute("Oui", listOf("AAA...", "...BBB")))
    }
}
