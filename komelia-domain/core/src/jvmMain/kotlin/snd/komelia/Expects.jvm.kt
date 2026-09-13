package snd.komelia

import io.ktor.http.*
import java.net.URI

actual fun codepointsCount(string: String): Long {
    return string.codePoints().count()
}

actual fun Float.formatDecimal(numberOfDecimals: Int) = "%.${numberOfDecimals}f".format(this)

actual fun Double.formatDecimal(numberOfDecimals: Int) = "%.${numberOfDecimals}f".format(this)

actual fun Url.resolve(childUrl: String): Url {
    val baseUri = this.toURI()
    val childUri = URI(childUrl)
    val relative = baseUri.resolve(childUri)

    return Url(relative)
}

actual fun isTransientNetworkFailure(e: Throwable): Boolean {
    var cause: Throwable? = e
    var depth = 0
    while (cause != null && depth < 8) {
        if (cause is kotlinx.coroutines.CancellationException) return false
        // OkHttp reports its own cancellation as an IOException("Canceled").
        // Ktor's ConnectTimeoutException and SocketTimeoutException are both
        // IOExceptions on the JVM, so one test covers connect, read and reset.
        if (cause is java.io.IOException && cause.message != "Canceled") return true
        cause = cause.cause
        depth++
    }
    return false
}
