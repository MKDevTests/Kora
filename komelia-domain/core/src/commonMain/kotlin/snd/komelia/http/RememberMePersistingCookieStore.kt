package snd.komelia.http

import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import kotlinx.coroutines.flow.StateFlow
import snd.komelia.settings.SecretsRepository

@Deprecated("changed to komga-remember-me since komga 1.21.0")
private const val deprecatedRememberMeCookie = "remember-me"
private const val rememberMeCookie = "komga-remember-me"
private const val sessionCookie = "KOMGA-SESSION"

class RememberMePersistingCookieStore(
    private val komgaUrl: StateFlow<Url>,
    private val secretsRepository: SecretsRepository,
) : CookiesStorage {
    private val delegate = AcceptAllCookiesStorage()

    suspend fun loadRememberMeCookie() {
        val url = komgaUrl.value
        secretsRepository.getCookie(url.toString())
            ?.let { parseServerSetCookieHeader(it) }
            ?.let { delegate.addCookie(url, it) }
    }

    /**
     *
     * if cookie manually added as part of request then it'll be updated with request's path
     * SSE reconnection will reuse the request with cookie headers
     * which will in turn override cookie with new path, breaking all other requests
     * as a workaround, skip cookie if its path doesn't equal to '/'
     * see [io.ktor.client.plugins.cookies.HttpCookies.captureHeaderCookies]
     */
    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        if ((cookie.name == rememberMeCookie || cookie.name == sessionCookie) && cookie.path != komgaUrl.value.encodedPath.ifBlank { "/" }) {
            return
        }

        delegate.addCookie(requestUrl, cookie)
        if (
            (cookie.name == rememberMeCookie || cookie.name == deprecatedRememberMeCookie)
            && cookie.value.isNotBlank()
            && komgaUrl.value.host == requestUrl.host
        ) {
            secretsRepository.setCookie(komgaUrl.value.toString(), renderSetCookieHeader(cookie))
        }

    }

    override suspend fun get(requestUrl: Url): List<Cookie> {
        val cookies = delegate.get(requestUrl)
        return cookies
    }

    /**
     * Copies the cookies held for [from] onto [to], and persists the
     * remember-me one under the new address. A Komga session is server-side:
     * the same token opens it from any address the server answers on, so a
     * failover to another address must not cost a login — nor a login after
     * the next restart, which is what the persisted copy is for.
     */
    suspend fun carryOver(from: Url, to: Url) {
        delegate.get(from).forEach { cookie ->
            val moved = cookie.copy(domain = null, path = "/")
            delegate.addCookie(to, moved)
            if (cookie.name == rememberMeCookie || cookie.name == deprecatedRememberMeCookie) {
                secretsRepository.setCookie(to.toString(), renderSetCookieHeader(moved))
            }
        }
    }

    override fun close() {
        delegate.close()
    }
}
