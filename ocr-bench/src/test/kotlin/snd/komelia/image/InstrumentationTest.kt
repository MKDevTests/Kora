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
     * The distinction the assembler's own comment gets wrong.
     *
     * It cites "Really...?" as the case the finished-sentence rule exists for,
     * but that balloon ends in a question mark, so the end-of-ellipsis test
     * fails first and the refusal never reaches that rule. The outcome is the
     * same -- not joined -- which is why the comment survived; the reason is
     * not, which is exactly what this instrumentation is for. The rule really
     * fires when the punctuation comes before the trailing dots.
     */
    @Test
    fun `a finished question is refused, for the right reason`() {
        assertEquals(
            listOf(BubbleAssembler.Seam.NO_ELLIPSIS_END),
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
