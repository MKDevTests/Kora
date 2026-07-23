package snd.komelia.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import snd.komelia.toolkit.ToolkitSecureStore

@Composable
actual fun rememberToolkitSettings(): ToolkitSettingsState? {
    val context = LocalContext.current
    return remember {
        object : ToolkitSettingsState {
            private var _baseUrl by mutableStateOf(ToolkitSecureStore.getBaseUrl(context).orEmpty())
            private var _token by mutableStateOf(ToolkitSecureStore.getToken(context).orEmpty())
            override val baseUrl: String get() = _baseUrl
            override val token: String get() = _token
            override val configured: Boolean get() = _baseUrl.isNotBlank() && _token.isNotBlank()

            override fun setBaseUrl(value: String) {
                _baseUrl = value
                ToolkitSecureStore.setBaseUrl(context, value)
            }

            override fun setToken(value: String) {
                _token = value
                ToolkitSecureStore.setToken(context, value)
            }
            // Reactive per-category library mapping.
            private val libByCategory = androidx.compose.runtime.mutableStateMapOf<String, String>().apply {
                snd.komelia.toolkit.ToolkitCategory.entries.forEach { cat ->
                    ToolkitSecureStore.getCategoryLibrary(context, cat.name)?.let { put(cat.name, it) }
                }
            }
            override fun libraryFor(category: snd.komelia.toolkit.ToolkitCategory): String? =
                libByCategory[category.name]
            override fun setLibraryFor(category: snd.komelia.toolkit.ToolkitCategory, libraryId: String?) {
                if (libraryId == null) libByCategory.remove(category.name) else libByCategory[category.name] = libraryId
                ToolkitSecureStore.setCategoryLibrary(context, category.name, libraryId)
            }
        }
    }
}
