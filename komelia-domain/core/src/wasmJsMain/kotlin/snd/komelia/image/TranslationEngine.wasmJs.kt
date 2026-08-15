package snd.komelia.image

/** No on-device engine in the browser; page translation is Android-only for now. */
actual fun defaultTranslationEngine(): TranslationEngine = NoopTranslationEngine
