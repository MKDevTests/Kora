package snd.komelia.settings.model

/** What the corner of a series card says about its unread books. */
enum class UnreadBadgeStyle {
    /** The number of unread books (the default since forever). */
    COUNT,
    /** A small dot: "there is something left", without the arithmetic. */
    DOT,
    /** Nothing; the complete-series check mark still shows. */
    NONE;

    companion object {
        fun parse(name: String?): UnreadBadgeStyle = entries.firstOrNull { it.name == name } ?: COUNT
    }
}
