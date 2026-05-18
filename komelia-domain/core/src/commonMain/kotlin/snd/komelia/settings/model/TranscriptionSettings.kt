package snd.komelia.settings.model

import kotlinx.serialization.Serializable

@Serializable
enum class TranscriptionEngineType { ML_KIT, WHISPER }

data class TranscriptionSettings(
    val engine: TranscriptionEngineType = TranscriptionEngineType.ML_KIT,
    val whisperLanguage: String? = null,
)
