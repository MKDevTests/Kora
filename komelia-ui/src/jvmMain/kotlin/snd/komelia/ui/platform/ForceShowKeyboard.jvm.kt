package snd.komelia.ui.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberForceShowKeyboard(): () -> Unit = { /* no-op on desktop */ }
