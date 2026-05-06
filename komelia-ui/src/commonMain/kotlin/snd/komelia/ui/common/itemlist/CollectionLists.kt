package snd.komelia.ui.common.itemlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import snd.komelia.ui.LocalFloatingToolbarPadding
import snd.komelia.ui.LocalTransparentNavBarPadding
import snd.komelia.ui.LocalUseNewLibraryUI
import snd.komelia.ui.common.cards.CollectionImageCard
import snd.komelia.ui.common.components.Pagination
import snd.komelia.ui.platform.VerticalScrollbar
import snd.komelia.ui.platform.VerticalScrollbarWithFullSpans
import snd.komga.client.collection.KomgaCollection
import snd.komga.client.collection.KomgaCollectionId

@Composable
fun CollectionLazyCardGrid(
    collections: List<KomgaCollection>,
    onCollectionClick: (KomgaCollectionId) -> Unit,
    onCollectionDelete: (KomgaCollectionId) -> Unit,
    totalPages: Int,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    minSize: Dp = 200.dp,
    scrollState: LazyGridState = rememberLazyGridState(),
    beforeContent: (@Composable () -> Unit)? = null,
    progressOf: ((KomgaCollectionId) -> Float?)? = null,
    onProgressNeeded: ((KomgaCollectionId) -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val useNewLibraryUI = LocalUseNewLibraryUI.current
    val cardSpacing = if (useNewLibraryUI) 7.dp else 8.dp
    val horizontalPadding = 10.dp
    val extraBottomPadding = LocalTransparentNavBarPadding.current
    val toolbarPadding = LocalFloatingToolbarPadding.current
    Box {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize),
            state = scrollState,
            horizontalArrangement = Arrangement.spacedBy(cardSpacing),
            verticalArrangement = Arrangement.spacedBy(cardSpacing),
            contentPadding = PaddingValues(start = horizontalPadding, end = horizontalPadding, top = toolbarPadding, bottom = 30.dp + extraBottomPadding),
        ) {
            beforeContent?.let {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    it()
                }
            }
            item(
                span = { GridItemSpan(maxLineSpan) },
            ) {
                if (scrollState.canScrollForward || scrollState.canScrollBackward)
                    Pagination(
                        totalPages = totalPages,
                        currentPage = currentPage,
                        onPageChange = onPageChange
                    )
            }

            items(collections) {
                if (onProgressNeeded != null) {
                    LaunchedEffect(it.id) { onProgressNeeded(it.id) }
                }
                CollectionImageCard(
                    collection = it,
                    onCollectionClick = { onCollectionClick(it.id) },
                    onCollectionDelete = { onCollectionDelete(it.id) },
                    modifier = Modifier.fillMaxSize(),
                    progress = progressOf?.invoke(it.id),
                )
            }

            item(
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Pagination(
                    totalPages = totalPages,
                    currentPage = currentPage,
                    onPageChange = {
                        coroutineScope.launch {
                            onPageChange(it)
                            scrollState.scrollToItem(0)
                        }
                    }
                )
            }

        }

        VerticalScrollbarWithFullSpans(scrollState, Modifier.align(Alignment.TopEnd), 2)
    }
}
