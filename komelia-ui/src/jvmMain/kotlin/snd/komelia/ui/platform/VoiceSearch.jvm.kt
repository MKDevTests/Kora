package snd.komelia.ui.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberVoiceSearchLauncher(onResult: (String) -> Unit): (() -> Unit)? = null
