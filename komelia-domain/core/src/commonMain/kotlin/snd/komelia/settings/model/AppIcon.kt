package snd.komelia.settings.model

/**
 * Launcher icon. Android switches between activity aliases (one per value)
 * so the change shows up on the home screen without a reinstall; other
 * platforms ignore it.
 */
enum class AppIcon {
    DEFAULT, EMBER, FOREST, MONO;

    companion object {
        fun parse(name: String?): AppIcon = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
