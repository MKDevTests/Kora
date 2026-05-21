package snd.komelia.settings.model

/**
 * How often the autobackup worker fires when the feature is enabled.
 * Mapped to a duration by [periodDays].
 */
enum class AutobackupFrequency(val periodDays: Long) {
    DAILY(1),
    WEEKLY(7),
    FORTNIGHTLY(15),
}
