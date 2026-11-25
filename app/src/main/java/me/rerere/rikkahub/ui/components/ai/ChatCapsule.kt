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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.rerere.rikkahub.data.datastore.AdvancedAppearanceSetting
import me.rerere.rikkahub.data.datastore.BackgroundSurfaceStyle
import me.rerere.rikkahub.data.datastore.DisplaySetting
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
 * 关掉高级外观时返回 null，调用方退回一道实色底（旧的全特效运行时已经退役）。
 * `showTopBarBackground` 只管整条顶栏的底，按钮是浮在上面的独立小块，两者各管各的
 * （和上游 ChatFloatingHeader 的分工一致）。
 *
 * 采样来源由调用方给（ChatPage 往上供的是含消息正文的 chrome 源），这里决定的是「要不要采」：
 * - [backdropActive] 为真表示整条顶栏那道底已经在用同一个源。这时胶囊保持静态着色，
 *   不让同一片区域对同一来源采样两遍——两遍叠加不只是多算一次，颜色也会深一层。
 * - 顶栏的底关着（或没开）时胶囊自己取源，模糊半径与性能开关跟整条顶栏同一组设置：
 *   [DisplaySetting.topBarBlurRadius]、[DisplaySetting.enableTopBarBlur]
 *   与 [AdvancedAppearanceSetting.enableTopBarPerformanceEffects]。
 */
internal fun AdvancedAppearanceSetting.topBarCapsuleMaterial(
    display: DisplaySetting,
    backdropActive: Boolean = false,
): AppearanceMaterial? =
    if (!enableAdvancedAppearance) {
        null
    } else {
        AppearanceMaterial(
            style = pageSurfaceStyle,
            opacity = display.topBarSurfaceOpacity,
            radius = display.topBarBlurRadius,
            effects = !backdropActive && enableTopBarPerformanceEffects && display.enableTopBarBlur,
        )
    }

/**
 * 这一档材质要不要再补一道平描边。
 *
 * 玻璃档由 [advancedMaterial] 自己画渐变描边（高光 + 内阴影），再叠一道平线就成了双线，
 * 所以只有玻璃档返回 false；其余各档（含实色）都补一道，和实色底那条路一样能看清边界。
 */
internal fun AppearanceMaterial?.needsFlatOutline(): Boolean =
    this != null && style != BackgroundSurfaceStyle.LIQUID_GLASS

/**
 * 顶栏与输入框下方悬浮栏共用的胶囊容器：同圆角、同投影、同底色，模糊与描边只有这一份实现。
 *
 * 圆角取 50%，容器多高就圆出多大的药丸；高度由调用方通过 [modifier] 给。
 * 里面的入口各自点击，段与段之间用 [ChatCapsuleDivider] 隔开。
 *
 * 高级外观开启后容器改用 [advancedMaterial]：材质、着色浓度、模糊半径都来自高级外观设置，
 * Haze 源仍然是页面那一个（不在这里新开源，也不给每一段各抓一份场景）。
 * 没开高级外观时只铺一道实色底：旧的全特效运行时已经退役，不再有第二层着色。
 *
 * [material] 由调用方决定：顶栏按 [topBarCapsuleMaterial]，悬浮栏按聊天坞的开关与浓度。
 * 传 null 表示这一处不该走高级外观（比如聊天坞关着），容器退回实色底 —— 这里不做任何兜底，
 * 否则「聊天坞关着」会被顶栏材质悄悄接管。
 */
@Composable
internal fun ChatCapsule(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    material: AppearanceMaterial? = null,
    content: @Composable RowScope.() -> Unit,
) {
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
                        // 玻璃档自带渐变描边，其余档补一道平线，和实色底那条路的观感接上
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
                    // 没开高级外观：只有这一道实色底，没有模糊、没有描边，也不会再叠第二层着色
                    Modifier.background(containerColor)
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
