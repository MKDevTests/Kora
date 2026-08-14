package snd.komelia.image

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.hzkitty.RapidOCR
import io.github.hzkitty.entity.OcrConfig
import io.github.hzkitty.entity.ParamConfig
import kotlinx.coroutines.tasks.await
import snd.komelia.image.AndroidBitmap.toBitmap
import androidx.compose.ui.geometry.Rect
import io.github.oshai.kotlinlogging.KotlinLogging
import snd.komelia.settings.model.OcrEngine
import snd.komelia.settings.model.OcrLanguage
import snd.komelia.settings.model.OcrSettings
import snd.komelia.settings.model.RapidOcrModel
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

private val logger = KotlinLogging.logger { }

/** Shares the reader-translation logger so one grep covers the whole path. */
private val ocrLogger = KotlinLogging.logger("KoraTranslate")

actual class OcrService {
    private val latinRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val chineseRecognizer by lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }
    private val devanagariRecognizer by lazy { TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()) }
    private val japaneseRecognizer by lazy { TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()) }
    private val koreanRecognizer by lazy { TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()) }

    /** Keyed by model *and* orientation classification: the two are baked into
     *  the engine's config, so Japanese and Latin need their own instance. */
    private val rapidOcrEngines = mutableMapOf<Pair<RapidOcrModel, Boolean>, RapidOCR>()
    /**
     * Per-character boxes were only there to guess word boundaries from the
     * gaps between letters. The recogniser turned out to emit spaces itself
     * (rapidocr4j appends " " to the character list), and guessing on top of
     * that split words in two — 'BUSY.' alone on its line came out 'BU SY'.
     * Computing them is pure cost now.
     */
    private val rapidOcrParams by lazy {
        ParamConfig().apply { setReturnWordBox(false) }
    }

    actual suspend fun recognizeText(image: ReaderImage, settings: OcrSettings): List<OcrElementBox> {
        // Measured apart from recognition itself: a slow scan can be the full-size
        // decode rather than the OCR engine, and the two need different fixes.
        val bitmap = snd.komelia.perf.PerfTrace.measure("reader.ocr.bitmap") {
            val komeliaImage = image.getOriginalImage().getOrNull() ?: return@measure null
            when (komeliaImage) {
                is AndroidBitmapBackedImage -> komeliaImage.bitmap
                else -> komeliaImage.toBitmap()
            }
        } ?: return emptyList()

        // The blocks OCR returns are in the coordinate space of THIS bitmap, while
        // the overlay maps them through getOriginalImageSize(). If the two differ,
        // recognition is running on a downscaled page — which is also the first
        // thing to check when whole lines of text come back missing.
        val declaredSize = image.getOriginalImageSize().getOrNull()
        ocrLogger.info {
            "ocr input ${bitmap.width}x${bitmap.height}, " +
                    "overlay space ${declaredSize?.width}x${declaredSize?.height}, " +
                    "mlkit upscale x${mlKitUpscaleFactor(bitmap.width)}"
        }

        return when (settings.engine) {
            OcrEngine.ML_KIT -> {
                // ML Kit misses whole bubbles on comic pages: measured on a
                // 1400px-wide page whose capitals are 13-25px tall, it returned
                // 'SOME' for "SOMETHING THE MATTER?" and nothing at all for a
                // neighbouring bubble, while picking 8x7px fragments out of the
                // artwork. Both are what an engine does when the glyphs are near
                // its lower size bound, so it gets a bigger page to read.
                val scale = mlKitUpscaleFactor(bitmap.width)
                if (scale <= 1f) recognizeWithMlKit(bitmap, settings.selectedLanguage)
                else {
                    // createScaledBitmap has to read pixels, which a HARDWARE
                    // bitmap does not allow — same copy RapidOCR already needs.
                    val readable = if (bitmap.config == Bitmap.Config.HARDWARE) {
                        bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    } else bitmap
                    val enlarged = Bitmap.createScaledBitmap(
                        readable,
                        (readable.width * scale).toInt(),
                        (readable.height * scale).toInt(),
                        true,
                    )
                    if (readable !== bitmap) readable.recycle()
                    try {
                        // Boxes come back in the enlarged space; the overlay works
                        // in the page's own, so they are scaled back down here.
                        recognizeWithMlKit(enlarged, settings.selectedLanguage)
                            .map { it.scaledBy(1f / scale) }
                    } finally {
                        // Only the copy: the source bitmap belongs to the reader image.
                        enlarged.recycle()
                    }
                }
            }

            OcrEngine.RAPID_OCR -> {
                val softwareBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else bitmap

                // Japanese comic lettering runs in vertical columns. The library
                // rotates a tall crop before recognising it, which leaves it
                // upside down half the time — that is exactly what the
                // orientation classifier is for. Latin pages are upright and
                // do not pay for it.
                val vertical = settings.selectedLanguage == OcrLanguage.JAPANESE
                val engine = getRapidOcrEngine(settings.rapidOcrModel, vertical)
                if (engine == null) emptyList()
                else recognizeWithRapidOcr(engine, softwareBitmap)
            }
        }
    }

    /**
     * How much to enlarge a page before handing it to ML Kit. Targets
     * [ML_KIT_TARGET_WIDTH] and never goes past 2x — beyond that the memory and
     * the time cost more than the extra glyphs are worth.
     */
    private fun mlKitUpscaleFactor(width: Int): Float {
        if (width <= 0 || width >= ML_KIT_TARGET_WIDTH) return 1f
        return (ML_KIT_TARGET_WIDTH.toFloat() / width).coerceAtMost(2f)
    }

    private fun OcrElementBox.scaledBy(factor: Float) = copy(
        imageRect = imageRect.scaledBy(factor),
        blockRect = blockRect.scaledBy(factor),
    )

    private fun Rect.scaledBy(factor: Float) = Rect(
        left = left * factor,
        top = top * factor,
        right = right * factor,
        bottom = bottom * factor,
    )

    private fun getRapidOcrEngine(model: RapidOcrModel, useCls: Boolean): RapidOCR? {
        val key = model to useCls
        val existing = rapidOcrEngines[key]
        if (existing != null) return existing

        val modelsDir = context.filesDir.resolve("rapidocr_models").toPath()
        if (!modelsDir.exists()) {
            logger.warn { "RapidOCR models directory does not exist" }
            return null
        }

        val isV6 = model == RapidOcrModel.PP_OCR_V6_SMALL
        // v6 ships its own detector, and its recogniser is trained against an
        // 18708-entry dictionary — pairing it with the v4 detector or letting it
        // fall back to the v4 keys produces confident nonsense, not an error.
        val detModel = modelsDir.resolve(
            if (isV6) "PP-OCRv6_small_det_infer.onnx" else "ch_PP-OCRv4_det_infer.onnx"
        )
        val clsModel = modelsDir.resolve("ch_ppocr_mobile_v2.0_cls_infer.onnx")
        val recModel = modelsDir.resolve(model.recModelName())
        val keysFile = modelsDir.resolve("ppocrv6_keys.txt")

        if (!detModel.exists() || !clsModel.exists() || !recModel.exists()) {
            logger.warn { "Some RapidOCR models are missing: det=${detModel.exists()}, cls=${clsModel.exists()}, rec=${recModel.exists()}" }
            return null
        }
        if (isV6 && !keysFile.exists()) {
            logger.warn { "PP-OCRv6 selected but ppocrv6_keys.txt is missing — download the v6 model bundle" }
            return null
        }

        // Half the cores, same rule as cover loading: the reader still has a page
        // to draw while this runs. Left at the default until now, which is one
        // reason a scan took 4-5 seconds.
        val threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)
        val config = OcrConfig().apply {
            det.modelPath = detModel.absolutePathString()
            det.intraOpNumThreads = threads
            cls.modelPath = clsModel.absolutePathString()
            cls.intraOpNumThreads = threads
            rec.modelPath = recModel.absolutePathString()
            rec.intraOpNumThreads = threads
            // Recognition runs once per detected box — 42 to 56 on a comic page.
            // Batching them is the difference between 50 model calls and 7.
            rec.recBatchNum = REC_BATCH
            if (isV6) rec.recKeysPath = keysFile.absolutePathString()
            // Orientation classification decides whether a crop is upside down.
            // A scanned Latin page is the right way up and does not need it, at
            // one model call per box; a rotated vertical Japanese column does.
            global.useCls = useCls
            // Drops the hallucinated reads over artwork and vertical Japanese
            // that made whole panels unusable ('HP L f n 7J iQ 75#+').
            global.textScore = 0.6f
            global.intraOpNumThreads = threads
        }
        logger.info {
            "RapidOCR engine for $model, $threads threads, " +
                    "keys=${if (isV6) "v6" else "built-in"}, cls=$useCls"
        }

        return try {
            val engine = RapidOCR.create(context, config)
            rapidOcrEngines[key] = engine
            engine
        } catch (e: Exception) {
            logger.error(e) { "Failed to create RapidOCR engine for model $model" }
            null
        }
    }

    private fun RapidOcrModel.recModelName() = when (this) {
        RapidOcrModel.PP_OCR_V6_SMALL -> "PP-OCRv6_small_rec_infer.onnx"
        RapidOcrModel.ENGLISH_CHINESE -> "ch_PP-OCRv4_rec_infer.onnx"
        RapidOcrModel.ENGLISH_ONLY -> "en_PP-OCRv4_rec_infer.onnx"
        RapidOcrModel.LATIN_MULTILINGUAL -> "latin_PP-OCRv3_rec_infer.onnx"
        RapidOcrModel.JAPANESE -> "japan_PP-OCRv4_rec_infer.onnx"
        RapidOcrModel.KOREAN -> "korean_PP-OCRv4_rec_infer.onnx"
        RapidOcrModel.ARABIC -> "arabic_PP-OCRv4_rec_infer.onnx"
        RapidOcrModel.HEBREW -> "he_PP-OCRv3_rec_infer.onnx"
    }

    private suspend fun recognizeWithMlKit(bitmap: android.graphics.Bitmap, language: OcrLanguage): List<OcrElementBox> {
        val recognizer = when (language) {
            OcrLanguage.LATIN -> latinRecognizer
            OcrLanguage.CHINESE -> chineseRecognizer
            OcrLanguage.DEVANAGARI -> devanagariRecognizer
            OcrLanguage.JAPANESE -> japaneseRecognizer
            OcrLanguage.KOREAN -> koreanRecognizer
        }

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(inputImage).await()

        val boxes = mutableListOf<OcrElementBox>()
        result.textBlocks.forEachIndexed { blockIdx, block ->
            val blockBoundingBox = block.boundingBox ?: return@forEachIndexed
            val blockRect = Rect(
                left = blockBoundingBox.left.toFloat(),
                top = blockBoundingBox.top.toFloat(),
                right = blockBoundingBox.right.toFloat(),
                bottom = blockBoundingBox.bottom.toFloat()
            )
            block.lines.forEachIndexed { lineIdx, line ->
                line.elements.forEachIndexed { elementIdx, element ->
                    val rect = element.boundingBox ?: return@forEachIndexed
                    boxes.add(
                        OcrElementBox(
                            text = element.text,
                            imageRect = Rect(
                                left = rect.left.toFloat(),
                                top = rect.top.toFloat(),
                                right = rect.right.toFloat(),
                                bottom = rect.bottom.toFloat()
                            ),
                            blockRect = blockRect,
                            blockIndex = blockIdx,
                            lineIndex = lineIdx,
                            elementIndex = elementIdx
                        )
                    )
                }
            }
        }
        return boxes
    }

    private fun recognizeWithRapidOcr(engine: RapidOCR, bitmap: android.graphics.Bitmap): List<OcrElementBox> {
        val result = engine.run(bitmap, rapidOcrParams)

        // Detection runs on the whole page, recognition on small crops. Which of
        // the two dominates decides whether a lighter detector (PP-OCRv6 tiny,
        // 0.43M parameters against small's) would buy anything: swapping it in
        // is one path change, but only worth the recall it costs if detection is
        // actually a large share of the 3.9 s a page takes.
        ocrLogger.info {
            "rapidocr split det=${result.detTime} cls=${result.clsTime} " +
                    "rec=${result.recTime} total=${result.elapseTime} (library units)"
        }

        val boxes = mutableListOf<OcrElementBox>()

        result.recRes.forEachIndexed { index, recResult ->
            val points = recResult.dtBoxes
            if (points == null || points.size < 4) return@forEachIndexed

            val xCoords = points.map { it.x }
            val yCoords = points.map { it.y }
            val rect = Rect(
                left = xCoords.min().toFloat(),
                top = yCoords.min().toFloat(),
                right = xCoords.max().toFloat(),
                bottom = yCoords.max().toFloat()
            )

            boxes.add(
                OcrElementBox(
                    text = recResult.text,
                    imageRect = rect,
                    blockRect = rect,
                    blockIndex = index,
                    lineIndex = 0,
                    elementIndex = 0
                )
            )
        }
        return boxes
    }

    companion object {
        lateinit var context: Context

        /** Width ML Kit is fed, when the page is smaller. */
        private const val ML_KIT_TARGET_WIDTH = 2800

        /** Text crops handed to the recogniser per call. */
        private const val REC_BATCH = 8
    }
}
