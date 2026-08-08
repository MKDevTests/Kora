package snd.komelia.settings.model

import kotlinx.serialization.Serializable

@Serializable
enum class Epub3Theme(val foreground: Int, val background: Int, val label: String) {
    DAY(0xFF111111.toInt(), 0xFFFFFFFF.toInt(), "Day"),
    SEPIA(0xFF78350F.toInt(), 0xFFFEF9C3.toInt(), "Sepia"),
    CRISP_WHITE(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), "Crisp\nWhite"),
    NIGHT(0xFFD1D5DB.toInt(), 0xFF111827.toInt(), "Night"),
}

@Serializable
enum class Epub3TextAlign { JUSTIFY, LEFT, CENTER, RIGHT }

@Serializable
enum class Epub3ReadAloudColor(val colorInt: Int, val label: String) {
    YELLOW(0x4DFFFF00, "Yellow"),
    RED(0x4DFF0000, "Red"),
    GREEN(0x4D00FF00, "Green"),
    BLUE(0x4D0000FF, "Blue"),
    MAGENTA(0x4DFF00FF, "Magenta"),
}

@Serializable
enum class Epub3ColumnCount { AUTO, ONE, TWO }

@Serializable
data class Epub3NativeSettings(
    val theme: Epub3Theme = Epub3Theme.DAY,
    val fontFamily: String = "Literata",
    val fontSize: Double = 1.0,
    val lineHeight: Double = 1.4,
    val paragraphSpacing: Double = 0.5,
    val textAlign: Epub3TextAlign = Epub3TextAlign.JUSTIFY,
    val readAloudColor: Epub3ReadAloudColor = Epub3ReadAloudColor.YELLOW,
    /**
     * Which edges turn pages, reusing the image reader's enum so both settings
     * screens offer the same four choices under the same names.
     *
     * Persisted per reader rather than shared: this lands in the existing
     * `epub3_native_settings_json` column, so it costs no migration and leaves
     * the image reader's shipped setting alone. The default matches what the
     * reader did before — left back, right forward.
     */
    val tapNavigationMode: ReaderTapNavigationMode = ReaderTapNavigationMode.LEFT_RIGHT,
    val scroll: Boolean = false,
    val columnCount: Epub3ColumnCount = Epub3ColumnCount.AUTO,
    val pageMargins: Double = 1.0,
    val topMargin: Float = 56f,
    val bottomMargin: Float = 66f,
    val showDateTimeOverlay: Boolean = true,
    val showLocationOverlay: Boolean = true,
    val publisherStyles: Boolean = false,
    val respectPublisherColors: Boolean = false,
    val playbackSpeed: Double = 1.0,
    val rewindEnabled: Boolean = true,
    val rewindAfterInterruption: Double = 3.0,
    val rewindAfterBreak: Double = 10.0,
)
