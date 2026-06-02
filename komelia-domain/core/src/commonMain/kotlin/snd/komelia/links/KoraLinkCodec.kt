package snd.komelia.links

import snd.komga.client.common.KomgaWebLink
import snd.komga.client.series.KomgaSeriesId

/**
 * Encodes/decodes Kora series relations as Komga web links, so typed relations
 * can be shared across users via the series `links` metadata field.
 *
 * A Kora link:
 *   label = "Sequel"  — human-friendly, cosmetic (shown clickable in Komga web)
 *   url   = "{serverUrl}/#/series/{targetId}?kora=sequel"  — machine-readable
 *
 * The `?kora=<token>` marker identifies Kora-managed links and carries the
 * relation type; the target series id is parsed from the `/series/<id>` path so
 * it survives a different host/reverse-proxy.
 */
object KoraLinkCodec {
    private const val MARKER = "kora="
    private const val SERIES_PATH = "/series/"

    /** Build a shareable link: "[target] is [type] of the current series". */
    fun relationLink(serverUrl: String, target: KomgaSeriesId, type: SeriesRelationType): KomgaWebLink =
        KomgaWebLink(
            type.displayLabel(),
            "${serverUrl.trimEnd('/')}$SERIES_PATH${target.value}?$MARKER${type.token()}",
        )

    fun isKoraLink(link: KomgaWebLink): Boolean = link.url.contains(MARKER)

    /** Parse a Kora-managed relation from a link, or null if it isn't one. */
    fun parse(link: KomgaWebLink): ParsedRelation? {
        val url = link.url
        val markerIdx = url.indexOf(MARKER)
        if (markerIdx < 0) return null
        val token = url.substring(markerIdx + MARKER.length).takeWhile { it != '&' && it != '#' }
        val type = tokenToType(token) ?: return null
        val target = parseSeriesId(url) ?: return null
        return ParsedRelation(target, type)
    }

    private fun parseSeriesId(url: String): KomgaSeriesId? {
        val idx = url.indexOf(SERIES_PATH)
        if (idx < 0) return null
        val id = url.substring(idx + SERIES_PATH.length)
            .takeWhile { it != '?' && it != '/' && it != '#' && it != '&' }
        return id.takeIf { it.isNotBlank() }?.let { KomgaSeriesId(it) }
    }

    private fun SeriesRelationType.token(): String = when (this) {
        SeriesRelationType.SEQUEL -> "sequel"
        SeriesRelationType.PREQUEL -> "prequel"
        SeriesRelationType.SPIN_OFF -> "spinoff"
        SeriesRelationType.MAIN_STORY -> "mainstory"
        SeriesRelationType.RELATED -> "related"
    }

    private fun tokenToType(token: String): SeriesRelationType? = when (token) {
        "sequel" -> SeriesRelationType.SEQUEL
        "prequel" -> SeriesRelationType.PREQUEL
        "spinoff" -> SeriesRelationType.SPIN_OFF
        "mainstory" -> SeriesRelationType.MAIN_STORY
        "related" -> SeriesRelationType.RELATED
        else -> null
    }

    private fun SeriesRelationType.displayLabel(): String = when (this) {
        SeriesRelationType.SEQUEL -> "Sequel"
        SeriesRelationType.PREQUEL -> "Prequel"
        SeriesRelationType.SPIN_OFF -> "Spin-off"
        SeriesRelationType.MAIN_STORY -> "Main series"
        SeriesRelationType.RELATED -> "Related"
    }

    data class ParsedRelation(val target: KomgaSeriesId, val type: SeriesRelationType)
}
