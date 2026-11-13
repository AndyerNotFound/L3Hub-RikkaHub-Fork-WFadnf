package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable

/**
 * 图片的裁剪区域，全部是 0..1 的比例。
 *
 * 坐标相对 [rotation] 个 90° 顺时针旋转之后的图片。动图保存的是源文件加这块区域，
 * 渲染时按同样顺序旋转、缩放和裁切，动画本身不会丢。
 */
@Serializable
data class ImageCrop(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val rotation: Int = 0,
)
