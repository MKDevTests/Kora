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
import snd.komelia.ui.LocalIgnoreList
import snd.komelia.ui.LocalHiddenAdmin
import snd.komelia.ui.LocalFavorites
import snd.komelia.ui.LocalPlanned
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import snd.komga.client.series.KomgaSeries
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.LocalAutoDownload
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.OfflinePin

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
    onSelect: (() -> Unit)? = null,
) {
    val isAdmin = LocalKomgaState.current.authenticatedUser.collectAsState().value?.roleAdmin() ?: true
    val isOffline = LocalOfflineMode.current.collectAsState().value

    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) {
        ConfirmationDialog(
            title = LocalStrings.current.ui.deleteSeries2,
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
            title = LocalStrings.current.ui.deleteDownloadedSeries,
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
        if (onSelect != null) {
            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.select, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Checklist, null) },
                onClick = {
                    onSelect()
                    onDismissRequest()
                }
            )
        }
        val ignoreList = LocalIgnoreList.current
        if (ignoreList != null && ignoreList.enabled.collectAsState().value) {
            val isIgnored = series.id.value in ignoreList.ignoredIds.collectAsState().value
            DropdownMenuItem(
                text = {
                    Text(
                        if (isIgnored) "Restore (stop ignoring)" else "Ignore",
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                leadingIcon = {
                    Icon(if (isIgnored) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, null)
                },
                onClick = {
                    if (isIgnored) ignoreList.unignore(listOf(series.id))
                    else ignoreList.ignore(listOf(series.id))
                    onDismissRequest()
                }
            )
        }
        // Local per-user Favorites (any user, no admin gate).
        val favorites = LocalFavorites.current
        if (favorites != null) {
            val isFavorite = series.id.value in favorites.favoriteIds.collectAsState().value
            DropdownMenuItem(
                text = {
                    Text(
                        if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                leadingIcon = {
                    Icon(if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, null)
                },
                onClick = {
                    favorites.toggle(series.id)
                    onDismissRequest()
                }
            )
        }
        // Per-series bound on the automatic downloader. Only offered when the
        // feature can act on it: three menu entries for a planner that is
        // switched off would be three entries that do nothing visible.
        val autoDownload = LocalAutoDownload.current
        if (autoDownload != null) {
            val isPinned = series.id.value in autoDownload.pinnedIds.collectAsState().value
            val isExcluded = series.id.value in autoDownload.excludedIds.collectAsState().value
            DropdownMenuItem(
                text = {
                    Text(
                        if (isPinned) LocalStrings.current.ui.autoDownloadUnpin
                        else LocalStrings.current.ui.autoDownloadPin,
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                leadingIcon = {
                    Icon(if (isPinned) Icons.Rounded.OfflinePin else Icons.Rounded.CloudDownload, null)
                },
                onClick = {
                    autoDownload.togglePinned(series.id)
                    onDismissRequest()
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (isExcluded) LocalStrings.current.ui.autoDownloadUnexclude
                        else LocalStrings.current.ui.autoDownloadExclude,
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                leadingIcon = {
                    Icon(if (isExcluded) Icons.Rounded.CloudQueue else Icons.Rounded.CloudOff, null)
                },
                onClick = {
                    autoDownload.toggleExcluded(series.id)
                    onDismissRequest()
                }
            )
        }
        // Local per-user "Planned" (a lire) — independent from Favorites.
        val planned = LocalPlanned.current
        if (planned != null) {
            val isPlanned = series.id.value in planned.plannedIds.collectAsState().value
            DropdownMenuItem(
                text = {
                    Text(
                        if (isPlanned) "Retirer de « à lire »" else "Marquer « à lire »",
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                leadingIcon = {
                    Icon(if (isPlanned) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null)
                },
                onClick = {
                    planned.toggle(series.id)
                    onDismissRequest()
                }
            )
        }
        // Admin "hide for everyone" (kora:hidden) — server-shared, admin-gated.
        val hiddenAdmin = LocalHiddenAdmin.current
        if (isAdmin && hiddenAdmin != null) {
            val isHidden = series.id.value in hiddenAdmin.hiddenIds.collectAsState().value
            DropdownMenuItem(
                text = {
                    Text(
                        if (isHidden) "Réafficher pour tous" else "Masquer pour tous",
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                leadingIcon = { Icon(Icons.Rounded.Public, null) },
                onClick = {
                    if (isHidden) hiddenAdmin.unhide(listOf(series.id))
                    else hiddenAdmin.hide(listOf(series.id))
                    onDismissRequest()
                }
            )
        }
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
            Text(LocalStrings.current.ui.rate, style = MaterialTheme.typography.labelLarge)
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
                text = { Text(LocalStrings.current.ui.analyze, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                onClick = {
                    actions.analyze(series)
                    onDismissRequest()
                }
            )

            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.refreshMetadata, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                onClick = {
                    actions.refreshMetadata(series)
                    onDismissRequest()
                }
            )

            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.addToCollection, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Add, null) },
                onClick = { showAddToCollectionDialog = true },
            )
        }

        val isRead = remember { series.booksReadCount == series.booksCount }
        val isUnread = remember { series.booksUnreadCount == series.booksCount }
        if (!isRead) {
            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.markAsRead, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Label, null) },
                onClick = {
                    actions.markAsRead(series)
                    onDismissRequest()
                },
            )
        }

        if (!isUnread) {
            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.markAsUnread, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.LabelOff, null) },
                onClick = {
                    actions.markAsUnread(series)
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

        if (!isOffline && showDownloadOption) {
            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.download, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Rounded.Download, null) },
                onClick = { showDownloadDialog = true },
            )
        }

        if (isOffline) {
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
                text = { Text(LocalStrings.current.ui.deleteFromServer, style = MaterialTheme.typography.labelLarge) },
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
                text = { Text(LocalStrings.current.ui.openInKomga, style = MaterialTheme.typography.labelLarge) },
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
