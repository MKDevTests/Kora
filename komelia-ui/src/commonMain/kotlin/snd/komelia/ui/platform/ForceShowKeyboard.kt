package snd.komelia.ui.platform

import androidx.compose.runtime.Composable

/**
 * Forces the soft keyboard to appear, even when a hardware input device
 * (e.g. a Bluetooth media remote) is connected. On Android, uses
 * `InputMethodManager.showSoftInput(SHOW_FORCED)`. No-op on other platforms.
 */
@Composable
expect fun rememberForceShowKeyboard(): () -> Unit
