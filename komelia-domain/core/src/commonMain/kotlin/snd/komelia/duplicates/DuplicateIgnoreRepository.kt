package snd.komelia.duplicates

/**
 * Pairs the admin has ruled out.
 *
 * Local and per server, like the similarity index it filters. Nothing here
 * reaches Komga: "these two are different works" is a judgement about this
 * catalogue, and the server has no field to record it in.
 */
interface DuplicateIgnoreRepository {

    /** [duplicatePairKey] values, ready to hand to [findDuplicateGroups]. */
    suspend fun ignoredPairs(): Set<String>

    suspend fun ignore(pairKey: String)

    /** Undo — dismissing is one tap and the list is otherwise unreachable. */
    suspend fun unignore(pairKey: String)

    suspend fun clear()
}
