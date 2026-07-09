package snd.komelia.ui.common.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import snd.komelia.ui.LocalHideParenthesesInNames
import snd.komelia.ui.LocalLanguageBadgeAtBottom
import snd.komelia.ui.LocalLanguageBadgeScale
import snd.komelia.ui.LocalLibraries
import snd.komelia.ui.LocalShowCompleteSeriesBadge
import snd.komelia.ui.LocalShowLanguageOnCovers
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.components.NoPaddingChip
import snd.komelia.ui.common.images.SeriesThumbnail
import snd.komelia.ui.common.menus.SeriesActionsMenu
import snd.komelia.ui.common.menus.SeriesMenuActions
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.utils.languageBadgeLabel
import snd.komelia.utils.removeParentheses
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesStatus
import androidx.compose.material3.LinearProgressIndicator

/** Status Ended and every volume owned — matches Komga's own "complete" series filter. */
private val KomgaSeries.isComplete: Boolean
    get() {
        val total = metadata.totalBookCount
        return metadata.status == KomgaSeriesStatus.ENDED && total != null && booksCount >= total
    }

@Composable
fun SeriesImageCard(
    series: KomgaSeries,
    onSeriesClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    onSeriesSelect: (() -> Unit)? = null,
    seriesMenuActions: SeriesMenuActions? = null,
    isDownloaded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val libraries = LocalLibraries.current
    val libraryIsDeleted = remember {
        libraries.value.firstOrNull { it.id == series.libraryId }?.unavailable ?: false
    }
    val hideParentheses = LocalHideParenthesesInNames.current
    val title = if (hideParentheses) series.metadata.title.removeParentheses() else series.metadata.title

    // Hoisted menu state so long-press anywhere on the card AND the
    // hover-only MoreVert button can both open the same actions menu.
    // Long-press takes priority over selection: when seriesMenuActions
    // is provided, long-press shows the menu (with Rate / Mark read /
    // Download / etc.). Selection is still reachable via the hover
    // overlay's radio button on desktop.
    var isMenuExpanded by remember { mutableStateOf(false) }
    val longClick: (() -> Unit)? = when {
        seriesMenuActions != null -> { -> isMenuExpanded = true }
        else -> onSeriesSelect
    }

    LibraryItemCard(
        modifier = modifier,
        title = title,
        isUnavailable = series.deleted || libraryIsDeleted,
        onClick = onSeriesClick,
        onLongClick = longClick,
        image = {
            SeriesThumbnail(
                series.id,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        },
        badges = {
            SeriesCardHoverOverlay(
                series = series,
                onSeriesSelect = onSeriesSelect,
                isSelected = isSelected,
                seriesActions = seriesMenuActions,
                isMenuExpanded = isMenuExpanded,
                onMenuExpandedChange = { isMenuExpanded = it },
            ) {
                SeriesImageBadges(series = series, isDownloaded = isDownloaded)
            }
        },
        progress = {
            val total = series.booksCount
            val read = series.booksReadCount
            if (total > 0 && read > 0 && read < total) {
                LinearProgressIndicator(
                    progress = { read.toFloat() / total.toFloat() },
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                    modifier = Modifier.height(4.dp).fillMaxWidth().align(Alignment.BottomStart),
                    drawStopIndicator = {}
                )
            }
        }
    )
}

@Composable
fun SeriesSimpleImageCard(
    series: KomgaSeries,
    onSeriesClick: (() -> Unit)? = null,
    fillMaxWidth: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val hideParentheses = LocalHideParenthesesInNames.current
    val title = if (hideParentheses) series.metadata.title.removeParentheses() else series.metadata.title

    LibraryItemCard(
        modifier = modifier,
        title = title,
        showText = false,
        onClick = onSeriesClick,
        fillMaxWidth = fillMaxWidth,
        image = {
            SeriesThumbnail(
                series.id,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        },
        badges = {
            SeriesImageBadges(series = series)
        }
    )
}

@Composable
private fun SeriesCardHoverOverlay(
    series: KomgaSeries,
    isSelected: Boolean,
    onSeriesSelect: (() -> Unit)?,
    seriesActions: SeriesMenuActions?,
    isMenuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered = interactionSource.collectIsHoveredAsState()
    val showOverlay = derivedStateOf { isHovered.value || isMenuExpanded || isSelected }
    val border = if (showOverlay.value) overlayBorderModifier() else Modifier

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hoverable(interactionSource)
            .then(border),
        contentAlignment = Alignment.Center
    ) {
        content()

        if (showOverlay.value) {
            val backgroundModifier =
                if (isSelected) Modifier.background(MaterialTheme.colorScheme.secondary.copy(alpha = .5f))
                else Modifier
            Column(backgroundModifier.fillMaxSize()) {
                if (onSeriesSelect != null) {
                    SelectionRadioButton(isSelected, onSeriesSelect)
                    Spacer(Modifier.weight(1f))
                }

                if (seriesActions != null) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Spacer(Modifier.weight(1f))

                        Box {
                            IconButton(
                                onClick = { onMenuExpandedChange(true) },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = null)
                            }

                            SeriesActionsMenu(
                                series = series,
                                actions = seriesActions,
                                expanded = isMenuExpanded,
                                showEditOption = true,
                                showDownloadOption = true,
                                onDismissRequest = { onMenuExpandedChange(false) },
                                onSelect = onSeriesSelect,
                            )
                        }
                    }
                }
            }
        } else if (isMenuExpanded && seriesActions != null) {
            // Long-press path on touch: the hover overlay is hidden (no
            // hover, no selection), but the user has just triggered the
            // menu. Render the menu standalone at the bottom-right of
            // the card so it lines up with where the MoreVert button
            // would be — preserves the menu's anchor point regardless
            // of how it was opened.
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Box {
                    // Invisible anchor — DropdownMenu attaches to its
                    // parent Box, which sits at BottomEnd.
                    Spacer(Modifier)
                    SeriesActionsMenu(
                        series = series,
                        actions = seriesActions,
                        expanded = isMenuExpanded,
                        showEditOption = true,
                        showDownloadOption = true,
                        onDismissRequest = { onMenuExpandedChange(false) },
                        onSelect = onSeriesSelect,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesImageBadges(
    series: KomgaSeries,
    isDownloaded: Boolean = false,
) {
    if (LocalShowLanguageOnCovers.current) {
        val label = languageBadgeLabel(series.metadata.language)
        if (label != null) {
            LanguageBadge(
                label = label,
                scale = LocalLanguageBadgeScale.current,
                atBottom = LocalLanguageBadgeAtBottom.current,
            )
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row {
            if (isDownloaded) {
                IndicatorBadge {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
    val showCompleteBadge = LocalShowCompleteSeriesBadge.current && series.isComplete
    if (series.booksUnreadCount > 0) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopEnd
        ) {
            if (showCompleteBadge) {
                IndicatorBadge(
                    backgroundColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
                    borderColor = MaterialTheme.colorScheme.tertiary,
                ) {
                    Text(
                        "${series.booksUnreadCount}",
                        color = MaterialTheme.colorScheme.onTertiary,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp)
                    )
                }
            } else {
                IndicatorBadge {
                    Text(
                        "${series.booksUnreadCount}",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp)
                    )
                }
            }
        }
    } else if (showCompleteBadge) {
        // Fully read and complete: no count left to show, but keep the corner
        // marked so a finished-and-owned-in-full series is still recognizable.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopEnd
        ) {
            IndicatorBadge(
                backgroundColor = MaterialTheme.colorScheme.tertiary,
                borderColor = MaterialTheme.colorScheme.tertiary,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Small FR/EN language pill overlaid on a series cover. Top-left by default,
 * bottom-left when [atBottom]; size driven by [scale]. Subtle translucent
 * surface background so it stays legible over any cover art. Only rendered
 * when the language is known (see [languageBadgeLabel]).
 */
@Composable
private fun LanguageBadge(
    label: String,
    scale: Float,
    atBottom: Boolean,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(4.dp),
        contentAlignment = if (atBottom) Alignment.BottomStart else Alignment.TopStart,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = (11 * scale).sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(horizontal = (5 * scale).dp, vertical = (2 * scale).dp),
        )
    }
}

@Composable
fun SeriesDetailedListCard(
    series: KomgaSeries,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hideParentheses = LocalHideParenthesesInNames.current
    val title = if (hideParentheses) series.metadata.title.removeParentheses() else series.metadata.title

    Card(
        modifier
            .cursorForHand()
            .clickable { onClick() }) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(Modifier.width(100.dp)) {
                SeriesSimpleImageCard(series = series, onSeriesClick = onClick, fillMaxWidth = false)
            }
            SeriesDetails(title, series)
        }
    }
}

@Composable
private fun SeriesDetails(title: String, series: KomgaSeries) {
    val width = LocalWindowWidth.current
    Column(Modifier.padding(start = 10.dp)) {
        Row {
            Text(title, fontWeight = FontWeight.Bold)
        }
        LazyRow(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(series.metadata.genres) {
                NoPaddingChip(
                    borderColor = MaterialTheme.colorScheme.surface,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

            }
        }
        Text(series.metadata.summary, maxLines = 2, style = MaterialTheme.typography.bodyMedium)

    }
}
