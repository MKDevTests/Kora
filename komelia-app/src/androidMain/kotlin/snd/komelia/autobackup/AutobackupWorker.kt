package snd.komelia.autobackup

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.PermissionChecker
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.first
import snd.komelia.MainActivity
import snd.komelia.backup.BackupService
import snd.komelia.settings.CommonSettingsRepository
import kotlin.time.Clock

private val logger = KotlinLogging.logger { }

const val autobackupFailureChannelId = "autobackup_failures_channel"
private const val autobackupFailureNotificationId = 9_001
private const val autobackupFilePrefix = "kora-autobackup-"
private const val autobackupFileSuffix = ".json"

/**
 * Writes the user's settings bundle into the SAF-picked folder, then
 * prunes oldest `kora-autobackup-*.json` files past `autobackupMaxKeep`.
 *
 * Manual exports (`kora-backup-*.json` from the Backup & Restore screen)
 * are NEVER touched — the rotation filter requires the `autobackup` prefix.
 */
class AutobackupWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val backupService: BackupService,
    private val settingsRepository: CommonSettingsRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            runBackup()
            settingsRepository.putAutobackupLastSuccessAt(Clock.System.now())
            settingsRepository.putAutobackupLastFailure(timestamp = null, message = null)
            applicationContext.cancelFailureNotification()
            Result.success()
        } catch (t: Throwable) {
            logger.error(t) { "Autobackup failed" }
            val message = t.message ?: t::class.simpleName ?: "Unknown error"
            settingsRepository.putAutobackupLastFailure(Clock.System.now(), message)
            applicationContext.postFailureNotification(message)
            Result.failure()
        }
    }

    private suspend fun runBackup() {
        val folderUriString = settingsRepository.getAutobackupFolderUri().first()
            ?: error("No autobackup folder selected")
        val maxKeep = settingsRepository.getAutobackupMaxKeep().first().coerceIn(1, 10)

        val folder = DocumentFile.fromTreeUri(applicationContext, Uri.parse(folderUriString))
            ?: error("Backup folder is no longer accessible")
        if (!folder.exists() || !folder.isDirectory || !folder.canWrite()) {
            error("Backup folder is no longer accessible")
        }

        val json = backupService.exportToJson()
        val fileName = autobackupFilePrefix + timestampSlug(Clock.System.now()) + autobackupFileSuffix
        val file = folder.createFile("application/json", fileName)
            ?: error("Could not create $fileName in backup folder")

        applicationContext.contentResolver.openOutputStream(file.uri)?.use { stream ->
            stream.write(json.toByteArray(Charsets.UTF_8))
        } ?: run {
            file.delete()
            error("Could not open backup file for writing")
        }

        rotate(folder, maxKeep)
    }

    private fun rotate(folder: DocumentFile, maxKeep: Int) {
        val backups = folder.listFiles()
            .filter { doc ->
                val name = doc.name ?: return@filter false
                name.startsWith(autobackupFilePrefix) && name.endsWith(autobackupFileSuffix)
            }
            .sortedByDescending { it.lastModified() }

        backups.drop(maxKeep).forEach { stale ->
            runCatching { stale.delete() }
                .onFailure { logger.warn(it) { "Could not prune ${stale.name}" } }
        }
    }

    private fun timestampSlug(now: kotlin.time.Instant): String {
        val date = java.time.LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()),
            java.time.ZoneId.systemDefault(),
        )
        return "%04d-%02d-%02d-%02d%02d%02d".format(
            date.year, date.monthValue, date.dayOfMonth,
            date.hour, date.minute, date.second,
        )
    }

    private fun Context.postFailureNotification(message: String) {
        if (!canPostNotifications()) return
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, autobackupFailureChannelId)
            .setContentTitle("Kora autobackup failed")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(this)
            .notify(autobackupFailureNotificationId, notification)
    }

    private fun Context.cancelFailureNotification() {
        if (!canPostNotifications()) return
        NotificationManagerCompat.from(this).cancel(autobackupFailureNotificationId)
    }

    private fun Context.canPostNotifications(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return true
        return PermissionChecker.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PermissionChecker.PERMISSION_GRANTED
    }
}
