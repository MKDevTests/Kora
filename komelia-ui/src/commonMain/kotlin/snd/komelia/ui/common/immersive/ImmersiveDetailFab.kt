package snd.komelia.ui.common.immersive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import snd.komelia.ui.LocalFloatingActionButton
import snd.komelia.ui.LocalFloatingActionButtonLeft
import snd.komelia.ui.LocalNavBarColor
import snd.komelia.ui.LocalTheme
import snd.komelia.ui.LocalUseFloatingNavigationBar
import snd.komelia.ui.Theme
import snd.komelia.ui.common.FloatingFAB
import snd.komelia.ui.common.FloatingFABWithDropdownMenu
import snd.komelia.ui.common.SplitFabMenu
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImmersiveDetailFab(
    onReadClick: () -> Unit,
    onReadIncognitoClick: () -> Unit,
    onDownloadClick: () -> Unit,
    accentColor: Color? = null,
    showReadActions: Boolean = true,
    onRandomSiblingClick: (() -> Unit)? = null,
    onPreviousSiblingSeriesClick: (() -> Unit)? = null,
    onNextSiblingSeriesClick: (() -> Unit)? = null,
    hasPreviousSiblingSeries: Boolean = false,
    hasNextSiblingSeries: Boolean = false,
    // Series-level quick-read shortcuts. When set, they appear to the RIGHT of
    // the Download FAB in the rightmost slot (i.e. ContinueRead is rightmost).
    // `canContinueRead` greys the continue button when there's nothing left to
    // resume (every book in the series is completed).
    onReadFromStartClick: (() -> Unit)? = null,
    onContinueReadClick: (() -> Unit)? = null,
    canContinueRead: Boolean = true,
) {
    val theme = LocalTheme.current
    val navBarColor = LocalNavBarColor.current

    val (fabContainerColor, fabContentColor) = if (theme.type == Theme.ThemeType.LIGHT) {
        Color(red = 43, green = 43, blue = 43) to Color.White
    } else {
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }

    val readNowContainerColor = accentColor
        ?: if (theme.type == Theme.ThemeType.LIGHT) Color(red = 43, green = 43, blue = 43)
        else navBarColor ?: MaterialTheme.colorScheme.primaryContainer

    val readNowContentColor = if (readNowContainerColor.luminance() > 0.5f) Color.Black else Color.White

    var expanded by remember { mutableStateOf(false) }

    val useFloatingNavigationBar = LocalUseFloatingNavigationBar.current
    val fab = LocalFloatingActionButton.current
    val fabLeft = LocalFloatingActionButtonLeft.current

    if (useFloatingNavigationBar) {
        val ownerKey = remember { Any() }

        if (showReadActions) {
            // Right FAB: primary Read action
            DisposableEffect(Unit) {
                fab.value = ownerKey to {
                    FloatingFAB(
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        onClick = onReadClick,
                        accentColor = accentColor,
                    )
                }
                onDispose { if (fab.value?.first == ownerKey) fab.value = null }
            }

            // Left FAB: dropdown menu with all three options
            DisposableEffect(Unit) {
                fabLeft.value = ownerKey to {
                    FloatingFABWithDropdownMenu(
                        icon = Icons.Rounded.MoreVert,
                        accentColor = accentColor,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Read") },
                            onClick = onReadClick,
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Read Incognito") },
                            onClick = onReadIncognitoClick,
                            leadingIcon = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Download") },
                            onClick = onDownloadClick,
                            leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                        )
                    }
                }
                onDispose { if (fabLeft.value?.first == ownerKey) fabLeft.value = null }
            }
        } else {
            // No primary Read FAB. Right slot composes Download + optional
            // series-level read shortcuts. Order (left → right): Download,
            // ReadFromStart, ContinueRead. ContinueRead is rightmost as the
            // primary action the user expects to tap most often.
            DisposableEffect(onReadFromStartClick, onContinueReadClick, canContinueRead) {
                fab.value = ownerKey to {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FloatingFAB(
                            icon = Icons.Rounded.Download,
                            onClick = onDownloadClick,
                            accentColor = accentColor,
                        )
                        if (onReadFromStartClick != null) {
                            FloatingFAB(
                                icon = Icons.Rounded.Replay,
                                onClick = onReadFromStartClick,
                                accentColor = accentColor,
                            )
                        }
                        if (onContinueReadClick != null) {
                            FloatingFAB(
                                icon = Icons.AutoMirrored.Rounded.MenuBook,
                                onClick = { if (canContinueRead) onContinueReadClick() },
                                accentColor = accentColor,
                                iconTint = if (canContinueRead) null
                                else (accentColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.3f),
                            )
                        }
                    }
                }
                onDispose { if (fab.value?.first == ownerKey) fab.value = null }
            }
            DisposableEffect(onRandomSiblingClick, onPreviousSiblingSeriesClick, onNextSiblingSeriesClick, hasPreviousSiblingSeries, hasNextSiblingSeries) {
                if (onRandomSiblingClick != null || onPreviousSiblingSeriesClick != null || onNextSiblingSeriesClick != null) {
                    fabLeft.value = ownerKey to {
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            if (onPreviousSiblingSeriesClick != null) {
                                FloatingFAB(
                                    icon = Icons.Rounded.SkipPrevious,
                                    onClick = onPreviousSiblingSeriesClick,
                                    accentColor = accentColor,
                                    iconTint = if (hasPreviousSiblingSeries) null else (accentColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.3f)
                                )
                            }
                            if (onRandomSiblingClick != null) {
                                FloatingFAB(
                                    icon = Icons.Rounded.Casino,
                                    onClick = onRandomSiblingClick,
                                    accentColor = accentColor,
                                )
                            }
                            if (onNextSiblingSeriesClick != null) {
                                FloatingFAB(
                                    icon = Icons.Rounded.SkipNext,
                                    onClick = onNextSiblingSeriesClick,
                                    accentColor = accentColor,
                                    iconTint = if (hasNextSiblingSeries) null else (accentColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
                onDispose { if (fabLeft.value?.first == ownerKey) fabLeft.value = null }
            }
        }
    } else {
        // ── Non-floating mode: unchanged ────────────��────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.weight(1f))

            if (showReadActions) {
                SplitFabMenu(
                    modifier = Modifier.offset(x = 20.dp, y = 20.dp),
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    primaryActionText = "Read",
                    primaryActionIcon = Icons.AutoMirrored.Rounded.MenuBook,
                    onPrimaryActionClick = {
                        expanded = false
                        onReadClick()
                    },
                    containerColor = readNowContainerColor,
                    contentColor = readNowContentColor,
                    menuItems = {
                        FloatingActionButtonMenuItem(
                            onClick = { expanded = false; onReadClick() },
                            icon = { Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null) },
                            text = { Text("Read") },
                            containerColor = readNowContainerColor,
                            contentColor = readNowContentColor
                        )
                        FloatingActionButtonMenuItem(
                            onClick = { expanded = false; onReadIncognitoClick() },
                            icon = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null) },
                            text = { Text("Read Incognito") },
                            containerColor = readNowContainerColor,
                            contentColor = readNowContentColor
                        )
                        FloatingActionButtonMenuItem(
                            onClick = { expanded = false; onDownloadClick() },
                            icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                            text = { Text("Download") },
                            containerColor = readNowContainerColor,
                            contentColor = readNowContentColor
                        )
                    }
                )
            } else {
                FloatingActionButton(
                    onClick = onDownloadClick,
                    containerColor = fabContainerColor,
                    contentColor = fabContentColor,
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = "Download")
                }
            }
        }
    }
}
