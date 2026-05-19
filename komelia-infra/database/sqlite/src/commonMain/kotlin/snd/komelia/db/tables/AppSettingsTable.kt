package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

object AppSettingsTable : Table("AppSettings") {
    val version = integer("version")
    val username = text("username")
    val serverUrl = text("serverUrl")
    val cardWidth = integer("card_width")
    val seriesPageLoadSize = integer("series_page_load_size")

    val bookPageLoadSize = integer("book_page_load_size")
    val bookListLayout = text("book_list_layout")
    val appTheme = text("app_theme")

    val checkForUpdatesOnStartup = bool("check_for_updates_on_startup")

    //FIXME Android doesn't support JDBC 4.1.
    // timestamp field type uses java.sql.ResultSet.getObject(int columnIndex, Class<T> type)
    // which does not exist on Android. why???
    val updateLastCheckedTimestamp = text("update_last_checked_timestamp").nullable()
    val updateLastCheckedReleaseVersion = text("update_last_checked_release_version").nullable()
    val updateDismissedVersion = text("update_dismissed_version").nullable()

    val navBarColor = text("nav_bar_color").nullable()
    val accentColor = text("accent_color").nullable()
    val useNewLibraryUI = bool("use_new_library_ui").default(true)
    val cardLayoutBelow = bool("card_layout_below").default(false)
    val immersiveColorEnabled = bool("immersive_color_enabled").default(true)
    val immersiveColorAlpha = float("immersive_color_alpha").default(0.12f)
    val lastSelectedLibraryId = text("last_selected_library_id").nullable()
    val hideParenthesesInNames = bool("hide_parentheses_in_names").default(true)
    val lockScreenRotation = bool("lock_screen_rotation").default(false)
    val keepReaderScreenOn = bool("keep_reader_screen_on").default(false)
    val cardLayoutOverlayBackground = bool("card_layout_overlay_background").default(false)
    val showImmersiveNavBar = bool("show_immersive_nav_bar").default(true)
    val useNewLibraryUI2 = bool("use_new_library_ui_2").default(true)
    val showContinueReading = bool("show_continue_reading").default(true)
    val useImmersiveMorphingCover = bool("use_immersive_morphing_cover").default(true)
    val cardWidthScale = float("card_width_scale").default(0.95f)
    val cardHeightScale = float("card_height_scale").default(0.95f)
    val cardSpacingBelow = float("card_spacing_below").default(0.0f)
    val cardShadowLevel = float("card_shadow_level").default(2.0f)
    val cardCornerRadius = float("card_corner_radius").default(8.0f)
    val useFloatingNavigationBar = bool("use_floating_navigation_bar").default(false)
    val lastHighlightColor = integer("last_highlight_color").nullable()
    val searchFuzzyEnabled = bool("search_fuzzy_enabled").default(true)
    val libraryDropdownInTitle = bool("library_dropdown_in_title").default(true)
    val startupScreen = text("startup_screen").default("HOME")
    val statsEnabled = bool("stats_enabled").default(true)
    val statsInBottomNav = bool("stats_in_bottom_nav").default(false)

    override val primaryKey = PrimaryKey(version)
}
