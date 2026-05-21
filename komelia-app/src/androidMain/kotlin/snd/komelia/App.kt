package snd.komelia

import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_LOW
import androidx.work.Configuration
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.LoggerContext
import snd.komelia.autobackup.AutobackupScheduler
import snd.komelia.autobackup.autobackupFailureChannelId
import snd.komelia.offline.sync.downloadChannelId
import snd.komelia.widget.WidgetRefresher
import snd.komelia.ui.DependencyContainer
import java.io.File
import java.util.concurrent.TimeUnit

val dependencies = MutableStateFlow<DependencyContainer?>(null)
class App : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        initLogging()
        GlobalExceptionHandler.initialize(applicationContext)
        saveLogcatSnapshot()
        setupNotificationChannels()
        initWorkManager()
        startAutobackupScheduler()
        startWidgetRefresher()
    }

    private fun startAutobackupScheduler() {
        appScope.launch {
            dependencies.filterNotNull().collectLatest { container ->
                AutobackupScheduler.observe(
                    context = applicationContext,
                    settings = container.appRepositories.settingsRepository,
                )
            }
        }
    }

    private fun startWidgetRefresher() {
        appScope.launch {
            dependencies.filterNotNull().collectLatest { container ->
                WidgetRefresher(
                    context = applicationContext,
                    events = container.bookCompletionEvents,
                ).start()
            }
        }
    }

    private fun initLogging() {
        val logDir = File(getExternalFilesDir(null), "komelia/logs")
        logDir.mkdirs()
        System.setProperty("LOG_DIR", logDir.absolutePath)       // before logback init
        val lc = LoggerFactory.getILoggerFactory() as LoggerContext
        lc.putProperty("LOG_DIR", logDir.absolutePath)           // belt-and-suspenders
    }

    private fun saveLogcatSnapshot() {
        val logDir = File(getExternalFilesDir(null), "komelia/logs")
        logDir.mkdirs()
        val outFile = File(logDir, "last_session_logcat.txt")
        try {
            val process = ProcessBuilder("logcat", "-d", "-t", "500", "-v", "threadtime", "*:D")
                .redirectErrorStream(true)
                .start()
            outFile.writeText(process.inputStream.bufferedReader().readText())
            process.waitFor(5, TimeUnit.SECONDS)
        } catch (_: Exception) {
            // best effort — non-fatal
        }
    }

    private fun setupNotificationChannels() {
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat
                    .Builder(downloadChannelId, IMPORTANCE_LOW)
                    .setName("downloads")
                    .setShowBadge(false)
                    .build(),
                NotificationChannelCompat
                    .Builder(autobackupFailureChannelId, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("Autobackup failures")
                    .setDescription("Shown when an automatic settings backup cannot be written.")
                    .setShowBadge(true)
                    .build()
            )
        )
    }

    private fun initWorkManager() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setWorkerFactory(MyWorkerFactory(dependencies.filterNotNull()))
            .setWorkerCoroutineContext(Dispatchers.IO)
            .build()
        WorkManager.initialize(this, config)
    }
}