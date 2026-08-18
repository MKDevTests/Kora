package snd.komelia.image

import android.content.Context
import android.graphics.Bitmap
import io.github.hzkitty.RapidOCR
import io.github.hzkitty.entity.OcrConfig
import io.github.hzkitty.entity.ParamConfig
import snd.komelia.image.AndroidBitmap.toBitmap
import androidx.compose.ui.geometry.Rect
import io.github.oshai.kotlinlogging.KotlinLogging
import snd.komelia.settings.model.OcrLanguage
import snd.komelia.settings.model.OcrSpeedMode
import snd.komelia.settings.model.OcrSettings
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

private val logger = KotlinLogging.logger { }

/** Shares the reader-translation logger so one grep covers the whole path. */
private val ocrLogger = KotlinLogging.logger("KoraTranslate")

actual class OcrService {

    /** Keyed by orientation classification and speed mode: both are baked into
     *  the engine's config, so each combination needs its own instance. */
    private val rapidOcrEngines = mutableMapOf<Pair<Boolean, OcrSpeedMode>, RapidOCR>()
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
                    "overlay space ${declaredSize?.width}x${declaredSize?.height}"
        }

        val softwareBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else bitmap

        // Japanese comic lettering runs in vertical columns. The library rotates
        // a tall crop before recognising it, which leaves it upside down half
        // the time — that is exactly what the orientation classifier is for.
        // Latin pages are upright and do not pay for it.
        val vertical = settings.selectedLanguage == OcrLanguage.JAPANESE
        val engine = getRapidOcrEngine(vertical, settings.speedMode) ?: return emptyList()
        return recognizeWithRapidOcr(engine, softwareBitmap)
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

    private fun getRapidOcrEngine(useCls: Boolean, speedMode: OcrSpeedMode): RapidOCR? {
        val existing = rapidOcrEngines[useCls to speedMode]
        if (existing != null) return existing

        val modelsDir = context.filesDir.resolve("rapidocr_models").toPath()
        if (!modelsDir.exists()) {
            logger.warn { "RapidOCR models directory does not exist" }
            return null
        }

        // v6 throughout: its recogniser is trained against an 18708-entry
        // dictionary, so pairing it with a v4 detector or v4 keys produces
        // confident nonsense rather than an error.
        //
        // Fast mode only swaps the detector, and only if the tiny model is in
        // the downloaded bundle — an older bundle must not silently produce no
        // OCR at all.
        val tinyDet = modelsDir.resolve("PP-OCRv6_tiny_det_infer.onnx")
        val fast = speedMode == OcrSpeedMode.FAST && tinyDet.exists()
        if (speedMode == OcrSpeedMode.FAST && !fast) {
            logger.warn { "Fast OCR asked for but PP-OCRv6_tiny_det_infer.onnx is missing — using the small detector" }
        }
        val detModel = if (fast) tinyDet else modelsDir.resolve("PP-OCRv6_small_det_infer.onnx")
        val clsModel = modelsDir.resolve("ch_ppocr_mobile_v2.0_cls_infer.onnx")
        val recModel = modelsDir.resolve("PP-OCRv6_small_rec_infer.onnx")
        val keysFile = modelsDir.resolve("ppocrv6_keys.txt")

        if (!detModel.exists() || !clsModel.exists() || !recModel.exists() || !keysFile.exists()) {
            logger.warn {
                "Some RapidOCR v6 models are missing — download the v6 bundle. " +
                        "det=${detModel.exists()}, cls=${clsModel.exists()}, " +
                        "rec=${recModel.exists()}, keys=${keysFile.exists()}"
            }
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
            rec.recKeysPath = keysFile.absolutePathString()
            // Orientation classification decides whether a crop is upside down.
            // A scanned Latin page is the right way up and does not need it, at
            // one model call per box; a rotated vertical Japanese column does.
            global.useCls = useCls
            // Drops the hallucinated reads over artwork and vertical Japanese
            // that made whole panels unusable ('HP L f n 7J iQ 75#+').
            global.textScore = 0.6f
            global.intraOpNumThreads = threads
            // ONNX Runtime's CPU arena, off.
            //
            // Measured on the tablet, reading the same volume twice: with the
            // reader alone the native heap sits at 273MB and stays there. The
            // moment the OCR engine is built it jumps by a full gigabyte, and
            // leaving the reader gives none of it back -- 1.1GB went to swap
            // instead of being freed. That is what an arena does: it takes
            // memory from the system and keeps it, which is a good trade on a
            // desktop and the wrong one here, where the app was killed for
            // LOW_MEMORY while reading a comic.
            //
            // Nothing in the library can free it after the fact. RapidOCR has
            // no close(), OrtInferSession has none either, and it holds its
            // OrtSession in a private final field -- the sessions live as long
            // as the process. Refusing the arena up front is the only lever
            // that does not go through reflection.
            det.useArena = false
            cls.useArena = false
            rec.useArena = false
        }
        // On the reader-translation logger, not the general one: which detector
        // ran and whether the orientation classifier is on are the two things a
        // page of odd results is read against, and they have to be in the same
        // grep as the blocks themselves.
        ocrLogger.info {
            "RapidOCR PP-OCRv6 engine, det=${if (fast) "tiny" else "small"}, " +
                    "$threads threads, cls=$useCls"
        }

        return try {
            val engine = RapidOCR.create(context, config)
            rapidOcrEngines[useCls to speedMode] = engine
            engine
        } catch (e: Exception) {
            logger.error(e) { "Failed to create the RapidOCR engine" }
            null
        }
    }

    /**
     * Recognises a page, cutting a webtoon strip into bands first.
     *
     * A comic page is about 1600x2264 and goes in whole. A webtoon page in this
     * library is 720x8295 — eleven and a half times taller than it is wide. The
     * detector normalises an image by its long side, so the whole strip in one
     * call means the 720px width is squeezed to well under a hundred pixels and
     * the lettering stops existing; asking it to keep the long side instead
     * builds a detection map of tens of megapixels. Measured on the tablet, the
     * strip produced a scan that never came back and eventually took the app
     * with it.
     *
     * Bands are cut to roughly the aspect ratio of an ordinary page, so each one
     * is an input the detector is built for.
     */
    private fun recognizeWithRapidOcr(engine: RapidOCR, bitmap: android.graphics.Bitmap): List<OcrElementBox> {
        val bandHeight = (bitmap.width * MAX_SCAN_ASPECT).toInt()
        if (bitmap.height <= bandHeight) return recognizeWhole(engine, bitmap)

        val boxes = mutableListOf<OcrElementBox>()
        var top = 0
        var blockIndex = 0
        while (top < bitmap.height) {
            val height = minOf(bandHeight, bitmap.height - top)
            // Overlap, because a band edge lands wherever it lands and a line of
            // lettering cut in half is recognised as neither half. Anything that
            // straddles the seam is therefore seen whole by one of the two bands,
            // and the duplicate is dropped below.
            val band = android.graphics.Bitmap.createBitmap(bitmap, 0, top, bitmap.width, height)
            val offset = top.toFloat()
            recognizeWhole(engine, band).forEach { box ->
                boxes.add(
                    box.copy(
                        imageRect = box.imageRect.translate(offset),
                        blockRect = box.blockRect.translate(offset),
                        blockIndex = blockIndex++,
                    )
                )
            }
            if (band != bitmap) band.recycle()
            if (height < bandHeight) break
            top += bandHeight - BAND_OVERLAP
        }
        return dropSeamDuplicates(boxes)
    }

    private fun Rect.translate(dy: Float) = Rect(left, top + dy, right, bottom + dy)

    /**
     * Removes the second reading of a line that two overlapping bands both saw.
     *
     * Same text within a line's height of the same place is the same line: the
     * two bands measure it from different origins, so the boxes agree to a few
     * pixels rather than exactly.
     */
    private fun dropSeamDuplicates(boxes: List<OcrElementBox>): List<OcrElementBox> {
        val kept = mutableListOf<OcrElementBox>()
        for (box in boxes) {
            val tolerance = (box.imageRect.bottom - box.imageRect.top).coerceAtLeast(8f)
            val duplicate = kept.any { other ->
                other.text == box.text &&
                        kotlin.math.abs(other.imageRect.top - box.imageRect.top) < tolerance &&
                        kotlin.math.abs(other.imageRect.left - box.imageRect.left) < tolerance
            }
            if (!duplicate) kept.add(box)
        }
        return kept
    }

    private fun recognizeWhole(engine: RapidOCR, bitmap: android.graphics.Bitmap): List<OcrElementBox> {
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
                    elementIndex = 0,
                    confidence = recResult.confidence,
                    backgroundColor = sampleBackground(bitmap, rect),
                )
            )
        }
        return boxes
    }

    /**
     * Median colour of a ring of points just outside [rect]: the inside of the
     * bubble for dialogue, the artwork for a sound effect painted over it.
     *
     * The median rather than the mean, because a mean of white bubble and black
     * outline is grey, which matches neither. Roughly a thousand pixel reads for
     * a whole page, against the several seconds recognition itself costs.
     */
    private fun sampleBackground(bitmap: Bitmap, rect: Rect): Int {
        val pad = (minOf(rect.width, rect.height) * 0.4f).coerceIn(3f, 24f)
        val samples = mutableListOf<Int>()

        fun sample(x: Float, y: Float) {
            val px = x.toInt()
            val py = y.toInt()
            if (px < 0 || py < 0 || px >= bitmap.width || py >= bitmap.height) return
            samples.add(bitmap.getPixel(px, py))
        }

        val steps = 8
        for (i in 0..steps) {
            val x = rect.left + rect.width * (i / steps.toFloat())
            sample(x, rect.top - pad)
            sample(x, rect.bottom + pad)
        }
        for (i in 0..4) {
            val y = rect.top + rect.height * (i / 4f)
            sample(rect.left - pad, y)
            sample(rect.right + pad, y)
        }

        if (samples.isEmpty()) return 0
        return samples.sortedBy { luminanceOf(it) }[samples.size / 2]
    }

    companion object {
        lateinit var context: Context

        /** Text crops handed to the recogniser per call. */
        private const val REC_BATCH = 8

        /**
         * Tallest a page may be, in widths, before it is scanned in bands.
         *
         * An ordinary comic page in this library runs about 1.4 widths tall and
         * a tall one 1.6. Three leaves every real page untouched — the banding
         * is for webtoon strips, which are eleven and a half.
         */
        private const val MAX_SCAN_ASPECT = 3f

        /**
         * How much of the previous band each new one repeats.
         *
         * Has to comfortably exceed one line of lettering, since the point is
         * that a line cut by a seam is whole in the other band. Webtoon
         * lettering measured at 30-45px on a 720px-wide strip.
         */
        private const val BAND_OVERLAP = 160
    }
}
