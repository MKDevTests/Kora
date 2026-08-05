package snd.komelia.ui.common.menus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.DropdownMenuItem
import snd.komelia.ui.common.components.AnimatedDropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.dialogs.readlistedit.ReadListEditDialog
import snd.komga.client.readlist.KomgaReadList
import snd.komelia.ui.LocalStrings

@Composable
fun ReadListActionsMenu(
    readList: KomgaReadList,
    onReadListDelete: () -> Unit,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) {
        ConfirmationDialog(
            title = LocalStrings.current.ui.deleteReadList,
            body = "Read list ${readList.name} will be removed from this server. Your media files will not be affected. This cannot be undone. Continue?",
            confirmText = "Yes, delete read list \"${readList.name}\"",
            onDialogConfirm = {
                onReadListDelete()
                onDismissRequest()

            },
            onDialogDismiss = {
                showDeleteDialog = false
                onDismissRequest()
            },
            buttonConfirmColor = MaterialTheme.colorScheme.errorContainer
        )
    }
    var showEditDialog by remember { mutableStateOf(false) }
    if (showEditDialog) {
        ReadListEditDialog(readList = readList, onDismissRequest = {
            showEditDialog = false
            onDismissRequest()
        })
    }

    val showDropdown = derivedStateOf { expanded && !showDeleteDialog }
    AnimatedDropdownMenu(
        expanded = showDropdown.value,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = { Text(LocalStrings.current.ui.edit, style = MaterialTheme.typography.labelLarge) },
            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
            onClick = { showEditDialog = true },
        )

        DropdownMenuItem(
            text = { Text(LocalStrings.current.ui.delete, style = MaterialTheme.typography.labelLarge) },
            leadingIcon = { Icon(Icons.Rounded.DeleteForever, null) },
            onClick = { showDeleteDialog = true },
            colors = MenuDefaults.itemColors(
                textColor = MaterialTheme.colorScheme.error,
                leadingIconColor = MaterialTheme.colorScheme.error
            )
        )
    }
}