package snd.komelia.settings.model

/**
 * Which screen the app navigates to on cold start, before the user
 * picks anything in-session.
 */
enum class StartupScreen {
    /** Curated landing page with carousels (default — matches historical behaviour). */
    HOME,

    /** Drop the user straight into the last library they opened, "resume" style. */
    LAST_LIBRARY,
}
