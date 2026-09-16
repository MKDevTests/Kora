package snd.komelia.ui.settings.appearance

import snd.komelia.settings.model.TitleFont
import snd.komelia.settings.model.UnreadBadgeStyle
import snd.komelia.settings.model.AppIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.LocalCardHeightScale
import snd.komelia.ui.LocalCardSpacingBelow
import snd.komelia.ui.LocalCardWidthScale
import snd.komelia.ui.LocalCardShadowLevel
import snd.komelia.ui.LocalCardCornerRadius
import snd.komelia.ui.LocalCardLayoutBelow
import snd.komelia.ui.LocalCardLayoutOverlayBackground
import snd.komelia.ui.LocalHideParenthesesInNames
import snd.komelia.ui.LocalLanguageBadgeAtBottom
import snd.komelia.ui.LocalLanguageBadgeScale
import snd.komelia.ui.LocalShowLanguageOnCovers
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.common.authorRoleLabel
import snd.komelia.ui.common.authorRolesOrder
import snd.komelia.ui.common.cards.LibraryItemCard
import snd.komelia.ui.common.components.AppSlider
import snd.komelia.ui.common.components.AppSliderDefaults
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.common.components.SwitchWithLabel
import snd.komelia.ui.platform.cursorForHand
import kotlin.math.roundToInt

@Composable
fun AppearanceSettingsContent(
    cardWidth: Dp,
    onCardWidthChange: (Dp) -> Unit,
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    darkAtNight: Boolean,
    onDarkAtNightChange: (Boolean) -> Unit,
    darkNightStart: Int,
    onDarkNightStartChange: (Int) -> Unit,
    darkNightEnd: Int,
    onDarkNightEndChange: (Int) -> Unit,
    pureBlack: Boolean,
    onPureBlackChange: (Boolean) -> Unit,
    paletteSeed: Color?,
    onPaletteSeedChange: (Color?) -> Unit,
    accentFollowsCover: Boolean,
    onAccentFollowsCoverChange: (Boolean) -> Unit,
    textScale: Float,
    onTextScaleChange: (Float) -> Unit,
    titleFont: TitleFont,
    onTitleFontChange: (TitleFont) -> Unit,
    unreadBadgeStyle: UnreadBadgeStyle,
    onUnreadBadgeStyleChange: (UnreadBadgeStyle) -> Unit,
    unreadBadgeAtStart: Boolean,
    onUnreadBadgeAtStartChange: (Boolean) -> Unit,
    compactUi: Boolean,
    onCompactUiChange: (Boolean) -> Unit,
    appIcon: AppIcon,
    onAppIconChange: (AppIcon) -> Unit,
    useNewLibraryUI: Boolean,
    onUseNewLibraryUIChange: (Boolean) -> Unit,
    cardLayoutBelow: Boolean,
    onCardLayoutBelowChange: (Boolean) -> Unit,
    immersiveColorEnabled: Boolean,
    onImmersiveColorEnabledChange: (Boolean) -> Unit,
    immersiveColorAlpha: Float,
    onImmersiveColorAlphaChange: (Float) -> Unit,
    showImmersiveNavBar: Boolean,
    onShowImmersiveNavBarChange: (Boolean) -> Unit,
    hideParenthesesInNames: Boolean,
    onHideParenthesesInNamesChange: (Boolean) -> Unit,
    authorRolesFilterEnabled: Boolean,
    onAuthorRolesFilterEnabledChange: (Boolean) -> Unit,
    hiddenAuthorRoles: Set<String>,
    onAuthorRoleVisibilityChange: (String, Boolean) -> Unit,
    showLanguageOnCovers: Boolean,
    onShowLanguageOnCoversChange: (Boolean) -> Unit,
    languageBadgeScale: Float,
    onLanguageBadgeScaleChange: (Float) -> Unit,
    languageBadgeAtBottom: Boolean,
    onLanguageBadgeAtBottomChange: (Boolean) -> Unit,
    showCompleteSeriesBadge: Boolean,
    onShowCompleteSeriesBadgeChange: (Boolean) -> Unit,
    lockScreenRotation: Boolean,
    onLockScreenRotationChange: (Boolean) -> Unit,
    cardLayoutOverlayBackground: Boolean,
    onCardLayoutOverlayBackgroundChange: (Boolean) -> Unit,
    useNewLibraryUI2: Boolean,
    onUseNewLibraryUI2Change: (Boolean) -> Unit,
    useFloatingNavigationBar: Boolean,
    onUseFloatingNavigationBarChange: (Boolean) -> Unit,
    useImmersiveMorphingCover: Boolean,
    onUseImmersiveMorphingCoverChange: (Boolean) -> Unit,
    uiLanguage: snd.komelia.ui.i18n.AppLanguage,
    onUiLanguageChange: (snd.komelia.ui.i18n.AppLanguage) -> Unit,
    cardWidthScale: Float,
    onCardWidthScaleChange: (Float) -> Unit,
    cardHeightScale: Float,
    onCardHeightScaleChange: (Float) -> Unit,
    cardSpacingBelow: Float,
    onCardSpacingBelowChange: (Float) -> Unit,
    cardShadowLevel: Float,
    onCardShadowLevelChange: (Float) -> Unit,
    cardCornerRadius: Float,
    onCardCornerRadiusChange: (Float) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val strings = LocalStrings.current.settings
        // Sliders below used to take the accent preset; the palette is the
        // theme's primary now, which is what a null accent resolves to.
        val accentColor: Color? = null

        // a. Theme: mode, night, pure black, palette, cover accent, preview
        ThemeSettingsSection(
            currentTheme = currentTheme,
            onThemeChange = onThemeChange,
            darkAtNight = darkAtNight,
            onDarkAtNightChange = onDarkAtNightChange,
            darkNightStart = darkNightStart,
            onDarkNightStartChange = onDarkNightStartChange,
            darkNightEnd = darkNightEnd,
            onDarkNightEndChange = onDarkNightEndChange,
            pureBlack = pureBlack,
            onPureBlackChange = onPureBlackChange,
            paletteSeed = paletteSeed,
            onPaletteSeedChange = onPaletteSeedChange,
            accentFollowsCover = accentFollowsCover,
            onAccentFollowsCoverChange = onAccentFollowsCoverChange,
            textScale = textScale,
            onTextScaleChange = onTextScaleChange,
            titleFont = titleFont,
            onTitleFontChange = onTitleFontChange,
        )

        HorizontalDivider()

        // a bis. Interface language. Not driven by the device on purpose: a
        // French reader on an English phone had no way to ask for French.
        DropdownChoiceMenu(
            label = { Text(strings.language) },
            selectedOption = LabeledEntry(uiLanguage, languageLabel(uiLanguage)),
            options = snd.komelia.ui.i18n.AppLanguage.entries.map { LabeledEntry(it, languageLabel(it)) },
            onOptionChange = { onUiLanguageChange(it.value) },
            inputFieldModifier = Modifier.widthIn(min = 250.dp)
        )

        HorizontalDivider()

        // a ter. Density and launcher icon (lot F).
        SwitchWithLabel(
            checked = compactUi,
            onCheckedChange = onCompactUiChange,
            label = { Text(LocalStrings.current.ui.compactUi) },
            supportingText = { Text(LocalStrings.current.ui.compactUiDesc) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        )
        DropdownChoiceMenu(
            label = { Text(LocalStrings.current.ui.appIcon) },
            selectedOption = LabeledEntry(appIcon, appIconLabel(appIcon)),
            options = AppIcon.entries.map { LabeledEntry(it, appIconLabel(it)) },
            onOptionChange = { onAppIconChange(it.value) },
            inputFieldModifier = Modifier.widthIn(min = 250.dp)
        )
        Text(
            LocalStrings.current.ui.appIconDesc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp),
        )

        HorizontalDivider()

        // c. New Library UI
        SwitchWithLabel(
            checked = useNewLibraryUI,
            onCheckedChange = onUseNewLibraryUIChange,
            label = { Text(LocalStrings.current.ui.newLibraryUi) },
            supportingText = { Text(LocalStrings.current.ui.floatingNavBarKeepReading) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        )

        if (useNewLibraryUI) {
            HorizontalDivider()

            // d. Immersive card color
            SwitchWithLabel(
                checked = immersiveColorEnabled,
                onCheckedChange = onImmersiveColorEnabledChange,
                label = { Text(LocalStrings.current.ui.immersiveCardColor) },
                supportingText = { Text(LocalStrings.current.ui.tintTheDetailCardBackground) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            )

            if (immersiveColorEnabled) {
                Text(
                    "Tint strength: ${(immersiveColorAlpha * 100).roundToInt()}%",
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                AppSlider(
                    value = immersiveColorAlpha,
                    onValueChange = onImmersiveColorAlphaChange,
                    valueRange = 0.05f..0.30f,
                    colors = AppSliderDefaults.colors(accentColor = accentColor),
                    modifier = Modifier.cursorForHand().padding(end = 20.dp),
                )
            }

            SwitchWithLabel(
                checked = showImmersiveNavBar,
                onCheckedChange = onShowImmersiveNavBarChange,
                label = { Text(LocalStrings.current.ui.showNavigationBarInImmersive) },
                supportingText = { Text(LocalStrings.current.ui.displayTheBottomNavigationBar) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            )

            SwitchWithLabel(
                checked = useNewLibraryUI2,
                onCheckedChange = onUseNewLibraryUI2Change,
                label = { Text(LocalStrings.current.ui.newUi2) },
                supportingText = { Text(LocalStrings.current.ui.modernTopAppBarAnd) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            )

            if (useNewLibraryUI2) {
                SwitchWithLabel(
                    checked = useFloatingNavigationBar,
                    onCheckedChange = onUseFloatingNavigationBarChange,
                    label = { Text(LocalStrings.current.ui.floatingNavigationBar) },
                    supportingText = { Text(LocalStrings.current.ui.replaceTheBottomNavigationBar) },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            SwitchWithLabel(
                checked = useImmersiveMorphingCover,
                onCheckedChange = onUseImmersiveMorphingCoverChange,
                label = { Text(LocalStrings.current.ui.morphingImmersiveCover) },
                supportingText = { Text(LocalStrings.current.ui.morphingCoverImageThatFlies) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            )


        }

        HorizontalDivider()

        // e. "Cards" header
        Text(
            LocalStrings.current.ui.cards,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp)
        )

        HorizontalDivider()

        // f. Image card size
        Text(strings.imageCardSize, modifier = Modifier.padding(10.dp))
        AppSlider(
            value = cardWidth.value,
            onValueChange = { onCardWidthChange(it.roundToInt().dp) },
            steps = 24,
            valueRange = 100f..350f,
            colors = AppSliderDefaults.colors(accentColor = accentColor),
            modifier = Modifier.cursorForHand().padding(end = 20.dp),
        )

        Text("${LocalStrings.current.ui.cardWidthGap}: ${(cardWidthScale * 100).roundToInt()}%", modifier = Modifier.padding(10.dp))
        AppSlider(
            value = cardWidthScale,
            onValueChange = onCardWidthScaleChange,
            valueRange = 0.8f..1.0f,
            colors = AppSliderDefaults.colors(accentColor = accentColor),
            modifier = Modifier.cursorForHand().padding(end = 20.dp),
        )

        Text("${LocalStrings.current.ui.cardHeightGap}: ${(cardHeightScale * 100).roundToInt()}%", modifier = Modifier.padding(10.dp))
        AppSlider(
            value = cardHeightScale,
            onValueChange = onCardHeightScaleChange,
            valueRange = 0.8f..1.0f,
            colors = AppSliderDefaults.colors(accentColor = accentColor),
            modifier = Modifier.cursorForHand().padding(end = 20.dp),
        )

        Text("${LocalStrings.current.ui.cardSpacingBelow}: ${(cardSpacingBelow * 100).roundToInt()}%", modifier = Modifier.padding(10.dp))
        AppSlider(
            value = cardSpacingBelow,
            onValueChange = onCardSpacingBelowChange,
            valueRange = 0.0f..0.2f,
            colors = AppSliderDefaults.colors(accentColor = accentColor),
            modifier = Modifier.cursorForHand().padding(end = 20.dp),
        )

        Text("${strings.cardShadowLevel}: ${cardShadowLevel.roundToInt()}dp", modifier = Modifier.padding(10.dp))
        AppSlider(
            value = cardShadowLevel,
            onValueChange = onCardShadowLevelChange,
            valueRange = 0.0f..16.0f,
            colors = AppSliderDefaults.colors(accentColor = accentColor),
            modifier = Modifier.cursorForHand().padding(end = 20.dp),
        )

        Text("${strings.cardCornerRadius}: ${cardCornerRadius.roundToInt()}dp", modifier = Modifier.padding(10.dp))
        AppSlider(
            value = cardCornerRadius,
            onValueChange = onCardCornerRadiusChange,
            valueRange = 0.0f..32.0f,
            colors = AppSliderDefaults.colors(accentColor = accentColor),
            modifier = Modifier.cursorForHand().padding(end = 20.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 400.dp, max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text("${cardWidth.value}")

            CompositionLocalProvider(
                LocalCardLayoutBelow provides cardLayoutBelow,
                LocalHideParenthesesInNames provides hideParenthesesInNames,
                LocalCardLayoutOverlayBackground provides cardLayoutOverlayBackground,
                LocalCardWidthScale provides cardWidthScale,
                LocalCardHeightScale provides cardHeightScale,
                LocalCardSpacingBelow provides cardSpacingBelow,
                LocalCardShadowLevel provides cardShadowLevel,
                LocalCardCornerRadius provides cardCornerRadius,
            ) {
                LibraryItemCard(
                    modifier = Modifier.width(cardWidth),
                    title = LocalStrings.current.ui.bookTitleExample,
                    secondaryText = "Series Example",
                    image = {
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(LocalStrings.current.ui.thumbnail)
                        }
                    },
                )
            }
        }

        HorizontalDivider()

        // g. Text below card (renamed from "Card layout")
        SwitchWithLabel(
            checked = cardLayoutBelow,
            onCheckedChange = onCardLayoutBelowChange,
            label = { Text(LocalStrings.current.ui.textBelowCard) },
            supportingText = { Text(LocalStrings.current.ui.showTitleAndMetadataBelow) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        )

        HorizontalDivider()

        // h. Card layout overlay background
        SwitchWithLabel(
            checked = cardLayoutOverlayBackground,
            onCheckedChange = onCardLayoutOverlayBackgroundChange,
            label = { Text(LocalStrings.current.ui.cardLayoutOverlayBackground) },
            supportingText = { Text(LocalStrings.current.ui.showASemiTransparentBackground) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        )

        HorizontalDivider()

        // i. Hide parentheses in names
        SwitchWithLabel(
            checked = hideParenthesesInNames,
            onCheckedChange = onHideParenthesesInNamesChange,
            label = { Text(LocalStrings.current.ui.hideParenthesesInNames) },
            supportingText = { Text(LocalStrings.current.ui.removeAnythingInParenthesesWhen) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        )

        HorizontalDivider()

        // ii. Which author credits are worth the space. Komga fills up to eight
        // roles and the book page prints a row per role; off by default so
        // nothing changes for anyone who doesn't care.
        SwitchWithLabel(
            checked = authorRolesFilterEnabled,
            onCheckedChange = onAuthorRolesFilterEnabledChange,
            label = { Text(LocalStrings.current.ui.chooseWhichAuthorRolesTo) },
            supportingText = { Text(LocalStrings.current.ui.appliesToBookAndSeries) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        )
        if (authorRolesFilterEnabled) {
            authorRolesOrder.forEach { role ->
                SwitchWithLabel(
                    checked = role !in hiddenAuthorRoles,
                    onCheckedChange = { visible -> onAuthorRoleVisibilityChange(role, visible) },
                    label = { Text(authorRoleLabel(role)) },
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                )
            }
        }

        HorizontalDivider()

        // i.2 Language pill on covers
        SwitchWithLabel(
            checked = showLanguageOnCovers,
            onCheckedChange = onShowLanguageOnCoversChange,
            label = { Text(LocalStrings.current.ui.showLanguageOnCovers) },
            supportingText = { Text(LocalStrings.current.ui.smallFrEnPillOn) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        )

        if (showLanguageOnCovers) {
            Text(
                "Badge size: ${(languageBadgeScale * 100).roundToInt()}%",
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            AppSlider(
                value = languageBadgeScale,
                onValueChange = onLanguageBadgeScaleChange,
                valueRange = 0.8f..2.0f,
                colors = AppSliderDefaults.colors(accentColor = accentColor),
                modifier = Modifier.cursorForHand().padding(end = 20.dp),
            )

            SwitchWithLabel(
                checked = languageBadgeAtBottom,
                onCheckedChange = onLanguageBadgeAtBottomChange,
                label = { Text(LocalStrings.current.ui.pillAtBottomLeft) },
                supportingText = { Text(LocalStrings.current.ui.otherwiseThePillSitsAt) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        // i.2 Unread badge: count, dot or nothing, and which corner.
        DropdownChoiceMenu(
            label = { Text(LocalStrings.current.ui.unreadBadge) },
            selectedOption = LabeledEntry(unreadBadgeStyle, unreadBadgeLabel(unreadBadgeStyle)),
            options = UnreadBadgeStyle.entries.map { LabeledEntry(it, unreadBadgeLabel(it)) },
            onOptionChange = { onUnreadBadgeStyleChange(it.value) },
            inputFieldModifier = Modifier.widthIn(min = 250.dp)
        )
        if (unreadBadgeStyle != UnreadBadgeStyle.NONE) {
            SwitchWithLabel(
                checked = unreadBadgeAtStart,
                onCheckedChange = onUnreadBadgeAtStartChange,
                label = { Text(LocalStrings.current.ui.unreadBadgeAtStart) },
                supportingText = { Text(LocalStrings.current.ui.unreadBadgeAtStartDesc) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        // i.3 Complete series badge
        SwitchWithLabel(
            checked = showCompleteSeriesBadge,
            onCheckedChange = onShowCompleteSeriesBadgeChange,
            label = { Text(LocalStrings.current.ui.highlightCompleteSeries) },
            supportingText = { Text(LocalStrings.current.ui.recolorsTheTopRightBadge) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        )

        HorizontalDivider()

        // j. Lock screen rotation
        SwitchWithLabel(
            checked = lockScreenRotation,
            onCheckedChange = onLockScreenRotationChange,
            label = { Text(LocalStrings.current.ui.lockScreenRotation) },
            supportingText = { Text(LocalStrings.current.ui.preventTheApplicationScreenFrom) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun unreadBadgeLabel(style: UnreadBadgeStyle): String {
    val strings = LocalStrings.current.ui
    return when (style) {
        UnreadBadgeStyle.COUNT -> strings.unreadBadgeCount
        UnreadBadgeStyle.DOT -> strings.unreadBadgeDot
        UnreadBadgeStyle.NONE -> strings.unreadBadgeNone
    }
}

@Composable
private fun appIconLabel(icon: AppIcon): String {
    val strings = LocalStrings.current.ui
    return when (icon) {
        AppIcon.DEFAULT -> strings.appIconDefault
        AppIcon.EMBER -> strings.paletteName("ember")
        AppIcon.FOREST -> strings.paletteName("forest")
        AppIcon.MONO -> strings.appIconMono
    }
}

@Composable
private fun languageLabel(language: snd.komelia.ui.i18n.AppLanguage) = when (language) {
    // Each language names itself: someone who put the app in a language they
    // cannot read has to find their way back out of the menu.
    snd.komelia.ui.i18n.AppLanguage.SYSTEM -> LocalStrings.current.settings.languageSystem
    snd.komelia.ui.i18n.AppLanguage.ENGLISH -> "English"
    snd.komelia.ui.i18n.AppLanguage.FRENCH -> "Français"
}
