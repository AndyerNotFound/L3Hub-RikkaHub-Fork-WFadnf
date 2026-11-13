package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.css
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.hooks.rememberAvatarShape
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.computeAIIconByName
import me.rerere.rikkahub.utils.loadAIIconNames
import me.rerere.rikkahub.utils.toCssHex

@Composable
private fun AIIcon(
    path: String,
    name: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    val contentColor = LocalContentColor.current
    val context = LocalContext.current
    val model = remember(path, contentColor, context) {
        ImageRequest.Builder(context)
            .data("file:///android_asset/icons/$path")
            .css(
                """
                svg {
                  fill: ${contentColor.toCssHex()};
                }
            """.trimIndent()
            )
            .build()
    }
    Surface(
        modifier = modifier.size(24.dp),
        shape = rememberAvatarShape(loading),
        color = color,
    ) {
        AsyncImage(
            model = model,
            contentDescription = name,
            modifier = Modifier.padding(4.dp)
        )
    }
}

@Composable
fun AutoAIIcon(
    name: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    // 指定资源名时直接用这张图，跳过按名字猜图标的流程
    iconAsset: String? = null,
) {
    val context = LocalContext.current
    val iconNames = remember(context) { loadAIIconNames(context) }
    val matched = remember(name, iconNames) { computeAIIconByName(name, iconNames) }
    val path = iconAsset ?: matched ?: run {
        TextAvatar(text = name, modifier = modifier, loading = loading, color = color)
        return
    }
    AIIcon(
        path = path,
        name = name,
        modifier = modifier,
        loading = loading,
        color = color,
    )
}

/** 本地模型没有厂商品牌，按运行时/格式取图标；其余提供商仍照名字匹配。 */
fun ProviderSetting.localModelIconAsset(): String? = when (this) {
    is ProviderSetting.LiteRT -> "litert.svg"
    is ProviderSetting.GGUF -> "llamacpp.svg"
    is ProviderSetting.ONNX -> "onnx.svg"
    else -> null
}

@Preview
@Composable
private fun PreviewAutoAIIcon() {
    Column {
        AutoAIIcon("测试")
    }
}

@Composable
fun SiliconFlowPowerByIcon(modifier: Modifier = Modifier) {
    val darkMode = LocalDarkMode.current
    if (!darkMode) {
        AsyncImage(model = R.drawable.siliconflow_light, contentDescription = null, modifier = modifier)
    } else {
        AsyncImage(model = R.drawable.siliconflow_dark, contentDescription = null, modifier = modifier)
    }
}
