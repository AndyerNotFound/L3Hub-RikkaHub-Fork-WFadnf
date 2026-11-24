package me.rerere.rikkahub.ui.pages.setting
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType

// 模型卡片上的两个小标签：分组名、覆盖配置类型。卡片本身只摆位置，样式留在这里

/** 同一个模型按分组各存一条时名称一模一样，靠这个标签区分是哪一页的 */
@Composable
internal fun ModelGroupLabelTag(label: String) {
    Tag(type = TagType.DEFAULT) {
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 覆盖配置是提供商的独立副本，卡片上标出它的类型；没有覆盖配置就不占位 */
@Composable
internal fun ModelOverwriteTag(providerOverride: ProviderSetting?) {
    val provider = providerOverride ?: return
    Tag(type = TagType.INFO) {
        Text(
            provider.javaClass.simpleName ?: provider.name
            ?: "ProviderOverwrite"
        )
    }
}
