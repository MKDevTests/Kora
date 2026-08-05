package snd.komelia.ui.settings.offline.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.vinceglb.filekit.PlatformFile
import snd.komelia.formatDecimal
import snd.komelia.offline.sync.model.DownloadEvent
import snd.komelia.offline.sync.model.OfflineScanResult
import snd.komelia.ui.dialogs.permissions.StoragePermissionRequestDialog
import snd.komga.client.book.KomgaBookId
import kotlin.coroutines.cancellation.CancellationException
import snd.komelia.ui.LocalStrings

@Composable
fun OfflineDownloadsContent(
    storageLocation: PlatformFile?,
    onStorageLocationChange: (PlatformFile) -> Unit,
    onStorageLocationReset: () -> Unit,
    downloads: Collection<DownloadEvent>,
    onDownloadCancel: (KomgaBookId) -> Unit,
    scanState: OfflineScanState,
    onScanClick: () -> Unit,
    onScanDialogClose: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (storageLocation != null) {
            Column {
                Text(LocalStrings.current.ui.storageLocation)
                Text(
                    rememberStorageLabel(storageLocation),
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }

        var showDirectoryPickerDialog by remember { mutableStateOf(false) }
        if (showDirectoryPickerDialog) {
            StoragePermissionRequestDialog { directory ->
                if (directory != null) {
                    onStorageLocationChange(directory)
                }
                showDirectoryPickerDialog = false
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { showDirectoryPickerDialog = true }) { Text(LocalStrings.current.ui.changeLocation) }
            Button(onClick = onStorageLocationReset) { Text(LocalStrings.current.ui.resetToInternal) }
        }

        Button(onClick = onScanClick) { Text(LocalStrings.current.ui.scanForExistingFiles) }

        HorizontalDivider()
        for (event in downloads) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(5.dp)
                    .fillMaxWidth()
            ) {
                when (event) {
                    is DownloadEvent.BookDownloadProgress -> DownloadProgress(event, onDownloadCancel)
                    is DownloadEvent.BookDownloadCompleted -> DownloadCompleted(event)
                    is DownloadEvent.BookDownloadError -> DownloadError(event)
                }
            }
        }
    }

    if (scanState !is OfflineScanState.Idle) {
        OfflineScanDialog(scanState, onScanDialogClose)
    }
}

@Composable
private fun OfflineScanDialog(
    state: OfflineScanState,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = { if (state is OfflineScanState.Finished) onClose() }) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = when (state) {
                        is OfflineScanState.Scanning -> "Scanning..."
                        is OfflineScanState.Finished -> "Scan Finished"
                        else -> ""
                    },
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(Modifier.height(16.dp))

                when (state) {
                    is OfflineScanState.Scanning -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text(LocalStrings.current.counts.processedFiles(state.count))
                        state.lastResult?.let { result ->
                            Text(
                                "Current: ${result.bookName}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                    }

                    is OfflineScanState.Finished -> {
                        ScanReportContent(state.report)
                    }

                    OfflineScanState.Idle -> {}
                }

                Spacer(Modifier.weight(1f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) {
                        Text(if (state is OfflineScanState.Finished) "Close" else "Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanReportContent(report: OfflineScanReport) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ReportStat("Imported", report.importedCount, Color(0xFF4CAF50))
            ReportStat("Updated", report.updatedCount, Color(0xFF2196F3))
            ReportStat("Out of Sync", report.outOfSyncCount, Color(0xFFFFC107))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ReportStat("No Match", report.noMatchCount, Color(0xFFF44336))
            ReportStat("Indexed", report.alreadyIndexedCount, Color.Gray)
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(report.results) { result ->
                ScanResultItem(result)
            }
        }
    }
}

@Composable
private fun ReportStat(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ScanResultItem(result: OfflineScanResult) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        val icon = when (result) {
            is OfflineScanResult.Imported -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
            is OfflineScanResult.Updated -> Icons.Default.CheckCircle to Color(0xFF2196F3)
            is OfflineScanResult.AlreadyIndexed -> Icons.Default.CheckCircle to Color.Gray
            is OfflineScanResult.OutOfSync -> Icons.Default.Warning to Color(0xFFFFC107)
            is OfflineScanResult.NoMatch -> Icons.Default.Error to Color(0xFFF44336)
        }

        Icon(icon.first, null, tint = icon.second, modifier = Modifier.size(20.dp))
        Column {
            Text(result.bookName, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${result.seriesName} (${result.libraryName})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (result is OfflineScanResult.NoMatch && result.error != null) {
                Text(result.error!!, style = MaterialTheme.typography.labelSmall, color = Color.Red)
            }
        }
    }
}

@Composable
private fun DownloadProgress(
    event: DownloadEvent.BookDownloadProgress,
    onCancel: (KomgaBookId) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DownloadProgressIndicator(event)
        IconButton(onClick = { onCancel(event.book.id) }) { Icon(Icons.Default.Cancel, null) }
    }
}

@Composable
private fun RowScope.DownloadProgressIndicator(
    event: DownloadEvent.BookDownloadProgress,
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(event.book.metadata.title)
        if (event.total == 0L) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { event.completed / event.total.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )

            val totalMiB = remember(event.total) {
                (event.total.toFloat() / 1024 / 1024).formatDecimal(2)
            }
            val completedMiB = remember(event.completed) {
                (event.completed.toFloat() / 1024 / 1024).formatDecimal(2)
            }
            Text("${completedMiB}MiB / ${totalMiB}MiB")
        }
    }
}

@Composable
private fun DownloadCompleted(event: DownloadEvent.BookDownloadCompleted) {
    Column {
        Text(event.book.metadata.title)
        Text(LocalStrings.current.ui.downloadComplete)
    }
}

@Composable
private fun DownloadError(event: DownloadEvent.BookDownloadError) {
    Column {
        Text(event.book?.metadata?.title ?: event.bookId.value)
        val errorMessage = remember {
            if (event.error is CancellationException) "Cancelled"
            else "${event.error::class.simpleName}: ${event.error.message}"
        }
        Text(errorMessage, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal expect fun rememberStorageLabel(file: PlatformFile): String
