package snd.komelia.anilist

import snd.komelia.links.SeriesRelationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AniListMappingTest {

    @Test
    fun relationTypeMapping() {
        assertEquals(SeriesRelationType.SEQUEL, toSeriesRelationType("SEQUEL"))
        assertEquals(SeriesRelationType.PREQUEL, toSeriesRelationType("PREQUEL"))
        assertEquals(SeriesRelationType.SPIN_OFF, toSeriesRelationType("SPIN_OFF"))
        assertEquals(SeriesRelationType.MAIN_STORY, toSeriesRelationType("PARENT"))
        // No exact Kora equivalent → defaults to SPIN_OFF (user-overridable).
        assertEquals(SeriesRelationType.SPIN_OFF, toSeriesRelationType("SIDE_STORY"))
    }

    @Test
    fun noisyRelationTypesAreDropped() {
        for (t in listOf("ADAPTATION", "SOURCE", "ALTERNATIVE", "SUMMARY", "COMPILATION", "CONTAINS", "CHARACTER", "OTHER", null, "")) {
            assertNull(toSeriesRelationType(t), "expected null for '$t'")
        }
    }

    @Test
    fun keepsMangaAndNovelsDropsAnime() {
        assertTrue(isLinkableMangaNode(media(type = "MANGA", format = "MANGA")))
        assertTrue(isLinkableMangaNode(media(type = "MANGA", format = "ONE_SHOT")))
        // Light novels come back under type=MANGA format=NOVEL — keep them; the
        // user often owns the manga adaptation under the same title.
        assertTrue(isLinkableMangaNode(media(type = "MANGA", format = "NOVEL")))
        assertFalse(isLinkableMangaNode(media(type = "ANIME", format = "TV")))
        assertFalse(isLinkableMangaNode(media(type = "ANIME", format = "MOVIE")))
        assertFalse(isLinkableMangaNode(media(type = null, format = null)))
    }

    @Test
    fun linkSuggestionsFilterAndMap() {
        val source = media(
            id = 1,
            relations = AniListRelations(
                edges = listOf(
                    edge("SEQUEL", media(id = 2, type = "MANGA", format = "MANGA")),
                    edge("SIDE_STORY", media(id = 3, type = "MANGA", format = "NOVEL")),   // kept (novel, manga adaptation may be owned)
                    edge("ADAPTATION", media(id = 4, type = "ANIME", format = "TV")),      // dropped: anime
                    edge("OTHER", media(id = 5, type = "MANGA", format = "MANGA")),        // dropped: noisy type
                    edge("SPIN_OFF", media(id = 6, type = "MANGA", format = "ONE_SHOT")),
                    edge(null, null),                                                      // dropped: empty
                ),
            ),
        )

        val suggestions = source.linkSuggestions()

        assertEquals(3, suggestions.size)
        assertEquals(2, suggestions[0].node.id)
        assertEquals(SeriesRelationType.SEQUEL, suggestions[0].suggestedType)
        assertEquals(3, suggestions[1].node.id)
        assertEquals(SeriesRelationType.SPIN_OFF, suggestions[1].suggestedType)
        assertEquals(6, suggestions[2].node.id)
        assertEquals(SeriesRelationType.SPIN_OFF, suggestions[2].suggestedType)
    }

    @Test
    fun displayTitleFallsBackThroughLanguages() {
        assertEquals("Romaji", media(title = AniListTitle(romaji = "Romaji", english = "En")).displayTitle)
        assertEquals("En", media(title = AniListTitle(romaji = "  ", english = "En")).displayTitle)
        assertEquals("ネイティブ", media(title = AniListTitle(native = "ネイティブ")).displayTitle)
        assertNull(media(title = AniListTitle()).displayTitle)
    }

    private fun media(
        id: Int = 0,
        type: String? = "MANGA",
        format: String? = "MANGA",
        title: AniListTitle = AniListTitle(romaji = "T$id"),
        relations: AniListRelations? = null,
    ) = AniListMedia(id = id, type = type, format = format, title = title, relations = relations)

    private fun edge(relationType: String?, node: AniListMedia?) =
        AniListRelationEdge(relationType = relationType, node = node)
}
