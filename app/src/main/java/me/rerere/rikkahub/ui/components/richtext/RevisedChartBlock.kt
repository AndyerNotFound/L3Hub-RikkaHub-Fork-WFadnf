package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import me.rerere.ui.charts.ChartAxis
import me.rerere.ui.charts.ChartCard
import me.rerere.ui.charts.ChartPoint
import me.rerere.ui.charts.ChartScaleType
import me.rerere.ui.charts.ChartSeries
import me.rerere.ui.charts.ChartSpec
import me.rerere.ui.charts.ChartStyle

/**
 * Revised 的 `echarts` 围栏在 L3Hub 里的原生预览。
 *
 * 只认一个安全、有界的声明式子集：严格 JSON（kotlinx-serialization，不放宽、不接受 JS
 * 对象字面量、不接受 `const option = {...}` 这类脚本），只把 line / bar / scatter 三种
 * 图形映射到 [ChartSpec]，最终交给现有的 [ChartCard] 绘制。没有模型脚本、没有网络、没有
 * 文件 IO、没有代码加载器、没有新增依赖。
 *
 * 子集之外的一切配置（dataset、formatter、地图、多轴、堆叠、平滑、符号等）都返回
 * [RevisedChartDecline]，调用方应当继续用原来的原始代码块渲染 —— 宁可显示源码，也不要
 * 用一张读错的图去误导用户。
 *
 * 复制 / 下载 / 源码视图仍然由父级 [HighlightCodeBlock] 的头部提供：这里只替换代码块
 * 正文，不接管、也不吞掉原始源码。
 */

/** 单个 echarts 围栏允许的最大源码长度。 */
internal const val MAX_REVISED_CHART_SOURCE_CHARS = 32 * 1024

/** 系列数量上限，与 chart_display 工具一致。 */
internal const val MAX_REVISED_CHART_SERIES = 12

/** 每个系列的数据点上限，与 chart_display 工具一致。 */
internal const val MAX_REVISED_CHART_VALUES = 2000

/** 类目标签数量上限。 */
internal const val MAX_REVISED_CHART_CATEGORIES = 2000

/** 标题 / 轴名 / 系列名 / 类目标签的字符上限。 */
internal const val MAX_REVISED_CHART_LABEL_CHARS = 120

/** JSON 嵌套深度上限：解析前先挡住深嵌套，避免递归解析炸栈。 */
internal const val MAX_REVISED_CHART_JSON_DEPTH = 64

/** 调用方没有语言标签时使用的默认值。 */
internal const val DEFAULT_REVISED_CHART_LANGUAGE = "echarts"

/** 由 Revised `RendererCatalog` 的 echarts 条目带入的围栏语言别名。 */
internal val REVISED_CHART_FENCE_LANGUAGES: Set<String> = setOf("echarts", "chart")

/**
 * 是否为 echarts 围栏。归一化复用 [normalizeCodeFenceLanguage]，因此 `language-echarts`、
 * `{.echarts}`、`echarts json` 都算；泛型 `json` / `javascript` 不算 —— 这里不做内容嗅探，
 * 免得把普通 JSON 抢成图表。
 */
internal fun isRevisedChartFenceLanguage(language: String): Boolean =
    normalizeCodeFenceLanguage(language) in REVISED_CHART_FENCE_LANGUAGES

/** 拒绝渲染的具体原因；调用方回退到原始代码块，不必区分，测试与排查需要。 */
internal enum class RevisedChartDecline {
    /** 围栏不是 echarts / chart。 */
    NotChartFence,

    /** 消息还在流式输出，配置可能不完整。 */
    Streaming,

    /** 源码超过 [MAX_REVISED_CHART_SOURCE_CHARS]。 */
    SourceTooLarge,

    /** 不是严格 JSON（含 JS 对象字面量、脚本、注释、尾逗号、过深嵌套）。 */
    NotJson,

    /** 顶层出现子集之外的语义字段，例如 dataset / geo / radar。 */
    UnsupportedOption,

    /** 缺少 series 或 series 不是数组。 */
    MissingSeries,

    /** 图形类型不是 line / bar / scatter。 */
    UnsupportedSeriesType,

    /** 多个系列的图形类型不一致。 */
    MixedSeriesTypes,

    /** 系列数量超过 [MAX_REVISED_CHART_SERIES]。 */
    TooManySeries,

    /** 系列里的字段或数据形状不在子集内（含 null 值、对象值、三维点）。 */
    UnsupportedSeriesData,

    /** 单个系列的数据点超过 [MAX_REVISED_CHART_VALUES]。 */
    TooManyPoints,

    /** 系列或数据为空。 */
    EmptySeries,

    /** 轴不是对象，或出现子集之外的轴字段。 */
    UnsupportedAxis,

    /** 类目标签不合法，或用在不该用的轴上。 */
    UnsupportedAxisData,

    /** 类目标签数量超过 [MAX_REVISED_CHART_CATEGORIES]。 */
    TooManyCategories,

    /** min / max 不是数字、区间反向，或该图形不支持范围限制。 */
    InvalidAxisRange,

    /** 对数轴上出现了非正数。 */
    LogAxisNonPositive,

    /** 系列数据长度与类目标签数量不一致。 */
    CategoryCountMismatch,

    /** itemStyle 只接受 `{color: "#rgb"|"#rrggbb"}`。 */
    InvalidSeriesStyle,

    /** 兜底的解析异常，绝不把异常抛进组合。 */
    Unexpected,
}

/** 解析结果：可渲染的 [ChartSpec]，或一个明确的拒绝原因。 */
internal sealed interface RevisedChartResult {
    val specOrNull: ChartSpec?

    data class Supported(val spec: ChartSpec) : RevisedChartResult {
        override val specOrNull: ChartSpec get() = spec
    }

    data class Declined(val reason: RevisedChartDecline) : RevisedChartResult {
        override val specOrNull: ChartSpec? get() = null
    }
}

/**
 * 把一段 echarts 围栏解析成 [ChartSpec]。
 *
 * 纯函数、无副作用：不发网络、不读文件、不加载任何东西、不执行任何脚本。
 *
 * @param language 围栏声明的语言，会先做 [normalizeCodeFenceLanguage] 归一化
 * @param code 围栏里的原始文本，复制 / 下载仍用原文
 * @param streaming 消息是否还在流式输出，为 true 时一律拒绝
 */
internal fun parseRevisedChartSpec(
    language: String,
    code: String,
    streaming: Boolean = false,
): RevisedChartResult {
    if (!isRevisedChartFenceLanguage(language)) {
        return RevisedChartResult.Declined(RevisedChartDecline.NotChartFence)
    }
    if (streaming) return RevisedChartResult.Declined(RevisedChartDecline.Streaming)
    if (code.length > MAX_REVISED_CHART_SOURCE_CHARS) {
        return RevisedChartResult.Declined(RevisedChartDecline.SourceTooLarge)
    }
    val text = code.trim().removePrefix("\uFEFF").trim()
    if (text.isEmpty()) return RevisedChartResult.Declined(RevisedChartDecline.NotJson)
    if (maxJsonDepth(text) > MAX_REVISED_CHART_JSON_DEPTH) {
        return RevisedChartResult.Declined(RevisedChartDecline.NotJson)
    }
    // 严格 JSON：默认 Json 不放宽、不允许尾逗号、不接受 JS 字面量
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
        ?: return RevisedChartResult.Declined(RevisedChartDecline.NotJson)
    val option = selectOption(root)
        ?: return RevisedChartResult.Declined(RevisedChartDecline.UnsupportedOption)
    return try {
        RevisedChartResult.Supported(buildChartSpec(option))
    } catch (declined: DeclineException) {
        RevisedChartResult.Declined(declined.reason)
    } catch (_: Exception) {
        RevisedChartResult.Declined(RevisedChartDecline.Unexpected)
    }
}

/**
 * 代码块正文的原生图表预览。
 *
 * 解析成功时绘制 [ChartCard]；不成立时调用 [fallback]（传 null 就什么都不画）。调用方把
 * 原来那段原始代码块放进 fallback，就能在"子集之外"自动退回源码，不会出现读错的图。
 * 父级 [HighlightCodeBlock] 的语言标签、复制、下载与源码切换保持原样。
 *
 * @param code 围栏原文，复制 / 下载 / 源码视图都用它
 * @param language 围栏声明的语言
 * @param streaming 还在流式输出时一律走 fallback，避免半截 JSON 闪成错误的图
 * @param fallback 拒绝渲染时的兜底内容，通常是原来的代码块
 */
@Composable
internal fun RevisedChartBlock(
    code: String,
    modifier: Modifier = Modifier,
    language: String = DEFAULT_REVISED_CHART_LANGUAGE,
    streaming: Boolean = false,
    fallback: (@Composable () -> Unit)? = null,
) {
    val spec = remember(code, language, streaming) {
        parseRevisedChartSpec(language = language, code = code, streaming = streaming).specOrNull
    }
    if (spec == null) {
        // 子集之外一律回到原始代码块：宁可显示源码，也不画一张读错的图
        if (fallback != null) fallback()
        return
    }
    ChartCard(spec = spec, modifier = modifier)
}

private class DeclineException(val reason: RevisedChartDecline) : Exception()

private fun decline(reason: RevisedChartDecline): Nothing = throw DeclineException(reason)

/** 会影响图形的顶层字段。 */
private val OPTION_KEYS = setOf("title", "xAxis", "yAxis", "series")

/**
 * 纯装饰 / 交互字段：App 自己接管，出现即忽略。
 *
 * 它们不改变数据的含义（tooltip 是悬停提示、grid 是留白、animation 是动效），忽略不会
 * 让用户读到一张错的图。
 */
private val IGNORED_OPTION_KEYS = setOf(
    "tooltip", "legend", "grid", "animation", "animationThreshold",
    "animationDuration", "animationDurationUpdate", "animationEasing", "animationEasingUpdate",
    "backgroundColor", "color", "textStyle", "stateAnimation",
    "toolbox", "aria", "dataZoom", "visualMap", "graphic", "timeline", "media",
    "progressive", "progressiveThreshold", "hoverLayerThreshold", "useUTC", "darkMode",
    "blendMode", "axisPointer", "emphasis", "select",
)

/** 系列里允许的字段；其它（smooth / stack / symbol / label / markLine…）一律拒绝。 */
private val SERIES_KEYS = setOf("type", "name", "data", "itemStyle")

/** 轴里允许的字段；会改变读数含义的字段不在内。 */
private val AXIS_KEYS = setOf("data", "name", "min", "max", "type")

/** 轴上的纯排版字段，忽略不影响读数。 */
private val IGNORED_AXIS_KEYS = setOf(
    "show", "position", "offset", "z", "zlevel", "gridIndex", "nameLocation", "nameGap",
    "nameRotate", "nameTextStyle", "nameTruncate", "axisLine", "axisTick", "splitLine",
    "splitArea", "minorTick", "minorSplitLine", "axisLabel", "boundaryGap", "silent", "triggerEvent",
    "alignTicks", "animation", "animationDuration",
)

/** 标题里允许忽略的排版字段。 */
private val IGNORED_TITLE_KEYS = setOf(
    "text", "subtext", "textStyle", "subtextStyle", "left", "top", "right", "bottom",
    "show", "padding", "itemGap", "textAlign", "textVerticalAlign", "z", "zlevel",
    "link", "target", "borderColor", "borderWidth", "backgroundColor", "triggerEvent", "silent",
)

/**
 * axisLabel 只允许这些排版键。`formatter` / `rich` 之类会改写标签文字，让读数和模型本意
 * 对不上，出现即拒绝，交给原始代码块。
 */
private val AXIS_LABEL_KEYS = setOf(
    "show", "rotate", "margin", "color", "fontSize", "fontWeight", "fontStyle", "align",
    "verticalAlign", "hideOverlap", "interval", "overflow", "width", "lineHeight", "distance",
)

private val CHART_HEX_COLOR_REGEX = Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")

private val ChartStyle.echartsType: String
    get() = when (this) {
        ChartStyle.Line -> "line"
        ChartStyle.Bar -> "bar"
        ChartStyle.Scatter -> "scatter"
    }

/** 兼容 Revised 的 `{option: {...}}` 包装；其余顶层字段只允许纯装饰项。 */
private fun selectOption(root: JsonObject): JsonObject? {
    val wrapped = root["option"] as? JsonObject ?: return root
    if (root.keys.any { it != "option" && it !in IGNORED_OPTION_KEYS }) return null
    return wrapped
}

private fun buildChartSpec(option: JsonObject): ChartSpec {
    if (option.keys.any { it !in OPTION_KEYS && it !in IGNORED_OPTION_KEYS }) {
        decline(RevisedChartDecline.UnsupportedOption)
    }
    val seriesArray = option["series"] as? JsonArray
        ?: decline(RevisedChartDecline.MissingSeries)
    if (seriesArray.size > MAX_REVISED_CHART_SERIES) decline(RevisedChartDecline.TooManySeries)
    if (seriesArray.isEmpty()) decline(RevisedChartDecline.EmptySeries)

    val style = resolveStyle(seriesArray)
    // 先解析轴再解析数据：超限时优先报告"类目太多"，而不是把类目轴当成普通数据
    val xAxis = parseAxis(option["xAxis"], isXAxis = true, style = style)
    val yAxis = parseAxis(option["yAxis"], isXAxis = false, style = style)
    val series = parseSeries(seriesArray, style)
    validateCombination(style = style, xAxis = xAxis, yAxis = yAxis, series = series)

    return ChartSpec(
        style = style,
        title = parseTitle(option["title"]),
        xAxis = xAxis,
        yAxis = yAxis,
        series = series,
    )
}

/** ECharts 里省略 `type` 的系列默认是折线。 */
private fun resolveStyle(seriesArray: JsonArray): ChartStyle {
    val first = seriesArray.firstOrNull() as? JsonObject
        ?: decline(RevisedChartDecline.UnsupportedSeriesData)
    val rawType = first["type"]
    if (rawType != null && rawType !is JsonNull && first.stringValue("type") == null) {
        decline(RevisedChartDecline.UnsupportedSeriesType)
    }
    return when (first.stringValue("type")?.lowercase() ?: "line") {
        "line" -> ChartStyle.Line
        "bar" -> ChartStyle.Bar
        "scatter" -> ChartStyle.Scatter
        else -> decline(RevisedChartDecline.UnsupportedSeriesType)
    }
}

private fun parseSeries(seriesArray: JsonArray, style: ChartStyle): List<ChartSeries> =
    seriesArray.map { element ->
        val item = element as? JsonObject
            ?: decline(RevisedChartDecline.UnsupportedSeriesData)
        if (item.keys.any { it !in SERIES_KEYS }) {
            decline(RevisedChartDecline.UnsupportedSeriesData)
        }
        val rawType = item["type"]
        if (rawType != null && rawType !is JsonNull && item.stringValue("type") == null) {
            decline(RevisedChartDecline.UnsupportedSeriesType)
        }
        val type = item.stringValue("type")?.lowercase() ?: "line"
        if (type != style.echartsType) decline(RevisedChartDecline.MixedSeriesTypes)

        val name = item.optionalText("name", RevisedChartDecline.UnsupportedSeriesData)
        val color = parseSeriesColor(item["itemStyle"])
        val data = item["data"] as? JsonArray
            ?: decline(RevisedChartDecline.UnsupportedSeriesData)
        if (data.size > MAX_REVISED_CHART_VALUES) decline(RevisedChartDecline.TooManyPoints)
        if (data.isEmpty()) decline(RevisedChartDecline.EmptySeries)

        when (style) {
            ChartStyle.Scatter -> ChartSeries(
                name = name,
                color = color,
                points = data.map { parsePoint(it) },
            )

            else -> ChartSeries(
                name = name,
                color = color,
                values = data.map { parseValue(it) },
            )
        }
    }

private fun parseSeriesColor(element: JsonElement?): Color? {
    if (element == null || element is JsonNull) return null
    val itemStyle = element as? JsonObject
        ?: decline(RevisedChartDecline.InvalidSeriesStyle)
    if (itemStyle.keys.any { it != "color" }) decline(RevisedChartDecline.InvalidSeriesStyle)
    val text = itemStyle.stringValue("color")
        ?: decline(RevisedChartDecline.InvalidSeriesStyle)
    return parseHexColor(text) ?: decline(RevisedChartDecline.InvalidSeriesStyle)
}

/** line / bar 的一维值；null、字符串、对象、数组一律拒绝，避免错位后画出假趋势。 */
private fun parseValue(element: JsonElement): Double {
    val primitive = element as? JsonPrimitive
        ?: decline(RevisedChartDecline.UnsupportedSeriesData)
    if (primitive is JsonNull || primitive.isString) {
        decline(RevisedChartDecline.UnsupportedSeriesData)
    }
    val value = primitive.doubleOrNull
        ?: decline(RevisedChartDecline.UnsupportedSeriesData)
    if (!value.isFinite()) decline(RevisedChartDecline.UnsupportedSeriesData)
    return value
}

/** scatter 的 `[x, y]` 点；只接受二维数值。 */
private fun parsePoint(element: JsonElement): ChartPoint {
    val pair = element as? JsonArray
        ?: decline(RevisedChartDecline.UnsupportedSeriesData)
    if (pair.size != 2) decline(RevisedChartDecline.UnsupportedSeriesData)
    return ChartPoint(x = parseCoordinate(pair[0]), y = parseCoordinate(pair[1]))
}

private fun parseCoordinate(element: JsonElement): Double {
    val primitive = element as? JsonPrimitive
        ?: decline(RevisedChartDecline.UnsupportedSeriesData)
    if (primitive is JsonNull || primitive.isString) {
        decline(RevisedChartDecline.UnsupportedSeriesData)
    }
    val value = primitive.doubleOrNull
        ?: decline(RevisedChartDecline.UnsupportedSeriesData)
    if (!value.isFinite()) decline(RevisedChartDecline.UnsupportedSeriesData)
    return value
}

private fun parseTitle(element: JsonElement?): String? {
    if (element == null || element is JsonNull) return null
    val text = when (element) {
        is JsonPrimitive -> {
            // 标题是纯文本: 出现非字符串就按不支持的配置拒绝, 不做静默丢弃
            if (!element.isString) decline(RevisedChartDecline.UnsupportedOption)
            element.contentOrNull
        }

        is JsonObject -> {
            if (element.keys.any { it !in IGNORED_TITLE_KEYS }) {
                decline(RevisedChartDecline.UnsupportedOption)
            }
            element.stringValue("text")
        }

        else -> decline(RevisedChartDecline.UnsupportedOption)
    } ?: return null
    if (text.length > MAX_REVISED_CHART_LABEL_CHARS) {
        decline(RevisedChartDecline.UnsupportedOption)
    }
    return text.takeIf { it.isNotBlank() }
}

private fun parseAxis(element: JsonElement?, isXAxis: Boolean, style: ChartStyle): ChartAxis {
    if (element == null || element is JsonNull) return ChartAxis()
    val axis = element as? JsonObject
        ?: decline(RevisedChartDecline.UnsupportedAxis)
    if (axis.keys.any { it !in AXIS_KEYS && it !in IGNORED_AXIS_KEYS }) {
        decline(RevisedChartDecline.UnsupportedAxis)
    }
    // axisLabel 的排版键可以忽略, 但 formatter / rich 会改写标签文字, 一律拒绝
    val axisLabel = axis["axisLabel"]
    if (axisLabel != null && axisLabel !is JsonNull) {
        val label = axisLabel as? JsonObject
            ?: decline(RevisedChartDecline.UnsupportedAxis)
        if (label.keys.any { it !in AXIS_LABEL_KEYS }) decline(RevisedChartDecline.UnsupportedAxis)
    }

    val type = axis.stringValue("type")?.lowercase()
    if (axis.containsKey("type") && type == null) decline(RevisedChartDecline.UnsupportedAxis)
    // 散点图两轴都是数值轴；折线 / 柱状图的 X 轴是类目轴，Y 轴是数值轴
    val allowedTypes = when {
        style == ChartStyle.Scatter -> setOf("value", "log")
        isXAxis -> setOf("category")
        else -> setOf("value", "log")
    }
    if (type != null && type !in allowedTypes) decline(RevisedChartDecline.UnsupportedAxis)
    val log = type == "log"

    val name = axis.optionalText("name", RevisedChartDecline.UnsupportedAxis)

    val min = axis.numberValue("min")
    val max = axis.numberValue("max")
    if (axis.containsKey("min") && min == null) decline(RevisedChartDecline.InvalidAxisRange)
    if (axis.containsKey("max") && max == null) decline(RevisedChartDecline.InvalidAxisRange)
    if (min != null && max != null && min >= max) decline(RevisedChartDecline.InvalidAxisRange)
    if (log && min != null && min <= 0) decline(RevisedChartDecline.InvalidAxisRange)
    // 柱状图恒从 0 起, 折线 / 柱状图的 X 轴是类目轴, 都没有可映射的数值范围
    if (style == ChartStyle.Bar && (min != null || max != null)) {
        decline(RevisedChartDecline.InvalidAxisRange)
    }
    if (isXAxis && style != ChartStyle.Scatter && (min != null || max != null)) {
        decline(RevisedChartDecline.InvalidAxisRange)
    }

    val data: List<String> = when (val raw = axis["data"]) {
        null, is JsonNull -> emptyList()

        is JsonArray -> {
            if (!isXAxis || style == ChartStyle.Scatter) {
                decline(RevisedChartDecline.UnsupportedAxisData)
            }
            if (raw.size > MAX_REVISED_CHART_CATEGORIES) {
                decline(RevisedChartDecline.TooManyCategories)
            }
            raw.map { label ->
                val primitive = label as? JsonPrimitive
                    ?: decline(RevisedChartDecline.UnsupportedAxisData)
                if (primitive is JsonNull) decline(RevisedChartDecline.UnsupportedAxisData)
                val text = primitive.content
                if (text.length > MAX_REVISED_CHART_LABEL_CHARS) {
                    decline(RevisedChartDecline.UnsupportedAxisData)
                }
                text
            }
        }

        else -> decline(RevisedChartDecline.UnsupportedAxisData)
    }

    return ChartAxis(
        title = name,
        data = data,
        min = min,
        max = max,
        scale = if (log) ChartScaleType.Log else ChartScaleType.Linear,
    )
}

private fun validateCombination(
    style: ChartStyle,
    xAxis: ChartAxis,
    yAxis: ChartAxis,
    series: List<ChartSeries>,
) {
    if (series.isEmpty()) decline(RevisedChartDecline.EmptySeries)
    val yLog = yAxis.scale == ChartScaleType.Log
    val xLog = xAxis.scale == ChartScaleType.Log
    if (style == ChartStyle.Scatter) {
        if (xAxis.data.isNotEmpty() || yAxis.data.isNotEmpty()) {
            decline(RevisedChartDecline.UnsupportedAxisData)
        }
        val bad = series.any { item ->
            item.points.any { point -> (xLog && point.x <= 0) || (yLog && point.y <= 0) }
        }
        if (bad) decline(RevisedChartDecline.LogAxisNonPositive)
        return
    }
    if (yAxis.data.isNotEmpty()) decline(RevisedChartDecline.UnsupportedAxisData)
    if (yLog && series.any { item -> item.values.any { it <= 0 } }) {
        decline(RevisedChartDecline.LogAxisNonPositive)
    }
    if (xAxis.data.isNotEmpty() && series.any { it.values.size != xAxis.data.size }) {
        decline(RevisedChartDecline.CategoryCountMismatch)
    }
}

private fun JsonObject.stringValue(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

/** 可选文本字段：缺失 / null 视为没有，出现但不是字符串、或过长则按 [reason] 拒绝。 */
private fun JsonObject.optionalText(key: String, reason: RevisedChartDecline): String? {
    val raw = this[key] ?: return null
    if (raw is JsonNull) return null
    val text = stringValue(key) ?: decline(reason)
    if (text.length > MAX_REVISED_CHART_LABEL_CHARS) decline(reason)
    return text.takeIf { it.isNotBlank() }
}

private fun JsonObject.numberValue(key: String): Double? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    // 字符串 "0" 不算数字: 严格 JSON 里 min/max 必须是数值
    if (primitive is JsonNull || primitive.isString) return null
    return primitive.doubleOrNull?.takeIf { it.isFinite() }
}

/**
 * 先扫一遍括号深度：字符串与转义里的括号不算。
 *
 * 只为挡住深嵌套导致解析期炸栈，不做任何语法判断 —— 语法交给 kotlinx-serialization。
 */
private fun maxJsonDepth(text: String): Int {
    var depth = 0
    var deepest = 0
    var inString = false
    var escaped = false
    for (char in text) {
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            '{', '[' -> {
                depth++
                if (depth > deepest) deepest = depth
            }

            '}', ']' -> depth--
        }
    }
    return deepest
}

private fun parseHexColor(text: String): Color? {
    if (!CHART_HEX_COLOR_REGEX.matches(text)) return null
    val hex = text.drop(1).let { raw ->
        if (raw.length == 3) raw.map { "$it$it" }.joinToString("") else raw
    }
    return Color(0xFF000000 or hex.toLong(16))
}
