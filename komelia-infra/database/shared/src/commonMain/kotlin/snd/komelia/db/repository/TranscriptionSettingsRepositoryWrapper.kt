package snd.komelia.db.repository

import kotlinx.coroutines.flow.Flow
import snd.komelia.db.SettingsStateWrapper
import snd.komelia.db.TranscriptionSettings
import snd.komelia.settings.TranscriptionSettingsRepository
import snd.komelia.settings.model.TranscriptionSettings as DomainTranscriptionSettings

class TranscriptionSettingsRepositoryWrapper(
    val wrapper: SettingsStateWrapper<TranscriptionSettings>
) : TranscriptionSettingsRepository {

    override fun getSettings(): Flow<DomainTranscriptionSettings> {
        return wrapper.mapState { it.toDomain() }
    }

    override suspend fun putSettings(settings: DomainTranscriptionSettings) {
        wrapper.transform { settings.toDb() }
    }

    private fun TranscriptionSettings.toDomain() = DomainTranscriptionSettings(
        engine = engine,
        whisperLanguage = whisperLanguage,
    )

    private fun DomainTranscriptionSettings.toDb() = TranscriptionSettings(
        engine = engine,
        whisperLanguage = whisperLanguage,
    )
}
