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
import me.rerere.rikkahub.data.datastore.AdvancedAppearanceSetting
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.BackgroundSurfaceStyle
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.effectiveBackgroundEffect
import me.rerere.rikkahub.ui.components.ui.AppearanceMaterial
import me.rerere.rikkahub.ui.components.ui.advancedMaterial
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
 * 顶栏按钮胶囊的材质：高级外观开着时取页面材质 + 顶栏自己的着色浓度。
 *
 * 关掉高级外观时返回 null，调用方继续走原来那套 `chatInputHaze`，旧路径一个像素都不动。
 * 这里只读显示设置里的顶栏滑杆，不读 `showTopBarBackground`：那是整条顶栏的底，
 * 按钮是浮在上面的独立小块，两者各管各的（和上游 ChatFloatingHeader 的分工一致）。
 *
 * 模糊半径与性能开关留在整条顶栏的底上（见 ChatTopBar）：上游的 HeaderSurface 也是
 * `blurRadius = 0f, effectsEnabled = false`，按钮只取材质和着色，不给每个按钮各糊一次场景。
 */
internal fun AdvancedAppearanceSetting.topBarCapsuleMaterial(display: DisplaySetting): AppearanceMaterial? =
    if (!enableAdvancedAppearance) {
        null
    } else {
        AppearanceMaterial(
            style = pageSurfaceStyle,
            opacity = display.topBarSurfaceOpacity,
            radius = 0f,
            effects = false,
        )
    }

/**
 * 这一档材质要不要再补一道平描边。
 *
 * 玻璃档由 [advancedMaterial] 自己画渐变描边（高光 + 内阴影），再叠一道平线就成了双线，
 * 所以只有玻璃档返回 false；其余各档（含实色）都补一道，和旧路径一样能看清边界。
 */
internal fun AppearanceMaterial?.needsFlatOutline(): Boolean =
    this != null && style != BackgroundSurfaceStyle.LIQUID_GLASS

/**
 * 顶栏与输入框下方悬浮栏共用的胶囊容器：同圆角、同投影、同底色，模糊与描边只有这一份实现。
 *
 * 圆角取 50%，容器多高就圆出多大的药丸；高度由调用方通过 [modifier] 给。
 * 关闭背景特效时容器自己铺实色，开着时着色交给 chatInputHaze。
 * 里面的入口各自点击，段与段之间用 [ChatCapsuleDivider] 隔开。
 *
 * 高级外观开启后容器改用 [advancedMaterial]：材质、着色浓度、模糊半径都来自高级外观设置，
 * Haze 源仍然是页面那一个（不在这里新开源，也不给每一段各抓一份场景）。
 *
 * [material] 由调用方决定：顶栏按 [topBarCapsuleMaterial]，悬浮栏按聊天坞的开关与浓度。
 * 传 null 表示这一处不该走高级外观，容器原样走旧的 `chatInputHaze` —— 这里不做任何兜底，
 * 否则「聊天坞关着」会被顶栏材质悄悄接管。
 */
@Composable
internal fun ChatCapsule(
    displaySetting: DisplaySetting,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    material: AppearanceMaterial? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val effect = displaySetting.effectiveBackgroundEffect
    val shape = capsuleShape()
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow

    Row(
        modifier = modifier
            .appButtonShadow(shape, 2.dp)
            .clip(shape)
            .then(
                if (material != null) {
                    Modifier
                        .advancedMaterial(
                            shape = shape,
                            base = containerColor,
                            material = material,
                            hazeState = hazeState,
                            // 胶囊压在消息正文上，着色掉到 0.35 以下就认不出边界了
                            readableFloor = .35f,
                        )
                        // 玻璃档自带渐变描边，其余档补一道平线，和旧路径的观感接上
                        .then(
                            if (material.needsFlatOutline()) {
                                Modifier.border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    shape,
                                )
                            } else {
                                Modifier
                            }
                        )
                } else {
                    Modifier
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
                        )
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
