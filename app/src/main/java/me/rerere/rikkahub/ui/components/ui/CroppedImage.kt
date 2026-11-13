package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import me.rerere.rikkahub.data.model.ImageCrop
import kotlin.math.max

/**
 * 按 [crop] 显示一张图片（GIF / WebP 动图也可以），保留动画。
 *
 * 裁剪框以外不画，所以调用方要按头像形状或背景边界先裁好外框。坐标和选图时的
 * [me.rerere.ui.crop.NormalizedCrop] 一致：先顺时针旋转 [ImageCrop.rotation] 个 90°，
 * 再按比例换算到旋转后的图上。
 *
 * @param cover 为 true 时按覆盖方式缩放并居中（背景），为 false 时让裁剪区域刚好铺满整块（头像）
 */
@Composable
fun CroppedImage(
    model: Any?,
    crop: ImageCrop,
    modifier: Modifier = Modifier,
    cover: Boolean = false,
    requestSize: Int = 1024,
) {
    val context = LocalContext.current
    val request = remember(model, requestSize) {
        ImageRequest.Builder(context)
            .data(model)
            // 动图每帧都会解码，必须限个尺寸；不传的话 Coil 会按原图大小解，4K 动图会直接吃满内存
            .size(requestSize, requestSize)
            .build()
    }
    val painter = rememberAsyncImagePainter(request)

    Canvas(modifier) {
        val source = painter.intrinsicSize
        if (!source.isSpecified || source.width <= 0f || source.height <= 0f) return@Canvas

        val rotatedWidth = if (crop.rotation % 2 == 0) source.width else source.height
        val rotatedHeight = if (crop.rotation % 2 == 0) source.height else source.width
        val cropWidth = (crop.width * rotatedWidth).coerceAtLeast(1f)
        val cropHeight = (crop.height * rotatedHeight).coerceAtLeast(1f)
        val cropLeft = crop.left * rotatedWidth
        val cropTop = crop.top * rotatedHeight

        val scale = if (cover) {
            max(size.width / cropWidth, size.height / cropHeight)
        } else {
            size.width / cropWidth
        }
        // 覆盖模式下裁剪区域可能比容器大，居中后两边各裁掉一点
        val dx = (size.width - cropWidth * scale) / 2f
        val dy = (size.height - cropHeight * scale) / 2f

        val matrix = android.graphics.Matrix().apply {
            // 源图中心转到原点 → 旋转 → 把旋转后包围盒左上角放回原点 → 裁剪区域左上角放到原点 → 缩放 → 居中
            postTranslate(-source.width / 2f, -source.height / 2f)
            postRotate(90f * crop.rotation)
            postTranslate(rotatedWidth / 2f, rotatedHeight / 2f)
            postTranslate(-cropLeft, -cropTop)
            postScale(scale, scale)
            postTranslate(dx, dy)
        }
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val checkpoint = native.save()
            native.concat(matrix)
            with(painter) {
                draw(source)
            }
            native.restoreToCount(checkpoint)
        }
    }
}
