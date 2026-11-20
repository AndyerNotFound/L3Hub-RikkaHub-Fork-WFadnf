package com.l3hub.database.wfadnf.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 与 L3Hub 的 `MaterialExpressiveTheme` 同一套主题：同一份 material3 版本、同一组表达性动效曲线，
 * 动态取色规则也一样（Android 12 以上跟随系统壁纸，否则用基线配色）。
 *
 * 只少了 L3Hub 自己的调色板、AMOLED 与 Miuix 分支：那是聊天应用的外观偏好，数据中心不需要。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HubTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
