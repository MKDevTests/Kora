package snd.komelia.toolkit

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger { }

/**
 * Encrypted-at-rest storage for the Komga Toolkit base URL + bearer token.
 *
 * The token is a write-capable secret, so it lives in EncryptedSharedPreferences
 * (AES via the Android Keystore) rather than plain prefs. An `object` with
 * Context-taking accessors — not DI — because both the settings UI and the DI
 * config provider (which runs before the graph is fully built) need it.
 */
object ToolkitSecureStore {
    private const val PREFS = "kora_toolkit_secure"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_TOKEN = "token"
    private const val KEY_CODE_HASH = "code_hash"

    @Volatile
    private var cached: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences? {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: runCatching {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    PREFS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                ).also { cached = it }
            }.onFailure { logger.error(it) { "Could not open the encrypted Toolkit store" } }.getOrNull()
        }
    }

    /** Trimmed base URL without a trailing slash, or null. */
    fun getBaseUrl(context: Context): String? =
        prefs(context)?.getString(KEY_BASE_URL, null)?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }

    fun getToken(context: Context): String? =
        prefs(context)?.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun setBaseUrl(context: Context, value: String?) {
        prefs(context)?.edit()?.putString(KEY_BASE_URL, value?.trim().orEmpty())?.apply()
    }

    fun setToken(context: Context, value: String?) {
        prefs(context)?.edit()?.putString(KEY_TOKEN, value.orEmpty())?.apply()
    }

    /** Config for the automation client, or null when either field is unset. */
    fun config(context: Context): ToolkitConfig? {
        val url = getBaseUrl(context) ?: return null
        val token = getToken(context) ?: return null
        return ToolkitConfig(url, token)
    }

    // -- Access code -----------------------------------------------------------
    // A local passcode that gates the screen so only the owner runs automation
    // on this device. Stored as a SHA-256 hash inside the already-encrypted
    // prefs — never the code itself. This is a local barrier, not a strong
    // cryptographic secret; the real protection is the token being device-only.

    fun hasCode(context: Context): Boolean =
        prefs(context)?.getString(KEY_CODE_HASH, null) != null

    fun setCode(context: Context, code: String) {
        prefs(context)?.edit()?.putString(KEY_CODE_HASH, hash(code))?.apply()
    }

    fun verifyCode(context: Context, code: String): Boolean {
        val stored = prefs(context)?.getString(KEY_CODE_HASH, null) ?: return false
        return stored == hash(code)
    }

    fun clearCode(context: Context) {
        prefs(context)?.edit()?.remove(KEY_CODE_HASH)?.apply()
    }

    private fun hash(code: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(code.encodeToByteArray())
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
