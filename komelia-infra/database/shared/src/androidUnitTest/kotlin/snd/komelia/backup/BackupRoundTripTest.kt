package snd.komelia.backup

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
import snd.komga.client.book.KomgaBookId
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.user.KomgaUser
import snd.komga.client.user.KomgaUserId
import snd.komga.client.user.ROLE_ADMIN
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Round-trip guard for the JSON backup format (Backup safety 1.3).
 *
 * 1. [exportImportExportIsStable] — populate a service, export, import the
 *    bytes into a fresh empty service, export again: the two payloads
 *    ([BackupBundle.sections], ignoring the timestamped envelope) must be
 *    identical. Catches any field the import/export contract silently drops.
 * 2. [invalidEntriesAreSkippedAndReported] — a bundle with out-of-range
 *    stars, a blank id and an unknown event type imports the valid rows and
 *    reports the dropped ones, and [BackupService.dryRun] previews the same
 *    counts before anything is applied.
 *
 * androidUnitTest (JVM host), NOT jvmTest: like MigrationRegistrationTest,
 * jvmTest pulls the desktop/JVM target of komelia-domain:offline which does
 * not compile. The Android chain (the one we ship) does.
 */
class BackupRoundTripTest {

    private val testUser = KomgaUser(
        id = KomgaUserId("user-1"),
        email = "test@example.org",
        roles = setOf(ROLE_ADMIN),
        sharedAllLibraries = true,
        sharedLibrariesIds = emptySet(),
        labelsAllow = emptySet(),
        labelsExclude = emptySet(),
        ageRestriction = null,
    )

    private val parseJson = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

    private fun buildService(
        ratings: FakeRatingsRepo,
        events: FakeEventsRepo,
        libFilters: FakeLibFiltersRepo,
        overrides: FakeOverridesRepo,
    ) = DefaultBackupService(
        appSettings = SettingsStateWrapper(AppSettings()) {},
        imageReader = SettingsStateWrapper(ImageReaderSettings()) {},
        epubReader = SettingsStateWrapper(EpubReaderSettings()) {},
        komf = SettingsStateWrapper(KomfSettings()) {},
        transcription = SettingsStateWrapper(TranscriptionSettings()) {},
        homeFilters = SettingsStateWrapper<List<HomeScreenFilter>>(emptyList()) {},
        librarySeriesFilters = libFilters,
        seriesReaderOverrides = overrides,
        seriesRatings = ratings,
        readingEvents = events,
        currentUser = MutableStateFlow(testUser),
    )

    @Test
    fun exportImportExportIsStable() = runBlocking {
        val uid = KomgaUserId("user-1")
        val ratings = FakeRatingsRepo().apply {
            byUser[uid] = mutableListOf(
                SeriesRating(KomgaSeriesId("s1"), 5, Instant.fromEpochMilliseconds(1_700_000_000_000)),
                SeriesRating(KomgaSeriesId("s2"), 3, Instant.fromEpochMilliseconds(1_700_000_500_000)),
            )
        }
        val events = FakeEventsRepo().apply {
            byUser[uid] = mutableListOf(
                ReadingEvent(KomgaBookId("b1"), ReadingEvent.Type.COMPLETED, Clock.System.now(), 120),
                ReadingEvent(KomgaBookId("b2"), ReadingEvent.Type.COMPLETED, Clock.System.now(), 88),
            )
        }
        val libFilters = FakeLibFiltersRepo().apply { map[KomgaLibraryId("lib1")] = """{"sort":"name"}""" }
        val overrides = FakeOverridesRepo().apply { map[KomgaSeriesId("s1")] = "LEFT_TO_RIGHT" }

        val export1 = buildService(ratings, events, libFilters, overrides).exportToJson()

        // Fresh, empty target — exactly the "restore on a clean install" case.
        val target = buildService(FakeRatingsRepo(), FakeEventsRepo(), FakeLibFiltersRepo(), FakeOverridesRepo())
        assertIs<ImportResult.Success>(target.importFromJson(export1))
        val export2 = target.exportToJson()

        val s1 = parseJson.decodeFromString(BackupBundle.serializer(), export1).sections
        val s2 = parseJson.decodeFromString(BackupBundle.serializer(), export2).sections
        assertEquals(s1, s2, "export -> import -> export changed the backup payload")
    }

    @Test
    fun invalidEntriesAreSkippedAndReported() = runBlocking {
        val bundleJson = """
            {
              "schema_version": 2,
              "exported_at": "2026-01-01T00:00:00Z",
              "exported_by": "test",
              "sections": {
                "user_sections": {
                  "user-1": {
                    "series_ratings": [
                      {"series_id":"s1","stars":4,"rated_at":1700000000000},
                      {"series_id":"s2","stars":99,"rated_at":1700000000000},
                      {"series_id":"","stars":3,"rated_at":1700000000000}
                    ],
                    "reading_events": [
                      {"book_id":"b1","event_type":"COMPLETED","timestamp":1700000000000,"page_count":100},
                      {"book_id":"b2","event_type":"BOGUS","timestamp":1700000000000}
                    ],
                    "pages_read_lifetime_carryover": 0
                  }
                }
              }
            }
        """.trimIndent()

        val ratings = FakeRatingsRepo()
        val events = FakeEventsRepo()
        val service = buildService(ratings, events, FakeLibFiltersRepo(), FakeOverridesRepo())

        // Preview matches what apply will do.
        val dry = service.dryRun(bundleJson)
        assertIs<DryRunResult.Ok>(dry)
        val ratingPlan = dry.plan.sections.first { it.label.startsWith("Ratings") }
        assertEquals(1, ratingPlan.incomingCount, "only s1 is a valid rating")
        assertEquals(2, ratingPlan.invalidCount, "stars=99 and blank id are invalid")
        val eventPlan = dry.plan.sections.first { it.label.startsWith("Reading events") }
        assertEquals(1, eventPlan.incomingCount, "only b1 is a valid event")
        assertEquals(1, eventPlan.invalidCount, "BOGUS event type is invalid")

        // Apply: valid rows land, invalid rows are dropped + reported.
        val result = service.importFromJson(bundleJson)
        assertIs<ImportResult.Success>(result)
        assertEquals(1, ratings.byUser[KomgaUserId("user-1")]?.size, "only the valid rating is stored")
        assertEquals(1, events.byUser[KomgaUserId("user-1")]?.count { it.type == ReadingEvent.Type.COMPLETED })
        assertTrue(
            result.sectionsSkipped.any { it.contains("invalid") },
            "dropped entries should be reported, got ${result.sectionsSkipped}",
        )
    }
}

// --- In-memory fakes ---------------------------------------------------------
// Only the methods the exporter/importer/dry-run touch are meaningful; the rest
// throw so an accidental new dependency surfaces loudly instead of silently
// returning a bogus value.

private class FakeRatingsRepo : SeriesRatingsRepository {
    val byUser = linkedMapOf<KomgaUserId?, MutableList<SeriesRating>>()

    override suspend fun listAllByUser(): Map<KomgaUserId?, List<SeriesRating>> =
        byUser.mapValues { it.value.toList() }

    override suspend fun replaceAllForUser(userId: KomgaUserId, ratings: List<SeriesRating>) {
        byUser[userId] = ratings.toMutableList()
    }

    override suspend fun replaceAll(ratings: List<SeriesRating>) {
        byUser[null] = ratings.toMutableList()
    }

    override suspend fun listAll(): List<SeriesRating> = byUser.values.flatten()
    override suspend fun backfillNullUserIds(userId: KomgaUserId): Int = 0

    override suspend fun get(seriesId: KomgaSeriesId): SeriesRating? = error("unused")
    override fun observe(seriesId: KomgaSeriesId): Flow<SeriesRating?> = error("unused")
    override suspend fun put(seriesId: KomgaSeriesId, stars: Int) = error("unused")
    override suspend fun delete(seriesId: KomgaSeriesId) = error("unused")
    override suspend fun listAllByStarsDesc(limit: Int): List<SeriesRating> = error("unused")
}

private class FakeEventsRepo : ReadingEventsRepository {
    val byUser = linkedMapOf<KomgaUserId?, MutableList<ReadingEvent>>()

    override suspend fun listAllByUser(): Map<KomgaUserId?, List<ReadingEvent>> =
        byUser.mapValues { it.value.toList() }

    override suspend fun upsertAllForUser(userId: KomgaUserId, events: List<ReadingEvent>): Int {
        val list = byUser.getOrPut(userId) { mutableListOf() }
        for (e in events) {
            list.removeAll { it.bookId == e.bookId && it.type == e.type }
            list += e
        }
        return events.size
    }

    override suspend fun upsertLifetimeCarryover(userId: KomgaUserId, pages: Long) {
        val carryoverBook = KomgaBookId("_carryover_${userId.value}")
        fun MutableList<ReadingEvent>.dropSentinel() =
            removeAll { it.type == ReadingEvent.Type.LIFETIME_CARRYOVER && it.bookId == carryoverBook }
        if (pages == 0L) {
            byUser[userId]?.dropSentinel()
            return
        }
        val list = byUser.getOrPut(userId) { mutableListOf() }
        list.dropSentinel()
        list += ReadingEvent(carryoverBook, ReadingEvent.Type.LIFETIME_CARRYOVER, Instant.fromEpochMilliseconds(0), pages.toInt())
    }

    override suspend fun backfillNullUserIds(userId: KomgaUserId): Int = 0

    override suspend fun record(bookId: KomgaBookId, type: ReadingEvent.Type, at: Instant, pageCount: Int?) = error("unused")
    override suspend fun countSince(type: ReadingEvent.Type, since: Instant): Int = error("unused")
    override suspend fun sumPagesSince(type: ReadingEvent.Type, since: Instant): Long = error("unused")
    override suspend fun sumPagesLifetime(type: ReadingEvent.Type): Long = error("unused")
    override suspend fun distinctDates(type: ReadingEvent.Type, limit: Int): List<String> = error("unused")
    override suspend fun monthlyBuckets(type: ReadingEvent.Type, since: Instant): Map<String, Int> = error("unused")
    override suspend fun dailyBuckets(type: ReadingEvent.Type, since: Instant): Map<String, Int> = error("unused")
    override suspend fun lifetimeDistinctBooks(type: ReadingEvent.Type): Int = error("unused")
    override suspend fun clear(type: ReadingEvent.Type) = error("unused")
    override suspend fun sumPagesLifetimeCarryover(): Long = error("unused")
}

private class FakeLibFiltersRepo : LibrarySeriesFiltersRepository {
    val map = linkedMapOf<KomgaLibraryId, String>()
    override suspend fun getAll(): Map<KomgaLibraryId, String> = map.toMap()
    override suspend fun deleteAll() = map.clear()
    override suspend fun put(libraryId: KomgaLibraryId, json: String) {
        map[libraryId] = json
    }

    override suspend fun get(libraryId: KomgaLibraryId): String? = map[libraryId]
    override suspend fun delete(libraryId: KomgaLibraryId) {
        map.remove(libraryId)
    }
}

private class FakeOverridesRepo : SeriesReaderOverridesRepository {
    val map = linkedMapOf<KomgaSeriesId, String>()
    override suspend fun getAll(): Map<KomgaSeriesId, String> = map.toMap()
    override suspend fun deleteAll() = map.clear()
    override suspend fun putReadingDirection(seriesId: KomgaSeriesId, direction: String) {
        map[seriesId] = direction
    }

    override suspend fun getReadingDirection(seriesId: KomgaSeriesId): String? = map[seriesId]
    override suspend fun delete(seriesId: KomgaSeriesId) {
        map.remove(seriesId)
    }
}
