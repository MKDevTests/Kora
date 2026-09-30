package snd.komelia.ui.settings.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import snd.komelia.chapters.normalizeForMatch
import snd.komelia.ui.LocalOfflineMode
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.LocalTransparentNavBarPadding
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.ui.settings.account.AccountSettingsScreen
import snd.komelia.ui.settings.analysis.MediaAnalysisScreen
import snd.komelia.ui.settings.announcements.AnnouncementsScreen
import snd.komelia.ui.settings.appearance.AppSettingsScreen
import snd.komelia.ui.settings.authactivity.AuthenticationActivityScreen
import snd.komelia.ui.settings.backup.BackupSettingsScreen
import snd.komelia.ui.settings.chapters.ChapterManagementScreen
import snd.komelia.ui.settings.components.SettingsCard
import snd.komelia.ui.settings.components.SettingsIcon
import snd.komelia.ui.settings.components.SettingsRowDivider
import snd.komelia.ui.settings.components.settingsTint
import snd.komelia.ui.settings.diagnostics.DiagnosticsScreen
import snd.komelia.ui.settings.duplicates.DuplicateSeriesScreen
import snd.komelia.ui.settings.epub.EpubReaderSettingsScreen
import snd.komelia.ui.settings.experimental.ExperimentalSettingsScreen
import snd.komelia.ui.settings.experimental.HiddenSeriesScreen
import snd.komelia.ui.settings.experimental.IgnoreListScreen
import snd.komelia.ui.settings.imagereader.ImageReaderSettingsScreen
import snd.komelia.ui.settings.komf.general.KomfSettingsScreen
import snd.komelia.ui.settings.komf.jobs.KomfJobsScreen
import snd.komelia.ui.settings.komf.notifications.KomfNotificationSettingsScreen
import snd.komelia.ui.settings.komf.processing.KomfProcessingSettingsScreen
import snd.komelia.ui.settings.komf.providers.KomfProvidersSettingsScreen
import snd.komelia.ui.settings.maintenance.MaintenanceScreen
import snd.komelia.ui.settings.offline.OfflineSettingsScreen
import snd.komelia.ui.settings.server.ServerSettingsScreen
import snd.komelia.ui.settings.servers.AppServerManagementScreen
import snd.komelia.ui.settings.toolkit.ToolkitScreen
import snd.komelia.ui.settings.updates.AppUpdatesScreen
import snd.komelia.ui.settings.users.UsersScreen
import snd.komf.api.MediaServer.KOMGA
import snd.komga.client.user.KomgaUser
import snd.webview.webviewIsAvailable

private data class NavEntry(
    val label: String,
    val isSelected: Boolean,
    val icon: ImageVector? = null,
    val summary: String? = null,
    val tint: Color? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val onClick: () -> Unit,
)

// Tints of the icon discs, one per family of settings (Refonte 2 mockups).
private val Pink = Color(0xFFF472B6)
private val Blue = Color(0xFF60A5FA)
private val Green = Color(0xFF34D399)
private val Violet = Color(0xFFA78BFA)
private val Amber = Color(0xFFFBBF24)
private val Slate = Color(0xFF94A3B8)
private val Red = Color(0xFFF87171)

@Composable
fun SettingsNavigationMenu(
    hasMediaErrors: Boolean,
    komfEnabled: Boolean,
    updatesEnabled: Boolean,
    newVersionIsAvailable: Boolean,
    currentScreen: Screen,
    onNavigation: (Screen) -> Unit = {},
    onLogout: () -> Unit,
    user: KomgaUser?,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val isAdmin = remember(user) { user?.roleAdmin() ?: true }
    val isOffline = LocalOfflineMode.current.collectAsState().value
    val isMobile = LocalPlatform.current == PlatformType.MOBILE
    var query by remember { mutableStateOf("") }
    val s = LocalStrings.current.ui

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (isMobile && query.isBlank() && user != null) {
            ProfileCard(
                user = user,
                isAdmin = isAdmin,
                isOffline = isOffline,
                onAccount = { onNavigation(AccountSettingsScreen()) },
            )
        }

        SettingsSearchField(query = query, onQueryChange = { query = it })

        if (query.isNotBlank()) {
            SettingResults(query = query, onNavigation = onNavigation)
        }

        FilteredSettingsGroup(
            title = s.stGroupApp,
            query = query,
            entries = buildList {
                add(
                    NavEntry(
                        label = s.appearance,
                        summary = s.sumAppearance,
                        icon = Icons.Rounded.Palette,
                        tint = Pink,
                        onClick = { onNavigation(AppSettingsScreen()) },
                        isSelected = currentScreen is AppSettingsScreen,
                    )
                )
                add(
                    NavEntry(
                        label = s.navigation,
                        summary = s.sumNavigation,
                        icon = Icons.Rounded.Navigation,
                        tint = Blue,
                        onClick = { onNavigation(NavigationSettingsScreen()) },
                        isSelected = currentScreen is NavigationSettingsScreen,
                    )
                )
                add(
                    NavEntry(
                        label = s.imageReader,
                        summary = s.sumImageReader,
                        icon = Icons.Rounded.Image,
                        tint = Green,
                        onClick = { onNavigation(ImageReaderSettingsScreen()) },
                        isSelected = currentScreen is ImageReaderSettingsScreen,
                    )
                )
                if (webviewIsAvailable()) {
                    add(
                        NavEntry(
                            label = s.epubReader,
                            summary = s.sumEpub,
                            icon = Icons.AutoMirrored.Rounded.MenuBook,
                            tint = Violet,
                            onClick = { onNavigation(EpubReaderSettingsScreen()) },
                            isSelected = currentScreen is EpubReaderSettingsScreen,
                        )
                    )
                }
                add(
                    NavEntry(
                        label = LocalStrings.current.discover.title,
                        summary = s.sumDiscover,
                        icon = Icons.Rounded.Explore,
                        tint = Amber,
                        onClick = { onNavigation(snd.komelia.ui.settings.discover.DiscoverSettingsScreen()) },
                        isSelected = currentScreen is snd.komelia.ui.settings.discover.DiscoverSettingsScreen,
                    )
                )
            }
        )

        FilteredSettingsGroup(
            title = s.stGroupData,
            query = query,
            entries = buildList {
                add(
                    NavEntry(
                        label = s.connectedServers,
                        summary = s.sumServers,
                        icon = Icons.Rounded.Dns,
                        tint = Blue,
                        onClick = { onNavigation(AppServerManagementScreen()) },
                        isSelected = currentScreen is AppServerManagementScreen,
                    )
                )
                add(
                    NavEntry(
                        label = s.offlineMode2,
                        summary = s.sumOffline,
                        icon = Icons.Rounded.CloudOff,
                        tint = Slate,
                        onClick = { onNavigation(OfflineSettingsScreen()) },
                        isSelected = currentScreen is OfflineSettingsScreen,
                    )
                )
                add(
                    NavEntry(
                        label = s.backupRestore2,
                        summary = s.sumBackup,
                        icon = Icons.Rounded.Backup,
                        tint = Green,
                        onClick = { onNavigation(BackupSettingsScreen()) },
                        isSelected = currentScreen is BackupSettingsScreen,
                    )
                )
                if (updatesEnabled) {
                    add(
                        NavEntry(
                            label = s.updates,
                            summary = s.sumUpdates,
                            icon = Icons.Rounded.SystemUpdate,
                            tint = Pink,
                            onClick = { onNavigation(AppUpdatesScreen()) },
                            isSelected = currentScreen is AppUpdatesScreen,
                            trailingContent = if (newVersionIsAvailable) {
                                { ErrorIndicator() }
                            } else null
                        )
                    )
                }
                add(
                    NavEntry(
                        label = s.diagnostics,
                        summary = s.sumDiagnostics,
                        icon = Icons.Rounded.BugReport,
                        tint = Slate,
                        onClick = { onNavigation(DiagnosticsScreen()) },
                        isSelected = currentScreen is DiagnosticsScreen,
                    )
                )
            }
        )

        FilteredSettingsGroup(
            // Not "Experimental" any more: the genre tab, the ignore list and
            // hidden series all shipped and all answer the same question --
            // what shows up in the library. Not "Administration" either: two
            // of the three are every user's business.
            title = s.contentSettings,
            query = query,
            entries = buildList {
                add(
                    NavEntry(
                        label = s.genreTab,
                        summary = s.sumGenreTab,
                        icon = Icons.Rounded.Category,
                        tint = Amber,
                        onClick = { onNavigation(ExperimentalSettingsScreen()) },
                        isSelected = currentScreen is ExperimentalSettingsScreen,
                    )
                )
                add(
                    NavEntry(
                        label = s.ignoreList,
                        summary = s.sumIgnoreList,
                        icon = Icons.Rounded.Block,
                        tint = Red,
                        onClick = { onNavigation(IgnoreListScreen()) },
                        isSelected = currentScreen is IgnoreListScreen,
                    )
                )
                if (isAdmin) {
                    add(
                        NavEntry(
                            label = s.sRiesMasquEs,
                            summary = s.sumHidden,
                            icon = Icons.Rounded.VisibilityOff,
                            tint = Slate,
                            onClick = { onNavigation(HiddenSeriesScreen()) },
                            isSelected = currentScreen is HiddenSeriesScreen,
                        )
                    )
                }
            }
        )

        // Whole group hidden from non-admins: these tools write server-side
        // metadata that Komga rejects for them anyway (403).
        if (isAdmin) {
            FilteredSettingsGroup(
                title = s.admin,
                query = query,
                entries = buildList {
                    add(
                        NavEntry(
                            label = s.maintenance,
                            icon = Icons.Rounded.Build,
                            tint = Slate,
                            onClick = { onNavigation(MaintenanceScreen()) },
                            isSelected = currentScreen is MaintenanceScreen,
                        )
                    )
                    add(
                        NavEntry(
                            label = s.komgaToolkit,
                            icon = Icons.Rounded.Construction,
                            tint = Amber,
                            onClick = { onNavigation(ToolkitScreen()) },
                            isSelected = currentScreen is ToolkitScreen,
                        )
                    )
                    add(
                        NavEntry(
                            label = s.chapterManagement,
                            icon = Icons.Rounded.FormatListNumbered,
                            tint = Blue,
                            onClick = { onNavigation(ChapterManagementScreen()) },
                            isSelected = currentScreen is ChapterManagementScreen,
                        )
                    )
                    add(
                        NavEntry(
                            label = s.duplicateSeries,
                            icon = Icons.Rounded.ContentCopy,
                            tint = Violet,
                            onClick = { onNavigation(DuplicateSeriesScreen()) },
                            isSelected = currentScreen is DuplicateSeriesScreen,
                        )
                    )
                }
            )
        }

        if (!isOffline) {
            FilteredSettingsGroup(
                title = s.userSettings,
                query = query,
                entries = buildList {
                    add(
                        NavEntry(
                            label = s.myAccount,
                            icon = Icons.Rounded.AccountCircle,
                            tint = Blue,
                            onClick = { onNavigation(AccountSettingsScreen()) },
                            isSelected = currentScreen is AccountSettingsScreen,
                        )
                    )
                    add(
                        NavEntry(
                            label = s.myAuthenticationActivity,
                            icon = Icons.Rounded.History,
                            tint = Slate,
                            onClick = { onNavigation(AuthenticationActivityScreen(true)) },
                            isSelected = currentScreen is AuthenticationActivityScreen && currentScreen.forMe,
                        )
                    )
                }
            )

            if (isAdmin) {
                FilteredSettingsGroup(
                    title = s.serverSettings,
                    query = query,
                    entries = buildList {
                        add(
                            NavEntry(
                                label = s.general,
                                icon = Icons.Rounded.Tune,
                                tint = Blue,
                                onClick = { onNavigation(ServerSettingsScreen()) },
                                isSelected = currentScreen is ServerSettingsScreen,
                            )
                        )
                        add(
                            NavEntry(
                                label = s.users,
                                icon = Icons.Rounded.Group,
                                tint = Green,
                                onClick = { onNavigation(UsersScreen()) },
                                isSelected = currentScreen is UsersScreen,
                            )
                        )
                        add(
                            NavEntry(
                                label = s.authenticationActivity,
                                icon = Icons.Rounded.Security,
                                tint = Slate,
                                onClick = { onNavigation(AuthenticationActivityScreen(false)) },
                                isSelected = currentScreen is AuthenticationActivityScreen && !currentScreen.forMe,
                            )
                        )
                        add(
                            NavEntry(
                                label = s.mediaManagement,
                                icon = Icons.Rounded.PermMedia,
                                tint = Violet,
                                onClick = { onNavigation(MediaAnalysisScreen()) },
                                isSelected = currentScreen is MediaAnalysisScreen,
                                trailingContent = if (hasMediaErrors) {
                                    { ErrorIndicator() }
                                } else null
                            )
                        )
                        add(
                            NavEntry(
                                label = s.announcements,
                                icon = Icons.Rounded.Campaign,
                                tint = Amber,
                                onClick = { onNavigation(AnnouncementsScreen()) },
                                isSelected = currentScreen is AnnouncementsScreen,
                            )
                        )
                    }
                )
            }

            if (isAdmin) {
                FilteredSettingsGroup(
                    title = s.komfSettings,
                    query = query,
                    entries = buildList {
                        add(
                            NavEntry(
                                label = s.connection,
                                icon = Icons.Rounded.Link,
                                tint = Slate,
                                onClick = { onNavigation(KomfSettingsScreen()) },
                                isSelected = currentScreen is KomfSettingsScreen,
                            )
                        )
                        if (komfEnabled) {
                            add(
                                NavEntry(
                                    label = s.processing2,
                                    icon = Icons.Rounded.Memory,
                                    tint = Slate,
                                    onClick = { onNavigation(KomfProcessingSettingsScreen(KOMGA)) },
                                    isSelected = currentScreen is KomfProcessingSettingsScreen,
                                )
                            )
                            add(
                                NavEntry(
                                    label = s.providers,
                                    icon = Icons.Rounded.Extension,
                                    tint = Slate,
                                    onClick = { onNavigation(KomfProvidersSettingsScreen()) },
                                    isSelected = currentScreen is KomfProvidersSettingsScreen,
                                )
                            )
                            add(
                                NavEntry(
                                    label = s.notifications,
                                    icon = Icons.Rounded.Notifications,
                                    tint = Slate,
                                    onClick = { onNavigation(KomfNotificationSettingsScreen()) },
                                    isSelected = currentScreen is KomfNotificationSettingsScreen,
                                )
                            )
                            add(
                                NavEntry(
                                    label = s.jobHistory,
                                    icon = Icons.Rounded.Schedule,
                                    tint = Slate,
                                    onClick = { onNavigation(KomfJobsScreen()) },
                                    isSelected = currentScreen is KomfJobsScreen,
                                )
                            )
                        }
                    }
                )
            }
        }

        var showLogoutConfirmation by remember { mutableStateOf(false) }
        FilteredSettingsGroup(
            title = s.actions,
            query = query,
            entries = listOf(
                NavEntry(
                    label = s.logOut,
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    tint = Red,
                    onClick = { showLogoutConfirmation = true },
                    isSelected = false,
                )
            )
        )

        Spacer(Modifier.height(16.dp + LocalTransparentNavBarPadding.current))

        if (showLogoutConfirmation) {
            ConfirmationDialog(
                title = s.logOut,
                body = s.logoutConfirm,
                buttonConfirm = s.logOut,
                buttonConfirmColor = MaterialTheme.colorScheme.errorContainer,

                onDialogConfirm = onLogout,
                onDialogDismiss = { showLogoutConfirmation = false })
        }
    }
}

/** Who is signed in, on which server, and the way to their account page. */
@Composable
private fun ProfileCard(user: KomgaUser, isAdmin: Boolean, isOffline: Boolean, onAccount: () -> Unit) {
    val s = LocalStrings.current.ui
    val name = user.email.substringBefore('@').replaceFirstChar { it.uppercase() }
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(primary, MaterialTheme.colorScheme.primaryContainer))),
            ) {
                Text(
                    name.take(1),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        "Komga",
                        if (isOffline) s.stOffline else s.stConnected,
                        s.stAdministrator.takeIf { isAdmin },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!isOffline) {
                FilledTonalButton(onClick = onAccount) { Text(s.myAccount) }
            }
        }
    }
}

/**
 * Settings found by name, one level below the pages: the label, what it
 * does, and the path to the page that holds it, which a tap opens.
 */
@Composable
private fun SettingResults(query: String, onNavigation: (Screen) -> Unit) {
    val s = LocalStrings.current.ui
    val needle = normalizeForMatch(query)
    val index = settingsSearchIndex()
    val matches = index.filter {
        normalizeForMatch("${it.label} ${it.description.orEmpty()} ${it.keywords}").contains(needle)
    }.take(30)
    if (matches.isEmpty()) return
    SettingsCard(s.settingsFound(matches.size)) {
        matches.forEachIndexed { i, entry ->
            if (i > 0) SettingsRowDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigation(entry.open()) }
                    .cursorForHand()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(entry.label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    entry.description?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        entry.path,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * A group of pages filtered by [query]. Renders nothing -- not even the
 * group title -- when no entry in the group matches, so searching doesn't
 * leave empty section headers on screen.
 */
@Composable
private fun FilteredSettingsGroup(
    title: String,
    entries: List<NavEntry>,
    query: String,
) {
    // Both sides folded, not just lowercased. Fourteen of this menu's
    // thirty-seven labels carry an accent -- "Séries masquées", "Boîte à outils
    // Komga", "Général" -- and a soft keyboard does not put one there by
    // accident, so "serie" found nothing at all.
    val needle = normalizeForMatch(query)
    val visible = entries.filter { needle.isEmpty() || normalizeForMatch(it.label).contains(needle) }
    if (visible.isEmpty()) return

    SettingsCard(title) {
        visible.forEachIndexed { index, entry ->
            if (index > 0) SettingsRowDivider()
            MenuRow(entry)
        }
    }
}

@Composable
private fun MenuRow(entry: NavEntry) {
    val selectedBg = if (entry.isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(selectedBg)
            .clickable(enabled = !entry.isSelected, onClick = entry.onClick)
            .cursorForHand()
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        entry.icon?.let { SettingsIcon(it, settingsTint(entry.tint ?: MaterialTheme.colorScheme.primary)) }
        Column(Modifier.weight(1f)) {
            Text(
                entry.label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.summary?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        entry.trailingContent?.invoke()
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

/** Filters the settings menu above as the user types. */
@Composable
private fun SettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(LocalStrings.current.ui.searchSettingsHint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = LocalStrings.current.ui.clear)
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedBorderColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
    )
}
