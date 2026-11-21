package me.rerere.rikkahub.ui.theme

import com.t8rin.toolbox.ui.model.IconShape

/**
 * 引擎的 [IconShape] 与设置里那个可序列化的键之间的对照。
 *
 * [IconShape] 是「形状 + 内边距 + 图标尺寸」的 data class，里面握着一个 Compose `Shape`，
 * 塞不进 DataStore；而 `IconShape.entriesNoRandom` 里那 51 个常量也都**没有名字**，
 * 只有列表里的位置（圆角/切角几个是现写的 `RoundedCornerShape`，后面 25 个是
 * `MaterialShapes` 逐个 `toShape()`）。所以键只能按位置生成：
 *
 * - `"entry:N"` → `entriesNoRandom[N]`
 * - `"random"`  → `IconShape.Random`（每次取一个别的形状，引擎自己在预览时随机）
 * - `null`      → 不带形状容器（引擎的「无」）
 *
 * 位置和 vendors 的列表同序：换引擎版本、或者有人往列表中间插一项，键就会指到别的形状上。
 * 那属于「跟着引擎走」的取舍——列表在 toolbox-ui/.../ui/model/IconShape.kt，动它的时候这里要一起对。
 */
object ToolboxIconShapes {
    private const val ENTRY_PREFIX = "entry:"
    private const val RANDOM_KEY = "random"

    /** 可选的全部形状，不含随机档 */
    val entries: List<IconShape> get() = IconShape.entriesNoRandom

    /** 随机档不在 [entries] 里，单独摆一个按钮 */
    val random: IconShape get() = IconShape.Random

    /** 用来比较选中状态；认不出的形状返回 null，界面按「没选」处理 */
    fun keyOf(shape: IconShape?): String? = when {
        shape == null -> null
        shape == IconShape.Random -> RANDOM_KEY
        else -> entries.indexOf(shape).takeIf { it >= 0 }?.let { ENTRY_PREFIX + it }
    }

    fun resolve(key: String?): IconShape? {
        val name = key ?: return null
        if (name == RANDOM_KEY) return IconShape.Random
        if (!name.startsWith(ENTRY_PREFIX)) return null
        val index = name.removePrefix(ENTRY_PREFIX).toIntOrNull() ?: return null
        return entries.getOrNull(index)
    }
}
