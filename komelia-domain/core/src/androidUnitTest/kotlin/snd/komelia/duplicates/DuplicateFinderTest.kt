package snd.komelia.duplicates

import snd.komelia.similarity.SimilarityIndexTitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cases taken from the real catalogue, not invented: every title below is a
 * group the bench actually produced on the 12 688-series index.
 */
class DuplicateFinderTest {

    private var next = 0
    private fun entry(title: String, library: String = "lib1", language: String? = null) =
        SimilarityIndexTitle("s${next++}", library, title, language)

    @Test
    fun `identical titles group`() {
        val groups = findDuplicateGroups(listOf(entry("One Piece"), entry("One Piece")))
        assertEquals(1, groups.size)
        assertEquals(2, groups.single().members.size)
        assertTrue(groups.single().likely)
    }

    @Test
    fun `case and punctuation do not separate`() {
        val groups = findDuplicateGroups(listOf(entry("Blue Exorcist"), entry("blue  exorcist!")))
        assertEquals(1, groups.size)
    }

    @Test
    fun `moved article is caught by the sorted-words pass`() {
        val groups = findDuplicateGroups(
            listOf(
                entry("Attaque Des Titans (l') - Before the Fall"),
                entry("L'attaque des Titans - Before The Fall"),
            )
        )
        assertEquals(1, groups.size)
    }

    @Test
    fun `series in different libraries never group`() {
        val groups = findDuplicateGroups(
            listOf(entry("One Piece", "lib1"), entry("One Piece", "lib2"))
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `a shared collection name is flagged unlikely`() {
        val groups = findDuplicateGroups(List(11) { entry("Star Wars") })
        assertEquals(1, groups.size)
        assertEquals(11, groups.single().members.size)
        assertTrue(!groups.single().likely)
    }

    @Test
    fun `three members stay likely`() {
        val groups = findDuplicateGroups(List(3) { entry("Choujin X") })
        assertTrue(groups.single().likely)
    }

    @Test
    fun `an ignored pair splits the group instead of hiding it`() {
        val entries = listOf(entry("Galaxias"), entry("Galaxias"), entry("Galaxias"))
        val ignored = setOf(
            duplicatePairKey(entries[0].seriesId, entries[1].seriesId),
            duplicatePairKey(entries[0].seriesId, entries[2].seriesId),
        )
        val groups = findDuplicateGroups(entries, ignored)
        assertEquals(1, groups.size)
        assertEquals(
            setOf(entries[1].seriesId, entries[2].seriesId),
            groups.single().members.mapTo(mutableSetOf()) { it.seriesId },
        )
    }

    @Test
    fun `ignoring every link removes the group`() {
        val entries = listOf(entry("Kagurabachi"), entry("Kagurabachi"))
        val ignored = setOf(duplicatePairKey(entries[0].seriesId, entries[1].seriesId))
        assertTrue(findDuplicateGroups(entries, ignored).isEmpty())
    }

    @Test
    fun `different works are not grouped`() {
        val groups = findDuplicateGroups(
            listOf(entry("Kingdom Hearts II"), entry("Kingdom Hearts III"), entry("Goblin Slayer"))
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `blank titles are ignored`() {
        val groups = findDuplicateGroups(listOf(entry("   "), entry("!!!")))
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `different languages are two editions, not a duplicate`() {
        val groups = findDuplicateGroups(
            listOf(entry("7th Garden", language = "fr"), entry("7th Garden", language = "en"))
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `the same language still groups`() {
        val groups = findDuplicateGroups(
            listOf(entry("7th Garden", language = "fr"), entry("7th Garden", language = "fr"))
        )
        assertEquals(1, groups.size)
    }

    @Test
    fun `an unknown language never splits a pair`() {
        // Rows written before V104 have none; reading that as a mismatch would
        // empty the screen until the index is rebuilt.
        val groups = findDuplicateGroups(
            listOf(entry("7th Garden", language = "fr"), entry("7th Garden", language = null))
        )
        assertEquals(1, groups.size)
    }

    @Test
    fun `a chapter series is never a duplicate`() {
        val groups = findDuplicateGroups(
            listOf(entry("One-Punch Man (Chap)"), entry("One-Punch Man (Chap)"))
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `a chapter series does not group with its volumes`() {
        val groups = findDuplicateGroups(listOf(entry("Dead Rock"), entry("Dead Rock (Chap)")))
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `an existing link rules the pair out`() {
        // "Chainsaw Man" twice, one of them the chapter release: nothing in the
        // title says so, only the Chapters edge the admin already recorded.
        val entries = listOf(entry("Chainsaw Man"), entry("Chainsaw Man"))
        val linked = setOf(duplicatePairKey(entries[0].seriesId, entries[1].seriesId))
        assertTrue(findDuplicateGroups(entries, linkedPairs = linked).isEmpty())
    }

    @Test
    fun `a three-way group survives one member being another language`() {
        val entries = listOf(
            entry("Galaxias", language = "fr"),
            entry("Galaxias", language = "fr"),
            entry("Galaxias", language = "en"),
        )
        val groups = findDuplicateGroups(entries)
        assertEquals(1, groups.size)
        assertEquals(
            setOf(entries[0].seriesId, entries[1].seriesId),
            groups.single().members.mapTo(mutableSetOf()) { it.seriesId },
        )
    }
}
