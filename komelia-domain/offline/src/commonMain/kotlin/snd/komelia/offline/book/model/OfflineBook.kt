package snd.komelia.offline.book.model

import io.github.vinceglb.filekit.PlatformFile
import snd.komga.client.book.KomgaBookId
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId
import kotlin.time.Instant

data class OfflineBook(
    val id: KomgaBookId,
    val seriesId: KomgaSeriesId,
    val libraryId: KomgaLibraryId,
    val name: String,
    val number: Int,
    val deleted: Boolean,
    val fileHash: String,
    val oneshot: Boolean,
    val url: String,

    val size: String,
    val sizeBytes: Long,
    val created: Instant,
    val lastModified: Instant,

    val remoteFileLastModified: Instant,
    val localFileLastModified: Instant,
    val remoteUnavailable: Boolean,
    val fileDownloadPath: PlatformFile,

    /**
     * Defaults to MANUAL, which is what every book downloaded before this
     * field existed actually was — and the safe reading for any caller that
     * has not been taught to say otherwise: the cleaner leaves MANUAL alone.
     */
    val downloadOrigin: DownloadOrigin = DownloadOrigin.MANUAL,
) {

    fun markRemoteUnavailable(): OfflineBook {
        return copy(remoteUnavailable = true)
    }


}
