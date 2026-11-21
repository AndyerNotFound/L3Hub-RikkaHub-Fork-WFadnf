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
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackHandler
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.Screen

/**
 * 每个导航目的地一个 [NavChild]：包住 Decompose 给的 child ComponentContext，
 * 并把 Android 的 [ViewModelStore] 挂在 InstanceKeeper 上——
 * koinViewModel() 默认读 LocalViewModelStoreOwner，每个页面因此拿到自己的一份 store，
 * 生命周期与 nav3 的 ViewModelStore decorator 对齐：随栈位置存活，出栈时清掉。
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
 * 导航引擎本体（ImageToolbox 同款 Decompose 结构）：
 *
 * - 根栈：[StackNavigation] + [childStack]，child 实例直接包成 [NavChild]；
 *   `handleBackButton = true` 让 Decompose 接管系统返回（栈深 > 1 才启用）；
 * - 右列栈：横屏设置分区时详情页进这里；不注册返回键，由 RouteActivity 的
 *   `BackCallback`（优先级 1，随布局开关）接管，模型编辑占根栈时不抢返回。
 */
class RootComponent(
    componentContext: ComponentContext,
    /** 首屏：onCreate 里同步读偏好决定，进程死亡后由 stateKeeper 恢复整条栈 */
    initialScreen: Screen,
) : ComponentContext by componentContext {

    private val rootNavigation = StackNavigation<Screen>()
    private val detailNavigation = StackNavigation<DetailDestination>()

    @OptIn(ExperimentalDecomposeApi::class)
    private val detailContext: ComponentContext =
        componentContext.childContext(
            key = "l3hub_settings_detail",
            backHandlerPriority = 1,
        )

    val detailBackHandler: BackHandler = detailContext.backHandler

    val childStack: Value<ChildStack<Screen, NavChild>> by lazy {
        childStack(
            source = rootNavigation,
            initialStack = { listOf(initialScreen) },
            serializer = Screen.serializer(),
            handleBackButton = true,
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

    // ---- 根栈操作 ----

    fun pushRoot(screen: Screen) {
        rootNavigation.pushNew(screen)
    }

    fun navigateRoot(transformer: (List<Screen>) -> List<Screen>) {
        rootNavigation.navigate(transformer)
    }

    fun popRoot() {
        rootNavigation.pop()
    }

    // ---- 右列操作 ----

    fun pushDetail(screen: Screen) {
        detailNavigation.pushNew(DetailDestination.Page(screen))
    }

    fun popDetail() {
        detailNavigation.pop()
    }

    fun clearDetail() {
        detailNavigation.navigate { listOf(DetailDestination.None) }
    }
}
