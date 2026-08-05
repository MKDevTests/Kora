package snd.komelia.ui.common.menus.bulk

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.contentColorFor
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalTheme
import snd.komelia.ui.Theme
import snd.komelia.ui.platform.cursorForHand
import kotlin.math.roundToInt
import snd.komelia.ui.LocalStrings

@Composable
fun BulkActionsContainer(
    onCancel: () -> Unit,
    selectedCount: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    val theme = LocalTheme.current
    val accentColor = LocalAccentColor.current
    val containerColor = accentColor ?: MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (accentColor != null) {
        if (accentColor.luminance() > 0.5f) Color.Black else Color.White
    } else contentColorFor(containerColor)

    Popup(popupPositionProvider = TopScreenPopupPositionProvider) {
        Surface(
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = 4.dp,
            shape = RectangleShape
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (theme.transparentBars) {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 4.dp)
                ) {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null, tint = contentColor) }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .clickable { onSelectAll() }
                            .cursorForHand()
                            .padding(end = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = allSelected,
                            onClick = { onSelectAll() },
                            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                selectedColor = contentColor,
                                unselectedColor = contentColor.copy(alpha = 0.6f)
                            )
                        )
                        Text(LocalStrings.current.ui.selectAll)
                    }
                    Text(LocalStrings.current.counts.selected(selectedCount), modifier = Modifier.width(110.dp).padding(start = 10.dp))

                    content()
                }
            }
        }
    }
}

@Composable
fun BottomPopupBulkActionsPanel(content: @Composable RowScope.() -> Unit) {
    val accentColor = LocalAccentColor.current
    val containerColor = accentColor ?: MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (accentColor != null) {
        if (accentColor.luminance() > 0.5f) Color.Black else Color.White
    } else contentColorFor(containerColor)

    Popup(popupPositionProvider = BottomScreenPopupPositionProvider) {
        Surface(
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(5.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                content()
            }
        }
    }
}

object TopScreenPopupPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ) = IntOffset(0, 0)
}

object BottomScreenPopupPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ) = IntOffset(0, windowSize.height)
}

@Composable
private fun BulkActionButton(
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    compact: Boolean
) {
    if (compact) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(min = 50.dp)
                .clickable { onClick() }
                .padding(horizontal = 10.dp)
                .pointerHoverIcon(PointerIcon.Hand),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null)
            Text(description, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }

    } else {
        ElevatedButton(onClick = onClick) {
            Icon(icon, null)
            Text(
                text = description,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun BulkActionsButtonsLayout(
    buttons: List<BulkActionButtonData>,
    compact: Boolean,
) = SubcomposeLayout { constraints ->
    val spacing = 5.dp.toPx().roundToInt()
    val buttonPlaceables = mutableListOf<Placeable>()
    val tempDropdown = subcompose(-1, { MoreActionsDropdown(emptyList(), compact) }).first().measure(constraints)
    val dropdownWidth = tempDropdown.width + spacing
    val dropdownHeight = tempDropdown.height
    var availableWidth = constraints.maxWidth - dropdownWidth

    var currentButtonIndex = 0
    for ((index, data) in buttons.withIndex()) {
        val measurable = subcompose(index, content = {
            BulkActionButton(
                description = data.description,
                icon = data.icon,
                onClick = data.onClick,
                compact = compact
            )
        }).first()

        val placeable = measurable.measure(constraints)
        val newAvailableWidth = availableWidth - (placeable.width + spacing)
        if (newAvailableWidth <= 0) {
            break
        }
        buttonPlaceables.add(placeable)
        availableWidth = newAvailableWidth
        currentButtonIndex = index
    }

    val moreActionsButton: Placeable? =
        if (currentButtonIndex < buttons.size - 1) {
            val moreActionsEntries = buttons.slice(currentButtonIndex + 1 until buttons.size)
            subcompose(-2, { MoreActionsDropdown(moreActionsEntries, compact) }).first()
                .measure(constraints)
        } else null

    val buttonsSpacing = (buttonPlaceables.size) * spacing
    val buttonsWidth = buttonPlaceables.sumOf { it.width }
    val moreActionsWidth = (moreActionsButton?.width?.plus(spacing) ?: 0)
    val width = buttonsSpacing + buttonsWidth + moreActionsWidth
    val y = (constraints.maxHeight - dropdownHeight) / 2

    layout(width, constraints.maxHeight) {
        var x = 0
        for (button in buttonPlaceables) {
            button.placeRelative(x, y)
            x += button.width + spacing
        }
        moreActionsButton?.placeRelative(x = x, y = 0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreActionsDropdown(actions: List<BulkActionButtonData>, compact: Boolean) {
    var showDropdown by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = showDropdown,
        onExpandedChange = { showDropdown = it },
    ) {
        BulkActionButton(
            description = LocalStrings.current.ui.more,
            icon = if (compact) Icons.Default.MoreHoriz else Icons.Default.MoreVert,
            onClick = { showDropdown = true },
            compact = compact
        )
        ExposedDropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false },
            matchAnchorWidth = false
        ) {
            for (actionData in actions) {
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (compact)
                                Icon(actionData.icon, null)
                            Text(actionData.description)
                        }
                    },
                    onClick = actionData.onClick
                )
            }
        }
    }
}

data class BulkActionButtonData(
    val description: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)
