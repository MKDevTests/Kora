package io.github.snd_r.komelia.infra.ncnn

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

object NcnnSharedLibraries {
    private var loaded = false
    var loadError: Throwable? = null
        private set

    fun load() {
        if (loaded) return
        try {
            System.loadLibrary("ncnn")
            System.loadLibrary("ncnn-upscaler")
            loaded = true
        } catch (e: Throwable) {
            loadError = e
            logger.error(e) { "Failed to load ncnn-upscaler library" }
        }
    }

    val isAvailable: Boolean
        get() = loaded
}
