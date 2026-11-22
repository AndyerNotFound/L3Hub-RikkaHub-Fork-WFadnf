package me.rerere.rikkahub.ui.context

import androidx.compose.runtime.compositionLocalOf
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.nav.NavStack
import me.rerere.rikkahub.nav.RootComponent
import me.rerere.rikkahub.ui.pages.setting.hostsDetailColumn
import me.rerere.rikkahub.ui.pages.setting.settingSection

/**
 * 导航入口（页面只认这一层）。
 *
 * 一列一个实例：页面拿到的是"我这一列"的导航器，返回与前进都只作用于所在的那一列，
 * 不再靠一个全局开关猜调用者站在哪个栈里。
 * 拆成接口是为了让导出图之类的离线上下文也能塞一个空实现。
 */
interface Navigator {
    fun navigate(screen: Screen, builder: NavigateOptionsBuilder.() -> Unit = {})
    fun clearAndNavigate(screen: Screen)
    fun popBackStack()
}

/**
 * 根列导航：整宽页面、通知与分享入口、左栏切分区。
 *
 * 一次 navigate 只做一件事：把新路由算进根栈。新路由不承载右列（[hostsDetailColumn]
 * 为假）时顺手清掉右列——右列的内容只属于某一个分区，离开分区上下文就不该留着。
 */
class RootNavigator(private val root: RootComponent) : Navigator {

    override fun navigate(screen: Screen, builder: NavigateOptionsBuilder.() -> Unit) {
        val options = NavigateOptionsBuilder().apply(builder)
        val target = options.popUpToScreen

        if (target != null) {
            // 换分区 / 原地重进：退到目标之后只留新页，右列一起收掉
            root.clearDetail()
            root.navigateRoot { current ->
                NavStack.popUpTo(
                    stack = current,
                    target = target,
                    inclusive = options.popUpToInclusive,
                    screen = screen,
                    singleTop = options.launchSingleTop,
                )
            }
            return
        }

        if (!screen.hostsDetailColumn()) root.clearDetail()
        root.navigateRoot { current -> NavStack.push(current, screen, options.launchSingleTop) }
    }

    override fun clearAndNavigate(screen: Screen) {
        root.clearDetail()
        root.navigateRoot { NavStack.replaceAll(screen) }
    }

    override fun popBackStack() {
        root.popRoot()
    }
}

/**
 * 中列导航：横屏分区首屏。
 *
 * 分区内的下钻进右列；跨分区的跳转（助手详情里的"工作区"这类）落回中列，
 * 免得左栏高亮停在旧分区、右列却在演另一个分区的内容。
 * 这一列上的返回按钮含义是"离开这一节"，不是替右列收尾。
 */
class SectionNavigator(
    private val root: RootComponent,
    private val rootColumn: RootNavigator,
) : Navigator {

    override fun navigate(screen: Screen, builder: NavigateOptionsBuilder.() -> Unit) {
        val options = NavigateOptionsBuilder().apply(builder)
        val section = root.rootStack.lastOrNull()?.settingSection()

        if (section != null && options.popUpToScreen == null && screen.settingSection() == section) {
            root.pushDetail(screen)
            return
        }
        rootColumn.navigate(screen, builder)
    }

    override fun clearAndNavigate(screen: Screen) {
        rootColumn.clearAndNavigate(screen)
    }

    override fun popBackStack() {
        root.clearDetail()
        root.popRoot()
    }
}

/**
 * 右列导航：只做下钻与返回。
 *
 * 右列到底时返回不再前进，避免把中列的分区页一起带走；
 * 需要整宽页面的调用（popUpTo 这类只对根栈有意义的写法）交给根列。
 */
class DetailNavigator(
    private val root: RootComponent,
    private val rootColumn: RootNavigator,
) : Navigator {

    override fun navigate(screen: Screen, builder: NavigateOptionsBuilder.() -> Unit) {
        val options = NavigateOptionsBuilder().apply(builder)
        if (options.popUpToScreen != null) {
            rootColumn.navigate(screen, builder)
            return
        }
        root.pushDetail(screen)
    }

    override fun clearAndNavigate(screen: Screen) {
        rootColumn.clearAndNavigate(screen)
    }

    override fun popBackStack() {
        root.popDetail()
    }
}

/**
 * 三列导航器的入口。页面只拿自己那一列，调用方不需要再记住"现在是几栏"。
 */
class ColumnNavigators(component: RootComponent) {
    val rootColumn = RootNavigator(component)
    val sectionColumn = SectionNavigator(component, rootColumn)
    val detailColumn = DetailNavigator(component, rootColumn)
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
