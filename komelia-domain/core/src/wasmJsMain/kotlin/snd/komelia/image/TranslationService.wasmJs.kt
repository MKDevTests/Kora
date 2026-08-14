package snd.komelia.image

import snd.komelia.settings.model.TranslationLanguage

actual class TranslationService {
    actual suspend fun isReady(source: TranslationLanguage, target: TranslationLanguage) = false

    actual suspend fun downloadModels(
        source: TranslationLanguage,
        target: TranslationLanguage,
        requireWifi: Boolean,
    ) = Unit

    actual suspend fun translate(
        texts: List<String>,
        source: TranslationLanguage,
        target: TranslationLanguage,
    ): List<String> = texts

    actual fun release() = Unit
}
