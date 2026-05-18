package snd.komelia.db

import kotlinx.serialization.Serializable
import snd.komelia.settings.model.TranscriptionEngineType

@Serializable
data class TranscriptionSettings(
    val engine: TranscriptionEngineType = TranscriptionEngineType.ML_KIT,
    val whisperLanguage: String? = null,
)
