package snd.komelia.ui.common.menus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.LabelOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import snd.komelia.AppNotification
import snd.komelia.AppNotifications
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.tasks.OfflineTaskEmitter
import snd.komelia.ui.LocalKomgaState
import snd.komelia.ui.LocalOfflineMode
import snd.komelia.ui.LocalUseImmersiveMorphingCover
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.dialogs.book.edit.BookEditDialog
import snd.komelia.ui.dialogs.permissions.DownloadNotificationRequestDialog
import snd.komelia.ui.dialogs.readlistadd.AddToReadListDialog
import snd.komga.client.book.KomgaBookReadProgressUpdateRequest

@Composable
fun BookActionsMenu(
    book: KomeliaBook,
    actions: BookMenuActions,
    expanded: Boolean,
    showEditOption: Boolean,
    showDownloadOption: Boolean,
    onDismissRequest: () -> Unit,
    onToggleImmersiveMode: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
) {
    val isAdmin = LocalKomgaState.current.authenticatedUser.collectAsState().value?.roleAdmin() ?: true
    val isOffline = LocalOfflineMode.current.collectAsState().value
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteDownloadedDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) {
        ConfirmationDialog(
            title = "Delete Book",
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
            title = "Delete downloaded Book",
            body = "Book ${book.metadata.title} will be removed from this device",
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
        BookEditDialog(book, onDismissRequest = {
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
    var showDownloadDialog by remember { mutableStateOf(false) }
    if (showDownloadDialog) {
        var permissionRequested by remember { mutableStateOf(false) }
        DownloadNotificationRequestDialog { permissionRequested = true }

        if (permissionRequested) {
            ConfirmationDialog(
                "Download book \"${book.metadata.title}\"?",
                onDialogConfirm = { actions.download(book) },
                onDialogDismiss = { showDownloadDialog = false }
            )
        }
    }

    val showDropdown = derivedStateOf { expanded && !showDeleteDialog && !showEditDialog }
    AnimatedDropdownMenu(
        expanded = showDropdown.value,
        onDismissRequest = onDismissRequest
    ) {
        if (onSelect != null) {
            DropdownMenuItem(
                text = { Text("Select", style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Checklist, null) },
                onClick = {
                    onSelect()
                    onDismissRequest()
                }
            )
        }
        if (isAdmin && !isOffline) {
            DropdownMenuItem(
                text = { Text("Analyze", style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                onClick = {
                    actions.analyze(book)
                    onDismissRequest()
                }
            )

            DropdownMenuItem(
                text = { Text("Refresh metadata", style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                onClick = {
                    actions.refreshMetadata(book)
                    onDismissRequest()
                }
            )

            DropdownMenuItem(
                text = { Text("Add to read list", style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Add, null) },
                onClick = { showAddToReadListDialog = true },
            )
        }

        val isRead = remember { book.readProgress?.completed ?: false }
        val isUnread = remember { book.readProgress == null }

        if (!isRead) {
            DropdownMenuItem(
                text = { Text("Mark as read", style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Label, null) },
                onClick = {
                    actions.markAsRead(book)
                    onDismissRequest()
                },
            )
        }

        if (!isUnread) {
            DropdownMenuItem(
                text = { Text("Mark as unread", style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.LabelOff, null) },
                onClick = {
                    actions.markAsUnread(book)
                    onDismissRequest()
                },
            )
        }

        if (isAdmin && !isOffline && showEditOption) {
            DropdownMenuItem(
                text = { Text("Edit", style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                onClick = { showEditDialog = true },
            )
        }
        if (!isOffline && showDownloadOption) {
            DropdownMenuItem(
                text = { Text("Download", style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Download, null) },
                onClick = { showDownloadDialog = true },
            )
        }

        if (book.downloaded) {
            DropdownMenuItem(
                text = { Text("Delete downloaded", style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = { showDeleteDownloadedDialog = true },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error
                )
            )

        }

        if (isAdmin && !isOffline) {
            DropdownMenuItem(
                text = { Text("Delete from server", style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.DeleteForever, null) },
                onClick = { showDeleteDialog = true },
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

data class BookMenuActions(
    val analyze: (KomeliaBook) -> Unit,
    val refreshMetadata: (KomeliaBook) -> Unit,
    val markAsRead: (KomeliaBook) -> Unit,
    val markAsUnread: (KomeliaBook) -> Unit,
    val delete: (KomeliaBook) -> Unit,
    val download: (KomeliaBook) -> Unit,
    val deleteDownloaded: (KomeliaBook) -> Unit,
) {
    constructor(
        bookApi: KomgaBookApi,
        notifications: AppNotifications,
        scope: CoroutineScope,
        taskEmitter: OfflineTaskEmitter
    ) : this(
        analyze = {
            notifications.runCatchingToNotifications(scope) {
                bookApi.analyze(it.id)
                notifications.add(AppNotification.Normal("Launched book analysis"))
            }
        },
        refreshMetadata = {
            notifications.runCatchingToNotifications(scope) {
                bookApi.refreshMetadata(it.id)
                notifications.add(AppNotification.Normal("Launched book metadata refresh"))
            }
        },
        markAsRead = { book ->
            notifications.runCatchingToNotifications(scope) {
                bookApi.markReadProgress(
                    book.id,
                    KomgaBookReadProgressUpdateRequest(completed = true)
                )
            }
        },
        markAsUnread = {
            notifications.runCatchingToNotifications(scope) { bookApi.deleteReadProgress(it.id) }
        },
        delete = {
            notifications.runCatchingToNotifications(scope) { bookApi.deleteBook(it.id) }
        },
        download = { scope.launch { taskEmitter.downloadBook(it.id) } },
        deleteDownloaded = { scope.launch { taskEmitter.deleteBook(it.id) } }
    )
}
