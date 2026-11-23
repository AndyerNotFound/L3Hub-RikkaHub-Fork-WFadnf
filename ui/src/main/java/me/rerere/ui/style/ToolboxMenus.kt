package me.rerere.ui.style

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.t8rin.toolbox.ui.widget.enhanced.EnhancedDropdownMenu

/**
 * ui 模块的菜单入口：引擎是 ImageToolbox 的 [EnhancedDropdownMenu]，形状/底色吃引擎默认
 * （surfaceContainerHighest + MaterialTheme.shapes.medium，随轮廓设置走），阴影按
 * drawContainerShadows 画。签名与 M3 DropdownMenu 同名同参，DropdownMenuItem 等 item
 * 依旧从 material3 直接取，菜单容器只在这里换内核。
 *
 * app 模块的 `ui.style.DropdownMenu` 转发到这里；ui 模块内部（Select、Sketch 菜单）也用
 * 这一份，避免两套菜单容器并存。
 */
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    EnhancedDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        content = content,
    )
}