package snd.komelia.db.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import snd.komelia.db.AppSettings
import snd.komelia.db.SettingsStateWrapper
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.model.AppTheme
import snd.komelia.settings.model.BooksLayout
import snd.komelia.settings.model.StartupScreen
import snd.komga.client.library.KomgaLibraryId
import snd.komelia.updates.AppVersion
import kotlin.time.Instant

class SettingsRepositoryWrapper(
    val wrapper: SettingsStateWrapper<AppSettings>,
) : CommonSettingsRepository {

    override fun getServerUrl(): Flow<String> {
        return wrapper.state.map { it.serverUrl }.distinctUntilChanged()
    }

    override suspend fun putServerUrl(url: String) {
        wrapper.transform { it.copy(serverUrl = url) }
    }

    override fun getCardWidth(): Flow<Int> {
        return wrapper.state.map { it.cardWidth }.distinctUntilChanged()
    }

    override suspend fun putCardWidth(cardWidth: Int) {
        wrapper.transform { it.copy(cardWidth = cardWidth) }
    }

    override fun getCurrentUser(): Flow<String> {
        return wrapper.state.map { it.username }.distinctUntilChanged()
    }

    override suspend fun putCurrentUser(username: String) {
        wrapper.transform { it.copy(username = username) }
    }

    override fun getSeriesPageLoadSize(): Flow<Int> {
        return wrapper.state.map { it.seriesPageLoadSize }.distinctUntilChanged()
    }

    override suspend fun putSeriesPageLoadSize(size: Int) {
        wrapper.transform { it.copy(seriesPageLoadSize = size) }
    }

    override fun getBookPageLoadSize(): Flow<Int> {
        return wrapper.state.map { it.bookPageLoadSize }.distinctUntilChanged()
    }

    override suspend fun putBookPageLoadSize(size: Int) {
        wrapper.transform { it.copy(bookPageLoadSize = size) }
    }

    override fun getBookListLayout(): Flow<BooksLayout> {
        return wrapper.state.map { it.bookListLayout }.distinctUntilChanged()
    }

    override suspend fun putBookListLayout(layout: BooksLayout) {
        wrapper.transform { it.copy(bookListLayout = layout) }
    }

    override fun getCheckForUpdatesOnStartup(): Flow<Boolean> {
        return wrapper.state.map { it.checkForUpdatesOnStartup }.distinctUntilChanged()
    }

    override suspend fun putCheckForUpdatesOnStartup(check: Boolean) {
        wrapper.transform { it.copy(checkForUpdatesOnStartup = check) }
    }

    override fun getLastUpdateCheckTimestamp(): Flow<Instant?> {
        return wrapper.state.map { it.updateLastCheckedTimestamp }.distinctUntilChanged()
    }

    override suspend fun putLastUpdateCheckTimestamp(timestamp: Instant) {
        wrapper.transform { it.copy(updateLastCheckedTimestamp = timestamp) }
    }

    override fun getLastCheckedReleaseVersion(): Flow<AppVersion?> {
        return wrapper.state.map { it.updateLastCheckedReleaseVersion }.distinctUntilChanged()
    }

    override suspend fun putLastCheckedReleaseVersion(version: AppVersion) {
        wrapper.transform { it.copy(updateLastCheckedReleaseVersion = version) }
    }

    override fun getDismissedVersion(): Flow<AppVersion?> {
        return wrapper.state.map { it.updateDismissedVersion }.distinctUntilChanged()
    }

    override suspend fun putDismissedVersion(version: AppVersion) {
        wrapper.transform { it.copy(updateDismissedVersion = version) }
    }

    override fun getAppTheme(): Flow<AppTheme> {
        return wrapper.state.map { it.appTheme }.distinctUntilChanged()
    }

    override suspend fun putAppTheme(theme: AppTheme) {
        wrapper.transform { it.copy(appTheme = theme) }
    }

    override fun getNavBarColor(): Flow<Long?> {
        return wrapper.state.map { it.navBarColor }.distinctUntilChanged()
    }

    override suspend fun putNavBarColor(color: Long?) {
        wrapper.transform { it.copy(navBarColor = color) }
    }

    override fun getAccentColor(): Flow<Long?> {
        return wrapper.state.map { it.accentColor }.distinctUntilChanged()
    }

    override suspend fun putAccentColor(color: Long?) {
        wrapper.transform { it.copy(accentColor = color) }
    }

    override fun getUseNewLibraryUI(): Flow<Boolean> {
        return wrapper.state.map { it.useNewLibraryUI }.distinctUntilChanged()
    }

    override suspend fun putUseNewLibraryUI(enabled: Boolean) {
        wrapper.transform { it.copy(useNewLibraryUI = enabled) }
    }

    override fun getCardLayoutBelow(): Flow<Boolean> {
        return wrapper.state.map { it.cardLayoutBelow }.distinctUntilChanged()
    }

    override suspend fun putCardLayoutBelow(enabled: Boolean) {
        wrapper.transform { it.copy(cardLayoutBelow = enabled) }
    }

    override fun getImmersiveColorEnabled(): Flow<Boolean> =
        wrapper.state.map { it.immersiveColorEnabled }.distinctUntilChanged()

    override suspend fun putImmersiveColorEnabled(enabled: Boolean) =
        wrapper.transform { it.copy(immersiveColorEnabled = enabled) }

    override fun getImmersiveColorAlpha(): Flow<Float> =
        wrapper.state.map { it.immersiveColorAlpha }.distinctUntilChanged()

    override suspend fun putImmersiveColorAlpha(alpha: Float) =
        wrapper.transform { it.copy(immersiveColorAlpha = alpha) }

    override fun getShowImmersiveNavBar(): Flow<Boolean> =
        wrapper.state.map { it.showImmersiveNavBar }.distinctUntilChanged()

    override suspend fun putShowImmersiveNavBar(enabled: Boolean) =
        wrapper.transform { it.copy(showImmersiveNavBar = enabled) }

    override fun getUseNewLibraryUI2(): Flow<Boolean> =
        wrapper.state.map { it.useNewLibraryUI2 }.distinctUntilChanged()

    override suspend fun putUseNewLibraryUI2(enabled: Boolean) =
        wrapper.transform { it.copy(useNewLibraryUI2 = enabled) }

    override fun getLastSelectedLibraryId(): Flow<KomgaLibraryId?> {
        return wrapper.state.map { it.lastSelectedLibraryId?.let { id -> KomgaLibraryId(id) } }.distinctUntilChanged()
    }

    override suspend fun putLastSelectedLibraryId(libraryId: KomgaLibraryId?) {
        wrapper.transform { it.copy(lastSelectedLibraryId = libraryId?.value) }
    }

    override fun getHideParenthesesInNames(): Flow<Boolean> =
        wrapper.state.map { it.hideParenthesesInNames }.distinctUntilChanged()

    override suspend fun putHideParenthesesInNames(hide: Boolean) =
        wrapper.transform { it.copy(hideParenthesesInNames = hide) }

    override fun getLockScreenRotation(): Flow<Boolean> =
        wrapper.state.map { it.lockScreenRotation }.distinctUntilChanged()

    override suspend fun putLockScreenRotation(locked: Boolean) =
        wrapper.transform { it.copy(lockScreenRotation = locked) }

    override fun getKeepReaderScreenOn(): Flow<Boolean> =
        wrapper.state.map { it.keepReaderScreenOn }.distinctUntilChanged()

    override suspend fun putKeepReaderScreenOn(enabled: Boolean) {
        wrapper.transform { it.copy(keepReaderScreenOn = enabled) }
    }

    override fun getCardLayoutOverlayBackground(): Flow<Boolean> =
        wrapper.state.map { it.cardLayoutOverlayBackground }.distinctUntilChanged()

    override suspend fun putCardLayoutOverlayBackground(enabled: Boolean) {
        wrapper.transform { it.copy(cardLayoutOverlayBackground = enabled) }
    }

    override fun getShowContinueReading(): Flow<Boolean> =
        wrapper.state.map { it.showContinueReading }.distinctUntilChanged()

    override suspend fun putShowContinueReading(enabled: Boolean) {
        wrapper.transform { it.copy(showContinueReading = enabled) }
    }

    override fun getUseImmersiveMorphingCover(): Flow<Boolean> =
        wrapper.state.map { it.useImmersiveMorphingCover }.distinctUntilChanged()

    override suspend fun putUseImmersiveMorphingCover(enabled: Boolean) =
        wrapper.transform { it.copy(useImmersiveMorphingCover = enabled) }

    override fun getCardWidthScale(): Flow<Float> =
        wrapper.state.map { it.cardWidthScale }.distinctUntilChanged()

    override suspend fun putCardWidthScale(scale: Float) =
        wrapper.transform { it.copy(cardWidthScale = scale) }

    override fun getCardHeightScale(): Flow<Float> =
        wrapper.state.map { it.cardHeightScale }.distinctUntilChanged()

    override suspend fun putCardHeightScale(scale: Float) =
        wrapper.transform { it.copy(cardHeightScale = scale) }

    override fun getCardSpacingBelow(): Flow<Float> =
        wrapper.state.map { it.cardSpacingBelow }.distinctUntilChanged()

    override suspend fun putCardSpacingBelow(spacing: Float) =
        wrapper.transform { it.copy(cardSpacingBelow = spacing) }

    override fun getCardShadowLevel(): Flow<Float> =
        wrapper.state.map { it.cardShadowLevel }.distinctUntilChanged()

    override suspend fun putCardShadowLevel(level: Float) =
        wrapper.transform { it.copy(cardShadowLevel = level) }

    override fun getCardCornerRadius(): Flow<Float> =
        wrapper.state.map { it.cardCornerRadius }.distinctUntilChanged()

    override suspend fun putCardCornerRadius(radius: Float) =
        wrapper.transform { it.copy(cardCornerRadius = radius) }

    override fun getFloatingNavigationBar(): Flow<Boolean> =
        wrapper.state.map { it.useFloatingNavigationBar }.distinctUntilChanged()

    override suspend fun putFloatingNavigationBar(enabled: Boolean) =
        wrapper.transform { it.copy(useFloatingNavigationBar = enabled) }

    override fun getLastHighlightColor(): Flow<Int> {
        return wrapper.state.map { it.lastHighlightColor ?: 0xFFFFEB3B.toInt() }.distinctUntilChanged()
    }

    override suspend fun putLastHighlightColor(color: Int) {
        wrapper.transform { it.copy(lastHighlightColor = color) }
    }

    override fun getSearchFuzzyEnabled(): Flow<Boolean> =
        wrapper.state.map { it.searchFuzzyEnabled }.distinctUntilChanged()

    override suspend fun putSearchFuzzyEnabled(enabled: Boolean) {
        wrapper.transform { it.copy(searchFuzzyEnabled = enabled) }
    }

    override fun getLibraryDropdownInTitle(): Flow<Boolean> =
        wrapper.state.map { it.libraryDropdownInTitle }.distinctUntilChanged()

    override suspend fun putLibraryDropdownInTitle(enabled: Boolean) {
        wrapper.transform { it.copy(libraryDropdownInTitle = enabled) }
    }

    override fun getStartupScreen(): Flow<StartupScreen> =
        wrapper.state.map { it.startupScreen }.distinctUntilChanged()

    override suspend fun putStartupScreen(screen: StartupScreen) {
        wrapper.transform { it.copy(startupScreen = screen) }
    }

    override fun getStatsEnabled(): Flow<Boolean> =
        wrapper.state.map { it.statsEnabled }.distinctUntilChanged()

    override suspend fun putStatsEnabled(enabled: Boolean) {
        wrapper.transform { it.copy(statsEnabled = enabled) }
    }

    override fun getStatsInBottomNav(): Flow<Boolean> =
        wrapper.state.map { it.statsInBottomNav }.distinctUntilChanged()

    override suspend fun putStatsInBottomNav(enabled: Boolean) {
        wrapper.transform { it.copy(statsInBottomNav = enabled) }
    }
}
