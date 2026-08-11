package snd.komelia.autobackup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.model.AutobackupFrequency
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger { }

const val autobackupPeriodicWorkName = "kora_autobackup"
const val autobackupOneTimeWorkName = "kora_autobackup_now"

/**
 * Watches the autobackup settings and keeps WorkManager in sync.
 *
 * - enabled && folderUri set → enqueue a PeriodicWorkRequest with
 *   ExistingPeriodicWorkPolicy.UPDATE keyed on [autobackupPeriodicWorkName].
 * - enabled false OR folderUri null → cancel that unique work.
 *
 * Frequency changes hit the same UPDATE policy so the next firing uses
 * the new period.
 */
object AutobackupScheduler {

    suspend fun observe(context: Context, settings: CommonSettingsRepository) {
        val triggers = combine(
            settings.getAutobackupEnabled().distinctUntilChanged(),
            settings.getAutobackupFolderUri().distinctUntilChanged(),
            settings.getAutobackupFrequency().distinctUntilChanged(),
        ) { enabled, folderUri, frequency ->
            Triple(enabled, folderUri, frequency)
        }
        triggers.collectLatest { (enabled, folderUri, frequency) ->
            applyScheduleState(context, enabled, folderUri, frequency)
        }
    }

    private fun applyScheduleState(
        context: Context,
        enabled: Boolean,
        folderUri: String?,
        frequency: AutobackupFrequency,
    ) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled || folderUri.isNullOrBlank()) {
            workManager.cancelUniqueWork(autobackupPeriodicWorkName)
            logger.info { "Autobackup disabled — cancelled $autobackupPeriodicWorkName" }
            return
        }
        // The only recurring work Kora schedules, and it had no conditions at
        // all: Android was free to wake the device to write a backup at 3%
        // battery or with no room left to write it. A backup is never urgent —
        // it can wait for a charge and for free space. Deliberately not
        // setRequiresDeviceIdle: on a tablet that is picked up and put down all
        // day, idle can go unmet for days, and a backup that never runs is
        // worse than one that runs at an awkward moment.
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<AutobackupWorker>(
            frequency.periodDays,
            TimeUnit.DAYS,
        ).setConstraints(constraints).build()
        workManager.enqueueUniquePeriodicWork(
            autobackupPeriodicWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        logger.info { "Autobackup scheduled — every ${frequency.periodDays} day(s)" }
    }

    /**
     * Fires a one-shot run, used by the "Backup now" button and by the
     * first-run feedback right after the user flips the toggle on. Safe
     * to call even when periodic work is already enqueued: this uses a
     * separate unique work name with REPLACE so back-to-back taps don't
     * pile up.
     *
     * No constraints here, unlike the periodic request: the user asked for it
     * now, and "now" means now even at 5% battery.
     */
    fun triggerImmediate(context: Context) {
        val request = OneTimeWorkRequestBuilder<AutobackupWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            autobackupOneTimeWorkName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
