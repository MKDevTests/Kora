package snd.komelia.ui.reader.image.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize
import snd.komelia.image.OcrElementBox
import snd.komelia.ui.common.components.AnimatedDropdownMenu
import snd.komelia.ui.platform.rememberTranslator
import snd.komelia.ui.LocalStrings

@Composable
fun TextSelectionOverlay(
    modifier: Modifier = Modifier,
    ocrResults: List<OcrElementBox>,
    intrinsicImageSize: IntSize,
    onSelectionChanged: (List<OcrElementBox>) -> Unit,
    onAddNote: (text: String, x: Float, y: Float) -> Unit = { _, _, _ -> },
) {
    var showMenu by remember { mutableStateOf(false) }
    var menuPosition by remember { mutableStateOf(Offset.Zero) }

    val selectedColor = Color.Blue.copy(alpha = 0.3f)
    val strokeColor = Color.Blue.copy(alpha = 0.5f)
    val clipboardManager = LocalClipboardManager.current
    val translateText = rememberTranslator()

    val currentOcrResults by rememberUpdatedState(ocrResults)
    val currentOnSelectionChanged by rememberUpdatedState(onSelectionChanged)

    Box(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(intrinsicImageSize) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val imagePoint = screenToImage(down.position, size, intrinsicImageSize)

                        val hit = currentOcrResults.find {
                            val r = it.imageRect
                            // Slightly expand hit area to make it easier to grab a word
                            val expanded = Rect(r.left - 10f, r.top - 10f, r.right + 10f, r.bottom + 10f)
                            expanded.contains(imagePoint)
                        }

                        if (hit == null) {
                            // Tap outside: clear selection and close menu
                            currentOnSelectionChanged(currentOcrResults.map { it.copy(selected = false) })
                            showMenu = false
                            return@awaitEachGesture
                        }

                        // Tap on a box: select the whole block and open menu
                        down.consume()
                        val blockIndex = hit.blockIndex
                        val newResults = currentOcrResults.map {
                            if (it.blockIndex == blockIndex) it.copy(selected = true)
                            else it.copy(selected = false)
                        }
                        currentOnSelectionChanged(newResults)
                        menuPosition = down.position
                        showMenu = true
                    }
                }
        ) {
            // 1. Draw segment borders (once per block)
            val processedBlocks = mutableSetOf<Int>()
            currentOcrResults.forEach { box ->
                if (box.blockIndex !in processedBlocks) {
                    val screenRect = imageToScreenRect(box.blockRect, intrinsicImageSize, size.toIntSize())
                    drawRect(
                        color = strokeColor,
                        topLeft = screenRect.topLeft,
                        size = screenRect.size,
                        style = Stroke(width = 2f)
                    )
                    processedBlocks.add(box.blockIndex)
                }
            }

            // 2. Draw individual word highlights (if selected)
            currentOcrResults.forEach { box ->
                if (box.selected) {
                    val screenRect = imageToScreenRect(box.imageRect, intrinsicImageSize, size.toIntSize())
                    drawRect(
                        color = selectedColor,
                        topLeft = screenRect.topLeft,
                        size = screenRect.size,
                        style = Fill
                    )
                }
            }
        }

        val selectedBoxes = currentOcrResults.filter { it.selected }
        val text = remember(selectedBoxes) {
            selectedBoxes
                .sortedWith(compareBy({ it.blockIndex }, { it.lineIndex }, { it.elementIndex }))
                .joinToString(" ") { it.text }
        }

        Box(Modifier.offset { IntOffset(menuPosition.x.toInt(), menuPosition.y.toInt()) }) {
            AnimatedDropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                transformOrigin = TransformOrigin(0f, 0f)
            ) {
                DropdownMenuItem(
                    text = { Text(LocalStrings.current.ui.copy) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(text))
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(LocalStrings.current.ui.translate) },
                    leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null) },
                    onClick = {
                        translateText(text)
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(LocalStrings.current.ui.addNote) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        val firstBox = selectedBoxes.minByOrNull { it.imageRect.top } ?: selectedBoxes.first()
                        val x = firstBox.imageRect.left / intrinsicImageSize.width
                        val y = firstBox.imageRect.top / intrinsicImageSize.height
                        onAddNote(text, x, y)
                        showMenu = false
                    }
                )
            }
        }
    }
}

private fun screenToImage(
    screenPoint: Offset,
    screenSize: IntSize,
    imageSize: IntSize
): Offset {
    val scaleX = screenSize.width.toFloat() / imageSize.width
    val scaleY = screenSize.height.toFloat() / imageSize.height
    return Offset(screenPoint.x / scaleX, screenPoint.y / scaleY)
}

internal fun imageToScreenRect(
    imageRect: Rect,
    imageSize: IntSize,
    screenSize: IntSize
): Rect {
    val scaleX = screenSize.width.toFloat() / imageSize.width
    val scaleY = screenSize.height.toFloat() / imageSize.height
    return Rect(
        left = imageRect.left * scaleX,
        top = imageRect.top * scaleY,
        right = imageRect.right * scaleX,
        bottom = imageRect.bottom * scaleY
    )
}
