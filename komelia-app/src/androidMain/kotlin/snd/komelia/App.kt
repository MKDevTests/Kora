package snd.komelia

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_LOW
import snd.komelia.ui.settings.toolkit.ToolkitFlowState
import snd.komelia.ui.settings.toolkit.ToolkitJobRunner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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
import io.github.snd_r.komelia.BuildConfig
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
        observeAppBackgroundForWidgetRefresh()
        observeToolkitCompletion()
    }

    /**
     * Posts a notification when a Komga Toolkit automation job finishes, so the
     * admin doesn't have to sit on the screen for the (multi-minute) run. The
     * result stays available on the screen; the notification just points there.
     */
    private fun observeToolkitCompletion() {
        appScope.launch {
            var lastNotified: ToolkitFlowState? = null
            ToolkitJobRunner.state.collect { s ->
                when (s) {
                    is ToolkitFlowState.Applied, is ToolkitFlowState.Failed ->
                        if (s !== lastNotified) { lastNotified = s; postToolkitNotification(s) }
                    else -> lastNotified = null
                }
            }
        }
    }

    private fun postToolkitNotification(state: ToolkitFlowState) {
        val nm = NotificationManagerCompat.from(this)
        if (!nm.areNotificationsEnabled()) return
        val (title, text) = when (state) {
            is ToolkitFlowState.Applied ->
                "Komga Toolkit — terminé" to "${state.function.label} · ${state.source.label} : résultat dans Kora."
            is ToolkitFlowState.Failed ->
                "Komga Toolkit — échec" to state.message
            else -> return
        }
        val openApp = Intent(this, MainActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pi = PendingIntent.getActivity(
            this, 0, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, toolkitChannelId)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { nm.notify(toolkitNotificationId, notification) }
    }

    /**
     * Refresh the "Next book up" widget whenever the whole app goes to
     * background (last activity stops, no successor within ~700ms).
     * Catches the common path where the user reads a few pages without
     * finishing a book — [snd.komelia.stats.BookCompletionEvents] wouldn't
     * fire, but onStop will, so the widget reflects the latest server
     * state by the time the user looks at the launcher.
     */
    private fun observeAppBackgroundForWidgetRefresh() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppForegroundState.setForeground(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                // Published before the widget refresh so the SSE grace period
                // starts counting from the moment the user actually left.
                AppForegroundState.setForeground(false)
                appScope.launch {
                    snd.komelia.widget.WidgetRefresher.refreshAll(applicationContext)
                }
            }
        })
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
        // Per-file rolling ceiling derived from the user's log-size cap. Read
        // synchronously from SharedPreferences here — before logback reads the
        // LOG_MAX_FILE_SIZE property during auto-configuration. Takes effect on
        // the next app start (logback config is fixed once initialized).
        val maxFileSize = LogSettings.perFileSize(LogSettings.getCapMb(applicationContext))
        // Quieter logs in release: DEBUG floods the rolling file (and the
        // Diagnostics log export) with internals. Debug builds keep DEBUG.
        val logLevel = if (BuildConfig.DEBUG) "DEBUG" else "INFO"
        System.setProperty("LOG_DIR", logDir.absolutePath)       // before logback init
        System.setProperty("LOG_MAX_FILE_SIZE", maxFileSize)     // before logback init
        System.setProperty("LOG_LEVEL", logLevel)                // before logback init
        val lc = LoggerFactory.getILoggerFactory() as LoggerContext
        lc.putProperty("LOG_DIR", logDir.absolutePath)           // belt-and-suspenders
        lc.putProperty("LOG_MAX_FILE_SIZE", maxFileSize)
        lc.putProperty("LOG_LEVEL", logLevel)
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
                    .build(),
                NotificationChannelCompat
                    .Builder(toolkitChannelId, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("Komga Toolkit")
                    .setDescription("Fin des tâches d'automatisation Komga Toolkit.")
                    .setShowBadge(true)
                    .build()
            )
        )
    }

    private val toolkitChannelId = "kora_toolkit"
    private val toolkitNotificationId = 4201

    private fun initWorkManager() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
            .setWorkerFactory(MyWorkerFactory(dependencies.filterNotNull()))
            .setWorkerCoroutineContext(Dispatchers.IO)
            .build()
        WorkManager.initialize(this, config)
    }
}