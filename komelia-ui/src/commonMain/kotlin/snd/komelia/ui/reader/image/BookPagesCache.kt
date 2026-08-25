package snd.komelia.ui.reader.image

import androidx.compose.ui.unit.IntSize
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.createDirectories
import snd.komelia.ui.common.onDisk
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import snd.komga.client.book.KomgaBookId

/**
 * Process-wide + disk-persisted cache of each book's page list (number +
 * dimensions per page).
 *
 * A page list is a pure function of the book FILE: it only changes when the
 * CBZ itself is replaced. Keying every entry by the book's [fileHash] makes
 * staleness structurally impossible — a re-packed file has a new hash, which
 * simply misses and refetches.
 *
 * Why this exists: opening a book fetches THREE page lists (current, previous
 * and next volume) among ~6 metadata calls, and the user's server serialises
 * requests at ~2s each when cold, so every eliminated call is ~2s off the
 * cold open. Mirrors the LibrarySeriesPageCache / HomeShelfCache pattern:
 * memory first, disk snapshot second, network last.
 */
object BookPagesCache {
    private val memory = mutableMapOf<String, Entry>()
    private val json = Json { ignoreUnknownKeys = true }

    private data class Entry(val fileHash: String, val pages: List<PageMetadata>)

    private fun cacheDir() = FileKit.filesDir / "book_pages"
    private fun cacheFile(bookId: String) = cacheDir() / "$bookId.json"

    /** Cached page list for this exact file version, or null. */
    suspend fun get(bookId: KomgaBookId, fileHash: String): List<PageMetadata>? {
        memory[bookId.value]?.let { return if (it.fileHash == fileHash) it.pages else null }
        val persisted = runCatching {
            json.decodeFromString(
                PersistedPages.serializer(),
                cacheFile(bookId.value).readBytes().decodeToString()
            )
        }.getOrNull() ?: return null
        if (persisted.fileHash != fileHash) return null
        val pages = persisted.pages.map {
            PageMetadata(
                bookId = bookId,
                pageNumber = it.n,
                size = if (it.w != null && it.h != null) IntSize(it.w, it.h) else null,
            )
        }
        memory[bookId.value] = Entry(fileHash, pages)
        return pages
    }

    /** Stores in memory and best-effort on disk. */
    suspend fun put(bookId: KomgaBookId, fileHash: String, pages: List<PageMetadata>) {
        memory[bookId.value] = Entry(fileHash, pages)
        onDisk {
            runCatching {
                cacheDir().createDirectories()
                val persisted = PersistedPages(
                    fileHash = fileHash,
                    pages = pages.map { PersistedPage(it.pageNumber, it.size?.width, it.size?.height) },
                )
                cacheFile(bookId.value).write(
                    json.encodeToString(PersistedPages.serializer(), persisted).encodeToByteArray()
                )
            }
        }
    }
}

@Serializable
private data class PersistedPages(val fileHash: String, val pages: List<PersistedPage>)

@Serializable
private data class PersistedPage(val n: Int, val w: Int?, val h: Int?)
