package snd.komelia.offline.sync

import snd.komga.client.book.KomgaBookId
import snd.komelia.offline.book.model.DownloadOrigin

/**
 * Start and manage long-running download jobs (e.g. using system specific APIs)
 * Optionally manages the display of system notifications
 */

interface PlatformDownloadManager {
    suspend fun launchBookDownload(bookId: KomgaBookId, origin: DownloadOrigin)
    suspend fun cancelBookDownload(bookId: KomgaBookId)
}