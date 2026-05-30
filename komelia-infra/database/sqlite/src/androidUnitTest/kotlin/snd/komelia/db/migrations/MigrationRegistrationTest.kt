package snd.komelia.db.migrations

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Guards the hand-maintained migration lists in [AppMigrations] / [GlobalMigrations]
 * / [OfflineMigrations] against the recurring footgun: dropping a new `VNN__*.sql`
 * file in composeResources WITHOUT adding its name to the provider's `migrations`
 * list ships it as a dead resource and crashes at the user's first launch with
 * `SQLITE_ERROR: no such column`.
 *
 * This asserts, per provider, that the set of `.sql` files on disk exactly matches
 * the registered list (both directions). A forgotten registration — or a
 * registered-but-missing file — now fails
 * `./gradlew :komelia-infra:database:sqlite:testDebugUnitTest` instead of users' devices.
 *
 * Runs as an Android host unit test (androidUnitTest), NOT jvmTest, on purpose:
 * jvmTest pulls the desktop/JVM target of komelia-domain:offline which currently
 * does not compile (unsupported). The Android chain (the one we ship) compiles.
 * The Gradle test task working dir is the module directory, so the resource path
 * resolves relative to it.
 *
 * OfflineTasksMigrations is intentionally NOT covered here: it is unused dead code
 * (0 usages) pointing at a non-existent `files/migrations/tasks/` dir, left as-is
 * to avoid diverging from upstream.
 */
class MigrationRegistrationTest {

    private data class Provider(val dir: String, val registered: List<String>)

    private val providers = listOf(
        Provider("app", AppMigrations().migrations),
        Provider("global", GlobalMigrations().migrations),
        Provider("offline", OfflineMigrations().migrations),
    )

    private val migrationsRoot =
        File("src/commonMain/composeResources/files/migrations")

    @Test
    fun resourceMigrationsMatchRegisteredLists() {
        if (!migrationsRoot.isDirectory) {
            fail(
                "Migrations resource dir not found at '${migrationsRoot.absolutePath}'. " +
                    "Test working dir is '${File(".").absolutePath}' — expected the sqlite module dir.",
            )
        }

        for (p in providers) {
            val dir = File(migrationsRoot, p.dir)
            assertTrue(dir.isDirectory, "[${p.dir}] resource dir missing: ${dir.absolutePath}")

            val onDisk = (dir.listFiles { f -> f.isFile && f.name.endsWith(".sql") } ?: emptyArray())
                .map { it.name }
                .toSortedSet()
            val registered = p.registered

            assertEquals(
                registered.size, registered.toSet().size,
                "[${p.dir}] duplicate entries in the registered migrations list: " +
                    registered.groupingBy { it }.eachCount().filterValues { it > 1 }.keys,
            )

            val notRegistered = onDisk - registered.toSet() // the footgun: file present, not registered
            val missingFile = registered.toSet() - onDisk    // registered but no .sql on disk

            assertTrue(
                notRegistered.isEmpty(),
                "[${p.dir}] .sql migration(s) on disk but NOT registered in code " +
                    "(ships as dead resource -> 'no such column' at runtime): $notRegistered",
            )
            assertTrue(
                missingFile.isEmpty(),
                "[${p.dir}] migration(s) registered in code but missing their .sql file: $missingFile",
            )
        }
    }
}
