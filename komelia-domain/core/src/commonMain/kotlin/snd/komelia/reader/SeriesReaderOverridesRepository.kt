package snd.komelia.reader

import snd.komga.client.series.KomgaSeriesId

/**
 * Local-only persistence for per-series reader overrides. Currently holds
 * the user's preferred reading direction; designed to grow if other
 * per-series knobs are added later.
 *
 * Never synced to the Komga server: the server-side series metadata is
 * shared across all users of that Komga instance, so we mustn't propagate
 * one user's reader preference there.
 */
interface SeriesReaderOverridesRepository {
    suspend fun getReadingDirection(seriesId: KomgaSeriesId): String?
    suspend fun putReadingDirection(seriesId: KomgaSeriesId, direction: String)
    suspend fun delete(seriesId: KomgaSeriesId)

    /** Snapshot of every persisted override. Used by backup/restore. */
    suspend fun getAll(): Map<KomgaSeriesId, String>

    /** Drops every override. Used before a backup import. */
    suspend fun deleteAll()
}
