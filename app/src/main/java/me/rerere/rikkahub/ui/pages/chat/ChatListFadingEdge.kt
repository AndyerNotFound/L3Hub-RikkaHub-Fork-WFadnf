// Extracted from ChatList.kt
package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

// 顶部渐隐：消息滚到标题栏下面时逐渐透明，从状态栏一直淡出到标题栏下沿之外，保证标题可读。
// 让内容自己变透明而不是盖一层背景色，背景图和动态渐变背景才不会被盖住。
//
// 这条渐隐靠离屏图层做 DstIn 遮罩：整块列表先录进一张离线纹理，再按渐变扣掉 alpha。
// 静止时没有失效，绘制整体被跳过；流式输出每来一个 token 都会失效，于是每帧都要
// 重新录制一整屏内容再做一次遮罩，是生成期间最贵的固定开销之一，[enabled] 关掉它。
internal fun Modifier.topFadingEdge(height: Dp, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val fadeHeight = height.toPx()
            val brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.6f to Color.Black.copy(alpha = 0.15f),
                1f to Color.Black,
                startY = 0f,
                endY = fadeHeight,
            )
            onDrawWithContent {
                drawContent()
                drawRect(brush, size = Size(size.width, fadeHeight), blendMode = BlendMode.DstIn)
            }
        }
}
