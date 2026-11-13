package me.rerere.ui.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ui.sketch.upright
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "ImageCropImage"

// 屏幕上显示用的图的长边：原图很大时单独缩一份来画，GPU 纹理有尺寸上限
private const val PREVIEW_SIZE = 2048

internal class CropImage(
    val bitmap: Bitmap,
    val preview: ImageBitmap,
    val animated: Boolean,
)

private class DecodedCrop(val bitmap: Bitmap, val animated: Boolean)

/**
 * 读出要裁剪的图片：按 EXIF 摆正，长边缩到 [maxSize] 以内。读不出来时返回 null。
 *
 * [CropImage.animated] 表示源文件是多帧动图：确认裁剪时只保留源文件和裁剪区域，
 * 由调用方在渲染时裁切，避免把动图压成一张静态图。
 */
internal suspend fun loadCropImage(context: Context, uri: Uri, maxSize: Int): CropImage? =
    withContext(Dispatchers.IO) {
        runCatching {
            val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { decodeWithImageDecoder(context, uri, maxSize) }.getOrElse {
                    Log.w(TAG, "ImageDecoder failed for $uri, falling back to BitmapFactory", it)
                    DecodedCrop(
                        bitmap = decodeWithBitmapFactory(context, uri, maxSize),
                        animated = isAnimatedImage(context, uri),
                    )
                }
            } else {
                DecodedCrop(
                    bitmap = decodeWithBitmapFactory(context, uri, maxSize),
                    animated = isAnimatedImage(context, uri),
                )
            }
            // HDR 的 HEIF 会解成 10 位或者半精度的格式，统一转成 8 位，后面才能按 JPEG 存
            val bitmap = if (decoded.bitmap.config != Bitmap.Config.ARGB_8888) {
                decoded.bitmap.copy(Bitmap.Config.ARGB_8888, false).also { decoded.bitmap.recycle() }
            } else {
                decoded.bitmap
            }
            val longest = max(bitmap.width, bitmap.height)
            val preview = if (longest > PREVIEW_SIZE) {
                val ratio = PREVIEW_SIZE.toFloat() / longest
                bitmap.scale(
                    (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
                    (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
                )
            } else {
                bitmap
            }
            CropImage(bitmap, preview.asImageBitmap(), decoded.animated)
        }.onFailure {
            Log.e(TAG, "Failed to load $uri", it)
        }.getOrNull()
    }

// ImageDecoder 自己会按 EXIF 摆正，也能解 HEIF，并且能从文件头判断是不是动图
@RequiresApi(Build.VERSION_CODES.P)
private fun decodeWithImageDecoder(context: Context, uri: Uri, maxSize: Int): DecodedCrop {
    var animated = false
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        animated = info.isAnimated
        // 要在 CPU 上裁剪和编码，不能用硬件位图
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val width = info.size.width
        val height = info.size.height
        val longest = max(width, height)
        if (longest > maxSize) {
            val ratio = maxSize.toFloat() / longest
            decoder.setTargetSize(
                (width * ratio).roundToInt().coerceAtLeast(1),
                (height * ratio).roundToInt().coerceAtLeast(1),
            )
        }
    }
    return DecodedCrop(bitmap, animated)
}

private fun decodeWithBitmapFactory(context: Context, uri: Uri, maxSize: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sampleSize = 1
    while (max(bounds.outWidth, bounds.outHeight) / sampleSize > maxSize) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("Failed to decode $uri")
    return decoded.upright(context, uri)
}

/**
 * 读文件头判断是不是多帧动图。只在 ImageDecoder 不可用（API 28 以下）或解码回退时使用。
 *
 * GIF 一律按动图处理：静态 GIF 保留原文件也只是多占一点存储，不影响显示。
 * WebP 的 VP8X 头里 bit 1 是动画标记，带 ANIM/ANMF 块的文件一定会置位。
 */
internal fun isAnimatedImage(context: Context, uri: Uri): Boolean = runCatching {
    val header = ByteArray(64)
    val read = context.contentResolver.openInputStream(uri)?.use { it.read(header) } ?: 0
    if (read >= 6 && header.copyOfRange(0, 4).contentEquals("GIF8".toByteArray(Charsets.US_ASCII))) {
        return true
    }
    if (read >= 21 &&
        header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
        header.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP" &&
        header.copyOfRange(12, 16).toString(Charsets.US_ASCII) == "VP8X"
    ) {
        return header[20].toInt() and 0x02 != 0
    }
    false
}.getOrDefault(false)
