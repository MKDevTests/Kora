package snd.komelia.ui.common.menus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.LabelOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewQuilt
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import snd.komelia.ui.LocalSeriesRatingsRepository
import snd.komelia.ui.common.components.RatingStars
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import snd.komelia.AppNotification
import snd.komelia.AppNotifications
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.offline.tasks.OfflineTaskEmitter
import snd.komelia.ui.LocalKomfIntegration
import snd.komelia.ui.LocalKomgaState
import snd.komelia.ui.LocalOfflineMode
import snd.komelia.ui.LocalUseImmersiveMorphingCover
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.dialogs.collectionadd.AddToCollectionDialog
import snd.komelia.ui.dialogs.komf.identify.KomfIdentifyDialog
import snd.komelia.ui.dialogs.komf.reset.KomfResetSeriesMetadataDialog
import snd.komelia.ui.dialogs.permissions.DownloadNotificationRequestDialog
import snd.komelia.ui.dialogs.series.edit.SeriesEditDialog
import snd.komga.client.series.KomgaSeries

@Composable
fun SeriesActionsMenu(
    series: KomgaSeries,
    actions: SeriesMenuActions,
    expanded: Boolean,
    showEditOption: Boolean,
    showDownloadOption: Boolean,
    onDismissRequest: () -> Unit,
    onToggleImmersiveMode: (() -> Unit)? = null,
    onOpenInKomga: (() -> Unit)? = null,
) {
    val isAdmin = LocalKomgaState.current.authenticatedUser.collectAsState().value?.roleAdmin() ?: true
    val isOffline = LocalOfflineMode.current.collectAsState().value

    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) {
        ConfirmationDialog(
            title = "Delete Series",
            body = "The Series ${series.metadata.title} will be removed from this server alongside with stored media files. This cannot be undone. Continue?",
            confirmText = "Yes, delete series \"${series.metadata.title}\"",
            onDialogConfirm = {
                actions.delete(series)
                onDismissRequest()

            },
            onDialogDismiss = {
                showDeleteDialog = false
                onDismissRequest()
            },
            buttonConfirmColor = MaterialTheme.colorScheme.errorContainer
        )
    }
    var showDeleteDownloadedDialog by remember { mutableStateOf(false) }
    if (showDeleteDownloadedDialog) {
        ConfirmationDialog(
            title = "Delete downloaded series",
            body = "The series ${series.metadata.title} will be removed from this device",
            onDialogConfirm = {
                actions.deleteDownloaded(series)
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
        SeriesEditDialog(series, onDismissRequest = {
            showEditDialog = false
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

    var showAddToCollectionDialog by remember { mutableStateOf(false) }
    if (showAddToCollectionDialog) {
        AddToCollectionDialog(
            series = listOf(series),
            onDismissRequest = {
                showAddToCollectionDialog = false
                onDismissRequest()
            })
    }
    var showDownloadDialog by remember { mutableStateOf(false) }
    if (showDownloadDialog) {
        var permissionRequested by remember { mutableStateOf(false) }
        DownloadNotificationRequestDialog { permissionRequested = true }

        if (permissionRequested) {
            ConfirmationDialog(
                "Download series \"${series.metadata.title}\"?",
                onDialogConfirm = { actions.download(series) },
                onDialogDismiss = { showDownloadDialog = false }
            )
        }
    }

    val showDropdown = derivedStateOf {
        expanded &&
                !showDeleteDialog &&
                !showKomfDialog &&
                !showKomfResetDialog &&
                !showEditDialog &&
                !showAddToCollectionDialog
    }
    AnimatedDropdownMenu(
        expanded = showDropdown.value,
        onDismissRequest = onDismissRequest
    ) {
        // Rate row at the top — read+write of the local SeriesRatingsRepository.
        // Inline stars rather than "open a separate picker" so the user can
        // assign/edit a rating in one tap without nested dialogs. Tapping the
        // current top star clears the rating (delegated to RatingStars).
        val ratingsRepo = LocalSeriesRatingsRepository.current
        val ratingScope = rememberCoroutineScope()
        val currentRating = remember(series.id) { ratingsRepo.observe(series.id) }
            .collectAsState(initial = null).value
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("Rate", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            RatingStars(
                rating = currentRating?.stars ?: 0,
                size = 22.dp,
                onRatingChange = { newStars ->
                    ratingScope.launch {
                        if (newStars == 0) ratingsRepo.delete(series.id)
                        else ratingsRepo.put(series.id, newStars)
                    }
                },
            )
        }
        HorizontalDivider()

        if (isAdmin && !isOffline) {
            DropdownMenuItem(
                text = { Text("Analyze", style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                onClick = {
                    actions.analyze(series)
                    onDismissRequest()
                }
            )

            DropdownMenuItem(
                text = { Text("Refresh metadata", style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                onClick = {
                    actions.refreshMetadata(series)
                    onDismissRequest()
                }
            )

            DropdownMenuItem(
                text = { Text("Add to collection", style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Add, null) },
                onClick = { showAddToCollectionDialog = true },
            )
        }

        val isRead = remember { series.booksReadCount == series.booksCount }
        val isUnread = remember { series.booksUnreadCount == series.booksCount }
        if (!isRead) {
            DropdownMenuItem(
                text = { Text("Mark as read", style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Label, null) },
                onClick = {
                    actions.markAsRead(series)
                    onDismissRequest()
                },
            )
        }

        if (!isUnread) {
            DropdownMenuItem(
                text = { Text("Mark as unread", style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.LabelOff, null) },
                onClick = {
                    actions.markAsUnread(series)
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

        if (isOffline) {
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

        val komfIntegration = LocalKomfIntegration.current.collectAsState(false)
        if (komfIntegration.value) {
            DropdownMenuItem(
                text = { Text("Identify (Komf)", style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                onClick = { showKomfDialog = true },
            )

            DropdownMenuItem(
                text = { Text("Reset Metadata (Komf)", style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                onClick = { showKomfResetDialog = true },
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

        if (onOpenInKomga != null) {
            DropdownMenuItem(
                text = { Text("Open in Komga", style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) },
                onClick = {
                    onOpenInKomga()
                    onDismissRequest()
                }
            )
        }
    }
}

data class SeriesMenuActions(
    val analyze: (KomgaSeries) -> Unit,
    val refreshMetadata: (KomgaSeries) -> Unit,
    val addToCollection: (KomgaSeries) -> Unit,
    val markAsRead: (KomgaSeries) -> Unit,
    val markAsUnread: (KomgaSeries) -> Unit,
    val delete: (KomgaSeries) -> Unit,
    val download: (KomgaSeries) -> Unit,
    val deleteDownloaded: (KomgaSeries) -> Unit,
) {
    constructor(
        seriesApi: KomgaSeriesApi,
        notifications: AppNotifications,
        taskEmitter: OfflineTaskEmitter,
        scope: CoroutineScope,
    ) : this(
        analyze = {
            notifications.runCatchingToNotifications(scope) {
                seriesApi.analyze(it.id)
                notifications.add(AppNotification.Normal("Launched series analysis"))
            }
        },
        refreshMetadata = {
            notifications.runCatchingToNotifications(scope) {
                seriesApi.refreshMetadata(it.id)
                notifications.add(AppNotification.Normal("Launched series metadata refresh"))
            }
        },
        addToCollection = { },
        markAsRead = {
            notifications.runCatchingToNotifications(scope) { seriesApi.markAsRead(it.id) }
        },
        markAsUnread = {
            notifications.runCatchingToNotifications(scope) { seriesApi.markAsUnread(it.id) }
        },
        delete = {
            notifications.runCatchingToNotifications(scope) { seriesApi.delete(it.id) }
        },
        download = { scope.launch { taskEmitter.downloadSeries(it.id) } },
        deleteDownloaded = { scope.launch { taskEmitter.deleteSeries(it.id) } }
    )
}
