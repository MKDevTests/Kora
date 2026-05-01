package snd.komelia.ui.reader.image.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import snd.komelia.ui.reader.image.PageMetadata

@Composable
fun ThumbnailCarousel(
    pages: List<PageMetadata>,
    currentPageIndex: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = currentPageIndex)

    val flingBehavior = ScrollableDefaults.flingBehavior()

    LazyRow(
        state = lazyListState,
        flingBehavior = flingBehavior,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
        modifier = modifier.height(180.dp)
    ) {
        itemsIndexed(
            items = pages,
            key = { _, page -> page.toPageId().toString() }
        ) { index, page ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                BookPageThumbnail(
                    page = page,
                    useRoundedCorners = false,
                    isCurrentPage = index == currentPageIndex,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(0.7f)
                        .clickable { onPageChange(index) }
                )
            }
        }
    }

    var initialScrollDone by remember { mutableStateOf(false) }
    val viewportWidth = lazyListState.layoutInfo.viewportSize.width
    LaunchedEffect(viewportWidth) {
        if (!initialScrollDone && viewportWidth > 0) {
            val offset = -(viewportWidth / 2) + 150
            lazyListState.scrollToItem(currentPageIndex, offset)
            initialScrollDone = true
        }
    }

    LaunchedEffect(currentPageIndex) {
        if (initialScrollDone) {
            val layoutInfo = lazyListState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val viewportWidth = layoutInfo.viewportSize.width
            val itemInfo = visibleItems.find { it.index == currentPageIndex }

            if (itemInfo == null) {
                val firstVisible = visibleItems.firstOrNull()?.index ?: 0
                if (currentPageIndex < firstVisible) {
                    lazyListState.animateScrollToItem(currentPageIndex)
                } else {
                    val lastItemSize = visibleItems.lastOrNull()?.size ?: 0
                    lazyListState.animateScrollToItem(currentPageIndex, -(viewportWidth - lastItemSize))
                }
            } else {
                val isFullyVisible = itemInfo.offset >= 0 &&
                        itemInfo.offset + itemInfo.size <= viewportWidth

                if (!isFullyVisible) {
                    if (itemInfo.offset < 0) {
                        lazyListState.animateScrollToItem(currentPageIndex)
                    } else {
                        lazyListState.animateScrollToItem(currentPageIndex, -(viewportWidth - itemInfo.size))
                    }
                }
            }
        }
    }
}

