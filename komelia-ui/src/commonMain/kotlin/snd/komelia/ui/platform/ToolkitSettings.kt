package snd.komelia.ui.platform

import androidx.compose.runtime.Composable

/**
 * Read/write access to the encrypted Komga Toolkit URL + token, from common
 * settings UI. Null on platforms without Toolkit support (desktop/web), which
 * hides the section — mirrors [rememberWidgetLibraryFilter].
 *
 * The token is written by the user typing it into a field; Kora never fills it.
 */
interface ToolkitSettingsState {
    val baseUrl: String
    val token: String
    /** True once both fields are set — the automation client can run. */
    val configured: Boolean
    fun setBaseUrl(value: String)
    fun setToken(value: String)

    /** Local access code that gates the screen. */
    val hasCode: Boolean
    fun setCode(code: String)
    fun verifyCode(code: String): Boolean
    fun clearCode()
}

@Composable
expect fun rememberToolkitSettings(): ToolkitSettingsState?
