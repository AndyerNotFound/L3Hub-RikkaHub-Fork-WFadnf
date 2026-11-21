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
 *
 * Note: this is a subset of ImageToolbox's core/ui/.../widget/enhanced/EnhancedHapticFeedback.kt.
 * Vendored: the HapticFeedback extensions (press/longPress) and the
 * hapticsClickable / hapticsCombinedClickable modifiers.
 * Left out: the EnhancedHapticFeedback HapticFeedback implementation,
 * rememberEnhancedHapticFeedback and EmptyHaptics — they call into
 * core.utils.makeLog and the platform View haptics constants.
 */

@file:Suppress("DEPRECATION")
@file:SuppressLint("UnnecessaryComposedModifier")

package com.t8rin.toolbox.ui.widget.enhanced

import android.annotation.SuppressLint
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role

fun HapticFeedback.press() = performHapticFeedback(HapticFeedbackType.TextHandleMove)
fun HapticFeedback.longPress() = performHapticFeedback(HapticFeedbackType.LongPress)

fun Modifier.hapticsClickable(
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    enableHaptics: Boolean = true,
    onClick: () -> Unit
): Modifier = this.composed {
    val haptics = LocalHapticFeedback.current

    Modifier.clickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = {
            if (enableHaptics) haptics.longPress()

            onClick()
        }
    )
}

fun Modifier.hapticsClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = this.composed {
    hapticsClickable(
        interactionSource = null,
        indication = LocalIndication.current,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick
    )
}

fun Modifier.hapticsCombinedClickable(
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit
) = this.composed {
    val haptics = LocalHapticFeedback.current

    Modifier.combinedClickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = if (onLongClick != null) {
            {
                haptics.longPress()
                onLongClick()
            }
        } else null,
        onDoubleClick = if (onDoubleClick != null) {
            {
                haptics.press()
                haptics.longPress()
                onDoubleClick()
            }
        } else null,
        hapticFeedbackEnabled = false,
        onClick = {
            haptics.longPress()
            onClick()
        }
    )
}

fun Modifier.hapticsCombinedClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit
) = this.composed {
    hapticsCombinedClickable(
        interactionSource = null,
        indication = LocalIndication.current,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        onClick = onClick
    )
}
