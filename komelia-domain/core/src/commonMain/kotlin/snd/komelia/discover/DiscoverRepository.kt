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
    /** How many people voted. 8.7 over 4350 votes is not 8.7 over 2. */
    val ratingVotes: Int,
    /** Publication state as the source words it: "27 Volumes (Complete)". */
    val status: String,
    val description: String,
    val genres: List<String>,
    val authors: List<String>,
    val publishers: List<String>,
    /**
     * An English edition exists, per the source. The only availability it can
     * answer: it tracks `Original` and `English` publishers and nothing else,
     * so a French edition is neither confirmed nor denied by this.
     */
    val licensed: Boolean = false,
    /** Names of the English-language publishers, for the card's badge. */
    val englishPublishers: List<String> = emptyList(),
    val score: Double,
    /**
     * True when readers voted this link, false when it came from the source's
     * tag-overlap list. The two are not measurements of the same thing, so
     * this orders results ahead of [score] rather than being folded into it.
     */
    val voted: Boolean = true,
    /** Kept by the user: survives a pass that no longer recommends it. */
    val interested: Boolean = false,
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

    /**
     * Everything the user marked as interesting, newest gesture first.
     *
     * Excluded from [topSuggestions]: once kept, a card has been decided on and
     * stops competing for room with the ones that have not.
     */
    suspend fun interestedSuggestions(): List<DiscoverSuggestion>

    /**
     * Marks or unmarks a suggestion as one to come back to.
     *
     * Marking is the opposite gesture to [dismiss] and, like it, survives a
     * refresh -- but the whole card is kept, not just the id, since the next
     * pass may well stop recommending it.
     */
    suspend fun setInterested(externalId: String, interested: Boolean)

    /** Replaces the whole result set — a pass recomputes every score. */
    suspend fun replaceSuggestions(suggestions: Collection<DiscoverSuggestion>)

    suspend fun dismiss(externalId: String)

    suspend fun dismissedIds(): Set<String>

    suspend fun clearDismissed()

    suspend fun scanState(): DiscoverScanState

    suspend fun putScanState(state: DiscoverScanState)
}
