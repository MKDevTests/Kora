package snd.komelia.ui.settings.epub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import snd.komelia.settings.model.EpubReaderType
import snd.komelia.settings.model.EpubReaderType.EPUB3_READER
import snd.komelia.settings.model.EpubReaderType.KOMGA_EPUB
import snd.komelia.settings.model.EpubReaderType.TTSU_EPUB
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.platform.cursorForHand

@Composable
fun EpubReaderSettingsContent(
    readerType: EpubReaderType,
    onReaderChange: (EpubReaderType) -> Unit,

    epubCacheSizeLimitMb: Long,
    onEpubCacheSizeLimitMbChange: (Long) -> Unit,
    onClearEpubCache: () -> Unit,
) {
    val strings = LocalStrings.current.settings
    val accentColor = LocalAccentColor.current
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DropdownChoiceMenu(
                selectedOption = remember(readerType) {
                    LabeledEntry(
                        readerType,
                        strings.forEpubReaderType(readerType)
                    )
                },
                options = remember(epub3ReaderAvailable) {
                    EpubReaderType.entries
                        .filter { it != EPUB3_READER || epub3ReaderAvailable }
                        .map { LabeledEntry(it, strings.forEpubReaderType(it)) }
                },
                onOptionChange = { onReaderChange(it.value) },
                label = { Text("Reader Type") },
                inputFieldModifier = Modifier.fillMaxWidth().animateContentSize(),
                modifier = Modifier.weight(1f),
            )

            AnimatedVisibility(readerType == TTSU_EPUB) {
                val uriHandler = LocalUriHandler.current
                ElevatedButton(
                    onClick = { uriHandler.openUri("https://github.com/ttu-ttu/ebook-reader") },
                    modifier = Modifier.cursorForHand().padding(start = 20.dp)
                ) {
                    Text("Project on Github")
                }
            }
        }


        when (readerType) {
            TTSU_EPUB -> Text(
                """
                    Loads entire book data at once. May cause long load times or performance issues
                    Adapted for use in Kora with storage/statistics features removed
                """.trimIndent()
            )

            KOMGA_EPUB -> Text("Komga webui epub reader adapted for use in Kora")

            EPUB3_READER -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Native EPUB 3 reader with synchronized audio overlay (SMIL) support. Android only.")

                    FilledTonalButton(
                        onClick = onClearEpubCache,
                        colors = accentColor?.let {
                            val contentColor = if (it.luminance() > 0.5f) Color.Black else Color.White
                            ButtonDefaults.filledTonalButtonColors(containerColor = it, contentColor = contentColor)
                        } ?: ButtonDefaults.filledTonalButtonColors()
                    ) { Text("Clear EPUB cache") }

                    Column {
                        Text(
                            "Max EPUB Cache Size: ${"%.1f".format(epubCacheSizeLimitMb.toDouble() / 1024)} GB",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Slider(
                            value = epubCacheSizeLimitMb.toFloat(),
                            onValueChange = { onEpubCacheSizeLimitMbChange(it.toLong()) },
                            valueRange = 1000f..10000f,
                            steps = 8, // (10000 - 1000) / 1000 - 1 = 8 steps for 1GB intervals
                            colors = accentColor?.let {
                                SliderDefaults.colors(
                                    thumbColor = it,
                                    activeTrackColor = it,
                                )
                            } ?: SliderDefaults.colors()
                        )
                    }
                }
            }
        }
    }
}
