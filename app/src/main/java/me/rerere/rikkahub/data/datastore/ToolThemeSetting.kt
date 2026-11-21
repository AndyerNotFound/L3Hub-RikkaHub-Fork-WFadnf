package me.rerere.rikkahub.data.datastore

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.t8rin.dynamic.theme.ColorBlindType
import com.t8rin.dynamic.theme.ColorTuple
import com.t8rin.dynamic.theme.PaletteStyle as ToolboxPaletteStyle
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * 主题引擎（`com.t8rin.dynamic.theme`）的设置。
 *
 * 单独挂一个类，不塞进 [DisplaySetting]：那一份是「界面怎么排」，这一份是「颜色和描边怎么算」，
 * 两件事混在一起以后要拆更费劲。整份设置按 JSON 存取（[SETTINGS_FIELDS] 里一行），
 * 新增字段带默认值即可，旧备份里没有这一项时读成默认值。
 *
 * 默认值刻意取引擎的「原样」档（TonalSpot / 对比度 0 / 不反色 / 无矫正 / 无主色覆盖），
 * 这样刚升级上来的人看到的配色和接引擎之前是同一套算法的同一个结果。
 */
@Serializable
data class ToolThemeSetting(
    /** 自定义主色等种子色；null 表示从 preset/custom 主题反推 */
    val appColorTuple: ThemeColorTuple? = null,
    /** 配色风格：引擎的九档 Material 配色算法 */
    @Serializable(with = ToolboxPaletteStyleSerializer::class)
    val paletteStyle: ToolboxPaletteStyle = ToolboxPaletteStyle.TonalSpot,
    /** 对比度 0.0–1.0，0 是引擎的默认对比度 */
    val themeContrastLevel: Double = 0.0,
    /** 色盲矫正；null 表示不矫正 */
    @Serializable(with = ColorBlindTypeSerializer::class)
    val colorBlindType: ColorBlindType? = null,
    /** 反色：把整套配色逐角色取反（引擎只在非动态取色时生效） */
    val isInvertThemeColors: Boolean = false,
    /**
     * 图标形状的键，见 [me.rerere.rikkahub.ui.theme.ToolboxIconShapes]。
     *
     * 引擎的 IconShape 握着一个 Compose Shape，不能直接序列化，这里只存位置键；null 表示不带形状容器。
     */
    val iconShapeName: String? = null,
    /** 组件描边宽度(dp)。引擎用它画按钮/卡片的描边，0 表示不描边 */
    val uiBorderWidth: Float = 0f,
    val drawContainerShadows: Boolean = false,
    val drawAppBarShadows: Boolean = false,
    val drawButtonShadows: Boolean = false,
    val drawFabShadows: Boolean = false,
    val drawSwitchShadows: Boolean = false,
    val drawSliderShadows: Boolean = false,
    /** 抽屉拖拽条的宽度(dp) */
    val dragHandleWidth: Float = 12f,
) {
    val uiBorderWidthDp: Dp get() = uiBorderWidth.dp

    val dragHandleWidthDp: Dp get() = dragHandleWidth.dp
}

/**
 * [ColorTuple] 的可序列化镜像。
 *
 * ColorTuple 里握的是 Compose 的 Color（inline value class + 色彩空间），序列化不了，
 * 所以存 ARGB 的 Long（和 [me.rerere.rikkahub.ui.theme.argbLong] 同一套约定）。
 */
@Serializable
data class ThemeColorTuple(
    val primary: Long,
    val secondary: Long? = null,
    val tertiary: Long? = null,
    val surface: Long? = null,
    val neutralVariant: Long? = null,
    val error: Long? = null,
) {
    fun toColorTuple(): ColorTuple = ColorTuple(
        primary = Color(primary.toInt()),
        secondary = secondary?.let { Color(it.toInt()) },
        tertiary = tertiary?.let { Color(it.toInt()) },
        surface = surface?.let { Color(it.toInt()) },
        neutralVariant = neutralVariant?.let { Color(it.toInt()) },
        error = error?.let { Color(it.toInt()) },
    )

    companion object {
        fun of(tuple: ColorTuple): ThemeColorTuple = ThemeColorTuple(
            primary = tuple.primary.argbLong(),
            secondary = tuple.secondary?.argbLong(),
            tertiary = tuple.tertiary?.argbLong(),
            surface = tuple.surface?.argbLong(),
            neutralVariant = tuple.neutralVariant?.argbLong(),
            error = tuple.error?.argbLong(),
        )

        private fun Color.argbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL
    }
}

/**
 * [ToolboxPaletteStyle] 按名字存取。
 *
 * 引擎那个枚举没有 `@Serializable`（也不能为了这个改引擎的公开类型），自己写一份按名字走的序列化，
 * 顺便把认不出的名字读成默认档——以后引擎加了新档、或者设置从别的版本导进来，都不会让整份设置读不出来。
 */
object ToolboxPaletteStyleSerializer : KSerializer<ToolboxPaletteStyle> {
    override val descriptor = PrimitiveSerialDescriptor(
        "me.rerere.rikkahub.data.datastore.ToolboxPaletteStyle",
        PrimitiveKind.STRING,
    )

    override fun serialize(encoder: Encoder, value: ToolboxPaletteStyle) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): ToolboxPaletteStyle {
        val name = decoder.decodeString()
        return ToolboxPaletteStyle.entries.firstOrNull { it.name == name } ?: ToolboxPaletteStyle.TonalSpot
    }
}

/** 同 [ToolboxPaletteStyleSerializer]，认不出的名字读成「不矫正」。 */
object ColorBlindTypeSerializer : KSerializer<ColorBlindType> {
    override val descriptor = PrimitiveSerialDescriptor(
        "me.rerere.rikkahub.data.datastore.ColorBlindType",
        PrimitiveKind.STRING,
    )

    override fun serialize(encoder: Encoder, value: ColorBlindType) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): ColorBlindType {
        val name = decoder.decodeString()
        return ColorBlindType.entries.firstOrNull { it.name == name } ?: ColorBlindType.Protanomaly
    }
}
