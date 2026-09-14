package snd.komelia.ui.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.drop
import snd.komelia.NetworkState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import snd.komelia.ui.LocalStrings

private val logger = KotlinLogging.logger {}

@Composable
fun ErrorContent(
    exception: Throwable,
    onReload: (() -> Unit)? = null,
    onExit: (() -> Unit)? = null,
) {
    val messageString = remember(exception) {
        exception.message?.let { message -> "${exception::class.simpleName} $message" }
            ?: exception::class.simpleName ?: "Unknown Error"
    }
    ErrorContent(messageString, onReload, onExit)
}

/**
 * The error screen every list falls back to, with its Reload button.
 *
 * While it is on screen, a network coming back presses Reload by itself.
 * Measured on the tablet on 2026-09-13: the home shelves failed at connect
 * during a Wi-Fi cut ("home.shelf 'Keep reading' FAILED after 10143ms") and
 * stayed on this screen until a tap, long after the link was back. Every
 * screen that shows this gets the repair, none has to know about the network.
 */
@Composable
fun ErrorContent(
    message: String,
    onReload: (() -> Unit)? = null,
    onExit: (() -> Unit)? = null,
) {
    if (onReload != null) {
        LaunchedEffect(Unit) {
            NetworkState.comebacks.drop(1).collect {
                logger.info { "network is back: reloading after '${message.take(80)}'" }
                onReload()
            }
        }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 1200.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SelectionContainer { Text(message) }
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (onReload != null) {
                    FilledTonalButton(onClick = onReload) {
                        Text(LocalStrings.current.ui.reload)
                    }
                }

                if (onExit != null) {
                    FilledTonalButton(onClick = onExit) {
                        Text(LocalStrings.current.ui.exit)
                    }
                }
            }

        }
    }
}