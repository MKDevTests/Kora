package snd.komelia.image

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The balloons a manga volume showed Bergamot failing on, as the reader sees
 * them. The bench cannot cover these: it replays the merge, the splitter and
 * the casing, then hands everything to the engine, while the phrase book is
 * consulted in ReaderState. Only a unit test stands between these and a
 * regression.
 */
class PhraseBookMangaTest {

    @Test
    fun `the balloons measured on 19 August answer from the book`() {
        val cases = mapOf(
            "Huh?" to "Hein ?",
            "Huh?!" to "Hein ?",
            "Gasp!" to "Ha !",
            "Worry not." to "N'aie crainte.",
            "Love is blind." to "L'amour est aveugle.",
            "See you later!" to "À plus tard !",
            "Which is it, then?!" to "Alors, c'est quoi ?",
            "What's that for?" to "C'est pour quoi faire ?",
            "Would you like one?" to "Tu en veux une ?",
            "What are you trying to get at?" to "Où veux-tu en venir ?",
            "But you must beware." to "Mais prends garde.",
            "Head over heels..." to "Fou amoureux.",
            "In short: love at first sight." to "Bref : le coup de foudre.",
        )
        for ((balloon, french) in cases) {
            assertEquals(french, PhraseBook.lookup(balloon), "for balloon '$balloon'")
        }
    }
}
