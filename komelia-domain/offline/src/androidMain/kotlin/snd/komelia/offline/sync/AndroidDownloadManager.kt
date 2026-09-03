package snd.komelia.offline.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import snd.komelia.offline.settings.OfflineSettingsRepository
import snd.komga.client.book.KomgaBookId
import java.util.concurrent.TimeUnit
import snd.komelia.offline.book.model.DownloadOrigin

/**
 *  recommended way to manage long-running download tasks
 *  should be able to execute and reschedule the work even if app is killed in background
 *  https://developer.android.com/reference/androidx/work/WorkManager
 */
class AndroidDownloadManager(
    private val context: Context,
    private val settingsRepository: OfflineSettingsRepository,
) : PlatformDownloadManager {

    override suspend fun launchBookDownload(bookId: KomgaBookId, origin: DownloadOrigin) {

        // Without constraints the worker ran whether or not there was a network
        // and whether or not there was room to write, failed, and the download
        // was simply lost. WorkManager can wait for both conditions instead of
        // burning a wake-up on an attempt that cannot succeed.
        //
        // The two user-facing brakes are read here rather than baked in: both
        // are off by default and the app warns about mobile data instead of
        // refusing to download over it. Turning one on makes WorkManager *wait*
        // for the condition, so a queued book resumes on its own once the
        // tablet is on Wi-Fi or on the charger — nothing is dropped.
        val wifiOnly = settingsRepository.getDownloadWifiOnly().first()
        val chargingOnly = settingsRepository.getDownloadWhileChargingOnly().first()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(chargingOnly)
            .setRequiresStorageNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(
                Data.Builder()
                    .putString(bookIdDataKey, bookId.value)
                    .putString(downloadOriginDataKey, origin.name)
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
