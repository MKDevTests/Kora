package snd.komelia.backup

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.StateFlow
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
import snd.komelia.stats.ReadingEvent
import snd.komelia.stats.ReadingEventsRepository
import snd.komelia.updates.AppVersion
import snd.komga.client.book.KomgaBookId
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.user.KomgaUser
import snd.komga.client.user.KomgaUserId
import snd.komga.client.user.ROLE_ADMIN
import kotlin.time.Clock
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

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
    private val readingEvents: ReadingEventsRepository,
    /**
     * Polled on each export/import call. On export, drives the per-user
     * sections + folds any legacy NULL-tagged rows into the current user's
     * section. On import, gates cross-user restore: own section restores
     * freely; foreign sections require the current user to be a Komga
     * admin (`KomgaUser.roleAdmin()`), otherwise they're silently skipped
     * and reported in [ImportResult.Success.sectionsSkipped].
     */
    private val currentUser: StateFlow<KomgaUser?>,
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

        // -- Per-user sections (v2 schema) --------------------------------
        // Fold any rows tagged with NULL `komga_user_id` (legacy data not
        // yet picked up by UserScopeBackfillJob) into the current user's
        // section. If no one is signed in we drop them silently — they
        // stay local and the next session will tag + export them.
        val currentUid = currentUser.value?.id
        val ratingsByUser = foldNullsInto(seriesRatings.listAllByUser(), currentUid)
        val eventsByUser = foldNullsInto(readingEvents.listAllByUser(), currentUid)

        val allUserIds = (ratingsByUser.keys + eventsByUser.keys).filterNotNull().toSet()
        val userSections = allUserIds.associate { uid ->
            uid.value to UserSection(
                seriesRatings = ratingsByUser[uid].orEmpty().map { it.toRatingExport() },
                readingEvents = eventsByUser[uid].orEmpty().map { it.toReadingEventExport() },
            )
        }

        // Top-level legacy `seriesRatings` is still written so v1 readers
        // (Kora ≤ 1.0.9) can ingest their own backups even after we ship
        // v2. We write only the current user's ratings — v1 had no concept
        // of multi-user, and shipping someone else's ratings to a v1
        // reader would silently overwrite the user's own.
        val legacyRatings = currentUid
            ?.let { ratingsByUser[it].orEmpty() }
            ?.map { it.toRatingExport() }

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
                seriesRatings = legacyRatings,
                userSections = userSections,
            )
        )
        return json.encodeToString(BackupBundle.serializer(), bundle)
    }

    /**
     * Merges the NULL-tagged slice of [byUser] into [foldInto]'s entry, or
     * returns [byUser] unchanged when [foldInto] is null. Used so a
     * just-installed v1.0.10 user — whose backfill job may not have run
     * yet — still exports their pre-upgrade events under their own
     * account section.
     */
    private fun <T> foldNullsInto(
        byUser: Map<KomgaUserId?, List<T>>,
        foldInto: KomgaUserId?,
    ): Map<KomgaUserId?, List<T>> {
        val nulls = byUser[null].orEmpty()
        if (foldInto == null || nulls.isEmpty()) return byUser
        val mutable = byUser.toMutableMap()
        mutable.remove(null)
        mutable[foldInto] = (mutable[foldInto].orEmpty()) + nulls
        return mutable
    }

    private fun SeriesRating.toRatingExport() = RatingExport(
        seriesId = seriesId.value,
        stars = stars,
        ratedAt = ratedAt.toEpochMilliseconds(),
    )

    private fun ReadingEvent.toReadingEventExport() = ReadingEventExport(
        bookId = bookId.value,
        eventType = type.name,
        timestamp = timestamp.toEpochMilliseconds(),
        pageCount = pageCount,
    )

    override suspend fun importFromJson(jsonString: String): ImportResult {
        val bundle = try {
            json.decodeFromString(BackupBundle.serializer(), jsonString)
        } catch (e: Exception) {
            return ImportResult.Failure("Not a valid Kora backup file (${e.message ?: e::class.simpleName})")
        }

        // Forward-read older bundles (v1 backups exported by Kora 1.0.7..1.0.9
        // still import on 1.0.10+). Newer-than-known bundles are rejected
        // because we can't safely guess what new sections mean.
        if (bundle.schemaVersion > BACKUP_SCHEMA_VERSION) {
            return ImportResult.Failure(
                "Backup was created by a newer version of Kora (schema v${bundle.schemaVersion})"
            )
        }

        val sections = bundle.sections
        val restored = mutableListOf<String>()
        val skipped = mutableListOf<String>()

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

        // -- v2 per-user sections --------------------------------------
        // Each section is either "your own" (restore freely) or "someone
        // else's" (requires Komga admin; otherwise silently skipped).
        // See ImportResult.Success.sectionsSkipped for the report path.
        val user = currentUser.value
        val isAdmin = user?.roles?.contains(ROLE_ADMIN) == true
        val currentUid = user?.id

        sections.userSections?.let { incoming ->
            for ((userIdStr, section) in incoming) {
                val sectionUid = KomgaUserId(userIdStr)
                val isOwn = sectionUid == currentUid
                if (!isOwn && !isAdmin) {
                    skipped.add("Data for user ${userIdStr.shortIdHint()} (Komga admin required to shadow-restore)")
                    continue
                }
                runCatching {
                    val ratings = section.seriesRatings.map { dto ->
                        SeriesRating(
                            seriesId = KomgaSeriesId(dto.seriesId),
                            stars = dto.stars,
                            ratedAt = Instant.fromEpochMilliseconds(dto.ratedAt),
                        )
                    }
                    val events = section.readingEvents.map { dto ->
                        ReadingEvent(
                            bookId = KomgaBookId(dto.bookId),
                            type = ReadingEvent.Type.valueOf(dto.eventType),
                            timestamp = Instant.fromEpochMilliseconds(dto.timestamp),
                            pageCount = dto.pageCount,
                        )
                    }
                    seriesRatings.replaceAllForUser(sectionUid, ratings)
                    readingEvents.upsertAllForUser(sectionUid, events)
                    val who = if (isOwn) "your data" else "data for ${userIdStr.shortIdHint()}"
                    restored.add("User section ($who, ${ratings.size} ratings + ${events.size} events)")
                }.onFailure {
                    return ImportResult.Failure(
                        "Failed to restore user section ${userIdStr.shortIdHint()}: ${it.message}"
                    )
                }
            }
        }

        // -- v1 legacy ratings fallback --------------------------------
        // Only applied when there are no v2 userSections (otherwise the v2
        // branch above already handled current-user ratings — we'd be
        // double-applying). Treat the top-level ratings as belonging to
        // whoever is signed in now.
        if (sections.userSections.isNullOrEmpty() && !sections.seriesRatings.isNullOrEmpty()) {
            if (currentUid == null) {
                skipped.add("Legacy ratings (${sections.seriesRatings.size}) — sign in first to attribute them")
            } else {
                runCatching {
                    val models = sections.seriesRatings.map { dto ->
                        SeriesRating(
                            seriesId = KomgaSeriesId(dto.seriesId),
                            stars = dto.stars,
                            ratedAt = Instant.fromEpochMilliseconds(dto.ratedAt),
                        )
                    }
                    seriesRatings.replaceAllForUser(currentUid, models)
                    restored.add("Series ratings (legacy v1, ${models.size} entries)")
                }.onFailure {
                    return ImportResult.Failure("Failed to restore legacy ratings: ${it.message}")
                }
            }
        }

        return ImportResult.Success(restored, skipped)
    }

    /**
     * Render a Komga user UUID as a short prefix for log lines and the
     * success/skipped dialog. Avoids dumping the full UUID into a tooltip
     * that nobody can read.
     */
    private fun String.shortIdHint(): String = take(8) + "…"

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
