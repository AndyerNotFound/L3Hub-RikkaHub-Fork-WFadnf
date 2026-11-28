package me.rerere.rikkahub.nav

import me.rerere.rikkahub.Screen

/**
 * 栈操作的纯函数层。
 *
 * 入参是当前栈、出参是新栈，不依赖 Decompose、Compose 或任何 Android API，
 * 所以"点一下应该落到哪一层"的每条规则都能在 JVM 上直接断言。
 * 界面层只负责把结果交给 [com.arkivanov.decompose.router.stack.StackNavigation]。
 *
 * 返回值一律不是空栈：栈底那一页是应用退出的边界，不允许被弹掉。
 */
object NavStack {

    /** 入栈；[singleTop] 且栈顶已经是同一路由时不动，重复点击不会压出两层。 */
    fun push(stack: List<Screen>, screen: Screen, singleTop: Boolean = false): List<Screen> =
        if (singleTop && stack.lastOrNull() == screen) stack else stack + screen

    /**
     * 回退到 [target]，默认保留 [target] 本身；[inclusive] 为真时连它一起弹掉。
     * 新的栈顶是 [screen]（[singleTop] 且恰好相同则不重复入栈）。
     * [target] 不在栈里时只做入栈、不做裁剪——这是写死的语义，不是兜底。
     */
    fun popUpTo(
        stack: List<Screen>,
        target: Screen,
        inclusive: Boolean,
        screen: Screen,
        singleTop: Boolean,
    ): List<Screen> {
        val index = stack.indexOfLast { it == target }
        val kept = if (index >= 0) stack.take(if (inclusive) index else index + 1) else stack
        return push(kept, screen, singleTop)
    }

    /** 整条栈换成一页（换会话、清空历史）。 */
    fun replaceAll(screen: Screen): List<Screen> = listOf(screen)

    /**
     * 从侧边栏往它里面点开一页。
     *
     * 侧边栏是**入口**，不是一层：从它进设置、历史、助手这些页时，
     * 侧边栏本身要从历史里退出。否则返回要按两次——先回到侧边栏、再回到聊天，
     * 而同一个设置页如果从聊天页直接进去，返回只要按一次，两条路径行为不一致。
     *
     * 规则：
     * - 栈顶是侧边栏 → 先弹掉它再入栈（`[Chat, Sidebar]` → `[Chat, Setting]`）
     * - 栈顶不是侧边栏 → 与普通 [push] 一致
     *
     * 栈底不动：侧边栏不会成为栈底，它总是压在某一页之上。
     */
    fun pushFromSidebar(
        stack: List<Screen>,
        screen: Screen,
        singleTop: Boolean = false,
    ): List<Screen> {
        val withoutSidebar =
            if (stack.lastOrNull() is Screen.Sidebar) stack.dropLast(1) else stack
        // 弹完只剩空栈时退回原栈：栈底那一页是退出边界，不能被这一条规则吃掉
        val base = withoutSidebar.ifEmpty { stack }
        return push(base, screen, singleTop)
    }

    /** 弹一层；栈底不动。 */
    fun pop(stack: List<Screen>): List<Screen> = if (stack.size > 1) stack.dropLast(1) else stack
}
