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

/** 设置右列的栈内容：空槽位 + 真正的页面 */
@Serializable
sealed interface DetailDestination {
    @Serializable
    data object None : DetailDestination

    @Serializable
    data class Page(val screen: Screen) : DetailDestination
}

/**
 * 导航引擎本体：两棵栈，根栈放整宽页面，右列栈只放分区内的下钻页。
 *
 * 这里只做栈本身的操作，不认识"设置分区""该不该挂右列"这类界面语义——
 * 那些判断在 [me.rerere.rikkahub.ui.context.RootNavigator] 与其兄弟里，规则由
 * [NavStack] / [nextBackStep] 两个纯函数给出。
 *
 * 两棵栈都 *不* 自己注册返回键（`handleBackButton = false`）：返回只有一个主人，
 * 见 RouteActivity 里注册的那一个 BackCallback 与 ui/navigation/StackHost.kt 的
 * 预测性返回回调，两者读同一个 [nextBackStep] 结果。
 */
@OptIn(ExperimentalDecomposeApi::class)
class RootComponent(
    componentContext: ComponentContext,
    /** 首屏：onCreate 里同步读偏好决定，进程死亡后由 stateKeeper 恢复整条栈 */
    initialScreen: Screen,
) : ComponentContext by componentContext {

    private val rootNavigation = StackNavigation<Screen>()
    private val detailNavigation = StackNavigation<DetailDestination>()

    private val detailContext: ComponentContext =
        componentContext.childContext(key = "l3hub_settings_detail")

    val childStack: Value<ChildStack<Screen, NavChild>> by lazy {
        childStack(
            source = rootNavigation,
            initialStack = { listOf(initialScreen) },
            serializer = Screen.serializer(),
            handleBackButton = false,
            childFactory = { config, context -> NavChild(context, config) },
        )
    }

    val detailStack: Value<ChildStack<DetailDestination, NavChild>> by lazy {
        detailContext.childStack(
            source = detailNavigation,
            initialStack = { listOf(DetailDestination.None) },
            serializer = DetailDestination.serializer(),
            handleBackButton = false,
            childFactory = { config, context ->
                NavChild(context, (config as? DetailDestination.Page)?.screen)
            },
        )
    }

    /** 根栈路由，栈底在 index 0 */
    val rootStack: List<Screen>
        get() = childStack.value.items.map { it.configuration }

    /** 右列栈内容，空槽位在 index 0 */
    val detailDestinations: List<DetailDestination>
        get() = detailStack.value.items.map { it.configuration }

    // ---- 根栈 ----

    /** 整条根栈一次算出新栈；算成空栈时保持原栈，栈底那一页是退出边界 */
    fun navigateRoot(transformer: (List<Screen>) -> List<Screen>) {
        rootNavigation.navigate { current -> transformer(current).ifEmpty { current } }
    }

    fun popRoot() {
        rootNavigation.navigate { current -> NavStack.pop(current) }
    }

    // ---- 右列 ----

    fun pushDetail(screen: Screen) {
        detailNavigation.navigate { current ->
            val stack = current.ifEmpty { listOf(DetailDestination.None) }
            if (stack.lastOrNull() == DetailDestination.Page(screen)) stack
            else stack + DetailDestination.Page(screen)
        }
    }

    fun popDetail() {
        detailNavigation.navigate { current -> if (current.size > 1) current.dropLast(1) else current }
    }

    /** 回到空槽位：右列只清内容，不动根栈 */
    fun clearDetail() {
        detailNavigation.navigate { current ->
            if (current.size <= 1) current else listOf(DetailDestination.None)
        }
    }
}
