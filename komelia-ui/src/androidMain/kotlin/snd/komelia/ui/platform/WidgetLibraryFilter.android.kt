package snd.komelia.ui.platform

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Storage for the widget's library filter. An `object` with Context-taking
 * functions (rather than DI) so the widget process — which renders without
 * the app's dependency graph — can read it too.
 */
object WidgetLibraryFilterSettings {
    private const val PREFS = "kora_widget"
    private const val KEY_LIBRARY_ID = "library_id"

    fun getLibraryId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LIBRARY_ID, null)
            ?.takeIf { it.isNotBlank() }

    fun setLibraryId(context: Context, libraryId: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LIBRARY_ID, libraryId ?: "").apply()
    }
}

@Composable
actual fun rememberWidgetLibraryFilter(): WidgetLibraryFilterState? {
    val context = LocalContext.current
    return remember {
        object : WidgetLibraryFilterState {
            override var libraryId: String? by mutableStateOf(
                WidgetLibraryFilterSettings.getLibraryId(context)
            )

            override fun set(libraryId: String?) {
                WidgetLibraryFilterSettings.setLibraryId(context, libraryId)
                this.libraryId = libraryId
            }
        }
    }
}
