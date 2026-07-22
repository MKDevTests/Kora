package snd.komelia.toolkit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
)

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
    val result: ReleaseResult? = null,
    val error: String = "",
    @SerialName("created_at") val createdAt: Double = 0.0,
    @SerialName("updated_at") val updatedAt: Double = 0.0,
) {
    val isTerminal: Boolean get() = status == STATUS_COMPLETED || status == STATUS_FAILED
    val isCompleted: Boolean get() = status == STATUS_COMPLETED

    companion object {
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

/** The two release-tracking sources; slug is used in the URL path. */
enum class ToolkitSource(val slug: String, val label: String) {
    MANGA_NEWS("manga-news", "Manga News"),
    MANGABAKA("mangabaka", "MangaBaka");

    companion object {
        /** Server `source` field uses an underscore ("manga_news"); map both forms. */
        fun fromServer(value: String?): ToolkitSource? = when (value) {
            "manga_news", "manga-news" -> MANGA_NEWS
            "mangabaka" -> MANGABAKA
            else -> null
        }
    }
}
