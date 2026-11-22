package me.rerere.rikkahub.nav

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.Screen

/**
 * 每个导航目的地一个 [NavChild]：包住 Decompose 给的 child ComponentContext，
 * 并把 Android 的 [ViewModelStore] 挂在 InstanceKeeper 上——
 * koinViewModel() 默认读 LocalViewModelStoreOwner，每个页面因此拿到自己的一份 store，
 * 生命周期与栈位置对齐：随栈位置存活，出栈时清掉。
 */
class NavChild(
    val context: ComponentContext,
    /** 该页对应的路由；右列的空槽位（[DetailDestination.None]）为 null，渲染成空盒子 */
    val screen: Screen?,
) : ViewModelStoreOwner {

    private val storeInstance: ViewModelStoreInstance =
        context.instanceKeeper.getOrCreate(VIEW_MODEL_STORE_KEY) { ViewModelStoreInstance() }

    override val viewModelStore: ViewModelStore
        get() = storeInstance.store

    /** essenty 的 Instance 回调：child 出栈销毁时清掉全部 ViewModel（onCleared 会触发） */
    private class ViewModelStoreInstance : InstanceKeeper.Instance {
        val store = ViewModelStore()

        override fun onDestroy() {
            store.clear()
        }
    }

    private companion object {
        const val VIEW_MODEL_STORE_KEY = "l3hub_view_model_store"
    }
}

/** 右列槽位：空槽位 + 真正承担的页 */
@Serializable
sealed interface DetailDestination {
    @Serializable
    data object None : DetailDestination

    @Serializable
    data class Page(val screen: Screen) : DetailDestination
}

/**
 * 导航引擎：一条历史，两列铺开。
 *
 * 历史只有一份——[logicalStack]，等于两列的栈拼起来（主列在前、右列在后）。所有前进后退
 * 都只改这一份，改完按当前窗口宽窄重新铺开（[setColumnsExpanded]）；屏幕宽窄只决定同时
 * 看见几页，不决定「这一页归哪条栈」。
 *
 * 两列各自的 [ChildStack] 仍在：Decompose 一个 Children 一次只动画一层，两列各有自己的
 * 转场与预测性返回。它们的内容是历史的两段切片（[NavProjection]），不是两份过去。
 *
 * 两棵栈都 *不* 自己注册返回键（`handleBackButton = false`）：返回只有一个主人，
 * 见 RouteActivity 里注册的那一个 BackCallback 与 ui/navigation/StackHost.kt 的
 * 预测性返回回调，两者读同一个 [nextBackStep]。
 */
@OptIn(ExperimentalDecomposeApi::class)
class RootComponent(
    componentContext: ComponentContext,
    /** 首屏：onCreate 里同步读偏好决定，进程死亡后由 stateKeeper 恢复整条栈 */
    initialScreen: Screen,
    /** 能不能当中列的分区首屏；判据由界面层给（哪一页属于哪个分区是页面自己的事） */
    private val isColumnHost: (Screen) -> Boolean = { false },
) : ComponentContext by componentContext {

    private val mainNavigation = StackNavigation<Screen>()
    private val sideNavigation = StackNavigation<DetailDestination>()

    private val sideContext: ComponentContext =
        componentContext.childContext(key = "l3hub_settings_detail")

    /** 当前是否按宽屏铺开；只影响投影，不影响历史。null 表示还没量过窗口 */
    private var expanded: Boolean? = null

    val childStack: Value<ChildStack<Screen, NavChild>> by lazy {
        childStack(
            source = mainNavigation,
            initialStack = { listOf(initialScreen) },
            serializer = Screen.serializer(),
            handleBackButton = false,
            childFactory = { config, context -> NavChild(context, config) },
        )
    }

    val sideStack: Value<ChildStack<DetailDestination, NavChild>> by lazy {
        sideContext.childStack(
            source = sideNavigation,
            initialStack = { listOf(DetailDestination.None) },
            serializer = DetailDestination.serializer(),
            handleBackButton = false,
            childFactory = { config, context ->
                NavChild(context, (config as? DetailDestination.Page)?.screen)
            },
        )
    }

    /** 主列承担的历史段，栈底在 index 0 */
    val mainStack: List<Screen>
        get() = childStack.value.items.map { it.configuration }

    /** 右列槽位：空槽位在 index 0 */
    val sideDestinations: List<DetailDestination>
        get() = sideStack.value.items.map { it.configuration }

    /** 右列真正承担的页（空槽位不算） */
    val sideScreens: List<Screen>
        get() = sideDestinations.mapNotNull { (it as? DetailDestination.Page)?.screen }

    /** 唯一的历史：两列拼起来，栈底在前 */
    val logicalStack: List<Screen>
        get() = mainStack + sideScreens

    /** 历史深度，返回键与预测性返回都读它 */
    val depth: Int
        get() = logicalStack.size

    /**
     * 按窗口宽窄铺开。真正发生变化的只有拆分方式：转屏时栈尾那几页会在两列之间搬一次位置，
     * 页面内的 ViewModel 会随之重建一次——这是搬列的唯一代价。
     */
    fun setColumnsExpanded(value: Boolean) {
        if (expanded == value) return
        val firstMeasure = expanded == null
        expanded = value
        // 首次量到窗口只记下来：恢复出来的栈本来就是上一轮的拆分结果，再拆一次只会让
        // child 白白搬一次位置。启动首帧因此不会闪
        if (!firstMeasure) apply(logicalStack)
    }

    /** 对历史做一次变换；算成空栈时保持原栈，栈底那一页是退出边界 */
    fun navigate(transformer: (List<Screen>) -> List<Screen>) {
        val next = transformer(logicalStack)
        if (next == logicalStack) return
        apply(next.ifEmpty { logicalStack })
    }

    fun pop() {
        val current = logicalStack
        if (current.size <= 1) return
        apply(current.dropLast(1))
    }

    /** 把一条历史按当前铺开方式分给两列 */
    private fun apply(logical: List<Screen>) {
        val projection = if (expanded == true) {
            NavProjection.masterDetail(logical, isColumnHost)
        } else {
            NavProjection.single(logical)
        }
        mainNavigation.navigate { current -> if (current == projection.root) current else projection.root }
        val side: List<DetailDestination> = if (projection.detail.isEmpty()) {
            listOf(DetailDestination.None)
        } else {
            listOf(DetailDestination.None) + projection.detail.map { DetailDestination.Page(it) }
        }
        sideNavigation.navigate { current -> if (current == side) current else side }
    }
}
