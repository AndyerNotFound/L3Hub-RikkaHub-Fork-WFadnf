package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.effectiveBackgroundEffect
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ai.chatInputHaze
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.modifier.rememberMotionBlurSteps
import me.rerere.rikkahub.ui.theme.rememberEffectsSpec
import me.rerere.rikkahub.ui.theme.rememberMotionBlurSpec
import kotlin.math.roundToInt

@Composable
internal fun EmptyChatContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    slogan: String,
    hazeState: HazeState,
    visibleState: MutableTransitionState<Boolean>,
    contentAlpha: Float,
    scrollEnabled: Boolean,
    // 英雄卡底边的窗口坐标：输入框的抬升量按它算，不靠屏幕高度和若干 dp 猜
    onHeroPlaced: (Float) -> Unit,
) {
    val heroBlur = rememberMotionBlurSteps(maxRadius = 8.dp)
    // 模糊的收放跟随动态模糊设置里的时长，透明度仍走动画速度档位
    val enterBlurSpec = rememberMotionBlurSpec()
    val fadeSpec = rememberEffectsSpec<Float>()
    val imeInsets = WindowInsets.ime
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState(), enabled = scrollEnabled)
                .heightIn(min = maxHeight)
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // 首条消息进来时英雄卡不跟着硬切：淡出并糊掉，列表和输入框各自就位
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(fadeSpec),
                exit = fadeOut(fadeSpec),
            ) {
                val blur by transition.animateFloat(
                    transitionSpec = { enterBlurSpec },
                    label = "emptyHeroBlur",
                ) { state ->
                    if (state == EnterExitState.Visible) 0f else 1f
                }
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { onHeroPlaced(it.boundsInRoot().bottom) }
                        .graphicsLayer {
                            // 键盘单独走绘制期位移：英雄卡和整页布局不跟着键盘每帧重排，
                            // 输入区在下面读同一个 inset 一起上移，两者不会差一帧
                            translationY = -(imeInsets.getBottom(this) / 2f)
                                .roundToInt().toFloat()
                            alpha = contentAlpha
                            renderEffect = heroBlur.at(blur)
                        },
                ) {
                    EmptyChatHero(
                        assistant = assistant,
                        slogan = slogan,
                        hazeState = hazeState,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyChatHero(
    assistant: Assistant,
    slogan: String,
    hazeState: HazeState,
) {
    val settings = LocalSettings.current
    val effect = settings.displaySetting.effectiveBackgroundEffect
    val shape = RoundedCornerShape(28.dp)
    val baseColor = MaterialTheme.colorScheme.surfaceContainerLow
    Column(
        modifier = Modifier
            .padding(horizontal = 40.dp)
            .clip(shape)
            .then(
                if (effect != BackgroundEffectType.OFF) {
                    Modifier.chatInputHaze(
                        hazeState = hazeState,
                        displaySetting = settings.displaySetting,
                        containerColor = baseColor,
                        shape = shape,
                    )
                } else {
                    Modifier
                }
            )
            // 描边画在模糊层之后，否则会被糊掉
            .then(
                if (effect == BackgroundEffectType.BLUR) {
                    Modifier.border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        shape,
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UIAvatar(
            name = assistant.name,
            value = assistant.avatar,
            avatarSize = 88.dp,
            shape = CircleShape,
        )
        Text(
            text = slogan.ifBlank { stringResource(R.string.chat_empty_default_slogan) },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

