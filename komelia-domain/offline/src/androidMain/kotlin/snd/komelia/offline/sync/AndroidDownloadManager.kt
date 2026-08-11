package snd.komelia.offline.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import snd.komga.client.book.KomgaBookId
import java.util.concurrent.TimeUnit

/**
 *  recommended way to manage long-running download tasks
 *  should be able to execute and reschedule the work even if app is killed in background
 *  https://developer.android.com/reference/androidx/work/WorkManager
 */
class AndroidDownloadManager(
    private val context: Context,
) : PlatformDownloadManager {

    override suspend fun launchBookDownload(bookId: KomgaBookId) {

        // Without constraints the worker ran whether or not there was a network
        // and whether or not there was room to write, failed, and the download
        // was simply lost. WorkManager can wait for both conditions instead of
        // burning a wake-up on an attempt that cannot succeed.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(
                Data.Builder()
                    .putString(bookIdDataKey, bookId.value)
                    .build()
            ).build()

        // KEEP rather than REPLACE: asking for the same book twice used to
        // cancel a download that was already running and start it over.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(bookId.value, ExistingWorkPolicy.KEEP, request)
    }

    override suspend fun cancelBookDownload(bookId: KomgaBookId) {
        WorkManager.getInstance(context)
            .cancelUniqueWork(bookId.value)
    }
}