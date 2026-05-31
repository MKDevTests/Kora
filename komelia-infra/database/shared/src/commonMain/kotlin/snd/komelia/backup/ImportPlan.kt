package snd.komelia.backup

/**
 * Outcome of validating a backup bundle WITHOUT applying it. Produced by
 * [BackupService.dryRun] and shown in the import-preview dialog so the user
 * sees exactly what a restore would replace before committing to it.
 */
sealed interface DryRunResult {
    /** Bundle parsed and its schema is supported. [plan] describes the per-section effect. */
    data class Ok(val plan: ImportPlan) : DryRunResult

    /** Bundle is unusable (not a Kora backup, or a newer schema). [reason] is end-user text. */
    data class Invalid(val reason: String) : DryRunResult
}

/**
 * Pre-flight summary of what importing a given bundle would do. Restore
 * semantics are REPLACE: a section present in the bundle overwrites the
 * current value wholesale (see [BackupService]). The dry run never mutates
 * anything — it only reads the current state to compute the counts.
 */
data class ImportPlan(
    val schemaVersion: Int,
    val exportedBy: String?,
    val exportedAt: String?,
    val sections: List<SectionPlan>,
) {
    /** True if at least one section would actually change something. */
    val hasAnyEffect: Boolean get() = sections.any { it.action == SectionAction.REPLACE }
}

/**
 * Per-section effect of an import.
 *
 * @param label human-readable section name.
 * @param action [SectionAction.REPLACE] or [SectionAction.SKIP].
 * @param currentCount entries currently stored, or null for single-object
 *   settings sections (which are simply overwritten).
 * @param incomingCount valid entries the backup would write, or null for
 *   single-object settings sections.
 * @param invalidCount entries dropped because they failed validation
 *   (out-of-range stars, unknown event type, blank id, negative timestamp).
 * @param detail extra context for the user — typically why a section is skipped.
 */
data class SectionPlan(
    val label: String,
    val action: SectionAction,
    val currentCount: Int? = null,
    val incomingCount: Int? = null,
    val invalidCount: Int = 0,
    val detail: String? = null,
)

enum class SectionAction {
    /** Will overwrite the current value(s). */
    REPLACE,

    /** Present in the bundle but intentionally not applied (e.g. another user's data, no admin rights). */
    SKIP,
}
