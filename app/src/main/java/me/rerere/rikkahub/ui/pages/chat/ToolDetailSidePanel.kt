package me.rerere.rikkahub.ui.pages.chat

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.message.tools.DefaultToolPreview
import me.rerere.rikkahub.ui.components.message.tools.LocalToolPreviewFullHeight
import me.rerere.rikkahub.ui.components.message.tools.ToolDetailHostState
import me.rerere.rikkahub.ui.components.message.tools.ToolDetailRequest

/**
 * 横屏时的工具调用详情：从右侧滑入，盖在聊天区与右栏之上。
 *
 * 这里是一层普通的 Compose 覆盖，不是 Dialog / BottomSheet 那样另开的窗口，
 * 所以不会把已经隐藏的状态栏重新带出来。
 */
@Composable
internal fun ToolDetailSidePanel(
    host: ToolDetailHostState,
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
            enter = fadeIn(animationSpec = tween(160)),
            exit = fadeOut(animationSpec = tween(160)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { host.request = null },
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(animationSpec = tween(220)) { it } +
                fadeIn(animationSpec = tween(220)),
            exit = slideOutHorizontally(animationSpec = tween(180)) { it } +
                fadeOut(animationSpec = tween(180)),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Surface(
                modifier = Modifier
                    .width(panelWidth)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                tonalElevation = 3.dp,
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
