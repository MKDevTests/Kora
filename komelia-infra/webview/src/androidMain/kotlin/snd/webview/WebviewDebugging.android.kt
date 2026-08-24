package snd.webview

import android.webkit.WebView
import java.util.concurrent.atomic.AtomicBoolean

private val applied = AtomicBoolean(false)

/**
 * Turns remote debugging of web contents off, once, at the moment a WebView is
 * first needed.
 *
 * This used to sit in `MainActivity.onCreate`, and it is not a cheap line to
 * put there: touching any `WebView` static loads the whole Chromium provider.
 * Measured on the tablet, to the millisecond across three cold starts:
 *
 * ```
 * 0.95 s   WebViewFactory: Loading com.google.android.webview version 151.0…
 * ```
 *
 * — on a launch that opens the home screen and, for anyone who does not read
 * EPUBs, will never show a WebView at all.
 *
 * It cannot simply be deleted. `false` is the documented default, but WebView
 * enables contents debugging by itself when the app manifest is debuggable —
 * which covers both the debug build and the `-PdebuggableRelease` variant used
 * for migration testing. So the call still has to happen; it only has to happen
 * later.
 *
 * Idempotent, and called from the two places a WebView can appear: the Compose
 * [snd.webview.compose.Webview] factory, and the Readium EPUB 3 reader.
 */
fun disableWebContentsDebuggingOnce() {
    if (applied.compareAndSet(false, true)) {
        WebView.setWebContentsDebuggingEnabled(false)
    }
}
