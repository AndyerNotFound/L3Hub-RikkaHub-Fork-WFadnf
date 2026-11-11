package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.ui.effectSurface

/**
 * 输入框、气泡、顶栏按钮共用的背景效果。
 *
 * 实现已经收敛到 [effectSurface]：和设置页卡片、抽屉、浮层走同一条路径，
 * 避免输入框和别的组件各调一套参数。
 *
 * 开启后容器本身需要保持透明，[containerColor] 由效果负责铺底和着色。
 */
@Composable
internal fun Modifier.chatInputHaze(
    hazeState: HazeState,
    displaySetting: DisplaySetting,
    containerColor: Color,
    shape: RoundedCornerShape,
    // 留空时按档位取滑杆：模糊读 blurOpacity，玻璃读 glassOpacity
    alpha: Float? = null,
): Modifier = effectSurface(
    shape = shape,
    baseColor = containerColor,
    hazeState = hazeState,
    tintAlpha = alpha,
    displaySetting = displaySetting,
)
