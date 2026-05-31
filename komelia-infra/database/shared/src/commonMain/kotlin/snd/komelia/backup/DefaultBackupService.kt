package snd.komelia.backup

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
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Rolling window kept in the per-user `reading_events` export. Events older
 * than this are summed into [UserSection.pagesReadLifetimeCarryover] so the
 * bundle stays bounded (~50 KB for a year of completions) instead of
 * accreting for the device's lifetime. 365 days covers every time-windowed
 * stats query (last 7/30 days, streak, monthly chart).
 */
private val eventHistoryWindow = 365.days

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

        // Bound the per-user `reading_events` slice to the last 365 days.
        // Older events are summed into a per-user scalar (`carryover`) and
        // re-imported as a single LIFETIME_CARRYOVER sentinel row, so the
        // pages-lifetime stat survives a device-to-device backup-restore
        // without us shipping years of per-book history in every file.
        // See the matching import branch below.
        val cutoffMs = (Clock.System.now() - eventHistoryWindow).toEpochMilliseconds()

        val allUserIds = (ratingsByUser.keys + eventsByUser.keys).filterNotNull().toSet()
        val userSections = allUserIds.associate { uid ->
            val allEvents = eventsByUser[uid].orEmpty()
            // Separate COMPLETED rows by age + drain any existing carryover
            // rows already in the DB (so re-export after a previous import
            // doesn't lose the inherited carryover).
            val recent = mutableListOf<ReadingEvent>()
            var carryover: Long = 0
            for (event in allEvents) {
                when {
                    event.type == ReadingEvent.Type.LIFETIME_CARRYOVER ->
                        carryover += (event.pageCount ?: 0).toLong()
                    event.timestamp.toEpochMilliseconds() >= cutoffMs ->
                        recent += event
                    else ->
                        carryover += (event.pageCount ?: 0).toLong()
                }
            }
            uid.value to UserSection(
                seriesRatings = ratingsByUser[uid].orEmpty().map { it.toRatingExport() },
                readingEvents = recent.map { it.toReadingEventExport() },
                pagesReadLifetimeCarryover = carryover,
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

    /**
     * Validate + map an imported [RatingExport]. Returns null — caller drops
     * it and reports the count — when the row is malformed: blank series id,
     * stars outside 1..5, or a negative timestamp. Shared by [dryRun] and
     * [importFromJson] so the previewed counts match what is actually applied.
     */
    private fun RatingExport.toValidRatingOrNull(): SeriesRating? {
        if (seriesId.isBlank()) return null
        if (stars !in 1..5) return null
        if (ratedAt < 0) return null
        return SeriesRating(
            seriesId = KomgaSeriesId(seriesId),
            stars = stars,
            ratedAt = Instant.fromEpochMilliseconds(ratedAt),
        )
    }

    /**
     * Validate + map an imported [ReadingEventExport]. Returns null — caller
     * drops it and reports the count — when the row is malformed: blank book
     * id, an event type this build doesn't know (replaces the old
     * [ReadingEvent.Type.valueOf] that threw on unknown values), a negative
     * timestamp, or a negative page count.
     */
    private fun ReadingEventExport.toValidEventOrNull(): ReadingEvent? {
        if (bookId.isBlank()) return null
        val parsedType = ReadingEvent.Type.entries.firstOrNull { it.name == eventType } ?: return null
        if (timestamp < 0) return null
        if (pageCount != null && pageCount < 0) return null
        return ReadingEvent(
            bookId = KomgaBookId(bookId),
            type = parsedType,
            timestamp = Instant.fromEpochMilliseconds(timestamp),
            pageCount = pageCount,
        )
    }

    override suspend fun dryRun(jsonString: String): DryRunResult {
        val bundle = try {
            json.decodeFromString(BackupBundle.serializer(), jsonString)
        } catch (e: Exception) {
            return DryRunResult.Invalid("Not a valid Kora backup file (${e.message ?: e::class.simpleName})")
        }
        if (bundle.schemaVersion > BACKUP_SCHEMA_VERSION) {
            return DryRunResult.Invalid(
                "Backup was created by a newer version of Kora (schema v${bundle.schemaVersion})"
            )
        }

        val s = bundle.sections
        val plans = mutableListOf<SectionPlan>()

        // Single-object settings sections: overwritten wholesale, no per-row count.
        if (s.appSettings != null) plans += SectionPlan("App settings", SectionAction.REPLACE)
        if (s.imageReaderSettings != null) plans += SectionPlan("Image reader settings", SectionAction.REPLACE)
        if (s.epubReaderSettings != null) plans += SectionPlan("EPUB reader settings", SectionAction.REPLACE)
        if (s.komfSettings != null) plans += SectionPlan("Komf settings", SectionAction.REPLACE)
        if (s.transcriptionSettings != null) plans += SectionPlan("Transcription settings", SectionAction.REPLACE)

        // Collection sections: the current set is replaced by the incoming one.
        s.homeScreenFilters?.let {
            plans += SectionPlan(
                "Home filters", SectionAction.REPLACE,
                currentCount = homeFilters.state.value.size, incomingCount = it.size,
            )
        }
        s.librarySeriesFilters?.let {
            plans += SectionPlan(
                "Library filters", SectionAction.REPLACE,
                currentCount = librarySeriesFilters.getAll().size, incomingCount = it.size,
            )
        }
        s.seriesReaderOverrides?.let {
            plans += SectionPlan(
                "Series reader overrides", SectionAction.REPLACE,
                currentCount = seriesReaderOverrides.getAll().size, incomingCount = it.size,
            )
        }

        // Per-user reading data (v2). Foreign sections require Komga admin —
        // mirrors the gate in importFromJson so the preview never promises a
        // restore the apply step would skip.
        val user = currentUser.value
        val isAdmin = user?.roles?.contains(ROLE_ADMIN) == true
        val currentUid = user?.id

        if (!s.userSections.isNullOrEmpty()) {
            val currentRatings = seriesRatings.listAllByUser()
            val currentEvents = readingEvents.listAllByUser()
            for ((uidStr, section) in s.userSections) {
                val sectionUid = KomgaUserId(uidStr)
                val isOwn = sectionUid == currentUid
                val who = if (isOwn) "your data" else "user ${uidStr.shortIdHint()}"
                if (!isOwn && !isAdmin) {
                    plans += SectionPlan(
                        "Reading data ($who)", SectionAction.SKIP,
                        detail = "Komga admin required to shadow-restore",
                    )
                    continue
                }
                val validRatings = section.seriesRatings.count { it.toValidRatingOrNull() != null }
                val validEvents = section.readingEvents.count { it.toValidEventOrNull() != null }
                plans += SectionPlan(
                    "Ratings ($who)", SectionAction.REPLACE,
                    currentCount = currentRatings[sectionUid]?.size ?: 0,
                    incomingCount = validRatings,
                    invalidCount = section.seriesRatings.size - validRatings,
                )
                plans += SectionPlan(
                    "Reading events ($who)", SectionAction.REPLACE,
                    currentCount = currentEvents[sectionUid]?.size ?: 0,
                    incomingCount = validEvents,
                    invalidCount = section.readingEvents.size - validEvents,
                )
            }
        } else if (!s.seriesRatings.isNullOrEmpty()) {
            // Legacy v1 ratings — attributed to whoever is signed in now.
            val valid = s.seriesRatings.count { it.toValidRatingOrNull() != null }
            val invalid = s.seriesRatings.size - valid
            if (currentUid == null) {
                plans += SectionPlan(
                    "Ratings (legacy v1)", SectionAction.SKIP,
                    incomingCount = valid, invalidCount = invalid,
                    detail = "Sign in first to attribute them",
                )
            } else {
                plans += SectionPlan(
                    "Ratings (legacy v1)", SectionAction.REPLACE,
                    currentCount = seriesRatings.listAllByUser()[currentUid]?.size ?: 0,
                    incomingCount = valid, invalidCount = invalid,
                )
            }
        }

        return DryRunResult.Ok(
            ImportPlan(
                schemaVersion = bundle.schemaVersion,
                exportedBy = bundle.exportedBy,
                exportedAt = bundle.exportedAt,
                sections = plans,
            )
        )
    }

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
                    // Skip-and-report malformed rows (out-of-range stars,
                    // unknown event type, blank id, …) instead of letting one
                    // bad row abort the whole section. Counts feed sectionsSkipped.
                    val ratings = section.seriesRatings.mapNotNull { it.toValidRatingOrNull() }
                    val events = section.readingEvents.mapNotNull { it.toValidEventOrNull() }
                    seriesRatings.replaceAllForUser(sectionUid, ratings)
                    readingEvents.upsertAllForUser(sectionUid, events)
                    // Mirror the export-side trim: persist the scalar
                    // carryover so the lifetime pages-read total includes
                    // events that were too old to ship in this bundle.
                    readingEvents.upsertLifetimeCarryover(sectionUid, section.pagesReadLifetimeCarryover)
                    val who = if (isOwn) "your data" else "data for ${userIdStr.shortIdHint()}"
                    restored.add("User section ($who, ${ratings.size} ratings + ${events.size} events)")
                    val dropped = (section.seriesRatings.size - ratings.size) +
                        (section.readingEvents.size - events.size)
                    if (dropped > 0) {
                        skipped.add("$dropped invalid entr${if (dropped == 1) "y" else "ies"} in $who")
                    }
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
                    val models = sections.seriesRatings.mapNotNull { it.toValidRatingOrNull() }
                    seriesRatings.replaceAllForUser(currentUid, models)
                    restored.add("Series ratings (legacy v1, ${models.size} entries)")
                    val dropped = sections.seriesRatings.size - models.size
                    if (dropped > 0) skipped.add("$dropped invalid legacy rating(s)")
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
