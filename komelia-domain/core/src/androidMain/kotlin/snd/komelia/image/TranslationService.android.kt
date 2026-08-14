package snd.komelia.image

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import snd.komelia.settings.model.TranslationLanguage

private val logger = KotlinLogging.logger { }

actual class TranslationService {
    private val mutex = Mutex()
    private var loaded: LoadedPair? = null

    private class LoadedPair(
        val source: TranslationLanguage,
        val target: TranslationLanguage,
        val translator: Translator,
    )

    actual suspend fun isReady(source: TranslationLanguage, target: TranslationLanguage): Boolean {
        val manager = RemoteModelManager.getInstance()
        return requiredLanguages(source, target).all { language ->
            val tag = TranslateLanguage.fromLanguageTag(language.code) ?: return false
            manager.isModelDownloaded(TranslateRemoteModel.Builder(tag).build()).await()
        }
    }

    actual suspend fun downloadModels(
        source: TranslationLanguage,
        target: TranslationLanguage,
        requireWifi: Boolean,
    ) {
        val conditions = DownloadConditions.Builder()
            .apply { if (requireWifi) requireWifi() }
            .build()
        // downloadModelIfNeeded pulls whatever the pair needs, pivot included.
        mutex.withLock { translatorFor(source, target) }
            .downloadModelIfNeeded(conditions)
            .await()
    }

    actual suspend fun translate(
        texts: List<String>,
        source: TranslationLanguage,
        target: TranslationLanguage,
    ): List<String> {
        if (texts.isEmpty()) return texts
        val translator = mutex.withLock { translatorFor(source, target) }
        return texts.map { text ->
            if (text.isBlank()) text
            else runCatching { translator.translate(text).await() }
                .onFailure { logger.warn(it) { "translation failed for a block, keeping the original" } }
                .getOrDefault(text)
        }
    }

    actual fun release() {
        loaded?.translator?.close()
        loaded = null
    }

    /**
     * One live translator at a time — each one holds its models in memory (tens
     * of megabytes), and the reader only ever uses the selected pair.
     */
    private fun translatorFor(source: TranslationLanguage, target: TranslationLanguage): Translator {
        val existing = loaded
        if (existing != null && existing.source == source && existing.target == target) {
            return existing.translator
        }
        existing?.translator?.close()

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(requireNotNull(TranslateLanguage.fromLanguageTag(source.code)) {
                "unsupported source language ${source.code}"
            })
            .setTargetLanguage(requireNotNull(TranslateLanguage.fromLanguageTag(target.code)) {
                "unsupported target language ${target.code}"
            })
            .build()
        val translator = Translation.getClient(options)
        loaded = LoadedPair(source, target, translator)
        return translator
    }

    /**
     * ML Kit's models all translate to and from English, so a pair that does not
     * involve English needs the English model as a pivot as well. Leaving it out
     * is how "the models are downloaded" ends up not being true at translate time.
     */
    private fun requiredLanguages(
        source: TranslationLanguage,
        target: TranslationLanguage,
    ): List<TranslationLanguage> {
        val pair = listOf(source, target)
        return if (TranslationLanguage.ENGLISH in pair) pair
        else pair + TranslationLanguage.ENGLISH
    }
}
