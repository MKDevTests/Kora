package snd.komelia.ui.reader.image.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import snd.komelia.image.OcrElementBox
import snd.komelia.image.ReaderImage
import snd.komelia.image.ReaderImageResult
import snd.komelia.ui.LocalStrings

@Composable
fun ReaderImageContent(
    imageResult: ReaderImageResult?,
    ocrResults: List<OcrElementBox> = emptyList(),
    translations: Map<Int, String> = emptyMap(),
    onSelectionChanged: (List<OcrElementBox>) -> Unit = {},
    onAddNote: (text: String, x: Float, y: Float) -> Unit = { _, _, _ -> },
    onRetry: (() -> Unit)? = null,
) {
    when (imageResult) {
        is ReaderImageResult.Success -> ImageContent(
            image = imageResult.image,
            ocrResults = ocrResults,
            translations = translations,
            onSelectionChanged = onSelectionChanged,
            onAddNote = onAddNote,
            onRetry = onRetry,
        )
        is ReaderImageResult.Error -> PageError(imageResult.throwable, onRetry)

        null -> Box(
            modifier = Modifier.fillMaxHeight().aspectRatio(0.7f).background(Color.White),
            contentAlignment = Alignment.Center,
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.Black)
                    Text(LocalStrings.current.ui.downloading, color = Color.Black)
                }
            }
        )
    }
}

/**
 * A page that failed, with the one control that was missing: a way to reload
 * THIS page.
 *
 * Before this, a failed page rendered the exception's class name in red and
 * nothing else. It could not be retried at all -- the whole book had to be
 * reopened -- and since the readers cache the failed load itself, reopening was
 * often the only thing that worked. Which is why page +1 could stay broken
 * while +2 was fine: not a network oddity, a cached failure with no way out.
 *
 * The exception is still shown, under the button and in the body style: it is
 * what tells a timeout from a 404, and this reader is read by the person who
 * builds it.
 */
@Composable
private fun PageError(error: Throwable, onRetry: (() -> Unit)?) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            LocalStrings.current.ui.errorLoadingPage,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onRetry) {
                Text(LocalStrings.current.ui.reload)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "${error::class.simpleName}: ${error.message}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ImageContent(
    image: ReaderImage,
    ocrResults: List<OcrElementBox>,
    translations: Map<Int, String>,
    onSelectionChanged: (List<OcrElementBox>) -> Unit,
    onAddNote: (text: String, x: Float, y: Float) -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    // reimplement collectAsState and call remember with image key,
    // this avoids unnecessary recomposition and flickering caused by attempt to render old value on image change
    // without remember key, old painter value is remembered until new value is collected from flow

    // could've been avoided by extracting flow collection to the top ancestor
    // and just accepting painter as function param here
    val painterState = remember(image) { mutableStateOf(image.painter.value) }
    LaunchedEffect(image) { image.painter.collect { painterState.value = it } }
    val errorState = remember(image) { mutableStateOf(image.error.value) }
    LaunchedEffect(image) { image.error.collect { errorState.value = it } }

    val error = errorState.value
    val painter = painterState.value
    if (error != null) {
        PageError(error, onRetry)
    } else if (painter == null) {
        val density = LocalDensity.current
        val imageDisplaySize = image.displaySize.collectAsState().value
        val sizeModifier = remember(imageDisplaySize) {
            if (imageDisplaySize != null) {
                Modifier.size(with(density) {
                    DpSize(
                        imageDisplaySize.width.toDp(),
                        imageDisplaySize.height.toDp()
                    )
                })
            } else {
                Modifier.fillMaxHeight().aspectRatio(0.7f)
            }
        }
        Column(
            modifier = Modifier.animateContentSize().background(Color.White).then(sizeModifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = Color.Black)
            Text(LocalStrings.current.ui.processing, color = Color.Black)
        }

    } else {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painter,
                contentDescription = null,
                colorFilter = LocalReaderNightModeIntensity.current
                    ?.let { nightModeColorFilter(it) },
            )

            if (ocrResults.isNotEmpty()) {
                val originalSize = image.originalSize.collectAsState().value
                if (originalSize != null) {
                    // Translation replaces the selection overlay rather than
                    // stacking on it: the blue word boxes would be drawn under
                    // opaque panels, and tapping one would select text nobody
                    // can see.
                    if (translations.isNotEmpty()) {
                        TranslationOverlay(
                            modifier = Modifier.matchParentSize(),
                            ocrResults = ocrResults,
                            translations = translations,
                            intrinsicImageSize = originalSize,
                        )
                    } else {
                        TextSelectionOverlay(
                            modifier = Modifier.matchParentSize(),
                            ocrResults = ocrResults,
                            intrinsicImageSize = originalSize,
                            onSelectionChanged = onSelectionChanged,
                            onAddNote = onAddNote,
                        )
                    }
                }
            }
        }
    }
}
