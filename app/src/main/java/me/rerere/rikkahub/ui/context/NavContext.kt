package me.rerere.rikkahub.ui.context

import androidx.compose.runtime.compositionLocalOf
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.nav.NavStack
import me.rerere.rikkahub.nav.RootComponent

/**
 * 导航入口（页面只认这一层）。
 *
 * 只有一条历史，所以只有一种入口：navigate 往历史尾部加一页，pop 弹一格。这一页最后落在
 * 哪一列由铺开方式决定（见 [me.rerere.rikkahub.nav.NavProjection]），调用方不需要先想想
 * 自己站在第几栏。
 * 拆成接口是为了让导出图之类的离线上下文也能塞一个空实现。
 */
interface Navigator {
    fun navigate(screen: Screen, builder: NavigateOptionsBuilder.() -> Unit = {})
    fun clearAndNavigate(screen: Screen)
    fun popBackStack()
}

/**
 * 唯一的导航器。所有栈操作都落在同一条历史上：
 *
 * - 普通前进：尾部加一页（[NavStack.push]，[NavigateOptionsBuilder.launchSingleTop] 时压掉重复）
 * - 从侧边栏前进：先把侧边栏弹掉再入栈（[NavStack.pushFromSidebar]）—— 侧边栏是入口不是一层
 * - 换分区 / 原地重进：先退到目标再入栈（[NavStack.popUpTo]）
 * - 清空重建：整条历史换成一页
 */
class AppNavigator(private val component: RootComponent) : Navigator {

    override fun navigate(screen: Screen, builder: NavigateOptionsBuilder.() -> Unit) {
        val options = NavigateOptionsBuilder().apply(builder)
        component.navigate { current ->
            val target = options.popUpToScreen
            when {
                target != null -> NavStack.popUpTo(
                    stack = current,
                    target = target,
                    inclusive = options.popUpToInclusive,
                    screen = screen,
                    singleTop = options.launchSingleTop,
                )

                // 从侧边栏往它里面点开一页：侧边栏是入口不是一层，要从历史里退出。
                // 否则「聊天→侧边栏→设置」返回要按两次，而「聊天→设置」只要按一次
                else -> NavStack.pushFromSidebar(
                    stack = current,
                    screen = screen,
                    singleTop = options.launchSingleTop,
                )
            }
        }
    }

    override fun clearAndNavigate(screen: Screen) {
        component.navigate { NavStack.replaceAll(screen) }
    }

    override fun popBackStack() {
        component.pop()
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
