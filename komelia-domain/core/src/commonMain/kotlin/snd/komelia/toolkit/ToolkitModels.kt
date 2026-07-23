package snd.komelia.toolkit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Shared lenient Json for decoding [ToolkitJob.result] into the right shape. */
internal val toolkitJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** The two automation functions. The slug is the URL path segment. */
enum class ToolkitFunction(val slug: String, val label: String) {
    NEXT_RELEASES("next-releases", "Prochaines sorties"),
    RELEASE_TRACKING("release-tracking", "Suivi des sorties"),
}

/**
 * Wire models for the Komga Toolkit automation API (release tracking).
 * Field names mirror the server JSON exactly (snake_case via @SerialName).
 *
 * The client's Json must be configured with `ignoreUnknownKeys = true`: the
 * server sends extra fields (raw_count, source_kind, guided_payload, …) that
 * Kora doesn't use, and may add more.
 *
 * See the integration doc for the mandatory flow: status → preview → poll job
 * → show counters + high-confidence rows → confirm → poll → summary.
 */

/** `GET /api/automation/status`. */
@Serializable
data class ToolkitStatus(
    @SerialName("contract_version") val contractVersion: String,
    val ready: Boolean,
    @SerialName("komga_connected") val komgaConnected: Boolean,
    @SerialName("preview_expires_in_seconds") val previewExpiresInSeconds: Int = 1800,
    val sources: List<String> = emptyList(),
    /** Per-source usability, e.g. ComicVine is false until its key is set. */
    @SerialName("source_ready") val sourceReady: Map<String, Boolean> = emptyMap(),
    @SerialName("request_delays_seconds") val requestDelays: Map<String, Double> = emptyMap(),
) {
    /** True when this source can be used (absent = assume usable). */
    fun isSourceReady(source: ToolkitSource): Boolean = sourceReady[source.serverKey] ?: true
}

/** Envelope returned by the preview/confirm POSTs. */
@Serializable
data class ToolkitJobEnvelope(
    @SerialName("contract_version") val contractVersion: String = "",
    // Absent on GET jobs/{id} (only preview/confirm set kind + source).
    val kind: String? = null,
    val source: String? = null,
    val job: ToolkitJob,
)

/** A running or finished automation job (also the body of `GET jobs/{id}`). */
@Serializable
data class ToolkitJob(
    val id: String,
    val label: String = "",
    val status: String,
    val current: Int = 0,
    val total: Int = 0,
    val message: String = "",
    // Raw so it can be decoded to the shape that matches the function + step:
    // a next-releases preview, a release-tracking preview, or an apply summary.
    val result: JsonObject? = null,
    val error: String = "",
    @SerialName("created_at") val createdAt: Double = 0.0,
    @SerialName("updated_at") val updatedAt: Double = 0.0,
) {
    val isTerminal: Boolean get() = status == STATUS_COMPLETED || status == STATUS_FAILED
    val isCompleted: Boolean get() = status == STATUS_COMPLETED

    fun releaseTrackingResult(): ReleaseResult? = decode(ReleaseResult.serializer())
    fun nextReleaseResult(): NextReleaseResult? = decode(NextReleaseResult.serializer())
    fun applyResult(): ApplyResult? = decode(ApplyResult.serializer())
    fun nextReleaseAutoResult(): NextReleaseAutoResult? = decode(NextReleaseAutoResult.serializer())
    fun releaseTrackingAutoResult(): ReleaseTrackingAutoResult? = decode(ReleaseTrackingAutoResult.serializer())

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>): T? =
        result?.let { runCatching { toolkitJson.decodeFromJsonElement(serializer, it) }.getOrNull() }

    companion object {
        const val STATUS_QUEUED = "queued"
        const val STATUS_RUNNING = "running"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
    }
}

/** `job.result` once a release-tracking job completes. */
@Serializable
data class ReleaseResult(
    val mode: String = "",
    val source: String = "",
    @SerialName("library_id") val libraryId: String = "",
    val loaded: Int = 0,
    @SerialName("non_ended") val nonEnded: Int = 0,
    val linked: Int = 0,
    @SerialName("high_confidence") val highConfidence: Int = 0,
    val review: Int = 0,
    val ignored: Int = 0,
    val errors: Int = 0,
    @SerialName("non_manga") val nonManga: Int = 0,
    val returned: Int = 0,
    val rows: List<ReleaseRow> = emptyList(),
) {
    /** Rows Toolkit would actually apply on confirm: only these are shown to confirm. */
    val applicableRows: List<ReleaseRow> get() = rows.filter { it.applyTotalBookCount || it.applyStatus }
}

/** One series' proposed change. Many fields are informational; Kora shows title,
 *  current/proposed volume count, current/proposed status, source and risk. */
@Serializable
data class ReleaseRow(
    @SerialName("series_id") val seriesId: String,
    val title: String = "",
    val source: String = "",
    @SerialName("source_url") val sourceUrl: String = "",
    @SerialName("current_total") val currentTotal: Int? = null,
    @SerialName("source_total") val sourceTotal: Int? = null,
    @SerialName("current_status") val currentStatus: String? = null,
    @SerialName("source_status") val sourceStatus: String? = null,
    @SerialName("total_decision") val totalDecision: TotalDecision = TotalDecision(),
    @SerialName("status_decision") val statusDecision: StatusDecision = StatusDecision(),
    val risk: String = "",
    @SerialName("apply_status") val applyStatus: Boolean = false,
    @SerialName("apply_totalBookCount") val applyTotalBookCount: Boolean = false,
    val action: String = "",
    val confidence: String = "",
)

@Serializable
data class TotalDecision(
    val current: Int? = null,
    val source: Int? = null,
    val proposed: Int? = null,
    val action: String = "",
    val risk: String = "",
    val note: String = "",
)

@Serializable
data class StatusDecision(
    val current: String? = null,
    val source: String? = null,
    val proposed: String? = null,
    val action: String = "",
    val risk: String = "",
    val note: String = "",
)

/**
 * `job.result` for a **next-releases** preview (mode "next_release_preview").
 * Rows are only the tags that actually differ and whose date is today/future.
 *
 * NOTE: the row field names below are a best guess — the doc lists the columns
 * (titre, tome, date, ancien tag, nouveau tag) but ships an empty rows[]. They
 * must be confirmed against a real preview with changes > 0.
 */
@Serializable
data class NextReleaseResult(
    val mode: String = "",
    val loaded: Int = 0,
    @SerialName("non_ended") val nonEnded: Int = 0,
    val linked: Int = 0,
    val changes: Int = 0,
    val unchanged: Int = 0,
    @SerialName("no_release") val noRelease: Int = 0,
    val errors: Int = 0,
    val returned: Int = 0,
    val rows: List<NextReleaseRow> = emptyList(),
)

@Serializable
data class NextReleaseRow(
    @SerialName("series_id") val seriesId: String = "",
    val title: String = "",
    val volume: String = "",
    val date: String = "",
    @SerialName("current_tag") val currentTag: String? = null,
    @SerialName("proposed_tag") val proposedTag: String? = null,
    val source: String = "",
)

/**
 * `job.result` after a **confirm** (apply) job of either function: the write
 * summary. Extra fields are ignored, so this covers both functions.
 */
@Serializable
data class ApplyResult(
    val applied: Int = 0,
    val unchanged: Int = 0,
    @SerialName("skipped_guardrail") val skippedGuardrail: Int = 0,
    val failed: Int = 0,
)

/** `job.result` after a /run next-releases job (mode "next_release_auto"):
 *  what was actually written. Rows carry the real fields from the guide. */
@Serializable
data class NextReleaseAutoResult(
    val mode: String = "",
    val source: String = "",
    val scanned: Int = 0,
    @SerialName("valid_changes") val validChanges: Int = 0,
    val applied: Int = 0,
    val unchanged: Int = 0,
    @SerialName("skipped_guardrail") val skippedGuardrail: Int = 0,
    val failed: Int = 0,
    val cancelled: Boolean = false,
    val rows: List<NextReleaseAutoRow> = emptyList(),
)

@Serializable
data class NextReleaseAutoRow(
    @SerialName("series_id") val seriesId: String = "",
    val title: String = "",
    val source: String = "",
    @SerialName("old_tag") val oldTag: String = "",
    @SerialName("new_tag") val newTag: String = "",
    val volume: String = "",
    val date: String = "",
)

/** `job.result` after a /run release-tracking job (mode "release_tracking_auto"). */
@Serializable
data class ReleaseTrackingAutoResult(
    val mode: String = "",
    val source: String = "",
    val scanned: Int = 0,
    @SerialName("high_confidence") val highConfidence: Int = 0,
    val applied: Int = 0,
    val unchanged: Int = 0,
    @SerialName("skipped_guardrail") val skippedGuardrail: Int = 0,
    val failed: Int = 0,
    val cancelled: Boolean = false,
    val rows: List<ReleaseTrackingAutoRow> = emptyList(),
)

@Serializable
data class ReleaseTrackingAutoRow(
    @SerialName("series_id") val seriesId: String = "",
    val title: String = "",
    val source: String = "",
    @SerialName("current_status") val currentStatus: String = "",
    @SerialName("new_status") val newStatus: String = "",
    @SerialName("current_totalBookCount") val currentTotal: Int? = null,
    @SerialName("new_totalBookCount") val newTotal: Int? = null,
)

/**
 * Automation sources. [slug] is the URL path segment; [serverKey] is the value
 * the server uses in `source`/`source_ready` (underscore form).
 */
enum class ToolkitSource(val slug: String, val serverKey: String, val label: String) {
    MANGA_NEWS("manga-news", "manga_news", "Manga News"),
    MANGABAKA("mangabaka", "mangabaka", "MangaBaka"),
    BEDETHEQUE("bedetheque", "bedetheque", "Bedetheque"),
    COMICVINE("comicvine", "comicvine", "ComicVine");

    companion object {
        fun fromServer(value: String?): ToolkitSource? =
            entries.firstOrNull { it.serverKey == value || it.slug == value }
    }
}
