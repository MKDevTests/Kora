package snd.komelia.ui.common.menus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.LabelOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewQuilt
import androidx.compose.material3.DropdownMenuItem
import snd.komelia.ui.common.components.AnimatedDropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LocalKomfIntegration
import snd.komelia.ui.LocalKomgaState
import snd.komelia.ui.LocalOfflineMode
import snd.komelia.ui.LocalUseImmersiveMorphingCover
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.dialogs.collectionadd.AddToCollectionDialog
import snd.komelia.ui.dialogs.komf.identify.KomfIdentifyDialog
import snd.komelia.ui.dialogs.komf.reset.KomfResetSeriesMetadataDialog
import snd.komelia.ui.dialogs.oneshot.OneshotEditDialog
import snd.komelia.ui.dialogs.readlistadd.AddToReadListDialog
import snd.komga.client.series.KomgaSeries
import snd.komelia.ui.LocalStrings

@Composable
fun OneshotActionsMenu(
    series: KomgaSeries,
    book: KomeliaBook,
    actions: BookMenuActions,
    expanded: Boolean,
    showEditOption: Boolean = false,
    onDismissRequest: () -> Unit,
    onToggleImmersiveMode: (() -> Unit)? = null,
) {
    val isAdmin = LocalKomgaState.current.authenticatedUser.collectAsState().value?.roleAdmin() ?: true
    val isOffline = LocalOfflineMode.current.collectAsState().value
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteDownloadedDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) {
        ConfirmationDialog(
            title = LocalStrings.current.ui.deleteBook,
            body = "The Book ${book.metadata.title} will be removed from this server alongside with stored media files. This cannot be undone. Continue?",
            confirmText = "Yes, delete book \"${book.metadata.title}\"",
            onDialogConfirm = {
                actions.delete(book)
                onDismissRequest()

            },
            onDialogDismiss = {
                showDeleteDialog = false
                onDismissRequest()
            },
            buttonConfirmColor = MaterialTheme.colorScheme.errorContainer
        )
    }
    if (showDeleteDownloadedDialog) {
        ConfirmationDialog(
            title = LocalStrings.current.ui.deleteDownloadedBook,
            body = "The Book ${book.metadata.title} will be removed from this device only",
            onDialogConfirm = {
                actions.deleteDownloaded(book)
                onDismissRequest()
            },
            onDialogDismiss = {
                showDeleteDownloadedDialog = false
                onDismissRequest()
            },
            buttonConfirmColor = MaterialTheme.colorScheme.errorContainer
        )
    }

    var showEditDialog by remember { mutableStateOf(false) }
    if (showEditDialog) {
        OneshotEditDialog(
            seriesId = series.id,
            series = series,
            book = book,
            onDismissRequest = {
                showEditDialog = false
                onDismissRequest()
            })
    }

    var showAddToReadListDialog by remember { mutableStateOf(false) }
    if (showAddToReadListDialog) {
        AddToReadListDialog(
            books = listOf(book),
            onDismissRequest = {
                showAddToReadListDialog = false
                onDismissRequest()
            })
    }
    var showAddToCollectionDialog by remember { mutableStateOf(false) }
    if (showAddToCollectionDialog) {
        AddToCollectionDialog(
            series = listOf(series),
            onDismissRequest = {
                showAddToCollectionDialog = false
                onDismissRequest()
            })
    }
    var showKomfDialog by remember { mutableStateOf(false) }
    if (showKomfDialog) {
        KomfIdentifyDialog(
            series = series,
            onDismissRequest = {
                showKomfDialog = false
                onDismissRequest()
            }
        )
    }
    var showKomfResetDialog by remember { mutableStateOf(false) }
    if (showKomfResetDialog) {
        KomfResetSeriesMetadataDialog(
            series = series,
            onDismissRequest = {
                showKomfResetDialog = false
                onDismissRequest()
            }
        )
    }

    val showDropdown = derivedStateOf {
        expanded &&
                !showDeleteDialog &&
                !showKomfDialog &&
                !showKomfResetDialog &&
                !showEditDialog &&
                !showAddToCollectionDialog &&
                !showAddToReadListDialog
    }

    AnimatedDropdownMenu(
        expanded = showDropdown.value,
        onDismissRequest = onDismissRequest
    ) {
        if (isAdmin && !isOffline) {
            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.analyze, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                onClick = {
                    actions.analyze(book)
                    onDismissRequest()
                }
            )

            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.refreshMetadata, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                onClick = {
                    actions.refreshMetadata(book)
                    onDismissRequest()
                }
            )

            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.addToReadList, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Add, null) },
                onClick = { showAddToReadListDialog = true },
            )
            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.addToCollection, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Add, null) },
                onClick = { showAddToCollectionDialog = true },
            )
        }

        val isRead = remember { book.readProgress?.completed ?: false }
        val isUnread = remember { book.readProgress == null }

        if (!isRead) {
            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.markAsRead, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Label, null) },
                onClick = {
                    actions.markAsRead(book)
                    onDismissRequest()
                },
            )
        }

        if (!isUnread) {
            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.markAsUnread, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.LabelOff, null) },
                onClick = {
                    actions.markAsUnread(book)
                    onDismissRequest()
                },
            )
        }

        if (isAdmin && !isOffline && showEditOption) {
            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.edit, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                onClick = { showEditDialog = true },
            )
        }

        val komfIntegration = LocalKomfIntegration.current.collectAsState(false)
        if (komfIntegration.value) {
            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.identifyKomf, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                onClick = { showKomfDialog = true },
            )

            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.resetMetadataKomf, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                onClick = { showKomfResetDialog = true },
            )
        }

        if (isAdmin && !isOffline) {
            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.delete, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.DeleteForever, null) },
                onClick = {
                    showDeleteDialog = true
                },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error
                )
            )
        }

        if (book.downloaded) {
            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.deleteDownloaded, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = { showDeleteDownloadedDialog = true },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error
                )
            )
            }

            if (onToggleImmersiveMode != null) {
            val useImmersiveMorphingCover = LocalUseImmersiveMorphingCover.current
            DropdownMenuItem(
                text = {
                    Text(
                        if (useImmersiveMorphingCover) "Disable Morphing Cover" else "Enable Morphing Cover",
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                leadingIcon = { Icon(Icons.Rounded.ViewQuilt, null) },
                onClick = {
                    onToggleImmersiveMode()
                    onDismissRequest()
                }
            )
            }
            }
            }
