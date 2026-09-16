package snd.komelia.db.repository

import snd.komelia.settings.model.TitleFont
import snd.komelia.settings.model.UnreadBadgeStyle
import snd.komelia.settings.model.AppIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import snd.komelia.db.AppSettings
import snd.komelia.db.SettingsStateWrapper
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.model.AppTheme
import snd.komelia.settings.model.ChapterSeriesFilter
import snd.komelia.settings.model.AutobackupFrequency
import snd.komelia.settings.model.BooksLayout
import snd.komelia.settings.model.StartupScreen
import snd.komga.client.library.KomgaLibraryId
import snd.komelia.updates.AppVersion
import kotlin.time.Instant
import snd.komelia.grid.snapPageLoadSize

class SettingsRepositoryWrapper(
    val wrapper: SettingsStateWrapper<AppSettings>,
) : CommonSettingsRepository {

    override fun getServerUrl(): Flow<String> {
        return wrapper.state.map { it.serverUrl }.distinctUntilChanged()
    }

    override suspend fun putServerUrl(url: String) {
        wrapper.transform { it.copy(serverUrl = url) }
    }

    override fun getAlternateServerUrls(): Flow<List<String>> {
        return wrapper.state.map { it.alternateServerUrls }.distinctUntilChanged()
    }

    override suspend fun putAlternateServerUrls(urls: List<String>) {
        wrapper.transform { it.copy(alternateServerUrls = urls) }
    }

    override fun getExperimentalGenreTab(): Flow<Boolean> =
        wrapper.state.map { it.experimentalGenreTab }.distinctUntilChanged()

    override suspend fun putExperimentalGenreTab(enabled: Boolean) {
        wrapper.transform { it.copy(experimentalGenreTab = enabled) }
    }

    override fun getDiscoverEnabled(): Flow<Boolean> =
        wrapper.state.map { it.discoverEnabled }.distinctUntilChanged()

    override suspend fun putDiscoverEnabled(enabled: Boolean) {
        wrapper.transform { it.copy(discoverEnabled = enabled) }
    }

    override fun getDiscoverLibraryIds(): Flow<Set<String>> =
        wrapper.state.map { it.discoverLibraryIds }.distinctUntilChanged()

    override suspend fun putDiscoverLibraryIds(libraryIds: Set<String>) {
        wrapper.transform { it.copy(discoverLibraryIds = libraryIds) }
    }

    override fun getDiscoverHideUnlicensed(): Flow<Boolean> =
        wrapper.state.map { it.discoverHideUnlicensed }.distinctUntilChanged()

    override suspend fun putDiscoverHideUnlicensed(hide: Boolean) {
        wrapper.transform { it.copy(discoverHideUnlicensed = hide) }
    }

    override fun getGenreCoverOverrides(): Flow<Map<String, String>> =
        wrapper.state.map { it.genreCoverOverrides }.distinctUntilChanged()

    override suspend fun putGenreCoverOverrides(overrides: Map<String, String>) {
        wrapper.transform { it.copy(genreCoverOverrides = overrides) }
    }

    override fun getGenreLabelOverrides(): Flow<Map<String, String>> =
        wrapper.state.map { it.genreLabelOverrides }.distinctUntilChanged()

    override suspend fun putGenreLabelOverrides(overrides: Map<String, String>) {
        wrapper.transform { it.copy(genreLabelOverrides = overrides) }
    }

    override fun getIgnoreListEnabled(): Flow<Boolean> =
        wrapper.state.map { it.ignoreListEnabled }.distinctUntilChanged()

    override suspend fun putIgnoreListEnabled(enabled: Boolean) {
        wrapper.transform { it.copy(ignoreListEnabled = enabled) }
    }

    override fun getIgnoreListMigratedToServerHidden(): Flow<Boolean> =
        wrapper.state.map { it.ignoreListMigratedToServerHidden }.distinctUntilChanged()

    override suspend fun putIgnoreListMigratedToServerHidden(value: Boolean) {
        wrapper.transform { it.copy(ignoreListMigratedToServerHidden = value) }
    }

    override fun getIgnoredSeriesIds(): Flow<Set<String>> =
        wrapper.state.map { it.ignoredSeriesIds }.distinctUntilChanged()

    override suspend fun putIgnoredSeriesIds(ids: Set<String>) {
        wrapper.transform { it.copy(ignoredSeriesIds = ids) }
    }

    override fun getFavoriteSeriesIds(): Flow<Set<String>> =
        wrapper.state.map { it.favoriteSeriesIds }.distinctUntilChanged()

    override suspend fun putFavoriteSeriesIds(ids: Set<String>) {
        wrapper.transform { it.copy(favoriteSeriesIds = ids) }
    }

    override fun getPlannedSeriesIds(): Flow<Set<String>> =
        wrapper.state.map { it.plannedSeriesIds }.distinctUntilChanged()

    override suspend fun putPlannedSeriesIds(ids: Set<String>) {
        wrapper.transform { it.copy(plannedSeriesIds = ids) }
    }

    override fun getSeriesLibraryIds(): Flow<Map<String, String>> =
        wrapper.state.map { it.seriesLibraryIds }.distinctUntilChanged()

    /** Merges: callers record what they just learned without dropping the rest. */
    override suspend fun putSeriesLibraryIds(mapping: Map<String, String>) {
        if (mapping.isEmpty()) return
        wrapper.transform { it.copy(seriesLibraryIds = it.seriesLibraryIds + mapping) }
    }

    override fun getExcludedLibraryIds(): Flow<Set<String>> =
        wrapper.state.map { it.excludedLibraryIds }.distinctUntilChanged()

    override suspend fun putExcludedLibraryIds(ids: Set<String>) {
        wrapper.transform { it.copy(excludedLibraryIds = ids) }
    }

    override fun getGenreTilesCustomAppearance(): Flow<Boolean> =
        wrapper.state.map { it.genreTilesCustomAppearance }.distinctUntilChanged()

    override suspend fun putGenreTilesCustomAppearance(enabled: Boolean) {
        wrapper.transform { it.copy(genreTilesCustomAppearance = enabled) }
    }

    override fun getGenreTileWidth(): Flow<Int> =
        wrapper.state.map { it.genreTileWidth }.distinctUntilChanged()

    override suspend fun putGenreTileWidth(width: Int) {
        wrapper.transform { it.copy(genreTileWidth = width) }
    }

    override fun getGenreTileTextBelow(): Flow<Boolean> =
        wrapper.state.map { it.genreTileTextBelow }.distinctUntilChanged()

    override suspend fun putGenreTileTextBelow(below: Boolean) {
        wrapper.transform { it.copy(genreTileTextBelow = below) }
    }

    override fun getGenreTileShowCount(): Flow<Boolean> =
        wrapper.state.map { it.genreTileShowCount }.distinctUntilChanged()

    override suspend fun putGenreTileShowCount(show: Boolean) {
        wrapper.transform { it.copy(genreTileShowCount = show) }
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

    // Snapped on read, not migrated in place: a size stored before the offered
    // list changed (50, 20) does not divide any usual column count, so it would
    // keep drawing a ragged last row on every page until the user happened to
    // reopen the grid-size menu.
    override fun getSeriesPageLoadSize(): Flow<Int> {
        return wrapper.state.map { snapPageLoadSize(it.seriesPageLoadSize) }.distinctUntilChanged()
    }

    override suspend fun putSeriesPageLoadSize(size: Int) {
        wrapper.transform { it.copy(seriesPageLoadSize = size) }
    }

    override fun getBookPageLoadSize(): Flow<Int> {
        return wrapper.state.map { snapPageLoadSize(it.bookPageLoadSize) }.distinctUntilChanged()
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

    override fun getPaletteSeed(): Flow<Long?> =
        wrapper.state.map { it.paletteSeed }.distinctUntilChanged()

    override suspend fun putPaletteSeed(color: Long?) {
        wrapper.transform { it.copy(paletteSeed = color) }
    }

    override fun getPureBlack(): Flow<Boolean> =
        wrapper.state.map { it.pureBlack }.distinctUntilChanged()

    override suspend fun putPureBlack(enabled: Boolean) {
        wrapper.transform { it.copy(pureBlack = enabled) }
    }

    override fun getDarkAtNight(): Flow<Boolean> =
        wrapper.state.map { it.darkAtNight }.distinctUntilChanged()

    override suspend fun putDarkAtNight(enabled: Boolean) {
        wrapper.transform { it.copy(darkAtNight = enabled) }
    }

    override fun getDarkNightStart(): Flow<Int> =
        wrapper.state.map { it.darkNightStart }.distinctUntilChanged()

    override suspend fun putDarkNightStart(minutes: Int) {
        wrapper.transform { it.copy(darkNightStart = minutes) }
    }

    override fun getDarkNightEnd(): Flow<Int> =
        wrapper.state.map { it.darkNightEnd }.distinctUntilChanged()

    override suspend fun putDarkNightEnd(minutes: Int) {
        wrapper.transform { it.copy(darkNightEnd = minutes) }
    }

    override fun getAccentFollowsCover(): Flow<Boolean> =
        wrapper.state.map { it.accentFollowsCover }.distinctUntilChanged()

    override suspend fun putAccentFollowsCover(enabled: Boolean) {
        wrapper.transform { it.copy(accentFollowsCover = enabled) }
    }

    override fun getTextScale(): Flow<Float> =
        wrapper.state.map { it.textScale }.distinctUntilChanged()

    override suspend fun putTextScale(scale: Float) {
        wrapper.transform { it.copy(textScale = scale) }
    }

    override fun getTitleFont(): Flow<TitleFont> =
        wrapper.state.map { it.titleFont }.distinctUntilChanged()

    override suspend fun putTitleFont(font: TitleFont) {
        wrapper.transform { it.copy(titleFont = font) }
    }

    override fun getUnreadBadgeStyle(): Flow<UnreadBadgeStyle> =
        wrapper.state.map { it.unreadBadgeStyle }.distinctUntilChanged()

    override suspend fun putUnreadBadgeStyle(style: UnreadBadgeStyle) {
        wrapper.transform { it.copy(unreadBadgeStyle = style) }
    }

    override fun getUnreadBadgeAtStart(): Flow<Boolean> =
        wrapper.state.map { it.unreadBadgeAtStart }.distinctUntilChanged()

    override suspend fun putUnreadBadgeAtStart(atStart: Boolean) {
        wrapper.transform { it.copy(unreadBadgeAtStart = atStart) }
    }

    override fun getSeriesListLayout(): Flow<BooksLayout> =
        wrapper.state.map { it.seriesListLayout }.distinctUntilChanged()

    override suspend fun putSeriesListLayout(layout: BooksLayout) {
        wrapper.transform { it.copy(seriesListLayout = layout) }
    }

    override fun getCompactUi(): Flow<Boolean> =
        wrapper.state.map { it.compactUi }.distinctUntilChanged()

    override suspend fun putCompactUi(enabled: Boolean) {
        wrapper.transform { it.copy(compactUi = enabled) }
    }

    override fun getAppIcon(): Flow<AppIcon> =
        wrapper.state.map { it.appIcon }.distinctUntilChanged()

    override suspend fun putAppIcon(icon: AppIcon) {
        wrapper.transform { it.copy(appIcon = icon) }
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

    override fun getAniListLinkSuggestionsEnabled(): Flow<Boolean> =
        wrapper.state.map { it.aniListLinkSuggestionsEnabled }.distinctUntilChanged()

    override suspend fun putAniListLinkSuggestionsEnabled(enabled: Boolean) {
        wrapper.transform { it.copy(aniListLinkSuggestionsEnabled = enabled) }
    }

    override fun getShareLinksViaKomga(): Flow<Boolean> =
        wrapper.state.map { it.shareLinksViaKomga }.distinctUntilChanged()

    override suspend fun putShareLinksViaKomga(enabled: Boolean) {
        wrapper.transform { it.copy(shareLinksViaKomga = enabled) }
    }

    override fun getChapterSeriesFilter(): Flow<ChapterSeriesFilter> =
        wrapper.state.map { it.chapterSeriesFilter }.distinctUntilChanged()

    override suspend fun putChapterSeriesFilter(filter: ChapterSeriesFilter) {
        wrapper.transform { it.copy(chapterSeriesFilter = filter) }
    }

    override fun getUiLanguage(): Flow<String> =
        wrapper.state.map { it.uiLanguage }.distinctUntilChanged()

    override suspend fun putUiLanguage(language: String) {
        wrapper.transform { it.copy(uiLanguage = language) }
    }

    override fun getAuthorRolesFilterEnabled(): Flow<Boolean> =
        wrapper.state.map { it.authorRolesFilterEnabled }.distinctUntilChanged()

    override suspend fun putAuthorRolesFilterEnabled(enabled: Boolean) {
        wrapper.transform { it.copy(authorRolesFilterEnabled = enabled) }
    }

    override fun getHiddenAuthorRoles(): Flow<Set<String>> =
        wrapper.state.map { it.hiddenAuthorRoles }.distinctUntilChanged()

    override suspend fun putHiddenAuthorRoles(roles: Set<String>) {
        wrapper.transform { it.copy(hiddenAuthorRoles = roles) }
    }

    override fun getShowLanguageOnCovers(): Flow<Boolean> =
        wrapper.state.map { it.showLanguageOnCovers }.distinctUntilChanged()

    override suspend fun putShowLanguageOnCovers(enabled: Boolean) {
        wrapper.transform { it.copy(showLanguageOnCovers = enabled) }
    }

    override fun getLanguageBadgeScale(): Flow<Float> =
        wrapper.state.map { it.languageBadgeScale }.distinctUntilChanged()

    override suspend fun putLanguageBadgeScale(scale: Float) {
        wrapper.transform { it.copy(languageBadgeScale = scale) }
    }

    override fun getLanguageBadgeAtBottom(): Flow<Boolean> =
        wrapper.state.map { it.languageBadgeAtBottom }.distinctUntilChanged()

    override suspend fun putLanguageBadgeAtBottom(atBottom: Boolean) {
        wrapper.transform { it.copy(languageBadgeAtBottom = atBottom) }
    }

    override fun getShowCompleteSeriesBadge(): Flow<Boolean> =
        wrapper.state.map { it.showCompleteSeriesBadge }.distinctUntilChanged()

    override suspend fun putShowCompleteSeriesBadge(enabled: Boolean) {
        wrapper.transform { it.copy(showCompleteSeriesBadge = enabled) }
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

    override fun getNextReleasesInBottomNav(): Flow<Boolean> =
        wrapper.state.map { it.nextReleasesInBottomNav }.distinctUntilChanged()

    override suspend fun putNextReleasesInBottomNav(enabled: Boolean) {
        wrapper.transform { it.copy(nextReleasesInBottomNav = enabled) }
    }

    override fun getLastSeenReleaseNotesVersion(): Flow<String?> =
        wrapper.state.map { it.lastSeenReleaseNotesVersion }.distinctUntilChanged()

    override suspend fun putLastSeenReleaseNotesVersion(version: String) {
        wrapper.transform { it.copy(lastSeenReleaseNotesVersion = version) }
    }

    override fun getAutobackupEnabled(): Flow<Boolean> =
        wrapper.state.map { it.autobackupEnabled }.distinctUntilChanged()

    override suspend fun putAutobackupEnabled(enabled: Boolean) {
        wrapper.transform { it.copy(autobackupEnabled = enabled) }
    }

    override fun getAutobackupFolderUri(): Flow<String?> =
        wrapper.state.map { it.autobackupFolderUri }.distinctUntilChanged()

    override suspend fun putAutobackupFolderUri(uri: String?) {
        wrapper.transform { it.copy(autobackupFolderUri = uri) }
    }

    override fun getAutobackupFrequency(): Flow<AutobackupFrequency> =
        wrapper.state.map { it.autobackupFrequency }.distinctUntilChanged()

    override suspend fun putAutobackupFrequency(frequency: AutobackupFrequency) {
        wrapper.transform { it.copy(autobackupFrequency = frequency) }
    }

    override fun getAutobackupMaxKeep(): Flow<Int> =
        wrapper.state.map { it.autobackupMaxKeep }.distinctUntilChanged()

    override suspend fun putAutobackupMaxKeep(maxKeep: Int) {
        wrapper.transform { it.copy(autobackupMaxKeep = maxKeep.coerceIn(1, 10)) }
    }

    override fun getAutobackupLastSuccessAt(): Flow<Instant?> =
        wrapper.state.map { it.autobackupLastSuccessAt }.distinctUntilChanged()

    override suspend fun putAutobackupLastSuccessAt(timestamp: Instant?) {
        wrapper.transform { it.copy(autobackupLastSuccessAt = timestamp) }
    }

    override fun getAutobackupLastFailureAt(): Flow<Instant?> =
        wrapper.state.map { it.autobackupLastFailureAt }.distinctUntilChanged()

    override suspend fun putAutobackupLastFailure(timestamp: Instant?, message: String?) {
        wrapper.transform {
            it.copy(
                autobackupLastFailureAt = timestamp,
                autobackupLastFailureMessage = message,
            )
        }
    }

    override fun getAutobackupLastFailureMessage(): Flow<String?> =
        wrapper.state.map { it.autobackupLastFailureMessage }.distinctUntilChanged()
}
