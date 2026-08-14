package snd.komelia.ui.reader.image.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toIntSize
import snd.komelia.image.OcrElementBox

/** Opaque panel painted over each bubble, so the original text never shows through. */
private val backgroundColor = Color(0xFF101010)
private val borderColor = Color(0x33FFFFFF)
private val textColor = Color.White

/** Breathing room inside a bubble, as a fraction of its short side. */
private const val PADDING_RATIO = 0.06f

/** How far the panel is grown past the detected text, per side. */
private const val PANEL_GROWTH_RATIO = 0.12f
private const val MAX_FONT_SP = 22f
private const val MIN_FONT_SP = 7f

/**
 * Draws the translated text over the page, one panel per OCR block.
 *
 * Deliberately non-interactive: it sits above the image but consumes no
 * gestures, so page turns and zoom keep working exactly as they do without
 * translation. Blocks with no translation yet are left untouched rather than
 * covered with an empty box — a half-translated page stays readable.
 */
@Composable
fun TranslationOverlay(
    modifier: Modifier = Modifier,
    ocrResults: List<OcrElementBox>,
    translations: Map<Int, String>,
    intrinsicImageSize: IntSize,
) {
    val measurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        ocrResults
            .distinctBy { it.blockIndex }
            .forEach { box ->
                val text = translations[box.blockIndex]?.takeIf { it.isNotBlank() } ?: return@forEach
                val textRect = imageToScreenRect(box.blockRect, intrinsicImageSize, size.toIntSize())
                if (textRect.width <= 1f || textRect.height <= 1f) return@forEach
                // The box hugs the glyphs the OCR found, so the original letters
                // peek out around the panel — worse when a line was missed
                // entirely. Grown outwards, clamped to the page.
                val grow = minOf(textRect.width, textRect.height) * PANEL_GROWTH_RATIO
                val screenRect = androidx.compose.ui.geometry.Rect(
                    left = (textRect.left - grow).coerceAtLeast(0f),
                    top = (textRect.top - grow).coerceAtLeast(0f),
                    right = (textRect.right + grow).coerceAtMost(size.width),
                    bottom = (textRect.bottom + grow).coerceAtMost(size.height),
                )

                drawRect(
                    color = backgroundColor,
                    topLeft = screenRect.topLeft,
                    size = screenRect.size,
                )
                drawRect(
                    color = borderColor,
                    topLeft = screenRect.topLeft,
                    size = screenRect.size,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
                )

                val padding = minOf(screenRect.width, screenRect.height) * PADDING_RATIO
                val innerWidth = (screenRect.width - padding * 2).coerceAtLeast(1f)
                val innerHeight = (screenRect.height - padding * 2).coerceAtLeast(1f)

                val layout = fitText(measurer, text, innerWidth, innerHeight)
                // Centred vertically; the text is usually shorter than the bubble
                // it replaces, and a top-aligned line looks like a rendering bug.
                val textTop = screenRect.top + padding +
                        ((innerHeight - layout.size.height) / 2).coerceAtLeast(0f)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(screenRect.left + padding, textTop),
                )
            }
    }
}

/**
 * Largest size that fits, found by measuring. Translated text is rarely the
 * length of the original, so a size derived from the bubble alone either
 * overflows or wastes most of it.
 */
private fun fitText(
    measurer: TextMeasurer,
    text: String,
    maxWidth: Float,
    maxHeight: Float,
): TextLayoutResult {
    val constraints = Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(1))
    var fontSize = MAX_FONT_SP
    var layout = measure(measurer, text, fontSize, constraints)
    // Eight steps take 22sp down to the 7sp floor; past that the text is
    // unreadable anyway and clipping is the honest outcome.
    repeat(8) {
        if (layout.size.height <= maxHeight || fontSize <= MIN_FONT_SP) return layout
        fontSize = (fontSize * 0.85f).coerceAtLeast(MIN_FONT_SP)
        layout = measure(measurer, text, fontSize, constraints)
    }
    return layout
}

private fun measure(
    measurer: TextMeasurer,
    text: String,
    fontSize: Float,
    constraints: Constraints,
): TextLayoutResult = measurer.measure(
    text = AnnotatedString(text),
    style = TextStyle(
        color = textColor,
        fontSize = fontSize.sp,
        textAlign = TextAlign.Center,
    ),
    constraints = constraints,
)
