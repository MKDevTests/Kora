package snd.komelia.ui.settings.appearance

import androidx.compose.runtime.Composable
import snd.komelia.settings.model.AppIcon

/**
 * Returns a function that makes the launcher show [AppIcon]. Android
 * enables the matching activity alias and disables the others; desktop and
 * web have no launcher icon to speak of and return a no-op.
 */
@Composable
expect fun rememberAppIconSwitcher(): (AppIcon) -> Unit
