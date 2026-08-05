package io.github.snd_r.komelia.infra.ncnn

import android.content.res.AssetManager
import android.graphics.Bitmap

class NcnnUpscaler {
    external fun createGpuInstance(): Int
    external fun destroyGpuInstance()

    external fun init(
        engineType: Int,
        gpuId: Int,
        ttaMode: Boolean,
        numThreads: Int
    ): Int

    external fun load(
        assetManager: AssetManager?,
        paramPath: String,
        modelPath: String
    ): Int

    fun loadModel(paramPath: String, modelPath: String): Int {
        return load(null, paramPath, modelPath)
    }

    external fun process(
        bitmapIn: Bitmap,
        bitmapOut: Bitmap
    ): Int

    external fun setScale(scale: Int)
    external fun setNoise(noise: Int)
    external fun setTileSize(tileSize: Int)
    external fun getTileSize(): Int

    external fun release()

    companion object {
        const val ENGINE_WAIFU2X = 0
        const val ENGINE_REALCUGAN = 1
        const val ENGINE_REALSR = 2
        const val ENGINE_REAL_ESRGAN = 3
    }
}
