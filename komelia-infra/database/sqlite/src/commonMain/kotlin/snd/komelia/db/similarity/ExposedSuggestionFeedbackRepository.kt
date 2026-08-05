package snd.komelia.db.similarity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komelia.db.ExposedRepository
import snd.komelia.db.tables.SuggestionDismissedTable
import snd.komelia.similarity.SuggestionFeedbackRepository
import snd.komga.client.series.KomgaSeriesId
import kotlin.time.Clock

/**
 * SQLite-backed "not interested" list (V88).
 *
 * Mirrored in a StateFlow so a dismissal removes the card immediately: the
 * suggestion lists are computed, not queried, and re-running the engine for one
 * tap would be a second of work for a cover that should just fade out.
 */
class ExposedSuggestionFeedbackRepository(
    database: Database,
) : ExposedRepository(database), SuggestionFeedbackRepository {

    private val cached = MutableStateFlow<Set<String>>(emptySet())
    private var loaded = false

    override fun observeDismissed(): Flow<Set<String>> = cached.asStateFlow()

    override suspend fun dismissed(): Set<String> {
        if (!loaded) {
            cached.value = transaction {
                SuggestionDismissedTable.selectAll()
                    .mapTo(mutableSetOf()) { it[SuggestionDismissedTable.seriesId] }
            }
            loaded = true
        }
        return cached.value
    }

    override suspend fun dismiss(seriesId: KomgaSeriesId) {
        transaction {
            SuggestionDismissedTable.upsert {
                it[SuggestionDismissedTable.seriesId] = seriesId.value
                it[dismissedAt] = Clock.System.now().toEpochMilliseconds().toString()
            }
        }
        cached.value = dismissed() + seriesId.value
    }

    override suspend fun clear() {
        transaction { SuggestionDismissedTable.deleteAll() }
        cached.value = emptySet()
        loaded = true
    }

    override suspend fun undismiss(seriesId: KomgaSeriesId) {
        transaction {
            SuggestionDismissedTable.deleteWhere { SuggestionDismissedTable.seriesId eq seriesId.value }
        }
        cached.value = dismissed() - seriesId.value
    }
}
