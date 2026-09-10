package snd.komelia.discover

import kotlin.time.Instant

/**
 * Which external series a local series is (V105).
 *
 * [externalId] null with [resolvedAt] set means "looked for, found nothing" —
 * a different state from "never looked", so a miss is not paid for again on
 * every pass.
 */
data class DiscoverSourceLink(
    val seriesId: String,
    val source: String = SOURCE_MANGAUPDATES,
    val externalId: String?,
    val resolvedAt: Instant?,
)

/**
 * One suggestion for something NOT in the library.
 *
 * Everything shown on the card is stored, because the source hands it all back
 * inside the recommendation payload: displaying a suggestion costs no request.
 * [becauseOf] holds the local series ids that produced it, for the "because you
 * read X" line — ids rather than titles, since a title can be renamed in Komga
 * and the card would then lie.
 */
data class DiscoverSuggestion(
    val externalId: String,
    val source: String = SOURCE_MANGAUPDATES,
    val title: String,
    val url: String,
    val imageUrl: String,
    val year: String,
    val rating: Double,
    val score: Double,
    val becauseOf: List<String>,
    val updatedAt: Instant?,
)

/** When the last pass ran, and what went wrong if it did. */
data class DiscoverScanState(
    val lastRunAt: Instant?,
    val lastError: String,
)

const val SOURCE_MANGAUPDATES = "mangaupdates"

/**
 * Local persistence for the Discover tab (V105).
 *
 * Everything here is a cache of a third party's answers, rebuildable by
 * rescanning, so — like the similarity index — it stays out of the backup
 * bundle. The one exception is [dismiss]: a wave-away is the user's own
 * decision and survives a refresh, which is why it lives in its own table
 * instead of a flag on the results.
 */
interface DiscoverRepository {

    /** The stored mapping for [seriesIds], keyed by local series id. */
    suspend fun linksOf(seriesIds: Collection<String>): Map<String, DiscoverSourceLink>

    suspend fun putLinks(links: Collection<DiscoverSourceLink>)

    /**
     * The [limit] best suggestions, dismissed ones already excluded.
     *
     * The tab calls this and makes no network request of its own — a pass costs
     * about a minute and must never happen while somebody is looking.
     */
    suspend fun topSuggestions(limit: Int): List<DiscoverSuggestion>

    /** Replaces the whole result set — a pass recomputes every score. */
    suspend fun replaceSuggestions(suggestions: Collection<DiscoverSuggestion>)

    suspend fun dismiss(externalId: String)

    suspend fun dismissedIds(): Set<String>

    suspend fun clearDismissed()

    suspend fun scanState(): DiscoverScanState

    suspend fun putScanState(state: DiscoverScanState)
}
