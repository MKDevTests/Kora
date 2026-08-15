package snd.komelia.image

import androidx.compose.ui.geometry.Rect

data class OcrElementBox(
    val text: String,
    val imageRect: Rect,
    val blockRect: Rect,
    val blockIndex: Int,
    val lineIndex: Int,
    val elementIndex: Int,
    /**
     * How sure the recogniser is of [text], 0..1. Carried so a bubble that came
     * out as nonsense can be told apart from one the translator mangled — the
     * two look identical on screen and need opposite fixes.
     */
    val confidence: Float = 1f,
    /**
     * ARGB of the page just outside this box, or 0 when it was not sampled.
     *
     * The translation panel used to be painted a fixed near-black. Inside a
     * white speech bubble nobody notices; over a sound effect drawn straight
     * onto the artwork — 'Snatch' at 442x402, 'Sob sob' at 332x309 — it is a
     * black rectangle across the drawing. Painting the panel in the colour it
     * covers makes it disappear into the bubble or the art either way.
     */
    val backgroundColor: Int = 0,
    var selected: Boolean = false
)

/**
 * Perceived brightness of an ARGB colour, 0..1. Shared so that the sampling
 * side and the drawing side rank colours the same way.
 */
fun luminanceOf(argb: Int): Float {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
}
