package snd.komelia.ui.reader.image

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import snd.komelia.annotations.AnnotationLocation
import snd.komelia.annotations.BookAnnotation
import snd.komelia.ui.LocalTheme
import snd.komelia.ui.Theme
import snd.komelia.ui.reader.common.AnnotationRow
import kotlin.math.roundToInt
import snd.komelia.ui.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicContentDialog(
    annotations: List<BookAnnotation>,
    onAnnotationTap: (BookAnnotation) -> Unit,
    onDeleteAnnotation: (BookAnnotation) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragOffsetY by remember { mutableStateOf(0f) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 1 })
    val coroutineScope = rememberCoroutineScope()
    val theme = LocalTheme.current
    val surfaceColor = if (theme.type == Theme.ThemeType.DARK) Color(43, 43, 43)
    else MaterialTheme.colorScheme.background

    BoxWithConstraints(modifier = modifier) {
        val maxHeight = maxHeight * 2f / 3f

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = surfaceColor,
        tonalElevation = 0.dp,
        modifier = Modifier
            .heightIn(max = maxHeight)
            .offset { IntOffset(0, dragOffsetY.roundToInt().coerceAtLeast(0)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { if (dragOffsetY > 120f) onDismiss() else dragOffsetY = 0f },
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
                    text = { Text(LocalStrings.current.ui.notes) },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top,
            ) { page ->
                when (page) {
                    0 -> ComicAnnotationsTab(annotations, onAnnotationTap, onDeleteAnnotation)
                }
            }
        }
    }
    } // end BoxWithConstraints
}

@Composable
private fun ComicAnnotationsTab(
    annotations: List<BookAnnotation>,
    onTap: (BookAnnotation) -> Unit,
    onDelete: (BookAnnotation) -> Unit,
) {
    if (annotations.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = LocalStrings.current.ui.noAnnotationsYet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        val sorted = remember(annotations) {
            annotations.sortedWith(
                compareBy(
                    { (it.location as? AnnotationLocation.ComicLocation)?.page ?: 0 },
                    { (it.location as? AnnotationLocation.ComicLocation)?.y ?: 0f },
                    { (it.location as? AnnotationLocation.ComicLocation)?.x ?: 0f },
                )
            )
        }
        val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        LazyColumn {
            itemsIndexed(sorted) { index, annotation ->
                if (index > 0) HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = dividerColor,
                )
                val loc = annotation.location as? AnnotationLocation.ComicLocation
                val locationLabel = if (loc != null) "Page ${loc.page + 1}" else "Unknown"
                AnnotationRow(
                    annotation = annotation,
                    locationLabel = locationLabel,
                    onTap = { onTap(annotation) },
                    onDelete = { onDelete(annotation) },
                )
            }
        }
    }
}
