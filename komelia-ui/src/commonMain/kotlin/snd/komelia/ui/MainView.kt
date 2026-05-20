package snd.komelia.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.NavigatorDisposeBehavior
import com.dokar.sonner.ToastWidthPolicy
import com.dokar.sonner.Toaster
import com.dokar.sonner.listenMany
import com.dokar.sonner.rememberToasterState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.KomgaAuthenticationState
import snd.komelia.komga.api.LocalFileApiProvider
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.KomgaAuthenticationState.DataState.AuthenticationRequired
import snd.komelia.KomgaAuthenticationState.DataState.Loaded
import snd.komelia.ui.Theme.Companion.toTheme
import snd.komelia.ui.Theme.ThemeType
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.dialogs.update.UpdateDialog
import snd.komelia.ui.dialogs.update.UpdateProgressDialog
import snd.komelia.ui.komf.KomfMainScreen
import snd.komelia.ui.login.LoginScreen
import snd.komelia.ui.reader.readerScreen
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.ConfigurePlatformTheme
import snd.komelia.ui.platform.PlatformTitleBar
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.PlatformType.DESKTOP
import snd.komelia.ui.platform.PlatformType.MOBILE
import snd.komelia.ui.platform.PlatformType.WEB_KOMF
import snd.komelia.ui.platform.WindowSizeClass
import snd.komelia.ui.session.ServerSessionManager
import snd.komelia.updates.AppRelease
import snd.komelia.updates.StartupUpdateChecker

private val vmFactory = MutableStateFlow<ViewModelFactory?>(null)

@Composable
fun MainView(
    dependencies: DependencyContainer?,
    sessionManager: ServerSessionManager,
    windowWidth: WindowSizeClass,
    windowHeight: WindowSizeClass,
    platformType: PlatformType,
    keyEvents: SharedFlow<KeyEvent>
) {
    val currentServerProfile by sessionManager.currentServerProfile.collectAsState()
    var theme by rememberSaveable { mutableStateOf(Theme.DARK) }
    var navBarColor by remember { mutableStateOf<Color?>(null) }
    var accentColor by remember { mutableStateOf<Color?>(null) }
    var useNewLibraryUI by remember { mutableStateOf(true) }
    var cardLayoutBelow by remember { mutableStateOf(false) }
    var immersiveColorEnabled by remember { mutableStateOf(true) }
    var immersiveColorAlpha by remember { mutableStateOf(0.12f) }
    var showImmersiveNavBar by remember { mutableStateOf(false) }
    var useNewLibraryUI2 by remember { mutableStateOf(false) }
    var useImmersiveMorphingCover by remember { mutableStateOf(false) }
    var cardWidthScale by remember { mutableStateOf(1.0f) }
    var cardHeightScale by remember { mutableStateOf(1.0f) }
    var cardSpacingBelow by remember { mutableStateOf(0.0f) }
    var cardShadowLevel by remember { mutableStateOf(2.0f) }
    var cardCornerRadius by remember { mutableStateOf(8.0f) }
    var useFloatingNavigationBar by remember { mutableStateOf(false) }
    var hideParenthesesInNames by remember { mutableStateOf(false) }
    var lockScreenRotation by remember { mutableStateOf(false) }
    var cardLayoutOverlayBackground by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(dependencies) {
        dependencies?.appRepositories?.settingsRepository?.getAppTheme()?.collect { theme = it.toTheme() }
    }
    LaunchedEffect(dependencies) {
        dependencies?.appRepositories?.settingsRepository?.getLockScreenRotation()
            ?.collect { lockScreenRotation = it }
    }
    LaunchedEffect(dependencies) {
        dependencies?.appRepositories?.settingsRepository?.getNavBarColor()
            ?.collect { navBarColor = it?.let { v -> Color(v.toInt()) } }
    }
    LaunchedEffect(dependencies) {
        dependencies?.appRepositories?.settingsRepository?.getAccentColor()
            ?.collect { accentColor = it?.let { v -> Color(v.toInt()) } }
    }
    LaunchedEffect(dependencies) {
        dependencies?.appRepositories?.settingsRepository?.getUseNewLibraryUI()
            ?.collect { useNewLibraryUI = it }
    }
    LaunchedEffect(dependencies) {
        dependencies?.appRepositories?.settingsRepository?.getCardLayoutBelow()
            ?.collect { cardLayoutBelow = it }
    }
    LaunchedEffect(dependencies) {
        dependencies?.appRepositories?.settingsRepository?.getImmersiveColorEnabled()
            ?.collect { immersiveColorEnabled = it }
    }
    LaunchedEffect(dependencies) {
        dependencies?.appRepositories?.settingsRepository?.getImmersiveColorAlpha()
            ?.collect { immersiveColorAlpha = it }
    }
    LaunchedEffect(dependencies) {
        dependencies?.appRepositories?.settingsRepository?.getShowImmersiveNavBar()
            ?.collect { showImmersiveNavBar = it }
    }
    LaunchedEffect(dependencies) {
        dependencies?.appRepositories?.settingsRepository?.getHideParenthesesInNames()
            ?.collect { hideParenthesesInNames = it }
    }
    LaunchedEffect(dependencies) {
        dependencies?.appRepositories?.settingsRepository?.getCardLayoutOverlayBackground()
            ?.collect { cardLayoutOverlayBackground = it }
    }
    LaunchedEffect(dependencies) {
        dependencies?.appRepositories?.settingsRepository?.getUseNewLibraryUI2()
            ?.collect { useNewLibraryUI2 = it }
    }
    LaunchedEffect(dependencies) {
        dependencies?.appRepositories?.settingsRepository?.getUseImmersiveMorphingCover()
            ?.collect { useImmersiveMorphingCover = it }
    }
    LaunchedEffect(dependencies) {
        dependencies?.appRepositories?.settingsRepository?.getCardWidthScale()
            ?.collect { cardWidthScale = it }
    }
    LaunchedEffect(dependencies) {
        dependencies?.appRepositories?.settingsRepository?.getCardHeightScale()
            ?.collect { cardHeightScale = it }
    }
    LaunchedEffect(dependencies) {
        dependencies?.appRepositories?.settingsRepository?.getCardSpacingBelow()
            ?.collect { cardSpacingBelow = it }
    }
    LaunchedEffect(dependencies) {
        dependencies?.appRepositories?.settingsRepository?.getCardShadowLevel()
            ?.collect { cardShadowLevel = it }
    }
    LaunchedEffect(dependencies) {
        dependencies?.appRepositories?.settingsRepository?.getCardCornerRadius()
            ?.collect { cardCornerRadius = it }
    }
    LaunchedEffect(dependencies) {
        dependencies?.appRepositories?.settingsRepository?.getFloatingNavigationBar()
            ?.collect { useFloatingNavigationBar = it }
    }

    MaterialTheme(colorScheme = theme.colorScheme) {
        ConfigurePlatformTheme(theme)
        val focusManager = LocalFocusManager.current
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
        ) {
            if (dependencies == null) {
                Column {
                    PlatformTitleBar { }
                    LoadingMaxSizeIndicator()
                }
                return@Surface
            }

            val viewModelFactory = vmFactory.collectAsState().value
            LaunchedEffect(dependencies) {
                if (dependencies != null) {
                    vmFactory.value = ViewModelFactory(dependencies, platformType, sessionManager)
                }
            }

            if (viewModelFactory == null) return@Surface

            val notificationToaster = rememberToasterState()

            snd.komelia.ui.platform.LockScreenOrientation(lockScreenRotation)

            CompositionLocalProvider(
                LocalViewModelFactory provides viewModelFactory,
                LocalToaster provides notificationToaster,
                LocalSeriesRatingsRepository provides dependencies.appRepositories.seriesRatingsRepository,
                LocalKomgaEvents provides dependencies.komgaEvents.events,
                LocalKomfIntegration provides dependencies.appRepositories.komfSettingsRepository.getKomfEnabled(),
                LocalKeyEvents provides keyEvents,
                LocalPlatform provides platformType,
                LocalTheme provides theme,
                LocalWindowState provides dependencies.windowState,
                LocalWindowWidth provides windowWidth,
                LocalWindowHeight provides windowHeight,
                LocalLibraries provides dependencies.komgaSharedState.libraries,
                LocalReloadEvents provides viewModelFactory.screenReloadEvents,
                LocalBookDownloadEvents provides dependencies.offlineDependencies.bookDownloadEvents,
                LocalOfflineMode provides dependencies.isOffline,
                LocalKomgaState provides dependencies.komgaSharedState,
                LocalNavBarColor provides navBarColor,
                LocalAccentColor provides accentColor,
                LocalUseNewLibraryUI provides useNewLibraryUI,
                LocalCardLayoutBelow provides cardLayoutBelow,
                LocalImmersiveColorEnabled provides immersiveColorEnabled,
                LocalImmersiveColorAlpha provides immersiveColorAlpha,
                LocalShowImmersiveNavBar provides showImmersiveNavBar,
                LocalHideParenthesesInNames provides hideParenthesesInNames,
                LocalLockScreenRotation provides lockScreenRotation,
                LocalOnLockScreenRotationChange provides { newRotation ->
                    coroutineScope.launch {
                        dependencies.appRepositories.settingsRepository.putLockScreenRotation(newRotation)
                    }
                },
                LocalCardLayoutOverlayBackground provides cardLayoutOverlayBackground,
                LocalUseNewLibraryUI2 provides useNewLibraryUI2,
                LocalUseImmersiveMorphingCover provides useImmersiveMorphingCover,
                LocalToggleImmersiveMorphingCover provides {
                    coroutineScope.launch {
                        dependencies.appRepositories.settingsRepository.putUseImmersiveMorphingCover(!useImmersiveMorphingCover)
                    }
                },
                LocalCardWidthScale provides cardWidthScale,
                LocalCardHeightScale provides cardHeightScale,
                LocalCardSpacingBelow provides cardSpacingBelow,
                LocalCardShadowLevel provides cardShadowLevel,
                LocalCardCornerRadius provides cardCornerRadius,
                LocalUseFloatingNavigationBar provides useFloatingNavigationBar,
            ) {
                key(currentServerProfile?.id) {
                    MainContent(platformType, dependencies.komgaSharedState, dependencies.localFileApiProvider)
                }

                AppNotifications(dependencies.appNotifications, theme)
                val updateChecker = remember { viewModelFactory.getStartupUpdateChecker() }
                if (updateChecker != null) {
                    StartupUpdateChecker(updateChecker)
                }
            }

            BackPressHandler {}
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MainContent(
    platformType: PlatformType,
    komgaSharedState: KomgaAuthenticationState,
    localFileApiProvider: LocalFileApiProvider? = null,
) {
    val loginScreen = remember(platformType) {
        when (platformType) {
            MOBILE, DESKTOP -> LoginScreen()
            WEB_KOMF -> KomfMainScreen()
        }
    }

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            Navigator(
                screen = loginScreen,
                disposeBehavior = NavigatorDisposeBehavior(disposeNestedNavigators = false),
                onBackPressed = null
            ) { navigator ->
                var canProceed by remember { mutableStateOf(komgaSharedState.authenticationState.value == Loaded) }
                // FIXME this looks like a hack. Find a multiplatform way to handle this outside of composition?
                // variable to track if Android app was killed in background and later restored
                var wasInitializedBefore by rememberSaveable { mutableStateOf(false) }
                navigator.clearEvent()

                LaunchedEffect(Unit) {
                    if (canProceed) return@LaunchedEffect

                    // not really necessary since Voyager navigator doesn't dispose existing MainScreen when it's replaced with LoginScreen
                    // when LoginScreen replaces itself back to MainScreen, it's restored to old state
                    // not sure if it's intended, do proper initialization here to avoid loading LoginScreen
                    if (wasInitializedBefore) {
                        komgaSharedState.tryReloadState()
                    }

                    val currentState = komgaSharedState.authenticationState.value
                    when (currentState) {
                        AuthenticationRequired -> navigator.replaceAll(loginScreen)
                        Loaded -> {}
                    }
                    canProceed = true

                    komgaSharedState.authenticationState.collect {
                        wasInitializedBefore = when (it) {
                            AuthenticationRequired -> false
                            Loaded -> true
                        }
                    }
                }

                LaunchedEffect(localFileApiProvider) {
                    localFileApiProvider?.processedBooksFlow?.collect { book ->
                        snapshotFlow { canProceed }.filter { it }.first()
                        navigator.popUntilRoot()
                        navigator.push(
                            readerScreen(
                                book = book,
                                markReadProgress = true,
                                bookSiblingsContext = BookSiblingsContext.Series(),
                            )
                        )
                    }
                }

                if (canProceed) {
                    CurrentScreen()
                }
            }
        }
    }
}


@Composable
fun AppNotifications(
    appNotifications: AppNotifications,
    theme: Theme,
    showCloseButton: Boolean = true,
) {
    val toaster =
        rememberToasterState(onToastDismissed = { appNotifications.remove(it.id as Long) })

    LaunchedEffect(toaster) {
        val toastsFlow = appNotifications.getNotifications()
            .map { notifications -> notifications.map { it.toToast() } }
        toaster.listenMany(toastsFlow)
    }

    Toaster(
        state = toaster,
        richColors = true,
        darkTheme = theme.type == ThemeType.DARK,
        showCloseButton = showCloseButton,
        widthPolicy = { ToastWidthPolicy(max = 500.dp) },
        actionSlot = { toast ->
            when (toast.action) {
                null -> {}
                else -> {}
            }
        },
        //FIXME: on Android API 35 popup is shown under nav bar due to enforced edge to edge mode.
        // WindowInsets doesn't seem to provide any values inside dialogs
        // add offset from main window as workaround, as a side effect, on lower apis it's drawn higher than usual
        // there's no simple way to enable edge to edge in compose dialogs on lower apis
        offset = IntOffset(0, -WindowInsets.navigationBars.getBottom(LocalDensity.current))
    )
}


@Composable
private fun StartupUpdateChecker(updater: StartupUpdateChecker) {
    val coroutineScope = rememberCoroutineScope()
    var newRelease by remember { mutableStateOf<AppRelease?>(null) }
    LaunchedEffect(Unit) { updater.checkForUpdates()?.let { newRelease = it } }

    val progress = updater.downloadProgress.collectAsState().value
    val release = newRelease
    if (release != null) {
        UpdateDialog(
            newRelease = release,
            onConfirm = {
                coroutineScope.launch {
                    updater.onUpdate(release)
                    newRelease = null
                }
            },
            onDismiss = {
                coroutineScope.launch { updater.onUpdateDismiss(release) }
                newRelease = null
            }
        )
    }
    if (progress != null) {
        UpdateProgressDialog(
            totalSize = progress.total,
            downloadedSize = progress.completed,
            onCancel = updater::onUpdateCancel
        )
    }
}