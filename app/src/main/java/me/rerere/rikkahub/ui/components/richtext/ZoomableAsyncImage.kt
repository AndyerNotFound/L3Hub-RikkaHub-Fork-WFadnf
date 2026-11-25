package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.placeholder
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.ui.components.LocalExportContext

/**
 * 聊天里的一张图片：点开预览。
 *
 * [respectIntrinsicSize] 打开后按原图比例把显示尺寸收进 [maxDisplayWidthDp] ×
 * [maxDisplayHeightDp] 的框里（生成图用），关掉时沿用调用方给的固定尺寸（附件缩略图用）。
 *
 * [retryable] 打开后加载失败会换成「失败 + 点击重试」的文字，重试时绕过 Coil 缓存。
 * 默认关闭：用户附件那条路保持原来的占位图行为，外观和尺寸都不动。
 */
@Composable
fun ZoomableAsyncImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    respectIntrinsicSize: Boolean = false,
    maxDisplayWidthDp: Float = 360f,
    maxDisplayHeightDp: Float = 280f,
    retryable: Boolean = false,
) {
    var showImageViewer by remember(model) { mutableStateOf(false) }
    var retry by remember(model) { mutableIntStateOf(0) }
    // 原图比例：解码成功后由 painter 报告；换 URL 或重试都清空重测，别拿上一次的尺寸
    var intrinsicSize by remember(model, respectIntrinsicSize, retry) {
        mutableStateOf(Size.Unspecified)
    }
    var loading by remember(model, retry) { mutableStateOf(true) }
    var failed by remember(model, retry) { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val placeholder = if (LocalDarkMode.current) R.drawable.placeholder_dark else R.drawable.placeholder
    val export = LocalExportContext.current
    val loadingLabel = stringResource(R.string.chat_media_image_loading)
    val loadedLabel = stringResource(R.string.chat_media_image_loaded)
    // 上层给的宽度可能是 Infinity（横向滚动、wrapContent 里），夹一下再进尺寸计算
    val boundedWidthDp = maxDisplayWidthDp.takeIf { it.isFinite() && it > 0f } ?: 360f
    val boundedHeightDp = maxDisplayHeightDp.takeIf { it.isFinite() && it > 0f } ?: 280f
    val coilModel = remember(context, model, placeholder, export, retry) {
        ImageRequest.Builder(context)
            .data(model)
            .apply {
                // 网关有时把错误页配着很长的缓存寿命一起返回。手动重试必须回到网络，
                // 否则解码的永远是同一份坏字节。
                if (retry > 0) {
                    memoryCachePolicy(CachePolicy.WRITE_ONLY)
                    diskCachePolicy(CachePolicy.WRITE_ONLY)
                }
            }
            .placeholder(placeholder)
            .crossfade(false)
            .allowHardware(!export)
            .build()
    }
    val displaySize = if (respectIntrinsicSize) {
        boundedIntrinsicImageSize(intrinsicSize, boundedWidthDp, boundedHeightDp, density)
    } else {
        Size.Unspecified
    }
    val displaySizeModifier = if (displaySize.isUsableImageSize()) {
        Modifier.size(displaySize.width.dp, displaySize.height.dp)
    } else {
        Modifier
    }

    if (failed && retryable) {
        // 只有生成图才换成错误文字+重试；用户附件缩略图沿用原来的占位图行为，
        // 尺寸和外观都不动。
        Text(
            text = listOfNotNull(
                contentDescription?.takeIf { it.isNotBlank() },
                stringResource(R.string.chat_media_image_failed),
                stringResource(R.string.chat_media_image_retry),
            ).joinToString(" · "),
            modifier = modifier
                .widthIn(max = boundedWidthDp.dp)
                .clickable { retry++ }
                .padding(4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        AsyncImage(
            model = coilModel,
            contentDescription = contentDescription,
            modifier = modifier
                .then(displaySizeModifier)
                .shimmer(isLoading = loading && !export)
                .semantics { stateDescription = if (loading) loadingLabel else loadedLabel }
                .clickable {
                    showImageViewer = true
                },
            contentScale = contentScale,
            alpha = alpha,
            alignment = alignment,
            onLoading = {
                loading = true
                failed = false
            },
            onSuccess = { state ->
                loading = false
                failed = false
                // 解码后的原始尺寸；不指定尺寸时 painter 报的就是图片本身的大小
                intrinsicSize = state.painter.intrinsicSize
            },
            onError = {
                // 只记录失败态本身，不把附件 URL 写进日志
                loading = false
                failed = true
                // 上一次成功的尺寸不能留到这次失败上，否则会按旧比例占位
                intrinsicSize = Size.Unspecified
            },
        )
    }
    if (showImageViewer) {
        ImagePreviewDialog(images = listOf(model ?: "")) {
            showImageViewer = false
        }
    }
}

private fun Size.isUsableImageSize(): Boolean =
    // Size.Unspecified 的宽高是 NaN：先判 isSpecified 再读分量，
    // 免得把 NaN 带进后面的 dp 换算和比例计算（NaN 的比较恒为 false，会静默走错分支）
    isSpecified && width.isFinite() && height.isFinite() && width > 0f && height > 0f

/**
 * 把图片原始像素尺寸按比例收进 dp 上限框；像素换算成 dp 之后再比，
 * 否则高密度屏上小图会被当成 dp 放大。不放大原图，小图保持原样。
 */
private fun boundedIntrinsicImageSize(
    intrinsic: Size,
    maxWidthDp: Float,
    maxHeightDp: Float,
    density: Density,
): Size {
    if (!intrinsic.isUsableImageSize()) return Size.Unspecified
    val widthDp = with(density) { intrinsic.width.toDp().value }
    val heightDp = with(density) { intrinsic.height.toDp().value }
    if (!widthDp.isFinite() || !heightDp.isFinite() || widthDp <= 0f || heightDp <= 0f) {
        return Size.Unspecified
    }
    val scale = minOf(1f, maxWidthDp / widthDp, maxHeightDp / heightDp)
    return Size(widthDp * scale, heightDp * scale)
}
