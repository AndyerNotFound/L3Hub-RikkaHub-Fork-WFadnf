package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.UiStyle
import me.rerere.rikkahub.ui.terminal.Switch
import me.rerere.rikkahub.ui.theme.LocalUiStyle
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import me.rerere.rikkahub.ui.theme.LocalButtonShadow

/** 跟随"按钮阴影"设置的开关。 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        // Miuix 模式下开关也换过来：滑块和轨道的比例、按下时的收缩都是 Miuix 的手感
        MiuixSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
        )
        return
    }
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = if (LocalButtonShadow.current && enabled) {
            modifier.shadow(
                elevation = 1.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.10f),
            )
        } else {
            modifier
        },
    )
}
