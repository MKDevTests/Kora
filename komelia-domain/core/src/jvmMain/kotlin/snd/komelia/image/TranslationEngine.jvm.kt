package snd.komelia.image

/** No on-device engine on desktop; page translation is Android-only for now. */
actual fun defaultTranslationEngine(): TranslationEngine = NoopTranslationEngine
