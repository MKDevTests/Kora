package snd.komelia.db.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import snd.komelia.db.EpubReaderSettings
import snd.komelia.db.SettingsStateWrapper
import snd.komelia.settings.EpubReaderSettingsRepository
import snd.komelia.settings.model.Epub3NativeSettings
import snd.komelia.settings.model.EpubReaderType
import snd.komelia.settings.model.TtsuReaderSettings

class EpubReaderSettingsRepositoryWrapper(
    val wrapper: SettingsStateWrapper<EpubReaderSettings>
) : EpubReaderSettingsRepository {

    override fun getReaderType(): Flow<EpubReaderType> {
        return wrapper.mapState { it.readerType }
    }

    override suspend fun putReaderType(type: EpubReaderType) {
        wrapper.transform { it.copy(readerType = type) }
    }

    override suspend fun getKomgaReaderSettings(): JsonObject {
        return wrapper.state.value.komgaReaderSettings
    }

    override suspend fun putKomgaReaderSettings(settings: JsonObject) {
        return wrapper.transform { it.copy(komgaReaderSettings = settings) }
    }

    override suspend fun getTtsuReaderSettings(): TtsuReaderSettings {
        return wrapper.state.value.ttsuReaderSettings
    }

    override suspend fun putTtsuReaderSettings(settings: TtsuReaderSettings) {
        return wrapper.transform { it.copy(ttsuReaderSettings = settings) }
    }

    override suspend fun getEpub3NativeSettings(): Epub3NativeSettings {
        return wrapper.state.value.epub3NativeSettings
    }

    override suspend fun putEpub3NativeSettings(settings: Epub3NativeSettings) {
        return wrapper.transform { it.copy(epub3NativeSettings = settings) }
    }

    override fun getEpubCacheSizeLimitMb(): Flow<Long> {
        return wrapper.mapState { it.epubCacheSizeLimitMb }
    }

    override suspend fun putEpubCacheSizeLimitMb(size: Long) {
        wrapper.transform { it.copy(epubCacheSizeLimitMb = size) }
    }
}