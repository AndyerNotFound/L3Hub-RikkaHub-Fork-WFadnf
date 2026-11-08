package me.rerere.rikkahub.ui.activity

import android.content.pm.ActivityInfo
import me.rerere.rikkahub.data.datastore.DisplayMode

/**
 * 显示模式落到 Activity 上的方向请求，由 RouteActivity 在设置变化时应用。
 *
 * 固定竖屏 / 横屏从 Android 16 起在最小宽度 ≥ 600dp 的屏幕上会被系统忽略
 * （targetSdk 36+ 的适配规则，没有可长期保留的开关），手机上照常生效；
 * [DisplayMode.AUTO] 与 [DisplayMode.SYSTEM] 用的两个值不在忽略名单里，任何屏幕都有效。
 */
internal val DisplayMode.activityOrientation: Int
    get() = when (this) {
        DisplayMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        DisplayMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        // 跟着重力感应走，系统方向锁拦不住；不转成倒竖屏
        DisplayMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        // 不表态：系统的自动旋转开关说了算，也是没设置过时的默认值
        DisplayMode.SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
