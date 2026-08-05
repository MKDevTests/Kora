package snd.komelia.ui.settings.imagereader.ncnn

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import snd.komelia.ui.LocalStrings

@Composable
actual fun NcnnCrashLogViewerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf("Loading...") }

    LaunchedEffect(Unit) {
        logs = readCrashLogs(context)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LocalStrings.current.ui.crashLogs) },
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
        }
    )
}

private suspend fun readCrashLogs(context: Context): String = withContext(Dispatchers.IO) {
    val logDir = File(context.getExternalFilesDir(null), "komelia/logs")
    if (!logDir.exists()) return@withContext "No log directory found at ${logDir.absolutePath}"
    val files = listOf("last_session_logcat.txt", "java_crash_report.txt", "komelia.log")
    val sb = StringBuilder()
    files.forEach { name ->
        val f = File(logDir, name)
        if (f.exists()) {
            sb.append("=== $name ===\n")
            sb.append(f.readLines().takeLast(300).joinToString("\n"))
            sb.append("\n\n")
        }
    }
    if (sb.isEmpty()) "No log files found in ${logDir.absolutePath}" else sb.toString()
}
