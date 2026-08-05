package snd.komelia.ui.reader.epub

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.common.components.AppSlider
import snd.komelia.ui.common.components.AppSliderDefaults
import snd.komelia.ui.reader.ReaderControlsCard
import kotlin.math.roundToInt
import snd.komelia.ui.LocalStrings

@Composable
fun Epub3ControlsCardNewUI(
    state: Epub3ReaderState,
    onSettingsClick: () -> Unit,
    onChapterClick: () -> Unit,
    onBookmarkToggle: (Locator) -> Unit,
    modifier: Modifier = Modifier,
    onCardHeightChanged: (Int) -> Unit = {},
) {
    val positions by state.positions.collectAsState()
    val currentLocator by state.currentLocator.collectAsState()
    val bookmarks by state.bookmarks.collectAsState()
    val toc by state.tableOfContents.collectAsState()
    val accentColor = LocalAccentColor.current
    val isBookmarked = remember(currentLocator, bookmarks) { state.isBookmarked(currentLocator) }

    ReaderControlsCard(modifier = modifier.onSizeChanged { onCardHeightChanged(it.height) }) {
        if (positions.isNotEmpty()) {
            val currentIndex = remember(currentLocator, positions) {
                locatorToPositionIndex(positions, currentLocator)
            }
            val interactionScope = rememberCoroutineScope()
            var interactionEndJob by remember { mutableStateOf<Job?>(null) }
            var isInteracting by remember { mutableStateOf(false) }
            var sliderDraft by remember { mutableStateOf(currentIndex.toFloat()) }

            LaunchedEffect(currentIndex) {
                if (!isInteracting) sliderDraft = currentIndex.toFloat()
            }

            fun navigate(newIndex: Int) {
                sliderDraft = newIndex.toFloat()
                state.navigateToPosition(newIndex)
                isInteracting = true
                interactionEndJob?.cancel()
                interactionEndJob = interactionScope.launch { delay(700); isInteracting = false }
            }

            Epub3LocationLabel(
                positions = positions,
                currentLocator = currentLocator,
                overrideIndex = sliderDraft.roundToInt(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            AppSlider(
                value = sliderDraft,
                onValueChange = { isInteracting = true; sliderDraft = it },
                onValueChangeFinished = { navigate(sliderDraft.roundToInt()) },
                valueRange = 0f..(positions.size - 1).toFloat(),
                steps = 0,
                accentColor = accentColor,
                colors = AppSliderDefaults.colors(accentColor = accentColor),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val locator = currentLocator
            val chapterTitle = if (locator != null) {
                locator.title
                    ?: findTocLink(toc, locator.href)?.title
                    ?: locator.href.toString()
                        .substringAfterLast('/')
                        .substringBeforeLast('.')
                        .replace('-', ' ')
                        .replace('_', ' ')
            } else ""

            Button(
                onClick = onChapterClick,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            ) {
                Text(
                    text = chapterTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Epub3BookmarkToggleButton(
                isBookmarked = isBookmarked,
                onClick = { currentLocator?.let { onBookmarkToggle(it) } },
                accentColor = accentColor
            )

            VerticalDivider(
                modifier = Modifier
                    .height(24.dp)
                    .padding(horizontal = 8.dp)
            )

            IconButton(onClick = { state.openContentDialog(3) }) {
                Icon(Icons.Default.Search, contentDescription = LocalStrings.current.ui.search, tint = accentColor ?: MaterialTheme.colorScheme.primary)
            }

            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Tune, contentDescription = LocalStrings.current.ui.readerSettings, tint = accentColor ?: MaterialTheme.colorScheme.primary)
            }
        }
    }
}

fun locatorToPositionIndex(positions: List<Locator>, locator: Locator?): Int {
    if (locator == null || positions.isEmpty()) return 0
    val sameHref = positions.filter { it.href == locator.href }
    if (sameHref.isEmpty()) return 0
    val prog = locator.locations.progression ?: 0.0
    val best = sameHref.minByOrNull { kotlin.math.abs((it.locations.progression ?: 0.0) - prog) }
        ?: sameHref.first()
    return positions.indexOf(best).coerceAtLeast(0)
}

fun findTocLink(links: List<Link>, href: Url): Link? {
    val targetHref = href.removeFragment()
    for (link in links) {
        if (link.url()?.removeFragment() == targetHref) return link
        findTocLink(link.children, href)?.let { return it }
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Epub3ControlsCard(
    state: Epub3ReaderState,
    onDismiss: () -> Unit,
    onCardHeightChanged: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onChapterClick: () -> Unit,
    onBookmarkToggle: (Locator) -> Unit,
    modifier: Modifier = Modifier,
) {
    val positions by state.positions.collectAsState()
    val currentLocator by state.currentLocator.collectAsState()
    val bookmarks by state.bookmarks.collectAsState()
    val toc by state.tableOfContents.collectAsState()
    val isBookmarked = remember(currentLocator, bookmarks) { state.isBookmarked(currentLocator) }
    val accentColor = LocalAccentColor.current

    var dragOffsetY by remember { mutableStateOf(0f) }

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = modifier
            .onSizeChanged { onCardHeightChanged(it.height) }
            .offset { IntOffset(0, dragOffsetY.roundToInt().coerceAtLeast(0)) },
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
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

            // Page label + slider flanked by − / + navigation buttons
            Epub3PageNavigatorRow(
                positions = positions,
                currentLocator = currentLocator,
                onNavigateToPosition = state::navigateToPosition,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .padding(top = 4.dp),
            )

            // Chapter chip — show whenever we have a locator; fall back to filename when no title.
            val locator = currentLocator
            if (locator != null) {
                val chapterTitle = locator.title
                    ?: findTocLink(toc, locator.href)?.title
                    ?: locator.href.toString()
                        .substringAfterLast('/')
                        .substringBeforeLast('.')
                        .replace('-', ' ')
                        .replace('_', ' ')

                SuggestionChip(
                    onClick = onChapterClick,
                    label = {
                        Text(
                            text = chapterTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp),
                )
            }

            // Settings and Bookmark buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Tune, contentDescription = LocalStrings.current.ui.readerSettings, tint = accentColor ?: MaterialTheme.colorScheme.primary)
                }
                
                IconButton(onClick = { state.openContentDialog(3) }) {
                    Icon(Icons.Default.Search, contentDescription = LocalStrings.current.ui.search, tint = accentColor ?: MaterialTheme.colorScheme.primary)
                }
                
                VerticalDivider(
                    modifier = Modifier
                        .height(24.dp)
                        .padding(horizontal = 4.dp)
                )
                
                Epub3BookmarkToggleButton(
                    isBookmarked = isBookmarked,
                    onClick = { currentLocator?.let { onBookmarkToggle(it) } },
                    accentColor = accentColor
                )
            }
        }
    }
}

@Composable
fun Epub3BookmarkToggleButton(
    isBookmarked: Boolean,
    onClick: () -> Unit,
    accentColor: Color?,
) {
    val indicatorColor = accentColor ?: MaterialTheme.colorScheme.secondaryContainer
    val selectedIconTint = if (accentColor != null && accentColor.luminance() > 0.5f) Color.Black else Color.White

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (isBookmarked) indicatorColor else Color.Transparent)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                if (isBookmarked) Icons.Filled.Bookmark else Icons.Default.BookmarkAdd,
                contentDescription = LocalStrings.current.ui.toggleBookmark,
                tint = if (isBookmarked) selectedIconTint else (accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }
    }
}

@Composable
fun Epub3PageNavigatorRow(
    positions: List<Locator>,
    currentLocator: Locator?,
    onNavigateToPosition: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (positions.size <= 1) return

    val accentColor = LocalAccentColor.current
    val currentIndex = remember(currentLocator, positions) {
        locatorToPositionIndex(positions, currentLocator)
    }
    val interactionScope = rememberCoroutineScope()
    var interactionEndJob by remember { mutableStateOf<Job?>(null) }
    var isInteracting by remember { mutableStateOf(false) }
    var sliderDraft by remember { mutableStateOf(currentIndex.toFloat()) }

    LaunchedEffect(currentIndex) {
        if (!isInteracting) sliderDraft = currentIndex.toFloat()
    }

    fun navigate(newIndex: Int) {
        sliderDraft = newIndex.toFloat()
        onNavigateToPosition(newIndex)
        isInteracting = true
        interactionEndJob?.cancel()
        interactionEndJob = interactionScope.launch { delay(700); isInteracting = false }
    }

    Column(modifier = modifier) {
        Epub3LocationLabel(
            positions = positions,
            currentLocator = currentLocator,
            overrideIndex = sliderDraft.roundToInt(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { navigate((sliderDraft.roundToInt() - 1).coerceAtLeast(0)) },
                enabled = sliderDraft.roundToInt() > 0,
            ) {
                Icon(Icons.Filled.Remove, contentDescription = LocalStrings.current.ui.previousPage)
            }
            AppSlider(
                value = sliderDraft,
                onValueChange = { isInteracting = true; sliderDraft = it },
                onValueChangeFinished = { navigate(sliderDraft.roundToInt()) },
                valueRange = 0f..(positions.size - 1).toFloat(),
                steps = 0,
                accentColor = accentColor,
                colors = AppSliderDefaults.colors(accentColor = accentColor),
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { navigate((sliderDraft.roundToInt() + 1).coerceAtMost(positions.size - 1)) },
                enabled = sliderDraft.roundToInt() < positions.size - 1,
            ) {
                Icon(Icons.Filled.Add, contentDescription = LocalStrings.current.ui.nextPage)
            }
        }
    }
}

@Composable
fun Epub3LocationLabel(
    positions: List<Locator>,
    currentLocator: Locator?,
    modifier: Modifier = Modifier,
    textAlign: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Unspecified,
    overrideIndex: Int? = null,
) {
    val index = overrideIndex ?: remember(currentLocator, positions) {
        locatorToPositionIndex(positions, currentLocator)
    }
    Text(
        text = "Loc. ${index + 1} of ${positions.size}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        textAlign = textAlign,
        modifier = modifier,
    )
}
