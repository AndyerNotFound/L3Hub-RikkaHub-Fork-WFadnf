package me.rerere.ui.crop

import android.graphics.Bitmap

/**
 * 裁剪框在图片上的位置，全部是 0..1 的比例。
 *
 * 坐标相对 [rotation] 个 90° 顺时针旋转之后的图片；渲染时先旋转整张图，再按这个矩形裁切。
 * 动图不能像静态图那样直接压成一张 JPEG，所以把裁剪区域和源文件一起交给调用方保存。
 */
data class NormalizedCrop(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val rotation: Int = 0,
)

/**
 * 一次裁剪的结果。
 *
 * [bitmap] 永远是按当前裁剪框裁出的第一帧（静态图就是成品，动图只用来做预览或兜底）；
 * [animated] 为 true 时，调用方应优先保存源文件加 [crop]，而不是 [bitmap]。
 */
data class CropResult(
    val bitmap: Bitmap,
    val crop: NormalizedCrop,
    val animated: Boolean,
)
