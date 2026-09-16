package snd.komelia.ui.common.immersive

import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.flow.first

actual suspend fun extractDominantColor(painter: AsyncImagePainter): Color? {
    val palette = paletteOf(painter) ?: return null
    val argb = palette.getDominantColor(0)
    return if (argb == 0) null else Color(argb)
}

actual suspend fun extractVibrantColor(painter: AsyncImagePainter): Color? {
    val palette = paletteOf(painter) ?: return null
    val swatch = palette.vibrantSwatch
        ?: palette.lightVibrantSwatch
        ?: palette.darkVibrantSwatch
        ?: palette.mutedSwatch
        ?: return null
    return Color(swatch.rgb)
}

private suspend fun paletteOf(painter: AsyncImagePainter): Palette? {
    val state = painter.state.first {
        it is AsyncImagePainter.State.Success || it is AsyncImagePainter.State.Error
    }
    val hwBitmap = ((state as? AsyncImagePainter.State.Success)?.result?.image as? BitmapImage)?.bitmap
        ?: return null
    val bitmap = if (hwBitmap.config == android.graphics.Bitmap.Config.HARDWARE)
        hwBitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
    else hwBitmap
    return Palette.from(bitmap).generate()
}
