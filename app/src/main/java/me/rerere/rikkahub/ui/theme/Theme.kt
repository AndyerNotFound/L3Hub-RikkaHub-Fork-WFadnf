package me.rerere.rikkahub.ui.theme

import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.t8rin.toolbox.ui.settings.LocalToolboxSettings
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.datastore.AnimationSpeed
import me.rerere.rikkahub.data.datastore.CornerStyle
import me.rerere.rikkahub.data.datastore.UiStyle
import me.rerere.rikkahub.ui.shape.styledShape
import me.rerere.rikkahub.ui.haptics.rememberEnhancedHapticFeedback
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.hooks.rememberCurrentColorMode
import me.rerere.rikkahub.ui.hooks.rememberUserSettingsState
import me.rerere.rikkahub.utils.getActivity
import me.rerere.ui.components.LocalDialogEnterSpec
import me.rerere.ui.components.LocalDialogExitSpec

private val ExtendLightColors = lightExtendColors()
private val ExtendDarkColors = darkExtendColors()
val LocalExtendColors = compositionLocalOf { ExtendLightColors }

val LocalDarkMode = compositionLocalOf { false }

private val AMOLED_DARK_BACKGROUND = Color(0xFF000000)

/** AMOLED：把整套配色里的大面积表面角色压成纯黑。文字与强调色不动，保证对比度。 */
private fun ColorScheme.amoledSurface(): ColorScheme = copy(
    background = AMOLED_DARK_BACKGROUND,
    surface = AMOLED_DARK_BACKGROUND,
    surfaceDim = AMOLED_DARK_BACKGROUND,
    surfaceBright = AMOLED_DARK_BACKGROUND,
    surfaceContainerLowest = AMOLED_DARK_BACKGROUND,
    surfaceContainerLow = AMOLED_DARK_BACKGROUND,
    surfaceContainer = AMOLED_DARK_BACKGROUND,
    surfaceContainerHigh = AMOLED_DARK_BACKGROUND,
    surfaceContainerHighest = AMOLED_DARK_BACKGROUND,
    surfaceVariant = AMOLED_DARK_BACKGROUND,
)

/** AMOLED：位置层里的大面积底色槽压纯黑。用户气泡、文字、边框保留，维持可读性与功能性区分。 */
private fun UiSlots.amoledSlots(): UiSlots = copy(
    pageBackground = AMOLED_DARK_BACKGROUND,
    topBar = AMOLED_DARK_BACKGROUND,
    card = AMOLED_DARK_BACKGROUND,
    listItem = AMOLED_DARK_BACKGROUND,
    panel = AMOLED_DARK_BACKGROUND,
    inputBar = AMOLED_DARK_BACKGROUND,
    aiBubble = AMOLED_DARK_BACKGROUND,
)

/** 关闭动效时用的 MotionScheme：所有 spec 都是 snap，动画一帧到位 */
private val SnapMotionScheme = object : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

@Serializable
enum class ColorMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Composable
fun RikkahubTheme(
    colorMode: ColorMode = rememberCurrentColorMode(),
    /**
     * 内容是外面量出来的尺寸（悬浮窗）：不铺满父容器，也不盖取色层。
     *
     * 铺满的话，wrap_content 的系统窗口会被撑到整块屏幕——窗口矩形就是触摸区，
     * 窗口外的滑动和点击都会先落到浮窗身上，下面的应用再也收不到。
     */
    wrapContent: Boolean = false,
    /** 嵌套主题（终端页之类）已经在外层缩放过一次，这里再乘一次会叠乘 */
    applyUiScale: Boolean = true,
    content: @Composable () -> Unit
) {
    val settings by rememberUserSettingsState()

    val darkTheme = when (colorMode) {
        ColorMode.SYSTEM -> isSystemInDarkTheme()
        ColorMode.LIGHT -> false
        ColorMode.DARK -> true
    }
    val amoledDarkMode by rememberAmoledDarkMode()

    // 调色板：深浅两套各自生成；没启用时返回 null，继续用主题设置里的配色
    val paletteScheme = remember(settings.palette, darkTheme) {
        settings.palette.generateColorScheme(darkTheme)
    }

    val colorScheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        paletteScheme != null -> paletteScheme
        else -> {
            val theme = findThemeById(settings.themeId, settings.customThemes)
                ?: findPresetTheme(settings.themeId)
            theme.getColorScheme(dark = darkTheme)
        }
    }
    // 全宽组件：直角 + 组件贴父容器边，由下面的 CompositionLocal 和 shapes 一起提供
    val fullWidthComponents = settings.displaySetting.fullWidthComponents
    // 界面风格：Miuix 模式下整棵树套一层 MiuixTheme，骨架组件各自走 Miuix 实现
    val uiStyle = settings.displaySetting.uiStyle
    val colorSchemeConverted = remember(darkTheme, amoledDarkMode, colorScheme) {
        if (darkTheme && amoledDarkMode) {
            colorScheme.amoledSurface()
        } else {
            colorScheme
        }
    }
    // 位置层：调色板的位置覆盖
    val uiSlots = remember(settings.palette, darkTheme, amoledDarkMode, colorSchemeConverted) {
        val base = settings.palette.uiSlots(darkTheme, colorSchemeConverted)
        // AMOLED 是显示级偏好，压黑优先级高于调色板的位置覆盖（底色槽）
        if (darkTheme && amoledDarkMode) base.amoledSlots() else base
    }
    // 全局字体：聊天字体可选应用到整个应用。
    // 注意：这里/下面的 Typography 是同包顶层 val（Type.kt），不是类，
    // 勿在此文件加 import androidx.compose.material3.Typography（会改为解析成类导致编译错误）。
    val chatFontFamily = rememberChatFontFamily(settings.displaySetting)
    val typography = remember(settings.displaySetting.fontApplyGlobal, chatFontFamily) {
        if (settings.displaySetting.fontApplyGlobal) Typography.withFontFamily(chatFontFamily) else Typography
    }
    val extendColors = if (darkTheme) ExtendDarkColors else ExtendLightColors
    val effectiveDark = darkTheme

    // 从 ImageToolbox 端口过来的组件（Enhanced 弹窗 / 抽屉）读这一份设置视图。
    // 深浅色与 AMOLED 用上面已经算好的结果传进去，桥接里不再重读一次 SharedPreferences；
    // 其余字段要么映射到本项目的设置，要么给那边的默认值，逐条写在 ToolboxSettingsBridge 里
    val toolboxSettings = remember(settings, effectiveDark, amoledDarkMode) {
        ToolboxSettingsBridge(
            settings = settings,
            darkTheme = effectiveDark,
            amoledDark = amoledDarkMode,
        )
    }

    // 更新状态栏图标颜色
    val view = LocalView.current
    val activity = view.context.getActivity()
    // 浮窗可能使用 Application Context，没有可更新系统栏的 Activity。
    if (!view.isInEditMode && activity != null) {
        DisposableEffect(view, activity, effectiveDark) {
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, view)
            val previousLightStatusBars = controller.isAppearanceLightStatusBars
            val previousLightNavigationBars = controller.isAppearanceLightNavigationBars
            controller.apply {
                isAppearanceLightStatusBars = !effectiveDark
                isAppearanceLightNavigationBars = !effectiveDark
            }
            onDispose {
                // 嵌套主题退出时恢复原有系统栏图标颜色。
                controller.isAppearanceLightStatusBars = previousLightStatusBars
                controller.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
        }
    }

    // 界面缩放：整棵 UI 用放大后的密度绘制，dp 和 sp 一起变；1.0 时保持原样
    val baseDensity = LocalDensity.current
    val uiScale = if (applyUiScale) settings.displaySetting.uiScale.coerceIn(0.5f, 2f) else 1f
    val scaledDensity = remember(baseDensity, uiScale) {
        if (uiScale == 1f) baseDensity else Density(baseDensity.density * uiScale, baseDensity.fontScale)
    }

    // 触感强度：SYSTEM 档返回 null，此时不接管 LocalHapticFeedback，沿用 Compose 默认实现
    val systemHaptics = LocalHapticFeedback.current
    val enhancedHaptics = rememberEnhancedHapticFeedback(settings.displaySetting.hapticsStrength)

    // 轮廓：形状类型与边角大小都从显示设置来，全宽模式仍然一律直角
    val cornerStyle = settings.displaySetting.cornerStyle
    val cornerScale = settings.displaySetting.cornerScale.coerceIn(0f, 2f)
    // 没有外层主题时 MaterialTheme.shapes 就是 M3 默认值，拿它当基准逐角换形状，
    // 五档半径不用在这里重写一遍（改了会和 M3 的 scale 脱节）
    val baseShapes = MaterialTheme.shapes
    val styledShapes = remember(baseShapes, cornerStyle, cornerScale) {
        Shapes(
            extraSmall = styledShape(cornerStyle, baseShapes.extraSmall.topStart, baseShapes.extraSmall.topEnd, baseShapes.extraSmall.bottomEnd, baseShapes.extraSmall.bottomStart, cornerScale),
            small = styledShape(cornerStyle, baseShapes.small.topStart, baseShapes.small.topEnd, baseShapes.small.bottomEnd, baseShapes.small.bottomStart, cornerScale),
            medium = styledShape(cornerStyle, baseShapes.medium.topStart, baseShapes.medium.topEnd, baseShapes.medium.bottomEnd, baseShapes.medium.bottomStart, cornerScale),
            large = styledShape(cornerStyle, baseShapes.large.topStart, baseShapes.large.topEnd, baseShapes.large.bottomEnd, baseShapes.large.bottomStart, cornerScale),
            extraLarge = styledShape(cornerStyle, baseShapes.extraLarge.topStart, baseShapes.extraLarge.topEnd, baseShapes.extraLarge.bottomEnd, baseShapes.extraLarge.bottomStart, cornerScale),
        )
    }

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalHapticFeedback provides (enhancedHaptics ?: systemHaptics),
        LocalCornerStyle provides cornerStyle,
        LocalCornerScale provides cornerScale,
        LocalCornerThrottle provides settings.displaySetting.cornerAnimThrottle.coerceIn(0, 2000),
        LocalSwitchType provides settings.displaySetting.switchType,
        LocalSliderType provides settings.displaySetting.sliderType,
        // 端口组件的设置视图（形状类型、开关/滑块样式、抽屉嵌套滚动…）：引擎只认这一个入口
        LocalToolboxSettings provides toolboxSettings,
        LocalUiSlots provides uiSlots,
        LocalDarkMode provides effectiveDark,
        LocalExtendColors provides extendColors,
        LocalOverscrollFactory provides null,
        LocalFullWidthComponents provides fullWidthComponents,
        LocalUiStyle provides uiStyle,
        LocalMiuixPressFeedback provides settings.displaySetting.miuixPressFeedback,
        LocalMiuixCornerRadius provides (settings.displaySetting.miuixCornerRadius * cornerScale).dp,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorSchemeConverted,
            typography = typography,
            shapes = when {
                fullWidthComponents -> SquareShapes
                // 全部是默认圆角时直接用基准那份，省掉一次五档重建
                cornerStyle == CornerStyle.ROUNDED && cornerScale == 1f -> baseShapes
                else -> styledShapes
            },
            content = {
                // 弹窗的进出场跟页面转场共用一档速度：用户在设置里调快以后，
                // 不该只有页面收住了、弹窗还按默认时长慢慢滑
                CompositionLocalProvider(
                    // 这一档原本只在 RouteActivity 里提供，而那份在 RikkahubTheme 内部：
                    // 下面两行在这里求值，读到的永远是默认的 SYSTEM，弹窗曲线便不跟设置走。
                    // 提到主题这一层，页面转场、菜单、弹窗才都读到同一档。
                    LocalAnimationSpeed provides if (settings.displaySetting.disableAnimations) {
                        AnimationSpeed.SYSTEM
                    } else {
                        settings.displaySetting.animationSpeed
                    },
                    // 进出同一档：退场不再取更紧的快档，两端同时长同曲线
                    LocalDialogEnterSpec provides rememberSpatialSpec<Float>(),
                    LocalDialogExitSpec provides rememberSpatialSpec<Float>(),
                    // 动态模糊的参数也在这一层下发：滚动、收放、入场都读同一份，
                    // 关掉动效时连同模糊一起停掉，不用每个效果自己判断
                    LocalMotionBlur provides if (settings.displaySetting.disableAnimations) {
                        settings.displaySetting.motionBlur.copy(enabled = false)
                    } else {
                        settings.displaySetting.motionBlur
                    },
                    // 手写的位移动画（侧边栏、菜单这类不走 MotionScheme 的）在关闭动效时也要停，
                    // 单独下发开关：档位那个 local 把关闭压成了 SYSTEM，读不出区别
                    LocalAnimationsEnabled provides !settings.displaySetting.disableAnimations,
                ) {
                    val body: @Composable () -> Unit = {
                        if (wrapContent) {
                            content()
                        } else {
                            // 编辑模式：全屏盖一层取色器，点哪算哪
                            Box(modifier = Modifier.fillMaxSize()) {
                                content()
                                ColorProbeOverlay()
                            }
                        }
                    }
                    // Miuix 模式下整棵树外面套一层 MiuixTheme：组件拿得到 Miuix 的色板与字号，
                    // 水波纹和滚动回弹也换成 Miuix 的。没迁过去的 Material 页面照常渲染，
                    // 只是共用这一层 LocalIndication。
                    if (uiStyle == UiStyle.MIUIX) {
                        MiuixRootTheme(
                            dark = effectiveDark,
                            colorSource = settings.displaySetting.miuixColorSource,
                            palette = settings.displaySetting.miuixPalette,
                            overscroll = settings.displaySetting.miuixOverscroll,
                            content = body,
                        )
                    } else {
                        body()
                    }
                }
            },
            // 档位直接换成对应的 MotionScheme：material3 自己的动效（底部抽屉、菜单、
            // 对话框遮罩、拖拽吸附）只认这一个口子，不换成自定义 scheme 的话，
            // 「动画速度」调成快 / 缓只影响手写的位移动画
            motionScheme = if (settings.displaySetting.disableAnimations) {
                SnapMotionScheme
            } else {
                motionSchemeOf(settings.displaySetting.animationSpeed)
            }
        )
    }
}

val MaterialTheme.extendColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendColors.current
