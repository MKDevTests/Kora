package snd.komelia

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent as AndroidKeyEvent
import android.webkit.WebView
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.window.layout.WindowMetricsCalculator
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import snd.komelia.ui.MainView
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass

import snd.komelia.session.DefaultServerSessionManager
import snd.komelia.ui.session.ServerSessionManager

private val initScope = CoroutineScope(Dispatchers.Default)
private val initMutex = Mutex()
private val mainActivity = MutableStateFlow<MainActivity?>(null)
private val sessionManager = MutableStateFlow<ServerSessionManager?>(null)
private val _incomingFileUriFlow = MutableSharedFlow<String>(replay = 1)
val incomingFileUriFlow: SharedFlow<String> = _incomingFileUriFlow.asSharedFlow()

class MainActivity : AppCompatActivity() {

    private val keyEvents = MutableSharedFlow<KeyEvent>(extraBufferCapacity = Int.MAX_VALUE)

    override fun dispatchKeyEvent(event: AndroidKeyEvent): Boolean {
        if (event.keyCode == AndroidKeyEvent.KEYCODE_VOLUME_UP ||
            event.keyCode == AndroidKeyEvent.KEYCODE_VOLUME_DOWN
        ) {
            keyEvents.tryEmit(KeyEvent(event as NativeKeyEvent))
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(null)
        WebView.setWebContentsDebuggingEnabled(false)
        FileKit.init(this)
        mainActivity.value = this

        initScope.launch {
            initMutex.withLock {
                if (sessionManager.value == null) {
                    LegacyDatabaseMigration(applicationContext.filesDir.absolutePath).runMigrationIfNeeded()
                    val manager = DefaultServerSessionManager(
                        globalDatabaseDir = applicationContext.filesDir.absolutePath,
                        appDatabaseDir = applicationContext.filesDir.absolutePath,
                        cacheDir = applicationContext.cacheDir.absolutePath,
                        appModuleFactory = { serverId ->
                            AndroidAppModule(
                                context = applicationContext,
                                mainActivity = mainActivity,
                                serverId = serverId
                            )
                        }
                    )
                    manager.loadLastActiveServer()
                    sessionManager.value = manager
                    manager.dependencies.collect { dependencies.value = it }
                }
            }
        }
        handleIntent(intent)

        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        // Ensure IME is shown on focus and content resizes around it.
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val windowSize = rememberWindowSize()
            val manager = sessionManager.collectAsState().value
            if (manager != null) {
                MainView(
                    dependencies = dependencies.collectAsState().value,
                    sessionManager = manager,
                    windowWidth = WindowSizeClass.fromDp(windowSize.width),
                    windowHeight = WindowSizeClass.fromDp(windowSize.height),
                    platformType = PlatformType.MOBILE,
                    keyEvents = keyEvents
                )
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.toString()?.let { _incomingFileUriFlow.tryEmit(it) }
        }
    }
}

@Composable
private fun Activity.rememberWindowSize(): DpSize {
    val configuration = LocalConfiguration.current
    val windowMetrics = remember(configuration) {
        WindowMetricsCalculator.getOrCreate()
            .computeCurrentWindowMetrics(this)
    }
    val windowDpSize = with(LocalDensity.current) {
        windowMetrics.bounds.toComposeRect().size.toDpSize()
    }
    return windowDpSize
}
