package snd.komelia.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BubbleAssemblerTest {

    @Test
    fun `joins a sentence split across two balloons`() {
        val texts = listOf("Well...", "…It's not quite time yet...", "Meow!")
        assertEquals(listOf(listOf(0, 1), listOf(2)), BubbleAssembler.group(texts))
    }

    @Test
    fun `needs an ellipsis on both sides`() {
        // Trailing off alone, then a fresh sentence.
        assertEquals(
            listOf(listOf(0), listOf(1)),
            BubbleAssembler.group(listOf("But...", "Come on in!"))
        )
        // Hesitation opening a balloon that nothing ran into.
        assertEquals(
            listOf(listOf(0), listOf(1)),
            BubbleAssembler.group(listOf("Thanks!", "...Or something like that."))
        )
    }

    @Test
    fun `a full stop ends the sentence, it does not trail off`() {
        // Straight from the tablet, Ramen Aka Neko 164 page 13. Two panels, two
        // speakers. They were welded, translated as one sentence and then split
        // back across both balloons, so the second one opened on "…formé" --
        // the tail of "non entraîné", whose head had stayed in the first.
        assertEquals(
            listOf(listOf(0), listOf(1)),
            BubbleAssembler.group(
                listOf(
                    "Tetra-chan used to pretend she was an untrained cat.",
                    "...With that strange meowing just now?",
                )
            )
        )
    }

    @Test
    fun `two dots still count, the letterer does not always use the character`() {
        assertEquals(
            listOf(listOf(0, 1)),
            BubbleAssembler.group(listOf("I mean..", "..if you want to."))
        )
    }

    @Test
    fun `a finished question does not swallow the answer`() {
        assertEquals(
            listOf(listOf(0), listOf(1)),
            BubbleAssembler.group(listOf("Really...?", "...I see."))
        )
    }

    @Test
    fun `stops at three balloons`() {
        val texts = List(5) { "…part $it…" }
        val groups = BubbleAssembler.group(texts)
        assertTrue(groups.all { it.size <= 3 }, "got $groups")
        assertEquals(texts.indices.toList(), groups.flatten())
    }

    @Test
    fun `every block survives grouping exactly once`() {
        val texts = listOf("A...", "…b...", "…c", "D!", "…e…", "…f")
        assertEquals(texts.indices.toList(), BubbleAssembler.group(texts).flatten())
    }

    @Test
    fun `join drops the seam ellipses but keeps the outer ones`() {
        assertEquals(
            "Aren't you guys still closed?",
            BubbleAssembler.join(listOf("Aren't...", "…you guys still closed?"))
        )
    }

    @Test
    fun `distribute gives every balloon a share and loses no word`() {
        val sources = listOf("Aren't...", "…you guys still closed?")
        val pieces = BubbleAssembler.distribute("Vous n'êtes pas encore fermés ?", sources)
        assertEquals(2, pieces.size)
        assertTrue(pieces.all { it.isNotBlank() }, "got $pieces")
        val words = pieces.joinToString(" ").replace("…", " ").split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
        assertEquals(listOf("Vous", "n'êtes", "pas", "encore", "fermés", "?"), words)
    }

    @Test
    fun `distribute keeps a short translation whole rather than emptying a balloon`() {
        val pieces = BubbleAssembler.distribute("Oui", listOf("Yes...", "…really"))
        assertEquals(listOf("Oui", ""), pieces)
    }

    @Test
    fun `a single balloon is returned untouched`() {
        assertEquals(listOf("Bonjour !"), BubbleAssembler.distribute("Bonjour !", listOf("Hello!")))
    }
}

class EnglishTextCleanerTest {

    @Test
    fun `keeps real dialogue`() {
        listOf(
            "Thanks for the meal!",
            "Meow!",
            "Oh, and light on the noodles, please.",
            "Mmm!",
        ).forEach { assertTrue(EnglishTextCleaner.isTranslatable(it), it) }
    }

    @Test
    fun `drops what recognition invented over the artwork`() {
        listOf(
            "0e 000 200 200 20",
            "2000c",
            "000",
            "",
        ).forEach { assertTrue(!EnglishTextCleaner.isTranslatable(it), it) }
    }
}

class PhraseBookTest {

    @Test
    fun `answers the idioms measured failing on real pages`() {
        assertEquals("Un problème ?", PhraseBook.lookup("Something the matter?"))
        assertEquals("C'est inquiétant.", PhraseBook.lookup("It's concerning"))
        // 2026-08-18, English comic. The engine answered "Pas de recul.",
        // "Nous serons bien," and "Nouvelles années" respectively.
        assertEquals("On ne regarde pas en arrière.", PhraseBook.lookup("No looking back."))
        assertEquals("Ça ira.", PhraseBook.lookup("We'll be fine,"))
        assertEquals("Le Nouvel An", PhraseBook.lookup("New year's"))
    }

    @Test
    fun `a balloon that merely contains an idiom is left to the engine`() {
        // The table is whole-utterance by design. "No looking back" inside a
        // longer sentence has a different French shape, and answering it from
        // the table would put the wrong clause on the page.
        assertNull(PhraseBook.lookup("There's no looking back now, is there?"))
        assertNull(PhraseBook.lookup("We'll be fine as long as nobody talks."))
    }

    @Test
    fun `punctuation and case do not change which phrase it is`() {
        val expected = PhraseBook.lookup("Something the matter?")
        listOf(
            "something the matter",
            "SOMETHING THE MATTER?!",
            "Something the matter...?",
            "  Something the matter ?  ",
        ).forEach { assertEquals(expected, PhraseBook.lookup(it), it) }
    }

    @Test
    fun `a typographic apostrophe matches a plain one`() {
        assertEquals(PhraseBook.lookup("it's concerning"), PhraseBook.lookup("it\u2019s concerning"))
    }

    @Test
    fun `leaves ordinary lines to the engine`() {
        listOf(
            "I haven't eaten anything since this morning.",
            "Something the matter with the soup?",
            "Meow!",
            "",
        ).forEach { assertEquals(null, PhraseBook.lookup(it), it) }
    }
}

/**
 * Guards the one failure in this feature that is completely silent.
 *
 * The shipped table is keyed by a Python normalise() in
 * scripts/phrasebook/build_table.py, and looked up through the Kotlin one in
 * PhraseBook. If the two ever disagree, nothing throws and no build breaks --
 * the keys simply stop matching and two thousand expressions quietly do
 * nothing, which looks exactly like the table not being worth much.
 */
/**
 * Guards the one failure in this feature that is completely silent.
 *
 * The shipped table is keyed by a Python normalise() in
 * scripts/phrasebook/build_table.py, and looked up through the Kotlin one in
 * PhraseBook. If the two ever disagree, nothing throws and no build breaks --
 * the keys simply stop matching and two thousand expressions quietly do
 * nothing, which looks exactly like the table not being worth much.
 */
class PhraseBookTableTest {

    private val table = java.io.File(
        "../komelia-ui/src/commonMain/composeResources/files/phrasebook/en-fr.json"
    )

    private fun shipped(): Map<String, String> = kotlinx.serialization.json.Json
        .decodeFromString(table.readText(Charsets.UTF_8))

    @Test
    fun `the shipped table is where the reader looks for it`() {
        assertTrue(table.exists(), "missing ${table.absolutePath}")
    }

    @Test
    fun `every shipped key survives the Kotlin normaliser unchanged`() {
        val keys = shipped().keys
        assertTrue(keys.size > 1000, "only ${keys.size} keys parsed -- the table looks wrong")
        val drifted = keys.filter { PhraseBook.normalise(it) != it }
        assertTrue(
            drifted.isEmpty(),
            "${drifted.size} keys the Kotlin normaliser rewrites, so they can never " +
                    "be matched at runtime: ${drifted.take(5)}"
        )
    }

    @Test
    fun `the shipped table answers a lookup once loaded`() {
        PhraseBook.load(shipped())
        assertEquals("Pourquoi pas ?", PhraseBook.lookup("Why not?"))
    }

    @Test
    fun `the curated table still wins over the shipped one`() {
        PhraseBook.load(mapOf("something the matter" to "PAS CELUI-CI"))
        assertEquals("Un problème ?", PhraseBook.lookup("Something the matter?"))
    }
}
