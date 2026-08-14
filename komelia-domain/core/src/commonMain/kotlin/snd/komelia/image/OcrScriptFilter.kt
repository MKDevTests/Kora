package snd.komelia.image

/**
 * Keeps only the OCR boxes written in the script we are translating from.
 *
 * PP-OCRv6 reads 50 languages, so on a manga page it also reads the Japanese
 * baked into the artwork — signage, sound effects, the shop's noren. Those boxes
 * sit right next to the speech bubbles, so [mergeOcrBoxes] glues them together
 * into one block spanning a quarter of the page, which then gets painted over
 * with a single opaque panel. Measured on one page: a 563x441 block holding the
 * shop sign and a bubble, and a 548x463 one holding three bubbles and a wall.
 *
 * Filtering has to happen BEFORE the merge, or the merge has already joined them.
 */
object OcrScriptFilter {

    /** Above this share of CJK characters, a box is not Latin text. */
    private const val CJK_TOLERANCE = 0.2

    fun keepLatin(boxes: List<OcrElementBox>): List<OcrElementBox> =
        boxes.filter { isLatin(it.text) }

    fun keepCjk(boxes: List<OcrElementBox>): List<OcrElementBox> =
        boxes.filter { it.text.any { c -> isCjk(c) } }

    /**
     * A little CJK is tolerated: the recogniser sometimes emits one stray glyph
     * inside an otherwise English line, and dropping the whole bubble for it
     * would cost more than it saves.
     */
    private fun isLatin(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return false
        val cjk = letters.count { isCjk(it) }
        return cjk.toDouble() / letters.length <= CJK_TOLERANCE
    }

    private fun isCjk(c: Char): Boolean = when (c.code) {
        in 0x3040..0x30FF -> true   // hiragana, katakana
        in 0x3400..0x4DBF -> true   // CJK extension A
        in 0x4E00..0x9FFF -> true   // CJK unified ideographs
        in 0xAC00..0xD7AF -> true   // hangul
        in 0xFF00..0xFFEF -> true   // fullwidth forms
        else -> false
    }
}
