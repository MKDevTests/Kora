package snd.komelia.ui.settings.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.LocalStrings

@Composable
fun ServerManagementContent(
    onScanAllLibraries: (deep: Boolean) -> Unit,
    onEmptyTrash: () -> Unit,
    onCancelAllTasks: () -> Unit,
    onShutdown: () -> Unit
) {

    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var showShutdownDialog by remember { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(LocalStrings.current.ui.serverManagement, style = MaterialTheme.typography.titleLarge)
        HorizontalDivider()
        Button(
            title = LocalStrings.current.ui.scanAllLibraries,
            description = LocalStrings.current.ui.checkFoldersForNewOr,
            buttonText = "Scan",
            level = WarningLevel.NORMAL,
            onClick = { onScanAllLibraries(false) }
        )
        HorizontalDivider()
        Button(
            title = LocalStrings.current.ui.deepScanAllLibraries,
            description = LocalStrings.current.ui.forceTheScannerToCompare,
            buttonText = "Deep Scan",
            level = WarningLevel.NORMAL,
            onClick = { onScanAllLibraries(true) }
        )
        HorizontalDivider()
        Button(
            title = LocalStrings.current.ui.emptyTrashForAllLibraries,
            description = LocalStrings.current.ui.deleteItemsMarkedAsUnavailable,
            buttonText = "Empty",
            level = WarningLevel.NORMAL,
            onClick = { showEmptyTrashDialog = true }
        )
        HorizontalDivider()
        Button(
            title = LocalStrings.current.ui.cancelAllTasks,
            description = LocalStrings.current.ui.cancelAllCurrentlyRunningTasks,
            buttonText = "Cancel",
            level = WarningLevel.WARNING,
            onClick = { onCancelAllTasks() }
        )
        HorizontalDivider()
        Button(
            title = LocalStrings.current.ui.shutdown,
            description = LocalStrings.current.ui.stopKomgaApplicationProcess,
            buttonText = "Shutdown",
            level = WarningLevel.DANGER,
            onClick = { showShutdownDialog = true }
        )
        HorizontalDivider()

        if (showEmptyTrashDialog) {
            ConfirmationDialog(
                title = LocalStrings.current.ui.emptyTrashForLibrary,
                body = "By default the media server doesn't remove information for media right away. This helps if a drive is temporarily disconnected. When you empty the trash for a library, all information about missing media is deleted.",
                buttonConfirm = "Empty",
                buttonCancel = "Cancel",
                onDialogConfirm = onEmptyTrash,
                onDialogDismiss = { showEmptyTrashDialog = false }
            )
        }

        if (showShutdownDialog) {
            ConfirmationDialog(
                title = LocalStrings.current.ui.shutDownServer,
                body = "Are you sure you want to stop Komga?",
                buttonConfirm = "Stop",
                buttonCancel = "Cancel",
                buttonConfirmColor = MaterialTheme.colorScheme.errorContainer,
                onDialogConfirm = onShutdown,
                onDialogDismiss = { showShutdownDialog = false }
            )
        }

    }
}

@Composable
private fun Button(
    title: String,
    description: String,
    buttonText: String,
    level: WarningLevel,
    onClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.labelLarge)
        }

        val colors = when (level) {
            WarningLevel.NORMAL -> ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )

            WarningLevel.WARNING -> ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )

            WarningLevel.DANGER -> ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        FilledTonalButton(
            onClick = onClick,
            colors = colors,
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
        ) {
            Text(buttonText)
        }

    }
}


private enum class WarningLevel {
    NORMAL,
    WARNING,
    DANGER
}