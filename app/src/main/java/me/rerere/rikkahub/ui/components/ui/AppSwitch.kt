package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.theme.LocalButtonShadow

/** 跟随"按钮阴影"设置的开关。 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = if (LocalButtonShadow.current) modifier.shadow(3.dp, CircleShape) else modifier,
    )
}
