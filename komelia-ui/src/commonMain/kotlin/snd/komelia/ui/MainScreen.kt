package snd.komelia.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DrawerValue.Closed
import androidx.compose.material3.DrawerValue.Open
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Bookmark
import snd.komelia.ui.favorites.FavoritesScreen
import snd.komelia.ui.planned.PlannedScreen
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyUp
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import snd.komelia.ui.book.BookScreen
import snd.komelia.ui.book.bookScreen
import snd.komelia.ui.home.HomeScreen
import snd.komelia.ui.library.LibraryScreen
import snd.komelia.ui.reader.ReaderExitDestination
import snd.komelia.ui.reader.ReaderNavigationIntent
import snd.komelia.ui.oneshot.OneshotScreen
import snd.komelia.ui.platform.PlatformType.DESKTOP
import snd.komelia.ui.platform.PlatformType.MOBILE
import snd.komelia.ui.platform.PlatformType.WEB_KOMF
import snd.komelia.ui.platform.WindowSizeClass
import snd.komelia.ui.platform.WindowSizeClass.FULL
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.ui.search.SearchScreen
import snd.komelia.ui.series.SeriesScreen
import snd.komelia.ui.series.seriesScreen
import snd.komelia.ui.settings.MobileSettingsScreen
import snd.komelia.ui.settings.SettingsScreen
import snd.komelia.ui.topbar.AppBar
import snd.komelia.ui.topbar.LibrariesNavBarContent
import snd.komelia.ui.topbar.NavBarContent
import snd.komelia.ui.LocalSharedTransitionScope

class MainScreen(
    private val defaultScreen: Screen = HomeScreen()
) : Screen {

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val platform = LocalPlatform.current

        Navigator(
            screen = defaultScreen,
            onBackPressed = null,
        ) { navigator ->

            val vm = rememberScreenModel { viewModelFactory.getNavigationViewModel() }
            CompositionLocalProvider(LocalMainScreenViewModel provides vm) {
                when (platform) {
                    MOBILE -> MobileLayout(navigator, vm)
                    DESKTOP, WEB_KOMF -> DesktopLayout(navigator, vm)
                }

                // "What's new" modal — overlays on top of the current
                // layout. The VM populates `releaseNotesToShow` lazily on
                // first launch after an upgrade (see init { } there). If
                // we're on the same version as last-seen, or GitHub
                // couldn't be reached, this stays null and nothing renders.
                val releaseNotes = vm.releaseNotesToShow.collectAsState().value
                if (releaseNotes != null) {
                    snd.komelia.ui.dialogs.release.ReleaseNotesDialog(
                        release = releaseNotes,
                        onDismiss = { vm.dismissReleaseNotes(save = true) },
                    )
                }
            }
            LaunchedEffect(Unit) {
                vm.initialize(navigator)
                // Respect the user's startup-screen preference. Default
                // (Home) is already on the navigator stack, so we only
                // intercept the "Last library" case. There may be a one-
                // frame flash of Home before replaceAll runs — acceptable
                // for v1, this only happens at cold start.
                val pref = vm.startupScreen.value
                if (pref == snd.komelia.settings.model.StartupScreen.LAST_LIBRARY) {
                    val lastLibId = vm.lastSelectedLibraryId.value
                    if (navigator.lastItem is HomeScreen) {
                        navigator.replaceAll(snd.komelia.ui.library.LibraryScreen(lastLibId))
                    }
                }
            }

            val keyEvents: SharedFlow<KeyEvent> = LocalKeyEvents.current
            LaunchedEffect(Unit) {
                keyEvents.collect { event ->
                    if (event.type == KeyUp && event.key == Key.DirectionLeft && event.isAltPressed) {
                        navigator.pop()
                    }

                }
            }

            // Consume return-nav intents posted by the image reader. The reader lives
            // on the parent navigator (above MainScreen), so it can't push series/library
            // screens itself without losing MainScreen's CompositionLocals (FAB, etc.).
            // It posts an intent + pops itself; we handle the push here on the inner nav.
            LaunchedEffect(Unit) {
                ReaderNavigationIntent.pending.collect { intent ->
                    // Build the destination, then avoid creating a DUPLICATE
                    // navigator key. The reader lives on the parent navigator and
                    // pops itself; we navigate on the inner nav here. When the user
                    // opened the reader FROM this series (or via series -> book), a
                    // screen with the same key is already on the inner stack, and
                    // pushing it again throws "Key <id>:screen was used multiple
                    // times" (SaveableStateHolder, surfaced by the AnimatedContent
                    // cross-fade). popUntil reuses the existing instance; we only
                    // push when the destination is genuinely not on the stack.
                    // Home is the root of this navigator, not something to push:
                    // replacing the stack is what the navigation bar's own home
                    // button does, and it leaves no series/library behind to back
                    // into. Handled here rather than below, which is push-shaped.
                    if (intent is ReaderExitDestination.Home) {
                        ReaderNavigationIntent.pending.value = null
                        if (navigator.lastItem !is HomeScreen) navigator.replaceAll(HomeScreen())
                        return@collect
                    }
                    val destination = when (intent) {
                        is ReaderExitDestination.Series -> SeriesScreen(intent.id)
                        is ReaderExitDestination.Library -> LibraryScreen(intent.id)
                        ReaderExitDestination.Home -> null
                        null -> null
                    }
                    if (destination != null) {
                        ReaderNavigationIntent.pending.value = null
                        if (navigator.items.any { it.key == destination.key }) {
                            navigator.popUntil { it.key == destination.key }
                        } else {
                            navigator.push(destination)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DesktopLayout(
        navigator: Navigator,
        vm: MainScreenViewModel
    ) {
        val width = LocalWindowWidth.current
        LaunchedEffect(width) {
            when (width) {
                FULL -> vm.navBarState.snapTo(Open)
                else -> vm.navBarState.snapTo(Closed)
            }
        }
        Column {
            AppBar(
                onMenuButtonPress = { vm.toggleNavBar() },
                query = vm.searchBarState.currentQuery(),
                onQueryChange = vm.searchBarState::onQueryChange,
                isLoading = vm.searchBarState.isLoading,
                onSearchAllClick = {
                    if (navigator.lastItem is SearchScreen) navigator.replace(SearchScreen(it))
                    else navigator.push(SearchScreen(it))
                },
                searchResults = vm.searchBarState.searchResults(),
                libraryById = vm.searchBarState::getLibraryById,
                onBookClick = { navigator.replaceAll(bookScreen(it)) },
                onSeriesClick = {
                    navigator.replaceAll(seriesScreen(it))
                },
                onRefreshClick = vm::onScreenReload,
                notificationsState = vm.notificationsState,
                isOffline = vm.isOffline.collectAsState().value,
                onOfflineModeChange = vm::goOnline
            )

            when (width) {
                FULL -> Row {
                    if (vm.navBarState.targetValue == Open) NavBar(vm, navigator, width)
                    CurrentScreen()
                }

                else -> ModalNavigationDrawer(
                    drawerState = vm.navBarState,
                    drawerContent = { NavBar(vm, navigator, width) },
                    content = { CurrentScreen() }
                )
            }
        }
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    private fun MobileLayout(
        navigator: Navigator,
        vm: MainScreenViewModel
    ) {
        val useNewLibraryUI = LocalUseNewLibraryUI.current
        val useNewLibraryUI2 = LocalUseNewLibraryUI2.current
        val useFloatingNavigationBar = LocalUseFloatingNavigationBar.current
        val isImmersiveScreen = navigator.lastItem is SeriesScreen ||
                navigator.lastItem is BookScreen ||
                navigator.lastItem is OneshotScreen
        val showImmersiveNavBar = LocalShowImmersiveNavBar.current
        val rawStatusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val rawNavBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val theme = LocalTheme.current
        val transparentBars = useNewLibraryUI && theme.transparentBars
        val useNewTopBar = useNewLibraryUI2 && useNewLibraryUI
        val hazeState = if (transparentBars || useNewTopBar) rememberHazeState() else null
        val floatingActionButton = remember { mutableStateOf<Pair<Any, @Composable () -> Unit>?>(null) }
        val floatingActionButtonLeft = remember { mutableStateOf<Pair<Any, @Composable () -> Unit>?>(null) }
        val floatingActionButtonFarRight = remember { mutableStateOf<Pair<Any, @Composable () -> Unit>?>(null) }
        CompositionLocalProvider(
            LocalRawStatusBarHeight provides rawStatusBarHeight,
            LocalRawNavBarHeight provides rawNavBarHeight,
            LocalHazeState provides hazeState,
            LocalFloatingActionButton provides floatingActionButton,
            LocalFloatingActionButtonLeft provides floatingActionButtonLeft,
            LocalFloatingActionButtonFarRight provides floatingActionButtonFarRight,
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                bottomBar = {
                    if (!useFloatingNavigationBar && (!isImmersiveScreen || showImmersiveNavBar)) {
                        if (useNewLibraryUI) {
                            AppNavigationBar(
                                navigator = navigator,
                                vm = vm,
                                containerColor = if (theme.transparentBars)
                                    theme.navBarContainerColor
                                else
                                    LocalNavBarColor.current ?: MaterialTheme.colorScheme.surfaceVariant
                            )
                        } else {
                            StandardBottomNavigationBar(
                                navigator = navigator,
                                vm = vm,
                                modifier = Modifier
                            )
                        }
                    }
                }
            ) { paddingValues ->
                val layoutDirection = LocalLayoutDirection.current
                val bottomPadding = if (transparentBars || useFloatingNavigationBar) 0.dp else paddingValues.calculateBottomPadding()
                val isModernNewTopBar = useNewTopBar && theme.transparentBars
                val topPadding = if (isModernNewTopBar) 0.dp else paddingValues.calculateTopPadding()
                CompositionLocalProvider(
                    LocalTransparentNavBarPadding provides if (transparentBars || useFloatingNavigationBar) paddingValues.calculateBottomPadding() + rawNavBarHeight + (if (useFloatingNavigationBar) 80.dp else 0.dp) else 0.dp,
                ) {
                    ModalNavigationDrawer(
                        drawerState = vm.navBarState,
                        drawerContent = {
                            LibrariesNavBar(
                                modifier = Modifier.padding(
                                    start = paddingValues.calculateStartPadding(layoutDirection),
                                    end = paddingValues.calculateEndPadding(layoutDirection),
                                    top = paddingValues.calculateTopPadding(),
                                    bottom = paddingValues.calculateBottomPadding(),
                                ).consumeWindowInsets(paddingValues),
                                vm = vm,
                                navigator = navigator
                            )
                        },
                        content = {
                            Box(Modifier.fillMaxSize()) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(
                                            start = paddingValues.calculateStartPadding(layoutDirection),
                                            end = paddingValues.calculateEndPadding(layoutDirection),
                                            top = topPadding,
                                            bottom = bottomPadding,
                                        )
                                        .then(
                                            if (isModernNewTopBar)
                                                Modifier.consumeWindowInsets(PaddingValues(bottom = paddingValues.calculateBottomPadding()))
                                            else
                                                Modifier.consumeWindowInsets(paddingValues)
                                        )
                                        .then(if (!isModernNewTopBar) Modifier.statusBarsPadding() else Modifier)
                                        .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
                                ) {
                                    SharedTransitionLayout {
                                        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                                            AnimatedContent(
                                                targetState = navigator.lastItem,
                                                transitionSpec = {
                                                    val isToImmersive = targetState is BookScreen || targetState is SeriesScreen || targetState is OneshotScreen
                                                    val isFromImmersive = initialState is BookScreen || initialState is SeriesScreen || initialState is OneshotScreen
                                                    when {
                                                        isToImmersive   -> EnterTransition.None togetherWith fadeOut(tween(200))
                                                        isFromImmersive -> fadeIn(tween(200)) togetherWith fadeOut(tween(450))
                                                        else            -> fadeIn(tween(400)) togetherWith fadeOut(tween(250))
                                                    }
                                                },
                                                label = "nav",
                                            ) { screen ->
                                                CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                                    navigator.saveableState("screen", screen) {
                                                        screen.Content()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (useFloatingNavigationBar && (!isImmersiveScreen || showImmersiveNavBar)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                                            .navigationBarsPadding()
                                    ) {
                                        androidx.compose.ui.layout.Layout(
                                            content = {
                                                Box(Modifier.layoutId("nav")) {
                                                    FloatingNavigationBar(
                                                        navigator = navigator,
                                                        vm = vm,
                                                        containerColor = if (theme.transparentBars)
                                                            theme.navBarContainerColor
                                                        else
                                                            LocalNavBarColor.current ?: MaterialTheme.colorScheme.surfaceVariant
                                                    )
                                                }
                                                val fab = LocalFloatingActionButton.current.value
                                                if (fab != null) {
                                                    Box(Modifier.layoutId("fab")) { fab.second() }
                                                }
                                                val fabLeft = LocalFloatingActionButtonLeft.current.value
                                                if (fabLeft != null) {
                                                    Box(Modifier.layoutId("fab-left")) { fabLeft.second() }
                                                }
                                                // The far-right slot is exclusively the root-screen
                                                // "Continue reading" FAB (Home / Library). An immersive
                                                // detail has its own read/continue FAB in the `fab` slot,
                                                // so suppress the far-right one there — otherwise the
                                                // leaving root screen's still-registered FAB lingers next
                                                // to the detail's during the transition (two book FABs).
                                                val fabFarRight = LocalFloatingActionButtonFarRight.current.value
                                                if (fabFarRight != null && !isImmersiveScreen) {
                                                    Box(Modifier.layoutId("fab-far-right")) { fabFarRight.second() }
                                                }
                                            }
                                        ) { measurables, constraints ->
                                            val loose = constraints.copy(minWidth = 0)
                                            val navPlaceable = measurables.first { it.layoutId == "nav" }.measure(loose)
                                            val fabPlaceable = measurables.firstOrNull { it.layoutId == "fab" }?.measure(loose)
                                            val fabLeftPlaceable = measurables.firstOrNull { it.layoutId == "fab-left" }?.measure(loose)
                                            val fabFarRightPlaceable = measurables.firstOrNull { it.layoutId == "fab-far-right" }?.measure(loose)

                                            val width = constraints.maxWidth
                                            val spacing = 8.dp.roundToPx()

                                            val leftExtra = fabLeftPlaceable?.let { it.width + spacing } ?: 0
                                            val rightClusterExtra = (fabPlaceable?.let { it.width + spacing } ?: 0) +
                                                (fabFarRightPlaceable?.let { it.width + spacing } ?: 0)

                                            // On a phone the island alone is ~316dp (six destinations) and
                                            // two FABs add 128dp: the row cannot fit, and the previous
                                            // shift-left/shift-right corrections fought each other and
                                            // pushed the right-hand FABs past the screen edge — the sort
                                            // and continue-reading buttons simply disappeared.
                                            //
                                            // When the single row does not fit, the FABs move to their own
                                            // row above the island instead of being squeezed next to it.
                                            val fabRowHeight = maxOf(
                                                fabPlaceable?.height ?: 0,
                                                fabLeftPlaceable?.height ?: 0,
                                                fabFarRightPlaceable?.height ?: 0,
                                            )
                                            val stacked = fabRowHeight > 0 &&
                                                leftExtra + navPlaceable.width + rightClusterExtra > width

                                            val height = if (stacked) navPlaceable.height + spacing + fabRowHeight
                                            else maxOf(navPlaceable.height, fabRowHeight)

                                            layout(width, height) {
                                                if (stacked) {
                                                    // FAB row on top, right-aligned, in reading order
                                                    // left / filter / continue-reading.
                                                    var x = width
                                                    listOfNotNull(
                                                        fabFarRightPlaceable,
                                                        fabPlaceable,
                                                        fabLeftPlaceable,
                                                    ).forEach { placeable ->
                                                        x -= placeable.width
                                                        placeable.placeRelative(x, (fabRowHeight - placeable.height) / 2)
                                                        x -= spacing
                                                    }

                                                    navPlaceable.placeRelative(
                                                        ((width - navPlaceable.width) / 2).coerceAtLeast(0),
                                                        fabRowHeight + spacing,
                                                    )
                                                    return@layout
                                                }

                                                // Start with nav centered
                                                var navX = (width - navPlaceable.width) / 2
                                                val navY = (height - navPlaceable.height) / 2

                                                // Shift nav left if the whole right cluster would overflow
                                                if (rightClusterExtra > 0) {
                                                    val clusterRightEdge = navX + navPlaceable.width + rightClusterExtra
                                                    if (clusterRightEdge > width) {
                                                        navX = (navX - (clusterRightEdge - width)).coerceAtLeast(0)
                                                    }
                                                }

                                                // Left FAB: shift nav right if it would overflow the left edge
                                                if (fabLeftPlaceable != null) {
                                                    val leftFabRightEdge = navX - spacing
                                                    if (leftFabRightEdge < fabLeftPlaceable.width) {
                                                        navX = (navX + (fabLeftPlaceable.width - leftFabRightEdge)).coerceAtMost(width - navPlaceable.width)
                                                    }
                                                }

                                                navPlaceable.placeRelative(navX, navY)

                                                // Place fab to the right of nav; far-right to the right of fab.
                                                var nextX = navX + navPlaceable.width + spacing
                                                if (fabPlaceable != null) {
                                                    fabPlaceable.placeRelative(nextX, (height - fabPlaceable.height) / 2)
                                                    nextX += fabPlaceable.width + spacing
                                                }
                                                if (fabFarRightPlaceable != null) {
                                                    fabFarRightPlaceable.placeRelative(nextX, (height - fabFarRightPlaceable.height) / 2)
                                                }

                                                if (fabLeftPlaceable != null) {
                                                    val finalLeftFabX = navX - spacing - fabLeftPlaceable.width
                                                    fabLeftPlaceable.placeRelative(finalLeftFabX.coerceAtLeast(0), (height - fabLeftPlaceable.height) / 2)
                                                }
                                            }
                                        }
                                    }
                                }

                            }
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun FloatingNavigationBar(
        navigator: Navigator,
        vm: MainScreenViewModel,
        containerColor: Color = LocalNavBarColor.current ?: MaterialTheme.colorScheme.surfaceVariant,
    ) {
        val accentColor = LocalAccentColor.current
        val hazeState = LocalHazeState.current
        val theme = LocalTheme.current
        val useHaze = hazeState != null && theme.transparentBars
        val hazeStyle = if (useHaze) HazeMaterials.regular(containerColor) else null

        Surface(
            color = if (useHaze) Color.Transparent else containerColor,
            shape = CircleShape,
            tonalElevation = 3.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            modifier = Modifier
                .height(56.dp)
                .wrapContentWidth()
                .clip(CircleShape)
                .then(
                    if (useHaze && hazeStyle != null)
                        Modifier.hazeEffect(hazeState!!) { style = hazeStyle }
                    else Modifier
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    val showSwitcher = remember { mutableStateOf(false) }
                    val libraries = vm.libraries.collectAsState().value
                    val haptics = LocalHapticFeedback.current
                    val currentLibraryId = (navigator.lastItem as? LibraryScreen)?.libraryId
                    FloatingToolbarButton(
                        icon = Icons.Rounded.LocalLibrary,
                        onClick = vm::navigateToLibrary,
                        isSelected = navigator.lastItem is LibraryScreen,
                        accentColor = accentColor,
                        // Long-press opens a quick switcher: Favorites + every library.
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showSwitcher.value = true
                        },
                    )
                    DropdownMenu(
                        expanded = showSwitcher.value,
                        onDismissRequest = { showSwitcher.value = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(LocalStrings.current.ui.favoris) },
                            onClick = {
                                showSwitcher.value = false
                                if (navigator.lastItem !is FavoritesScreen) navigator.push(FavoritesScreen())
                            },
                            leadingIcon = { Icon(Icons.Rounded.Star, null) },
                        )
                        DropdownMenuItem(
                            text = { Text(LocalStrings.current.ui.lire) },
                            onClick = {
                                showSwitcher.value = false
                                if (navigator.lastItem !is PlannedScreen) navigator.push(PlannedScreen())
                            },
                            leadingIcon = { Icon(Icons.Rounded.Bookmark, null) },
                        )
                        if (libraries.isNotEmpty()) HorizontalDivider()
                        libraries.forEach { lib ->
                            DropdownMenuItem(
                                text = { Text(lib.name) },
                                onClick = {
                                    showSwitcher.value = false
                                    vm.navigateToLibrary(lib.id)
                                },
                                leadingIcon = {
                                    if (currentLibraryId == lib.id) Icon(Icons.Filled.Check, null)
                                    else Spacer(Modifier.width(24.dp))
                                },
                            )
                        }
                    }
                }
                FloatingToolbarButton(
                    icon = Icons.Rounded.Home,
                    onClick = { if (navigator.lastItem !is HomeScreen) navigator.replaceAll(HomeScreen()) },
                    isSelected = navigator.lastItem is HomeScreen,
                    accentColor = accentColor
                )
                FloatingToolbarButton(
                    icon = Icons.Rounded.Search,
                    onClick = { if (navigator.lastItem !is SearchScreen) navigator.push(SearchScreen(null)) },
                    isSelected = navigator.lastItem is SearchScreen,
                    accentColor = accentColor
                )
                if (vm.showStatsInBottomNav.collectAsState().value) {
                    FloatingToolbarButton(
                        icon = Icons.Rounded.BarChart,
                        onClick = {
                            if (navigator.lastItem !is snd.komelia.ui.stats.ReadingStatsScreen)
                                navigator.push(snd.komelia.ui.stats.ReadingStatsScreen())
                        },
                        isSelected = navigator.lastItem is snd.komelia.ui.stats.ReadingStatsScreen,
                        accentColor = accentColor
                    )
                }
                if (vm.showNextReleasesInBottomNav.collectAsState().value) {
                    FloatingToolbarButton(
                        icon = Icons.Rounded.Event,
                        onClick = {
                            if (navigator.lastItem !is snd.komelia.ui.nextreleases.NextReleasesScreen)
                                navigator.push(snd.komelia.ui.nextreleases.NextReleasesScreen())
                        },
                        isSelected = navigator.lastItem is snd.komelia.ui.nextreleases.NextReleasesScreen,
                        accentColor = accentColor
                    )
                }
                FloatingToolbarButton(
                    icon = Icons.Rounded.Settings,
                    onClick = {
                        if (navigator.lastItem !is MobileSettingsScreen && navigator.lastItem !is SettingsScreen)
                            navigator.push(MobileSettingsScreen())
                    },
                    isSelected = navigator.lastItem is MobileSettingsScreen || navigator.lastItem is SettingsScreen,
                    accentColor = accentColor
                )
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun FloatingToolbarButton(
        icon: ImageVector,
        onClick: () -> Unit,
        isSelected: Boolean,
        accentColor: Color?,
        onLongClick: (() -> Unit)? = null,
    ) {
        val tint = if (isSelected) accentColor ?: MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
        if (onLongClick == null) {
            IconButton(onClick = onClick) {
                Icon(icon, null, tint = tint)
            }
        } else {
            // IconButton has no long-press; reproduce its size + circular ripple.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = tint)
            }
        }
    }

    @Composable
    private fun AppNavigationBar(
        navigator: Navigator,
        vm: MainScreenViewModel,
        containerColor: Color = LocalNavBarColor.current ?: MaterialTheme.colorScheme.surfaceVariant,
    ) {
        val accentColor = LocalAccentColor.current
        val hazeState = LocalHazeState.current
        val hazeStyle = if (hazeState != null) HazeMaterials.regular(containerColor) else null
        val itemColors = if (accentColor != null) {
            NavigationBarItemDefaults.colors(
                selectedIconColor = if (accentColor.luminance() > 0.5f) Color.Black else Color.White,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = accentColor
            )
        } else {
            NavigationBarItemDefaults.colors()
        }
        NavigationBar(
            containerColor = if (hazeState != null) Color.Transparent else containerColor,
            modifier = if (hazeState != null && hazeStyle != null)
                Modifier.hazeEffect(hazeState) { style = hazeStyle }
            else Modifier,
        ) {
            NavigationBarItem(
                alwaysShowLabel = true,
                selected = navigator.lastItem is LibraryScreen,
                onClick = vm::navigateToLibrary,
                icon = { Icon(Icons.Rounded.LocalLibrary, null) },
                label = { Text(LocalStrings.current.navigation.libraries) },
                colors = itemColors
            )
            NavigationBarItem(
                alwaysShowLabel = true,
                selected = navigator.lastItem is HomeScreen,
                onClick = { if (navigator.lastItem !is HomeScreen) navigator.replaceAll(HomeScreen()) },
                icon = { Icon(Icons.Rounded.Home, null) },
                label = { Text(LocalStrings.current.navigation.home) },
                colors = itemColors
            )
            NavigationBarItem(
                alwaysShowLabel = true,
                selected = navigator.lastItem is SearchScreen,
                onClick = { if (navigator.lastItem !is SearchScreen) navigator.push(SearchScreen(null)) },
                icon = { Icon(Icons.Rounded.Search, null) },
                label = { Text(LocalStrings.current.navigation.search) },
                colors = itemColors
            )
            if (vm.showStatsInBottomNav.collectAsState().value) {
                NavigationBarItem(
                    alwaysShowLabel = true,
                    selected = navigator.lastItem is snd.komelia.ui.stats.ReadingStatsScreen,
                    onClick = {
                        if (navigator.lastItem !is snd.komelia.ui.stats.ReadingStatsScreen)
                            navigator.push(snd.komelia.ui.stats.ReadingStatsScreen())
                    },
                    icon = { Icon(Icons.Rounded.BarChart, null) },
                    label = { Text(LocalStrings.current.navigation.stats) },
                    colors = itemColors
                )
            }
            if (vm.showNextReleasesInBottomNav.collectAsState().value) {
                NavigationBarItem(
                    alwaysShowLabel = true,
                    selected = navigator.lastItem is snd.komelia.ui.nextreleases.NextReleasesScreen,
                    onClick = {
                        if (navigator.lastItem !is snd.komelia.ui.nextreleases.NextReleasesScreen)
                            navigator.push(snd.komelia.ui.nextreleases.NextReleasesScreen())
                    },
                    icon = { Icon(Icons.Rounded.Event, null) },
                    label = { Text(LocalStrings.current.navigation.releases) },
                    colors = itemColors
                )
            }
            NavigationBarItem(
                alwaysShowLabel = true,
                selected = navigator.lastItem is MobileSettingsScreen || navigator.lastItem is SettingsScreen,
                onClick = {
                    if (navigator.lastItem !is MobileSettingsScreen && navigator.lastItem !is SettingsScreen)
                        navigator.push(MobileSettingsScreen())
                },
                icon = { Icon(Icons.Rounded.Settings, null) },
                label = { Text(LocalStrings.current.navigation.settings) },
                colors = itemColors
            )
        }
    }

    @Composable
    private fun StandardBottomNavigationBar(
        navigator: Navigator,
        vm: MainScreenViewModel,
        modifier: Modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column {
                HorizontalDivider()
                Row(
                    modifier = modifier,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CompactNavButton(
                        text = LocalStrings.current.ui.libraries,
                        icon = Icons.Rounded.LocalLibrary,
                        onClick = vm::navigateToLibrary,
                        isSelected = navigator.lastItem is LibraryScreen,
                        modifier = Modifier.weight(1f)
                    )

                    CompactNavButton(
                        text = LocalStrings.current.ui.home,
                        icon = Icons.Rounded.Home,
                        onClick = { if (navigator.lastItem !is HomeScreen) navigator.replaceAll(HomeScreen()) },
                        isSelected = navigator.lastItem is HomeScreen,
                        modifier = Modifier.weight(1f)
                    )

                    CompactNavButton(
                        text = LocalStrings.current.ui.search,
                        icon = Icons.Rounded.Search,
                        onClick = { if (navigator.lastItem !is SearchScreen) navigator.push(SearchScreen(null)) },
                        isSelected = navigator.lastItem is SearchScreen,
                        modifier = Modifier.weight(1f)
                    )

                    if (vm.showStatsInBottomNav.collectAsState().value) {
                        CompactNavButton(
                            text = LocalStrings.current.ui.stats,
                            icon = Icons.Rounded.BarChart,
                            onClick = {
                                if (navigator.lastItem !is snd.komelia.ui.stats.ReadingStatsScreen)
                                    navigator.push(snd.komelia.ui.stats.ReadingStatsScreen())
                            },
                            isSelected = navigator.lastItem is snd.komelia.ui.stats.ReadingStatsScreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (vm.showNextReleasesInBottomNav.collectAsState().value) {
                        CompactNavButton(
                            text = LocalStrings.current.ui.sorties,
                            icon = Icons.Rounded.Event,
                            onClick = {
                                if (navigator.lastItem !is snd.komelia.ui.nextreleases.NextReleasesScreen)
                                    navigator.push(snd.komelia.ui.nextreleases.NextReleasesScreen())
                            },
                            isSelected = navigator.lastItem is snd.komelia.ui.nextreleases.NextReleasesScreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    CompactNavButton(
                        text = LocalStrings.current.ui.settings,
                        icon = Icons.Rounded.Settings,
                        onClick = {
                            if (navigator.lastItem !is MobileSettingsScreen && navigator.lastItem !is SettingsScreen)
                                navigator.push(MobileSettingsScreen())
                        },
                        isSelected = navigator.lastItem is SettingsScreen || navigator.lastItem is MobileSettingsScreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    @Composable
    private fun CompactNavButton(
        text: String,
        icon: ImageVector,
        onClick: () -> Unit,
        isSelected: Boolean,
        modifier: Modifier
    ) {
        val accentColor = LocalAccentColor.current
        Surface(
            modifier = modifier,
            color = Color.Transparent,
            contentColor =
            if (isSelected) accentColor ?: MaterialTheme.colorScheme.secondary
            else contentColorFor(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .clickable { onClick() }
                    .cursorForHand()
                    .padding(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, null)
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable
    private fun NavBar(
        vm: MainScreenViewModel,
        navigator: Navigator,
        width: WindowSizeClass
    ) {
        val coroutineScope = rememberCoroutineScope()
        NavBarContent(
            currentScreen = navigator.lastItem,
            libraries = vm.libraries.collectAsState().value,
            libraryActions = vm.getLibraryActions(),
            onHomeClick = {
                if (navigator.lastItem !is HomeScreen) navigator.replaceAll(HomeScreen())
                if (width != FULL) coroutineScope.launch { vm.navBarState.snapTo(Closed) }
            },
            onLibrariesClick = {
                val current = navigator.lastItem
                if (current !is LibraryScreen || current.libraryId != null) {
                    navigator.replaceAll(LibraryScreen())
                }
                if (width != FULL) coroutineScope.launch { vm.navBarState.snapTo(Closed) }
            },

            onLibraryClick = { libraryId ->
                val current = navigator.lastItem
                if (current !is LibraryScreen || current.libraryId != libraryId) {
                    navigator.replaceAll(LibraryScreen(libraryId))
                }
                if (width != FULL) coroutineScope.launch { vm.navBarState.snapTo(Closed) }
            },
            onFavoritesClick = {
                // push, not replaceAll: unlike Home/Library (tab-like, no back
                // button), Favorites/Planned have an on-screen back arrow +
                // BackPressHandler that need a real stack entry to pop to.
                if (navigator.lastItem !is FavoritesScreen) navigator.push(FavoritesScreen())
                if (width != FULL) coroutineScope.launch { vm.navBarState.snapTo(Closed) }
            },
            onPlannedClick = {
                if (navigator.lastItem !is PlannedScreen) navigator.push(PlannedScreen())
                if (width != FULL) coroutineScope.launch { vm.navBarState.snapTo(Closed) }
            },
            onSettingsClick = { navigator.parent!!.push(SettingsScreen()) },
            taskQueueStatus = vm.komgaTaskQueueStatus.collectAsState().value
        )
    }

    @Composable
    private fun LibrariesNavBar(
        modifier: Modifier,
        vm: MainScreenViewModel,
        navigator: Navigator,
    ) {
        val coroutineScope = rememberCoroutineScope()
        LibrariesNavBarContent(
            modifier = modifier,
            currentScreen = navigator.lastItem,
            libraries = vm.libraries.collectAsState().value,
            libraryActions = vm.getLibraryActions(),
            onLibrariesClick = {
                val current = navigator.lastItem
                if (current !is LibraryScreen || current.libraryId != null) {
                    navigator.replaceAll(LibraryScreen())
                }
                coroutineScope.launch { vm.navBarState.snapTo(Closed) }
            },

            onLibraryClick = { libraryId ->
                val current = navigator.lastItem
                if (current !is LibraryScreen || current.libraryId != libraryId) {
                    navigator.replaceAll(LibraryScreen(libraryId))
                }
                coroutineScope.launch { vm.navBarState.snapTo(Closed) }
            },
            onFavoritesClick = {
                if (navigator.lastItem !is FavoritesScreen) navigator.push(FavoritesScreen())
                coroutineScope.launch { vm.navBarState.snapTo(Closed) }
            },
            onPlannedClick = {
                if (navigator.lastItem !is PlannedScreen) navigator.push(PlannedScreen())
                coroutineScope.launch { vm.navBarState.snapTo(Closed) }
            },
        )
    }
}
