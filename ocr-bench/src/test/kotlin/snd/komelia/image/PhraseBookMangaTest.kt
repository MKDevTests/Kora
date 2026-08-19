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

    @Test
    fun `the answer takes the capital the balloon had`() {
        // The shipped table is written as a dictionary of expressions, so most
        // of its answers are lowercase. A balloon is a line, not an entry.
        // The shipped table, not a stand-in: load is idempotent and shared.
        val json = java.io.File(
            "../komelia-ui/src/commonMain/composeResources/files/phrasebook/en-fr.json"
        ).readText()
        PhraseBook.load(
            kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(json)
        )
        // The exclamation comes back too, for the same reason the capital does:
        // the entry is a dictionary headword, the balloon is a line someone
        // shouted. See `a shouted balloon keeps its exclamation`.
        assertEquals("C'est pas trop tôt !", PhraseBook.lookup("About time!"))
        assertEquals("C'est pas trop tôt !", PhraseBook.lookup("ABOUT TIME!"))
        assertEquals("c'est pas trop tôt", PhraseBook.lookup("about time"))
    }

    @Test
    fun `the second pass over the same volume answers too`() {
        assertEquals("En effet.", PhraseBook.lookup("Quite."))
        assertEquals("De rien.", PhraseBook.lookup("It's no trouble."))
        assertEquals("Ressaisis-toi !", PhraseBook.lookup("Get your act together!"))
        assertEquals("Merci.", PhraseBook.lookup("Thank... You."))
        assertEquals("Qui ferait ça ?", PhraseBook.lookup("Who does that"))
    }
}
