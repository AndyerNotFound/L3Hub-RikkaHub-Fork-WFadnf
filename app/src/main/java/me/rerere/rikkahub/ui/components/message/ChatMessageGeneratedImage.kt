package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.richtext.ZoomableAsyncImage
import me.rerere.rikkahub.ui.modifier.shimmer

/** 生成图在聊天里的显示框：宽度占气泡的 82%，上限 320dp 见方。 */
private const val GENERATED_IMAGE_WIDTH_FRACTION = 0.82f
private const val GENERATED_IMAGE_MAX_DP = 320f

/**
 * 助手生成或工具产出的图片：按原图比例收在框里，加载中显示骨架，失败能点着重试。
 *
 * 与用户附件的固定高度缩略图分开：附件那条路保持原样，这里的图不裁成方块。
 * [url] 为空或只有一个未填充的 base64 头时当作还在生成，直接显示骨架。
 */
@Composable
fun ChatMessageGeneratedImage(
    url: String,
    modifier: Modifier = Modifier,
) {
    if (isPendingGeneratedImageUrl(url)) {
        ImageGenerationSkeleton(modifier = modifier)
        return
    }
    BoxWithConstraints(
        modifier = modifier
            .widthIn(max = GENERATED_IMAGE_MAX_DP.dp)
            .fillMaxWidth(GENERATED_IMAGE_WIDTH_FRACTION),
        contentAlignment = Alignment.CenterStart,
    ) {
        val maxWidthDp = maxWidth.value.takeIf { it.isFinite() && it > 0f }
            ?.let { minOf(it, GENERATED_IMAGE_MAX_DP) }
            ?: GENERATED_IMAGE_MAX_DP
        ZoomableAsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .widthIn(max = maxWidthDp.dp)
                .heightIn(max = GENERATED_IMAGE_MAX_DP.dp)
                .clip(MaterialTheme.shapes.medium),
            respectIntrinsicSize = true,
            maxDisplayWidthDp = maxWidthDp,
            maxDisplayHeightDp = GENERATED_IMAGE_MAX_DP,
            retryable = true,
        )
    }
}

/**
 * 还没拿到图片字节：空串，或者形如 `data:image/png;base64,` 的空载荷。
 * 这种占位不交给 Coil，否则会先跑一次必然失败的请求再显示错误。
 */
internal fun isPendingGeneratedImageUrl(url: String): Boolean =
    url.isBlank() || url.matches(EmptyDataImageUrl)

private val EmptyDataImageUrl = Regex("^data:image/[^;]*;base64,\\s*$")

/** 生成中的占位块：与生成图同宽的方框，带扫光。 */
@Composable
fun ImageGenerationSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = stringResource(R.string.chat_media_image_generating),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .widthIn(max = GENERATED_IMAGE_MAX_DP.dp)
                .fillMaxWidth(GENERATED_IMAGE_WIDTH_FRACTION)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                .shimmer(
                    isLoading = true,
                    shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                    backgroundColor = Color.Transparent,
                    durationMillis = 1_100,
                ),
        )
    }
}
