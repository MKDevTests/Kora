package snd.komelia.similarity

import kotlin.math.pow
import kotlin.math.sqrt

/** One suggestion: a series id, its score, and why it was picked. */
data class SimilarSeries(
    val seriesId: String,
    val score: Double,
    /** Shared terms, strongest first — shown to the user as the reason. */
    val reasons: List<Feature>,
    /**
     * Only for [SimilarityEngine.recommend]: the liked series that contributed
     * most to this pick, strongest first. Lets "For you" say WHICH series it is
     * extrapolating from instead of listing bare tags.
     */
    val becauseOf: List<SourceAttribution> = emptyList(),
)

/** How much one liked series explains a suggestion, and through which terms. */
data class SourceAttribution(
    val seriesId: String,
    /** Share of the suggestion's positive score this source accounts for, 0..1. */
    val share: Double,
    /**
     * The terms actually shared with that source, strongest first — the only
     * honest answer to "why is this under Because you liked X". The reasons on
     * [SimilarSeries] answer a different question (the whole profile), and
     * showing those under a per-series heading is what makes a correct
     * suggestion look wrong.
     */
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

    /**
     * Vector length of an average series, used to floor the source lengths in
     * [recommend]. Computed once: it only depends on the index.
     */
    private val averageNorm: Double =
        norms.values.filter { it > 0.0 }.let { if (it.isEmpty()) 0.0 else it.sum() / it.size }

    /**
     * Below this, a profile series is treated as if it were of average length.
     *
     * A source's terms enter the profile divided by its own vector length, so a
     * series carrying ONE term puts its entire affinity on that term — the most
     * any term can get. On the real library that is not a corner case: 5% of the
     * manga series carry a single term, and one of them (a series whose only
     * indexed term was its publisher) made "Publisher: Kurokawa" the headline
     * reason of nine suggestions out of ten, pulling horror and comedy in under
     * a historical drama. A series that says almost nothing about itself must
     * not speak louder than the library average.
     */
    private val minSourceNorm = averageNorm * weights.minSourceNormRatio

    /** Own length, floored: see [minSourceNorm]. Zero when the series has no terms. */
    private fun sourceNorm(seriesId: String): Double {
        val norm = norms[seriesId] ?: return 0.0
        return if (norm <= 0.0) 0.0 else maxOf(norm, minSourceNorm)
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
            val norm = sourceNorm(seriesId)
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

        // Attribution runs on the kept list only: it costs a walk of the whole
        // profile per suggestion, which is nothing for twenty of them and a
        // second walk of the library for all candidates.
        return capPerAuthor(ranked, limit).map { it.copy(becauseOf = attribute(it.seriesId, affinities)) }
    }

    /**
     * Which liked series pulled [candidateId] in, strongest first, with the
     * share of the score each accounts for and the terms it shares.
     *
     * The score of a candidate is a sum over the profile, and the profile is a
     * sum over the liked series — so a candidate's score splits back per source
     * exactly, and the shares are that split. Constant denominators are dropped:
     * they cancel out in the ratio.
     */
    private fun attribute(candidateId: String, affinities: Map<String, Double>): List<SourceAttribution> {
        val candidateFeatures = termsBySeries[candidateId] ?: return emptyList()
        val byKey = candidateFeatures.associateBy { it.key }
        val scored = ArrayList<Pair<SourceAttribution, Double>>()
        var total = 0.0
        for ((sourceId, affinity) in affinities) {
            if (affinity <= 0.0 || sourceId == candidateId) continue
            val features = termsBySeries[sourceId] ?: continue
            // A series with two tags and no author explains nothing; saying a
            // suggestion comes "because you liked" it is noise where a better
            // explained source was available.
            if (features.size < MIN_SOURCE_TERMS) continue
            val norm = sourceNorm(sourceId)
            if (norm <= 0.0) continue
            var overlap = 0.0
            val shared = ArrayList<Feature>()
            for (feature in features) {
                val own = byKey[feature.key] ?: continue
                val weight = termWeight[feature.key] ?: continue
                if (weight <= 0.0) continue
                overlap += weight * weight
                shared += own
            }
            // The imprint is not a taste. Sharing only a publisher is the one
            // overlap that must never be presented as a reason.
            if (overlap <= 0.0 || shared.all { it.family == TermFamily.PUBLISHER }) continue
            // Not the plain cosine denominator: dividing by the full length
            // makes a source need shared terms proportional to the SQUARE ROOT
            // of its own size to compete, so the richly tagged series — the ones
            // that actually say something about a taste — never get to explain
            // anything. Measured on the manga library with a 315-series profile:
            // a 73-term series rated 5 stars headed 1 card out of 40 at 1.0, and
            // 5 at 0.5, without any single series taking over the page.
            val contribution = affinity * overlap / norm.pow(weights.attributionNormExponent)
            total += contribution
            scored += SourceAttribution(
                seriesId = sourceId,
                share = 0.0,
                reasons = shared.sortedByDescending { termWeight[it.key] ?: 0.0 },
            ) to contribution
        }
        if (total <= 0.0) return emptyList()
        return scored
            .sortedWith(
                compareByDescending<Pair<SourceAttribution, Double>> { it.second }
                    .thenBy { it.first.seriesId }
            )
            .take(MAX_ATTRIBUTIONS)
            .map { (attribution, contribution) -> attribution.copy(share = contribution / total) }
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

    private companion object {
        /** Enough for the UI to group by the first and fall back to the next. */
        const val MAX_ATTRIBUTIONS = 3

        /** Below this a series is too thin to explain another one. */
        const val MIN_SOURCE_TERMS = 3
    }
}
