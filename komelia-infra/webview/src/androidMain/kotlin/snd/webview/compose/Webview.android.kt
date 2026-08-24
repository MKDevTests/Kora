package snd.webview.compose

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import snd.webview.KomeliaWebview
import snd.webview.disableWebContentsDebuggingOnce

@Composable
actual fun Webview(
    onCreated: (KomeliaWebview) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            // Loading the Chromium provider is what this costs; it belongs here,
            // where a WebView is actually about to exist, not in onCreate.
            disableWebContentsDebuggingOnce()
            WebView(it).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { onCreated(KomeliaWebview(it)) })
}