package snd.komelia.settings

import kotlinx.coroutines.flow.Flow
import snd.komelia.settings.model.AppTheme
import snd.komelia.settings.model.BooksLayout
import snd.komga.client.library.KomgaLibraryId
import snd.komelia.updates.AppVersion
import kotlin.time.Instant

interface CommonSettingsRepository {
    fun getServerUrl(): Flow<String>
    suspend fun putServerUrl(url: String)

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
}
