package snd.komelia.similarity

import kotlin.math.sqrt

/** One suggestion: a series id, its score, and why it was picked. */
data class SimilarSeries(
    val seriesId: String,
    val score: Double,
    /** Shared terms, strongest first — shown to the user as the reason. */
    val reasons: List<Feature>,
)

/**
 * Scores one series against a library, entirely locally.
 *
 * Built once per library from the persisted term index, then reused. Two things
 * make it cheap enough to run on every series screen:
 *
 *  - an INVERTED index (term -> series), so scoring only ever touches series
 *    sharing at least one term — a few hundred out of several thousand, not the
 *    whole library;
 *  - vectors that are just weights, so a score is a dot product over the
 *    intersection.
 *
 * Similarity is cosine over rarity-weighted term vectors. Cosine normalises
 * length, so a series carrying twelve tags doesn't mechanically outrank one
 * carrying four — a bias that otherwise shows up immediately on real data.
 */
class SimilarityEngine(
    series: List<IndexedSeries>,
    private val weights: SimilarityWeights = SimilarityWeights.Default,
) {
    private val termsBySeries: Map<String, List<Feature>> =
        series.associate { it.seriesId to it.terms.features() }

    /** term key -> series carrying it. The reason scoring stays sub-linear. */
    private val seriesByTerm: Map<String, List<String>> = buildMap<String, MutableList<String>> {
        termsBySeries.forEach { (id, features) ->
            features.forEach { getOrPut(it.key) { mutableListOf() }.add(id) }
        }
    }

    private val total = termsBySeries.size

    /** Rarity-adjusted weight of a term, cached: it is read once per posting. */
    private val termWeight: Map<String, Double> = buildMap {
        termsBySeries.values.forEach { features ->
            features.forEach { feature ->
                if (!containsKey(feature.key)) {
                    val df = seriesByTerm[feature.key]?.size ?: 0
                    put(feature.key, weights.familyWeight(feature) * idf(total, df))
                }
            }
        }
    }

    /** Vector length per series, so cosine doesn't recompute it per comparison. */
    private val norms: Map<String, Double> = termsBySeries.mapValues { (_, features) ->
        sqrt(features.sumOf { val w = termWeight[it.key] ?: 0.0; w * w })
    }

    val indexedCount: Int get() = total

    /**
     * Series most similar to [seriesId], best first.
     *
     * [exclude] drops ids outright (the source series, hidden, ignored). Already
     * read series are NOT excluded here on purpose: the caller badges them, so
     * the tab stays useful on a heavily-read library instead of coming up empty.
     */
    fun similarTo(
        seriesId: String,
        limit: Int = 20,
        exclude: Set<String> = emptySet(),
    ): List<SimilarSeries> {
        val sourceFeatures = termsBySeries[seriesId] ?: return emptyList()
        val sourceNorm = norms[seriesId] ?: 0.0
        if (sourceNorm <= 0.0) return emptyList()

        val scores = HashMap<String, Double>()
        val shared = HashMap<String, MutableList<Feature>>()

        for (feature in sourceFeatures) {
            val weight = termWeight[feature.key] ?: continue
            if (weight <= 0.0) continue
            val postings = seriesByTerm[feature.key] ?: continue
            // A term carried by nearly everything contributes almost nothing yet
            // costs a full walk of its postings; skip those outright.
            if (postings.size >= total) continue
            for (candidate in postings) {
                if (candidate == seriesId || candidate in exclude) continue
                scores[candidate] = (scores[candidate] ?: 0.0) + weight * weight
                shared.getOrPut(candidate) { mutableListOf() }.add(feature)
            }
        }

        val ranked = scores.asSequence()
            .mapNotNull { (candidate, dot) ->
                val norm = norms[candidate] ?: return@mapNotNull null
                if (norm <= 0.0) return@mapNotNull null
                val reasons = shared[candidate].orEmpty()
                    .sortedByDescending { termWeight[it.key] ?: 0.0 }
                SimilarSeries(candidate, dot / (sourceNorm * norm), reasons)
            }
            .sortedWith(compareByDescending<SimilarSeries> { it.score }.thenBy { it.seriesId })

        return capPerAuthor(ranked, limit)
    }

    /**
     * Series that fit a taste profile, best first — what the library's "For you"
     * tab shows.
     *
     * [affinities] maps a series id to how much the user liked it: positive for
     * liked, **negative for disliked**. The profile is the weighted sum of those
     * series' term vectors, so the signal ends up per TERM, not per genre: a
     * genre carried by many liked series survives a couple of bad ratings, while
     * a tag specific to the disliked ones goes negative and pushes its series
     * down. Penalising a whole genre because one series in it was rated 1 star
     * is exactly what this avoids.
     *
     * Each source series is divided by its own vector length before being added,
     * otherwise a series carrying 200 tags would drown fifty others.
     */
    fun recommend(
        affinities: Map<String, Double>,
        limit: Int = 20,
        exclude: Set<String> = emptySet(),
    ): List<SimilarSeries> {
        val profile = HashMap<String, Double>()
        for ((seriesId, affinity) in affinities) {
            if (affinity == 0.0) continue
            val features = termsBySeries[seriesId] ?: continue
            val norm = norms[seriesId] ?: continue
            if (norm <= 0.0) continue
            for (feature in features) {
                val weight = termWeight[feature.key] ?: continue
                if (weight <= 0.0) continue
                profile[feature.key] = (profile[feature.key] ?: 0.0) + affinity * weight / norm
            }
        }
        val profileNorm = sqrt(profile.values.sumOf { it * it })
        if (profileNorm <= 0.0) return emptyList()

        // Candidates come from the LIKED terms only — walking the postings of
        // every term would touch the whole library for nothing. A candidate is
        // then scored over its own full vector, so the disliked terms it carries
        // still count against it.
        val candidates = HashSet<String>()
        for ((key, weight) in profile) {
            if (weight <= 0.0) continue
            val postings = seriesByTerm[key] ?: continue
            if (postings.size >= total) continue
            for (candidate in postings) {
                if (candidate !in exclude) candidates += candidate
            }
        }

        val ranked = candidates.asSequence()
            .mapNotNull { candidate ->
                val features = termsBySeries[candidate] ?: return@mapNotNull null
                val norm = norms[candidate] ?: return@mapNotNull null
                if (norm <= 0.0) return@mapNotNull null
                val dot = features.sumOf { (profile[it.key] ?: 0.0) * (termWeight[it.key] ?: 0.0) }
                if (dot <= 0.0) return@mapNotNull null
                val reasons = features
                    .filter { (profile[it.key] ?: 0.0) > 0.0 }
                    .sortedByDescending { (profile[it.key] ?: 0.0) * (termWeight[it.key] ?: 0.0) }
                SimilarSeries(candidate, dot / (profileNorm * norm), reasons)
            }
            .sortedWith(compareByDescending<SimilarSeries> { it.score }.thenBy { it.seriesId })

        return capPerAuthor(ranked, limit)
    }

    /**
     * Keeps at most [SimilarityWeights.maxPerAuthor] entries per SHARED author,
     * so one prolific author can't fill the list — the case that makes this
     * feature redundant with the author filter.
     *
     * Two details the bench proved necessary on the real library:
     *
     *  - the cap counts only authors **shared with the source**, not every
     *    author of the candidate. Anthologies here credit up to 151 people;
     *    counting all of them would exhaust the cap on names that had nothing
     *    to do with the match.
     *  - a candidate is dropped as soon as **one** shared author is at the cap.
     *    Requiring all of them was equivalent to no cap at all: series credit 3
     *    to 5 authors, so there was always a fresh name and 44% of suggestions
     *    still shared an author with the source.
     */
    private fun capPerAuthor(ranked: Sequence<SimilarSeries>, limit: Int): List<SimilarSeries> {
        val perAuthor = HashMap<String, Int>()
        val kept = ArrayList<SimilarSeries>(limit)
        for (candidate in ranked) {
            val sharedAuthors = candidate.reasons
                .filter { it.family == TermFamily.AUTHOR }
                .map { it.value }
            if (sharedAuthors.any { (perAuthor[it] ?: 0) >= weights.maxPerAuthor }) continue
            sharedAuthors.forEach { perAuthor[it] = (perAuthor[it] ?: 0) + 1 }
            kept += candidate
            if (kept.size >= limit) break
        }
        return kept
    }
}
