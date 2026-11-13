package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable

@Serializable
sealed class Avatar {
    @Serializable
    data object Dummy : Avatar()

    @Serializable
    data class Emoji(val content: String) : Avatar()

    /**
     * 图片头像。动图用 [crop] 记录裁剪区域，渲染时保留动画；静态图在选图时已经压成 JPEG，[crop] 为空。
     */
    @Serializable
    data class Image(
        val url: String,
        val crop: ImageCrop? = null,
    ) : Avatar()

    /**
     * 视频头像。循环静音播放；太小的头像位置只显示一帧封面，避免一个列表里挂一堆解码器。
     */
    @Serializable
    data class Video(val url: String) : Avatar()

    /** 图片或视频头像指向的文件地址，其它类型没有可清理的文件。 */
    val mediaUrl: String?
        get() = when (this) {
            is Image -> url
            is Video -> url
            else -> null
        }
}
