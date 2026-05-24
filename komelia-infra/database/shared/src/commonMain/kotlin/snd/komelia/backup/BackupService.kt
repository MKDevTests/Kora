package snd.komelia.backup

/**
 * Export and import the user's local settings as a single JSON document.
 * Intended for surviving downgrade-reinstalls and moving config between
 * devices. Does NOT cover reading state (progress, bookmarks, annotations,
 * audio positions) or server-coupled fields like credentials.
 */
interface BackupService {
    /** Serialize the in-scope settings to a single JSON string. */
    suspend fun exportToJson(): String

    /**
     * Restore the bundle in [json] over the current settings. Sections
     * absent from the bundle are left alone; sections present overwrite
     * the current value wholesale.
     */
    suspend fun importFromJson(json: String): ImportResult
}

sealed interface ImportResult {
    /**
     * @param sectionsRestored human-readable list of what was applied.
     * @param sectionsSkipped human-readable list of sections that were
     *   intentionally not applied — e.g. per-user sections for a foreign
     *   account when the current user is not Komga admin. The dialog
     *   surfaces these so the user knows nothing went silently missing.
     */
    data class Success(
        val sectionsRestored: List<String>,
        val sectionsSkipped: List<String> = emptyList(),
    ) : ImportResult

    /** [reason] is a single-sentence end-user message. */
    data class Failure(val reason: String) : ImportResult
}
