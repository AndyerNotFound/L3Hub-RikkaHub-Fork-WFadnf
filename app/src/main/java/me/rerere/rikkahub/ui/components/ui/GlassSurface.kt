package me.rerere.rikkahub.ui.components.ui

import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeState

/**
 * 朴素外观下不再有背景模糊。
 *
 * [LocalGlassHazeState] 仍然保留，因为调用点还在往下传 Haze 源；
 * 等接口清理完成，这一整层连同 haze 依赖一起删掉。
 */
val LocalGlassHazeState = compositionLocalOf<HazeState?> { null }

@Composable
internal fun Modifier.blurBackdrop(hazeState: HazeState, radius: Dp): Modifier = this

@Composable
fun glassColor(base: Color): Color = base

/** 浮在消息上的小控件不再抬高透明度下限，直接用底色。 */
@Composable
internal fun overlayControlColor(base: Color, floor: Float = .6f, fallback: Color = base): Color = base

@Composable
fun glassSegmentedButtonColors() = SegmentedButtonDefaults.colors()

@Composable
fun glassSwitchColors() = SwitchDefaults.colors()

@Composable
fun glassFilterChipColors() = FilterChipDefaults.filterChipColors()

@Composable
fun glassAssistChipColors() = AssistChipDefaults.assistChipColors()
