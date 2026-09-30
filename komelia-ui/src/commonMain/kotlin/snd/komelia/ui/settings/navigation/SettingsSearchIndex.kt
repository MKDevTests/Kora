package snd.komelia.ui.settings.navigation

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.settings.appearance.AppearanceSection
import snd.komelia.ui.settings.appearance.AppearanceSectionScreen
import snd.komelia.ui.settings.appearance.appearanceSectionTitle
import snd.komelia.ui.settings.imagereader.ImageReaderSettingsScreen

/**
 * One setting as the settings search sees it: what it is called, what it
 * does, where it lives, and the page that holds it. [keywords] are words a
 * person types that are in neither the label nor the description ("dark"
 * for the three-way mode choice).
 */
class SettingSearchEntry(
    val label: String,
    val description: String?,
    val path: String,
    val keywords: String = "",
    val open: () -> Screen,
)

/**
 * Every setting of Appearance, Navigation and the image reader, by name.
 * The menu search used to match page titles only, so "rotation" or "dark"
 * found nothing: the words were one page deeper.
 */
@Composable
fun settingsSearchIndex(): List<SettingSearchEntry> {
    val s = LocalStrings.current.ui
    val st = LocalStrings.current.settings
    val result = mutableListOf<SettingSearchEntry>()

    @Composable
    fun appearance(section: AppearanceSection, vararg items: Triple<String, String?, String>) {
        val path = "${s.appearance} › ${appearanceSectionTitle(section)}"
        items.forEach { (label, desc, keywords) ->
            result += SettingSearchEntry(label, desc, path, keywords) { AppearanceSectionScreen(section) }
        }
    }

    fun t(label: String, desc: String? = null, keywords: String = "") = Triple(label, desc, keywords)

    appearance(
        AppearanceSection.THEME,
        t(s.themeMode, null, "${s.themeModeSystem} ${s.themeModeLight} ${s.themeModeDark} theme thème"),
        t(s.darkAtNight, s.darkAtNightDesc),
        t(s.pureBlack, s.pureBlackDesc, "oled amoled"),
        t(s.palette, s.paletteDesc, "${s.paletteCustom} couleur colour color"),
        t(s.accentFollowsCover, s.accentFollowsCoverDesc),
        t(s.appIcon, s.appIconDesc, "launcher icône icon"),
    )
    appearance(
        AppearanceSection.TEXT,
        t(st.language, null, "english français langue language"),
        t(s.textScale, null, "police font taille size"),
        t(s.compactUi, s.compactUiDesc),
        t(s.titleFont, null, "${s.titleFontSerif} ${s.titleFontSans} police font"),
    )
    appearance(
        AppearanceSection.CARDS,
        t(st.imageCardSize, null, "vignette cover couverture"),
        t(s.cardWidthGap, null, "vignette card"),
        t(s.cardHeightGap, null, "vignette card"),
        t(s.cardSpacingBelow, null, "vignette card"),
        t(st.cardShadowLevel, null, "vignette card"),
        t(st.cardCornerRadius, null, "vignette card"),
        t(s.textBelowCard, s.showTitleAndMetadataBelow),
        t(s.cardLayoutOverlayBackground, s.showASemiTransparentBackground),
        t(s.showLanguageOnCovers, s.smallFrEnPillOn, "badge"),
        t(s.badgeSize, null, "pastille badge langue language"),
        t(s.pillAtBottomLeft, s.otherwiseThePillSitsAt, "badge"),
        t(s.unreadBadge, null, "${s.unreadBadgeCount} ${s.unreadBadgeDot} non lu unread"),
        t(s.unreadBadgeAtStart, s.unreadBadgeAtStartDesc),
        t(s.highlightCompleteSeries, s.recolorsTheTopRightBadge),
    )
    appearance(
        AppearanceSection.SCREENS,
        t(s.floatingNavigationBar, s.replaceTheBottomNavigationBar),
        t(s.showNavigationBarInImmersive, s.displayTheBottomNavigationBar),
        t(s.immersiveCardColor, s.tintTheDetailCardBackground),
        t(s.tintStrength, null, "teinte tint"),
        t(s.morphingImmersiveCover, s.morphingCoverImageThatFlies),
        t(s.lockScreenRotation, s.preventTheApplicationScreenFrom, "portrait paysage landscape"),
        t(s.newLibraryUi, s.floatingNavBarKeepReading),
        t(s.newUi2, s.modernTopAppBarAnd),
    )
    appearance(
        AppearanceSection.NAMES,
        t(s.hideParenthesesInNames, s.removeAnythingInParenthesesWhen),
        t(s.chooseWhichAuthorRolesTo, s.appliesToBookAndSeries, "auteur author scénario dessin writer penciller"),
    )

    listOf(
        t(s.librarySwitcherInPageTitle),
        t(s.startupScreen, null, "démarrage startup accueil home"),
        t(s.readingStats, null, "statistiques stats"),
        t(s.showStatsInBottomNavigation),
        t(s.showUpcomingReleasesInBottom, null, "sorties releases"),
        t(s.anilistLinkSuggestionsOnline),
        t(s.shareSeriesLinksViaKomga),
    ).forEach { (label, desc, keywords) ->
        result += SettingSearchEntry(label, desc, s.navigation, keywords) { NavigationSettingsScreen() }
    }

    listOf(
        t(s.loadSmallPreviewsWhenDragging, s.canBeSlowForHigh),
        t(s.volumeKeysNavigation),
        t(s.keepScreenOnWhileReading, null, "veille sleep écran screen"),
        t(s.autoDetectReadingDirection, s.useSeriesMetadataManualFlips, "manga sens"),
        t(s.autoSkipBlankPages, s.whenCropBordersIsOn),
        t(s.autoDetectWebtoon, s.ifTheFirst3Pages),
        t(s.webtoonSmartScroll, s.inTheContinuousReaderA),
        t(s.invertSpeechBubbles, s.blackBubbleWhiteTextArtwork, "bulles bubbles"),
        t(s.stopAtEndOfBook, s.pauseAtTheLastPage),
        t(s.clearImageCache, null, "cache"),
    ).forEach { (label, desc, keywords) ->
        result += SettingSearchEntry(label, desc, s.imageReader, keywords) { ImageReaderSettingsScreen() }
    }
    return result
}
