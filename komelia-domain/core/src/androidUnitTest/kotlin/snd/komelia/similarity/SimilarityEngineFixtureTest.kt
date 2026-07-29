package snd.komelia.similarity

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
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
            val actual = engine.similarTo(queryId, limit = limit)
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
            }
        }
    }

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
}
