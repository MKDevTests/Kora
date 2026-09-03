package snd.komelia.ui.settings.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.settings.SettingsScreenContainer
import snd.komelia.ui.settings.offline.downloads.OfflineDownloadsContent
import snd.komelia.ui.settings.offline.logs.OfflineLogsContent
import snd.komelia.ui.settings.offline.users.OfflineUserSettingsContent
import snd.komelia.ui.LocalStrings
import androidx.compose.material.icons.filled.Tune
import snd.komelia.ui.settings.offline.policy.DownloadPolicyContent

class OfflineSettingsScreen : Screen {

    @Composable
    override fun Content() {
        val currentNavigator = LocalNavigator.currentOrThrow
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getOfflineModeSettingsViewModel() }

        LaunchedEffect(Unit) {
            vm.initialize(currentNavigator)
        }

        SettingsScreenContainer(LocalStrings.current.ui.offlineMode) {
            var selectedTab by rememberSaveable { mutableStateOf(0) }

            SecondaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.heightIn(min = 40.dp).pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Default.Person, null)
                        Text(LocalStrings.current.ui.users)
                    }
                }
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.heightIn(min = 40.dp).pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Default.Download, null)
                        Text(LocalStrings.current.ui.downloads)
                    }
                }

                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    modifier = Modifier.heightIn(min = 40.dp).pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Default.Tune, null)
                        Text(LocalStrings.current.ui.downloadPolicy)
                    }
                }

                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    modifier = Modifier.heightIn(min = 40.dp).pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Default.Cached, null)
                        Text(LocalStrings.current.ui.logs)
                    }
                }
            }
            when (selectedTab) {
                0 -> {
                    val userState = vm.usersState
                    OfflineUserSettingsContent(
                        currentUser = userState.currentUser.collectAsState().value,
                        onlineServerUrl = userState.onlineServerUrl.collectAsState().value,
                        isOffline = userState.isOffline.collectAsState(false).value,
                        goOnline = userState::goOnline,
                        loginAs = userState::loginAs,
                        serverUsers = userState.offlineUsers.collectAsState().value,
                        onServerDelete = userState::onServerDelete,
                        onUserDelete = userState::onUserDelete,
                    )
                }

                1 -> {
                    val downloadsState = vm.downloadsSate
                    OfflineDownloadsContent(
                        storageLocation = downloadsState.storageLocation.collectAsState().value,
                        onStorageLocationChange = downloadsState::onStorageLocationChange,
                        onStorageLocationReset = downloadsState::onStorageLocationReset,
                        downloads = downloadsState.downloads.collectAsState().value,
                        onDownloadCancel = downloadsState::onDownloadCancel,
                        scanState = downloadsState.scanState.collectAsState().value,
                        onScanClick = downloadsState::onScanClick,
                        onScanDialogClose = downloadsState::onScanDialogClose
                    )
                }

                2 -> {
                    val policy = vm.policyState
                    DownloadPolicyContent(
                        wifiOnly = policy.wifiOnly.collectAsState().value,
                        onWifiOnlyChange = policy::onWifiOnlyChange,
                        whileChargingOnly = policy.whileChargingOnly.collectAsState().value,
                        onWhileChargingOnlyChange = policy::onWhileChargingOnlyChange,
                        storageLimitMb = policy.storageLimitMb.collectAsState().value,
                        onStorageLimitChange = policy::onStorageLimitChange,
                        cleanupReadAfterDays = policy.cleanupReadAfterDays.collectAsState().value,
                        onCleanupReadAfterDaysChange = policy::onCleanupReadAfterDaysChange,
                        cleanupIncludeManual = policy.cleanupIncludeManual.collectAsState().value,
                        onCleanupIncludeManualChange = policy::onCleanupIncludeManualChange,
                        usedBytes = policy.usedBytes.collectAsState().value,
                        isCleaning = policy.isCleaning.collectAsState().value,
                        onCleanupNow = policy::onCleanupNow,
                        autoDownloadEnabled = policy.autoDownloadEnabled.collectAsState().value,
                        onAutoDownloadEnabledChange = policy::onAutoDownloadEnabledChange,
                        autoDownloadMaxSeries = policy.autoDownloadMaxSeries.collectAsState().value,
                        onAutoDownloadMaxSeriesChange = policy::onAutoDownloadMaxSeriesChange,
                        autoDownloadBooksAhead = policy.autoDownloadBooksAhead.collectAsState().value,
                        onAutoDownloadBooksAheadChange = policy::onAutoDownloadBooksAheadChange,
                        libraries = policy.libraries.collectAsState().value,
                        autoDownloadLibraryIds = policy.autoDownloadLibraryIds.collectAsState().value,
                        onAutoDownloadLibraryToggle = policy::onAutoDownloadLibraryToggle,
                    )
                }

                3 -> {
                    val state = vm.logsState
                    OfflineLogsContent(
                        logs = state.logs.collectAsState().value,
                        totalPages = state.totalPages.collectAsState().value,
                        currentPage = state.pageNumber.collectAsState().value,
                        onPageChange = state::onPageChange,
                        selectedTab = state.tab.collectAsState().value,
                        onTabSelect = state::onTabChange,
                        onDelete = state::onLogsDelete
                    )
                }
            }


        }
    }
}