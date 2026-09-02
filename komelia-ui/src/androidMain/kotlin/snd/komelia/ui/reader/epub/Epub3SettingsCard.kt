package snd.komelia.ui.reader.epub

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import com.storyteller.reader.BundledFonts
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.launch
import snd.komelia.fonts.UserFont
import snd.komelia.settings.model.Epub3ColumnCount
import snd.komelia.settings.model.Epub3ReadAloudColor
import snd.komelia.settings.model.Epub3TextAlign
import snd.komelia.settings.model.Epub3Theme
import snd.komelia.settings.model.Epub3NativeSettings
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.reader.image.settings.NavigationSettings
import snd.komelia.ui.common.components.AppSlider
import snd.komelia.ui.common.components.AppSliderDefaults
import kotlin.math.roundToInt
import snd.komelia.ui.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Epub3SettingsCard(
    settings: Epub3NativeSettings,
    onSettingsChange: (Epub3NativeSettings) -> Unit,
    onDismiss: () -> Unit,
    userFonts: List<UserFont> = emptyList(),
    onLoadFont: (PlatformFile) -> Unit = {},
    onDeleteFont: (UserFont) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var dragOffsetY by remember { mutableStateOf(0f) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val theme = snd.komelia.ui.LocalTheme.current
    val surfaceColor = if (theme.type == snd.komelia.ui.Theme.ThemeType.DARK) Color(43, 43, 43)
    else MaterialTheme.colorScheme.background
    val accentColor = LocalAccentColor.current ?: MaterialTheme.colorScheme.primary
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 2f / 3f).dp

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = surfaceColor,
        tonalElevation = 0.dp,
        modifier = modifier
            .heightIn(max = maxHeight)
            .offset { IntOffset(0, dragOffsetY.roundToInt().coerceAtLeast(0)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .navigationBarsPadding(),
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragOffsetY > 120f) onDismiss()
                                else dragOffsetY = 0f
                            },
                            onDragCancel = { dragOffsetY = 0f },
                            onVerticalDrag = { _, delta ->
                                dragOffsetY = (dragOffsetY + delta).coerceAtLeast(0f)
                            }
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                BottomSheetDefaults.DragHandle()
            }

            // Tab row — sticky. Scrollable, because a fourth tab does not fit
            // on a phone otherwise; same widget as the image reader's sheet.
            //
            // Navigation sits at index 1, where the image reader puts it, so the
            // two readers are found in the same place.
            SecondaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                edgePadding = 16.dp,
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(LocalStrings.current.ui.appearance) },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(LocalStrings.current.ui.navigation) },
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                    text = { Text(LocalStrings.current.ui.fontText) },
                )
            }

            // Tab content — fills remaining height, scrollable within it
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top,
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                ) {
                    when (page) {
                        0 -> AppearanceTab(settings, onSettingsChange, accentColor)
                        // The image reader's own control, reused whole: same
                        // labels, same diagrams, same four choices.
                        1 -> NavigationSettings(
                            currentMode = settings.tapNavigationMode,
                            onModeChange = { onSettingsChange(settings.copy(tapNavigationMode = it)) },
                        )

                        2 -> FontTextTab(settings, onSettingsChange, accentColor, userFonts, onLoadFont, onDeleteFont)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceTab(
    settings: Epub3NativeSettings,
    onSettingsChange: (Epub3NativeSettings) -> Unit,
    accentColor: Color,
) {
    Column {
        // Theme chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Epub3Theme.entries.forEach { theme ->
                ThemeChip(
                    theme = theme,
                    selected = settings.theme == theme,
                    accentColor = accentColor,
                    onClick = { onSettingsChange(settings.copy(theme = theme)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Reading Mode
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(LocalStrings.current.ui.readingMode2, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(112.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    selected = !settings.scroll,
                    onClick = { onSettingsChange(settings.copy(scroll = false)) },
                    label = { Text(LocalStrings.current.ui.page) },
                )
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    selected = settings.scroll,
                    onClick = { onSettingsChange(settings.copy(scroll = true)) },
                    label = { Text(LocalStrings.current.ui.scroll) },
                )
            }
        }

        // Column count
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(LocalStrings.current.ui.columns, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(112.dp))
            val colOptions = listOf(
                Epub3ColumnCount.AUTO to "Auto",
                Epub3ColumnCount.ONE  to "1",
                Epub3ColumnCount.TWO  to "2",
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                colOptions.forEachIndexed { i, (col, label) ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                        selected = settings.columnCount == col,
                        onClick = { onSettingsChange(settings.copy(columnCount = col)) },
                        label = { Text(label) },
                    )
                }
            }
        }

        // Page margins slider
        SliderRow(
            label = LocalStrings.current.ui.margins,
            valueLabel = "${"%.1f".format(settings.pageMargins)}×",
            value = settings.pageMargins.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(pageMargins = it.toDouble())) },
            valueRange = 0.5f..2.0f,
            accentColor = accentColor,
        )

        val screenHeight = LocalConfiguration.current.screenHeightDp.toFloat()
        val maxMargin = screenHeight * 0.2f

        // Top margin slider
        SliderRow(
            label = LocalStrings.current.ui.topMargin,
            valueLabel = "${settings.topMargin.toInt()}dp",
            value = settings.topMargin,
            onValueChange = { onSettingsChange(settings.copy(topMargin = it)) },
            valueRange = 0f..maxMargin,
            accentColor = accentColor,
        )

        // Bottom margin slider
        SliderRow(
            label = LocalStrings.current.ui.bottomMargin,
            valueLabel = "${settings.bottomMargin.toInt()}dp",
            value = settings.bottomMargin,
            onValueChange = { onSettingsChange(settings.copy(bottomMargin = it)) },
            valueRange = 0f..maxMargin,
            accentColor = accentColor,
        )

        // Read-aloud highlight
        Text(
            text = LocalStrings.current.ui.readAloudHighlight,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Epub3ReadAloudColor.entries.forEach { c ->
                val solidColor = Color(c.colorInt.toLong() or 0xFF000000L)
                val selected = settings.readAloudColor == c
                FilterChip(
                    selected = selected,
                    onClick = { onSettingsChange(settings.copy(readAloudColor = c)) },
                    modifier = Modifier.then(
                        if (selected) Modifier.border(2.dp, accentColor, RoundedCornerShape(8.dp))
                        else Modifier
                    ),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = solidColor.copy(alpha = 0.5f),
                        containerColor = solidColor.copy(alpha = 0.2f),
                    ),
                    label = {},
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(solidColor.copy(alpha = 0.8f))
                        )
                    },
                )
            }
        }

        // Date & time overlay toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(LocalStrings.current.ui.showDateTime, style = MaterialTheme.typography.labelLarge)
            Switch(
                checked = settings.showDateTimeOverlay,
                onCheckedChange = { onSettingsChange(settings.copy(showDateTimeOverlay = it)) },
            )
        }

        // Location overlay toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(LocalStrings.current.ui.showLocation, style = MaterialTheme.typography.labelLarge)
            Switch(
                checked = settings.showLocationOverlay,
                onCheckedChange = { onSettingsChange(settings.copy(showLocationOverlay = it)) },
            )
        }
    }
}

@Composable
private fun FontGroupHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
    )
}

/** Occupies the leading slot either way, so the labels stay on one line. */
@Composable
private fun SelectedFontMark(selected: Boolean) {
    if (selected) {
        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
    } else {
        Box(Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontTextTab(
    settings: Epub3NativeSettings,
    onSettingsChange: (Epub3NativeSettings) -> Unit,
    accentColor: Color,
    userFonts: List<UserFont>,
    onLoadFont: (PlatformFile) -> Unit,
    onDeleteFont: (UserFont) -> Unit,
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    val fontPicker = rememberFilePickerLauncher(
        type = FileKitType.File(listOf("ttf", "otf")),
    ) { file -> file?.let { onLoadFont(it) } }
    val strings = LocalStrings.current.ui
    // What the field shows: the picker's label, not the css family name, so the
    // list reads "Source Serif" rather than "Source Serif 4".
    val selectedLabel = BundledFonts.all.firstOrNull { it.family == settings.fontFamily }?.label
        ?: userFonts.firstOrNull { it.canonicalName == settings.fontFamily }?.name
        ?: settings.fontFamily

    Column {
        // Font family — dropdown with bundled + user fonts
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(strings.font, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(112.dp))
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                ) {
                    val groups = listOf(
                        strings.fontGroupSerif to BundledFonts.serif,
                        strings.fontGroupAccessible to BundledFonts.accessible,
                        strings.fontGroupSystem to BundledFonts.system,
                    )
                    groups.forEachIndexed { index, (header, fonts) ->
                        if (index > 0) HorizontalDivider()
                        FontGroupHeader(header)
                        fonts.forEach { font ->
                            DropdownMenuItem(
                                text = { Text(font.label) },
                                leadingIcon = { SelectedFontMark(font.family == settings.fontFamily) },
                                onClick = {
                                    onSettingsChange(settings.copy(fontFamily = font.family))
                                    dropdownExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                    if (userFonts.isNotEmpty()) {
                        HorizontalDivider()
                        FontGroupHeader(strings.fontGroupYours)
                        userFonts.forEach { font ->
                            DropdownMenuItem(
                                text = { Text(font.name) },
                                leadingIcon = { SelectedFontMark(font.canonicalName == settings.fontFamily) },
                                onClick = {
                                    onSettingsChange(settings.copy(fontFamily = font.canonicalName))
                                    dropdownExpanded = false
                                },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        if (settings.fontFamily == font.canonicalName) {
                                            onSettingsChange(settings.copy(fontFamily = BundledFonts.default.family))
                                        }
                                        onDeleteFont(font)
                                        dropdownExpanded = false
                                    }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete ${font.name}",
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(strings.loadFont) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            dropdownExpanded = false
                            fontPicker.launch()
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }

        // Text alignment
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(LocalStrings.current.ui.align, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(112.dp))
            val alignOptions = listOf(
                Epub3TextAlign.JUSTIFY to Icons.Default.FormatAlignJustify,
                Epub3TextAlign.LEFT    to Icons.AutoMirrored.Filled.FormatAlignLeft,
                Epub3TextAlign.CENTER  to Icons.Default.FormatAlignCenter,
                Epub3TextAlign.RIGHT   to Icons.AutoMirrored.Filled.FormatAlignRight,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                alignOptions.forEachIndexed { i, (align, icon) ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(i, 4),
                        selected = settings.textAlign == align,
                        onClick = { onSettingsChange(settings.copy(textAlign = align)) },
                        label = { Icon(icon, contentDescription = align.name) },
                    )
                }
            }
        }

        // Font size slider
        SliderRow(
            label = LocalStrings.current.ui.fontSize,
            valueLabel = "${(settings.fontSize * 100).roundToInt()}%",
            value = settings.fontSize.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(fontSize = it.toDouble())) },
            valueRange = 0.7f..2.0f,
            accentColor = accentColor,
        )

        // Line height slider
        SliderRow(
            label = LocalStrings.current.ui.lineHeight,
            valueLabel = "${"%.1f".format(settings.lineHeight)}×",
            value = settings.lineHeight.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(lineHeight = it.toDouble())) },
            valueRange = 1.0f..2.0f,
            accentColor = accentColor,
        )

        // Paragraph spacing slider
        SliderRow(
            label = LocalStrings.current.ui.paraSpacing,
            valueLabel = "${"%.1f".format(settings.paragraphSpacing)}×",
            value = settings.paragraphSpacing.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(paragraphSpacing = it.toDouble())) },
            valueRange = 0.0f..2.0f,
            accentColor = accentColor,
        )

        // Publisher styles toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(LocalStrings.current.ui.publisherStyles, style = MaterialTheme.typography.labelLarge)
            Switch(
                checked = settings.publisherStyles,
                onCheckedChange = { onSettingsChange(settings.copy(publisherStyles = it)) },
            )
        }

        // Respect publisher colors toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(LocalStrings.current.ui.respectPublisherColors, style = MaterialTheme.typography.labelLarge)
            Switch(
                checked = settings.respectPublisherColors,
                onCheckedChange = { onSettingsChange(settings.copy(respectPublisherColors = it)) },
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(Modifier.width(112.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AppSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = 0,
            accentColor = accentColor,
            colors = AppSliderDefaults.colors(accentColor = accentColor),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ThemeChip(
    theme: Epub3Theme,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.outline
    val bgColor = Color(theme.background.toLong() or 0xFF000000L)
    val fgColor = Color(theme.foreground.toLong() or 0xFF000000L)
    val borderColor = if (selected) accentColor else outline
    val borderWidth = if (selected) 2.dp else 1.dp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = fgColor,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                text = theme.label,
                color = fgColor,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                minLines = 2,
            )
        }
    }
}
