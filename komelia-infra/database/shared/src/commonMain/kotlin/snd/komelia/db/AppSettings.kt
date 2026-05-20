package snd.komelia.db

import kotlinx.serialization.Serializable
import snd.komelia.settings.model.AppTheme
import snd.komelia.settings.model.BooksLayout
import snd.komelia.settings.model.StartupScreen
import snd.komelia.updates.AppVersion
import kotlin.time.Instant

@Serializable
data class AppSettings(
    val username: String = "admin@example.org",
    val serverUrl: String = "http://localhost:25600",

    val cardWidth: Int = 170,
    val seriesPageLoadSize: Int = 20,
    val bookPageLoadSize: Int = 20,
    val bookListLayout: BooksLayout = BooksLayout.GRID,
    val appTheme: AppTheme = AppTheme.DARK_MODERN,

    val checkForUpdatesOnStartup: Boolean = true,
    val updateLastCheckedTimestamp: Instant? = null,
    val updateLastCheckedReleaseVersion: AppVersion? = null,
    val updateDismissedVersion: AppVersion? = null,

    val navBarColor: Long? = null,
    val accentColor: Long? = null,
    val useNewLibraryUI: Boolean = true,
    val cardLayoutBelow: Boolean = false,
    val immersiveColorEnabled: Boolean = true,
    val immersiveColorAlpha: Float = 0.12f,
    val lastSelectedLibraryId: String? = null,
    val hideParenthesesInNames: Boolean = true,
    val lockScreenRotation: Boolean = false,
    val keepReaderScreenOn: Boolean = false,
    val cardLayoutOverlayBackground: Boolean = false,
    val showImmersiveNavBar: Boolean = true,
    val useNewLibraryUI2: Boolean = true,
    val showContinueReading: Boolean = true,
    val useImmersiveMorphingCover: Boolean = true,
    val cardWidthScale: Float = 0.95f,
    val cardHeightScale: Float = 0.95f,
    val cardSpacingBelow: Float = 0.0f,
    val cardShadowLevel: Float = 2.0f,
    val cardCornerRadius: Float = 8.0f,
    val useFloatingNavigationBar: Boolean = false,
    /** Null means use default yellow (0xFFFFEB3B.toInt()). */
    val lastHighlightColor: Int? = null,
    /**
     * When true the global search bar appends Lucene fuzzy syntax (~1) to
     * query terms ≥ 4 chars so typos are tolerated. User-toggleable in the
     * search screen.
     */
    val searchFuzzyEnabled: Boolean = true,

    /**
     * When true the big page title at the top of Home / Library screens
     * becomes a dropdown that lists Home + every library for one-tap
     * switching. When false the title is plain text (historical behaviour);
     * users keep the side drawer (☰) for library switching.
     */
    val libraryDropdownInTitle: Boolean = true,

    /** Which screen the app navigates to on cold start. */
    val startupScreen: StartupScreen = StartupScreen.HOME,

    /**
     * Master switch for the Reading Stats feature. When false, the stats
     * page is unreachable, the Home card hides and the bottom-nav button
     * (if enabled) disappears. Completion-event logging stops too so we
     * don't accumulate data the user doesn't want.
     */
    val statsEnabled: Boolean = true,

    /**
     * When true the Stats page gets a dedicated entry in the bottom
     * navigation bar (next to Home / Search / Library). When false the
     * page is still reachable via the Home card. Default off to keep the
     * historical nav layout for existing users.
     */
    val statsInBottomNav: Boolean = false,

    /**
     * App version (e.g. "1.0.3") for which the user has already
     * acknowledged the release-notes "What's new" modal. Null means
     * never seen, so the modal will show on the next launch.
     */
    val lastSeenReleaseNotesVersion: String? = null,
)
