package snd.komelia.similarity

import kotlinx.coroutines.flow.Flow
import snd.komga.client.series.KomgaSeriesId

/**
 * Series the user said "not interested" about.
 *
 * Local and per server, like the ratings it complements. A dismissal is a
 * judgement on the SERIES, so it also feeds the taste profile as a negative
 * signal — hiding one cover without learning anything would make the user
 * repeat themselves on every near-identical series the same terms produce.
 */
interface SuggestionFeedbackRepository {

    /** Reactive so a dismissal removes the card without a reload. */
    fun observeDismissed(): Flow<Set<String>>

    suspend fun dismissed(): Set<String>

    suspend fun dismiss(seriesId: KomgaSeriesId)

    /** Undo — the action is one tap and mistakes are cheap to make. */
    suspend fun undismiss(seriesId: KomgaSeriesId)

    /** Wipes the whole list, the way out of a mis-tap nobody noticed. */
    suspend fun clear()
}
