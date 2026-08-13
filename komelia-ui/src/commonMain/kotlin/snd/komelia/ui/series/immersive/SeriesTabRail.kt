package snd.komelia.ui.series.immersive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import snd.komelia.ui.LocalStrings

/** How long the rail stays after the last interaction before it fades out. */
private const val RAIL_DWELL_MS = 4_000L

/**
 * Small guard so a two-pixel scroll does not blink the rail out and back in.
 * The scrollbars use the same idea in the other direction.
 */
private const val RAIL_SCROLL_GUARD_MS = 80L

/** Share of the screen height the rail (and its recall strip) may occupy. */
private const val RAIL_HEIGHT_FRACTION = 0.6f

/**
 * A second way into the series tabs, floating on the right edge.
 *
 * The tab row itself lives inside the scrolling grid, after the cover, the
 * description, the genres, the rating and the summary — so it leaves the screen
 * as soon as you start reading, and getting back to it means scrolling all the
 * way up. This rail answers the same question without moving anything: it sits
 * over the grid rather than in it, which is also why it costs no restructuring —
 * the grid is a single scroll container shared by every tab's content, and
 * nothing here joins it.
 *
 * It is deliberately NOT tied to whether the tab row is visible. On a tablet
 * there is room for both, and having the rail there too is the point.
 *
 * Only the selected entry shows its name. Five icons alone would be a guessing
 * game — "tags", "links" and "similar" do not have obvious glyphs — and five
 * permanent labels would eat a column of covers on a phone.
 */
// internal, like [ImmersiveTab] itself: the rail is part of the series screen,
// not something another module composes.
@Composable
internal fun SeriesTabRail(
    currentTab: ImmersiveTab,
    onTabChange: (ImmersiveTab) -> Unit,
    showCollectionsTab: Boolean,
    scrollState: LazyGridState,
    accentColor: Color?,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current.ui
    val entries = buildList {
        add(Triple(ImmersiveTab.BOOKS, Icons.AutoMirrored.Rounded.MenuBook, strings.books))
        if (showCollectionsTab) add(Triple(ImmersiveTab.COLLECTIONS, Icons.Rounded.Collections, strings.collections))
        add(Triple(ImmersiveTab.TAGS, Icons.Rounded.LocalOffer, strings.tags))
        add(Triple(ImmersiveTab.LINKS, Icons.Rounded.Link, strings.links))
        add(Triple(ImmersiveTab.SIMILAR, Icons.Rounded.AutoAwesome, strings.similar))
    }

    var visible by remember { mutableStateOf(true) }
    // Bumped on every interaction that should restart the dwell timer: a scroll
    // ending, a tab picked from the rail, a tap on the edge handle.
    var wakeCount by remember { mutableStateOf(0) }

    // Scrolling hides it; stopping brings it back and restarts the countdown.
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                delay(RAIL_SCROLL_GUARD_MS)
                visible = false
            } else {
                visible = true
                wakeCount++
            }
        }
    }

    LaunchedEffect(visible, wakeCount) {
        if (!visible) return@LaunchedEffect
        delay(RAIL_DWELL_MS)
        visible = false
    }

    // Deliberately NOT full height: the invisible recall strip below is a real
    // touch target, and at full height it would sit on top of the top bar's
    // right-hand buttons and of the bottom bar. The middle band is still a
    // very large target and keeps both ends clear.
    Box(modifier = modifier.fillMaxHeight(RAIL_HEIGHT_FRACTION), contentAlignment = Alignment.CenterEnd) {
        // Once the rail is gone the only thing left is this invisible strip:
        // touching the right edge brings it back without having to scroll.
        if (!visible) {
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        visible = true
                        wakeCount++
                    }
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(150)) + slideInHorizontally(tween(150)) { it / 2 },
            exit = fadeOut(tween(250)) + slideOutHorizontally(tween(250)) { it / 2 },
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 3.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                modifier = Modifier.padding(end = 6.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(vertical = 5.dp, horizontal = 4.dp),
                ) {
                    entries.forEach { (tab, icon, label) ->
                        RailEntry(
                            icon = icon,
                            label = label,
                            selected = tab == currentTab,
                            accentColor = accentColor,
                            onClick = {
                                onTabChange(tab)
                                wakeCount++
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RailEntry(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accentColor: Color?,
    onClick: () -> Unit,
) {
    val tint = if (selected) accentColor ?: MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    if (selected) {
        Surface(
            shape = CircleShape,
            color = (accentColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.14f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.height(34.dp).padding(start = 9.dp, end = 11.dp),
            ) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
            }
        }
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clickable(onClick = onClick),
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
        }
    }
}
