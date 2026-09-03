package snd.komelia.offline.book.model

/**
 * Who asked for a downloaded book.
 *
 * The distinction exists for one reason: the cleaner. A book the user
 * downloaded on purpose is not the app's to delete behind their back, while a
 * book the app fetched on its own is exactly what it should reclaim first. Ask
 * a single flag to carry both and you get a cleaner that either never frees
 * anything or quietly throws away what someone chose to keep.
 */
enum class DownloadOrigin {
    /** The user asked for this book — from a menu, a selection, a series. */
    MANUAL,

    /** The app fetched it to stay ahead of what the user is reading. */
    AUTOMATIC,
}
