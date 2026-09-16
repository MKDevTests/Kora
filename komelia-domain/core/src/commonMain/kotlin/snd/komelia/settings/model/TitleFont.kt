package snd.komelia.settings.model

/** Face of the screen and item titles (display, headline, titleLarge). */
enum class TitleFont {
    SERIF, SANS, SYSTEM;

    companion object {
        fun parse(name: String?): TitleFont = entries.firstOrNull { it.name == name } ?: SERIF
    }
}
