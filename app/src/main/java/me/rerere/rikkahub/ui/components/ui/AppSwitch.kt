package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.SwitchType
import me.rerere.rikkahub.ui.style.Switch
import me.rerere.rikkahub.ui.components.ui.switch.SwitchByType
import me.rerere.rikkahub.ui.theme.LocalSwitchType
import me.rerere.rikkahub.ui.theme.LocalButtonShadow

/**
 * 跟随"按钮阴影"设置的开关，并按「开关类型」分发到对应实现。
 *
 * [SwitchType.MATERIAL_YOU]（默认）不接管，走 M3 的 `Switch`，与改动前完全一致。
 * 其余七档用 `ui/components/ui/switch` 里从 ImageToolbox 搬过来的自绘实现。
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val useShadow = LocalButtonShadow.current && enabled
    val switchType = LocalSwitchType.current
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
    if (switchType == SwitchType.MATERIAL_YOU) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = shadowedModifier,
        )
    } else {
        SwitchByType(
            switchType = switchType,
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = shadowedModifier,
            enabled = enabled,
        )
    }
}
