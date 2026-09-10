package snd.komelia.discover

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every triple here — local title, API title, hit_title — was recorded against
 * the live search on 2026-09-10, over two samples of the real library: its 43
 * seed series, and 45 manga drawn from the 567 whose shelf name is a single
 * significant word.
 *
 * The rejected cases are what the tab would show without the check.
 */
class TitleMatchingTest {

    @Test
    fun `matches a french shelf name through the alias the api matched on`() {
        // Nothing in common with the romaji title; everything in common with
        // the French alias, which is what hit_title carries.
        assertTrue(
            titleMatches(
                "Iruma à l’école des démons",
                "Mairimashita! Iruma-kun",
                "Iruma à l’école des démons",
            )
        )
        assertTrue(
            titleMatches("Negima ! - Le Maître Magicien", "Mahou Sensei Negima!", "Negima! Le Maître magicien")
        )
    }

    @Test
    fun `matches an alias despite the language the api appends to it`() {
        // "(French)" on the alias is what made this fail the first time.
        assertTrue(titleMatches("008 Apprenti espion", "Kimi wa 008", "008 Apprenti espion (French)"))
        assertTrue(titleMatches("Centaures", "Jimba", "Centaures (French)"))
    }

    @Test
    fun `matches one-word manga on the title itself`() {
        listOf("Hellsing", "Kingdom", "Beastars", "Doubt", "Rainbow", "Persona", "Enigma")
            .forEach { assertTrue(titleMatches(it, it, it), it) }
        assertTrue(titleMatches("Spy X Family", "Spy x Family", "Spy x Family"))
        assertTrue(titleMatches("GEOBREEDERS (EN)", "Geobreeders", "Geobreeders"))
        assertTrue(titleMatches("Wandance (Chap)", "WonDance", "Wandance"))
    }

    @Test
    fun `matches the right edition rather than the spin-off ranked above it`() {
        // Gantz:G is returned first for "Gantz (Perfect Edition)". Only exact
        // equality, checked over the whole page, picks Gantz out.
        assertFalse(titleMatches("Gantz (Perfect Edition)", "Gantz:G", "Gantz G"))
        assertTrue(titleMatches("Gantz (Perfect Edition)", "Gantz", "Gantz"))
    }

    @Test
    fun `rejects the comics the search invents a match for`() {
        assertFalse(titleMatches("Les profs", "Sakitcho Dakedemo", "Les Sciences et les Fantaisies"))
        assertFalse(titleMatches("Titeuf", "Colorful (Tteul Ebom)", "Colorful (Tteul Ebom)"))
        assertFalse(
            titleMatches("Poutine - L'ascension d'un dictateur", "Solo Slime's Ascension", "L'Ascension du Solo Slime")
        )
        assertFalse(titleMatches("Pizza roadtrip", "Friendship Pizza", "Friendship Pizza"))
        assertFalse(titleMatches("Pyongyang", "PeongYang GoalKeeper", "PeongYang GoalKeeper"))
        assertFalse(titleMatches("Le patient", "Patient Flowers", "Patient Flowers"))
    }

    @Test
    fun `rejects genre folders that are not series at all`() {
        assertFalse(titleMatches("Aventure", "Aventure Virgin", "Aventure Virgin"))
        assertFalse(titleMatches("Comédie", "Peking Reijinshou", "2- La comédie des sentiments"))
        assertFalse(titleMatches("Guerre", "Flowers May Wither but You Remain", "La Guerre des Fleurs"))
        assertFalse(titleMatches("Histoire", "One Story Tick Tock", "L'instant d'une histoire"))
        assertFalse(titleMatches("Non-Fiction", "Space Non-Fiction", "Space Non-Fiction"))
    }

    @Test
    fun `strips the shelf decorations this library uses`() {
        assertEquals(listOf("2.5 Dimensional Seduction"), localTitleVariants("2.5 Dimensional Seduction (Chap)"))
        assertEquals(listOf("Ramen Akaneko"), localTitleVariants("Ramen Akaneko (Chap)"))
        assertEquals(listOf("Fairy Tail"), localTitleVariants("Fairy Tail (Univers)"))
    }

    @Test
    fun `puts a displaced article back in front`() {
        assertEquals(listOf("The Fable", "Fable"), localTitleVariants("Fable (The)"))
        assertEquals(
            listOf("L'Attaque Des Titans - Before the Fall", "Attaque Des Titans - Before the Fall"),
            localTitleVariants("Attaque Des Titans (l') - Before the Fall"),
        )
    }

    @Test
    fun `reads a romanised title out of a tracker link`() {
        // The slug IS the title, in the form the search indexes. No request.
        assertEquals(
            "arpeggio of blue steel",
            trackerTitle("https://www.nautiljon.com/mangas/arpeggio+of+blue+steel.html"),
        )
        assertEquals(
            "404 démons",
            trackerTitle("https://www.nautiljon.com/mangas/404+d%C3%A9mons.html"),
        )
        assertEquals(
            "fairy tail 100 years quest",
            trackerTitle("https://www.anime-planet.com/manga/fairy-tail-100-years-quest"),
        )
    }

    @Test
    fun `ignores trackers whose links carry no title`() {
        // An id says nothing that can be searched on.
        assertNull(trackerTitle("https://anilist.co/manga/30002"))
        assertNull(trackerTitle("https://myanimelist.net/manga/1"))
        assertNull(trackerTitle("https://www.bedetheque.com/serie-1234-BD.html"))
    }

    @Test
    fun `folds accents and punctuation`() {
        assertEquals("les indes fourbes", normalizeTitle("Les indes Fourbes"))
        assertEquals("iruma a l ecole des demons", normalizeTitle("Iruma à l’école des démons"))
        assertEquals("Centaures", stripSourceLanguage("Centaures (French)"))
        assertEquals("Gantz:G", stripSourceLanguage("Gantz:G"))
    }
}
