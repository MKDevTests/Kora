package snd.komelia.ui.settings.appearance

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.settings.SettingsScreenContainer

class AppSettingsScreen : Screen {

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getAppearanceViewModel() }
        LaunchedEffect(Unit) { vm.initialize() }
        val state = vm.state.collectAsState()

        SettingsScreenContainer("Appearance") {
            when (val result = state.value) {
                is LoadState.Error -> Text("${result::class.simpleName}: ${result.exception.message}")
                LoadState.Uninitialized, LoadState.Loading -> LoadingMaxSizeIndicator()
                is LoadState.Success -> AppearanceSettingsContent(
                    cardWidth = vm.cardWidth,
                    onCardWidthChange = vm::onCardWidthChange,
                    currentTheme = vm.currentTheme,
                    onThemeChange = vm::onAppThemeChange,
                    accentColor = vm.accentColor,
                    onAccentColorChange = vm::onAccentColorChange,
                    useNewLibraryUI = vm.useNewLibraryUI,
                    onUseNewLibraryUIChange = vm::onUseNewLibraryUIChange,
                    cardLayoutBelow = vm.cardLayoutBelow,
                    onCardLayoutBelowChange = vm::onCardLayoutBelowChange,
                    immersiveColorEnabled = vm.immersiveColorEnabled,
                    onImmersiveColorEnabledChange = vm::onImmersiveColorEnabledChange,
                    immersiveColorAlpha = vm.immersiveColorAlpha,
                    onImmersiveColorAlphaChange = vm::onImmersiveColorAlphaChange,
                    showImmersiveNavBar = vm.showImmersiveNavBar,
                    onShowImmersiveNavBarChange = vm::onShowImmersiveNavBarChange,
                    hideParenthesesInNames = vm.hideParenthesesInNames,
                    onHideParenthesesInNamesChange = vm::onHideParenthesesInNamesChange,
                    showLanguageOnCovers = vm.showLanguageOnCovers,
                    onShowLanguageOnCoversChange = vm::onShowLanguageOnCoversChange,
                    languageBadgeScale = vm.languageBadgeScale,
                    onLanguageBadgeScaleChange = vm::onLanguageBadgeScaleChange,
                    languageBadgeAtBottom = vm.languageBadgeAtBottom,
                    onLanguageBadgeAtBottomChange = vm::onLanguageBadgeAtBottomChange,
                    lockScreenRotation = vm.lockScreenRotation,
                    onLockScreenRotationChange = vm::onLockScreenRotationChange,
                    cardLayoutOverlayBackground = vm.cardLayoutOverlayBackground,
                    onCardLayoutOverlayBackgroundChange = vm::onCardLayoutOverlayBackgroundChange,
                    useNewLibraryUI2 = vm.useNewLibraryUI2,
                    onUseNewLibraryUI2Change = vm::onUseNewLibraryUI2Change,
                    useFloatingNavigationBar = vm.useFloatingNavigationBar,
                    onUseFloatingNavigationBarChange = vm::onUseFloatingNavigationBarChange,
                    useImmersiveMorphingCover = vm.useImmersiveMorphingCover,
                    onUseImmersiveMorphingCoverChange = vm::onUseImmersiveMorphingCoverChange,
                    cardWidthScale = vm.cardWidthScale,
                    onCardWidthScaleChange = vm::onCardWidthScaleChange,
                    cardHeightScale = vm.cardHeightScale,
                    onCardHeightScaleChange = vm::onCardHeightScaleChange,
                    cardSpacingBelow = vm.cardSpacingBelow,
                    onCardSpacingBelowChange = vm::onCardSpacingBelowChange,
                    cardShadowLevel = vm.cardShadowLevel,
                    onCardShadowLevelChange = vm::onCardShadowLevelChange,
                    cardCornerRadius = vm.cardCornerRadius,
                    onCardCornerRadiusChange = vm::onCardCornerRadiusChange,
                    experimentalGenreTab = vm.experimentalGenreTab,
                    onExperimentalGenreTabChange = vm::onExperimentalGenreTabChange,
                )
            }
        }
    }
}
