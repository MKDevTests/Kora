package snd.komelia.ui.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import snd.komelia.ui.KoraShapes
import snd.komelia.ui.LocalStrings

/** Komga listens here unless configured otherwise; an empty port on http means this one. */
const val KOMGA_DEFAULT_PORT = "25600"

/**
 * A server address as three fields instead of one free-text URL: a
 * http / https segment, the host (IP or name, a path may follow) and a
 * numeric port, with the assembled URL shown underneath. The value that
 * goes in and out is still the full URL, so callers and storage are
 * unchanged.
 *
 * Pasting "https://host:1234/path" in the address field spreads it over
 * the three fields at once; typed keystroke by keystroke it is left alone
 * (moving a digit mid-word would steal it from under the cursor) and
 * tidied when the field loses focus. Either way the assembled URL is the
 * single source of truth.
 */
@Composable
fun ServerAddressFields(
    url: String,
    onUrlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hostFieldModifier: Modifier = Modifier,
    portFieldModifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current.ui
    var parts by remember { mutableStateOf(ServerUrlParts.parse(url)) }
    // Every URL handed to the parent, newest last. The parent echoes each one
    // back a frame later, and fast typing can be a few keystrokes ahead by
    // then: comparing the echo with the latest value only took a stale echo
    // for an outside change and reset the field mid-word, which is how
    // "shodan" came out as "sodanh" (measured on the tablet, 4 times).
    val emitted = remember { ArrayDeque<String>().apply { addLast(url) } }

    // Follow real outside changes (profile switch, prefilled last URL).
    LaunchedEffect(url) {
        if (url !in emitted) {
            parts = ServerUrlParts.parse(url)
            emitted.clear()
            emitted.addLast(url)
        }
    }

    fun update(next: ServerUrlParts) {
        parts = next
        val built = next.build()
        emitted.addLast(built)
        while (emitted.size > 64) emitted.removeFirst()
        onUrlChange(built)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !parts.https,
                onClick = { update(parts.copy(https = false)) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { Icon(Icons.Default.LockOpen, null, Modifier.width(18.dp)) },
                label = { Text("http") },
            )
            SegmentedButton(
                selected = parts.https,
                onClick = { update(parts.copy(https = true)) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { Icon(Icons.Default.Lock, null, Modifier.width(18.dp)) },
                label = { Text("https") },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = parts.host,
                onValueChange = { update(parts.withTypedHost(it.filterNot(Char::isWhitespace))) },
                modifier = hostFieldModifier
                    .weight(1f)
                    .onFocusChanged { if (!it.isFocused) parts.normalized().let { tidy -> if (tidy != parts) update(tidy) } },
                label = { Text(strings.serverAddress) },
                placeholder = { Text("192.168.1.10") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                value = parts.port,
                onValueChange = { update(parts.copy(port = it.filter(Char::isDigit).take(5))) },
                label = { Text(strings.serverPort) },
                placeholder = { Text(if (parts.https) "443" else KOMGA_DEFAULT_PORT) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = portFieldModifier.width(120.dp),
            )
        }

        Text(
            strings.serverAddressHelp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp),
        )

        if (parts.host.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, KoraShapes.small)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    strings.fullAddress,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    parts.build(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                )
            }
        }
    }
}

/** scheme + host(+path) + port, round-tripping with the URL string. */
data class ServerUrlParts(
    val https: Boolean = false,
    /** Host, optionally followed by a path ("nas.local/komga"). Never a port. */
    val host: String = "",
    /** Digits only; empty means [KOMGA_DEFAULT_PORT] on http, the default 443 on https. */
    val port: String = "",
) {
    /** The full URL, or "" while there is no host: an empty form must not become "http://:25600". */
    fun build(): String = normalized().buildNormalized()

    private fun buildNormalized(): String {
        if (host.isBlank()) return ""
        val scheme = if (https) "https" else "http"
        val slash = host.indexOf('/')
        val hostOnly = if (slash >= 0) host.substring(0, slash) else host
        val path = if (slash >= 0) host.substring(slash) else ""
        // Empty port: Komga's own port on plain http; nothing on https, where
        // the address is almost always a reverse proxy on 443.
        val effectivePort = port.ifBlank { if (https) "" else KOMGA_DEFAULT_PORT }
        val portPart = if (effectivePort.isBlank()) "" else ":$effectivePort"
        return "$scheme://$hostOnly$portPart$path"
    }

    /**
     * A paste (several characters at once) is spread over the fields right
     * away; a keystroke only replaces the host text, so ":8080" can be
     * typed in full before [normalized] moves it on focus loss.
     */
    fun withTypedHost(text: String): ServerUrlParts {
        val pasted = text.length - host.length > 1
        val next = copy(host = text)
        return if (pasted) next.normalized() else next
    }

    /**
     * The address field accepts what people paste anyway: "host:8080/path"
     * moves the digits to the port field, "https://host" flips the scheme,
     * a dangling ":" is dropped.
     */
    fun normalized(): ServerUrlParts {
        val hasScheme = host.contains("://")
        val https = if (hasScheme) host.startsWith("https://", ignoreCase = true) else https
        val rest = (if (hasScheme) host.substringAfter("://") else host).removeSuffix(":")
        val match = hostWithPort.find(rest)
        return if (match != null) {
            val (hostOnly, port, path) = match.destructured
            copy(https = https, host = hostOnly + path, port = port)
        } else copy(https = https, host = rest)
    }

    companion object {
        private val hostWithPort = Regex("""^(\[[^\]]*]|[^/:]*):(\d{1,5})(/.*)?$""")
        private val pattern = Regex("""^(?:(https?)://)?(\[[^\]]*]|[^/:]*)(?::(\d{0,5}))?(/.*)?$""", RegexOption.IGNORE_CASE)

        fun parse(url: String): ServerUrlParts {
            val trimmed = url.trim()
            val match = pattern.find(trimmed)
                ?: return ServerUrlParts(https = trimmed.startsWith("https://", ignoreCase = true), host = trimmed.substringAfter("://"))
            val (scheme, host, port, path) = match.destructured
            return ServerUrlParts(
                https = scheme.equals("https", ignoreCase = true),
                host = host + path,
                port = port,
            )
        }
    }
}
