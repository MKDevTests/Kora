package snd.komelia.links

import snd.komga.client.series.KomgaSeriesId

/**
 * Relation a series has to another, from the viewing series' perspective:
 * a row means "`other` is the [SeriesRelationType] of this series".
 *
 * Sequel/Prequel and SpinOff/MainStory are stored as inverse pairs so both
 * series show the correct label; Related is symmetric (its own inverse).
 * MainStory is never picked by the user directly — it's the auto-inverse of
 * SpinOff (shown on the parent work).
 */
enum class SeriesRelationType {
    SEQUEL,
    PREQUEL,
    SPIN_OFF,
    MAIN_STORY,
    RELATED;

    fun inverse(): SeriesRelationType = when (this) {
        SEQUEL -> PREQUEL
        PREQUEL -> SEQUEL
        SPIN_OFF -> MAIN_STORY
        MAIN_STORY -> SPIN_OFF
        RELATED -> RELATED
    }
}

/** A related series + how it relates to the series it was queried from. */
data class SeriesRelation(val series: KomgaSeriesId, val type: SeriesRelationType)

/** Raw directed edge, used for backup export/import. */
data class SeriesRelationEdge(
    val from: KomgaSeriesId,
    val to: KomgaSeriesId,
    val type: SeriesRelationType,
)

/**
 * Local-only persistence for manual series links. Two independent concepts:
 *  - **Versions** — same work in another language/edition, symmetric group.
 *  - **Relations** — typed, bidirectional (sequel/prequel/spin-off/related).
 *
 * Never synced to the Komga server (its series metadata is shared across all
 * users of the instance). Included in the JSON backup.
 */
interface SeriesLinksRepository {

    /** Other series in the same "versions" group as [seriesId] (excludes it). */
    suspend fun versionsOf(seriesId: KomgaSeriesId): List<KomgaSeriesId>

    /** Mark [a] and [b] as versions of each other (merges their groups). */
    suspend fun linkVersion(a: KomgaSeriesId, b: KomgaSeriesId)

    /** Remove [seriesId] from its versions group (and dissolve a leftover single). */
    suspend fun unlinkVersion(seriesId: KomgaSeriesId)

    /** Typed relations whose subject is [seriesId] (outgoing edges). */
    suspend fun relationsOf(seriesId: KomgaSeriesId): List<SeriesRelation>

    /**
     * Record that [to] is the [type] of [from]; the inverse edge
     * ([from] is [SeriesRelationType.inverse] of [to]) is written too.
     */
    suspend fun linkRelation(from: KomgaSeriesId, to: KomgaSeriesId, type: SeriesRelationType)

    /** Remove the relation between [a] and [b] in both directions. */
    suspend fun unlinkRelation(a: KomgaSeriesId, b: KomgaSeriesId)

    // -- Backup --
    suspend fun getAllVersions(): Map<KomgaSeriesId, String>
    suspend fun replaceAllVersions(byGroup: Map<KomgaSeriesId, String>)
    suspend fun getAllRelations(): List<SeriesRelationEdge>
    suspend fun replaceAllRelations(edges: List<SeriesRelationEdge>)
}
