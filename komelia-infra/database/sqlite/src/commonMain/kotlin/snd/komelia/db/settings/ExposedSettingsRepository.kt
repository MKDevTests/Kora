package snd.komelia.db.settings

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komelia.db.AppSettings
import snd.komelia.db.ExposedRepository
import snd.komelia.db.tables.AppSettingsTable
import snd.komelia.settings.model.AppTheme
import snd.komelia.settings.model.BooksLayout
import snd.komelia.updates.AppVersion
import kotlin.time.Instant

class ExposedSettingsRepository(database: Database) : ExposedRepository(database) {

    suspend fun get(): AppSettings? {
        return transaction {
            AppSettingsTable.selectAll()
                .firstOrNull()
                ?.toAppSettings()
        }
    }

    suspend fun save(settings: AppSettings) {
        transaction {
            AppSettingsTable.upsert {
                it[version] = 1
                it[username] = settings.username
                it[serverUrl] = settings.serverUrl
                it[cardWidth] = settings.cardWidth

                it[seriesPageLoadSize] = settings.seriesPageLoadSize
                it[bookPageLoadSize] = settings.bookPageLoadSize
                it[bookListLayout] = settings.bookListLayout.name
                it[appTheme] = settings.appTheme.name

                it[checkForUpdatesOnStartup] = settings.checkForUpdatesOnStartup
                it[updateLastCheckedTimestamp] = settings.updateLastCheckedTimestamp?.toString()
                it[updateLastCheckedReleaseVersion] = settings.updateLastCheckedReleaseVersion?.toString()
                it[updateDismissedVersion] = settings.updateDismissedVersion?.toString()
                it[navBarColor] = settings.navBarColor?.toString(16)
                it[accentColor] = settings.accentColor?.toString(16)
                it[useNewLibraryUI] = settings.useNewLibraryUI
                it[cardLayoutBelow] = settings.cardLayoutBelow
                it[immersiveColorEnabled] = settings.immersiveColorEnabled
                it[immersiveColorAlpha] = settings.immersiveColorAlpha
                it[lastSelectedLibraryId] = settings.lastSelectedLibraryId
                it[hideParenthesesInNames] = settings.hideParenthesesInNames
                it[keepReaderScreenOn] = settings.keepReaderScreenOn
                it[cardLayoutOverlayBackground] = settings.cardLayoutOverlayBackground
                it[showImmersiveNavBar] = settings.showImmersiveNavBar
                it[useNewLibraryUI2] = settings.useNewLibraryUI2
                it[showContinueReading] = settings.showContinueReading
                it[useImmersiveMorphingCover] = settings.useImmersiveMorphingCover
                it[cardWidthScale] = settings.cardWidthScale
                it[cardHeightScale] = settings.cardHeightScale
                it[cardSpacingBelow] = settings.cardSpacingBelow
                it[cardShadowLevel] = settings.cardShadowLevel
                it[cardCornerRadius] = settings.cardCornerRadius
                it[useFloatingNavigationBar] = settings.useFloatingNavigationBar
                it[lastHighlightColor] = settings.lastHighlightColor
                it[searchFuzzyEnabled] = settings.searchFuzzyEnabled
                it[libraryDropdownInTitle] = settings.libraryDropdownInTitle
                it[startupScreen] = settings.startupScreen.name
                it[statsEnabled] = settings.statsEnabled
                it[statsInBottomNav] = settings.statsInBottomNav
                it[lastSeenReleaseNotesVersion] = settings.lastSeenReleaseNotesVersion
            }
        }
    }

    private fun ResultRow.toAppSettings(): AppSettings {
        return AppSettings(
            username = get(AppSettingsTable.username),
            serverUrl = get(AppSettingsTable.serverUrl),
            cardWidth = get(AppSettingsTable.cardWidth),
            seriesPageLoadSize = get(AppSettingsTable.seriesPageLoadSize),
            bookPageLoadSize = get(AppSettingsTable.bookPageLoadSize),
            bookListLayout = BooksLayout.valueOf(get(AppSettingsTable.bookListLayout)),
            appTheme = AppTheme.valueOf(get(AppSettingsTable.appTheme)),
            checkForUpdatesOnStartup = get(AppSettingsTable.checkForUpdatesOnStartup),
            updateLastCheckedTimestamp = get(AppSettingsTable.updateLastCheckedTimestamp)?.let {
                runCatching { Instant.parse(it) }.getOrNull()
            },
            updateLastCheckedReleaseVersion = get(AppSettingsTable.updateLastCheckedReleaseVersion)
                ?.let { AppVersion.fromString(it) },
            updateDismissedVersion = get(AppSettingsTable.updateDismissedVersion)
                ?.let { AppVersion.fromString(it) },
            navBarColor = get(AppSettingsTable.navBarColor)?.toLong(16),
            accentColor = get(AppSettingsTable.accentColor)?.toLong(16),
            useNewLibraryUI = get(AppSettingsTable.useNewLibraryUI),
            cardLayoutBelow = get(AppSettingsTable.cardLayoutBelow),
            immersiveColorEnabled = get(AppSettingsTable.immersiveColorEnabled),
            immersiveColorAlpha = get(AppSettingsTable.immersiveColorAlpha),
            lastSelectedLibraryId = get(AppSettingsTable.lastSelectedLibraryId),
            hideParenthesesInNames = get(AppSettingsTable.hideParenthesesInNames),
            lockScreenRotation = get(AppSettingsTable.lockScreenRotation),
            keepReaderScreenOn = get(AppSettingsTable.keepReaderScreenOn),
            cardLayoutOverlayBackground = get(AppSettingsTable.cardLayoutOverlayBackground),
            showImmersiveNavBar = get(AppSettingsTable.showImmersiveNavBar),
            useNewLibraryUI2 = get(AppSettingsTable.useNewLibraryUI2),
            showContinueReading = get(AppSettingsTable.showContinueReading),
            useImmersiveMorphingCover = get(AppSettingsTable.useImmersiveMorphingCover),
            cardWidthScale = get(AppSettingsTable.cardWidthScale),
            cardHeightScale = get(AppSettingsTable.cardHeightScale),
            cardSpacingBelow = get(AppSettingsTable.cardSpacingBelow),
            cardShadowLevel = get(AppSettingsTable.cardShadowLevel),
            cardCornerRadius = get(AppSettingsTable.cardCornerRadius),
            useFloatingNavigationBar = get(AppSettingsTable.useFloatingNavigationBar),
            lastHighlightColor = get(AppSettingsTable.lastHighlightColor),
            searchFuzzyEnabled = get(AppSettingsTable.searchFuzzyEnabled),
            libraryDropdownInTitle = get(AppSettingsTable.libraryDropdownInTitle),
            startupScreen = runCatching {
                snd.komelia.settings.model.StartupScreen.valueOf(get(AppSettingsTable.startupScreen))
            }.getOrDefault(snd.komelia.settings.model.StartupScreen.HOME),
            statsEnabled = get(AppSettingsTable.statsEnabled),
            statsInBottomNav = get(AppSettingsTable.statsInBottomNav),
            lastSeenReleaseNotesVersion = get(AppSettingsTable.lastSeenReleaseNotesVersion),
        )
    }
}
