package snd.komelia.settings.model

/**
 * Light or dark. Since 1.8.22 the UI writes only DARK, LIGHT and SYSTEM;
 * the other three are what older installs and backups carry and still
 * parse (DARKER also flipped `pureBlack` on in migration V113). Colours no
 * longer live here: they come from the palette seed, see snd.komelia.ui.Theme.
 */
enum class AppTheme {
    DARK, LIGHT, DARKER, LIGHT_MODERN, DARK_MODERN, SYSTEM
}