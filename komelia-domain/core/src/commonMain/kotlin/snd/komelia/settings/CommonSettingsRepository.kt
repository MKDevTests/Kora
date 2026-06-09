package snd.komelia.settings

import kotlinx.coroutines.flow.Flow
import snd.komelia.settings.model.AppTheme
import snd.komelia.settings.model.AutobackupFrequency
import snd.komelia.settings.model.BooksLayout
import snd.komelia.settings.model.StartupScreen
import snd.komga.client.library.KomgaLibraryId
import snd.komelia.updates.AppVersion
import kotlin.time.Instant

interface CommonSettingsRepository {
    fun getServerUrl(): Flow<String>
    suspend fun putServerUrl(url: String)

    /**
     * Alternate URLs that reach the same Komga server as [getServerUrl] (e.g. a
     * LAN IP and a Tailscale address). The active URL is the one returned by
     * [getServerUrl]; these are the spares the user can switch to. They share
     * one server profile, so stats / ratings / links stay unified.
     */
    fun getAlternateServerUrls(): Flow<List<String>>
    suspend fun putAlternateServerUrls(urls: List<String>)

    /** Experimental: per-library Genre tab grouping series by kora:genre:* tags. */
    fun getExperimentalGenreTab(): Flow<Boolean>
    suspend fun putExperimentalGenreTab(enabled: Boolean)

    fun getCardWidth(): Flow<Int>
    suspend fun putCardWidth(cardWidth: Int)

    fun getCurrentUser(): Flow<String>
    suspend fun putCurrentUser(username: String)

    fun getSeriesPageLoadSize(): Flow<Int>
    suspend fun putSeriesPageLoadSize(size: Int)

    fun getBookPageLoadSize(): Flow<Int>
    suspend fun putBookPageLoadSize(size: Int)

    fun getBookListLayout(): Flow<BooksLayout>
    suspend fun putBookListLayout(layout: BooksLayout)

    fun getCheckForUpdatesOnStartup(): Flow<Boolean>
    suspend fun putCheckForUpdatesOnStartup(check: Boolean)

    fun getLastUpdateCheckTimestamp(): Flow<Instant?>
    suspend fun putLastUpdateCheckTimestamp(timestamp: Instant)

    fun getLastCheckedReleaseVersion(): Flow<AppVersion?>
    suspend fun putLastCheckedReleaseVersion(version: AppVersion)

    fun getDismissedVersion(): Flow<AppVersion?>
    suspend fun putDismissedVersion(version: AppVersion)

    fun getAppTheme(): Flow<AppTheme>
    suspend fun putAppTheme(theme: AppTheme)

    fun getNavBarColor(): Flow<Long?>
    suspend fun putNavBarColor(color: Long?)

    fun getAccentColor(): Flow<Long?>
    suspend fun putAccentColor(color: Long?)

    fun getUseNewLibraryUI(): Flow<Boolean>
    suspend fun putUseNewLibraryUI(enabled: Boolean)

    fun getCardLayoutBelow(): Flow<Boolean>
    suspend fun putCardLayoutBelow(enabled: Boolean)

    fun getImmersiveColorEnabled(): Flow<Boolean>
    suspend fun putImmersiveColorEnabled(enabled: Boolean)

    fun getImmersiveColorAlpha(): Flow<Float>
    suspend fun putImmersiveColorAlpha(alpha: Float)

    fun getShowImmersiveNavBar(): Flow<Boolean>
    suspend fun putShowImmersiveNavBar(enabled: Boolean)

    fun getLastSelectedLibraryId(): Flow<KomgaLibraryId?>
    suspend fun putLastSelectedLibraryId(libraryId: KomgaLibraryId?)

    fun getHideParenthesesInNames(): Flow<Boolean>
    suspend fun putHideParenthesesInNames(hide: Boolean)

    fun getLockScreenRotation(): Flow<Boolean>
    suspend fun putLockScreenRotation(locked: Boolean)

    fun getKeepReaderScreenOn(): Flow<Boolean>
    suspend fun putKeepReaderScreenOn(enabled: Boolean)

    fun getCardLayoutOverlayBackground(): Flow<Boolean>
    suspend fun putCardLayoutOverlayBackground(enabled: Boolean)

    fun getUseNewLibraryUI2(): Flow<Boolean>
    suspend fun putUseNewLibraryUI2(enabled: Boolean)

    fun getShowContinueReading(): Flow<Boolean>
    suspend fun putShowContinueReading(enabled: Boolean)

    fun getUseImmersiveMorphingCover(): Flow<Boolean>
    suspend fun putUseImmersiveMorphingCover(enabled: Boolean)

    fun getCardWidthScale(): Flow<Float>
    suspend fun putCardWidthScale(scale: Float)

    fun getCardHeightScale(): Flow<Float>
    suspend fun putCardHeightScale(scale: Float)

    fun getCardSpacingBelow(): Flow<Float>
    suspend fun putCardSpacingBelow(spacing: Float)

    fun getCardShadowLevel(): Flow<Float>
    suspend fun putCardShadowLevel(level: Float)

    fun getCardCornerRadius(): Flow<Float>
    suspend fun putCardCornerRadius(radius: Float)

    fun getFloatingNavigationBar(): Flow<Boolean>
    suspend fun putFloatingNavigationBar(enabled: Boolean)

    fun getLastHighlightColor(): Flow<Int>
    suspend fun putLastHighlightColor(color: Int)

    fun getSearchFuzzyEnabled(): Flow<Boolean>
    suspend fun putSearchFuzzyEnabled(enabled: Boolean)

    /**
     * Opt-in for AniList online link suggestions on the series Links tab.
     * Off by default — sends series titles to a third party.
     */
    fun getAniListLinkSuggestionsEnabled(): Flow<Boolean>
    suspend fun putAniListLinkSuggestionsEnabled(enabled: Boolean)

    /**
     * Opt-in for sharing typed series relations via the Komga `links` field
     * (read for all, write for admins). Off by default → purely local links.
     */
    fun getShareLinksViaKomga(): Flow<Boolean>
    suspend fun putShareLinksViaKomga(enabled: Boolean)

    /** Optional FR/EN language pill on series covers, with size + position. */
    fun getShowLanguageOnCovers(): Flow<Boolean>
    suspend fun putShowLanguageOnCovers(enabled: Boolean)
    fun getLanguageBadgeScale(): Flow<Float>
    suspend fun putLanguageBadgeScale(scale: Float)
    fun getLanguageBadgeAtBottom(): Flow<Boolean>
    suspend fun putLanguageBadgeAtBottom(atBottom: Boolean)

    /**
     * Whether the big page title at the top of Home/Library screens is a
     * dropdown library switcher. When false the title is plain text and
     * users use the side drawer (☰) for library switching.
     */
    fun getLibraryDropdownInTitle(): Flow<Boolean>
    suspend fun putLibraryDropdownInTitle(enabled: Boolean)

    /** Which screen the app navigates to on cold start. */
    fun getStartupScreen(): Flow<StartupScreen>
    suspend fun putStartupScreen(screen: StartupScreen)

    /**
     * Master switch for the Reading Stats feature. When false, the stats
     * page is unreachable, the Home card is hidden, and the completion
     * event tracker stops logging.
     */
    fun getStatsEnabled(): Flow<Boolean>
    suspend fun putStatsEnabled(enabled: Boolean)

    /**
     * Whether the Stats page should be reachable from a dedicated entry
     * in the bottom navigation bar. Independent from [getStatsEnabled].
     */
    fun getStatsInBottomNav(): Flow<Boolean>
    suspend fun putStatsInBottomNav(enabled: Boolean)

    /**
     * App version (e.g. "1.0.3") for which the user has already
     * acknowledged the "What's new" release-notes modal. Null means
     * never seen — the modal will show on the next launch.
     */
    fun getLastSeenReleaseNotesVersion(): Flow<String?>
    suspend fun putLastSeenReleaseNotesVersion(version: String)

    fun getAutobackupEnabled(): Flow<Boolean>
    suspend fun putAutobackupEnabled(enabled: Boolean)

    fun getAutobackupFolderUri(): Flow<String?>
    suspend fun putAutobackupFolderUri(uri: String?)

    fun getAutobackupFrequency(): Flow<AutobackupFrequency>
    suspend fun putAutobackupFrequency(frequency: AutobackupFrequency)

    fun getAutobackupMaxKeep(): Flow<Int>
    suspend fun putAutobackupMaxKeep(maxKeep: Int)

    fun getAutobackupLastSuccessAt(): Flow<Instant?>
    suspend fun putAutobackupLastSuccessAt(timestamp: Instant?)

    fun getAutobackupLastFailureAt(): Flow<Instant?>
    suspend fun putAutobackupLastFailure(timestamp: Instant?, message: String?)

    fun getAutobackupLastFailureMessage(): Flow<String?>
}
