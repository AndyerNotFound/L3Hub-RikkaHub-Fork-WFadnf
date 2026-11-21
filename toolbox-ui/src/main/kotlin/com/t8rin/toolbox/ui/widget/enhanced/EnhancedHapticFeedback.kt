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
 * Only the HapticFeedback extensions are vendored; the EnhancedHapticFeedback
 * implementation and rememberEnhancedHapticFeedback were left out because they
 * depend on core.utils.Logger / LogsWriter.
 */

package com.t8rin.toolbox.ui.widget.enhanced

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

fun HapticFeedback.press() = performHapticFeedback(HapticFeedbackType.TextHandleMove)
fun HapticFeedback.longPress() = performHapticFeedback(HapticFeedbackType.LongPress)
