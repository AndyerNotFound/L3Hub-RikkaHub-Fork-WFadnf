package me.rerere.rikkahub.data.datastore

/**
 * 外观类数值的收口：越界的夹到区间里，非有限值（NaN / Infinity）回落到设计值。
 *
 * 高级外观下线后这个函数留在这里：消息样式、气泡、富内容这些**没被下线**的界面还在用它，
 * 包名和函数名保持不变，调用方不用跟着改。参数含义不变：
 *
 * @param min 合法下限（含）
 * @param max 合法上限（含）
 * @param fallback [value] 不是有限数时用的设计值
 */
internal fun finiteAppearanceValue(value: Float, min: Float, max: Float, fallback: Float): Float =
    if (value.isFinite()) value.coerceIn(min, max) else fallback
