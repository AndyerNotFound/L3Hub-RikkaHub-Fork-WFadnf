package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 一次工具调用详情的请求：渲染器、上下文，以及输出解析不出来时的兜底标记。
 */
class ToolDetailRequest(
    val context: ToolUIContext,
    val renderer: ToolUIRenderer,
    val outputUnparsable: Boolean,
)

/**
 * 详情面板的宿主。
 *
 * 横屏由聊天页挂一个右侧滑入的看板；其它情况仍然是原来的 BottomSheet。
 */
@Stable
class ToolDetailHostState {
    var request by mutableStateOf<ToolDetailRequest?>(null)
}

/** 聊天页提供；拿不到就退回 BottomSheet */
val LocalToolDetailHost = staticCompositionLocalOf<ToolDetailHostState?> { null }

/**
 * 详情内容铺满可用的高度。
 *
 * BottomSheet 里要留 20% 给手势区，右侧看板里没有这个顾虑，铺满更好看。
 */
val LocalToolPreviewFullHeight = compositionLocalOf { false }
