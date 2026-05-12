package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

object ImageReaderSettingsTable : Table("ImageReaderSettings") {
    val bookId = text("book_id")

    val readerType = text("reader_type")
    val stretchToFit = bool("stretch_to_fit")

    val pagedScaleType = text("paged_scale_type")
    val pagedReadingDirection = text("paged_reading_direction")
    val pagedPageLayout = text("paged_page_layout")

    val continuousReadingDirection = text("continuous_reading_direction")
    val continuousPadding = float("continuous_padding")
    val continuousPageSpacing = integer("continuous_page_spacing")
    val cropBorders = bool("crop_borders")

    val loadThumbnailPreviews = bool("load_thumbnail_previews")
    val volumeKeysNavigation = bool("volume_keys_navigation")

    val flashOnPageChange = bool("flash_on_page_change")
    val flashDuration = long("flash_duration")
    val flashEveryNPages = integer("flash_every_n_pages")
    val flashWith = text("flash_with")

    val downsamplingKernel = text("downsampling_kernel")
    val linearLightDownsampling = bool("linear_light_downsampling")
    val upsamplingMode = text("upsampling_mode")

    val ncnnEnabled = bool("ncnn_enabled").default(false)
    val ncnnEngine = text("ncnn_engine").default("WAIFU2X")
    val ncnnModel = text("ncnn_model").default("models-cunet/scale2.0x_model")
    val ncnnGpuId = integer("ncnn_gpu_id").default(0)
    val ncnnTtaMode = bool("ncnn_tta_mode").default(false)
    val ncnnNumThreads = integer("ncnn_num_threads").default(4)
    val ncnnUpscaleOnLoad = bool("ncnn_upscale_on_load").default(false)
    val ncnnUpscaleThreshold = integer("ncnn_upscale_threshold").default(1200)
    val ncnnUpscalerUrl = text("ncnn_upscaler_url").default("https://github.com/eserero/Komelia/releases/download/model/NcnnUpscalerModels.zip")

    val ortDeviceId = integer("onnx_runtime_device_id")
    val ortUpscalerMode = text("onnx_runtime_mode")
    val ortUpscalerTileSize = integer("onnx_runtime_tile_size")
    val ortUpscalerUserModelPath = text("onnx_runtime_model_path").nullable()
    val panelDetectionUrl = text("panel_detection_url").default("https://github.com/eserero/Sipurra/releases/download/model/rf-detr-med.onnx.zip")
    val rapidOcrModelsUrl = text("rapid_ocr_models_url").default("https://github.com/eserero/Sipurra/releases/download/model/RapidOcrModels.zip")

    val panelsFullPageDisplayMode = text("panels_full_page_display_mode").default("BOTH")
    val pagedReaderTapToZoom = bool("paged_reader_tap_to_zoom").default(true)
    val panelReaderTapToZoom = bool("panel_reader_tap_to_zoom").default(false)
    val pagedReaderAdaptiveBackground = bool("paged_reader_adaptive_background").default(true)
    val panelReaderAdaptiveBackground = bool("panel_reader_adaptive_background").default(true)
    val tapNavigationMode = text("tap_navigation_mode").default("LEFT_RIGHT")
    val imageCacheSizeLimitMb = long("image_cache_size_limit_mb").default(1024L)

    val ocrEnabled = bool("ocr_enabled").default(false)
    val ocrLanguage = text("ocr_language").default("LATIN")
    val ocrEngine = text("ocr_engine").default("ML_KIT")
    val ocrRapidOcrModel = text("ocr_rapid_ocr_model").default("ENGLISH_CHINESE")
    val ocrMergeBoxes = bool("ocr_merge_boxes").default(true)

    val pagedSplitDoublePages = bool("paged_split_double_pages").default(false)
    val pagedReaderAutoDirection = bool("paged_reader_auto_direction").default(true)

    override val primaryKey = PrimaryKey(bookId)
}
