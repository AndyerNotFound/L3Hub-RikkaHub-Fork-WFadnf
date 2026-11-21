// Ported from ImageToolbox (https://github.com/T8RIN/ImageToolbox), Apache-2.0, Copyright (c) T8RIN (Malik Mukhametzyanov).
/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/LICENSE-2.0>.
 */

package me.rerere.rikkahub.ui.components.ui.switch

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import me.rerere.rikkahub.data.datastore.SwitchType

@Composable
fun SwitchByType(
    switchType: SwitchType,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors? = null,
    interactionSource: MutableInteractionSource? = null,
    colorUnderSwitch: Color = Color.Unspecified,
) {
    val switchColors = colors ?: SwitchDefaults.colors(
        disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurface
            .copy(alpha = 0.12f)
            .compositeOverSafe(MaterialTheme.colorScheme.surface)
    )
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        val switchModifier = modifier
            .minimumInteractiveComponentSize()
            // 本项目用「按钮阴影」设置控制阴影，这里不重复加
        val switchOnCheckedChange: ((Boolean) -> Unit)? = onCheckedChange?.let {
            { boolean ->
                onCheckedChange(boolean)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                focus.clearFocus()
            }
        }

        AnimatedContent(
            targetState = switchType,
            transitionSpec = {
                fadeIn() togetherWith fadeOut() using SizeTransform(false)
            }
        ) { switchType ->
            when (switchType) {
                // 「跟随皮肤」这一档由调用方（AppSwitch）直接走皮肤自己的开关实现，
                // 正常情况下到不了这里；万一被直接调用，退到 Compose 基础款而不是静默不画。
                // 源工程这一档是 MDC 的 MaterialSwitch（`com.google.android.material`），
                // 本仓库没有那个依赖，也不为一个开关把 appcompat 拉进来。
                SwitchType.MATERIAL_YOU -> {
                    ComposeSwitch(
                        modifier = switchModifier,
                        colors = switchColors,
                        checked = checked,
                        enabled = enabled,
                        onCheckedChange = switchOnCheckedChange,
                        interactionSource = interactionSource
                    )
                }

                SwitchType.COMPOSE -> {
                    ComposeSwitch(
                        modifier = switchModifier,
                        colors = switchColors,
                        checked = checked,
                        enabled = enabled,
                        onCheckedChange = switchOnCheckedChange,
                        interactionSource = interactionSource,
                        thumbContent = null
                    )
                }

                SwitchType.PIXEL -> {
                    PixelSwitch(
                        modifier = switchModifier,
                        colors = switchColors,
                        checked = checked,
                        enabled = enabled,
                        onCheckedChange = switchOnCheckedChange,
                        interactionSource = interactionSource
                    )
                }

                SwitchType.FLUENT -> {
                    FluentSwitch(
                        modifier = switchModifier,
                        colors = switchColors,
                        checked = checked,
                        enabled = enabled,
                        onCheckedChange = switchOnCheckedChange,
                        interactionSource = interactionSource
                    )
                }

                SwitchType.CUPERTINO -> {
                    CupertinoSwitch(
                        checked = checked,
                        onCheckedChange = switchOnCheckedChange,
                        modifier = switchModifier,
                        enabled = enabled,
                        interactionSource = interactionSource,
                        colors = CupertinoSwitchDefaults.colors()
                    )
                }

                SwitchType.LIQUID_GLASS -> {
                    LiquidGlassSwitch(
                        checked = checked,
                        onCheckedChange = switchOnCheckedChange,
                        internalModifier = switchModifier,
                        modifier = modifier,
                        enabled = enabled,
                        interactionSource = interactionSource,
                        colors = CupertinoSwitchDefaults.colors(),
                        backgroundColor = colorUnderSwitch.takeOrElse {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                }

                SwitchType.HYPEROS -> {
                    HyperOSSwitch(
                        modifier = switchModifier,
                        colors = switchColors.copy(
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        checked = checked,
                        enabled = enabled,
                        onCheckedChange = switchOnCheckedChange,
                        interactionSource = interactionSource
                    )
                }

                SwitchType.ONE_UI -> {
                    OneUISwitch(
                        modifier = modifier
                            .minimumInteractiveComponentSize(),
                        colors = switchColors.copy(
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        checked = checked,
                        enabled = enabled,
                        onCheckedChange = switchOnCheckedChange,
                        interactionSource = interactionSource
                    )
                }
            }
        }
    }
}
