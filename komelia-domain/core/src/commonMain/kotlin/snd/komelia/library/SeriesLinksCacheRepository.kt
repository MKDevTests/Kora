package snd.komelia.library

import kotlinx.serialization.Serializable
import snd.komga.client.series.KomgaSeries

/** The Links tab as the server last described it. */
@Serializable
data class CachedSeriesLinks(
    val versions: List<KomgaSeries> = emptyList(),
    /** Relation type name -> series, kept as names so the enum can gain members. */
    val relations: Map<String, List<KomgaSeries>> = emptyMap(),
)

/**
 * Last known Links tab, per series.
 *
 * Opening the tab resolves every linked id with its own request — one to three
 * seconds each against a real server, so a series with a handful of links took
 * over ten seconds to show anything, on every visit. Remembering the resolved
 * answer lets the tab be drawn at once and corrected behind.
 */
interface SeriesLinksCacheRepository {
    suspend fun get(seriesId: String): CachedSeriesLinks?
    suspend fun put(seriesId: String, links: CachedSeriesLinks)
}
