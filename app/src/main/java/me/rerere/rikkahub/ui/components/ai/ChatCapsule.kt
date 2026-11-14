package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.effectiveBackgroundEffect
import me.rerere.rikkahub.ui.theme.LocalFullWidthComponents
import me.rerere.rikkahub.ui.theme.appButtonShadow

/** 胶囊本身的形状：药丸；全宽模式下和其余组件一起收成直角。 */
@Composable
private fun capsuleShape(): RoundedCornerShape =
    if (LocalFullWidthComponents.current) {
        RoundedCornerShape(0.dp)
    } else {
        RoundedCornerShape(50)
    }

/**
 * 顶栏与输入框下方悬浮栏共用的胶囊容器：同圆角、同投影、同底色，模糊与描边只有这一份实现。
 *
 * 圆角取 50%，容器多高就圆出多大的药丸；高度由调用方通过 [modifier] 给。
 * 关闭背景特效时容器自己铺实色，开着时着色交给 chatInputHaze。
 * 里面的入口各自点击，段与段之间用 [ChatCapsuleDivider] 隔开。
 */
@Composable
internal fun ChatCapsule(
    displaySetting: DisplaySetting,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val effect = displaySetting.effectiveBackgroundEffect
    val shape = capsuleShape()
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow

    Row(
        modifier = modifier
            .appButtonShadow(shape, 2.dp)
            .clip(shape)
            .background(if (effect == BackgroundEffectType.OFF) containerColor else Color.Transparent)
            .chatInputHaze(
                hazeState = hazeState,
                displaySetting = displaySetting,
                containerColor = containerColor,
                shape = shape,
            )
            // 描边画在模糊层之后，否则会被糊掉
            .then(
                if (effect == BackgroundEffectType.BLUR) {
                    Modifier.border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        shape,
                    )
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 入口按整颗胶囊的高度铺满，不被 M3 的最小触控尺寸撑大，两头才一般高
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            content()
        }
    }
}

/** 胶囊里两段之间的细线：只切中间一段，胶囊还是一整块。 */
@Composable
internal fun ChatCapsuleDivider(height: Dp = 18.dp) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(height)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    )
}
