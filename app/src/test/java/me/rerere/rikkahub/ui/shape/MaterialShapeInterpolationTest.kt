package me.rerere.rikkahub.ui.shape

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Interpolatable
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.CornerStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `withInterpolatableCorners` 的 JVM 回归测试。
 *
 * 关心的只有一件事：M3 的动画器在进度中段调用 `Interpolatable.lerp`，共享扩展必须把各状态
 * 收进同一个可插值族，并让四个逻辑角的来源、次序、端点、夹取和 group key 都保持可预测。
 *
 * 全部断言只读取 [CornerBasedShape] 的角值（`CornerSize.toPx`）或经 [RecordingCornerShape]
 * 观察 `createOutline` 的入参，不构造任何 Android `Path`：Wavy / Scoop / Notch / Smooth /
 * Squircle 只被当作角值容器读取，不渲染轮廓，因此这份测试可以在无图形栈的 JVM 上跑。
 */
class MaterialShapeInterpolationTest {

    @Test
    fun `listItemShapes wraps six slots into one interpolatable family`() {
        val prototype = RecordingCornerShape()
        val base = ListItemShapes(
            shape = RoundedCornerShape(CornerSize(2f)),
            selectedShape = RoundedCornerShape(CornerSize(6f)),
            pressedShape = RectangleShape,
            focusedShape = styledShape(CornerStyle.SMOOTH, 8.dp),
            hoveredShape = RoundedCornerShape(CornerSize(14f)),
            draggedShape = styledShape(CornerStyle.NOTCH, 20.dp),
        )

        val wrapped = base.withInterpolatableCorners(prototype)
        assertNotSame("只要有一槽不是圆角，整组就必须换成逐角包装", base, wrapped)

        val corners = wrapped.slots().map { it.asCornerShape() }
        assertEquals(6, corners.size)
        // 六个槽位的 topStart 必须互不相同，否则“中间值而非二选一”无从判断
        assertEquals(6, corners.map { it.topStart.toPx(bigSquare, unitDensity) }.distinct().size)

        assertInterleaves(corners, "ListItemShapes")
    }

    @Test
    fun `toggleButtonShapes wraps three slots into one interpolatable family`() {
        val prototype = RecordingCornerShape()
        val base = ToggleButtonShapes(
            shape = CircleShape,
            pressedShape = styledShape(CornerStyle.SCOOP, 10.dp),
            checkedShape = RoundedCornerShape(CornerSize(16f)),
        )

        val wrapped = base.withInterpolatableCorners(prototype)
        assertNotSame("固定 CircleShape 与自定义按下形状必须在同一族", base, wrapped)

        val corners = listOf(wrapped.shape, wrapped.pressedShape, wrapped.checkedShape).map { it.asCornerShape() }
        assertEquals(3, corners.size)
        assertEquals(3, corners.map { it.topStart.toPx(bigSquare, unitDensity) }.distinct().size)

        assertInterleaves(corners, "ToggleButtonShapes")
    }

    @Test
    fun `styledShape exposes raw corner slots for every corner style without rendering`() {
        val expectedNames = listOf("ROUNDED", "CUT", "SQUIRCLE", "SMOOTH", "WAVY", "SCOOP", "NOTCH")
        val styles = CornerStyle.entries
        assertTrue("CornerStyle 应覆盖 7 种，实际 ${styles.size}", styles.size >= expectedNames.size)
        assertTrue(
            "CornerStyle 缺少 ${expectedNames - styles.map { it.name }}",
            styles.map { it.name }.containsAll(expectedNames),
        )

        val expectedType = mapOf(
            CornerStyle.ROUNDED to RoundedCornerShape::class,
            CornerStyle.CUT to CutCornerShape::class,
            CornerStyle.SMOOTH to ContinuousRoundedRectangle::class,
            CornerStyle.WAVY to WavyShape::class,
            CornerStyle.SCOOP to ScoopShape::class,
            CornerStyle.NOTCH to NotchShape::class,
        )
        val radii = listOf(4f, 8f, 12f, 16f)

        val built = styles.associateWith { style ->
            styledShape(
                style = style,
                topStart = CornerSize(radii[0]),
                topEnd = CornerSize(radii[1]),
                bottomEnd = CornerSize(radii[2]),
                bottomStart = CornerSize(radii[3]),
            )
        }

        // 每种风格都要落到自己的实现分支；SquircleShape 来自外部库，这里不写死它的类名，
        // 用「N 种风格 N 种实现」锁住分派，将来新增风格也会在这里被点名。
        assertEquals(
            "每种 CornerStyle 都应有独立的形状实现",
            styles.size,
            built.values.map { it::class }.distinct().size,
        )

        for ((style, shape) in built) {
            expectedType[style]?.let { expected ->
                assertEquals("style=$style 分支实现类", expected, shape::class)
            }

            // 只读角值、不调 createOutline：Wavy / Notch 的 Path 依赖不会进入 JVM 测试
            val actual = shape.cornerPx()
            assertEquals("style=$style 槽位数", 4, actual.size)
            radii.forEachIndexed { index, radius ->
                assertEquals(
                    "style=$style 第 $index 个槽位应只被 cornerScale 缩放一次",
                    radius * style.scaleRatio,
                    actual[index],
                    TOLERANCE,
                )
            }

            val pressed = shape.copy(CornerSize(24f))
            val listGroup = ListItemShapes(shape, pressed, pressed, pressed, pressed, pressed)
                .withInterpolatableCorners(shape)
            assertEquals(
                "style=$style 列表实际状态组必须可插值",
                (actual[0] + 24f) / 2f,
                listGroup.shape.asCornerShape().lerped(listGroup.pressedShape.asCornerShape(), 0.5f)
                    .topStart.toPx(bigSquare, unitDensity),
                TOLERANCE,
            )
            val tabGroup = ToggleButtonShapes(CircleShape, pressed, shape).withInterpolatableCorners(shape)
            assertEquals(
                "style=$style 分页胶囊与选中状态必须可插值",
                (200f + actual[0]) / 2f,
                tabGroup.shape.asCornerShape().lerped(tabGroup.checkedShape.asCornerShape(), 0.5f)
                    .topStart.toPx(bigSquare, unitDensity),
                TOLERANCE,
            )
        }
    }

    @Test
    fun `circle percent and dp corners resolve at runtime size and density`() {
        val prototype = RecordingCornerShape()
        val percent = CircleShape.withInterpolatableCorners(prototype).asCornerShape()
        val dp = RoundedCornerShape(8.dp).withInterpolatableCorners(prototype).asCornerShape()

        // 百分比角跟尺寸走、dp 角跟密度走，都不该被预折成固定像素
        assertEquals(50f, percent.topStart.toPx(Size(200f, 100f), unitDensity), TOLERANCE)
        assertEquals(20f, percent.topStart.toPx(Size(100f, 40f), unitDensity), TOLERANCE)
        assertEquals(8f, dp.topStart.toPx(Size(200f, 100f), unitDensity), TOLERANCE)
        assertEquals(16f, dp.topStart.toPx(Size(200f, 100f), doubleDensity), TOLERANCE)

        val mid = percent.lerped(dp, 0.5f, "percent<->dp")
        assertEquals("(50 + 8) / 2", 29f, mid.topStart.toPx(Size(200f, 100f), unitDensity), TOLERANCE)
        assertEquals("(50 + 16) / 2", 33f, mid.topStart.toPx(Size(200f, 100f), doubleDensity), TOLERANCE)
        assertEquals("(20 + 16) / 2", 18f, mid.topStart.toPx(Size(100f, 40f), doubleDensity), TOLERANCE)
    }

    @Test
    fun `copy maps positional arguments to logical corners in order`() {
        val prototype = RecordingCornerShape()
        val shape = RoundedCornerShape(CornerSize(9f)).withInterpolatableCorners(prototype).asCornerShape()

        // 位置参数顺序必须是 topStart, topEnd, bottomEnd, bottomStart
        val copied = shape.copy(CornerSize(1f), CornerSize(2f), CornerSize(3f), CornerSize(4f))
        assertEquals(1f, copied.topStart.toPx(bigSquare, unitDensity), TOLERANCE)
        assertEquals(2f, copied.topEnd.toPx(bigSquare, unitDensity), TOLERANCE)
        assertEquals(3f, copied.bottomEnd.toPx(bigSquare, unitDensity), TOLERANCE)
        assertEquals(4f, copied.bottomStart.toPx(bigSquare, unitDensity), TOLERANCE)
        assertEquals(listOf(1f, 2f, 3f, 4f), copied.cornerPx())

        // copy 之后仍留在同一 prototype 族，否则 copy 会把动画打回硬切
        assertNotNull("copy 应保留 prototype", copied.lerp(shape, 0.5f))
    }

    @Test
    fun `outline delegation keeps corner order and layout direction`() {
        val prototype = RecordingCornerShape()
        val shape = RoundedCornerShape(
            topStart = CornerSize(1f),
            topEnd = CornerSize(2f),
            bottomEnd = CornerSize(3f),
            bottomStart = CornerSize(4f),
        ).withInterpolatableCorners(prototype).asCornerShape()

        val size = Size(100f, 100f)
        shape.createOutline(size, LayoutDirection.Ltr, unitDensity)
        shape.createOutline(size, LayoutDirection.Rtl, unitDensity)

        assertEquals(2, prototype.calls.size)
        val ltr = prototype.calls[0]
        val rtl = prototype.calls[1]

        assertEquals(LayoutDirection.Ltr, ltr.layoutDirection)
        assertEquals(LayoutDirection.Rtl, rtl.layoutDirection)
        assertEquals("尺寸原样转交", size, ltr.size)
        assertEquals("尺寸原样转交", size, rtl.size)
        assertEquals(listOf(1f, 2f, 3f, 4f), ltr.corners)
        // RTL 下不在这里交换，四角按逻辑顺序交给 prototype 自己解释
        assertEquals("RTL 不交换角次序", ltr.corners, rtl.corners)
    }

    @Test
    fun `lerp returns edges at 0 1 and overshoot`() {
        val prototype = RecordingCornerShape()
        val a = RoundedCornerShape(CornerSize(4f)).withInterpolatableCorners(prototype).asCornerShape()
        val b = RoundedCornerShape(CornerSize(20f)).withInterpolatableCorners(prototype).asCornerShape()

        assertSame(a, a.lerp(b, 0f))
        assertSame(b, a.lerp(b, 1f))
        assertSame("负过冲直接回到起点", a, a.lerp(b, -0.4f))
        assertSame("超过 1 的过冲停在终点", b, a.lerp(b, 1.4f))

        // 端点不嵌套 InterpolatedCornerSize，角值就是端点本身
        assertEquals(listOf(4f, 4f, 4f, 4f), (a.lerp(b, 0f) as CornerBasedShape).cornerPx())
        assertEquals(listOf(20f, 20f, 20f, 20f), (a.lerp(b, 1f) as CornerBasedShape).cornerPx())

        // 非逐角形状不猜路径，交给 M3 自己降级
        assertNull(a.lerp(RectangleShape, 0.5f))
        assertNull(a.lerp(null, 0.5f))
    }

    @Test
    fun `interpolated corners stay non negative and let the shape normalize corner pairs`() {
        val prototype = RecordingCornerShape()
        val negative = object : CornerSize {
            override fun toPx(shapeSize: Size, density: Density): Float = -40f
        }

        val from = RoundedCornerShape(negative).withInterpolatableCorners(prototype).asCornerShape()
        val zero = RoundedCornerShape(CornerSize(0f)).withInterpolatableCorners(prototype).asCornerShape()
        // -20 落点必须夹到 0，不能把负半径交给 CornerBasedShape（那里会抛异常）
        assertEquals(0f, from.lerped(zero, 0.5f, "负角来源").topStart.toPx(bigSquare, unitDensity), TOLERANCE)

        val huge = RoundedCornerShape(CornerSize(400f)).withInterpolatableCorners(prototype).asCornerShape()
        val mid = zero.lerped(huge, 0.25f, "原生角对归一化")
        assertEquals("原始角尺寸保持线性", 100f, mid.topStart.toPx(bigSquare, unitDensity), TOLERANCE)
        val small = Size(100f, 60f)
        assertEquals("不在 toPx 阶段单独夹到半短边", 100f, mid.topStart.toPx(small, unitDensity), TOLERANCE)
        mid.createOutline(small, LayoutDirection.Ltr, unitDensity)
        prototype.calls.last().corners.forEach {
            assertEquals("原生父类归一化对称角", 30f, it, TOLERANCE)
        }

        // 单角可超过半短边；若中段夹到 30、端点再变成 60，就会在动画尾部再跳一次。
        val asymmetric = zero.copy(topStart = CornerSize(100f))
        zero.lerped(asymmetric, 0.99f).createOutline(small, LayoutDirection.Ltr, unitDensity)
        val beforeEnd = prototype.calls.last().corners
        asymmetric.createOutline(small, LayoutDirection.Ltr, unitDensity)
        listOf(60f, 0f, 0f, 0f).forEachIndexed { index, expected ->
            assertEquals(expected, beforeEnd[index], TOLERANCE)
            assertEquals("端点与上一帧归一化方式一致", beforeEnd[index], prototype.calls.last().corners[index], TOLERANCE)
        }
    }

    @Test
    fun `equal shapes have stable hash and stable group key`() {
        val prototype = RecordingCornerShape()

        fun buildListGroup() = ListItemShapes(
            shape = RoundedCornerShape(CornerSize(4f)),
            selectedShape = RoundedCornerShape(CornerSize(8f)),
            pressedShape = RectangleShape,
            focusedShape = styledShape(CornerStyle.NOTCH, 12.dp),
            hoveredShape = RoundedCornerShape(CornerSize(16f)),
            draggedShape = RectangleShape,
        )

        fun buildToggleGroup() = ToggleButtonShapes(
            shape = CircleShape,
            pressedShape = RectangleShape,
            checkedShape = RoundedCornerShape(CornerSize(16f)),
        )

        val listA = buildListGroup().withInterpolatableCorners(prototype)
        val listB = buildListGroup().withInterpolatableCorners(prototype)
        assertEquals("相同参数必须相等，否则重组会重建动画状态", listA, listB)
        assertEquals(listA.hashCode(), listB.hashCode())
        listA.slots().zip(listB.slots()).forEachIndexed { index, (first, second) ->
            assertEquals("槽位 $index", first, second)
            assertEquals("槽位 $index hashCode", first.hashCode(), second.hashCode())
        }

        val toggleA = buildToggleGroup().withInterpolatableCorners(prototype)
        val toggleB = buildToggleGroup().withInterpolatableCorners(prototype)
        assertEquals(toggleA, toggleB)
        assertEquals(toggleA.hashCode(), toggleB.hashCode())

        // 角值变了就是另一组 key
        val changedCorners = buildListGroup()
            .copy(hoveredShape = RoundedCornerShape(CornerSize(17f)))
            .withInterpolatableCorners(prototype)
        assertNotEquals(listA, changedCorners)

        // prototype 换了实例也算另一组：轮廓算法换了，动画必须重建
        assertNotEquals(listA, buildListGroup().withInterpolatableCorners(RecordingCornerShape()))
    }

    @Test
    fun `styled corners are not scaled twice and prototype corners are ignored`() {
        for (style in listOf(CornerStyle.SCOOP, CornerStyle.NOTCH)) {
            // prototype 自己的角被 cornerScale 缩放一次：24dp * 0.65 * 0.4
            val prototype = styledShape(style, 24.dp, scale = 0.4f)
            assertEquals(
                "style=$style prototype 角只缩放一次",
                24f * 0.65f * 0.4f,
                prototype.topStart.toPx(bigSquare, unitDensity),
                TOLERANCE,
            )

            val shape = RoundedCornerShape(CornerSize(10f)).withInterpolatableCorners(prototype).asCornerShape()
            assertEquals(
                "style=$style 包装后的角来自来源形状，不再乘 prototype 的 scale",
                10f,
                shape.topStart.toPx(bigSquare, unitDensity),
                TOLERANCE,
            )

            val other = RoundedCornerShape(CornerSize(20f)).withInterpolatableCorners(prototype).asCornerShape()
            assertEquals(
                "style=$style 中段也不受 prototype 角影响",
                15f,
                shape.lerped(other, 0.5f, "style=$style").topStart.toPx(bigSquare, unitDensity),
                TOLERANCE,
            )
        }
    }

    @Test
    fun `fully rounded groups are returned unchanged`() {
        val prototype = RecordingCornerShape()
        val rounded = ListItemShapes(
            shape = RoundedCornerShape(4.dp),
            selectedShape = RoundedCornerShape(8.dp),
            pressedShape = RoundedCornerShape(2.dp),
            focusedShape = RoundedCornerShape(6.dp),
            hoveredShape = RoundedCornerShape(10.dp),
            draggedShape = RoundedCornerShape(12.dp),
        )
        assertSame("全圆角组保持 M3 原生实现", rounded, rounded.withInterpolatableCorners(prototype))

        val zero = ListItemShapes(
            shape = RoundedCornerShape(0.dp),
            selectedShape = RoundedCornerShape(0.dp),
            pressedShape = RoundedCornerShape(0.dp),
            focusedShape = RoundedCornerShape(0.dp),
            hoveredShape = RoundedCornerShape(0.dp),
            draggedShape = RoundedCornerShape(0.dp),
        )
        assertSame("全零全宽组保持原对象", zero, zero.withInterpolatableCorners(prototype))

        val toggles = ToggleButtonShapes(
            shape = CircleShape,
            pressedShape = RoundedCornerShape(4.dp),
            checkedShape = RoundedCornerShape(8.dp),
        )
        assertSame(toggles, toggles.withInterpolatableCorners(prototype))
    }

    @Test
    fun `unknown shapes pass through and rectangle shape becomes zero corners`() {
        val prototype = RecordingCornerShape()
        val unknown = object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
                Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }
        assertSame("未知形状原样透传，不猜它的路径", unknown, unknown.withInterpolatableCorners(prototype))

        val rect = RectangleShape.withInterpolatableCorners(prototype).asCornerShape()
        assertEquals(listOf(0f, 0f, 0f, 0f), rect.cornerPx())

        // RectangleShape 状态要能和逐角状态互相插值，M3 的默认值就带 RectangleShape
        val rounded = RoundedCornerShape(CornerSize(10f)).withInterpolatableCorners(prototype).asCornerShape()
        assertEquals(5f, rect.lerped(rounded, 0.5f, "rect->rounded").topStart.toPx(bigSquare, unitDensity), TOLERANCE)
    }

    @Test
    fun `lerp between different prototypes returns null`() {
        val a = RoundedCornerShape(CornerSize(4f))
            .withInterpolatableCorners(RecordingCornerShape())
            .asCornerShape()
        val b = RoundedCornerShape(CornerSize(8f))
            .withInterpolatableCorners(RecordingCornerShape())
            .asCornerShape()

        assertNull("不同 prototype 实例属于不同族", a.lerp(b, 0.5f))
        assertNull(b.lerp(a, 0.5f))

        val shared = RecordingCornerShape()
        val c = RoundedCornerShape(CornerSize(4f)).withInterpolatableCorners(shared).asCornerShape()
        val d = RoundedCornerShape(CornerSize(8f)).withInterpolatableCorners(shared).asCornerShape()
        assertNotNull("同一 prototype 实例才可插值", c.lerp(d, 0.5f))
    }

    @Test
    fun `interrupted animation restarts from the previous midpoint`() {
        val prototype = RecordingCornerShape()
        fun wrap(px: Float) = RoundedCornerShape(CornerSize(px)).withInterpolatableCorners(prototype).asCornerShape()

        val a = wrap(8f)
        val b = wrap(40f)
        val c = wrap(0f)

        val midway = a.lerped(b, 0.5f, "打断前")
        assertEquals(24f, midway.topStart.toPx(bigSquare, unitDensity), TOLERANCE)
        assertSame("新动画 t=0 必须就是上一帧的顶点，不许跳变", midway, midway.lerp(c, 0f))
        assertEquals(
            24f,
            (midway.lerp(c, 0f) as CornerBasedShape).topStart.toPx(bigSquare, unitDensity),
            TOLERANCE,
        )

        // 新目标的 25% 从当前值 24 出发，而不是从原来的起点 8
        assertEquals(18f, midway.lerped(c, 0.25f, "打断后").topStart.toPx(bigSquare, unitDensity), TOLERANCE)
        // 反向打断同理
        assertEquals(32f, midway.lerped(b, 0.5f, "反向打断").topStart.toPx(bigSquare, unitDensity), TOLERANCE)
    }

    @Test
    fun `repeated interruptions keep corner evaluation flat`() {
        val prototype = RecordingCornerShape()
        var reads = 0
        fun tracked(px: Float): CornerBasedShape {
            val corner = object : CornerSize {
                override fun toPx(shapeSize: Size, density: Density): Float {
                    reads++
                    return px
                }
            }
            return RoundedCornerShape(corner).withInterpolatableCorners(prototype).asCornerShape()
        }
        val a = tracked(8f)
        val b = tracked(40f)
        val c = tracked(0f)
        var current = a
        var expected = 8f
        repeat(2_000) { index ->
            val target = if (index % 2 == 0) b else c
            expected += ((if (index % 2 == 0) 40f else 0f) - expected) * 0.35f
            current = current.lerped(target, 0.35f)
        }
        reads = 0
        current.cornerPx().forEach { assertEquals(expected, it, TOLERANCE) }
        assertTrue("每角最多读取三个原始来源，不随中断次数递归增长，实际 $reads", reads <= 12)
    }

    private fun assertInterleaves(shapes: List<CornerBasedShape>, label: String) {
        // 0.49 / 0.51 分居两侧：硬切会在这里出现整段跳变，线性插值则只有 2% 的差
        val fractions = listOf(0.25f, 0.49f, 0.51f, 0.75f)

        for (i in shapes.indices) {
            for (j in i + 1 until shapes.size) {
                val from = shapes[i].topStart.toPx(bigSquare, unitDensity)
                val to = shapes[j].topStart.toPx(bigSquare, unitDensity)
                val lowest = minOf(from, to)
                val highest = maxOf(from, to)

                for (t in fractions) {
                    val mid = shapes[i].lerped(shapes[j], t, "$label 槽位 $i->$j")
                    val actual = mid.topStart.toPx(bigSquare, unitDensity)
                    assertEquals("$label 槽位 $i->$j t=$t", from + (to - from) * t, actual, TOLERANCE)
                    assertTrue(
                        "$label 槽位 $i->$j t=$t 应是中间值，实际 $actual / [$lowest, $highest]",
                        actual > lowest + TOLERANCE && actual < highest - TOLERANCE,
                    )
                }

                val before = shapes[i].lerped(shapes[j], 0.49f).topStart.toPx(bigSquare, unitDensity)
                val after = shapes[i].lerped(shapes[j], 0.51f).topStart.toPx(bigSquare, unitDensity)
                assertEquals(
                    "$label 槽位 $i->$j 中点两侧应连续",
                    0.02f * (to - from),
                    after - before,
                    TOLERANCE,
                )
            }
        }
    }

    private fun ListItemShapes.slots(): List<Shape> =
        listOf(shape, selectedShape, pressedShape, focusedShape, hoveredShape, draggedShape)
}

private val unitDensity = Density(1f)
private val doubleDensity = Density(2f)
private val bigSquare = Size(400f, 400f)
private const val TOLERANCE = 1e-3f

private fun Shape.asCornerShape(): CornerBasedShape {
    assertTrue("期望 CornerBasedShape，实际是 $this", this is CornerBasedShape)
    return this as CornerBasedShape
}

private fun CornerBasedShape.cornerPx(
    size: Size = bigSquare,
    density: Density = unitDensity,
): List<Float> = listOf(
    topStart.toPx(size, density),
    topEnd.toPx(size, density),
    bottomEnd.toPx(size, density),
    bottomStart.toPx(size, density),
)

/** 走真正的 `Interpolatable.lerp`，并把 null 变成带上下文的断言失败。 */
private fun CornerBasedShape.lerped(other: CornerBasedShape, t: Float, label: String = ""): CornerBasedShape {
    val direct = lerp(other, t)
    assertTrue("$label lerp(t=$t) 应返回 CornerBasedShape，实际是 $direct", direct is CornerBasedShape)
    val result = Interpolatable.lerp(this, other, t)
    assertTrue("$label M3 插值入口应保留逐角形状，实际是 $result", result is CornerBasedShape)
    return result as CornerBasedShape
}

/**
 * 只记录入参并返回 [Outline.Rectangle] 的 prototype。
 *
 * 这样能观察到 [CornerBasedShape] 解析后的四个角值与 layoutDirection，又不碰 Android 的 Path。
 */
private class RecordingCornerShape(
    topStart: CornerSize = CornerSize(0f),
    topEnd: CornerSize = CornerSize(0f),
    bottomEnd: CornerSize = CornerSize(0f),
    bottomStart: CornerSize = CornerSize(0f),
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    data class Call(
        val size: Size,
        val topStart: Float,
        val topEnd: Float,
        val bottomEnd: Float,
        val bottomStart: Float,
        val layoutDirection: LayoutDirection,
    ) {
        val corners: List<Float> get() = listOf(topStart, topEnd, bottomEnd, bottomStart)
    }

    val calls = mutableListOf<Call>()

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        calls += Call(size, topStart, topEnd, bottomEnd, bottomStart, layoutDirection)
        return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = RecordingCornerShape(topStart, topEnd, bottomEnd, bottomStart)
}
