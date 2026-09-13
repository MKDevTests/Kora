package snd.komelia

import io.ktor.http.*

expect fun codepointsCount(string: String): Long
expect fun Float.formatDecimal(numberOfDecimals: Int): String
expect fun Double.formatDecimal(numberOfDecimals: Int): String
expect fun Url.resolve(childUrl: String): Url

/**
 * Whether [e] is a failure of the network path rather than of the request:
 * connect refused or timed out, socket reset, host unreachable, name lookup.
 * Such a failure is worth trying again in a moment; an HTTP error or a broken
 * image is not.
 *
 * Measured on 2026-09-13: after the tablet wakes from sleep, the Wi-Fi link
 * stays dead for about a minute while Android still reports it connected, and
 * every page asked during that minute fails at `connect()`.
 */
expect fun isTransientNetworkFailure(e: Throwable): Boolean
