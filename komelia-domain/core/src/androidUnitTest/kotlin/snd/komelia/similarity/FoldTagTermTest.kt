package snd.komelia.similarity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The tag fold, on the spellings the real libraries actually contain.
 *
 * The counts in the names are from the measured index (12 686 series): they are
 * what makes each case worth a test rather than a hypothetical.
 */
class FoldTagTermTest {

    @Test
    fun `the three ninja spellings are one term`() {
        // ninjas 36 series, ninja 16, ninja slash-s 7.
        val key = foldTagTerm("ninjas")
        assertEquals(key, foldTagTerm("ninja"))
        assertEquals(key, foldTagTerm("ninja/s"))
        assertEquals("ninja", key)
    }

    @Test
    fun `only the last word is singularised`() {
        assertEquals("monster girl", foldTagTerm("monster girls"))
        assertEquals("monster girl", foldTagTerm("monster girl/s"))
        // "girls love" is not "girl love": the first word keeps its s.
        assertEquals("girls love", foldTagTerm("girls love"))
    }

    @Test
    fun `separators collapse onto a single space`() {
        assertEquals(foldTagTerm("super power"), foldTagTerm("super-power"))
        assertEquals(foldTagTerm("super power"), foldTagTerm("super_power"))
        assertEquals("super power", foldTagTerm("super  powers"))
        assertEquals("e sport", foldTagTerm("e-sports"))
    }

    @Test
    fun `a slash that is not the plural notation is left alone`() {
        // "food/gourmet" is a compound tag, not "food or foods".
        assertEquals("food/gourmet", foldTagTerm("food/gourmet"))
        assertEquals("food/beverage", foldTagTerm("food/beverage"))
    }

    @Test
    fun `a double s is never stripped`() {
        assertEquals("boss", foldTagTerm("boss"))
        assertEquals("chess", foldTagTerm("chess"))
        // …and a short word is not singularised either.
        assertEquals("bus", foldTagTerm("bus"))
    }

    @Test
    fun `case and accents still fold`() {
        assertEquals("tranche de vie", foldTagTerm("Tranche-De-Vie"))
        assertEquals("heroine", foldTagTerm("héroïnes"))
    }

    @Test
    fun `two different ideas do not collide`() {
        // The measurement read all 66 clusters the singular step creates; these
        // are the pairs that came closest to touching and must not.
        assertNotEquals(foldTagTerm("girls love"), foldTagTerm("girl"))
        assertNotEquals(foldTagTerm("class"), foldTagTerm("cla"))
        assertNotEquals(foldTagTerm("press"), foldTagTerm("pre"))
    }

    @Test
    fun `names and genres are folded the old way`() {
        // Singularising a name would be wrong, not merely useless.
        assertEquals("a:kentaro miura", Feature(TermFamily.AUTHOR, "Kentarô MIURA").key)
        assertEquals("p:delcourt", Feature(TermFamily.PUBLISHER, "Delcourt").key)
        // A curated slug keeps its separators AND its final s.
        assertEquals("g:tranche-de-vie", Feature(TermFamily.GENRE, "tranche-de-vie").key)
        assertEquals("g:comics", Feature(TermFamily.GENRE, "comics").key)
    }

    @Test
    fun `tags and book tags use the fold`() {
        assertEquals("t:ninja", Feature(TermFamily.TAG, "Ninja/s").key)
        assertEquals("bt:shonen", Feature(TermFamily.BOOK_TAG, "shonen").key)
    }

    @Test
    fun `a series carrying two spellings of one tag counts it once`() {
        val terms = SeriesTerms(tags = setOf("ninja", "ninjas", "ninja/s"))
        assertEquals(1, terms.features().size)
    }
}
