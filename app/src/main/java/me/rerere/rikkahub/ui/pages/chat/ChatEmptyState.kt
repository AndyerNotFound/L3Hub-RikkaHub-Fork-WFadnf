package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.BackgroundSurfaceStyle
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.richtext.richContentMaterial
import me.rerere.rikkahub.ui.components.ui.AppearanceMaterial
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.ui.advancedMaterial
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.modifier.rememberMotionBlurSteps
import me.rerere.rikkahub.ui.theme.rememberEffectsSpec
import me.rerere.rikkahub.ui.theme.rememberMotionBlurSpec
import kotlin.math.roundToInt

// 空态头像与它外圈的光：88dp 头像，外扩 22dp 盛辉光，虚线环压在头像外沿 7dp，
// 三个数和 demo/m3/app.css 的 .empty-halo::before / ::after 的 inset 对得上。
private val HeroAvatarSize = 88.dp
private val HeroGlowReach = 22.dp
private val HeroRingInset = 7.dp

@Composable
internal fun EmptyChatContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    slogan: String,
    hazeState: HazeState?,
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
    hazeState: HazeState?,
) {
    val settings = LocalSettings.current
    val appearance = settings.advancedAppearanceSetting
    val inspiration = appearance.inspirationAppearance
    val bespoke = appearance.enableAdvancedAppearance && !inspiration.followRichContent
    val material = when {
        bespoke -> AppearanceMaterial(BackgroundSurfaceStyle.TRANSLUCENT, inspiration.surfaceOpacity)
        else -> appearance.richContentMaterial()
    }
    val shape = RoundedCornerShape(if (bespoke) inspiration.cornerRadius.dp else 28.dp)
    val scheme = MaterialTheme.colorScheme
    val baseColor = scheme.surfaceContainerLow
    val halo = scheme.primary.copy(alpha = 0.18f)
    val ring = scheme.outlineVariant.copy(alpha = 0.8f)
    Column(
        modifier = Modifier
            .padding(horizontal = 32.dp)
            // 默认外观下这里是空态本身：不留卡片、不描边，只有头像和一束光。
            // 用户开了高级外观才垫一层很淡的底，那是他自己选的表面，不该被这次改版吃掉
            .then(
                if (material != null) {
                    Modifier.clip(shape).advancedMaterial(shape, baseColor, material, hazeState)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            modifier = Modifier
                .size(HeroAvatarSize + HeroGlowReach * 2)
                .drawBehind {
                    // 头像外圈先铺一层主色辉光，到边缘化没（demo 的 .empty-halo::before）
                    val reach = size.minDimension / 2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(halo, Color.Transparent),
                            center = center,
                            radius = reach,
                        ),
                        radius = reach,
                    )
                    // 再压一道虚线圈（demo 的 ::after）。这里只要静态的一圈，
                    // 空态不做无限旋转，省一帧都不画的动画
                    drawCircle(
                        color = ring,
                        radius = (HeroAvatarSize / 2 + HeroRingInset).toPx(),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(5.dp.toPx(), 5.dp.toPx()),
                            ),
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            UIAvatar(
                name = assistant.name,
                value = assistant.avatar,
                avatarSize = HeroAvatarSize,
                shape = CircleShape,
            )
        }
        Text(
            text = slogan.ifBlank { stringResource(R.string.chat_empty_default_slogan) },
            // demo 的 .empty h1：24–30px、600、字距收紧。这里落成 M3 的 headlineSmall 加粗一档
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            color = scheme.onSurface,
        )
    }
}

