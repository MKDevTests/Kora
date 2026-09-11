package snd.komelia.discover

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Decodes payloads captured from the live API on 2026-09-10.
 *
 * Written after a first pass on the tablet resolved 1 series out of 43: every
 * failure was swallowed by a `runCatching`, so nothing said whether the network
 * or the model was at fault. These fixtures answer that without a build.
 */
class MangaUpdatesPayloadTest {

    // The client's own Json, deliberately: a test that configured its own
    // would pass while the app kept failing.
    private val json = MangaUpdatesClient.json

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .readBytes().decodeToString()

    @Test
    fun `decodes a search response`() {
        val response = json.decodeFromString<SearchResponse>(fixture("mangaupdates_search.json"))
        val first = checkNotNull(response.results.firstOrNull()?.record)
        assertEquals("Servant x Service", first.title)
        assertTrue(first.seriesId > 0, "series_id must survive decoding")
    }

    @Test
    fun `decodes a series with its recommendations`() {
        val series = json.decodeFromString<MangaUpdatesSeries>(fixture("mangaupdates_series.json"))
        assertEquals("Servant x Service", series.title)
        assertTrue(series.allRecommendations.isNotEmpty(), "recommendations must survive decoding")
        assertTrue(series.allRecommendations.all { it.seriesId > 0 })
    }

    @Test
    fun `reads the english licence and its publisher`() {
        val series = json.decodeFromString<MangaUpdatesSeries>(fixture("mangaupdates_series.json"))
        assertTrue(series.licensed, "Servant x Service is licensed in English")
        // The source only ever names Original and English publishers -- measured
        // over 58 entries on 2026-09-10 -- so English is the one type the card
        // can turn into a badge.
        assertEquals(
            listOf("Yen Press"),
            series.publishers.filter { it.type.equals("English", ignoreCase = true) }.map { it.publisherName },
        )
    }

    @Test
    fun `reads the id out of a modern series url`() {
        assertEquals(
            "1353796125",
            MangaUpdatesClient.idFromUrl("https://www.mangaupdates.com/series/0me0jrx/kimi-wa-008"),
        )
    }

    @Test
    fun `reads the id out of a legacy series url`() {
        assertEquals(
            "1353796125",
            MangaUpdatesClient.idFromUrl("https://www.mangaupdates.com/series.html?id=1353796125"),
        )
    }
}
