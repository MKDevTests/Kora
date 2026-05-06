package snd.komelia.ui.library.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.common.itemlist.PlaceHolderLazyCardGrid
import snd.komelia.ui.common.itemlist.ReadListLazyCardGrid
import snd.komga.client.readlist.KomgaReadList
import snd.komga.client.readlist.KomgaReadListId

@Composable
fun LibraryReadListsContent(
    readLists: List<KomgaReadList>,
    readListsTotalCount: Int,
    onReadListClick: (KomgaReadListId) -> Unit,
    onReadListDelete: (KomgaReadListId) -> Unit,
    isLoading: Boolean,


    totalPages: Int,
    currentPage: Int,
    pageSize: Int,
    onPageChange: (Int) -> Unit,
    onPageSizeChange: (Int) -> Unit,

    minSize: Dp,
    beforeContent: (@Composable () -> Unit)? = null,
    progressOf: ((KomgaReadListId) -> Float?)? = null,
    onProgressNeeded: ((KomgaReadListId) -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.Center) {

        if (isLoading) {
            if (readListsTotalCount > pageSize) PlaceHolderLazyCardGrid(pageSize, minSize)
            else LoadingMaxSizeIndicator()
        } else {
            ReadListLazyCardGrid(
                readLists = readLists,
                onReadListClick = onReadListClick,
                onReadListDelete = onReadListDelete,
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
