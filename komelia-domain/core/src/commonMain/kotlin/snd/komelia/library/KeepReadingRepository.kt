package snd.komelia.library

import snd.komelia.komga.api.model.KomeliaBook

/**
 * Last known "Keep reading" row, per library.
 *
 * The request behind that row costs one to three seconds against a real
 * server, and until it answers the row is absent — a hole at the top of the
 * screen, exactly where the user was heading. Remembering the last answer lets
 * it be drawn immediately and replaced when the server responds.
 *
 * Staleness is the accepted price: a book finished on another device can show
 * for the couple of seconds the refresh takes. An empty row for three seconds
 * is worse.
 */
interface KeepReadingRepository {
    suspend fun get(libraryId: String): List<KomeliaBook>
    suspend fun put(libraryId: String, books: List<KomeliaBook>)
}
