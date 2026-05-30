package snd.komelia.db.migrations

import io.github.snd_r.komelia.db.sqlite.sqlite.generated.resources.Res
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class GlobalMigrations : MigrationResourcesProvider() {

    internal val migrations = listOf(
        "V1__initial_migration.sql",
    )

    override suspend fun getMigration(name: String): ByteArray? {
        return try {
            Res.readBytes("files/migrations/global/$name")
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            null
        }
    }

    override suspend fun getMigrations(): Map<String, ByteArray> {
        return migrations.associateWith { Res.readBytes("files/migrations/global/$it") }
    }
}
