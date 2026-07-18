package snd.komelia.ui.nextreleases

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId

/**
 * Process-wide + disk-persisted cache of the last computed upcoming-releases
 * list. Scanning every `nextrelease:*` tag across every library costs one
 * ~2s Komga query per matching tag (same server-query cost as the Genre
 * tab's discovery), so without a cache every Home-card render or screen
 * open re-pays that full cost — and an in-memory-only cache still repays it
 * once per app launch. Persisting to disk (mirrors
 * [snd.komelia.ui.library.LibraryGenreTabState]'s genre catalog) means a
 * cold start shows the last known list instantly while a fresh scan runs
 * silently in the background and overwrites both the memory and disk copy
 * once it resolves.
 */
object NextReleasesCache {
    /** In-memory hit — fastest path, cleared on process restart. */
    var releases: List<NextReleasesService.UpcomingRelease>? = null

    /** When the last *successful* scan finished. Null = none this process. */
    private var lastScanAt: Instant? = null

    /**
     * How long a successful scan is trusted before a re-scan is worth its cost.
     * A scan is one Komga query per `nextrelease:` tag; without this the scan
     * re-ran on every single entry into Home, which is what made the feature
     * expensive enough to notice in the battery stats.
     */
    private val scanTtl = 30.minutes

    /** True when a fresh scan is worth running (no recent successful one). */
    fun isStale(now: Instant = Clock.System.now()): Boolean {
        val last = lastScanAt ?: return true
        return (now - last) >= scanTtl
    }

    private fun cacheDir() = FileKit.filesDir / "next_releases"
    private fun cacheFile() = cacheDir() / "cache.json"

    /** Reads the disk snapshot (survives process restart). Null if none yet, or unreadable. */
    suspend fun loadPersisted(): List<NextReleasesService.UpcomingRelease>? = runCatching {
        val json = cacheFile().readBytes().decodeToString()
        Json.decodeFromString(ListSerializer(PersistedRelease.serializer()), json).map {
            NextReleasesService.UpcomingRelease(
                seriesId = KomgaSeriesId(it.seriesId),
                seriesTitle = it.seriesTitle,
                libraryId = KomgaLibraryId(it.libraryId),
                volume = it.volume,
                date = LocalDate.parse(it.date),
            )
        }
    }.getOrNull()

    /**
     * Stores the result of a scan, in memory and on disk.
     *
     * An **incomplete** scan (a query failed) is never allowed to replace a
     * non-empty cache with an empty list. That exact sequence — a timeout
     * yielding an empty list, persisted over a good one — is what left the
     * calendar permanently blank despite the tags still being there.
     * Best-effort on the disk write: a write failure isn't fatal.
     */
    suspend fun update(scan: NextReleasesService.Scan) {
        if (!scan.complete && scan.releases.isEmpty() && !releases.isNullOrEmpty()) return
        if (scan.complete) lastScanAt = Clock.System.now()

        val fresh = scan.releases
        releases = fresh
        runCatching {
            cacheDir().createDirectories()
            val snapshots = fresh.map {
                PersistedRelease(
                    seriesId = it.seriesId.value,
                    seriesTitle = it.seriesTitle,
                    libraryId = it.libraryId.value,
                    volume = it.volume,
                    date = it.date.toString(),
                )
            }
            cacheFile().write(
                Json.encodeToString(ListSerializer(PersistedRelease.serializer()), snapshots).encodeToByteArray()
            )
        }
    }
}

@Serializable
private data class PersistedRelease(
    val seriesId: String,
    val seriesTitle: String,
    val libraryId: String,
    val volume: String,
    val date: String,
)
