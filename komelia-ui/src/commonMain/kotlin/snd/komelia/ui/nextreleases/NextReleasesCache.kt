package snd.komelia.ui.nextreleases

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
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

    /**
     * When the last *successful* scan finished.
     *
     * Persisted, unlike the process-lifetime var this used to be. Measured on
     * the tablet 2026-08-21: the calendar holds ~181 tags, the scan is one
     * count query per tag, and the server answers each in ~3s four at a time
     * -- over two minutes of a saturated server. A memory-only timestamp meant
     * paying that on EVERY cold start while the list it produced was already
     * on disk, and anything the user did meanwhile (opening a series) was
     * answered by a server busy with our own scan: a book list that curl gets
     * in 1 154ms took 10 414ms in the app.
     *
     * Seeded by [loadPersistedScanTime], which [NextReleasesScanner.primeFromDisk]
     * calls before the staleness check runs.
     */
    private var lastScanAt: Instant? = null

    /**
     * How long a successful scan is trusted before a re-scan is worth its cost.
     *
     * Twelve hours, not the thirty minutes this was written with. Thirty was
     * chosen against repeated entries into Home, when the cost was believed to
     * be small. It is not: measured on the tablet 2026-08-22, the scan is nine
     * queries of 25 to 31 SECONDS of server time each -- 174 tags cost about
     * 230s of Komga whatever the batching, because the cost is in the tag
     * condition itself.
     *
     * At thirty minutes that meant the server was monopolised for four minutes
     * out of every thirty, and during that window everything else crawled:
     * GET /api/v1/series/{id} took 19 234ms, a book list 26 822ms, with
     * queue=1ms on both -- the app was waiting on a server busy with us.
     *
     * What this data actually is: a release calendar, hand-tagged in Komga.
     * It changes when the user tags a new volume, which is a daily event at
     * most, and the calendar screen still forces a fresh scan on open for
     * whoever wants one now.
     */
    private val scanTtl = 12.hours

    /** True when a fresh scan is worth running (no recent successful one). */
    fun isStale(now: Instant = Clock.System.now()): Boolean {
        val last = lastScanAt ?: return true
        return (now - last) >= scanTtl
    }

    private fun cacheDir() = FileKit.filesDir / "next_releases"
    private fun cacheFile() = cacheDir() / "cache.json"

    /**
     * Kept beside the snapshot rather than inside it: the snapshot's format is
     * a plain list, and a missing or unreadable stamp must mean "scan now",
     * which is what the old behaviour was. A format change could not fail that
     * safely.
     */
    private fun scanStampFile() = cacheDir() / "scanned_at.txt"

    /**
     * Whether a scan did enough work to be worth not repeating for a while.
     *
     * NOT [NextReleasesService.Scan.complete]: that demands every lookup
     * succeed, and the measured run was 178 of 179 -- the single failure being
     * the server returning 5xx under the load of the scan itself. Stamping only
     * perfect scans meant never stamping, so the scan re-ran on every cold
     * start and re-created the conditions for its own failure.
     *
     * A scan that resolved almost everything is a good calendar. One that
     * mostly failed is not, and must be retried rather than trusted for the
     * next half hour.
     */
    private fun NextReleasesService.Scan.worthTrusting(): Boolean {
        if (complete) return true
        if (attempted == 0 || releases.isEmpty()) return false
        return resolved.toDouble() / attempted >= MIN_RESOLVED_RATIO
    }

    /** 178/179 passes; a scan that lost a tenth of its lookups does not. */
    private const val MIN_RESOLVED_RATIO = 0.9

    /** Seeds [lastScanAt] from disk. No-op once the timestamp is known. */
    suspend fun loadPersistedScanTime() {
        if (lastScanAt != null) return
        lastScanAt = runCatching {
            Instant.fromEpochMilliseconds(scanStampFile().readBytes().decodeToString().trim().toLong())
        }.getOrNull()
    }

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
        if (scan.worthTrusting()) {
            val now = Clock.System.now()
            lastScanAt = now
            // Best-effort, like the snapshot write below: losing the stamp
            // costs one extra scan, never a wrong calendar.
            runCatching {
                cacheDir().createDirectories()
                scanStampFile().write(now.toEpochMilliseconds().toString().encodeToByteArray())
            }
        }

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
