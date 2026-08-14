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
    var selected: Boolean = false
)
