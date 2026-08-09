package com.storyteller.reader

import org.readium.r2.navigator.epub.css.FontWeight
import org.readium.r2.navigator.preferences.FontFamily

/**
 * The fonts the reader offers, in one place.
 *
 * There used to be two entries and one of them did not work: `Literata` was
 * declared as `fonts/Literata_500Medium.ttf`, a file that existed in neither the
 * repository nor the apk (checked by unzipping it). Being the default, it meant
 * every book was rendered in whatever the WebView fell back to. That is the kind
 * of thing a hand-written list in the middle of a fragment hides, hence this
 * list, read by both the fragment that declares the faces and the settings
 * screen that offers them.
 *
 * Files are shipped **unmodified**. The OFL allows subsetting, and subsetting
 * would cut these to roughly a sixth, but half of them reserve their name — a
 * subset would have to ship as "Korabooks Lora", which is worse in a font menu
 * than a larger download.
 */
sealed interface BundledFontFace {
    val asset: String
    val italic: Boolean

    /** One file, one weight. */
    data class Static(
        override val asset: String,
        override val italic: Boolean,
        val weight: FontWeight,
    ) : BundledFontFace

    /**
     * One file covering a weight range.
     *
     * The range is declared wider than any of these fonts actually carry: CSS
     * clamps to what the font has, and the point is only to tell the engine the
     * face can be bold, so it stops synthesising a fake bold over the regular.
     */
    data class Variable(
        override val asset: String,
        override val italic: Boolean,
        val weights: IntRange = 100..900,
    ) : BundledFontFace
}

/**
 * A font offered in the reader's picker.
 *
 * [faces] is empty for the families the reading engine already answers for: the
 * CSS generics, which the system serves, and the two Readium ships in its own
 * assets. Those cost nothing to offer.
 */
data class BundledFont(
    /** The CSS family name, and what gets stored in the settings. */
    val family: String,
    /** What the picker shows. */
    val label: String,
    val faces: List<BundledFontFace> = emptyList(),
)

object BundledFonts {

    private fun variable(name: String) = listOf(
        BundledFontFace.Variable("fonts/$name-Roman-VF.ttf", italic = false),
        BundledFontFace.Variable("fonts/$name-Italic-VF.ttf", italic = true),
    )

    private fun fourFaces(name: String, extension: String = "ttf") = listOf(
        BundledFontFace.Static("fonts/$name-Regular.$extension", false, FontWeight.NORMAL),
        BundledFontFace.Static("fonts/$name-Italic.$extension", true, FontWeight.NORMAL),
        BundledFontFace.Static("fonts/$name-Bold.$extension", false, FontWeight.BOLD),
        BundledFontFace.Static("fonts/$name-BoldItalic.$extension", true, FontWeight.BOLD),
    )

    /** Serif faces meant for long-form reading, roughly from most to least neutral. */
    val serif: List<BundledFont> = listOf(
        BundledFont("Literata", "Literata", variable("Literata")),
        BundledFont("Lora", "Lora", variable("Lora")),
        BundledFont("PT Serif", "PT Serif", fourFaces("PTSerif")),
        BundledFont("Source Serif 4", "Source Serif", variable("SourceSerif4")),
        BundledFont("Bitter", "Bitter", variable("Bitter")),
        BundledFont("Crimson Pro", "Crimson Pro", variable("CrimsonPro")),
        BundledFont("EB Garamond", "EB Garamond", variable("EBGaramond")),
        BundledFont("Libre Baskerville", "Libre Baskerville", variable("LibreBaskerville")),
    )

    /**
     * Faces chosen for legibility rather than for the look of a page.
     *
     * OpenDyslexic keeps its four hand-picked files: it is not a variable font,
     * and its italic and bold are drawn, not interpolated.
     */
    val accessible: List<BundledFont> = listOf(
        BundledFont("Atkinson Hyperlegible", "Atkinson Hyperlegible", fourFaces("AtkinsonHyperlegible")),
        BundledFont(FontFamily.OPEN_DYSLEXIC.name, "OpenDyslexic", listOf(
            BundledFontFace.Static("fonts/OpenDyslexic-Regular.otf", false, FontWeight.NORMAL),
            BundledFontFace.Static("fonts/OpenDyslexic-Italic.otf", true, FontWeight.NORMAL),
            BundledFontFace.Static("fonts/OpenDyslexic-Bold.otf", false, FontWeight.BOLD),
            BundledFontFace.Static("fonts/OpenDyslexic-Bold-Italic.otf", true, FontWeight.BOLD),
        )),
        // Shipped inside Readium's own assets and known to its CSS — offering
        // them costs nothing at all.
        BundledFont(FontFamily.ACCESSIBLE_DFA.name, "Accessible DfA"),
        BundledFont(FontFamily.IA_WRITER_DUOSPACE.name, "iA Writer Duospace"),
    )

    /**
     * The reader falls back on the device for these.
     *
     * `cursive` and `fantasy` are deliberately left out: Android maps them to
     * whatever is at hand, and the result is not something to read a novel in.
     */
    val system: List<BundledFont> = listOf(
        BundledFont(FontFamily.SERIF.name, "Serif"),
        BundledFont(FontFamily.SANS_SERIF.name, "Sans serif"),
        BundledFont(FontFamily.MONOSPACE.name, "Monospace"),
    )

    /** Every family the picker offers, in the order it offers them. */
    val all: List<BundledFont> = serif + accessible + system

    /** The default, and now a font that is actually in the apk. */
    val default: BundledFont = serif.first()

    /**
     * Every file the WebView is allowed to ask for.
     *
     * Missing an entry here does not fail loudly: the `@font-face` simply never
     * loads and the text quietly renders in something else. Deriving it from the
     * same list as the declarations is the point.
     */
    val servedAssets: List<String> = all.flatMap { font -> font.faces.map { it.asset } }
}
