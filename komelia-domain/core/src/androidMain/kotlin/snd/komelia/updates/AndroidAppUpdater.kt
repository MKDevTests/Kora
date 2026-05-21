package snd.komelia.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.STATUS_FAILURE
import android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED
import android.content.pm.PackageInstaller.STATUS_FAILURE_BLOCKED
import android.content.pm.PackageInstaller.STATUS_FAILURE_CONFLICT
import android.content.pm.PackageInstaller.STATUS_FAILURE_INCOMPATIBLE
import android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID
import android.content.pm.PackageInstaller.STATUS_FAILURE_STORAGE
import android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION
import android.content.pm.PackageInstaller.STATUS_SUCCESS
import android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.PermissionChecker
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.io.readByteArray
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

private const val INSTALL_FAILURE_CHANNEL_ID = "kora_install_failures"
private const val INSTALL_FAILURE_NOTIFICATION_ID = 9_101

class AndroidAppUpdater(
    private val githubClient: UpdateClient,
    private val context: Context,
) : AppUpdater {
    private var inProgress = AtomicBoolean(false)

    override suspend fun getReleases(): List<AppRelease> {
        return githubClient.getKomeliaReleases().map { it.toAppRelease() }
    }

    override suspend fun updateToLatest(): Flow<UpdateProgress>? {
        val latest = githubClient.getKomeliaLatestRelease().toAppRelease()
        return updateTo(latest)
    }

    override fun updateTo(release: AppRelease): Flow<UpdateProgress>? {
        if (!inProgress.compareAndSet(false, true)) return null
        if (release.assetUrl == null) return null

        return flow {
            emit(UpdateProgress(0, 0))
            val sessionParams = PackageInstaller.SessionParams(MODE_FULL_INSTALL)
            val packageInstaller = context.packageManager.packageInstaller
            val sessionId = packageInstaller.createSession(sessionParams)
            val session = packageInstaller.openSession(sessionId)

            githubClient.streamFile(release.assetUrl) { response -> streamToSession(response, session) }

            val receiverIntent = Intent(context, PackageInstallerStatusReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val receiverPendingIntent = PendingIntent.getBroadcast(context, 0, receiverIntent, flags)
            session.commit(receiverPendingIntent.intentSender)
            session.close()
            inProgress.set(false)
        }
    }

    private suspend fun FlowCollector<UpdateProgress>.streamToSession(
        response: HttpResponse,
        session: PackageInstaller.Session
    ) {
        val length = response.headers["Content-Length"]?.toLong() ?: 0L
        emit(UpdateProgress(length, 0))
        val channel = response.bodyAsChannel().counted()
        val sessionStream = session.openWrite("komelia", 0, -1)
        sessionStream.buffered().use { bufferedSessionStream ->
            while (!channel.isClosedForRead) {

                val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                while (!packet.exhausted()) {
                    val bytes = packet.readByteArray()
                    bufferedSessionStream.write(bytes)
                }
                emit(UpdateProgress(length, channel.totalBytesRead))
            }
            bufferedSessionStream.flush()
            session.fsync(sessionStream)
        }
    }

    private fun GithubRelease.toAppRelease(): AppRelease {
        val asset = assets.firstOrNull { it.name.endsWith(".apk") }

        return AppRelease(
            version = AppVersion.fromString(tagName),
            publishDate = publishedAt,
            releaseNotesBody = body.replace("\r", ""),
            htmlUrl = htmlUrl,
            assetName = asset?.name,
            assetUrl = asset?.browserDownloadUrl
        )
    }
}

class PackageInstallerStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val pkg = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)

        when (status) {
            STATUS_PENDING_USER_ACTION -> {
                logger.info { "Install pending user action (pkg=$pkg)" }
                val confirmationIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmationIntent != null) {
                    context.startActivity(confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    logger.warn { "STATUS_PENDING_USER_ACTION without EXTRA_INTENT — install dialog cannot be shown" }
                }
            }

            STATUS_SUCCESS -> {
                logger.info { "Install succeeded (pkg=$pkg)" }
            }

            STATUS_FAILURE,
            STATUS_FAILURE_ABORTED,
            STATUS_FAILURE_BLOCKED,
            STATUS_FAILURE_CONFLICT,
            STATUS_FAILURE_INCOMPATIBLE,
            STATUS_FAILURE_INVALID,
            STATUS_FAILURE_STORAGE -> {
                val label = installFailureLabel(status)
                logger.error { "Install failed: $label (pkg=$pkg, status=$status, message=$statusMessage)" }
                context.postInstallFailureNotification(label, statusMessage)
            }

            else -> {
                logger.warn { "Unknown PackageInstaller status=$status (pkg=$pkg, message=$statusMessage)" }
            }
        }
    }

    private fun installFailureLabel(status: Int): String = when (status) {
        STATUS_FAILURE -> "Install failed"
        STATUS_FAILURE_ABORTED -> "Install aborted"
        STATUS_FAILURE_BLOCKED -> "Install blocked by the system"
        STATUS_FAILURE_CONFLICT -> "Install conflict with an existing app"
        STATUS_FAILURE_INCOMPATIBLE -> "Signature or version incompatible — try uninstalling first"
        STATUS_FAILURE_INVALID -> "Invalid APK"
        STATUS_FAILURE_STORAGE -> "Not enough storage to install"
        else -> "Install failed (status=$status)"
    }

    private fun Context.postInstallFailureNotification(label: String, statusMessage: String?) {
        if (!canPostNotifications()) return
        ensureInstallFailureChannel()

        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = openAppIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val body = listOfNotNull(label, statusMessage?.takeIf { it.isNotBlank() })
            .joinToString(separator = ": ")

        val notification = NotificationCompat.Builder(this, INSTALL_FAILURE_CHANNEL_ID)
            .setContentTitle("Kora update failed")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
            .build()

        NotificationManagerCompat.from(this)
            .notify(INSTALL_FAILURE_NOTIFICATION_ID, notification)
    }

    private fun Context.ensureInstallFailureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(INSTALL_FAILURE_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            INSTALL_FAILURE_CHANNEL_ID,
            "App update failures",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Shown when an in-app update fails to install"
        }
        manager.createNotificationChannel(channel)
    }

    private fun Context.canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return PermissionChecker.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PermissionChecker.PERMISSION_GRANTED
    }
}
