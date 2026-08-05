package snd.komelia.similarity

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [SimilarityEngine] to the same numbers the Python bench produces.
 *
 * The weights of this feature are meant to be tuned in `scripts/similar-bench`,
 * because judging a suggestion takes seeing it on the real library and a build
 * cycle per guess is unaffordable. That only works if the bench scores exactly
 * like the app — an "almost the same" bench sends the tuning in the wrong
 * direction, silently, which is precisely how the panels bench once wasted a
 * whole investigation.
 *
 * So both sides score `scripts/similar-bench/fixture.json` and must agree.
 * After changing scoring on either side: run `python bench.py emit-expected`,
 * then this test.
 *
 * androidUnitTest, not jvmTest, for the same reason as MigrationRegistrationTest:
 * the JVM target of komelia-domain:offline does not compile. The Gradle test
 * working dir is the module directory, hence the `../..` hop.
 */
class SimilarityEngineFixtureTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val benchDir = File("../../scripts/similar-bench")

    private companion object {
        /** The "For you" row of the expectations, scored from a taste profile. */
        const val FOR_YOU_KEY = "_forYou"
    }

    @Test
    fun matchesTheBench() {
        val fixture = json.parseToJsonElement(read("fixture.json")).jsonObject
        val expected = json.parseToJsonElement(read("fixture_expected.json")).jsonObject

        val series = fixture.getValue("series").jsonArray.map { element ->
            val entry = element.jsonObject
            IndexedSeries(
                seriesId = entry.getValue("seriesId").jsonPrimitive.content,
                terms = json.decodeFromJsonElement<SeriesTerms>(entry.getValue("terms")),
            )
        }
        val limit = fixture["limit"]?.jsonPrimitive?.content?.toInt() ?: 5
        val engine = SimilarityEngine(series)

        assertTrue(expected.isNotEmpty(), "the fixture expectations are empty")
        for ((queryId, expectedResults) in expected) {
            val actual =
                if (queryId == FOR_YOU_KEY) engine.recommend(
                    affinities = tasteAffinities(fixture.evidence()),
                    limit = limit,
                    exclude = fixture.forYouExclusions(),
                )
                else engine.similarTo(queryId, limit = limit)
            val wanted = (expectedResults as JsonArray).map { it.jsonObject }

            assertEquals(
                wanted.map { it.getValue("seriesId").jsonPrimitive.content },
                actual.map { it.seriesId },
                "ranking for $queryId",
            )
            wanted.forEachIndexed { index, want ->
                val wantScore = want.getValue("score").jsonPrimitive.content.toDouble()
                val gotScore = actual[index].score
                assertTrue(
                    abs(wantScore - gotScore) < 1e-9,
                    "score for $queryId #${index + 1}: bench $wantScore, engine $gotScore",
                )
                assertEquals(
                    want.getValue("reasons").jsonArray.map { it.jsonPrimitive.content },
                    actual[index].reasons.map { "${it.family.prefix}:${it.value}" },
                    "reasons for $queryId #${index + 1}",
                )
                // Attribution drives the "Because you liked X" headings and the
                // per-section reasons; a divergence there means the bench no
                // longer explains what the app shows, even when the ranking
                // still matches.
                want["becauseOf"]?.let { becauseOf ->
                    val wantedSources = becauseOf.jsonArray.map { it.jsonObject }
                    val gotSources = actual[index].becauseOf
                    assertEquals(
                        wantedSources.map { it.getValue("seriesId").jsonPrimitive.content },
                        gotSources.map { it.seriesId },
                        "becauseOf for $queryId #${index + 1}",
                    )
                    wantedSources.forEachIndexed { sourceIndex, wantSource ->
                        val wantShare = wantSource.getValue("share").jsonPrimitive.content.toDouble()
                        assertTrue(
                            abs(wantShare - gotSources[sourceIndex].share) < 1e-9,
                            "share for $queryId #${index + 1} source ${sourceIndex + 1}",
                        )
                        assertEquals(
                            wantSource.getValue("reasons").jsonArray.map { it.jsonPrimitive.content },
                            gotSources[sourceIndex].reasons.map { "${it.family.prefix}:${it.value}" },
                            "shared reasons for $queryId #${index + 1} source ${sourceIndex + 1}",
                        )
                    }
                }
            }
        }
    }

    /** The taste profile the "For you" expectations were produced from. */
    private fun JsonObject.evidence(): List<SeriesEvidence> =
        getValue("forYou").jsonObject.getValue("evidence").jsonArray.map { element ->
            val row = element.jsonObject
            SeriesEvidence(
                seriesId = row.getValue("seriesId").jsonPrimitive.content,
                read = row["read"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                inProgress = row["inProgress"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                isFavorite = row["isFavorite"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                stars = row["stars"]?.jsonPrimitive?.content?.toInt(),
            )
        }

    private fun JsonObject.forYouExclusions(): Set<String> =
        getValue("forYou").jsonObject.getValue("exclude").jsonArray
            .map { it.jsonPrimitive.content }
            .toSet()

    private fun read(name: String): String {
        val file = File(benchDir, name)
        assertTrue(file.exists(), "missing ${file.absolutePath} — run `python bench.py emit-expected`")
        return file.readText()
    }

    /** Guards the JSON key contract the bench relies on (short @SerialName keys). */
    @Test
    fun termsKeysAreTheShortOnes() {
        val terms = json.decodeFromString<SeriesTerms>(
            """{"a":{"kishimoto":"writer"},"g":["action"],"t":["ninja"],"bt":["shonen"],"p":"kana"}"""
        )
        assertEquals(mapOf("kishimoto" to "writer"), terms.authors)
        assertEquals(setOf("action"), terms.genres)
        assertEquals(setOf("ninja"), terms.tags)
        assertEquals(setOf("shonen"), terms.bookTags)
        assertEquals("kana", terms.publisher)
    }

    /**
     * The two rules that decide whether "For you" is usable: an unrated series
     * is engagement, not a zero (most series here are never rated), and a rating
     * counts even when the series is unfinished.
     */
    @Test
    fun unratedIsNotAZeroAndRatingsCountUnfinished() {
        val affinities = tasteAffinities(
            listOf(
                SeriesEvidence("read-unrated", read = true),
                SeriesEvidence("started-rated", inProgress = true, stars = 5),
                SeriesEvidence("disliked", read = true, stars = 1),
                SeriesEvidence("untouched"),
            )
        )
        assertEquals(TasteWeights().read, affinities["read-unrated"])
        assertEquals(TasteWeights().stars.getValue(5), affinities["started-rated"])
        assertTrue((affinities.getValue("disliked")) < 0.0, "a 1-star series must push its terms down")
        assertFalse(affinities.containsKey("untouched"))
    }

    /**
     * Marker tags carry app state, not taste. `nextrelease:` is the dangerous
     * one: one tag per series means the highest rarity weight of all, so two
     * series sharing a release date would outrank two sharing an author.
     */
    @Test
    fun markerTagsAreNotScored() {
        assertTrue(isSimilarityMarkerTag("nextrelease:23-03.09.2026"))
        assertTrue(isSimilarityMarkerTag("kora:hidden"))
        assertTrue(isSimilarityMarkerTag("KORA:Hidden"), "the check is case-insensitive")
        assertFalse(isSimilarityMarkerTag("kora:genre:action"))
        assertFalse(isSimilarityMarkerTag("kora:tag:seinen"))
        assertFalse(isSimilarityMarkerTag("adapted to anime"))
    }
}
