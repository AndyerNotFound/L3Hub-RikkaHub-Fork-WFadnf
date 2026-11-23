package me.rerere.rikkahub.ui.components.ai

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.AppIconBadge
import me.rerere.rikkahub.ui.components.ui.AppSettingsRow

/**
 * 「默认模型」页里那种模型选项：图标、用途说明，下面一颗显示当前模型的胶囊按钮。
 *
 * 全 app 需要选模型的地方都走这里，省得每个页面各拼一份、样式各长一样。
 * 以前走旧 DSL 的 formItem，现在自己拼 [AppSettingsRow]：标题行 + 内容区。
 */
@Composable
fun modelSettingItem(
    icon: ImageVector,
    @StringRes title: Int,
    state: ModelListState,
    @StringRes description: Int? = null,
    iconContainerColor: Color? = null,
    iconContentColor: Color? = null,
    @StringRes placeholder: Int = R.string.model_list_select_model,
    onClear: (() -> Unit)? = null,
    // 清除按钮只有一个叉，描述得由调用方给，才不至于念出英文 Clear
    @StringRes clearContentDescription: Int? = null,
    // 传 id 不传字符串：调用方要在这里拼 res 名，省得每个调用点各写一份
    @StringRes warning: Int? = null,
) {
    AppSettingsRow {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                // 和旧 formItem 一致：带说明文字时，两侧内容靠上对齐
                verticalAlignment = if (description == null && warning == null) {
                    Alignment.CenterVertically
                } else {
                    Alignment.Top
                },
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AppIconBadge(
                    icon = icon,
                    containerColor = iconContainerColor ?: MaterialTheme.colorScheme.primaryContainer,
                    contentColor = iconContentColor ?: MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(stringResource(title))
                    if (description != null || warning != null) {
                        Column {
                            description?.let {
                                Text(
                                    text = stringResource(it),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            warning?.let {
                                Text(stringResource(it), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            ModelField(
                model = state.currentModel,
                onClick = { state.open() },
                placeholder = stringResource(placeholder),
                onClear = onClear,
                clearContentDescription = clearContentDescription?.let { stringResource(it) },
            )
        }
    }
}
