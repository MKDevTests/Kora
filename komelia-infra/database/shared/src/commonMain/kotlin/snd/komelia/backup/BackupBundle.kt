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
 * Bumped on backwards-incompatible changes. Imports of any other version
 * are rejected with a friendly message; downgrades are not auto-migrated.
 */
const val BACKUP_SCHEMA_VERSION = 1

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
)
