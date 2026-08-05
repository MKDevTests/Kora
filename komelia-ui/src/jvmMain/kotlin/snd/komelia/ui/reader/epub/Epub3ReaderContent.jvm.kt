package snd.komelia.ui.reader.epub

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import snd.komelia.ui.LocalStrings

@Composable
actual fun Epub3ReaderContent(state: EpubReaderState) {
    Text(LocalStrings.current.ui.epub3ReaderIsNotAvailable)
}
