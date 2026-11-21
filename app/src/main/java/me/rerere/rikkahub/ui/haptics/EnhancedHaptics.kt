package me.rerere.rikkahub.ui.haptics

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalView
import androidx.core.content.getSystemService
import me.rerere.rikkahub.data.datastore.HapticsStrength

/**
 * 触感强度，取自 ImageToolbox 的 `EnhancedHapticFeedback`（T8RIN，Apache-2.0）。
 *
 * 系统那条路上有两道闸会把手感抹平：`Settings.System.HAPTIC_FEEDBACK_ENABLED`
 * （用户在系统里关了触摸震动）与各家 ROM 的强度档。`FLAG_IGNORE_GLOBAL_SETTING`
 * 绕过第一道，强度档由 [HapticsStrength] 自己给：
 *
 * - [HapticsStrength.SYSTEM]：不接管，沿用 Compose 默认实现（等于没有这个功能）
 * - [HapticsStrength.LIGHT]：`CONTEXT_CLICK`，短促一下
 * - [HapticsStrength.STRONG]：`LONG_PRESS`，重一些
 *
 * 只映射 Compose 明确用到的两类语义（[HapticFeedbackType.LongPress] 与
 * [HapticFeedbackType.TextHandleMove]），其余类型一律落到 `VIRTUAL_KEY`。
 * 不按 Compose 的完整类型列表分支：那套常量在各版本间增删过，
 * 逐个列举会在升级 Compose 时编译失败，而绝大多数调用点本来只发这两种。
 */
class EnhancedHapticFeedback(
    private val strength: HapticsStrength,
    private val view: View,
) : HapticFeedback {

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        val constant = when {
            hapticFeedbackType == HapticFeedbackType.LongPress ->
                if (strength == HapticsStrength.STRONG) {
                    HapticFeedbackConstants.LONG_PRESS
                } else {
                    HapticFeedbackConstants.CONTEXT_CLICK
                }

            hapticFeedbackType == HapticFeedbackType.TextHandleMove ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    HapticFeedbackConstants.TEXT_HANDLE_MOVE
                } else {
                    HapticFeedbackConstants.CLOCK_TICK
                }

            else -> HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performRespectingAccessibility(constant)
    }
}

/**
 * 读屏开着时不做震动：`isTouchExplorationEnabled` 的用户靠语音反馈定位，
 * 手上再抖一下只是干扰。
 */
private fun View.performRespectingAccessibility(constant: Int) {
    runCatching {
        if (context.isTouchExplorationEnabled()) return
        isHapticFeedbackEnabled = true
        performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
    }
}

private fun Context.isTouchExplorationEnabled(): Boolean =
    getSystemService<AccessibilityManager>()?.isTouchExplorationEnabled == true

/**
 * 按当前设置产出一个 [HapticFeedback]；[HapticsStrength.SYSTEM] 返回 null，
 * 调用方据此不接管 [androidx.compose.ui.platform.LocalHapticFeedback]。
 *
 * 用 null 而不是一个空实现：不接管时系统默认实现还在，读屏、键盘和其它平台的
 * 手感不会被我们静音。
 */
@Composable
fun rememberEnhancedHapticFeedback(strength: HapticsStrength): HapticFeedback? {
    val view = LocalView.current
    return remember(strength, view) {
        if (strength == HapticsStrength.SYSTEM) null else EnhancedHapticFeedback(strength, view)
    }
}
