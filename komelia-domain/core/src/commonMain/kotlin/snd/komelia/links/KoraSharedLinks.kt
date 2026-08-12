package snd.komelia.links

import snd.komelia.komga.api.KomgaSeriesApi
import snd.komga.client.common.KomgaWebLink
import snd.komga.client.common.patch
import snd.komga.client.common.patchLists
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.series.KomgaSeriesMetadataUpdateRequest

/**
 * Reads and writes the *shared* link layer: relations published to Komga as web
 * links (see [KoraLinkCodec]), so they travel with the server instead of living
 * in one app install.
 *
 * Split out of the series Links tab because it was private there, and a second
 * writer — the admin chapter screen — was silently writing local-only links as
 * a result. Every relation type goes through here: the codec covers all of
 * them, and nothing about a chapters/volumes pair makes it less shareable than
 * a sequel.
 *
 * Writing is the caller's decision, not this object's: the rule is "sharing
 * enabled AND admin", and only the caller knows both.
 */
object KoraSharedLinks {

    /** The relations published on [series], read straight from its metadata. */
    fun relationsOf(series: KomgaSeries): List<SeriesRelation> =
        series.metadata.links
            .mapNotNull { KoraLinkCodec.parse(it) }
            .map { SeriesRelation(it.target, it.type) }

    /**
     * Publish "[to] is the [type] of [from]" on the server, both directions —
     * the inverse edge is written on [to] so the pair reads correctly from
     * either series without a second lookup.
     */
    suspend fun link(
        seriesApi: KomgaSeriesApi,
        from: KomgaSeriesId,
        to: KomgaSeriesId,
        type: SeriesRelationType,
    ) {
        write(seriesApi, on = from, target = to, type = type)
        write(seriesApi, on = to, target = from, type = type.inverse())
    }

    /** Remove the published pairing between [a] and [b], both directions. */
    suspend fun unlink(seriesApi: KomgaSeriesApi, a: KomgaSeriesId, b: KomgaSeriesId) {
        remove(seriesApi, on = a, target = b)
        remove(seriesApi, on = b, target = a)
    }

    private suspend fun write(
        seriesApi: KomgaSeriesApi,
        on: KomgaSeriesId,
        target: KomgaSeriesId,
        type: SeriesRelationType,
    ) {
        val series = seriesApi.getOneSeries(on)
        // Drop any earlier Kora link to the same target: re-linking with a
        // different type must replace it, not stack a second one.
        val kept = series.metadata.links.filterNot { KoraLinkCodec.parse(it)?.target == target }
        seriesApi.update(on, linksUpdate(series, kept + KoraLinkCodec.relationLink(target, type)))
    }

    private suspend fun remove(seriesApi: KomgaSeriesApi, on: KomgaSeriesId, target: KomgaSeriesId) {
        val series = seriesApi.getOneSeries(on)
        val newLinks = series.metadata.links.filterNot { KoraLinkCodec.parse(it)?.target == target }
        // Nothing to remove is not an error, but it must not cost a write either.
        if (newLinks.size != series.metadata.links.size) {
            seriesApi.update(on, linksUpdate(series, newLinks))
        }
    }

    /** Update request that touches ONLY links + linksLock, leaving all else unchanged. */
    private fun linksUpdate(series: KomgaSeries, newLinks: List<KomgaWebLink>) =
        KomgaSeriesMetadataUpdateRequest(
            links = patchLists(series.metadata.links, newLinks),
            linksLock = patch(series.metadata.linksLock, true),
        )
}
