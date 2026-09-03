package snd.komelia.db

import kotlinx.serialization.Serializable
import snd.komelia.settings.model.AppTheme
import snd.komelia.settings.model.AutobackupFrequency
import snd.komelia.settings.model.BooksLayout
import snd.komelia.settings.model.ChapterSeriesFilter
import snd.komelia.settings.model.StartupScreen
import snd.komelia.updates.AppVersion
import kotlin.time.Instant

@Serializable
data class AppSettings(
    val username: String = "admin@example.org",
    val serverUrl: String = "http://localhost:25600",

    /**
     * Alternate URLs for the *same* Komga server (e.g. a LAN IP at home and a
     * Tailscale address remotely). [serverUrl] is the active one; switching
     * promotes one of these to active and demotes the old active here. Because
     * they share the same server profile (same per-server DB), stats/ratings/
     * links stay unified across URLs. Stored as a JSON string array column.
     */
    val alternateServerUrls: List<String> = emptyList(),

    val cardWidth: Int = 170,
    // Multiples of 60 so a full page fills whole rows on 2 to 6 columns.
    val seriesPageLoadSize: Int = 60,
    val bookPageLoadSize: Int = 60,
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
    // Fresh-install default. Only ever read when no settings row exists yet,
    // so an existing install keeps whatever it had -- see the note on the class.
    val useFloatingNavigationBar: Boolean = true,
    /** Null means use default yellow (0xFFFFEB3B.toInt()). */
    val lastHighlightColor: Int? = null,
    /**
     * When true the global search bar appends Lucene fuzzy syntax (~1) to
     * query terms ≥ 4 chars so typos are tolerated. User-toggleable in the
     * search screen.
     */
    val searchFuzzyEnabled: Boolean = true,

    /**
     * Opt-in: when true the series Links tab may query the public AniList
     * GraphQL API to suggest related series. Off by default — it sends series
     * titles to a third party. See PRIVACY_POLICY.MD.
     */
    val aniListLinkSuggestionsEnabled: Boolean = false,

    /**
     * Opt-in: read (everyone) / write (admin only) typed series relations from
     * the shared Komga `links` field, on top of the private local links. Off by
     * default → purely local. See series Links tab.
     */
    // Fresh-install default. Only ever read when no settings row exists yet,
    // so an existing install keeps whatever it had -- see the note on the class.
    val shareLinksViaKomga: Boolean = true,

    /**
     * What to do with series whose title ends with "(Chap)" in every list — the
     * library grid, the home shelves, search, upcoming releases. Three-way since
     * V95; V94 shipped it as a boolean.
     */
    val chapterSeriesFilter: ChapterSeriesFilter = ChapterSeriesFilter.ANY,

    /** Optional FR/EN language pill on series covers (Home / Library). */
    val showLanguageOnCovers: Boolean = false,
    val languageBadgeScale: Float = 1.0f,
    /** false = top-left (default), true = bottom-left (just above the title). */
    val languageBadgeAtBottom: Boolean = false,

    /**
     * Recolors the top-right series card badge to signal a complete series
     * (status Ended and every volume owned) instead of just the unread count.
     * On by default.
     */
    val showCompleteSeriesBadge: Boolean = true,

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
     * When true the "Upcoming releases" page gets a dedicated entry in the
     * bottom navigation bar. When false the page is still reachable via its
     * Home card. Default off, mirrors [statsInBottomNav].
     */
    // Fresh-install default. Only ever read when no settings row exists yet,
    // so an existing install keeps whatever it had -- see the note on the class.
    val nextReleasesInBottomNav: Boolean = true,

    /**
     * App version (e.g. "1.0.3") for which the user has already
     * acknowledged the release-notes "What's new" modal. Null means
     * never seen, so the modal will show on the next launch.
     */
    val lastSeenReleaseNotesVersion: String? = null,

    /**
     * Master switch for the autobackup feature. Off by default — the
     * user must explicitly opt in (and pick a folder) before the
     * periodic worker is scheduled.
     */
    val autobackupEnabled: Boolean = false,

    /**
     * SAF tree URI (`content://…`) the user picked via
     * `ACTION_OPEN_DOCUMENT_TREE`. The matching permission is granted
     * via `takePersistableUriPermission` and survives reboot. Null when
     * the user hasn't picked a folder yet.
     */
    val autobackupFolderUri: String? = null,

    /** How often the periodic worker fires. */
    val autobackupFrequency: AutobackupFrequency = AutobackupFrequency.DAILY,

    /**
     * How many `kora-autobackup-*.json` files to keep in the chosen
     * folder. Older ones get pruned by the worker after each write.
     * Clamped to 1..10 at the UI layer; default 3.
     */
    val autobackupMaxKeep: Int = 3,

    /** Timestamp of the most recent successful run, for the settings UI. */
    val autobackupLastSuccessAt: Instant? = null,

    /** Timestamp of the most recent failure, for the settings UI. */
    val autobackupLastFailureAt: Instant? = null,

    /** Human-readable cause of the most recent failure. */
    val autobackupLastFailureMessage: String? = null,

    // --- Experimental features (App Settings → Experimental) ---

    /**
     * Experimental: show a per-library "Genre" tab that groups series by their
     * `kora:genre:*` Komga tags. Off by default. The catalog (genre list, chosen
     * cover, label override, cached count) is stored locally; a genre's series
     * are fetched live by tag.
     */
    // Fresh-install default. Only ever read when no settings row exists yet,
    // so an existing install keeps whatever it had -- see the note on the class.
    val experimentalGenreTab: Boolean = true,

    /**
     * Per-(library, genre) cover override for the Genre tab: key
     * "<libraryId|all>:<genreSlug>" → chosen series id. JSON map column.
     */
    val genreCoverOverrides: Map<String, String> = emptyMap(),

    /**
     * Per-(library, genre) display-name override for the Genre tab: key
     * "<libraryId|all>:<genreSlug>" → custom label. JSON map column.
     */
    val genreLabelOverrides: Map<String, String> = emptyMap(),

    /**
     * Experimental: master switch for the local Ignore List. When off, no
     * filtering happens and the "Ignore" action is hidden. Per-server.
     */
    // Fresh-install default. Only ever read when no settings row exists yet,
    // so an existing install keeps whatever it had -- see the note on the class.
    val ignoreListEnabled: Boolean = true,

    /**
     * One-shot flag: true once the admin's local Ignore List has been pushed to
     * the server as kora:hidden tags (the launch prompt). Prevents re-running.
     */
    val ignoreListMigratedToServerHidden: Boolean = false,

    /**
     * Experimental: series ids the user has ignored locally. Ignored series and
     * their books are filtered out of every list (libraries, collections,
     * search, home, genres). Never sent to the server. JSON string array column.
     */
    val ignoredSeriesIds: Set<String> = emptySet(),

    /**
     * Series ids the user has marked as favorites locally (cross-library). Shown
     * in the virtual "Favorites" section. Per-server, never sent to the server.
     * JSON string array column.
     */
    val favoriteSeriesIds: Set<String> = emptySet(),

    /**
     * Series ids the user wants to read but isn't actively following yet
     * (cross-library). Independent from Favorites — a series can be both.
     * Shown in the virtual "Planned" section. Per-server, never sent to the
     * server. JSON string array column.
     */
    val plannedSeriesIds: Set<String> = emptySet(),

    /**
     * Local cache of `seriesId -> libraryId`, so the personal lists (Favorites /
     * Planned) can be filtered by library WITHOUT resolving every entry over the
     * network first. Filled in as entries are resolved or added; a missing id is
     * simply resolved once and recorded. Never a source of truth.
     */
    val seriesLibraryIds: Map<String, String> = emptyMap(),

    /**
     * Libraries kept OUT of the "All" view of the personal lists — e.g. a
     * "Divers" library you only want to browse on its own tab. Still reachable
     * by selecting that library explicitly. Shared by Favorites and Planned.
     */
    val excludedLibraryIds: Set<String> = emptySet(),

    /**
     * Experimental Genre tab: when true the genre tiles use their own
     * appearance (below) instead of inheriting the global card style. Size +
     * text only, per the user's choice.
     */
    val genreTilesCustomAppearance: Boolean = false,
    val genreTileWidth: Int = 170,
    /** false = title overlaid on the cover, true = title below it. */
    val genreTileTextBelow: Boolean = false,
    val genreTileShowCount: Boolean = true,

    /**
     * When true, only the author roles the user kept are displayed (book page,
     * series pages). Off by default → every credit is shown, as before.
     */
    val authorRolesFilterEnabled: Boolean = false,
    /** "" = follow the system locale; otherwise a language tag such as "fr". */
    val uiLanguage: String = "",

    /**
     * Roles hidden when the filter above is on, lowercase ("letterer", …).
     * The HIDDEN set is stored rather than the visible one, so a role Komga
     * adds later shows up instead of silently vanishing.
     */
    val hiddenAuthorRoles: Set<String> = emptySet(),
)
