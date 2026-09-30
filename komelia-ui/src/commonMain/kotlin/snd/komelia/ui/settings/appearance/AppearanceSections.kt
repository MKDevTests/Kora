package snd.komelia.ui.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import snd.komelia.settings.model.AppIcon
import snd.komelia.settings.model.AppTheme
import snd.komelia.settings.model.TitleFont
import snd.komelia.settings.model.UnreadBadgeStyle
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalCardCornerRadius
import snd.komelia.ui.LocalCardHeightScale
import snd.komelia.ui.LocalCardLayoutBelow
import snd.komelia.ui.LocalCardLayoutOverlayBackground
import snd.komelia.ui.LocalCardShadowLevel
import snd.komelia.ui.LocalCardSpacingBelow
import snd.komelia.ui.LocalCardWidthScale
import snd.komelia.ui.LocalHideParenthesesInNames
import snd.komelia.ui.LocalLanguageBadgeAtBottom
import snd.komelia.ui.LocalLanguageBadgeScale
import snd.komelia.ui.LocalShowCompleteSeriesBadge
import snd.komelia.ui.LocalShowLanguageOnCovers
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.LocalTheme
import snd.komelia.ui.LocalUnreadBadgeAtStart
import snd.komelia.ui.LocalUnreadBadgeStyle
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.Theme
import snd.komelia.ui.common.authorRoleLabel
import snd.komelia.ui.common.authorRolesOrder
import snd.komelia.ui.common.cards.LibraryItemCard
import snd.komelia.ui.common.cards.SeriesCoverBadges
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.KoraChipDefaults
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.hslColor
import snd.komelia.ui.koraTypography
import snd.komelia.ui.settings.SettingsScreenContainer
import snd.komelia.ui.settings.components.SettingsCard
import snd.komelia.ui.settings.components.SettingsChoiceRow
import snd.komelia.ui.settings.components.SettingsNavRow
import snd.komelia.ui.settings.components.SettingsRowDivider
import snd.komelia.ui.settings.components.SettingsSliderRow
import snd.komelia.ui.settings.components.SettingsSwitchRow
import snd.komelia.ui.settings.components.SettingsTexts
import snd.komelia.ui.settings.components.settingsTint
import kotlin.math.roundToInt

/*
 * Appearance used to be one page of thirty-five settings separated by
 * dividers. It is now a hub of five pages, each named after what it
 * changes; every setting is still there, only regrouped. The pages share
 * AppSettingsViewModel: each opens its own instance, which reads the
 * current values from the repository, and the hub re-reads them when it
 * comes back so its summaries follow what was just changed.
 */
enum class AppearanceSection { THEME, TEXT, CARDS, SCREENS, NAMES }

@Composable
fun appearanceSectionTitle(section: AppearanceSection): String {
    val s = LocalStrings.current.ui
    return when (section) {
        AppearanceSection.THEME -> s.apThemeColors
        AppearanceSection.TEXT -> s.apTextLanguage
        AppearanceSection.CARDS -> s.apCoversBadges
        AppearanceSection.SCREENS -> s.apScreensBars
        AppearanceSection.NAMES -> s.apNamesAuthors
    }
}

class AppearanceSectionScreen(private val section: AppearanceSection) : Screen {
    override val key: ScreenKey = "appearance_${section.name}"

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getAppearanceViewModel() }
        LaunchedEffect(Unit) { vm.initialize() }
        val state = vm.state.collectAsState().value
        val loaded = state is LoadState.Success

        // Theme and covers keep their preview above the scroll: the point of
        // both pages is to watch it change while moving a control.
        val pinned: (@Composable () -> Unit)? = when {
            !loaded -> null
            section == AppearanceSection.THEME -> {
                { ThemePreview(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            }

            section == AppearanceSection.CARDS -> {
                { CoversPreview(vm) }
            }

            else -> null
        }

        SettingsScreenContainer(appearanceSectionTitle(section), pinned = pinned) {
            when (state) {
                is LoadState.Error -> Text("${state::class.simpleName}: ${state.exception.message}")
                LoadState.Uninitialized, LoadState.Loading -> LoadingMaxSizeIndicator()
                is LoadState.Success -> when (section) {
                    AppearanceSection.THEME -> ThemeSection(vm)
                    AppearanceSection.TEXT -> TextSection(vm)
                    AppearanceSection.CARDS -> CardsSection(vm)
                    AppearanceSection.SCREENS -> ScreensSection(vm)
                    AppearanceSection.NAMES -> NamesSection(vm)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ hub

@Composable
fun AppearanceHub(vm: AppSettingsViewModel, onOpen: (AppearanceSection) -> Unit) {
    val s = LocalStrings.current.ui
    ThemePreview()
    SettingsCard(s.apSettingsGroup) {
        val dark = LocalTheme.current.type == Theme.ThemeType.DARK
        val seed = vm.paletteSeed ?: Theme.DEFAULT_SEED
        val paletteKey = Theme.PALETTES.firstOrNull { it.second.toArgb() == seed.toArgb() }?.first
        SettingsNavRow(
            label = s.apThemeColors,
            summary = listOfNotNull(
                modeLabel(vm.currentTheme),
                paletteKey?.let { s.paletteName(it) } ?: s.paletteCustom,
                s.apAccentFollows.takeIf { vm.accentFollowsCover },
            ).joinToString(" · "),
            icon = Icons.Rounded.Palette,
            iconTint = settingsTint(Color(0xFFF472B6)),
            trailing = {
                Box(Modifier.size(18.dp).clip(CircleShape).background(Theme.primaryOf(seed, dark)))
            },
            onClick = { onOpen(AppearanceSection.THEME) },
        )
        SettingsRowDivider()
        SettingsNavRow(
            label = s.apTextLanguage,
            summary = listOf(
                languageLabel(vm.uiLanguage),
                "${(vm.textScale * 100).roundToInt()} %",
                titleFontLabel(vm.titleFont),
            ).joinToString(" · "),
            icon = Icons.Rounded.TextFields,
            iconTint = settingsTint(Color(0xFF60A5FA)),
            onClick = { onOpen(AppearanceSection.TEXT) },
        )
        SettingsRowDivider()
        SettingsNavRow(
            label = s.apCoversBadges,
            summary = listOf(
                vm.cardWidth.value.roundToInt().toString(),
                if (vm.cardLayoutBelow) s.apTitleBelow else s.apTitleOnCover,
                s.apCorners(vm.cardCornerRadius.roundToInt()),
            ).joinToString(" · "),
            icon = Icons.Rounded.ViewModule,
            iconTint = settingsTint(Color(0xFF34D399)),
            onClick = { onOpen(AppearanceSection.CARDS) },
        )
        SettingsRowDivider()
        SettingsNavRow(
            label = s.apScreensBars,
            summary = listOfNotNull(
                if (vm.useFloatingNavigationBar) s.apFloatingBar else s.apFullBar,
                s.apImmersiveTint.takeIf { vm.immersiveColorEnabled },
                if (vm.lockScreenRotation) s.apRotationLocked else s.apRotationFree,
            ).joinToString(" · "),
            icon = Icons.Rounded.Dashboard,
            iconTint = settingsTint(Color(0xFFA78BFA)),
            onClick = { onOpen(AppearanceSection.SCREENS) },
        )
        SettingsRowDivider()
        val shownRoles = authorRolesOrder.count { it !in vm.hiddenAuthorRoles }
        SettingsNavRow(
            label = s.apNamesAuthors,
            summary = listOf(
                if (vm.hideParenthesesInNames) s.apParenthesesHidden else s.apParenthesesShown,
                if (vm.authorRolesFilterEnabled) s.apRolesCount(shownRoles) else s.apAllRoles,
            ).joinToString(" · "),
            icon = Icons.Rounded.Person,
            iconTint = settingsTint(Color(0xFFFBBF24)),
            onClick = { onOpen(AppearanceSection.NAMES) },
        )
    }
}

// ------------------------------------------------------------------ Theme & colours

@Composable
private fun ThemeSection(vm: AppSettingsViewModel) {
    val s = LocalStrings.current.ui
    SettingsCard(s.apModeGroup) {
        // The three legacy values map onto the segment they mean.
        val selectedMode = when (vm.currentTheme) {
            AppTheme.DARK, AppTheme.DARKER, AppTheme.DARK_MODERN -> AppTheme.DARK
            AppTheme.LIGHT, AppTheme.LIGHT_MODERN -> AppTheme.LIGHT
            AppTheme.SYSTEM -> AppTheme.SYSTEM
        }
        SettingsChoiceRow(
            label = null,
            options = listOf(
                AppTheme.SYSTEM to s.themeModeSystem,
                AppTheme.LIGHT to s.themeModeLight,
                AppTheme.DARK to s.themeModeDark,
            ),
            selected = selectedMode,
            onSelect = vm::onAppThemeChange,
        )
        SettingsRowDivider()
        SettingsSwitchRow(s.darkAtNight, vm.darkAtNight, vm::onDarkAtNightChange, s.darkAtNightDesc)
        if (vm.darkAtNight) {
            val hours = (0..23).map { LabeledEntry(it * 60, hourLabel(it * 60)) }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            ) {
                DropdownChoiceMenu(
                    label = { Text(s.darkNightFrom) },
                    selectedOption = LabeledEntry(vm.darkNightStart, hourLabel(vm.darkNightStart)),
                    options = hours,
                    onOptionChange = { vm.onDarkNightStartChange(it.value) },
                    inputFieldModifier = Modifier.width(140.dp),
                )
                DropdownChoiceMenu(
                    label = { Text(s.darkNightTo) },
                    selectedOption = LabeledEntry(vm.darkNightEnd, hourLabel(vm.darkNightEnd)),
                    options = hours,
                    onOptionChange = { vm.onDarkNightEndChange(it.value) },
                    inputFieldModifier = Modifier.width(140.dp),
                )
            }
        }
        SettingsRowDivider()
        SettingsSwitchRow(s.pureBlack, vm.pureBlack, vm::onPureBlackChange, s.pureBlackDesc)
    }

    SettingsCard(s.apColourGroup) {
        SettingsTexts(s.palette, s.paletteDesc, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
        val effectiveSeed = vm.paletteSeed ?: Theme.DEFAULT_SEED
        // Swatches show the primary the seed becomes, not the raw seed.
        val dark = LocalTheme.current.type == Theme.ThemeType.DARK
        val isPreset = Theme.PALETTES.any { it.second.toArgb() == effectiveSeed.toArgb() }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Theme.PALETTES.forEach { (key, color) ->
                PaletteSwatch(
                    label = s.paletteName(key),
                    color = Theme.primaryOf(color, dark),
                    selected = isPreset && color.toArgb() == effectiveSeed.toArgb(),
                    onClick = { vm.onPaletteSeedChange(if (color.toArgb() == Theme.DEFAULT_SEED.toArgb()) null else color) },
                    modifier = Modifier.weight(1f),
                )
            }
            PaletteSwatch(
                label = s.paletteCustom,
                color = if (isPreset) null else Theme.primaryOf(effectiveSeed, dark),
                selected = !isPreset,
                // Starts the custom colour from the current one, so picking
                // "custom" changes nothing until a slider moves.
                onClick = { if (isPreset) vm.onPaletteSeedChange(effectiveSeed.withHueShift(0.5f)) },
                modifier = Modifier.weight(1f),
            )
        }
        if (!isPreset) {
            Box(Modifier.padding(horizontal = 6.dp)) {
                CustomSeedEditor(seed = effectiveSeed, onSeedChange = vm::onPaletteSeedChange)
            }
        }
        SettingsRowDivider()
        SettingsSwitchRow(s.accentFollowsCover, vm.accentFollowsCover, vm::onAccentFollowsCoverChange, s.accentFollowsCoverDesc)
    }

    SettingsCard(s.appIcon) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
        ) {
            AppIcon.entries.forEach { icon ->
                AppIconTile(icon, selected = vm.appIcon == icon, onClick = { vm.onAppIconChange(icon) }, Modifier.weight(1f))
            }
        }
        Text(
            s.appIconDesc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        )
    }
}

/** The launcher icon as it looks: a "K" on the colour of that variant. */
@Composable
private fun AppIconTile(icon: AppIcon, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (bg, fg) = when (icon) {
        AppIcon.DEFAULT -> Color(0xFF1B2A44) to Color(0xFF60A5FA)
        AppIcon.EMBER -> Color(0xFF3A2415) to Color(0xFFFB923C)
        AppIcon.FOREST -> Color(0xFF16301F) to Color(0xFF4ADE80)
        AppIcon.MONO -> Color(0xFF2A2A2A) to Color(0xFFE0E0E0)
    }
    val accent = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .then(if (selected) Modifier.border(2.dp, accent, RoundedCornerShape(16.dp)) else Modifier)
                .padding(if (selected) 4.dp else 0.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(bg),
        ) {
            Text("K", color = fg, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleLarge)
        }
        Text(
            appIconLabel(icon),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// ------------------------------------------------------------------ Text & language

@Composable
private fun TextSection(vm: AppSettingsViewModel) {
    val s = LocalStrings.current.ui
    val settings = LocalStrings.current.settings
    SettingsCard(settings.language) {
        // Not driven by the device on purpose: a French reader on an English
        // phone had no way to ask for French.
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            DropdownChoiceMenu(
                label = { Text(settings.language) },
                selectedOption = LabeledEntry(vm.uiLanguage, languageLabel(vm.uiLanguage)),
                options = snd.komelia.ui.i18n.AppLanguage.entries.map { LabeledEntry(it, languageLabel(it)) },
                onOptionChange = { vm.onUiLanguageChange(it.value) },
                inputFieldModifier = Modifier.fillMaxWidth(),
            )
        }
    }

    SettingsCard(s.apSizeGroup) {
        SettingsSliderRow(
            label = s.textScale,
            valueText = "${(vm.textScale * 100).roundToInt()} %",
            value = vm.textScale,
            onValueChange = vm::onTextScaleChange,
            valueRange = 0.85f..1.2f,
            steps = 6,
        )
        Text(
            s.apTextSample,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
        SettingsRowDivider()
        SettingsSwitchRow(s.compactUi, vm.compactUi, vm::onCompactUiChange, s.compactUiDesc)
    }

    SettingsCard(s.titleFont) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            listOf(
                TitleFont.SERIF to koraTypography(TitleFont.SERIF).titleLarge,
                TitleFont.SANS to koraTypography(TitleFont.SANS).titleLarge,
                TitleFont.SYSTEM to koraTypography(TitleFont.SYSTEM).titleLarge,
            ).forEach { (font, style) ->
                val selected = vm.titleFont == font
                val accent = MaterialTheme.colorScheme.primary
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerLowest)
                        .then(if (selected) Modifier.border(2.dp, accent, RoundedCornerShape(12.dp)) else Modifier)
                        .clickable { vm.onTitleFontChange(font) }
                        .padding(vertical = 12.dp),
                ) {
                    Text("Bleach", style = style.copy(fontWeight = FontWeight.Bold))
                    Text(
                        titleFontLabel(font),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Covers & badges

@Composable
private fun CardsSection(vm: AppSettingsViewModel) {
    val s = LocalStrings.current.ui
    val settings = LocalStrings.current.settings
    SettingsCard(s.apSizeShapeGroup) {
        SettingsSliderRow(
            label = settings.imageCardSize,
            valueText = vm.cardWidth.value.roundToInt().toString(),
            value = vm.cardWidth.value,
            onValueChange = { vm.onCardWidthChange(it.roundToInt().dp) },
            valueRange = 100f..350f,
            steps = 24,
        )
        SettingsSliderRow(
            label = s.cardWidthGap,
            valueText = "${(vm.cardWidthScale * 100).roundToInt()} %",
            value = vm.cardWidthScale,
            onValueChange = vm::onCardWidthScaleChange,
            valueRange = 0.8f..1.0f,
        )
        SettingsSliderRow(
            label = s.cardHeightGap,
            valueText = "${(vm.cardHeightScale * 100).roundToInt()} %",
            value = vm.cardHeightScale,
            onValueChange = vm::onCardHeightScaleChange,
            valueRange = 0.8f..1.0f,
        )
        SettingsSliderRow(
            label = s.cardSpacingBelow,
            valueText = "${(vm.cardSpacingBelow * 100).roundToInt()} %",
            value = vm.cardSpacingBelow,
            onValueChange = vm::onCardSpacingBelowChange,
            valueRange = 0.0f..0.2f,
        )
        SettingsSliderRow(
            label = settings.cardShadowLevel,
            valueText = "${vm.cardShadowLevel.roundToInt()} dp",
            value = vm.cardShadowLevel,
            onValueChange = vm::onCardShadowLevelChange,
            valueRange = 0.0f..16.0f,
        )
        SettingsSliderRow(
            label = settings.cardCornerRadius,
            valueText = "${vm.cardCornerRadius.roundToInt()} dp",
            value = vm.cardCornerRadius,
            onValueChange = vm::onCardCornerRadiusChange,
            valueRange = 0.0f..32.0f,
        )
    }

    SettingsCard(s.apTitleGroup) {
        SettingsSwitchRow(s.textBelowCard, vm.cardLayoutBelow, vm::onCardLayoutBelowChange, s.showTitleAndMetadataBelow)
        SettingsRowDivider()
        SettingsSwitchRow(
            s.cardLayoutOverlayBackground,
            vm.cardLayoutOverlayBackground,
            vm::onCardLayoutOverlayBackgroundChange,
            s.showASemiTransparentBackground,
        )
    }

    SettingsCard(s.apBadgesGroup) {
        SettingsSwitchRow(s.showLanguageOnCovers, vm.showLanguageOnCovers, vm::onShowLanguageOnCoversChange, s.smallFrEnPillOn)
        if (vm.showLanguageOnCovers) {
            SettingsSliderRow(
                label = s.badgeSize,
                valueText = "${(vm.languageBadgeScale * 100).roundToInt()} %",
                value = vm.languageBadgeScale,
                onValueChange = vm::onLanguageBadgeScaleChange,
                valueRange = 0.8f..2.0f,
            )
            SettingsSwitchRow(s.pillAtBottomLeft, vm.languageBadgeAtBottom, vm::onLanguageBadgeAtBottomChange, s.otherwiseThePillSitsAt)
        }
        SettingsRowDivider()
        SettingsChoiceRow(
            label = s.unreadBadge,
            options = UnreadBadgeStyle.entries.map { it to unreadBadgeLabel(it) },
            selected = vm.unreadBadgeStyle,
            onSelect = vm::onUnreadBadgeStyleChange,
        )
        if (vm.unreadBadgeStyle != UnreadBadgeStyle.NONE) {
            SettingsSwitchRow(s.unreadBadgeAtStart, vm.unreadBadgeAtStart, vm::onUnreadBadgeAtStartChange, s.unreadBadgeAtStartDesc)
        }
        SettingsRowDivider()
        SettingsSwitchRow(s.highlightCompleteSeries, vm.showCompleteSeriesBadge, vm::onShowCompleteSeriesBadgeChange, s.recolorsTheTopRightBadge)
    }
}

/**
 * Three sample cards drawn with the page's current values, not the saved
 * ones: every slider shows its effect before the repository write lands.
 * As many cards as fit the width at the chosen size, capped in height so a
 * 350 dp card cannot push every control off the screen.
 */
@Composable
private fun CoversPreview(vm: AppSettingsViewModel) {
    val s = LocalStrings.current.ui
    val samples = listOf(
        Triple("Cells at Work!", "EN", 3),
        Triple(s.bookTitleExample, "FR", 0),
        Triple("Blacksad", "FR", 5),
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .heightIn(max = 330.dp),
    ) {
        val gap = 14.dp
        val cardWidth = vm.cardWidth
        val fit = (((maxWidth - 24.dp + gap) / (cardWidth + gap)).toInt()).coerceIn(1, 3)
        CompositionLocalProvider(
            LocalCardLayoutBelow provides vm.cardLayoutBelow,
            LocalHideParenthesesInNames provides vm.hideParenthesesInNames,
            LocalCardLayoutOverlayBackground provides vm.cardLayoutOverlayBackground,
            LocalCardWidthScale provides vm.cardWidthScale,
            LocalCardHeightScale provides vm.cardHeightScale,
            LocalCardSpacingBelow provides vm.cardSpacingBelow,
            LocalCardShadowLevel provides vm.cardShadowLevel,
            LocalCardCornerRadius provides vm.cardCornerRadius,
            LocalShowLanguageOnCovers provides vm.showLanguageOnCovers,
            LocalLanguageBadgeScale provides vm.languageBadgeScale,
            LocalLanguageBadgeAtBottom provides vm.languageBadgeAtBottom,
            LocalShowCompleteSeriesBadge provides vm.showCompleteSeriesBadge,
            LocalUnreadBadgeStyle provides vm.unreadBadgeStyle,
            LocalUnreadBadgeAtStart provides vm.unreadBadgeAtStart,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 12.dp, start = 12.dp, end = 12.dp),
            ) {
                samples.take(fit).forEachIndexed { index, (title, language, unread) ->
                    LibraryItemCard(
                        modifier = Modifier.width(cardWidth),
                        title = title,
                        secondaryText = s.volumeNumber("${index + 1}"),
                        badges = {
                            SeriesCoverBadges(
                                languageLabel = language,
                                unreadCount = unread,
                                isComplete = index == 2,
                            )
                        },
                        image = { PreviewCover(index) },
                    )
                }
            }
        }
        Text(
            s.apLivePreview.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** A cover-like gradient: no real cover here, a sample must not depend on the server. */
@Composable
private fun PreviewCover(index: Int) {
    val hue = listOf(210f, 20f, 150f)[index % 3]
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(hslColor(hue, 0.45f, 0.42f), hslColor(hue + 30f, 0.35f, 0.18f))
                )
            )
    )
}

// ------------------------------------------------------------------ Screens & bars

@Composable
private fun ScreensSection(vm: AppSettingsViewModel) {
    val s = LocalStrings.current.ui
    // The floating bar and the detail-page options only exist on the new
    // interface; they stay visible but greyed when it is off, so nothing
    // disappears from this page depending on another switch.
    val newUi = vm.useNewLibraryUI
    SettingsCard(s.apBottomBarGroup) {
        SettingsSwitchRow(
            s.floatingNavigationBar,
            vm.useFloatingNavigationBar,
            vm::onUseFloatingNavigationBarChange,
            s.replaceTheBottomNavigationBar,
            enabled = newUi && vm.useNewLibraryUI2,
        )
        SettingsRowDivider()
        SettingsSwitchRow(
            s.showNavigationBarInImmersive,
            vm.showImmersiveNavBar,
            vm::onShowImmersiveNavBarChange,
            s.displayTheBottomNavigationBar,
            enabled = newUi,
        )
    }

    SettingsCard(s.apDetailPagesGroup) {
        SettingsSwitchRow(
            s.immersiveCardColor,
            vm.immersiveColorEnabled,
            vm::onImmersiveColorEnabledChange,
            s.tintTheDetailCardBackground,
            enabled = newUi,
        )
        if (newUi && vm.immersiveColorEnabled) {
            SettingsSliderRow(
                label = s.tintStrength,
                valueText = "${(vm.immersiveColorAlpha * 100).roundToInt()} %",
                value = vm.immersiveColorAlpha,
                onValueChange = vm::onImmersiveColorAlphaChange,
                valueRange = 0.05f..0.30f,
            )
        }
        SettingsRowDivider()
        SettingsSwitchRow(
            s.morphingImmersiveCover,
            vm.useImmersiveMorphingCover,
            vm::onUseImmersiveMorphingCoverChange,
            s.morphingCoverImageThatFlies,
            enabled = newUi,
        )
    }

    SettingsCard(s.apScreenGroup) {
        SettingsSwitchRow(
            s.lockScreenRotation,
            vm.lockScreenRotation,
            vm::onLockScreenRotationChange,
            s.preventTheApplicationScreenFrom,
            icon = Icons.Rounded.ScreenLockRotation,
            iconTint = settingsTint(Color(0xFF94A3B8)),
        )
    }

    SettingsCard(s.apLegacyGroup) {
        Text(
            s.apLegacyDesc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        SettingsSwitchRow(s.newLibraryUi, vm.useNewLibraryUI, vm::onUseNewLibraryUIChange, s.floatingNavBarKeepReading)
        SettingsRowDivider()
        SettingsSwitchRow(s.newUi2, vm.useNewLibraryUI2, vm::onUseNewLibraryUI2Change, s.modernTopAppBarAnd, enabled = newUi)
    }
}

// ------------------------------------------------------------------ Names & authors

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NamesSection(vm: AppSettingsViewModel) {
    val s = LocalStrings.current.ui
    SettingsCard(s.apNamesGroup) {
        SettingsSwitchRow(s.hideParenthesesInNames, vm.hideParenthesesInNames, vm::onHideParenthesesInNamesChange, s.removeAnythingInParenthesesWhen)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            val example = "Donjon de Naheulbeuk (Le)"
            Text(example, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp),
            )
            Text(
                if (vm.hideParenthesesInNames) "Donjon de Naheulbeuk" else example,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    // Komga fills up to eight roles and the book page prints a row per
    // role; off by default so nothing changes for anyone who doesn't care.
    SettingsCard(s.apAuthorsGroup) {
        SettingsSwitchRow(s.chooseWhichAuthorRolesTo, vm.authorRolesFilterEnabled, vm::onAuthorRolesFilterEnabledChange, s.appliesToBookAndSeries)
        if (vm.authorRolesFilterEnabled) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            ) {
                authorRolesOrder.forEach { role ->
                    val visible = role !in vm.hiddenAuthorRoles
                    FilterChip(
                        selected = visible,
                        onClick = { vm.onAuthorRoleVisibilityChange(role, !visible) },
                        label = { Text(authorRoleLabel(role)) },
                        leadingIcon = if (visible) {
                            { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = KoraChipDefaults.filterChipColors(),
                        border = KoraChipDefaults.border,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ labels

@Composable
internal fun modeLabel(theme: AppTheme): String {
    val s = LocalStrings.current.ui
    return when (theme) {
        AppTheme.DARK, AppTheme.DARKER, AppTheme.DARK_MODERN -> s.themeModeDark
        AppTheme.LIGHT, AppTheme.LIGHT_MODERN -> s.themeModeLight
        AppTheme.SYSTEM -> s.themeModeSystem
    }
}

@Composable
internal fun titleFontLabel(font: TitleFont): String {
    val s = LocalStrings.current.ui
    return when (font) {
        TitleFont.SERIF -> s.titleFontSerif
        TitleFont.SANS -> s.titleFontSans
        TitleFont.SYSTEM -> s.titleFontSystem
    }
}

@Composable
internal fun unreadBadgeLabel(style: UnreadBadgeStyle): String {
    val strings = LocalStrings.current.ui
    return when (style) {
        UnreadBadgeStyle.COUNT -> strings.unreadBadgeCount
        UnreadBadgeStyle.DOT -> strings.unreadBadgeDot
        UnreadBadgeStyle.NONE -> strings.unreadBadgeNone
    }
}

@Composable
internal fun appIconLabel(icon: AppIcon): String {
    val strings = LocalStrings.current.ui
    return when (icon) {
        AppIcon.DEFAULT -> strings.appIconDefault
        AppIcon.EMBER -> strings.paletteName("ember")
        AppIcon.FOREST -> strings.paletteName("forest")
        AppIcon.MONO -> strings.appIconMono
    }
}

@Composable
internal fun languageLabel(language: snd.komelia.ui.i18n.AppLanguage) = when (language) {
    // Each language names itself: someone who put the app in a language they
    // cannot read has to find their way back out of the menu.
    snd.komelia.ui.i18n.AppLanguage.SYSTEM -> LocalStrings.current.settings.languageSystem
    snd.komelia.ui.i18n.AppLanguage.ENGLISH -> "English"
    snd.komelia.ui.i18n.AppLanguage.FRENCH -> "Français"
}
