package snd.komelia.ui.settings.appearance

import snd.komelia.settings.model.TitleFont
import snd.komelia.settings.model.BooksLayout
import snd.komelia.settings.model.UnreadBadgeStyle
import snd.komelia.settings.model.AppIcon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.LoadState
import snd.komelia.ui.common.cards.defaultCardWidth

class AppSettingsViewModel(
    private val settingsRepository: CommonSettingsRepository,
) : StateScreenModel<LoadState<Unit>>(LoadState.Uninitialized) {
    var cardWidth by mutableStateOf(defaultCardWidth.dp)
    var currentTheme by mutableStateOf(AppTheme.DARK)
    var uiLanguage by mutableStateOf(snd.komelia.ui.i18n.AppLanguage.SYSTEM)
    var paletteSeed by mutableStateOf<Color?>(null)
    var pureBlack by mutableStateOf(false)
    var darkAtNight by mutableStateOf(false)
    var darkNightStart by mutableStateOf(21 * 60)
    var darkNightEnd by mutableStateOf(7 * 60)
    var accentFollowsCover by mutableStateOf(true)
    var textScale by mutableStateOf(1f)
    var titleFont by mutableStateOf(TitleFont.SERIF)
    var unreadBadgeStyle by mutableStateOf(UnreadBadgeStyle.COUNT)
    var unreadBadgeAtStart by mutableStateOf(false)
    var compactUi by mutableStateOf(false)
    var appIcon by mutableStateOf(AppIcon.DEFAULT)
    var useNewLibraryUI by mutableStateOf(true)
    var cardLayoutBelow by mutableStateOf(false)
    var immersiveColorEnabled by mutableStateOf(true)
    var immersiveColorAlpha by mutableStateOf(0.12f)
    var showImmersiveNavBar by mutableStateOf(false)
    var hideParenthesesInNames by mutableStateOf(false)
    var authorRolesFilterEnabled by mutableStateOf(false)
    var hiddenAuthorRoles by mutableStateOf(emptySet<String>())
    var showLanguageOnCovers by mutableStateOf(false)
    var languageBadgeScale by mutableStateOf(1.0f)
    var languageBadgeAtBottom by mutableStateOf(false)
    var showCompleteSeriesBadge by mutableStateOf(true)
    var lockScreenRotation by mutableStateOf(false)
    var cardLayoutOverlayBackground by mutableStateOf(true)
    var useNewLibraryUI2 by mutableStateOf(false)
    var useImmersiveMorphingCover by mutableStateOf(false)
    var cardWidthScale by mutableStateOf(1.0f)
    var cardHeightScale by mutableStateOf(1.0f)
    var cardSpacingBelow by mutableStateOf(0.0f)
    var cardShadowLevel by mutableStateOf(2.0f)
    var cardCornerRadius by mutableStateOf(8.0f)
    var useFloatingNavigationBar by mutableStateOf(false)

    suspend fun initialize() {
        if (state.value !is LoadState.Uninitialized) return
        mutableState.value = LoadState.Loading
        cardWidth = settingsRepository.getCardWidth().map { it.dp }.first()
        currentTheme = settingsRepository.getAppTheme().first()
        uiLanguage = snd.komelia.ui.i18n.AppLanguage.of(settingsRepository.getUiLanguage().first())
        paletteSeed = settingsRepository.getPaletteSeed().first()?.let { Color(it.toInt()) }
        pureBlack = settingsRepository.getPureBlack().first()
        darkAtNight = settingsRepository.getDarkAtNight().first()
        darkNightStart = settingsRepository.getDarkNightStart().first()
        darkNightEnd = settingsRepository.getDarkNightEnd().first()
        accentFollowsCover = settingsRepository.getAccentFollowsCover().first()
        textScale = settingsRepository.getTextScale().first()
        titleFont = settingsRepository.getTitleFont().first()
        unreadBadgeStyle = settingsRepository.getUnreadBadgeStyle().first()
        unreadBadgeAtStart = settingsRepository.getUnreadBadgeAtStart().first()
        compactUi = settingsRepository.getCompactUi().first()
        appIcon = settingsRepository.getAppIcon().first()
        useNewLibraryUI = settingsRepository.getUseNewLibraryUI().first()
        cardLayoutBelow = settingsRepository.getCardLayoutBelow().first()
        immersiveColorEnabled = settingsRepository.getImmersiveColorEnabled().first()
        immersiveColorAlpha = settingsRepository.getImmersiveColorAlpha().first()
        showImmersiveNavBar = settingsRepository.getShowImmersiveNavBar().first()
        hideParenthesesInNames = settingsRepository.getHideParenthesesInNames().first()
        authorRolesFilterEnabled = settingsRepository.getAuthorRolesFilterEnabled().first()
        hiddenAuthorRoles = settingsRepository.getHiddenAuthorRoles().first()
        showLanguageOnCovers = settingsRepository.getShowLanguageOnCovers().first()
        languageBadgeScale = settingsRepository.getLanguageBadgeScale().first()
        languageBadgeAtBottom = settingsRepository.getLanguageBadgeAtBottom().first()
        showCompleteSeriesBadge = settingsRepository.getShowCompleteSeriesBadge().first()
        lockScreenRotation = settingsRepository.getLockScreenRotation().first()
        cardLayoutOverlayBackground = settingsRepository.getCardLayoutOverlayBackground().first()
        useNewLibraryUI2 = settingsRepository.getUseNewLibraryUI2().first()
        useImmersiveMorphingCover = settingsRepository.getUseImmersiveMorphingCover().first()
        cardWidthScale = settingsRepository.getCardWidthScale().first()
        cardHeightScale = settingsRepository.getCardHeightScale().first()
        cardSpacingBelow = settingsRepository.getCardSpacingBelow().first()
        cardShadowLevel = settingsRepository.getCardShadowLevel().first()
        cardCornerRadius = settingsRepository.getCardCornerRadius().first()
        useFloatingNavigationBar = settingsRepository.getFloatingNavigationBar().first()

        settingsRepository.putNavBarColor(null)
        mutableState.value = LoadState.Success(Unit)
    }

    fun onCardShadowLevelChange(level: Float) {
        this.cardShadowLevel = level
        screenModelScope.launch { settingsRepository.putCardShadowLevel(level) }
    }

    fun onCardCornerRadiusChange(radius: Float) {
        this.cardCornerRadius = radius
        screenModelScope.launch { settingsRepository.putCardCornerRadius(radius) }
    }

    fun onUseFloatingNavigationBarChange(enabled: Boolean) {
        this.useFloatingNavigationBar = enabled
        screenModelScope.launch { settingsRepository.putFloatingNavigationBar(enabled) }
    }

    fun onCardWidthScaleChange(scale: Float) {
        this.cardWidthScale = scale
        screenModelScope.launch { settingsRepository.putCardWidthScale(scale) }
    }

    fun onCardHeightScaleChange(scale: Float) {
        this.cardHeightScale = scale
        screenModelScope.launch { settingsRepository.putCardHeightScale(scale) }
    }

    fun onCardSpacingBelowChange(spacing: Float) {
        this.cardSpacingBelow = spacing
        screenModelScope.launch { settingsRepository.putCardSpacingBelow(spacing) }
    }

    fun onCardWidthChange(cardWidth: Dp) {
        this.cardWidth = cardWidth
        screenModelScope.launch { settingsRepository.putCardWidth(cardWidth.value.toInt()) }
    }

    fun onAppThemeChange(theme: AppTheme) {
        this.currentTheme = theme
        screenModelScope.launch { settingsRepository.putAppTheme(theme) }
    }

    fun onPaletteSeedChange(color: Color?) {
        this.paletteSeed = color
        screenModelScope.launch { settingsRepository.putPaletteSeed(color?.toArgb()?.toLong()) }
    }

    fun onPureBlackChange(enabled: Boolean) {
        this.pureBlack = enabled
        screenModelScope.launch { settingsRepository.putPureBlack(enabled) }
    }

    fun onDarkAtNightChange(enabled: Boolean) {
        this.darkAtNight = enabled
        screenModelScope.launch { settingsRepository.putDarkAtNight(enabled) }
    }

    fun onDarkNightStartChange(minutes: Int) {
        this.darkNightStart = minutes
        screenModelScope.launch { settingsRepository.putDarkNightStart(minutes) }
    }

    fun onDarkNightEndChange(minutes: Int) {
        this.darkNightEnd = minutes
        screenModelScope.launch { settingsRepository.putDarkNightEnd(minutes) }
    }

    fun onAccentFollowsCoverChange(enabled: Boolean) {
        this.accentFollowsCover = enabled
        screenModelScope.launch { settingsRepository.putAccentFollowsCover(enabled) }
    }

    fun onTextScaleChange(scale: Float) {
        this.textScale = scale
        screenModelScope.launch { settingsRepository.putTextScale(scale) }
    }

    fun onTitleFontChange(font: TitleFont) {
        this.titleFont = font
        screenModelScope.launch { settingsRepository.putTitleFont(font) }
    }

    fun onUnreadBadgeStyleChange(style: UnreadBadgeStyle) {
        this.unreadBadgeStyle = style
        screenModelScope.launch { settingsRepository.putUnreadBadgeStyle(style) }
    }

    fun onUnreadBadgeAtStartChange(atStart: Boolean) {
        this.unreadBadgeAtStart = atStart
        screenModelScope.launch { settingsRepository.putUnreadBadgeAtStart(atStart) }
    }

    fun onCompactUiChange(enabled: Boolean) {
        this.compactUi = enabled
        screenModelScope.launch { settingsRepository.putCompactUi(enabled) }
    }

    fun onAppIconChange(icon: AppIcon) {
        this.appIcon = icon
        screenModelScope.launch { settingsRepository.putAppIcon(icon) }
    }

    fun onUseNewLibraryUIChange(enabled: Boolean) {
        this.useNewLibraryUI = enabled
        screenModelScope.launch { settingsRepository.putUseNewLibraryUI(enabled) }
    }

    fun onCardLayoutBelowChange(enabled: Boolean) {
        this.cardLayoutBelow = enabled
        screenModelScope.launch { settingsRepository.putCardLayoutBelow(enabled) }
    }

    fun onImmersiveColorEnabledChange(enabled: Boolean) {
        this.immersiveColorEnabled = enabled
        screenModelScope.launch { settingsRepository.putImmersiveColorEnabled(enabled) }
    }

    fun onImmersiveColorAlphaChange(alpha: Float) {
        this.immersiveColorAlpha = alpha
        screenModelScope.launch { settingsRepository.putImmersiveColorAlpha(alpha) }
    }

    fun onShowImmersiveNavBarChange(enabled: Boolean) {
        this.showImmersiveNavBar = enabled
        screenModelScope.launch { settingsRepository.putShowImmersiveNavBar(enabled) }
    }

    fun onUiLanguageChange(language: snd.komelia.ui.i18n.AppLanguage) {
        this.uiLanguage = language
        screenModelScope.launch { settingsRepository.putUiLanguage(language.tag) }
    }

    fun onHideParenthesesInNamesChange(hide: Boolean) {
        this.hideParenthesesInNames = hide
        screenModelScope.launch { settingsRepository.putHideParenthesesInNames(hide) }
    }

    fun onAuthorRolesFilterEnabledChange(enabled: Boolean) {
        this.authorRolesFilterEnabled = enabled
        screenModelScope.launch { settingsRepository.putAuthorRolesFilterEnabled(enabled) }
    }

    /** [visible] is what the switch shows, so a role is hidden when it's off. */
    fun onAuthorRoleVisibilityChange(role: String, visible: Boolean) {
        val updated = if (visible) hiddenAuthorRoles - role else hiddenAuthorRoles + role
        this.hiddenAuthorRoles = updated
        screenModelScope.launch { settingsRepository.putHiddenAuthorRoles(updated) }
    }

    fun onShowLanguageOnCoversChange(enabled: Boolean) {
        this.showLanguageOnCovers = enabled
        screenModelScope.launch { settingsRepository.putShowLanguageOnCovers(enabled) }
    }

    fun onLanguageBadgeScaleChange(scale: Float) {
        this.languageBadgeScale = scale
        screenModelScope.launch { settingsRepository.putLanguageBadgeScale(scale) }
    }

    fun onLanguageBadgeAtBottomChange(atBottom: Boolean) {
        this.languageBadgeAtBottom = atBottom
        screenModelScope.launch { settingsRepository.putLanguageBadgeAtBottom(atBottom) }
    }

    fun onShowCompleteSeriesBadgeChange(enabled: Boolean) {
        this.showCompleteSeriesBadge = enabled
        screenModelScope.launch { settingsRepository.putShowCompleteSeriesBadge(enabled) }
    }

    fun onLockScreenRotationChange(locked: Boolean) {
        this.lockScreenRotation = locked
        screenModelScope.launch { settingsRepository.putLockScreenRotation(locked) }
    }

    fun onCardLayoutOverlayBackgroundChange(enabled: Boolean) {
        this.cardLayoutOverlayBackground = enabled
        screenModelScope.launch { settingsRepository.putCardLayoutOverlayBackground(enabled) }
    }

    fun onUseNewLibraryUI2Change(enabled: Boolean) {
        this.useNewLibraryUI2 = enabled
        screenModelScope.launch { settingsRepository.putUseNewLibraryUI2(enabled) }
    }

    fun onUseImmersiveMorphingCoverChange(enabled: Boolean) {
        this.useImmersiveMorphingCover = enabled
        screenModelScope.launch { settingsRepository.putUseImmersiveMorphingCover(enabled) }
    }

}