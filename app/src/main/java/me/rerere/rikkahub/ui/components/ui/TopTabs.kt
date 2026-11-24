package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A fill-width tab row with the Catalog's inline, elastic, rounded-line preset.
 * [swipePosition] supplies the fractional page index only while the pager drives the indicator.
 */
@Composable
internal fun TopTabRow(
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    minTabWidth: Dp = 72.dp,
    swipePosition: (() -> Float)? = null,
    tabs: @Composable () -> Unit,
) {
    val scroll = rememberScrollState()
    // Keep Material Components' emphasized PathEasing local to each row.
    val tabAnimation = remember {
        tween<Float>(
            durationMillis = 500,
            easing = PathEasing(Path().apply {
                moveTo(0f, 0f)
                cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
                cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
            }),
        )
    }
    val bounds = remember { mutableStateOf(emptyList<TabBounds>()) }
    val frame = remember { mutableStateOf<IndicatorFrame?>(null) }
    val lastDrawn = remember { DrawnIndicator() }
    val index = rememberUpdatedState(selectedIndex)
    val followsSwipe = rememberUpdatedState(swipePosition != null)
    val indicatorColor = MaterialTheme.colorScheme.primary
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    LaunchedEffect(Unit) {
        snapshotFlow { IndicatorTarget(bounds.value, index.value, followsSwipe.value) }
            .collectLatest { (tabs, selected, followingSwipe) ->
                val end = tabs.getOrNull(selected)
                val start = lastDrawn.boundsIn(tabs)
                if (followingSwipe || end == null || start == null || start == end) {
                    frame.value = null
                } else {
                    val next = IndicatorFrame(tabs, start, end)
                    frame.value = next
                    next.progress.animateTo(1f, tabAnimation)
                }
            }
    }

    LaunchedEffect(selectedIndex) {
        snapshotFlow {
            val selected = bounds.value.getOrNull(selectedIndex)
            val viewport = scroll.viewportSize
            if (selected == null || viewport == 0) null else {
                ((selected.start + selected.end - viewport) / 2f)
                    .roundToInt().coerceIn(0, scroll.maxValue)
            }
        }.collectLatest { offset ->
            if (offset != null && offset != scroll.value) {
                scroll.animateScrollTo(offset, tabAnimation)
            }
        }
    }

    Layout(
        content = tabs,
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .selectableGroup()
            .horizontalScroll(scroll)
            .drawWithCache {
                val underline = Path()
                val thickness = 3.dp.toPx().coerceAtMost(size.height)
                val divider = 1.dp.toPx().coerceAtMost(size.height)
                // Full-width uses the tab bounds; the rounded drawable insets its ends by 4dp.
                val inset = 4.dp.toPx()
                val radius = CornerRadius(thickness)
                val rtl = layoutDirection == LayoutDirection.Rtl
                onDrawWithContent {
                    drawContent()
                    if (size.width <= 0f || size.height <= 0f) return@onDrawWithContent
                    drawRect(dividerColor, Offset(0f, size.height - divider), Size(size.width, divider))
                    val spans = bounds.value
                    val target = spans.getOrNull(selectedIndex)
                    val span = when {
                        swipePosition != null -> spans.atPage(swipePosition()) ?: target
                        target == null -> null
                        else -> frame.value?.boundsIn(spans) ?: lastDrawn.boundsIn(spans) ?: target
                    }
                    lastDrawn.record(spans, span)
                    if (span == null) return@onDrawWithContent
                    val left = if (rtl) size.width - span.end else span.start
                    val right = if (rtl) size.width - span.start else span.end
                    if (right - left > 2 * inset) {
                        underline.rewind()
                        underline.addRoundRect(
                            RoundRect(
                                left = left + inset,
                                top = size.height - thickness,
                                right = right - inset,
                                bottom = size.height,
                                topLeftCornerRadius = radius,
                                topRightCornerRadius = radius,
                            ),
                        )
                        drawPath(underline, indicatorColor)
                    }
                }
            },
    ) { measurables, constraints ->
        if (measurables.isEmpty()) {
            bounds.value = emptyList()
            return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        }
        // horizontalScroll preserves the viewport as minWidth but unbounds maxWidth.
        val viewport = constraints.minWidth
        val minimum = minTabWidth.roundToPx()
        val maximum = viewport.coerceIn(minimum, maxOf(minimum, 264.dp.roundToPx()))
        val preferred = measurables.map {
            it.maxIntrinsicWidth(constraints.maxHeight).coerceIn(minimum, maximum)
        }
        val count = measurables.size
        // Only equalize when every label fits its share. Otherwise prefer natural widths and scroll
        // if they overflow; if they fit, distribute the remaining space so there is no blank end.
        val fill = preferred.all { it <= viewport / count }
        val extra = if (fill) viewport else (viewport - preferred.sum()).coerceAtLeast(0)
        var start = 0
        val positions = ArrayList<TabBounds>(count)
        val placeables = measurables.mapIndexed { i, measurable ->
            val share = extra / count + if (i < extra % count) 1 else 0
            val width = if (fill) share else preferred[i] + share
            positions += TabBounds(start.toFloat(), (start + width).toFloat())
            start += width
            measurable.measure(Constraints(width, width, constraints.minHeight, constraints.maxHeight))
        }
        bounds.value = positions
        val height = placeables.maxOf { it.height }
        layout(start, height) {
            placeables.forEachIndexed { i, tab ->
                tab.placeRelative(positions[i].start.toInt(), (height - tab.height) / 2)
            }
        }
    }
}

@Composable
internal fun TopTab(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: ImageVector,
    labelModifier: Modifier = Modifier,
    labelOverflow: TextOverflow = TextOverflow.Ellipsis,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    TopTab(
        selected = selected,
        onClick = onClick,
        label = label,
        labelModifier = labelModifier,
        labelOverflow = labelOverflow,
        trailingAction = trailingAction,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
    }
}

@Composable
internal fun TopTab(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    labelModifier: Modifier = Modifier,
    labelOverflow: TextOverflow = TextOverflow.Ellipsis,
    trailingAction: (@Composable () -> Unit)? = null,
    leadingIcon: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val tint = if (selected) colors.primary else colors.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .selectable(
                    selected = selected,
                    interactionSource = null,
                    indication = ripple(color = colors.primary),
                    role = Role.Tab,
                    onClick = onClick,
                )
                .padding(start = 12.dp, end = if (trailingAction == null) 12.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides tint) {
                leadingIcon()
            }
            Text(
                text = label,
                modifier = labelModifier,
                color = tint,
                style = MaterialTheme.typography.titleSmall,
                letterSpacing = 0.sp,
                maxLines = 1,
                softWrap = false,
                overflow = labelOverflow,
            )
        }
        trailingAction?.invoke()
    }
}

private data class TabBounds(val start: Float, val end: Float)

private data class IndicatorTarget(
    val tabs: List<TabBounds>,
    val selected: Int,
    val followingSwipe: Boolean,
)

private class IndicatorFrame(
    private val tabs: List<TabBounds>,
    private val start: TabBounds,
    private val end: TabBounds,
) {
    val progress = Animatable(0f)

    fun boundsIn(tabs: List<TabBounds>): TabBounds? =
        if (this.tabs == tabs) elasticBounds(start, end, progress.value) else null
}

// Keep the last painted bounds across gesture/animation handoffs, without invalidating the draw.
private class DrawnIndicator {
    private var tabs: List<TabBounds>? = null
    private var bounds: TabBounds? = null

    fun boundsIn(tabs: List<TabBounds>): TabBounds? = if (this.tabs == tabs) bounds else null

    fun record(tabs: List<TabBounds>, bounds: TabBounds?) {
        this.tabs = tabs
        this.bounds = bounds
    }
}

private fun List<TabBounds>.atPage(page: Float): TabBounds? {
    if (isEmpty() || !page.isFinite()) return null
    val position = page.coerceIn(0f, lastIndex.toFloat())
    val first = floor(position).toInt()
    val next = (first + 1).coerceAtMost(lastIndex)
    return elasticBounds(this[first], this[next], position - first)
}

private fun elasticBounds(from: TabBounds, to: TabBounds, progress: Float): TabBounds {
    if (progress <= 0f || from == to) return from
    if (progress >= 1f) return to
    val angle = progress * PI / 2
    // Same edge interpolation as ElasticTabIndicatorInterpolator: lead first, then catch up.
    val slow = (1 - cos(angle)).toFloat()
    val fast = sin(angle).toFloat()
    val forward = from.start + from.end < to.start + to.end
    return TabBounds(
        from.start + (to.start - from.start) * if (forward) slow else fast,
        from.end + (to.end - from.end) * if (forward) fast else slow,
    )
}
