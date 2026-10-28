package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.flow.drop
import me.rerere.ai.core.ReasoningLevel
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Idea
import me.rerere.hugeicons.stroke.Idea01
import me.rerere.rikkahub.R
import me.rerere.ui.components.ToggleSurface
import me.rerere.ui.icons.ReasoningHigh
import me.rerere.ui.icons.ReasoningLow
import me.rerere.ui.icons.ReasoningMedium
import kotlin.math.roundToInt

private val levels = ReasoningLevel.entries
private val levelCount = levels.size
private val levelShapes = levels.map { it.shape() }

@Composable
fun ReasoningButton(
    modifier: Modifier = Modifier,
    onlyIcon: Boolean = false,
    reasoningLevel: ReasoningLevel,
    onUpdateReasoningLevel: (ReasoningLevel) -> Unit,
    modelName: String? = null,
    onOpenModelList: (() -> Unit)? = null,
    useL3HubStyle: Boolean = false,
) {
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        ReasoningPicker(
            reasoningLevel = reasoningLevel,
            onDismissRequest = { showPicker = false },
            onUpdateReasoningLevel = onUpdateReasoningLevel,
            modelName = modelName,
            onOpenModelList = onOpenModelList,
            useL3HubStyle = useL3HubStyle,
        )
    }

    ToggleSurface(
        checked = reasoningLevel.isEnabled,
        onClick = { showPicker = true },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (useL3HubStyle) {
                    Text(
                        text = reasoningLevel.letter(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Icon(reasoningLevel.icon(), null)
                }
            }
            if (!onlyIcon) Text(stringResource(R.string.setting_provider_page_reasoning))
        }
    }
}

@Composable
fun ReasoningPicker(
    reasoningLevel: ReasoningLevel,
    onDismissRequest: () -> Unit = {},
    onUpdateReasoningLevel: (ReasoningLevel) -> Unit,
    modelName: String? = null,
    onOpenModelList: (() -> Unit)? = null,
    useL3HubStyle: Boolean = false,
) {
    val currentIndex = levels.indexOf(reasoningLevel).coerceAtLeast(0)
    val sliderState = remember {
        SliderState(
            value = currentIndex.toFloat(),
            trackRange = 0f..(levelCount - 1).toFloat(),
            steps = levelCount - 2,
        )
    }
    val interactionSource = remember { MutableInteractionSource() }
    val hapticFeedback = LocalHapticFeedback.current
    // 拖动过程中就跟随滑块预览，松手后才真正提交
    val previewLevel = levels[sliderState.value.roundToInt().coerceIn(0, levelCount - 1)]

    LaunchedEffect(currentIndex) {
        sliderState.value = currentIndex.toFloat()
    }

    LaunchedEffect(sliderState) {
        snapshotFlow { sliderState.value.roundToInt() }
            .drop(1)
            .collect { hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (onOpenModelList != null) {
                Surface(
                    onClick = {
                        onDismissRequest()
                        onOpenModelList()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Brain02,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = modelName ?: stringResource(R.string.model_list_select_model),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.reasoning_picker_model),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            PickerValueHeader(
                title = stringResource(R.string.reasoning_picker_title),
                value = previewLevel,
                hint = stringResource(R.string.reasoning_picker_hint),
                label = { it.label() },
            ) {
                // 等级越高形状越「激烈」
                PickerHero(
                    shapes = levelShapes,
                    index = levels.indexOf(previewLevel),
                    icon = previewLevel.icon(),
                    letter = if (useL3HubStyle) previewLevel.letter() else null,
                    containerColor = when {
                        !previewLevel.isEnabled -> MaterialTheme.colorScheme.surfaceContainerHighest
                        previewLevel >= ReasoningLevel.HIGH -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = when {
                        !previewLevel.isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        previewLevel >= ReasoningLevel.HIGH -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            }

            if (useL3HubStyle) {
                CircularReasoningSlider(
                    level = previewLevel,
                    onPreview = { sliderState.value = levels.indexOf(it).toFloat() },
                    onCommit = { onUpdateReasoningLevel(it) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            } else {
                Slider(
                    state = sliderState,
                    onValueChange = { sliderState.value = it },
                    onValueChangeFinished = {
                        val snappedIndex = sliderState.value.roundToInt().coerceIn(0, levelCount - 1)
                        sliderState.value = snappedIndex.toFloat()
                        onUpdateReasoningLevel(levels[snappedIndex])
                    },
                    modifier = Modifier.fillMaxWidth(),
                    interactionSource = interactionSource,
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource = interactionSource,
                            isVertical = false,
                            thumbSize = DpSize(4.dp, 52.dp),
                        )
                    },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            trackCornerSize = 12.dp,
                            modifier = Modifier.height(40.dp),
                        )
                    }
                )
            }
        }
    }
}

private fun ReasoningLevel.icon(): ImageVector = when (this) {
    ReasoningLevel.OFF -> HugeIcons.Idea
    ReasoningLevel.AUTO -> HugeIcons.Idea01
    ReasoningLevel.LOW -> ReasoningLow
    ReasoningLevel.MEDIUM -> ReasoningMedium
    ReasoningLevel.HIGH -> ReasoningHigh
    ReasoningLevel.XHIGH -> ReasoningHigh
    ReasoningLevel.MAX -> ReasoningHigh
    ReasoningLevel.ULTRA -> ReasoningHigh
}

private fun ReasoningLevel.shape(): RoundedPolygon = when (this) {
    ReasoningLevel.OFF -> MaterialShapes.Circle
    ReasoningLevel.AUTO -> MaterialShapes.Cookie4Sided
    ReasoningLevel.LOW -> MaterialShapes.Cookie6Sided
    ReasoningLevel.MEDIUM -> MaterialShapes.Cookie7Sided
    ReasoningLevel.HIGH -> MaterialShapes.Cookie9Sided
    ReasoningLevel.XHIGH -> MaterialShapes.Cookie12Sided
    ReasoningLevel.MAX -> MaterialShapes.SoftBurst
    ReasoningLevel.ULTRA -> MaterialShapes.Circle
}

@Composable
private fun ReasoningLevel.label(): String = when (this) {
    ReasoningLevel.OFF -> stringResource(R.string.reasoning_off)
    ReasoningLevel.AUTO -> stringResource(R.string.reasoning_auto)
    ReasoningLevel.LOW -> stringResource(R.string.reasoning_light)
    ReasoningLevel.MEDIUM -> stringResource(R.string.reasoning_medium)
    ReasoningLevel.HIGH -> stringResource(R.string.reasoning_heavy)
    ReasoningLevel.XHIGH -> stringResource(R.string.reasoning_xhigh)
    ReasoningLevel.MAX -> stringResource(R.string.reasoning_max)
    ReasoningLevel.ULTRA -> stringResource(R.string.reasoning_ultra)
}

private fun ReasoningLevel.letter(): String = when (this) {
    ReasoningLevel.OFF -> "O"
    ReasoningLevel.AUTO -> "A"
    ReasoningLevel.LOW -> "L"
    ReasoningLevel.MEDIUM -> "M"
    ReasoningLevel.HIGH -> "H"
    ReasoningLevel.XHIGH -> "X"
    ReasoningLevel.MAX -> "M"
    ReasoningLevel.ULTRA -> "U"
}

private const val CIRCLE_START_ANGLE = 135f
private const val CIRCLE_SWEEP_ANGLE = 270f

@Composable
private fun CircularReasoningSlider(
    level: ReasoningLevel,
    onPreview: (ReasoningLevel) -> Unit,
    onCommit: (ReasoningLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()
    var previewIndex by remember { mutableIntStateOf(levels.indexOf(level).coerceAtLeast(0)) }
    LaunchedEffect(level) {
        previewIndex = levels.indexOf(level).coerceAtLeast(0)
    }

    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)

    fun indexAt(position: Offset, canvasSize: Size): Int {
        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
        val angle = Math.toDegrees(
            atan2((position.y - center.y).toDouble(), (position.x - center.x).toDouble())
        ).toFloat()
        val relative = ((angle - CIRCLE_START_ANGLE) % 360f + 360f) % 360f
        if (relative > CIRCLE_SWEEP_ANGLE) {
            val pastEnd = relative - CIRCLE_SWEEP_ANGLE
            return if (pastEnd < (360f - CIRCLE_SWEEP_ANGLE) / 2f) levels.lastIndex else 0
        }
        return (relative / CIRCLE_SWEEP_ANGLE * levels.lastIndex)
            .roundToInt()
            .coerceIn(0, levels.lastIndex)
    }

    fun select(index: Int, commit: Boolean) {
        if (index != previewIndex) {
            previewIndex = index
            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onPreview(levels[index])
        }
        if (commit) onCommit(levels[index])
    }

    Box(modifier = modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .size(260.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        select(
                            index = indexAt(offset, Size(size.width.toFloat(), size.height.toFloat())),
                            commit = true,
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            select(
                                index = indexAt(offset, Size(size.width.toFloat(), size.height.toFloat())),
                                commit = false,
                            )
                        },
                        onDrag = { change, _ ->
                            select(
                                index = indexAt(change.position, Size(size.width.toFloat(), size.height.toFloat())),
                                commit = false,
                            )
                        },
                        onDragEnd = { onCommit(levels[previewIndex]) },
                        onDragCancel = { onCommit(levels[previewIndex]) },
                    )
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.34f
            val stroke = 14.dp.toPx()
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)
            drawArc(
                color = trackColor,
                startAngle = CIRCLE_START_ANGLE,
                sweepAngle = CIRCLE_SWEEP_ANGLE,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val progress = previewIndex.toFloat() / levels.lastIndex
            drawArc(
                color = primary,
                startAngle = CIRCLE_START_ANGLE,
                sweepAngle = CIRCLE_SWEEP_ANGLE * progress,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            levels.forEachIndexed { index, item ->
                val angle = Math.toRadians(
                    (CIRCLE_START_ANGLE + CIRCLE_SWEEP_ANGLE * index / levels.lastIndex).toDouble()
                )
                val point = Offset(
                    center.x + cos(angle).toFloat() * (radius + stroke * 1.4f),
                    center.y + sin(angle).toFloat() * (radius + stroke * 1.4f),
                )
                val layout = textMeasurer.measure(
                    text = AnnotatedString(item.letter()),
                    style = labelStyle.copy(color = if (index == previewIndex) primary else labelColor),
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = point - Offset(layout.size.width / 2f, layout.size.height / 2f),
                )
            }
            val thumbAngle = Math.toRadians(
                (CIRCLE_START_ANGLE + CIRCLE_SWEEP_ANGLE * progress).toDouble()
            )
            val thumbCenter = Offset(
                center.x + cos(thumbAngle).toFloat() * radius,
                center.y + sin(thumbAngle).toFloat() * radius,
            )
            drawCircle(color = primary, radius = 9.dp.toPx(), center = thumbCenter)
            drawCircle(color = onPrimary, radius = 3.dp.toPx(), center = thumbCenter)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = levels[previewIndex].letter(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = primary,
            )
            Text(
                text = levels[previewIndex].label(),
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
            )
        }
    }
}

@Composable
@Preview(showBackground = true)
private fun ReasoningPickerPreview() {
    MaterialTheme {
        var level by remember { mutableStateOf(ReasoningLevel.AUTO) }
        ReasoningPicker(
            reasoningLevel = level,
            onUpdateReasoningLevel = { level = it }
        )
    }
}
