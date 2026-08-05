package snd.komelia.ui.settings.imagereader.ncnn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import snd.komelia.ui.LocalStrings

@Composable
actual fun NcnnLogViewerDialog(onDismiss: () -> Unit) {
    var logs by remember { mutableStateOf("Loading logs...") }

    LaunchedEffect(Unit) {
        logs = fetchLogs()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LocalStrings.current.ui.ncnnUpscalerLogs) },
        text = {
            Column(modifier = Modifier.fillMaxHeight(0.8f)) {
                HorizontalDivider()
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            text = logs,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(LocalStrings.current.ui.close) }
        },
        dismissButton = {
            TextButton(onClick = {
                logs = "Refreshing..."
                val oldLogs = logs
                // Trigger refresh
                logs = "" 
            }) {
                Text(LocalStrings.current.ui.refresh)
            }
        }
    )

    if (logs == "") {
        LaunchedEffect(Unit) {
            logs = fetchLogs()
        }
    }
}

private suspend fun fetchLogs(): String = withContext(Dispatchers.IO) {
    try {
        val process = Runtime.getRuntime().exec("logcat -d -v time *:D")
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val log = StringBuilder()
        val filter = listOf("ncnn", "komelia", "Upscaler", "Vulkan", "Fatal", "DEBUG", "epub3-diag")
        
        reader.useLines { lines ->
            lines.filter { line ->
                filter.any { line.contains(it, ignoreCase = true) }
            }.toList().takeLast(200).forEach {
                log.append(it).append("\n")
            }
        }
        if (log.isEmpty()) "No relevant logs found." else log.toString()
    } catch (e: Exception) {
        "Failed to fetch logs: ${e.message}"
    }
}
