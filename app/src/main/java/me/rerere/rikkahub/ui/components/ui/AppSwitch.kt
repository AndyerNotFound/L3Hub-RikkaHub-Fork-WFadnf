package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.style.Switch
import me.rerere.rikkahub.ui.theme.LocalButtonShadow

/**
 * 跟随「按钮阴影」设置的开关。
 *
 * 开关样式统一由 `ui.style.Switch` 提供（material3 原生实现，未选中轨道带玻璃配色）。
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val useShadow = LocalButtonShadow.current && enabled
    val shadowedModifier = if (useShadow) {
        modifier.shadow(
            elevation = 1.dp,
            shape = CircleShape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.06f),
            spotColor = Color.Black.copy(alpha = 0.10f),
        )
    } else {
        modifier
    }
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = shadowedModifier,
    )
}
