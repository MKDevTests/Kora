package snd.komelia

import android.content.Context

/**
 * Persists the user-chosen log size cap in SharedPreferences so it can be read
 * synchronously in [App.onCreate], before logback initializes (the DB isn't
 * available that early). The cap is a TOTAL budget in MB; logback's
 * RollingFileAppender keeps the active file plus [ROLLED_FILES] rolled files,
 * so the per-file ceiling is the budget split across them.
 */
object LogSettings {
    private const val PREFS = "kora_logging"
    private const val KEY_CAP_MB = "log_total_size_cap_mb"
    const val DEFAULT_CAP_MB = 20

    /** logback FixedWindowRollingPolicy keeps this many rolled files (+ the active one). */
    private const val ROLLED_FILES = 3

    fun getCapMb(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_CAP_MB, DEFAULT_CAP_MB)

    fun setCapMb(context: Context, mb: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_CAP_MB, mb)
            .apply()
    }

    /**
     * Per-file ceiling for logback's `LOG_MAX_FILE_SIZE` property: the total
     * budget divided across the active file + [ROLLED_FILES] rolled files,
     * floored at 1 MB. e.g. 20MB -> "5mb", 50MB -> "12mb", 100MB -> "25mb".
     */
    fun perFileSize(capMb: Int): String {
        val perFile = (capMb / (ROLLED_FILES + 1)).coerceAtLeast(1)
        return "${perFile}mb"
    }
}
