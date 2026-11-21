package me.rerere.rikkahub.ui.hooks

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionDefaults
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.LocalSharedTransitionScope
import me.rerere.rikkahub.ui.pages.mediacreation.mediaCreationHeroKey
import me.rerere.rikkahub.ui.theme.rememberSpatialSpec
import me.rerere.rikkahub.ui.theme.adaptiveCornerRadius
import me.rerere.rikkahub.ui.theme.adaptiveShape

/** 源条目侧的圆角。页面到位后收到 0；返回时跟着容器一起长回这个值，落回条目那一帧才对得上。 */
val HeroSourceCorner = 20.dp

/**
 * 这一页是不是「被弹出离场」。
 *
 * 由 `rememberAppNavEntryDecorators()` 里的离场标记提供：离场且已经不在返回栈里。
 * 被新页盖住的那一页仍是
 * false —— 它得留着页内共享元素源那一半，否则进场的元素动画没有东西可配。
 */
val LocalNavEntryPopped = compositionLocalOf { false }

@Composable
fun Modifier.heroAnimation(
    key: Any,
    boundsTransform: BoundsTransform = SharedTransitionDefaults.BoundsTransform,
): Modifier {
    // 返回（这一页正在被弹出）不再配对：返回由整页容器变换收回条目演，
    // 页内元素再飞一次就成了两套过渡一起演
    if (LocalNavEntryPopped.current) return Modifier
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current
    return with(sharedTransitionScope) {
        this@heroAnimation.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = boundsTransform,
        )
    }
}

/**
 * 容器变换：同一个 key 的两个容器（比如列表项和它打开的整页）在页面切换时互相展开、收回，内容交叉淡入淡出。
 * 两边都按宽度缩放、顶部对齐，不会在动画的每一帧重新测量整页。
 */
@Composable
fun Modifier.heroContainer(
    key: Any,
    shape: Shape,
): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current
    return with(sharedTransitionScope) {
        this@heroContainer.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedVisibilityScope,
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter),
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

/**
 * 目标页面侧的容器变换：把整页包进去，它就从源条目（同一个 [heroKey]）展开，返回时收回条目里。
 *
 * 两个方向都参与共享元素：进场展开、返回收回，是同一段容器变换的正反两面。
 * display 级（RouteActivity 的 popTransitionSpec）只留一个不位移的退场窗口，不再用位移演返回。
 */
@Composable
fun HeroPage(
    heroKey: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // 全宽组件模式下条目是直角，页面起始形状得跟着它，否则展开的第一帧会对不上
    val sourceCorner = adaptiveCornerRadius(HeroSourceCorner)
    // 圆角也用同一档速度，关掉动效时它是 snap，不会留一段 300ms 的收角
    val cornerSpec = rememberSpatialSpec<Dp>()
    val transition = LocalNavAnimatedContentScope.current.transition
    // 两个方向都收角：进场从条目的圆角收到直角，返回时再长回条目的圆角，
    // 收起的那一帧才和它落进去的条目对得上。原来离场停在直角，是因为返回靠整页下滑演；
    // 下滑撤掉以后，收角与收回条目必须是同一段动画
    val corner by transition.animateDp(
        transitionSpec = { cornerSpec },
        label = "heroPageCorner",
    ) { state ->
        if (state == EnterExitState.Visible) 0.dp else sourceCorner
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            // 弹簧会过冲，负半径会让形状构造抛异常
            // 起始形状跟着当前轮廓类型走：选了切角 / 连续圆角时，展开的第一帧才不会对不上条目
            .heroPageContainer(heroKey, adaptiveShape(corner.coerceAtLeast(0.dp))),
    ) {
        content()
    }
}

/**
 * 页面侧（共享元素的目标那一半）的容器变换：进场展开、返回收回，两个方向都套着。
 *
 * `sharedBounds` 没有方向参数，配对了就两向都演。上一版在退场时把这一半摘掉，返回只剩 display 级
 * 的位移，于是同一类页面进场是展开、退场是下滑，两套过渡各演一次；现在留着一半，
 * 返回与进场是同一段容器变换的正反两面，摘掉的动作只发生在页面离开组合的时候。
 */
@Composable
fun Modifier.heroPageContainer(key: Any, shape: Shape): Modifier = heroContainer(key, shape)

/** 源条目侧：和 [HeroPage] 成对使用，shape 要和条目自身的圆角一致。 */
@Composable
fun Modifier.heroSource(
    screen: Screen,
    shape: Shape? = null,
): Modifier = heroContainer(
    screen.heroKey(),
    // 没传形状时按主题当前的轮廓类型造，和条目自己画的那个一致
    shape ?: adaptiveShape(adaptiveCornerRadius(HeroSourceCorner)),
)

/**
 * 这一页是不是靠容器变换进出：从卡片 / 选项卡展开成整页，返回时收回那一块。
 *
 * 判据必须与 `RouteActivity.kt` 的入口清单一致：走 `heroEntry` 的页面都算 [true]；
 * 走普通 `entry` 的会话页与设置首页是 [false]——它们是滑入的，退场要滑回原来那一侧。
 * `Screen.MediaCreation` 虽然注册成普通 `entry`，但它在页面内部用 sharedBounds 展开，
 * 同样算容器变换页（它自带 entry 级 metadata，display 级的进场规格不作用于它）。
 */
fun Screen.usesContainerTransform(): Boolean = when (this) {
    is Screen.Chat -> false
    Screen.Setting -> false
    else -> true
}

/**
 * 每个 Screen 一个稳定 key：源条目和目标页面各算一次，两边必须完全一致。
 *
 * 新增 Screen 时这里会编译不过，正好逼着补一条——key 撞车会让两个页面的容器变换互相串台。
 */
fun Screen.heroKey(): String = when (this) {
    is Screen.Chat -> "hero_chat_$id"
    is Screen.ShareHandler -> "hero_share_handler"
    Screen.History -> "hero_history"
    Screen.Favorite -> "hero_favorite"
    Screen.Assistant -> "hero_assistant"
    is Screen.AssistantDetail -> "hero_assistant_detail_$id"
    is Screen.AssistantBasic -> "hero_assistant_basic_$id"
    is Screen.AssistantPrompt -> "hero_assistant_prompt_$id"
    is Screen.AssistantMemory -> "hero_assistant_memory_$id"
    is Screen.MemoryFile -> "hero_memory_file_${memoryId}_${path.orEmpty()}"
    is Screen.AssistantRequest -> "hero_assistant_request_$id"
    is Screen.AssistantHeartbeat -> "hero_assistant_heartbeat_$id"
    is Screen.AssistantMcp -> "hero_assistant_mcp_$id"
    is Screen.AssistantLocalTool -> "hero_assistant_local_tool_$id"
    is Screen.AssistantInjections -> "hero_assistant_injections_$id"
    Screen.Translator -> "hero_translator"
    Screen.Setting -> "hero_setting"
    Screen.Backup -> "hero_backup"
    Screen.ImageGen -> "hero_image_gen"
    Screen.MediaCreationSessions -> "hero_media_creation_sessions"
    // 会话列表那侧用的就是 mediaCreationHeroKey，这里保持一致，免得同一对元素两个 key
    is Screen.MediaCreation -> mediaCreationHeroKey(id)
    is Screen.WebView -> "hero_webview_${contentId}_$url"
    Screen.SettingTheme -> "hero_setting_theme"
    Screen.SettingPalette -> "hero_setting_palette"
    Screen.SettingPreferences -> "hero_setting_preferences"
    Screen.SettingPreferencesTheme -> "hero_setting_preferences_theme"
    Screen.SettingPreferencesNotification -> "hero_setting_preferences_notification"
    Screen.SettingPreferencesGeneral -> "hero_setting_preferences_general"
    Screen.SettingPreferencesUI -> "hero_setting_preferences_ui"
    Screen.SettingPreferencesNetwork -> "hero_setting_preferences_network"
    Screen.SettingProvider -> "hero_setting_provider"
    is Screen.SettingProviderDetail -> "hero_setting_provider_detail_$providerId"
    Screen.SettingModels -> "hero_setting_models"
    is Screen.ModelEditor -> "hero_model_editor_${providerId}_${modelId.orEmpty()}"
    Screen.SettingAbout -> "hero_setting_about"
    Screen.SettingSearch -> "hero_setting_search"
    is Screen.SettingSearchDetail -> "hero_setting_search_detail_$serviceId"
    Screen.SettingSpeech -> "hero_setting_speech"
    Screen.SettingMedia -> "hero_setting_media"
    is Screen.SettingMediaProviderDetail -> "hero_setting_media_provider_detail_$providerId"
    Screen.SettingMcp -> "hero_setting_mcp"
    Screen.ToolOverrides -> "hero_tool_overrides"
    is Screen.SettingMcpDetail -> "hero_setting_mcp_detail_$serverId"
    Screen.SettingDonate -> "hero_setting_donate"
    Screen.SettingFiles -> "hero_setting_files"
    Screen.SettingWeb -> "hero_setting_web"
    Screen.Debug -> "hero_debug"
    Screen.Log -> "hero_log"
    Screen.Extensions -> "hero_extensions"
    Screen.QuickMessages -> "hero_quick_messages"
    Screen.Prompts -> "hero_prompts"
    Screen.Skills -> "hero_skills"
    Screen.Workspaces -> "hero_workspaces"
    is Screen.WorkspaceDetail -> "hero_workspace_detail_$id"
    is Screen.WorkspaceTerminal -> "hero_workspace_terminal_$id"
    is Screen.WorkspaceSshEditor -> "hero_workspace_ssh_editor_${id.orEmpty()}"
    is Screen.WorkspaceFileEditor -> "hero_workspace_file_editor_${id}_${area}_$path"
    is Screen.SkillDetail -> "hero_skill_detail_$skillName"
    Screen.MessageSearch -> "hero_message_search"
    Screen.Stats -> "hero_stats"
}
