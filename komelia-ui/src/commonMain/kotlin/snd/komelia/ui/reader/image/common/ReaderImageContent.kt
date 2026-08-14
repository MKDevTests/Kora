package snd.komelia.ui.reader.image.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.DpSize
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
) {
    when (imageResult) {
        is ReaderImageResult.Success -> ImageContent(
            image = imageResult.image,
            ocrResults = ocrResults,
            translations = translations,
            onSelectionChanged = onSelectionChanged,
            onAddNote = onAddNote
        )
        is ReaderImageResult.Error -> Text(
            "${imageResult.throwable::class.simpleName}: ${imageResult.throwable.message}",
            color = MaterialTheme.colorScheme.error
        )

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

@Composable
private fun ImageContent(
    image: ReaderImage,
    ocrResults: List<OcrElementBox>,
    translations: Map<Int, String>,
    onSelectionChanged: (List<OcrElementBox>) -> Unit,
    onAddNote: (text: String, x: Float, y: Float) -> Unit,
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
        Text(
            "${error::class.simpleName}: ${error.message}",
            color = MaterialTheme.colorScheme.error
        )
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
