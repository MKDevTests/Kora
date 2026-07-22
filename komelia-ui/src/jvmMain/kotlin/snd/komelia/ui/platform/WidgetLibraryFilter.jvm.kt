package snd.komelia.ui.platform

import androidx.compose.runtime.Composable

/** No home-screen widget on desktop — hides the settings section. */
@Composable
actual fun rememberWidgetLibraryFilter(): WidgetLibraryFilterState? = null
