package me.rerere.rikkahub.ui.components.richtext

import me.rerere.ui.charts.ChartScaleType
import me.rerere.ui.charts.ChartStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Revised `echarts` 围栏的原生预览：只验证"解析成什么"和"什么时候拒绝"。
 *
 * 绘制交给现有的 ChartCard，不在这里重复断言像素。拒绝的用例比通过的用例更重要 ——
 * 子集之外的配置必须落回原始代码块，不能让用户看到一张读错的图。
 */
class RevisedChartBlockTest {

    private fun decline(language: String, code: String, streaming: Boolean = false) =
        (parseRevisedChartSpec(language = language, code = code, streaming = streaming)
            as? RevisedChartResult.Declined)?.reason
            ?: error("expected decline for: $code")

    private fun supported(code: String, language: String = "echarts") =
        (parseRevisedChartSpec(language = language, code = code) as? RevisedChartResult.Supported)?.spec
            ?: error("expected supported chart for: $code")

    // ---------- 围栏语言 ----------

    @Test
    fun `chart fence aliases are recognized`() {
        assertTrue(isRevisedChartFenceLanguage("echarts"))
        assertTrue(isRevisedChartFenceLanguage("ECHARTS"))
        assertTrue(isRevisedChartFenceLanguage("chart"))
        assertTrue(isRevisedChartFenceLanguage("language-echarts"))
        assertTrue(isRevisedChartFenceLanguage("{.chart}"))
        assertTrue(isRevisedChartFenceLanguage("echarts title=销量"))
    }

    @Test
    fun `plain json and script fences are not stolen`() {
        assertFalse(isRevisedChartFenceLanguage("json"))
        assertFalse(isRevisedChartFenceLanguage("javascript"))
        assertFalse(isRevisedChartFenceLanguage(""))
        assertFalse(isRevisedChartFenceLanguage("mermaid"))
        assertEquals(
            RevisedChartDecline.NotChartFence,
            decline("json", """{"series":[{"type":"bar","data":[1,2]}]}"""),
        )
        assertEquals(
            RevisedChartDecline.NotChartFence,
            decline("", """{"series":[{"type":"bar","data":[1,2]}]}"""),
        )
    }

    // ---------- 支持的子集 ----------

    @Test
    fun `line chart maps to chart spec`() {
        val chart = supported(
            """
            {
              "title": {"text": "月活跃用户"},
              "xAxis": {"type": "category", "name": "月份", "data": ["1月", "2月", "3月"]},
              "yAxis": {"type": "value", "name": "万人"},
              "series": [
                {"name": "Android", "type": "line", "data": [12.5, 14.1, 15.8]},
                {"name": "iOS", "type": "line", "data": [8.2, 8.9, 9.4]}
              ]
            }
            """.trimIndent(),
        )
        assertEquals(ChartStyle.Line, chart.style)
        assertEquals("月活跃用户", chart.title)
        assertEquals("月份", chart.xAxis.title)
        assertEquals(listOf("1月", "2月", "3月"), chart.xAxis.data)
        assertEquals("万人", chart.yAxis.title)
        assertEquals(2, chart.series.size)
        assertEquals("Android", chart.series[0].name)
        assertEquals(listOf(12.5, 14.1, 15.8), chart.series[0].values)
        assertTrue(chart.series[0].points.isEmpty())
        assertEquals(ChartScaleType.Linear, chart.yAxis.scale)
        assertEquals(3, chart.categoryCount)
        assertEquals("2月", chart.categoryLabel(1))
    }

    @Test
    fun `bar chart maps to chart spec`() {
        val chart = supported(
            """{"xAxis":{"data":["A","B"]},"yAxis":{},"series":[{"type":"bar","data":[2,4]}]}""",
        )
        assertEquals(ChartStyle.Bar, chart.style)
        assertEquals(listOf(2.0, 4.0), chart.series.single().values)
        assertNull(chart.title)
        assertNull(chart.series.single().name)
        assertNull(chart.series.single().color)
    }

    @Test
    fun `scatter chart maps pairs and carries numeric axes`() {
        val chart = supported(
            """
            {
              "xAxis": {"type": "value", "name": "身高 (cm)"},
              "yAxis": {"type": "value", "name": "体重 (kg)"},
              "series": [{"type": "scatter", "name": "样本", "data": [[160, 52], [175, 70]]}]
            }
            """.trimIndent(),
        )
        assertEquals(ChartStyle.Scatter, chart.style)
        val points = chart.series.single().points
        assertEquals(2, points.size)
        assertEquals(160.0, points[0].x, 0.0)
        assertEquals(52.0, points[0].y, 0.0)
        assertTrue(chart.series.single().values.isEmpty())
        assertEquals("身高 (cm)", chart.xAxis.title)
    }

    @Test
    fun `series type defaults to line like echarts`() {
        val chart = supported("""{"xAxis":{"data":["A"]},"series":[{"data":[3]}]}""")
        assertEquals(ChartStyle.Line, chart.style)
    }

    @Test
    fun `option wrapper is unwrapped`() {
        val chart = supported(
            """{"option":{"xAxis":{"data":["A","B"]},"series":[{"type":"bar","data":[1,2]}]},"backgroundColor":"#fff"}""",
        )
        assertEquals(ChartStyle.Bar, chart.style)
        assertEquals(2, chart.categoryCount)
    }

    @Test
    fun `itemStyle color is applied and only hex colors are accepted`() {
        val chart = supported("""{"series":[{"type":"bar","data":[1,2],"itemStyle":{"color":"#F80"}}]}""")
        assertNotNull(chart.series.single().color)

        assertEquals(
            RevisedChartDecline.InvalidSeriesStyle,
            decline("echarts", """{"series":[{"type":"bar","data":[1],"itemStyle":{"color":"red"}}]}"""),
        )
        assertEquals(
            RevisedChartDecline.InvalidSeriesStyle,
            decline("echarts", """{"series":[{"type":"bar","data":[1],"itemStyle":{"color":"#12345"}}]}"""),
        )
        assertEquals(
            RevisedChartDecline.InvalidSeriesStyle,
            decline("echarts", """{"series":[{"type":"bar","data":[1],"itemStyle":{"opacity":0.5}}]}"""),
        )
        assertEquals(
            RevisedChartDecline.InvalidSeriesStyle,
            decline("echarts", """{"series":[{"type":"bar","data":[1],"itemStyle":"#F80"}]}"""),
        )
    }

    @Test
    fun `decorative option keys are ignored without changing the data`() {
        val chart = supported(
            """
            {
              "tooltip": {"trigger": "axis"},
              "legend": {"data": ["销量"]},
              "grid": {"left": 8},
              "color": ["#111", "#222"],
              "animation": true,
              "title": {"text": "销量", "left": "center"},
              "xAxis": {"data": ["A", "B"], "axisLabel": {"rotate": 30}, "splitLine": {"show": false}},
              "yAxis": {"axisLine": {"show": false}},
              "series": [{"type": "bar", "name": "销量", "data": [1, 2]}]
            }
            """.trimIndent(),
        )
        assertEquals("销量", chart.title)
        assertEquals(listOf(1.0, 2.0), chart.series.single().values)
        assertEquals(listOf("A", "B"), chart.xAxis.data)
    }

    @Test
    fun `log axis and explicit range are carried over`() {
        val chart = supported(
            """{"yAxis":{"type":"log","min":1,"max":1000},"series":[{"type":"line","data":[1,10,100]}]}""",
        )
        assertEquals(ChartScaleType.Log, chart.yAxis.scale)
        assertEquals(1.0, chart.yAxis.min!!, 0.0)
        assertEquals(1000.0, chart.yAxis.max!!, 0.0)
    }

    @Test
    fun `missing axes fall back to defaults`() {
        val chart = supported("""{"series":[{"type":"bar","data":[5]}]}""")
        assertTrue(chart.xAxis.data.isEmpty())
        assertNull(chart.xAxis.title)
        assertEquals(1, chart.categoryCount)
        assertEquals("1", chart.categoryLabel(0))
    }

    @Test
    fun `whitespace and bom are tolerated`() {
        val chart = supported("\uFEFF\n  {\"series\":[{\"type\":\"bar\",\"data\":[1]}]}  \n")
        assertEquals(ChartStyle.Bar, chart.style)
    }

    // ---------- 拒绝：脚本、超限、子集之外 ----------

    @Test
    fun `streaming source never renders`() {
        val code = """{"series":[{"type":"bar","data":[1,2]}]}"""
        assertEquals(RevisedChartDecline.Streaming, decline("echarts", code, streaming = true))
    }

    @Test
    fun `oversized source is rejected`() {
        val code = "{\"series\":[{\"type\":\"bar\",\"data\":[" +
            "1,".repeat(MAX_REVISED_CHART_SOURCE_CHARS) + "1]}]}"
        assertTrue(code.length > MAX_REVISED_CHART_SOURCE_CHARS)
        assertEquals(RevisedChartDecline.SourceTooLarge, decline("echarts", code))
    }

    @Test
    fun `javascript and loose json are rejected`() {
        assertEquals(
            RevisedChartDecline.NotJson,
            decline("echarts", "const option = {series:[{type:'bar',data:[1,2]}]};"),
        )
        assertEquals(
            RevisedChartDecline.NotJson,
            decline("echarts", "{series:[{type:'bar',data:[1,2]}]}"),
        )
        assertEquals(
            RevisedChartDecline.NotJson,
            decline("echarts", """{"series":[{"type":"bar","data":[1,2],}],}"""),
        )
        assertEquals(
            RevisedChartDecline.NotJson,
            decline("echarts", "// chart\n{\"series\":[{\"type\":\"bar\",\"data\":[1]}]}"),
        )
        assertEquals(RevisedChartDecline.NotJson, decline("echarts", "   "))
        assertEquals(RevisedChartDecline.NotJson, decline("echarts", """["series"]"""))
        assertEquals(
            RevisedChartDecline.NotJson,
            decline("echarts", """{"series":[{"type":"bar","data":[1]}]} while(true){}"""),
        )
    }

    @Test
    fun `deeply nested json is rejected before parsing`() {
        val deep = "{\"series\":[{\"type\":\"bar\",\"data\":[1],\"itemStyle\":" +
            "[".repeat(MAX_REVISED_CHART_JSON_DEPTH + 4) +
            "]".repeat(MAX_REVISED_CHART_JSON_DEPTH + 4) + "}]}"
        assertEquals(RevisedChartDecline.NotJson, decline("echarts", deep))
    }

    @Test
    fun `unsupported option keys are rejected`() {
        listOf(
            """{"dataset":{"source":[["A",1]]},"series":[{"type":"bar","data":[1]}]}""",
            """{"geo":{},"series":[{"type":"bar","data":[1]}]}""",
            """{"radar":{},"series":[{"type":"bar","data":[1]}]}""",
            """{"bmap":{},"series":[{"type":"bar","data":[1]}]}""",
        ).forEach { code ->
            assertEquals(code, RevisedChartDecline.UnsupportedOption, decline("echarts", code))
        }
        assertEquals(
            RevisedChartDecline.UnsupportedOption,
            decline("echarts", """{"option":{"series":[{"type":"bar","data":[1]}]},"series":[{"data":[1]}]}"""),
        )
        assertEquals(
            RevisedChartDecline.UnsupportedOption,
            decline("echarts", """{"title":[{"text":"A"}],"series":[{"type":"bar","data":[1]}]}"""),
        )
        val longTitle = "长".repeat(MAX_REVISED_CHART_LABEL_CHARS + 1)
        assertEquals(
            RevisedChartDecline.UnsupportedOption,
            decline("echarts", """{"title":{"text":"$longTitle"},"series":[{"type":"bar","data":[1]}]}"""),
        )
    }

    @Test
    fun `unsupported chart types are rejected`() {
        listOf("pie", "map", "radar", "graph", "heatmap", "candlestick", "gauge", "tree", "sankey")
            .forEach { type ->
                assertEquals(
                    type,
                    RevisedChartDecline.UnsupportedSeriesType,
                    decline("echarts", """{"series":[{"type":"$type","data":[1]}]}"""),
                )
            }
        assertEquals(
            RevisedChartDecline.MixedSeriesTypes,
            decline("echarts", """{"series":[{"type":"bar","data":[1]},{"type":"line","data":[2]}]}"""),
        )
    }

    @Test
    fun `unsupported series keys are rejected instead of silently dropped`() {
        listOf(
            "\"smooth\":true",
            "\"stack\":\"total\"",
            "\"areaStyle\":{}",
            "\"markLine\":{\"data\":[{\"type\":\"average\"}]}",
            "\"label\":{\"show\":true}",
            "\"symbol\":\"circle\"",
            "\"encode\":{\"x\":\"a\"}",
            "\"yAxisIndex\":1",
        ).forEach { extra ->
            assertEquals(
                extra,
                RevisedChartDecline.UnsupportedSeriesData,
                decline("echarts", "{\"series\":[{\"type\":\"bar\",\"data\":[1],$extra}]}"),
            )
        }
    }

    @Test
    fun `series data must be plain numbers for line and bar`() {
        listOf(
            """{"series":[{"type":"bar","data":[1,null,3]}]}""",
            """{"series":[{"type":"bar","data":[1,{"value":2}]}]}""",
            """{"series":[{"type":"bar","data":[[1,2]]}]}""",
            """{"series":[{"type":"bar","data":["2"]}]}""",
            """{"series":[{"type":"bar","data":[1],"name":5}]}""",
            """{"series":[{"type":"bar"}]}""",
            """{"series":["bar"]}""",
        ).forEach { code ->
            assertEquals(code, RevisedChartDecline.UnsupportedSeriesData, decline("echarts", code))
        }
    }

    @Test
    fun `scatter points must be two dimensional numbers`() {
        listOf(
            """{"series":[{"type":"scatter","data":[[1,2,3]]}]}""",
            """{"series":[{"type":"scatter","data":[[1]]}]}""",
            """{"series":[{"type":"scatter","data":[[1,"2"]]}]}""",
            """{"series":[{"type":"scatter","data":[{"x":1,"y":2}]}]}""",
            """{"series":[{"type":"scatter","data":[1,2]}]}""",
        ).forEach { code ->
            assertEquals(code, RevisedChartDecline.UnsupportedSeriesData, decline("echarts", code))
        }
    }

    @Test
    fun `series and point counts are bounded`() {
        val tooManySeries = buildString {
            append("""{"series":[""")
            repeat(MAX_REVISED_CHART_SERIES + 1) { index ->
                if (index > 0) append(',')
                append("""{"type":"line","data":[1]}""")
            }
            append("]}")
        }
        assertEquals(RevisedChartDecline.TooManySeries, decline("echarts", tooManySeries))

        val exactlyMax = buildString {
            append("""{"series":[""")
            repeat(MAX_REVISED_CHART_SERIES) { index ->
                if (index > 0) append(',')
                append("""{"type":"line","data":[1]}""")
            }
            append("]}")
        }
        assertEquals(MAX_REVISED_CHART_SERIES, supported(exactlyMax).series.size)

        val tooManyValues = """{"series":[{"type":"line","data":[""" +
            "1,".repeat(MAX_REVISED_CHART_VALUES) + "1]}]}"
        assertEquals(RevisedChartDecline.TooManyPoints, decline("echarts", tooManyValues))

        assertEquals(RevisedChartDecline.EmptySeries, decline("echarts", """{"series":[]}"""))
        assertEquals(
            RevisedChartDecline.EmptySeries,
            decline("echarts", """{"series":[{"type":"bar","data":[]}]}"""),
        )
        assertEquals(RevisedChartDecline.MissingSeries, decline("echarts", """{"xAxis":{"data":["A"]}}"""))
        assertEquals(
            RevisedChartDecline.MissingSeries,
            decline("echarts", """{"series":{"type":"bar","data":[1]}}"""),
        )
    }

    @Test
    fun `category labels are bounded and validated`() {
        val tooMany = buildString {
            append("""{"xAxis":{"data":[""")
            repeat(MAX_REVISED_CHART_CATEGORIES + 1) { index ->
                if (index > 0) append(',')
                append("\"L$index\"")
            }
            append("""]},"series":[{"type":"bar","data":[""")
            repeat(MAX_REVISED_CHART_CATEGORIES + 1) { index ->
                if (index > 0) append(',')
                append("1")
            }
            append("]}]}")
        }
        assertEquals(RevisedChartDecline.TooManyCategories, decline("echarts", tooMany))

        assertEquals(
            RevisedChartDecline.UnsupportedAxisData,
            decline("echarts", """{"xAxis":{"data":"A,B"},"series":[{"type":"bar","data":[1]}]}"""),
        )
        assertEquals(
            RevisedChartDecline.UnsupportedAxisData,
            decline("echarts", """{"yAxis":{"data":["A"]},"series":[{"type":"bar","data":[1]}]}"""),
        )
        assertEquals(
            RevisedChartDecline.UnsupportedAxisData,
            decline("echarts", """{"xAxis":{"data":["A"]},"series":[{"type":"scatter","data":[[1,2]]}]}"""),
        )
        assertEquals(
            RevisedChartDecline.UnsupportedAxisData,
            decline("echarts", """{"xAxis":{"data":[{"name":"A"}]},"series":[{"type":"bar","data":[1]}]}"""),
        )
        assertEquals(
            RevisedChartDecline.CategoryCountMismatch,
            decline("echarts", """{"xAxis":{"data":["A"]},"series":[{"type":"bar","data":[1,2]}]}"""),
        )
    }

    @Test
    fun `unsupported axis shapes are rejected`() {
        assertEquals(
            RevisedChartDecline.UnsupportedAxis,
            decline(
                "echarts",
                """{"xAxis":{"type":"category","data":["A"],"axisLabel":{"formatter":"{value} 月"}},"series":[{"type":"bar","data":[1]}]}""",
            ),
        )
        assertEquals(
            RevisedChartDecline.UnsupportedAxis,
            decline("echarts", """{"yAxis":{"type":"time"},"series":[{"type":"line","data":[1]}]}"""),
        )
        assertEquals(
            RevisedChartDecline.UnsupportedAxis,
            decline("echarts", """{"xAxis":{"type":"value"},"series":[{"type":"bar","data":[1]}]}"""),
        )
        assertEquals(
            RevisedChartDecline.UnsupportedAxis,
            decline("echarts", """{"yAxis":{"type":"category"},"series":[{"type":"line","data":[1]}]}"""),
        )
        assertEquals(
            RevisedChartDecline.UnsupportedAxis,
            decline("echarts", """{"yAxis":"value","series":[{"type":"line","data":[1]}]}"""),
        )
        assertEquals(
            RevisedChartDecline.UnsupportedAxis,
            decline("echarts", """{"xAxis":[{"data":["A"]}],"series":[{"type":"bar","data":[1]}]}"""),
        )
    }

    @Test
    fun `axis ranges follow the chart_display rules`() {
        assertEquals(
            RevisedChartDecline.InvalidAxisRange,
            decline("echarts", """{"yAxis":{"min":5,"max":5},"series":[{"type":"line","data":[1,5]}]}"""),
        )
        assertEquals(
            RevisedChartDecline.InvalidAxisRange,
            decline("echarts", """{"yAxis":{"min":"0"},"series":[{"type":"line","data":[1,5]}]}"""),
        )
        assertEquals(
            RevisedChartDecline.InvalidAxisRange,
            decline("echarts", """{"yAxis":{"min":0},"series":[{"type":"bar","data":[1,5]}]}"""),
        )
        assertEquals(
            RevisedChartDecline.InvalidAxisRange,
            decline("echarts", """{"xAxis":{"min":0},"series":[{"type":"line","data":[1,5]}]}"""),
        )
        assertEquals(
            RevisedChartDecline.InvalidAxisRange,
            decline("echarts", """{"yAxis":{"type":"log","min":0},"series":[{"type":"line","data":[1,5]}]}"""),
        )
    }

    @Test
    fun `log axes reject non positive values`() {
        assertEquals(
            RevisedChartDecline.LogAxisNonPositive,
            decline("echarts", """{"yAxis":{"type":"log"},"series":[{"type":"line","data":[0,10]}]}"""),
        )
        assertEquals(
            RevisedChartDecline.LogAxisNonPositive,
            decline("echarts", """{"xAxis":{"type":"log"},"series":[{"type":"scatter","data":[[0,1]]}]}"""),
        )
        assertEquals(
            RevisedChartDecline.LogAxisNonPositive,
            decline("echarts", """{"yAxis":{"type":"log"},"series":[{"type":"scatter","data":[[1,-2]]}]}"""),
        )
    }

    @Test
    fun `decline never throws and never carries a spec`() {
        val result = parseRevisedChartSpec("echarts", "{not json")
        assertTrue(result is RevisedChartResult.Declined)
        assertNull(result.specOrNull)
        assertNotNull(
            parseRevisedChartSpec("echarts", """{"series":[{"type":"bar","data":[1]}]}""").specOrNull,
        )
    }
}
