package me.rerere.rikkahub.ui.pages.chat

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.rerere.rikkahub.ui.theme.LocalUiSlots
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.message.tools.DefaultToolPreview
import me.rerere.rikkahub.ui.components.message.tools.LocalToolPreviewFullHeight
import me.rerere.rikkahub.ui.components.message.tools.ToolDetailHostState
import me.rerere.rikkahub.ui.components.message.tools.ToolDetailRequest
import me.rerere.rikkahub.ui.components.ui.advancedMaterial
import me.rerere.rikkahub.ui.components.ui.overlayMaterial
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.rememberEffectsSpec
import me.rerere.rikkahub.ui.theme.rememberSpatialSpec

/**
 * 横屏时的工具调用详情：从右侧滑入，盖在聊天区与右栏之上。
 *
 * 这里是一层普通的 Compose 覆盖，不是 Dialog / BottomSheet 那样另开的窗口，
 * 所以不会把已经隐藏的状态栏重新带出来。
 *
 * [hazeState] 是同一会话里含消息正文的那一份 Haze 源（ChatPage 的 chrome 源）：
 * 侧栏压着聊天区，要糊出正文，就不能只取页面背景那一份。没有源时
 * [advancedMaterial] 会降级成静态填充，内容照样读得清。
 */
@Composable
internal fun ToolDetailSidePanel(
    host: ToolDetailHostState,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    val request = host.request
    // 收起动画期间 request 已经是 null，用最后一份内容把退场演完
    var shown by remember { mutableStateOf<ToolDetailRequest?>(null) }
    LaunchedEffect(request) {
        if (request != null) shown = request
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(landscape) {
        if (!landscape) host.request = null
    }
    val visible = request != null && landscape

    BackHandler(enabled = visible) { host.request = null }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val panelWidth = (maxWidth * 0.42f).coerceIn(360.dp, 560.dp)

        AnimatedVisibility(
            visible = visible,
            // 遮罩和面板取同一档曲线与时长：进出场同参，收起不会比展开快一半
            enter = fadeIn(animationSpec = rememberEffectsSpec()),
            exit = fadeOut(animationSpec = rememberEffectsSpec()),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LocalUiSlots.current.scrim.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { host.request = null },
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(animationSpec = rememberSpatialSpec<IntOffset>()) { it } +
                fadeIn(animationSpec = rememberEffectsSpec()),
            exit = slideOutHorizontally(animationSpec = rememberSpatialSpec<IntOffset>()) { it } +
                fadeOut(animationSpec = rememberEffectsSpec()),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            val scheme = MaterialTheme.colorScheme
            val shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
            // 高级外观开着时底交给 overlayMaterial；关着时 material 为 null，
            // 下面 Surface 用原来的实色 surface + tonalElevation 兜底，旧观感一像素不动
            val material = LocalSettings.current.advancedAppearanceSetting
                .takeIf { it.enableAdvancedAppearance }?.overlayMaterial()
            val panel = if (material != null) {
                Modifier.advancedMaterial(
                    shape = shape,
                    base = scheme.surface,
                    material = material,
                    hazeState = hazeState,
                    // 面板压着聊天区正文，着色太淡就分不出底板和下面的字
                    readableFloor = .62f,
                )
            } else Modifier
            Surface(
                modifier = Modifier
                    .width(panelWidth)
                    .fillMaxHeight()
                    .then(panel),
                color = if (material == null) scheme.surface else Color.Transparent,
                contentColor = scheme.onSurface,
                shape = shape,
                tonalElevation = if (material == null) 3.dp else 0.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        // 空处也要吃掉点击，不然会漏到面板下面的聊天区
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {},
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(start = 20.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = shown?.context?.tool?.toolName.orEmpty(),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { host.request = null }) {
                            Icon(
                                imageVector = HugeIcons.Cancel01,
                                contentDescription = stringResource(R.string.close),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        // 委托属性不能智能转换，取一份局部值再判空
                        val detail = shown
                        if (detail != null) {
                            CompositionLocalProvider(
                                LocalToolPreviewFullHeight provides true,
                            ) {
                                if (detail.outputUnparsable) {
                                    DefaultToolPreview(context = detail.context)
                                } else {
                                    detail.renderer.Preview(
                                        context = detail.context,
                                        onDismissRequest = { host.request = null },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
