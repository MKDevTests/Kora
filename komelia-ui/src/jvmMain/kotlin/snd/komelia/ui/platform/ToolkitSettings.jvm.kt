package snd.komelia.ui.platform

import androidx.compose.runtime.Composable

/** No Komga Toolkit UI on desktop. */
@Composable
actual fun rememberToolkitSettings(): ToolkitSettingsState? = null
