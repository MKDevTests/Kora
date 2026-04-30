package snd.komelia.ui.common.itemlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyGridState
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalFloatingToolbarPadding
import snd.komelia.ui.LocalTransparentNavBarPadding
import snd.komelia.ui.LocalUseNewLibraryUI
import snd.komelia.ui.common.cards.DraggableImageCard
import snd.komelia.ui.common.cards.SeriesImageCard
import snd.komelia.ui.common.components.Pagination
import snd.komelia.ui.common.menus.SeriesMenuActions
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.VerticalScrollbarWithFullSpans
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import snd.komelia.image.coil.SeriesDefaultThumbnailRequest

@Composable
fun SeriesLazyCardGrid(
    series: List<KomgaSeries>,
    downloadedSeriesIds: Set<KomgaSeriesId> = emptySet(),
    onSeriesClick: (KomgaSeries) -> Unit,
    seriesMenuActions: SeriesMenuActions?,

    selectedSeries: List<KomgaSeries> = emptyList(),
    onSeriesSelect: ((KomgaSeries) -> Unit)? = null,

    reorderable: Boolean = false,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onReorderDragStateChange: (dragging: Boolean) -> Unit = {},

    totalPages: Int,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    minSize: Dp = 200.dp,
    gridState: LazyGridState = rememberLazyGridState(),

    modifier: Modifier = Modifier,

    beforeContent: @Composable () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val reorderableLazyGridState = rememberReorderableLazyGridState(
        lazyGridState = gridState,
        onMove = { from, to -> onReorder(from.index - 1, to.index - 1) }
    )
    LaunchedEffect(reorderableLazyGridState.isAnyItemDragging) {
        onReorderDragStateChange(reorderableLazyGridState.isAnyItemDragging)
    }

    // Prefetch upcoming thumbnails as the user scrolls (~10 items ahead)
    val context = LocalPlatformContext.current
    val imageLoader = remember { SingletonImageLoader.get(context) }
    LaunchedEffect(series, gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible
        }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val prefetchCount = 10
                val end = (lastVisible + prefetchCount).coerceAtMost(series.size)
                val start = (lastVisible + 1).coerceAtLeast(0)
                if (start >= end) return@collect
                for (i in start until end) {
                    val target = series.getOrNull(i) ?: continue
                    val request = ImageRequest.Builder(context)
                        .data(SeriesDefaultThumbnailRequest(target.id))
                        .memoryCacheKey(target.id.value)
                        .placeholderMemoryCacheKey(target.id.value)
                        .diskCacheKey(target.id.value)
                        .build()
                    imageLoader.enqueue(request)
                }
            }
    }


    val useNewLibraryUI = LocalUseNewLibraryUI.current
    val cardSpacing = if (useNewLibraryUI) 7.dp else 15.dp
    val horizontalPadding = if (useNewLibraryUI) 10.dp else 20.dp
    val extraBottomPadding = LocalTransparentNavBarPadding.current
    val toolbarPadding = LocalFloatingToolbarPadding.current
    Box(modifier) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize),
            horizontalArrangement = Arrangement.spacedBy(cardSpacing),
            verticalArrangement = Arrangement.spacedBy(cardSpacing),
            contentPadding = PaddingValues(
                start = horizontalPadding, end = horizontalPadding,
                top = toolbarPadding,
                bottom = 15.dp + extraBottomPadding,
            ),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                beforeContent()
            }

            items(items = series, key = { it.id.value }) { series ->
                val isSelected = remember(selectedSeries) { selectedSeries.any { it.id == series.id } }
                DraggableImageCard(
                    key = series.id.value,
                    dragEnabled = reorderable,
                    reorderableState = reorderableLazyGridState
                ) {
                    SeriesImageCard(
                        series = series,
                        isDownloaded = series.id in downloadedSeriesIds,
                        onSeriesClick = { onSeriesClick(series) },
                        seriesMenuActions = seriesMenuActions,
                        isSelected = isSelected,
                        onSeriesSelect = onSeriesSelect?.let { { onSeriesSelect(series) } },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Pagination(
                    totalPages = totalPages,
                    currentPage = currentPage,
                    onPageChange = {
                        coroutineScope.launch {
                            onPageChange(it)
                            gridState.scrollToItem(0)
                        }
                    }
                )
            }
        }

        VerticalScrollbarWithFullSpans(gridState, Modifier.align(Alignment.TopEnd), 2)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyGridItemScope.DraggableSeriesCard(
    series: KomgaSeries,
    onSeriesClick: (KomgaSeries) -> Unit,
    seriesMenuActions: SeriesMenuActions?,
    isSelected: Boolean = false,
    onSeriesSelect: ((KomgaSeries) -> Unit)?,
    reorderableState: ReorderableLazyGridState
) {
    val platform = LocalPlatform.current
    ReorderableItem(reorderableState, key = series.id.value) {
        if (platform == PlatformType.MOBILE) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SeriesImageCard(
                    series = series,
                    onSeriesClick = { onSeriesClick(series) },
                    seriesMenuActions = seriesMenuActions,
                    isSelected = isSelected,
                    onSeriesSelect = onSeriesSelect?.let { { onSeriesSelect(series) } },
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .fillMaxWidth()
                        .draggableHandle()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.DragHandle, null) }
            }
        } else {
            SeriesImageCard(
                series = series,
                onSeriesClick = { onSeriesClick(series) },
                seriesMenuActions = seriesMenuActions,
                isSelected = isSelected,
                onSeriesSelect = onSeriesSelect?.let { { onSeriesSelect(series) } },
                modifier = Modifier.fillMaxSize().draggableHandle()
            )
        }
    }
}