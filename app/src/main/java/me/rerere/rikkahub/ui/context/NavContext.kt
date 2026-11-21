package me.rerere.rikkahub.ui.context

import androidx.compose.runtime.compositionLocalOf
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.nav.RootComponent
import me.rerere.rikkahub.ui.pages.setting.isSettingSectionPage

/**
 * 导航入口（页面只认这一层）。
 *
 * 底层换成了 Decompose 的 [com.arkivanov.decompose.router.stack.StackNavigation]，
 * 对外的 API 与 nav3 时代的 Navigator 完全一致，页面调用点零改动。
 * 拆成接口是为了让导出图之类的离线上下文也能塞一个空实现。
 */
interface Navigator {
    fun navigate(screen: Screen, builder: NavigateOptionsBuilder.() -> Unit = {})
    fun clearAndNavigate(screen: Screen)
    fun popBackStack()
}

/**
 * 真正的导航实现。
 *
 * 横屏设置的三栏布局下，详情页挂在右列自己的栈里（见 [RootComponent.detailStack]），
 * 由 [detailActive] 决定一次 navigate 是进根栈还是进右列；右列有页时返回先弹右列，
 * 再弹根栈——与系统返回键的优先级（右列子 context 抬高了一级）保持同一套规则。
 */
class RootNavigator(private val root: RootComponent) : Navigator {

    /** 横屏设置分区打开时由 RouteActivity 写入：详情页路由到右列栈 */
    internal var detailActive: Boolean = false

    private val detailCanPop: Boolean
        get() = root.detailStack.value.items.size > 1

    private val rootTopIsSection: Boolean
        get() = root.childStack.value.active.configuration.isSettingSectionPage()

    override fun navigate(screen: Screen, builder: NavigateOptionsBuilder.() -> Unit = {}) {
        val options = NavigateOptionsBuilder().apply(builder)

        if (options.popUpToScreen != null) {
            // popUpTo 是根栈语义（换分区 / 原地重进），顺手把右列收掉
            navigateRootWithPopUpTo(screen, options)
            root.clearDetail()
            return
        }

        if (detailActive) {
            val detailTop = root.detailStack.value.active.configuration
            if (options.launchSingleTop &&
                detailTop is me.rerere.rikkahub.nav.DetailDestination.Page &&
                detailTop.screen == screen
            ) {
                return
            }
            root.pushDetail(screen)
            return
        }

        if (options.launchSingleTop && root.childStack.value.active.configuration == screen) {
            return
        }
        root.pushRoot(screen)
    }

    private fun navigateRootWithPopUpTo(screen: Screen, options: NavigateOptionsBuilder) {
        val target = options.popUpToScreen ?: return
        val inclusive = options.popUpToInclusive
        val singleTop = options.launchSingleTop
        root.navigateRoot { items ->
            val targetIndex = items.indexOfLast { it == target }
            val kept = if (targetIndex != -1) {
                val removeFromIndex = if (inclusive) targetIndex else targetIndex + 1
                items.subList(0, removeFromIndex).toList()
            } else {
                items
            }
            if (singleTop && kept.lastOrNull() == screen) {
                kept
            } else {
                kept + screen
            }
        }
    }

    override fun clearAndNavigate(screen: Screen) {
        root.clearDetail()
        root.navigateRoot { listOf(screen) }
    }

    override fun popBackStack() {
        if (detailActive && detailCanPop) {
            // 右列有页：先弹右列（与系统返回的优先级一致）
            root.popDetail()
            return
        }
        if (rootTopIsSection) {
            // 离开分区：把右列一起收掉，再弹根栈
            root.clearDetail()
        }
        root.popRoot()
    }
}

class NavigateOptionsBuilder {
    internal var popUpToScreen: Screen? = null
    internal var popUpToInclusive: Boolean = false
    var launchSingleTop: Boolean = false

    fun popUpTo(screen: Screen, builder: PopUpToBuilder.() -> Unit = {}) {
        val options = PopUpToBuilder().apply(builder)
        popUpToScreen = screen
        popUpToInclusive = options.inclusive
    }
}

class PopUpToBuilder {
    var inclusive: Boolean = false
}

val LocalNavController = compositionLocalOf<Navigator> {
    error("No Navigator provided")
}
