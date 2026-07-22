package snd.komelia.ui.platform

import androidx.compose.runtime.Composable

/**
 * Read/write access to the "Next book up" widget's library filter, from
 * common settings UI.
 *
 * The widget is Android-only and so is its preference (plain
 * SharedPreferences — deliberately NOT a DB-backed setting: no migration to
 * burn for something meaningless on desktop/web). Mirrors the
 * [rememberVoiceSearchLauncher] expect/actual pattern: null on platforms
 * without the widget, which hides the settings section entirely.
 */
interface WidgetLibraryFilterState {
    /** Library id the widget is restricted to, or null for all libraries. */
    val libraryId: String?
    fun set(libraryId: String?)
}

@Composable
expect fun rememberWidgetLibraryFilter(): WidgetLibraryFilterState?
