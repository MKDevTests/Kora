package snd.komelia.ui.reader.epub.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import snd.komelia.audiobook.AudioBookmark
import snd.komelia.audiobook.AudioChapterEntry
import snd.komelia.ui.LocalAccentColor
import kotlin.math.roundToInt
import snd.komelia.ui.LocalStrings

private fun formatHMS(seconds: Double): String {
    val totalSeconds = seconds.toLong()
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrackListDialog(
    chapters: List<AudioChapterEntry>,
    bookmarks: List<AudioBookmark>,
    currentChapterIndex: Int,
    onChapterClick: (Int) -> Unit,
    onBookmarkClick: (AudioBookmark) -> Unit,
    onDeleteBookmark: (AudioBookmark) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragOffsetY by remember { mutableStateOf(0f) }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 2f / 3f).dp
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    val theme = snd.komelia.ui.LocalTheme.current
    val surfaceColor = if (theme.type == snd.komelia.ui.Theme.ThemeType.DARK) Color(43, 43, 43)
    else MaterialTheme.colorScheme.background

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

            SecondaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(LocalStrings.current.ui.chapters) },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(LocalStrings.current.ui.bookmarks) },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top,
            ) { page ->
                when (page) {
                    0 -> ChaptersTab(
                        chapters = chapters,
                        currentChapterIndex = currentChapterIndex,
                        onChapterClick = { index ->
                            onChapterClick(index)
                            onDismiss()
                        },
                    )
                    1 -> AudioBookmarksTab(
                        bookmarks = bookmarks,
                        onBookmarkClick = { bookmark ->
                            onBookmarkClick(bookmark)
                            onDismiss()
                        },
                        onDeleteBookmark = onDeleteBookmark,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChaptersTab(
    chapters: List<AudioChapterEntry>,
    currentChapterIndex: Int,
    onChapterClick: (Int) -> Unit,
) {
    val lazyListState = rememberLazyListState()

    LaunchedEffect(currentChapterIndex) {
        if (currentChapterIndex >= 0 && currentChapterIndex < chapters.size) {
            lazyListState.scrollToItem(currentChapterIndex)
        }
    }

    if (chapters.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = LocalStrings.current.ui.noChaptersAvailable,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        val accentColor = LocalAccentColor.current ?: MaterialTheme.colorScheme.secondary
        val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        LazyColumn(state = lazyListState) {
            itemsIndexed(chapters) { index, chapter ->
                if (index > 0) HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = dividerColor,
                )
                val isCurrentChapter = chapter.chapterIndex == currentChapterIndex
                val buttonColors = if (isCurrentChapter) {
                    ButtonDefaults.textButtonColors(
                        containerColor = accentColor.copy(alpha = 0.15f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                }
                TextButton(
                    onClick = { onChapterClick(chapter.chapterIndex) },
                    colors = buttonColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = formatHMS(chapter.durationSeconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioBookmarksTab(
    bookmarks: List<AudioBookmark>,
    onBookmarkClick: (AudioBookmark) -> Unit,
    onDeleteBookmark: (AudioBookmark) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = LocalStrings.current.ui.noBookmarksYet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        LazyColumn {
            itemsIndexed(bookmarks) { index, bookmark ->
                if (index > 0) HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = dividerColor,
                )
                AudioBookmarkRow(
                    bookmark = bookmark,
                    onNavigate = { onBookmarkClick(bookmark) },
                    onDelete = { onDeleteBookmark(bookmark) },
                )
            }
        }
    }
}

@Composable
private fun AudioBookmarkRow(
    bookmark: AudioBookmark,
    onNavigate: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigate() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bookmark.trackTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = formatHMS(bookmark.positionSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = LocalStrings.current.ui.deleteBookmark)
        }
    }
}
