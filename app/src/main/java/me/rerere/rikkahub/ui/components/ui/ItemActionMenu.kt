package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.pages.chat.rememberNativeSelectMenu
import me.rerere.rikkahub.ui.pages.chat.rememberSelectMenuAnchor

@Immutable
data class ItemAction(
    val text: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * 一颗「更多」按钮，点开是一组操作。
 *
 * 展开 / 收起走容器变换：从按钮那一小块原地长成菜单卡，不是淡入淡出。
 * 起点由 [rememberSelectMenuAnchor] 给出 —— 它记的是这颗按钮的窗口矩形。
 */
@Composable
fun ItemActionMenu(
    actions: List<ItemAction>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val anchor = rememberSelectMenuAnchor()

    rememberNativeSelectMenu(
        expanded = expanded,
        onDismiss = { expanded = false },
        anchorBounds = anchor.value.value,
        icons = actions.map { it.icon },
    ) {
        actions.forEach { action ->
            addItem(
                action.text,
                null,
                /* selected = */ false,
                /* enabled = */ action.enabled,
                /* destructive = */ action.destructive,
            ) { action.onClick() }
        }
    }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.then(anchor.modifier),
        ) {
            Icon(
                imageVector = HugeIcons.MoreVertical,
                contentDescription = stringResource(R.string.more_options),
            )
        }
    }
}
