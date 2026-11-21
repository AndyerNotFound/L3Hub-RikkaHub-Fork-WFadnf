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
 * Note: this is a subset of ImageToolbox's core/ui/.../utils/animation/Animations.kt.
 * `fancySlideTransition` and `toolboxPredictiveBackAnimation` were left out because
 * they depend on com.arkivanov.decompose; only the spring specs and
 * `animateFloatingRangeAsState` are vendored here.
 */

@file:Suppress("NOTHING_TO_INLINE")

package com.t8rin.toolbox.ui.utils.animation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember

inline fun <T> springySpec() = spring<T>(
    dampingRatio = 0.35f,
    stiffness = Spring.StiffnessLow
)

inline fun <T> lessSpringySpec() = spring<T>(
    dampingRatio = 0.4f,
    stiffness = Spring.StiffnessLow
)

@Composable
fun animateFloatingRangeAsState(
    range: ClosedFloatingPointRange<Float>,
    animationSpec: AnimationSpec<Float> = spring()
): State<ClosedFloatingPointRange<Float>> {
    val start = animateFloatAsState(
        targetValue = range.start,
        animationSpec = animationSpec
    )

    val end = animateFloatAsState(
        targetValue = range.endInclusive,
        animationSpec = animationSpec
    )

    return remember(start, end) {
        derivedStateOf {
            start.value..end.value
        }
    }
}
