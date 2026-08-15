package snd.komelia.image

import kotlin.test.Test
import kotlin.test.assertEquals
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
