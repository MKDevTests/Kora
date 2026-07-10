package snd.komelia.ui.nextreleases

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
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

    /** Updates the in-memory cache and persists to disk. Best-effort — a write failure isn't fatal. */
    suspend fun update(fresh: List<NextReleasesService.UpcomingRelease>) {
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
