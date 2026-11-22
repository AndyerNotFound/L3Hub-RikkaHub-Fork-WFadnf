@file:OptIn(ExperimentalDecomposeApi::class)

package me.rerere.rikkahub

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.arkivanov.decompose.DecomposeSettings
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.StackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.retainedComponent
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.AppLockStateStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker
import me.rerere.rikkahub.data.db.MigrationState
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.repository.MediaCreationRepository
import me.rerere.rikkahub.service.MediaCreationForegroundService
import me.rerere.rikkahub.ui.activity.AppLockActivity
import me.rerere.rikkahub.ui.activity.AppLockScreen
import me.rerere.rikkahub.ui.activity.SafeModeActivity
import me.rerere.rikkahub.ui.activity.activityOrientation
import me.rerere.rikkahub.ui.components.ui.ColumnResizeHandle
import me.rerere.rikkahub.ui.components.ui.TTSController
import me.rerere.rikkahub.ui.context.ColumnNavigators
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.nav.DetailDestination
import me.rerere.rikkahub.nav.NavChild
import me.rerere.rikkahub.nav.RootComponent
import me.rerere.rikkahub.ui.hooks.readBooleanPreference
import me.rerere.rikkahub.ui.hooks.readStringPreference
import me.rerere.rikkahub.ui.hooks.rememberCustomAsrState
import me.rerere.rikkahub.ui.hooks.rememberCustomTtsState
import me.rerere.rikkahub.ui.navigation.ScreenContent
import me.rerere.rikkahub.ui.navigation.NavigationBackHandler
import me.rerere.rikkahub.ui.navigation.l3HubDetailAnimation
import me.rerere.rikkahub.ui.navigation.l3HubStackAnimation
import me.rerere.rikkahub.ui.pages.setting.SettingRail
import me.rerere.rikkahub.ui.pages.setting.isSettingBoard
import me.rerere.rikkahub.ui.pages.setting.hostsDetailColumn
import me.rerere.rikkahub.ui.theme.LocalButtonShadow
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.ui.theme.slideDurationMillis
import me.rerere.rikkahub.ui.theme.rememberEffectsSpec
import me.rerere.rikkahub.utils.AppLanguage
import me.rerere.rikkahub.utils.CrashHandler
import me.rerere.rikkahub.utils.getActivity
import me.rerere.rikkahub.utils.openUsageAccessSettings
import me.rerere.ui.toast.Toaster
import me.rerere.ui.toast.rememberToasterState
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private const val TAG = "RouteActivity"
private const val ACTION_TRANSLATE = "me.rerere.rikkahub.action.TRANSLATE"
private const val ACTION_MEDIA_CREATION = "me.rerere.rikkahub.action.MEDIA_CREATION"
private const val EXTRA_CONVERSATION_ID = "conversationId"

/** 打开指定会话的聊天页 */
fun conversationIntent(context: Context, conversationId: String): Intent =
    Intent(context, RouteActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(EXTRA_CONVERSATION_ID, conversationId)
    }

class RouteActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    private val okHttpClient by inject<OkHttpClient>()
    private val settingsStore by inject<SettingsStore>()
    private val appLockState by lazy { AppLockStateStore(this) }
    private val mediaCreationRepository by inject<MediaCreationRepository>()
    private val rootComponent: RootComponent by lazy {
        retainedComponent { context ->
            RootComponent(
                componentContext = context,
                initialScreen = startScreen(),
            )
        }
    }

    /** 三列各自的导航器：只读栈、不缓存状态；Activity 级入口（通知、分享、快捷方式）用 rootColumn */
    private val navColumns: ColumnNavigators by lazy { ColumnNavigators(rootComponent) }

    /** 首屏由偏好同步决定；进程死亡后整条栈走 Decompose stateKeeper 恢复 */
    private fun startScreen(): Screen =
        Screen.Chat(
            id = if (readBooleanPreference("create_new_conversation_on_start", true)) {
                Uuid.random().toString()
            } else {
                readStringPreference("lastConversationId") ?: Uuid.random().toString()
            }
        )

    // Volume key listener registry — last registered handler wins
    internal val volumeKeyListeners = mutableListOf<(isVolumeUp: Boolean) -> Boolean>()

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val isVolumeUp = when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> true
                KeyEvent.KEYCODE_VOLUME_DOWN -> false
                else -> return super.dispatchKeyEvent(event)
            }
            if (volumeKeyListeners.lastOrNull()?.invoke(isVolumeUp) == true) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        disableNavigationBarContrast()
        super.onCreate(savedInstanceState)
        if (CrashHandler.hasCrashed(this)) {
            startActivity(Intent(this, SafeModeActivity::class.java))
            finish()
            return
        }
        // 同一 Screen 可能重复入栈（popUpTo 原地重进、会话重开等）
        DecomposeSettings.update { it.copy(duplicateConfigurationsEnabled = true) }
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
        setContent {
            RikkahubTheme {
                setSingletonImageLoaderFactory { context ->
                    ImageLoader.Builder(context)
                        .crossfade(true)
                        .components {
                            add(
                                OkHttpNetworkFetcherFactory(
                                    callFactory = { okHttpClient },
                                    cacheStrategy = { CacheControlCacheStrategy() },
                                )
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                add(AnimatedImageDecoder.Factory())
                            } else {
                                add(GifDecoder.Factory())
                            }
                            add(SvgDecoder.Factory(scaleToDensity = true))
                        }
                        .build()
                }
                var locked by remember { mutableStateOf(appLockState.shouldLock()) }
                val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
                val awaitingSettings = settings.init && !locked && appLockState.leftAt != 0L

                // 显示模式：竖屏 / 横屏 / 自动 / 跟随系统。改动即时生效；
                // 大屏机型上系统会忽略固定方向，见 DisplayModeOrientation.kt
                LaunchedEffect(settings.displaySetting.displayMode) {
                    requestedOrientation = settings.displaySetting.displayMode.activityOrientation
                }

                fun launchLockActivity() {
                    if (isFinishing || isDestroyed) return
                    startActivity(
                        Intent(this@RouteActivity, AppLockActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                }

                fun evaluateLock() {
                    val current = settingsStore.settingsFlow.value
                    if (current.init) return
                    if (!current.appLockEnabled) {
                        if (appLockState.locked || appLockState.pendingLockAt != 0L || appLockState.leftAt != 0L) {
                            appLockState.clear()
                        }
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        locked = false
                        return
                    }
                    if (appLockState.locked) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        locked = true
                        return
                    }
                    val deadline = appLockState.lockDeadline(current.appLockTimeoutMinutes)
                    if (deadline > 0L && System.currentTimeMillis() >= deadline) {
                        appLockState.markLocked()
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        locked = true
                    } else {
                        if (appLockState.pendingLockAt != 0L || appLockState.leftAt != 0L) {
                            appLockState.clearPending()
                        }
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        locked = false
                    }
                }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> {
                                val current = settingsStore.settingsFlow.value
                                if (!current.init && current.appLockEnabled) {
                                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                                }
                                if (!appLockState.locked) {
                                    if (current.init) {
                                        appLockState.markLeft(null)
                                    } else if (current.appLockEnabled) {
                                        appLockState.markLeft(current.appLockTimeoutMinutes)
                                    }
                                }
                            }

                            Lifecycle.Event.ON_START -> evaluateLock()
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                LaunchedEffect(settings.init, settings.appLockEnabled) {
                    evaluateLock()
                }

                LaunchedEffect(locked) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        setRecentsScreenshotEnabled(!locked)
                    }
                    if (locked) launchLockActivity()
                }

                if (awaitingSettings) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                } else {
                    Box(
                        modifier = if (locked) {
                            Modifier
                                .fillMaxSize()
                                .clearAndSetSemantics {}
                        } else {
                            Modifier.fillMaxSize()
                        },
                    ) {
                        AppRoutes()
                    }
                }
                if (locked) {
                    Dialog(
                        onDismissRequest = {},
                        properties = DialogProperties(
                            usePlatformDefaultWidth = false,
                            dismissOnBackPress = false,
                            dismissOnClickOutside = false,
                            decorFitsSystemWindows = false,
                        ),
                    ) {
                        AppLockScreen(onUnlock = ::launchLockActivity)
                    }
                }
            }
        }
    }

    private fun disableNavigationBarContrast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val destination = when (intent.action) {
            ACTION_TRANSLATE -> Screen.Translator
            ACTION_MEDIA_CREATION -> {
                openNewMediaCreation()
                null
            }
            Intent.ACTION_SEND -> Screen.ShareHandler(
                text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty(),
                streamUri = intent.getStringExtra(Intent.EXTRA_STREAM),
            )
            Intent.ACTION_PROCESS_TEXT -> Screen.ShareHandler(
                text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty(),
            )
            else -> mediaCreationDestination(intent)
                ?: intent.getStringExtra(EXTRA_CONVERSATION_ID)?.let { Screen.Chat(it) }
        }
        if (destination != null) {
            navColumns.rootColumn.navigate(destination) { launchSingleTop = true }
        }
    }

    // 快捷方式没有指定会话，打开一个空会话
    private fun openNewMediaCreation() {
        lifecycleScope.launch {
            val destination = Screen.MediaCreation(mediaCreationRepository.newSession().id.toString())
            navColumns.rootColumn.navigate(destination) { launchSingleTop = true }
        }
    }

    // 媒体创作的通知带着会话 ID，点击后直接打开那个会话
    private fun mediaCreationDestination(intent: Intent): Screen? {
        val sessionId = intent.getStringExtra(MediaCreationForegroundService.EXTRA_MEDIA_SESSION_ID) ?: return null
        return runCatching { Uuid.parse(sessionId) }.getOrNull()?.let { Screen.MediaCreation(it.toString()) }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun AppRoutes() {
        val toastState = rememberToasterState()
        val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
        val tts = rememberCustomTtsState()
        val asr = rememberCustomAsrState()
        val eventBus = koinInject<AppEventBus>()
        LaunchedEffect(tts) {
            eventBus.events.collect { event ->
                when (event) {
                    is AppEvent.Speak -> tts.speak(event.text)
                    is AppEvent.OpenUsageAccessSettings -> this@RouteActivity.openUsageAccessSettings()
                    is AppEvent.ChatGenerationUpdate -> Unit // 由 ChatNotificationManager 消费
                    is AppEvent.ChatGenerationStatus -> Unit // 由 ChatNotificationManager 消费
                    is AppEvent.ChatGenerationEnded -> Unit // 由 ChatNotificationManager 消费
                }
            }
        }
        val migrationState by DatabaseMigrationTracker.state.collectAsStateWithLifecycle()

        val backStack by rootComponent.childStack.subscribeAsState()
        val detailStack by rootComponent.detailStack.subscribeAsState()
        // Local 的值一直在变，读它的页面会跟着整片重组：Navigator 只造一次
        CompositionLocalProvider(
            LocalNavController provides navColumns.rootColumn,
            LocalSettings provides settings,
                LocalButtonShadow provides settings.displaySetting.buttonShadow,
                LocalToaster provides toastState,
                LocalTTSState provides tts,
                LocalASRState provides asr,
            ) {
                TTSController()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true }
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    val navController = LocalNavController.current
                    // 根栈带预测性返回（ImageToolbox 的 toolboxPredictiveBackAnimation 同款）；
                    // 右列详情页不接手势，走同一套 fade + slide + scale 的普通返回动画。
                    // 时长跟着设置里的「动画速度」档位走（见 ui/navigation/StackHost.kt）
                    val pageAnimMillis = slideDurationMillis()
                    val rootAnimation: StackAnimation<Screen, NavChild> =
                        remember(rootComponent, pageAnimMillis) {
                            l3HubStackAnimation(
                                backHandler = rootComponent.backHandler,
                                onBack = rootComponent::popRoot,
                                durationMillis = pageAnimMillis,
                            )
                        }
                    val detailAnimation: StackAnimation<DetailDestination, NavChild> =
                        remember(pageAnimMillis) { l3HubDetailAnimation(pageAnimMillis) }
                    // 横屏的设置分区：左列常驻一级选项，右列放具体页面。
                    // 栏本身不参与转场，Children 一直待在同一个槽位里，进出设置不会重建。
                    val windowDpSize = currentWindowDpSize()
                    val landscape = windowDpSize.width > windowDpSize.height
                    val currentScreen = backStack.active.configuration
                    val showSettingRail = landscape &&
                        windowDpSize.width >= 700.dp &&
                        currentScreen.isSettingBoard()
                    // 横屏收起状态栏：顶栏贴到屏幕顶边，竖向空间全给内容
                    HideStatusBarInLandscape(landscape)
                    // 左栏用宽度动画进出：离开设置时右栏是平滑变宽，不是整块跳一下
                    val settingRailWidth by animateDpAsState(
                        targetValue = if (showSettingRail) 224.dp else 0.dp,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "settingRailWidth",
                    )
                    // 设置三列：中列停在分区页，页内下钻的详情页挂到右列自己的栈
                    // （Decompose 的 child 身份随栈位置，同一 child 不能在两个 Children 里，
                    //  所以不再像 nav3 那样切片同一根栈，而是双栈分流，见 Navigator）
                    val splitSettingColumns = showSettingRail && currentScreen.hostsDetailColumn()
                    // 中列在分三栏时用中列导航（下钻进右列），整宽时用根列导航
                    val middleColumn = if (splitSettingColumns) navColumns.sectionColumn else navColumns.rootColumn
                    // 右列收起动画期间（转竖屏 / 模型编辑占根栈）把最后一次的栈撑住
                    var lastDetailStack by remember {
                        mutableStateOf<ChildStack<DetailDestination, NavChild>?>(null)
                    }
                    LaunchedEffect(detailStack, splitSettingColumns) {
                        if (detailStack.items.size > 1 || splitSettingColumns) lastDetailStack = detailStack
                    }
                    val shownDetailStack: ChildStack<DetailDestination, NavChild> =
                        if (splitSettingColumns || detailStack.items.size > 1) detailStack
                        else lastDetailStack ?: detailStack

                    // 返回的唯一主人：右列收页、根栈弹页、交回系统的顺序见 NavPolicy
                    NavigationBackHandler(
                        component = rootComponent,
                        rootDepth = backStack.items.size,
                        detailDepth = detailStack.items.size,
                    )
                    // 中列 / 右列的分割比例，拖分隔条改；默认中列窄一点，详情页宽一点
                    var settingSplit by rememberSaveable { mutableStateOf(0.42f) }
                    val settingContentWidth = (windowDpSize.width - 224.dp).coerceAtLeast(1.dp)
                    // 右列展开 / 收起：宽度比例做动画，关闭时这段宽度平滑还给中列。
                    // 拖动时切回 snap，手柄不能落后于手指
                    var rightColumnResizing by remember { mutableStateOf(false) }
                    val rightColumnFraction by animateFloatAsState(
                        targetValue = if (splitSettingColumns) 1f - settingSplit else 0f,
                        animationSpec = if (rightColumnResizing) {
                            snap()
                        } else {
                            MaterialTheme.motionScheme.defaultSpatialSpec()
                        },
                        label = "settingRightColumn",
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            // 横屏的挖孔在侧边：整块内容让开它，insets 被消费后子页面里的顶栏
                            // 不会再各自让一遍（否则三栏里中列会离左栏多出一段刘海宽的空当）
                            .then(
                                if (landscape) {
                                    Modifier.windowInsetsPadding(
                                        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        if (settingRailWidth > 0.dp) {
                            Box(
                                modifier = Modifier
                                    .width(settingRailWidth)
                                    .fillMaxHeight()
                                    .clipToBounds(),
                            ) {
                                SettingRail(
                                    current = currentScreen,
                                    // 换分区时把上一分区的详情页收掉，返回只回到设置首页
                                    onNavigate = { target ->
                                        navController.navigate(target) {
                                            popUpTo(Screen.Setting)
                                            launchSingleTop = true
                                        }
                                    },
                                )
                            }
                        }
                        // 设置中列不再随右列变宽被压缩：宽度固定成「右列最窄时」的宽度，
                        // 右列更宽时从右边盖上来，列表不会跟着来回重排
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            val contentWidth = maxWidth
                            val minRightFraction = 0.34f
                            val middleTargetWidth = if (splitSettingColumns) {
                                contentWidth * (1f - minRightFraction)
                            } else {
                                contentWidth
                            }
                            val middleColumnWidth by animateDpAsState(
                                targetValue = middleTargetWidth,
                                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                label = "settingMiddleWidth",
                            )
                            val rightColumnWidth = (contentWidth * rightColumnFraction)
                                .coerceIn(1.dp, contentWidth)

                            Children(
                                stack = backStack,
                                modifier = Modifier
                                    .width(middleColumnWidth)
                                    .fillMaxHeight(),
                                animation = rootAnimation,
                            ) { child ->
                                NavChildContent(child.instance, middleColumn)
                            }

                            // 右列：只放设置里的下钻页，转场引擎与中列同源（见 ui/navigation/StackHost.kt）；
                            // 宽度超出中列留出的那部分时，从右边盖住中列，而不是把中列压窄
                            if (rightColumnFraction > 0.001f) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .width(rightColumnWidth)
                                        .fillMaxHeight(),
                                ) {
                                    ColumnResizeHandle(
                                        onDrag = { delta ->
                                            settingSplit = (settingSplit + delta.value / settingContentWidth.value)
                                                .coerceIn(0.28f, 0.66f)
                                        },
                                        onDragStart = { rightColumnResizing = true },
                                        onDragEnd = { rightColumnResizing = false },
                                    )
                                    Children(
                                        stack = shownDetailStack,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        animation = detailAnimation,
                                    ) { child ->
                                        NavChildContent(child.instance, navColumns.detailColumn)
                                    }
                                }
                            }
                        }
                    }
                    if (BuildConfig.DEBUG) {
                        Text(
                            text = "[开发模式]",
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                    AnimatedVisibility(
                        visible = migrationState is MigrationState.Migrating,
                        enter = fadeIn(rememberEffectsSpec<Float>()),
                        exit = fadeOut(rememberEffectsSpec<Float>()),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val state = migrationState as? MigrationState.Migrating
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = stringResource(R.string.db_migrating),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (state != null) {
                                    Text(
                                        text = "v${state.from} → v${state.to}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                Toaster(
                    state = toastState,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

/**
 * 栈顶内容渲染：每页拿到自己的 [NavChild] 当 ViewModelStoreOwner
 * （koinViewModel 默认读 LocalViewModelStoreOwner，作用域与 nav3 的
 * ViewModelStore decorator 对齐），路由分发见 `ui/navigation/ScreenPages.kt`。
 */
@Composable
private fun NavChildContent(child: NavChild, navigator: Navigator) {
    val screen = child.screen
    if (screen == null) {
        // 右列的空槽位（DetailDestination.None）：右列可见但还没有详情页
        Box(modifier = Modifier.fillMaxSize())
    } else {
        // 页面拿自己那一列的导航器与自己的 ViewModelStore：返回不跨列，store 随栈位置销毁
        CompositionLocalProvider(
            LocalNavController provides navigator,
            LocalViewModelStoreOwner provides child,
        ) {
            ScreenContent(screen)
        }
    }
}

/**
 * 横屏隐藏状态栏，让页面顶栏直接贴到屏幕顶边；回到竖屏再显示出来。
 *
 * 判据用窗口尺寸而不是 Configuration：Activity 的 configChanges 含 orientation，转屏不重建，
 * 只能听测量结果。onResume 再补一次，转屏发生在后台时回来也能对上。
 */
@Composable
private fun HideStatusBarInLandscape(landscape: Boolean) {
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(view) {
        view.context.getActivity()?.let { WindowCompat.getInsetsController(it.window, view) }
    }
    DisposableEffect(controller, lifecycleOwner, landscape) {
        val applyBars = {
            val bars = controller
            if (bars != null) {
                if (landscape) {
                    bars.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    bars.hide(WindowInsetsCompat.Type.statusBars())
                } else {
                    bars.show(WindowInsetsCompat.Type.statusBars())
                }
            }
        }
        applyBars()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) applyBars()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
