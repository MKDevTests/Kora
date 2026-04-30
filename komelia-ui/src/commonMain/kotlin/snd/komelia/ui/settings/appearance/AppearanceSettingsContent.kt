package snd.komelia.ui.settings.appearance

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
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.common.cards.LibraryItemCard
import snd.komelia.ui.common.components.AppSlider
import snd.komelia.ui.common.components.AppSliderDefaults
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.common.components.SwitchWithLabel
import snd.komelia.ui.platform.cursorForHand
import kotlin.math.roundToInt

private val accentPresets: List<Pair<Color?, String>> = listOf(
    null to "Auto",
    Color(0xFF800020.toInt()) to "Burgundy",
    Color(0xFFE57373.toInt()) to "Muted Red",
    Color(0xFFC47890.toInt()) to "Muted Rose",
    Color(0xFFCC8855.toInt()) to "Muted Amber",
    Color(0xFF8A9B5A.toInt()) to "Muted Olive",
    Color(0xFF5A8F6E.toInt()) to "Muted Sage",
    Color(0xFF4D8D8D.toInt()) to "Muted Teal",
    Color(0xFF5783D4.toInt()) to "Secondary Blue",
    Color(0xFF8678B8.toInt()) to "Muted Violet",
    Color(0xFF201F23.toInt()) to "Toolbar (Dark)",
    Color(0xFFE1E1E1.toInt()) to "Toolbar (Light)",
    Color(0xFF2D3436.toInt()) to "Charcoal",
    Color(0xFF1A1A2E.toInt()) to "Navy",
    Color(0xFF0D3B46.toInt()) to "D.Teal",
    Color(0xFF1B4332.toInt()) to "Forest",
    Color(0xFF3D1A78.toInt()) to "Violet",
    Color(0xFF3B82F6.toInt()) to "Blue",
    Color(0xFF14B8A6.toInt()) to "Teal",
    Color(0xFF8B5CF6.toInt()) to "Purple",
    Color(0xFF6A1CF6.toInt()) to "Modern Purple",
    Color(0xFFBA9EFF.toInt()) to "Modern Lavender",
    Color(0xFF9720AB.toInt()) to "Modern Magenta",
    Color(0xFFEC4899.toInt()) to "Pink",
    Color(0xFFF97316.toInt()) to "Orange",
    Color(0xFF22C55E.toInt()) to "Green",
)

@Composable
fun AppearanceSettingsContent(
    cardWidth: Dp,
    onCardWidthChange: (Dp) -> Unit,
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    accentColor: Color?,
    onAccentColorChange: (Color?) -> Unit,
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

        // a. App theme
        DropdownChoiceMenu(
            label = { Text(strings.appTheme) },
            selectedOption = LabeledEntry(currentTheme, strings.forAppTheme(currentTheme)),
            options = AppTheme.entries.map { LabeledEntry(it, strings.forAppTheme(it)) },
            onOptionChange = { onThemeChange(it.value) },
            inputFieldModifier = Modifier.widthIn(min = 250.dp)
        )

        HorizontalDivider()

        // b. Accent color (always shown)
        DropdownChoiceMenu(
            label = { Text("Accent Color (chips & tabs)") },
            selectedOption = accentPresets.find { it.first == accentColor }
                ?.let { LabeledEntry(it.first, it.second) },
            options = accentPresets.map { LabeledEntry(it.first, it.second) },
            onOptionChange = { onAccentColorChange(it.value) },
            inputFieldModifier = Modifier.widthIn(min = 250.dp),
            selectedOptionContent = { ColorLabel(it) },
            optionContent = { ColorLabel(it) }
        )

        HorizontalDivider()

        // c. New Library UI
        SwitchWithLabel(
            checked = useNewLibraryUI,
            onCheckedChange = onUseNewLibraryUIChange,
            label = { Text("New library UI") },
            supportingText = { Text("Floating nav bar, Keep Reading panel, and pill-shaped tabs") },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        )

        if (useNewLibraryUI) {
            HorizontalDivider()

            // d. Immersive card color
            SwitchWithLabel(
                checked = immersiveColorEnabled,
                onCheckedChange = onImmersiveColorEnabledChange,
                label = { Text("Immersive card color") },
                supportingText = { Text("Tint the detail card background with the cover's dominant color") },
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
                label = { Text("Show navigation bar in immersive screens") },
                supportingText = { Text("Display the bottom navigation bar on series, book, and oneshot screens") },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            )

            SwitchWithLabel(
                checked = useNewLibraryUI2,
                onCheckedChange = onUseNewLibraryUI2Change,
                label = { Text("New UI 2") },
                supportingText = { Text("Modern top app bar and updated item cards") },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            )

            if (useNewLibraryUI2) {
                SwitchWithLabel(
                    checked = useFloatingNavigationBar,
                    onCheckedChange = onUseFloatingNavigationBarChange,
                    label = { Text("Floating navigation bar") },
                    supportingText = { Text("Replace the bottom navigation bar with a floating toolbar") },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            SwitchWithLabel(
                checked = useImmersiveMorphingCover,
                onCheckedChange = onUseImmersiveMorphingCoverChange,
                label = { Text("Morphing Immersive Cover") },
                supportingText = { Text("Morphing cover image that flies to thumbnail on expand") },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            )


        }

        HorizontalDivider()

        // e. "Cards" header
        Text(
            "Cards",
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

        Text("Width Gap: ${(cardWidthScale * 100).roundToInt()}%", modifier = Modifier.padding(10.dp))
        AppSlider(
            value = cardWidthScale,
            onValueChange = onCardWidthScaleChange,
            valueRange = 0.8f..1.0f,
            colors = AppSliderDefaults.colors(accentColor = accentColor),
            modifier = Modifier.cursorForHand().padding(end = 20.dp),
        )

        Text("Height Gap: ${(cardHeightScale * 100).roundToInt()}%", modifier = Modifier.padding(10.dp))
        AppSlider(
            value = cardHeightScale,
            onValueChange = onCardHeightScaleChange,
            valueRange = 0.8f..1.0f,
            colors = AppSliderDefaults.colors(accentColor = accentColor),
            modifier = Modifier.cursorForHand().padding(end = 20.dp),
        )

        Text("Spacing Below: ${(cardSpacingBelow * 100).roundToInt()}%", modifier = Modifier.padding(10.dp))
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
                    title = "Book Title Example",
                    secondaryText = "Series Example",
                    image = {
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Thumbnail")
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
            label = { Text("Text below card") },
            supportingText = { Text("Show title and metadata below the thumbnail instead of on top") },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        )

        HorizontalDivider()

        // h. Card layout overlay background
        SwitchWithLabel(
            checked = cardLayoutOverlayBackground,
            onCheckedChange = onCardLayoutOverlayBackgroundChange,
            label = { Text("Card layout overlay background") },
            supportingText = { Text("Show a semi-transparent background overlay behind the text on cards") },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        )

        HorizontalDivider()

        // i. Hide parentheses in names
        SwitchWithLabel(
            checked = hideParenthesesInNames,
            onCheckedChange = onHideParenthesesInNamesChange,
            label = { Text("Hide parentheses in names") },
            supportingText = { Text("Remove anything in parentheses when displaying series and oneshot names") },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        )

        HorizontalDivider()

        // j. Lock screen rotation
        SwitchWithLabel(
            checked = lockScreenRotation,
            onCheckedChange = onLockScreenRotationChange,
            label = { Text("Lock screen rotation") },
            supportingText = { Text("Prevent the application screen from rotating") },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ColorLabel(entry: LabeledEntry<Color?>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val swatchColor = entry.value ?: MaterialTheme.colorScheme.surfaceVariant
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(swatchColor)
                .then(
                    if (entry.value == null) Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        CircleShape
                    )
                    else Modifier
                )
        ) {
            if (entry.value == null) {
                Text(
                    "A",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Text(entry.label)
    }
}
