package snd.komelia.ui.library.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.common.itemlist.CollectionLazyCardGrid
import snd.komelia.ui.common.itemlist.PlaceHolderLazyCardGrid
import snd.komga.client.collection.KomgaCollection
import snd.komga.client.collection.KomgaCollectionId

@Composable
fun LibraryCollectionsContent(
    collections: List<KomgaCollection>,
    collectionsTotalCount: Int,
    onCollectionClick: (KomgaCollectionId) -> Unit,
    onCollectionDelete: (KomgaCollectionId) -> Unit,
    isLoading: Boolean,

    totalPages: Int,
    currentPage: Int,
    pageSize: Int,
    onPageChange: (Int) -> Unit,
    onPageSizeChange: (Int) -> Unit,

    minSize: Dp,
    beforeContent: (@Composable () -> Unit)? = null,
    progressOf: ((KomgaCollectionId) -> Float?)? = null,
    onProgressNeeded: ((KomgaCollectionId) -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.Center) {

        if (isLoading) {
            if (collectionsTotalCount > pageSize) PlaceHolderLazyCardGrid(pageSize, minSize)
            else LoadingMaxSizeIndicator()
        } else {
            CollectionLazyCardGrid(
                collections = collections,
                onCollectionClick = onCollectionClick,
                onCollectionDelete = onCollectionDelete,
                totalPages = totalPages,
                currentPage = currentPage,
                onPageChange = onPageChange,
                minSize = minSize,
                beforeContent = beforeContent,
                progressOf = progressOf,
                onProgressNeeded = onProgressNeeded,
            )
        }
    }
}
