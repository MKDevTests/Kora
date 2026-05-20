package snd.komelia.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToasterState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import snd.komelia.AppWindowState
import snd.komelia.KomgaAuthenticationState
import snd.komelia.offline.sync.model.DownloadEvent
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass
import snd.komelia.ui.strings.EnStrings
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.sse.KomgaEvent

val LocalViewModelFactory = compositionLocalOf<ViewModelFactory> { error("ViewModel factory is not set") }
val LocalMainScreenViewModel = compositionLocalOf<MainScreenViewModel> { error("MainScreenViewModel is not set") }

val LocalToaster = compositionLocalOf<ToasterState> { error("Toaster is not set") }
val LocalKomgaEvents = compositionLocalOf<SharedFlow<KomgaEvent>> { error("Komga events are not set") }
val LocalKomfIntegration = compositionLocalOf { flowOf(false) }
val LocalKeyEvents = compositionLocalOf<SharedFlow<KeyEvent>> { error("Key events are not set") }
val LocalWindowWidth = compositionLocalOf<WindowSizeClass> { error("Window size is not set") }
val LocalWindowHeight = compositionLocalOf<WindowSizeClass> { error("Window size is not set") }
val LocalStrings = staticCompositionLocalOf { EnStrings }
val LocalPlatform = compositionLocalOf<PlatformType> { error("Platform type is not set") }
val LocalTheme = compositionLocalOf { Theme.DARK }
val LocalWindowState = compositionLocalOf<AppWindowState> { error("Window state was not initialized") }
val LocalLibraries = compositionLocalOf<StateFlow<List<KomgaLibrary>>> { error("Libraries were not initialized") }
val LocalReloadEvents = staticCompositionLocalOf<SharedFlow<Unit>> { error("Reload event flow was not initialized") }
val LocalBookDownloadEvents =
    staticCompositionLocalOf<SharedFlow<DownloadEvent>?> { error("Book download event flow was not initialized") }
val LocalOfflineMode = staticCompositionLocalOf<StateFlow<Boolean>> { error("offline mode flow was not initialized") }
val LocalKomgaState = staticCompositionLocalOf<KomgaAuthenticationState> { error("komga state was not initialized") }
val LocalNavBarColor = compositionLocalOf<Color?> { null }
val LocalAccentColor = compositionLocalOf<Color?> { null }
val LocalUseNewLibraryUI = compositionLocalOf { true }
val LocalCardLayoutBelow = compositionLocalOf { false }
val LocalImmersiveColorEnabled = compositionLocalOf { true }
val LocalImmersiveColorAlpha = compositionLocalOf { 0.12f }
val LocalShowImmersiveNavBar = compositionLocalOf { false }
val LocalUseNewLibraryUI2 = compositionLocalOf { false }
val LocalUseFloatingNavigationBar = compositionLocalOf { false }
val LocalUseImmersiveMorphingCover = compositionLocalOf { false }
val LocalToggleImmersiveMorphingCover = staticCompositionLocalOf<() -> Unit> { {} }
val LocalCardWidthScale = compositionLocalOf { 1.0f }
val LocalCardHeightScale = compositionLocalOf { 1.0f }
val LocalCardSpacingBelow = compositionLocalOf { 0.0f }
val LocalCardShadowLevel = compositionLocalOf { 2.0f }
val LocalCardCornerRadius = compositionLocalOf { 8.0f }
val LocalHideParenthesesInNames = compositionLocalOf { false }
val LocalCardLayoutOverlayBackground = compositionLocalOf { true }
val LocalLockScreenRotation = compositionLocalOf { false }
val LocalOnLockScreenRotationChange = staticCompositionLocalOf<(Boolean) -> Unit> { {} }
val LocalRawStatusBarHeight = staticCompositionLocalOf { 0.dp }
val LocalRawNavBarHeight = staticCompositionLocalOf { 0.dp }
val LocalFloatingActionButton = staticCompositionLocalOf<MutableState<Pair<Any, @Composable () -> Unit>?>> { error("Not provided") }
val LocalFloatingActionButtonLeft = staticCompositionLocalOf<MutableState<Pair<Any, @Composable () -> Unit>?>> { error("Not provided") }
/**
 * Additional FAB slot rendered to the *right* of [LocalFloatingActionButton]
 * in the floating-nav layout. Used by the Continue-reading FAB so it
 * sits on the same horizontal line as the nav island and the
 * Edit/filter button, never above them. Order across the bottom row:
 *   [fab-left] [nav-island] [fab] [fab-far-right]
 */
val LocalFloatingActionButtonFarRight = staticCompositionLocalOf<MutableState<Pair<Any, @Composable () -> Unit>?>> { error("Not provided") }
// When transparent bars mode is active and content extends behind the nav bar,
// scrollable content should add this as bottom padding so items remain reachable.
val LocalTransparentNavBarPadding = compositionLocalOf { 0.dp }
// When a floating toolbar overlays scroll content (e.g. LibraryScreen in modern themes),
// scrollable grids should add this as top contentPadding so items start below the toolbar.
val LocalFloatingToolbarPadding = compositionLocalOf { 0.dp }
val LocalHazeState = compositionLocalOf<HazeState?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }
