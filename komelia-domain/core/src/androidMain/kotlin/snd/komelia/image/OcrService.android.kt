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
import org.opencv.core.Point as OcvPoint
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

actual class OcrService {
    private val latinRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val chineseRecognizer by lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }
    private val devanagariRecognizer by lazy { TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()) }
    private val japaneseRecognizer by lazy { TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()) }
    private val koreanRecognizer by lazy { TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()) }

    private val rapidOcrEngines = mutableMapOf<RapidOcrModel, RapidOCR>()
    private val rapidOcrParams by lazy {
        ParamConfig().apply { setReturnWordBox(true) }
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

        return when (settings.engine) {
            OcrEngine.ML_KIT -> recognizeWithMlKit(bitmap, settings.selectedLanguage)
            OcrEngine.RAPID_OCR -> {
                val softwareBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else bitmap

                val engine = getRapidOcrEngine(settings.rapidOcrModel)
                if (engine == null) emptyList()
                else recognizeWithRapidOcr(engine, softwareBitmap)
            }
        }
    }

    private fun getRapidOcrEngine(model: RapidOcrModel): RapidOCR? {
        val existing = rapidOcrEngines[model]
        if (existing != null) return existing

        val modelsDir = context.filesDir.resolve("rapidocr_models").toPath()
        if (!modelsDir.exists()) {
            logger.warn { "RapidOCR models directory does not exist" }
            return null
        }

        val detModel = modelsDir.resolve("ch_PP-OCRv4_det_infer.onnx")
        val clsModel = modelsDir.resolve("ch_ppocr_mobile_v2.0_cls_infer.onnx")
        val recModel = modelsDir.resolve(model.recModelName())

        if (!detModel.exists() || !clsModel.exists() || !recModel.exists()) {
            logger.warn { "Some RapidOCR models are missing: det=${detModel.exists()}, cls=${clsModel.exists()}, rec=${recModel.exists()}" }
            return null
        }

        val config = OcrConfig().apply {
            det.modelPath = detModel.absolutePathString()
            cls.modelPath = clsModel.absolutePathString()
            rec.modelPath = recModel.absolutePathString()
        }

        return try {
            val engine = RapidOCR.create(context, config)
            rapidOcrEngines[model] = engine
            engine
        } catch (e: Exception) {
            logger.error(e) { "Failed to create RapidOCR engine for model $model" }
            null
        }
    }

    private fun RapidOcrModel.recModelName() = when (this) {
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

            val charBoxes = recResult.wordBoxResult?.sortedWordBoxList
            val text = if (charBoxes != null && charBoxes.size == recResult.text.length) {
                insertSpacesByGap(recResult.text, charBoxes)
            } else {
                recResult.text
            }

            boxes.add(
                OcrElementBox(
                    text = text,
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

    // Inserts spaces between characters whose bounding boxes have a gap larger than
    // half the preceding character's width — recovers word boundaries lost by PaddleOCR's CTC decoder.
    private fun insertSpacesByGap(text: String, charBoxes: List<Array<OcvPoint>>): String {
        val sb = StringBuilder()
        for (i in text.indices) {
            sb.append(text[i])
            if (i < text.length - 1) {
                val right = charBoxes[i].maxOf { it.x }
                val charWidth = right - charBoxes[i].minOf { it.x }
                val nextLeft = charBoxes[i + 1].minOf { it.x }
                if (nextLeft - right > charWidth * 0.5) sb.append(' ')
            }
        }
        return sb.toString()
    }

    companion object {
        lateinit var context: Context
    }
}
