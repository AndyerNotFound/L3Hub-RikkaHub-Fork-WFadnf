package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.material3.Material3
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassStyleScope
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.material3.material3
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.effectiveBackgroundEffect
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.LocalDarkMode

/** 组件特效统一的模糊半径；玻璃档的 depth 也按这个基准缩放。 */
val ComponentEffectBlurRadius: Dp = 12.dp

private val ComponentGlassBlurRadius: Dp = 16.dp

/**
 * 组件特效能取到的 Haze 源。
 *
 * 聊天页提供的是背景层（加上静息时的消息列表）；默认 null 时特效退化成半透明色块，开销为零。
 *
 * 源由页面显式提供，不给列表每一项挂 `hazeSource`：滚动时每项重糊一次会明显掉帧。
 */
val LocalGlassHazeState = compositionLocalOf<HazeState?> { null }

/**
 * 当前这一档特效的着色浓度：模糊读 [DisplaySetting.blurOpacity]，玻璃读 [DisplaySetting.glassOpacity]，
 * 关闭时为 1。所有组件（输入框、气泡、顶栏、卡片、抽屉、浮层）都读这一个值，
 * 两个滑杆各自只管自己那一档，不会再出现「滑杆只对输入框生效」。
 */
val DisplaySetting.effectSurfaceAlpha: Float
    get() = when (effectiveBackgroundEffect) {
        BackgroundEffectType.OFF -> 1f
        BackgroundEffectType.BLUR -> blurOpacity.coerceIn(0f, 1f)
        BackgroundEffectType.GLASS -> glassOpacity.coerceIn(0f, 1f)
    }

/**
 * 浮层与悬浮控件的着色浓度：仍然由滑杆线性控制，只是给一个读得清的下限 [floor]。
 *
 * 需要下限的层级有一个共同点 —— 底下压着**没被糊过的正文**：BottomSheet 在独立窗口里，
 * 拿页面上的 haze 源取不到东西；抽屉和浮在列表上的小按钮同理。着色掉到下限以下，
 * 正文会原样透上来，整块浮层看起来就像没有底板。
 *
 * 下限之内照样跟着滑杆走（0% 落在 [floor]，100% 落在 1），滑杆不会在这些组件上变成死值。
 */
fun DisplaySetting.glassOverlayAlpha(floor: Float): Float {
    val min = floor.coerceIn(0f, 1f)
    return min + (1f - min) * effectSurfaceAlpha
}

/** 实色底换成同色半透明；关闭模糊特效时原样返回。 */
@Composable
fun glassColor(base: Color): Color {
    val displaySetting = LocalSettings.current.displaySetting
    if (!displaySetting.enableBlurEffect) return base
    return base.copy(alpha = base.alpha * displaySetting.effectSurfaceAlpha)
}

/**
 * 浮在正文上的小控件（消息跳转按钮、建议回复、下拉新建胶囊）的底色。
 *
 * 和 [glassColor] 的区别只有下限：这些控件体积小、底下又压着消息正文，全透明就点不准了。
 */
@Composable
fun glassOverlayColor(base: Color, floor: Float = 0.6f): Color {
    val displaySetting = LocalSettings.current.displaySetting
    if (!displaySetting.enableBlurEffect) return base
    return base.copy(alpha = base.alpha * displaySetting.glassOverlayAlpha(floor))
}

/**
 * 浮层（抽屉 / BottomSheet）能不能真做成玻璃。
 *
 * 想糊出东西就得手上有一个 Haze 源，外加模糊特效开着。缺任意一条时
 * [glassSurface] 只剩一层半透明 —— 浮层半透明却不模糊，底下的正文会直接透上来，
 * 比实色更难读，所以调用方按这个返回 false 保持原来的实色底。
 */
@Composable
fun canGlassPane(hazeState: HazeState? = LocalGlassHazeState.current): Boolean {
    val displaySetting = LocalSettings.current.displaySetting
    return hazeState != null && displaySetting.enableBlurEffect
}

/**
 * 组件特效底：所有带模糊/玻璃的组件都走这里，输入框、气泡、顶栏、卡片、抽屉、浮层共用同一套
 * 半径、着色和档位判断。
 *
 * - `enableBlurEffect == false`：一条 haze 都不挂，也不改透明度，直接返回原 modifier，
 *   由组件自己的实色底兜住。
 * - 没有 Haze 源：只画一层半透明底（等价于旧版 `background`），保证滑杆仍然生效。
 * - 模糊档：先糊源内容，再把着色层叠在模糊之上。Haze 的 `backgroundColor` 画在模糊内容
 *   底下，源不透明时完全看不见，所以着色不能塞进 style。
 * - 玻璃档：着色交给 `GlassStyle.tint`，和输入框用的是同一份光学参数。
 *
 * 调用方在特效开启时不要再自己铺一层底色，否则会叠成两倍浓度（以前输入框和气泡就是这样）。
 */
@Composable
fun Modifier.effectSurface(
    shape: Shape,
    baseColor: Color,
    hazeState: HazeState? = LocalGlassHazeState.current,
    tintAlpha: Float? = null,
    blurRadius: Dp = ComponentEffectBlurRadius,
    minAlpha: Float = 0f,
    border: BorderStroke? = null,
    displaySetting: DisplaySetting = LocalSettings.current.displaySetting,
): Modifier {
    val effect = displaySetting.effectiveBackgroundEffect
    if (effect == BackgroundEffectType.OFF) return this
    val alpha = (tintAlpha ?: displaySetting.effectSurfaceAlpha)
        .let { if (minAlpha > 0f) minAlpha + (1f - minAlpha) * it else it }
        .coerceIn(0f, 1f)
    val tint = baseColor.copy(alpha = baseColor.alpha * alpha)
    val pane = when {
        hazeState == null -> Modifier.background(tint)

        effect == BackgroundEffectType.BLUR -> {
            val radius = blurRadius
            Modifier
                .hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    style = HazeBlurStyle.Material3(containerColor = Color.Transparent) {
                        blurRadius(radius)
                    },
                )
                .background(tint)
        }

        else -> Modifier.hazeGlass(
            input = HazeInput.Sources(hazeState),
            style = rememberGlassSurfaceStyle(
                containerColor = baseColor,
                tint = tint,
                shape = shape,
            ),
        )
    }
    return clip(shape)
        .then(pane)
        .then(if (border != null) Modifier.border(border, shape) else Modifier)
}

/** 玻璃档的样式：和输入框共用一份参数，只有形状与着色不同。 */
@Composable
private fun rememberGlassSurfaceStyle(
    containerColor: Color,
    tint: Color,
    shape: Shape,
): GlassStyle {
    val darkMode = LocalDarkMode.current
    val glassShape = when (shape) {
        is RoundedCornerShape -> shape
        RectangleShape -> RoundedCornerShape(0.dp)
        else -> RoundedCornerShape(ComponentGlassBlurRadius)
    }
    val baseStyle = GlassStyle.material3(
        containerColor = containerColor,
        tint = tint,
    )
    return remember(baseStyle, darkMode, glassShape) {
        baseStyle.then {
            appearanceResponse(dark = darkMode)
            // Keep background text from competing with the content.
            optics {
                copy(
                    blurRadius = OpticalSizeValue.Fixed(ComponentGlassBlurRadius),
                    depth = OpticalSizeValue.Fixed(0.5f),
                )
            }
            shape(glassShape)
        }
    }
}

// Haze 按系统夜间模式选玻璃的明暗预设，应用内单独设置主题时会选错（深色主题下被提亮发白），
// 所以按应用主题显式写入。深色数值对应库里 internal 的 GlassStyle.regularDark。
private fun GlassStyleScope.appearanceResponse(dark: Boolean) {
    if (dark) {
        specularIntensity(0.38f)
        edgeShadow(Color.Black.copy(alpha = 0.32f))
        ambientResponse(0.08f)
        contrast(0.08f)
        whitePoint(-0.22f)
        chromaMultiplier(1.1f)
    } else {
        specularIntensity(GlassDefaults.specularIntensity)
        edgeShadow(GlassDefaults.edgeShadow)
        ambientResponse(GlassDefaults.ambientResponse)
        contrast(GlassDefaults.contrast)
        whitePoint(GlassDefaults.whitePoint)
        chromaMultiplier(GlassDefaults.chromaMultiplier)
    }
}

/**
 * 毛玻璃底：半透明色 + hazeEffect。
 *
 * - `enableBlurEffect == false`：一条 haze 都不挂，也不改透明度，直接返回原 modifier，
 *   由组件自己的实色底兜住。
 * - 没有 Haze 源：只画半透明底加一道描边。
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape,
    baseColor: Color,
    hazeState: HazeState? = LocalGlassHazeState.current,
    radius: Dp = ComponentEffectBlurRadius,
    // 大块浮层（抽屉、BottomSheet）必须给个不透明下限：haze 只糊注册过的源，
    // 正文本身不在源里，35% 透明时文字会原样透上来，看着像没底板。
    // 下限之内仍然随滑杆变化，见 glassOverlayAlpha。
    minAlpha: Float = 0f,
): Modifier = effectSurface(
    shape = shape,
    baseColor = baseColor,
    hazeState = hazeState,
    blurRadius = radius,
    minAlpha = minAlpha,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
)

/** BottomSheet 底板圆角：跟 M3 默认的 28dp 顶角对齐，玻璃底和内容裁切才不会错位。 */
val GlassSheetShape: Shape =
    RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp)

/**
 * 选项卡（`SingleChoiceSegmentedButtonRow` 里的 `SegmentedButton`）的配色。
 *
 * 选中项原来是一块实色 secondaryContainer，这里换成 surface 加「当前档位的透明度」，
 * 选中状态靠主色文字区分；关闭模糊特效时整份交回 M3 默认配色。
 */
@Composable
fun glassSegmentedButtonColors() = if (
    !LocalSettings.current.displaySetting.enableBlurEffect
) {
    SegmentedButtonDefaults.colors()
} else {
    SegmentedButtonDefaults.colors(
        activeContainerColor = glassColor(MaterialTheme.colorScheme.surface),
        activeContentColor = MaterialTheme.colorScheme.primary,
    )
}

/**
 * 开关配色：未选中的轨道（关着的时候它就是开关的「容器」）走玻璃底。
 *
 * 轨道淡下去以后形状全靠描边认，所以描边留 0.5 不透明度；选中态保持主色实心，
 * 「开着」得一眼看得出来。关闭模糊特效时整份交回 M3 默认配色。
 */
@Composable
fun glassSwitchColors() = if (
    !LocalSettings.current.displaySetting.enableBlurEffect
) {
    SwitchDefaults.colors()
} else {
    SwitchDefaults.colors(
        uncheckedTrackColor = glassColor(MaterialTheme.colorScheme.surfaceContainerHighest),
        uncheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    )
}

/** FilterChip 配色：未选中是玻璃底，选中态是玻璃底的 secondaryContainer。 */
@Composable
fun glassFilterChipColors() = if (
    !LocalSettings.current.displaySetting.enableBlurEffect
) {
    FilterChipDefaults.filterChipColors()
} else {
    FilterChipDefaults.filterChipColors(
        containerColor = glassColor(MaterialTheme.colorScheme.surfaceContainerLow),
        selectedContainerColor = glassColor(MaterialTheme.colorScheme.secondaryContainer),
    )
}

/** AssistChip 配色：同上，只有一层玻璃底。 */
@Composable
fun glassAssistChipColors() = if (
    !LocalSettings.current.displaySetting.enableBlurEffect
) {
    AssistChipDefaults.assistChipColors()
} else {
    AssistChipDefaults.assistChipColors(
        containerColor = glassColor(MaterialTheme.colorScheme.surfaceContainerLow),
    )
}
