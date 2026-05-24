package snd.komelia.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import snd.komelia.db.AppSettings
import snd.komelia.db.EpubReaderSettings
import snd.komelia.db.ImageReaderSettings
import snd.komelia.db.KomfSettings
import snd.komelia.db.TranscriptionSettings
import snd.komelia.homefilters.HomeScreenFilter

/**
 * Bumped on schema-breaking changes. Forward reads work for any version
 * up to this one — older fields stay supported. Newer-than-known bundles
 * are rejected (can't safely guess what new sections mean).
 *
 * History:
 *  - v1: initial format (v1.0.7+). Device-wide sections only; ratings
 *        at top level.
 *  - v2: per-user sections (v1.0.10+). Reading events + ratings live
 *        inside [BackupSections.userSections] keyed by Komga user id.
 *        Top-level [BackupSections.seriesRatings] is still written too
 *        for backward compatibility with v1 readers.
 */
const val BACKUP_SCHEMA_VERSION = 2

/**
 * Top-level envelope written to the JSON file. Self-describing so future
 * tooling can sniff the version without parsing the entire document.
 */
@Serializable
data class BackupBundle(
    @SerialName("schema_version") val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    @SerialName("exported_at") val exportedAt: String,
    @SerialName("exported_by") val exportedBy: String,
    val sections: BackupSections,
)

/**
 * Every section is nullable so a partial export remains a valid bundle and
 * older bundles missing newer sections can still be imported.
 */
@Serializable
data class BackupSections(
    @SerialName("app_settings") val appSettings: AppSettings? = null,
    @SerialName("image_reader_settings") val imageReaderSettings: ImageReaderSettings? = null,
    @SerialName("epub_reader_settings") val epubReaderSettings: EpubReaderSettings? = null,
    @SerialName("komf_settings") val komfSettings: KomfSettings? = null,
    @SerialName("transcription_settings") val transcriptionSettings: TranscriptionSettings? = null,
    @SerialName("home_screen_filters") val homeScreenFilters: List<HomeScreenFilter>? = null,
    @SerialName("library_series_filters") val librarySeriesFilters: Map<String, String>? = null,
    @SerialName("series_reader_overrides") val seriesReaderOverrides: Map<String, String>? = null,
    /**
     * Legacy v1 location for ratings. Still written by v1.0.10+ exports
     * so older Kora installs can keep reading their own backups. v1.0.10+
     * imports prefer [userSections] when both are present.
     */
    @SerialName("series_ratings") val seriesRatings: List<RatingExport>? = null,
    /**
     * Per-Komga-user data introduced in v2 (v1.0.10). Map key is the
     * Komga user UUID (`KomgaUser.id.value`). Reading-event history and
     * star ratings live here so they can be attributed to the right user
     * across reinstalls, device transfers and multi-account scenarios.
     */
    @SerialName("user_sections") val userSections: Map<String, UserSection>? = null,
)

/**
 * Wire-format DTO for a single user rating. We do not put the domain
 * [snd.komelia.ratings.SeriesRating] here directly: it's not @Serializable,
 * embeds [kotlin.time.Instant] which is awkward over JSON, and we want the
 * backup file to stay stable even if the domain type evolves.
 *
 * [ratedAt] is epoch milliseconds (matches the SQLite column) so the
 * original timestamp survives round-trips unchanged.
 */
@Serializable
data class RatingExport(
    @SerialName("series_id") val seriesId: String,
    val stars: Int,
    @SerialName("rated_at") val ratedAt: Long,
)

/**
 * Wire-format DTO for one row of the local reading_events log
 * (v1.0.10+). `pageCount` may be null for rows recorded before the
 * page-count tracking landed.
 */
@Serializable
data class ReadingEventExport(
    @SerialName("book_id") val bookId: String,
    @SerialName("event_type") val eventType: String,
    val timestamp: Long,
    @SerialName("page_count") val pageCount: Int? = null,
)

/**
 * Everything attributable to a specific Komga user. On import:
 *  - section's user == current user → restored as-is
 *  - section's user != current user, current user is Komga admin →
 *    "shadow restore" (rows are tagged with the foreign user id so they
 *    surface when that user next signs in on this device)
 *  - section's user != current user, current user is not admin →
 *    silently skipped, reported in [snd.komelia.backup.ImportResult.Success].
 */
@Serializable
data class UserSection(
    @SerialName("series_ratings") val seriesRatings: List<RatingExport> = emptyList(),
    @SerialName("reading_events") val readingEvents: List<ReadingEventExport> = emptyList(),
)
