package snd.komelia.backup

import kotlinx.serialization.json.Json
import snd.komelia.db.AppSettings
import snd.komelia.db.EpubReaderSettings
import snd.komelia.db.ImageReaderSettings
import snd.komelia.db.KomfSettings
import snd.komelia.db.SettingsStateWrapper
import snd.komelia.db.TranscriptionSettings
import snd.komelia.homefilters.HomeScreenFilter
import snd.komelia.libraryfilters.LibrarySeriesFiltersRepository
import snd.komelia.ratings.SeriesRating
import snd.komelia.ratings.SeriesRatingsRepository
import snd.komelia.reader.SeriesReaderOverridesRepository
import snd.komelia.updates.AppVersion
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Default implementation that reaches each [SettingsStateWrapper] directly to
 * snapshot its in-memory value, and uses the widened repository interfaces
 * for the map-shaped sections.
 *
 * Imports apply section-by-section. The first failing section aborts the
 * whole import: previously-applied sections stay applied (no DB-wide
 * transaction in v1) but the caller is told exactly where it stopped.
 */
class DefaultBackupService(
    private val appSettings: SettingsStateWrapper<AppSettings>,
    private val imageReader: SettingsStateWrapper<ImageReaderSettings>,
    private val epubReader: SettingsStateWrapper<EpubReaderSettings>,
    private val komf: SettingsStateWrapper<KomfSettings>,
    private val transcription: SettingsStateWrapper<TranscriptionSettings>,
    private val homeFilters: SettingsStateWrapper<List<HomeScreenFilter>>,
    private val librarySeriesFilters: LibrarySeriesFiltersRepository,
    private val seriesReaderOverrides: SeriesReaderOverridesRepository,
    private val seriesRatings: SeriesRatingsRepository,
) : BackupService {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    override suspend fun exportToJson(): String {
        val app = appSettings.state.value.sanitizedForExport()
        // ortUpscalerUserModelPath is a PlatformFile pointing at a local
        // ONNX model. After a re-install the path is almost certainly
        // invalid, and PlatformFile's wire format may not survive a
        // cross-device move, so we don't round-trip it.
        val image = imageReader.state.value.copy(ortUpscalerUserModelPath = null)
        val epub = epubReader.state.value
        val komfValue = komf.state.value
        val trans = transcription.state.value
        val home = homeFilters.state.value
        val libFilters = librarySeriesFilters.getAll().mapKeys { (id, _) -> id.value }
        val seriesOverrides = seriesReaderOverrides.getAll().mapKeys { (id, _) -> id.value }
        val ratings = seriesRatings.listAll().map { rating ->
            RatingExport(
                seriesId = rating.seriesId.value,
                stars = rating.stars,
                ratedAt = rating.ratedAt.toEpochMilliseconds(),
            )
        }

        val bundle = BackupBundle(
            schemaVersion = BACKUP_SCHEMA_VERSION,
            exportedAt = Clock.System.now().toString(),
            exportedBy = "Kora ${AppVersion.current} (Android)",
            sections = BackupSections(
                appSettings = app,
                imageReaderSettings = image,
                epubReaderSettings = epub,
                komfSettings = komfValue,
                transcriptionSettings = trans,
                homeScreenFilters = home,
                librarySeriesFilters = libFilters,
                seriesReaderOverrides = seriesOverrides,
                seriesRatings = ratings,
            )
        )
        return json.encodeToString(BackupBundle.serializer(), bundle)
    }

    override suspend fun importFromJson(jsonString: String): ImportResult {
        val bundle = try {
            json.decodeFromString(BackupBundle.serializer(), jsonString)
        } catch (e: Exception) {
            return ImportResult.Failure("Not a valid Kora backup file (${e.message ?: e::class.simpleName})")
        }

        if (bundle.schemaVersion != BACKUP_SCHEMA_VERSION) {
            return ImportResult.Failure(
                if (bundle.schemaVersion > BACKUP_SCHEMA_VERSION) {
                    "Backup was created by a newer version of Kora (schema v${bundle.schemaVersion})"
                } else {
                    "Backup uses an older schema (v${bundle.schemaVersion}) that is no longer supported"
                }
            )
        }

        val sections = bundle.sections
        val restored = mutableListOf<String>()

        // App settings: preserve current volatile fields so we don't blow
        // away the user's active session/server when importing.
        sections.appSettings?.let { incoming ->
            runCatching {
                appSettings.transform { current ->
                    incoming.copy(
                        username = current.username,
                        serverUrl = current.serverUrl,
                        updateLastCheckedTimestamp = current.updateLastCheckedTimestamp,
                        updateLastCheckedReleaseVersion = current.updateLastCheckedReleaseVersion,
                        updateDismissedVersion = current.updateDismissedVersion,
                        lastSelectedLibraryId = current.lastSelectedLibraryId,
                        // Autobackup is device-local: the SAF tree URI from another
                        // device would point at nothing here. Keep the user's
                        // own settings + recorded state.
                        autobackupEnabled = current.autobackupEnabled,
                        autobackupFolderUri = current.autobackupFolderUri,
                        autobackupFrequency = current.autobackupFrequency,
                        autobackupMaxKeep = current.autobackupMaxKeep,
                        autobackupLastSuccessAt = current.autobackupLastSuccessAt,
                        autobackupLastFailureAt = current.autobackupLastFailureAt,
                        autobackupLastFailureMessage = current.autobackupLastFailureMessage,
                    )
                }
                restored.add("App settings")
            }.onFailure { return ImportResult.Failure("Failed to restore App settings: ${it.message}") }
        }

        sections.imageReaderSettings?.let { incoming ->
            runCatching {
                imageReader.transform { current ->
                    // Backups strip ortUpscalerUserModelPath (local file path).
                    // If the incoming has null but the user has a path set
                    // locally, keep theirs — otherwise nuking it on every
                    // import would silently break their custom upscaler.
                    incoming.copy(
                        ortUpscalerUserModelPath = incoming.ortUpscalerUserModelPath
                            ?: current.ortUpscalerUserModelPath
                    )
                }
                restored.add("Image reader settings")
            }.onFailure { return ImportResult.Failure("Failed to restore Image reader settings: ${it.message}") }
        }

        sections.epubReaderSettings?.let { incoming ->
            runCatching {
                epubReader.transform { incoming }
                restored.add("EPUB reader settings")
            }.onFailure { return ImportResult.Failure("Failed to restore EPUB reader settings: ${it.message}") }
        }

        sections.komfSettings?.let { incoming ->
            runCatching {
                komf.transform { incoming }
                restored.add("Komf settings")
            }.onFailure { return ImportResult.Failure("Failed to restore Komf settings: ${it.message}") }
        }

        sections.transcriptionSettings?.let { incoming ->
            runCatching {
                transcription.transform { incoming }
                restored.add("Transcription settings")
            }.onFailure { return ImportResult.Failure("Failed to restore Transcription settings: ${it.message}") }
        }

        sections.homeScreenFilters?.let { incoming ->
            runCatching {
                homeFilters.transform { incoming }
                restored.add("Home filters (${incoming.size} entries)")
            }.onFailure { return ImportResult.Failure("Failed to restore Home filters: ${it.message}") }
        }

        sections.librarySeriesFilters?.let { incoming ->
            runCatching {
                librarySeriesFilters.deleteAll()
                for ((id, jsonBlob) in incoming) {
                    librarySeriesFilters.put(KomgaLibraryId(id), jsonBlob)
                }
                restored.add("Library filters (${incoming.size} entries)")
            }.onFailure { return ImportResult.Failure("Failed to restore Library filters: ${it.message}") }
        }

        sections.seriesReaderOverrides?.let { incoming ->
            runCatching {
                seriesReaderOverrides.deleteAll()
                for ((id, direction) in incoming) {
                    seriesReaderOverrides.putReadingDirection(KomgaSeriesId(id), direction)
                }
                restored.add("Series reader overrides (${incoming.size} entries)")
            }.onFailure { return ImportResult.Failure("Failed to restore Series reader overrides: ${it.message}") }
        }

        sections.seriesRatings?.let { incoming ->
            runCatching {
                // replaceAll is transactional and preserves each row's
                // original ratedAt — that's why we use it instead of
                // looping put() which would stamp every entry with now().
                val models = incoming.map { dto ->
                    SeriesRating(
                        seriesId = KomgaSeriesId(dto.seriesId),
                        stars = dto.stars,
                        ratedAt = Instant.fromEpochMilliseconds(dto.ratedAt),
                    )
                }
                seriesRatings.replaceAll(models)
                restored.add("Series ratings (${incoming.size} entries)")
            }.onFailure { return ImportResult.Failure("Failed to restore Series ratings: ${it.message}") }
        }

        return ImportResult.Success(restored)
    }

    /** Strips transient/server-coupled fields so the export is portable. */
    private fun AppSettings.sanitizedForExport(): AppSettings = AppSettings().let { defaults ->
        copy(
            username = defaults.username,
            serverUrl = defaults.serverUrl,
            updateLastCheckedTimestamp = defaults.updateLastCheckedTimestamp,
            updateLastCheckedReleaseVersion = defaults.updateLastCheckedReleaseVersion,
            updateDismissedVersion = defaults.updateDismissedVersion,
            lastSelectedLibraryId = defaults.lastSelectedLibraryId,
            autobackupEnabled = defaults.autobackupEnabled,
            autobackupFolderUri = defaults.autobackupFolderUri,
            autobackupFrequency = defaults.autobackupFrequency,
            autobackupMaxKeep = defaults.autobackupMaxKeep,
            autobackupLastSuccessAt = defaults.autobackupLastSuccessAt,
            autobackupLastFailureAt = defaults.autobackupLastFailureAt,
            autobackupLastFailureMessage = defaults.autobackupLastFailureMessage,
        )
    }
}
