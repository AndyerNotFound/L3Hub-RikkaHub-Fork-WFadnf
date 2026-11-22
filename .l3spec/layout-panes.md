# 页面骨架、导航与右侧面板 规格

用于把 Android Compose 界面复刻成 HTML 原型。所有数值取自代码里的字面量；代码里没有的东西写「代码中未定义」，凭推理补的标「推测」。

取证范围（全部只读）：`RouteActivity.kt`、`ui/pages/chat/{ChatPage,ChatTopBar,ChatList,ChatListOverlays,ChatEmptyState,ChatDrawer,ChatSidePanel,ChatPaneHost,ChatWorkspaceFilesPane,ChatWorkspaceTerminalPane,ChatPickerSheets,ChatSelection,ChatConversationStatusPane,ConversationDetailsPanel,ToolDetailSidePanel,SubAgentTaskSheet,SubAgentConversationWindow,Background,MeshGradientBackground}.kt`、`ui/pages/setting/SettingRail.kt`、`ui/navigation/StackHost.kt`、`ui/theme/{MotionSpecs,MotionEasing}.kt`、`ui/components/ai/{ChatInput,ChatFloatingBar}.kt`、`ui/components/ui/ColumnResizeHandle.kt`、`ui/components/message/ChatMessageSubAgentNotice.kt`、`app/src/main/res/values-zh/strings.xml`（简体中文资源；仓库里没有 `values-zh-rCN`，简中值在 `values-zh`）。

---

## 1. 竖屏与宽屏的布局规则

### 1.1 根壳：三列容器

【结构】`RouteActivity.AppRoutes` 的 `Box(fillMaxSize)` 里只有一行 `Row`：
左为可选的设置栏 `Box(width = settingRailWidth)` → 中为 `BoxWithConstraints(weight(1f))`，其中放 `Children(backStack)`（中列）与 `Row(align TopEnd, width = rightColumnWidth)`（右列，含左缘拖拽条 + `Children(detailStack)`）。整行在横屏时额外加 `windowInsetsPadding(displayCutout horizontal)`，侧边挖孔被这一层吃掉，子页面顶栏不再各自让一次。

【尺寸/阈值】
- `landscape = windowDpSize.width > windowDpSize.height`（窗口测量值，不是 Configuration；Activity 的 configChanges 含 orientation）。
- 设置左栏出现：`landscape && windowDpSize.width >= 700.dp && currentScreen.isSettingBoard()`。`isSettingBoard()` = 路由是 `Screen.Setting` 或能映射到某个设置分区；`Screen.ModelEditor` 明确排除（它自己就要三栏宽度）。
- 左栏宽度：`animateDpAsState(224.dp ↔ 0.dp)`，spec = `MaterialTheme.motionScheme.defaultSpatialSpec()`。
- 右列出现：`splitSettingColumns = showSettingRail && currentScreen.hostsDetailColumn()`，且 `rightColumnFraction > 0.001f`。`hostsDetailColumn()` = 是分区首屏且不是设置首页 —— 也就是「设置首页只有两列，进入分区后的下钻页才拉出右列」。
- 右列默认比例：`settingSplit` 默认 `0.42`（中列占比），右列 = `1 - 0.42 = 0.58`；拖拽钳制 `settingSplit ∈ [0.28, 0.66]`，即右列 `[0.34, 0.72]`。
- 中列宽度：分三栏时固定为 `contentWidth * (1 - minRightFraction)`，`minRightFraction = 0.34`，即 **0.66 × 可用宽**；不分栏时吃满。这是刻意的：右列变宽时从右边盖上去，中列列表不来回重排。
- `settingContentWidth = windowDpSize.width - 224.dp`（拖拽换算基准）。
- 拖拽条 `ColumnResizeHandle`：命中区宽 `10.dp`，可见条 `3.dp` 宽、`fillMaxHeight(0.3f)`、圆角 `2.dp`。
- 拖拽期间宽度动画切 `snap()`，松手恢复空间曲线。

【文案】左栏标题「设置」；分组「通用设置 / 模型与服务 / 数据设置 / 更多」；项依次为「偏好设置、助手、扩展管理、默认模型和提示词、提供商、搜索服务、语音服务、媒体、MCP、工具覆写、Web服务器、数据备份、存储管理、关于、请求日志」。选中项底色 `secondaryContainer`，圆角 14dp，行内 padding h12/v7，图标 17dp。

【触发】任何设置相关路由入栈即出现左栏；换分区时 `popUpTo(Screen.Setting)`，返回只回设置首页。

### 1.2 聊天页：竖屏抽屉 / 横屏三栏

【结构】`ChatPage` 顶层分两支：`threePaneLayout` 为真走 `Row { 左栏(可折叠) + 拖拽条 + ChatPageContent(中列 + 右栏) }`；否则走 `ModalNavigationDrawer(drawerContent = ChatDrawerContent) { ChatPageContent }`。`ToolDetailSidePanel` 永远挂在 `Box` 最外层，两者都能盖住。

【尺寸/阈值】
- 三栏判据：`windowDpSize.width > windowDpSize.height && windowDpSize.width >= 600.dp`（**竖屏永不三栏，横屏 600dp 起**）。
- 三栏整块缩放：`LocalDensity(density = base × 0.75f, fontScale 不变)`，常量 `LANDSCAPE_UI_SCALE = 0.75f`。所以三栏坐标系里先用 `totalWidth = windowDpSize.width / 0.75` 换算。
- 左栏（会话抽屉列）：默认展开（`rememberSaveable = true`），默认宽 `LANDSCAPE_SIDEBAR_WIDTH = 300.dp`，拖拽钳制 `200.dp..440.dp`，收起动画到 `0.dp` 后整列不再组合。
- 中列：默认 `totalWidth * 0.4f`，钳制 `320.dp .. maxChatWidth`；`maxChatWidth = totalWidth - 左栏宽 - 260.dp`，下限不低于 `320.dp`。**260.dp 是留给右栏的最小宽度。**
- 右栏：`Modifier.weight(1f).fillMaxHeight().padding(start = 4.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)`，即吃掉剩余宽度。
- 竖屏抽屉宽度：`min(300.dp × 1.2, 窗口宽)` = **360dp**（窄屏取窗口宽）。
- 转进三栏时若模态抽屉还开着，主动 `close()`（避免转回竖屏仍开着）。
- 横屏隐藏系统状态栏（`HideStatusBarInLandscape`），页面顶栏贴屏幕顶边；回竖屏再显示。

【文案】左栏开合按钮 `contentDescription = "Messages"`（代码里硬编码英文，未走资源）。

【触发】`threePaneLayout` 变化即时切换；`sidebarOpen` 由顶栏菜单按钮取反；抽屉开时 `BackHandler` 优先收抽屉。

---

## 2. 聊天页的垂直装配

### 2.1 层级（由底向上）

【结构】
1. `Surface(color = LocalUiSlots.pageBackground, fillMaxSize)` — 页面底色。
2. `AssistantBackground`：注册为 `hazeSource(backgroundHazeState)`，是所有玻璃/模糊的唯一取样源（不取聊天列表，避免正文糊进抽屉）。
3. `Row { Box(chatWidth) { Scaffold } , 右栏 }`。
4. `Scaffold(containerColor = Transparent)`：
   - `topBar = Column { TopBar; ChatQuotaStrip(条件显示) }`
   - `bottomBar = Box(onGloballyPositioned 取底边) { Box(graphicsLayer 位移/alpha/模糊) { ChatInput } }`
   - `content = Box { ChatList | EmptyChatContent | 点击遮罩层 | 详情浮层 | 限额看板 }`
5. 页外层浮层：`BackgroundTasksSheet`、各类对话框/弹层、`ModelListSheet`。

【尺寸/阈值】
- 顶栏：按钮 `45.dp`，图标 `22.5.dp`，按钮间距 `6.dp`，无标题时标题位留给会话胶囊（高 45dp）。
- 顶栏内容随三栏变化：`threePane = true` 时标题位不画模型/文件夹胶囊，右侧胶囊里只剩「新建 + 更多」，详情与任务树入口被右栏标签页取代。
- 消息列表内边距：水平 `adaptiveHorizontalPaddingValue(16.dp)`，垂直上 16dp、下 `32.dp + innerPadding.bottom`；列表尾部有一个 `5.dp` 的 `ScrollBottomKey` 占位项用于「滚到底」定位。
- 输入区外层：水平 `adaptiveHorizontalPadding(8.dp)`，底部 `8.dp + liftedBy`，列间距 `8.dp`；Ime inset 由 `imePadding().navigationBarsPadding()` 承担，空会话时改成只 `navigationBarsPadding()`（键盘位移交给页面绘制期平移）。

【触发】`hasMessages` 决定 `ChatList` 是否挂载；`keepEmptyLayout` 决定英雄卡是否还留在组合里播退场。

### 2.2 顶栏内部顺序

【结构】`TopBar` = 导航位（菜单按钮）→ 标题位（会话文件夹胶囊 + 模型图标，仅非三栏）→ 右侧胶囊（详情 / 任务树 / 新建 / 更多，横屏时去掉前两个，中间用细分割线隔开）。下方可选 `ChatQuotaStrip`（折叠条）。

【尺寸/阈值】胶囊高 `45.dp`；`LocalFullWidthComponents` 打开时导航按钮形状从 `CircleShape` 变 `RoundedCornerShape(0.dp)`。

【文案】详情按钮「详情」；任务树按钮「任务树」；更多「更多选项」；会话菜单三项「对话地图 / 导出 / 删除」（空会话时整组置灰）；文件夹选单标题「会话文件夹」，未归类「未归类」，空态「暂无文件夹」，末尾「新建文件夹」。

【触发】`showCapsuleBackground = 列表可以向上滚` —— 滚到标题栏下面时胶囊才铺背景，页面顶部时按钮直接浮在背景上。

### 2.3 消息列表上方的浮层（同一 Box 内，按代码顺序）

【结构】① `ErrorCardsDisplay`（`BottomCenter`，`zIndex(5f)`）→ ② `ChatSelectionToolbar`（`BottomCenter`，`offset y = -48.dp`）→ ③ `MessageJumper`（`CenterStart` 或 `CenterEnd`）→ ④ 空态英雄卡（列表为空时 `Center`，`padding bottom 32.dp`）→ ⑤ 下拉新建聊天胶囊（`BottomCenter`，`padding bottom = innerPadding.bottom + 24.dp`）。

【尺寸/阈值】
- `MessageJumper`：竖向排列 4 个圆按钮，`padding 8.dp`，按钮间距 `8.dp`，图标自带 `4.dp` 内边距，`tonalElevation 4.dp`；玻璃背景下限 `0.6f`，关玻璃时为 `alpha 0.65f`。
- 下拉新建胶囊：`CircleShape`，玻璃背景下限 `0.7f`，内部 `padding h16 v10`，间距 `6.dp`，进度条 `140.dp × 4.dp`，位移 `(1-progress) × 24.dp`，`tonalElevation 3.dp`；拉动阈值 `createPullMax = 300.dp` 换算成 px。
- **代码里没有 Material 的 `FloatingActionButton`**：聊天页的「悬浮元素」是上面这几个自绘 `Surface`，没有圆形 FAB。

【文案】下拉胶囊：「继续下拉开始新聊天」/「松开开始新聊天」；滚动按钮 `contentDescription = "滚动到底部"`。

【触发】MessageJumper：`isRecentScroll && !列表滚动中 && 设置里 showMessageJumper && 未在截图`；可左可右由 `messageJumperOnLeft` 决定。

### 2.4 输入区内部顺序

【结构】外层 `Surface(Transparent)` → 可选顶部渐变 `scrimModifier`（竖屏才有，`showScrim = !threePane`，透明度跟 `inputScrimAlpha`）→ `Column(spacedBy 8.dp)`：
1. `ConversationTodoCard`（清单 + 引导合并成一张卡；横屏三栏时由 `LocalConversationStatusInSidePanel` 关掉，改由右栏呈现）
2. `MessageQueuePanel`（消息队列）
3. 世界书注入提示行（labelSmall）
4. `ContextMeterSwitcher`（上下文计量 + 速度表盘，`contextMeterStyle != OFF` 才画）
5. 卡片槽 `Box(cardSlotMinHeight)` → `Column(spacedBy 8.dp)`：输入卡片 `Surface`（1dp 描边 `outlineVariant 0.5f`、`chatInputHaze`）→ `ChatFloatingBar`（`enableFloatingBar` 且槽位非空时）

【尺寸/阈值】
- 悬浮栏：基础按钮 `44.dp`，基础图标 `20.dp`，缩放 `0.8f..1.4f`；`floatingBarFullHeight = 16.dp + buttonExtent(scale)`，作为卡片槽底部预留。
- 卡片内部：`padding(horizontal = inputPadding.dp, vertical = 4.dp)`，`spacedBy 2.dp`。
- 卡片槽高度 = `max(卡片实测高, 收起高 + 一档展开) + 悬浮栏高`，两个高度都在测量阶段读，避免收放动画逐帧重排底栏。

【文案】输入区「+」按钮 `contentDescription = "更多选项"`；插话按钮 `contentDescription = "插话"`；世界书注入提示「已注入世界书 %1$d 条」；悬浮栏终端槽位标题「终端」。

【触发】悬浮栏显示：`enableFloatingBar && items 非空`；`floatingBarAutoHide` 时输入框获焦或一段时间无操作就收起，点输入区重新露出。

### 2.5 空会话：英雄卡与输入区的落点

【结构】`EmptyChatContent` 与输入区是**同一个组合实例**：输入框常驻 `bottomBar`，空态时用 `graphicsLayer.translationY` 把它抬到英雄卡下面；英雄卡在 `Scaffold` content 里另画。

【尺寸/阈值】英雄卡：`padding(horizontal = 40.dp)`，圆角 `28.dp`，内部 `padding(h28, v24)`、间距 `12.dp`，头像 `88.dp` 圆形。抬升量 = `输入栏高 + (英雄卡底边 + 16.dp - 输入栏底边)`，键盘 inset 只上移一半（`imeBottom / 2`，取整）。首条消息发出后进度 `0→1` 滑回底部，模糊从 1 收到 0（`MotionBlur maxRadius 6.dp`）。

【文案】默认标语取 `chat_empty_default_slogan`（可被 `displaySetting.emptyChatSlogan` 覆盖）。

【触发】只有「新会话还空着」才播滑动；打开已有会话的首帧空列表按历史加载处理，直接贴底。

### 2.6 背景：MeshGradientBackground 的呈现

【结构】`AssistantBackground`：`assistant.useGradientBackground` 为真 → `MeshGradientBackground`；否则画背景图/视频层 + 可选全屏渐变遮罩。

【尺寸/阈值】
- 网格背景 = 底层线性渐变（竖向）+ 4 个 radialGradient 光斑（中心有色、边缘透明），不依赖 `Modifier.blur`。
- 底层渐变色标：暗色 `0 → #191428, 0.22 → #141020, 0.45 → #0D0A15, 0.65 → #09070E, 1 → #07060A`；亮色 `0 → #EAE4F8, 0.22 → #F4F0FC, 0.45 → #FBF9FF, 0.65 → #FFFFFF, 1 → #FFFFFF`。
- 光斑半径基准 `r = max(w, h)`，四个半径 `0.36r / 0.28r / 0.30r / 0.26r`；位置随相位漂移（蓝 `w0.48±0.38w, h0.08±0.18h`、青 `w0.18±0.30w, h0.24±0.20h`、紫品红 `w0.82∓0.34w, h0.12±0.18h`、暖色 `w0.58±0.28w, h0.34±0.16h`）。
- 相位周期：`p1 5500ms`、`p2 7000ms`、`p3 8500ms`、`p4 6200ms`，色相另走 `31000ms` 慢时钟；色相摆动用 `sin` 各自偏移（0 / 2.1 / 4.0 / 1.2），饱和度与明度写死（暗色明度约 0.58~0.66、alpha ≤ 0.44；亮色明度 1、alpha ≤ 0.55）。
- 生成中 `animateGradient = loadingJob == null` —— 停掉漂移，否则它作为模糊源每帧失效、整屏重糊。
- 背景图模式：不透明度 `backgroundOpacity`（0..1），模糊 `backgroundBlur × 24.dp`，视频上限 `1280`，图片解码 `2048`。
- 全屏遮罩（仅竖屏，`scrim = !threePane`）：竖向渐变 `pageBackground alpha 0.2 → 0.5`。**注意：网格背景分支在设置遮罩之前就 `return` 了，所以网格模式永远不叠这层遮罩。**

【文案】无。

【触发】`assistant.useGradientBackground`；遮罩与漂移动画分别由 `threePane`、`loadingJob` 控制。

---

## 3. 右侧面板（ChatSidePanel 及各个 pane）

### 3.1 面板外壳

【结构】`ChatSidePanes` = `ColumnResizeHandle` + `ChatSidePanelColumn`：一块玻璃 `Surface` 里装 `Column { 标签条 / 空态 或 内容区 }`。内容区 `Box(weight(1f))` 只渲染选中的一个 pane。

【尺寸/阈值】圆角 `24.dp`；底色 `surfaceContainerLow`（开模糊时 `Color.Transparent`，着色交给 `glassSurface(minAlpha = 0.5f)`）；阴影 `appButtonShadow(shape, 6.dp)`；内层 `statusBarsPadding() + navigationBarsPadding()`。**面板自身不写宽度** —— 宽度来自调用点的 `weight(1f)`（横屏三栏下 = 窗口剩余宽 - 4/8/8dp 内边距），并受中列 `maxChatWidth` 里预留的 `260.dp` 约束。

【文案】见 3.2。

【触发】仅 `threePaneLayout` 为真时组合；竖屏对应内容改用弹层（见 3.4）。

### 3.2 标签条

【结构】`Row(spacedBy)`：`SecondaryScrollableTabRow(weight(1f))` + 右侧「+」按钮 + `DropdownMenu`。每个 Tab 内是「标题（labelLarge，单行省略）+ 关闭按钮」。

【尺寸/阈值】行 `padding(start 8.dp, end 4.dp, top 6.dp)`；`edgePadding = 0.dp`、`minTabWidth = 104.dp`；Tab `height 38.dp`、`widthIn(104.dp, 200.dp)`，内部 `padding(start 14.dp, end 2.dp)`、间距 `2.dp`；关闭按钮 `28.dp`、图标 `14.dp`；「+」按钮 `40.dp`、图标 `20.dp`。菜单里已打开的 pane 带勾（`Tick01`，18dp）。

【文案】10 个 pane 的标题：「详情、任务树、本轮改动、清单与引导、用量、附件与工具、工作区文件、工作区终端、模型、后台任务」；关闭标签 `contentDescription = "关闭标签页"`，「+」`contentDescription = "新建标签页"`。

【触发】标签顺序 = 打开顺序；默认只开 `Details` 且选中它；关掉当前标签后落到剩下的最后一个。收起方式只有两个：标签上的 ×，或点「+」菜单切走 —— 面板不是模态的，点聊天区不会收起。

### 3.3 空标签页

【结构】竖排：标题 → 说明 → 10 个可直接打开的卡片。

【尺寸/阈值】`padding(h16, v12)`，间距 `8.dp`；卡片 `RoundedCornerShape(16.dp)` + `surfaceContainerHigh`，内文 `padding(h16, v12)`。

【文案】标题「新建标签页」；说明「还没有打开看板，点右上角的 + 新建一个」。

【触发】`panes.isEmpty()`。

### 3.4 各 pane 的内容结构与关键文案

**详情 `Details`**（= `ConversationDetailsPanel(embedded = true, showHeader = false)`）
【结构】竖向滚动 `Column(spacedBy 12.dp, padding start16/end10/top10/bottom14)`，分节：`会话`（助手 / 模型 / 推理 / 上下文 / 消息）→ `工作区`（每行工作区 + 文件夹接管说明）→ `工具`（FlowRow 标签，间距 6dp）→ `工具定义`（可展开，`JsonTree` 初始展开 2 层）→ `工具说明`（3 行截断，可展开出「工具说明」正文）→ `当前提示词`（等宽字体）→ `提示注入`（可展开，行尾标来源与位置）。
【尺寸/阈值】节内标签列宽 `DetailsLabelWidth = 52.dp`；竖屏浮层形态下宽 `fillMaxWidth - 24dp`、高 `screenHeight × 0.75`、圆角 22dp、玻璃下限 0.88f；右栏形态下无底板、无标题行。
【文案】`会话详情`、`会话`、`助手`、`模型`、`推理`、`上下文`、`消息`（`%d 条消息`）、`工作区`、`未绑定工作区`、`由文件夹「%1$s」接管`、`由会话文件夹接管`、`工具`、`未启用工具`、`工具定义`、`未注册工具`、`工具说明`、`无工具说明`、`当前提示词`、`%1$s 字符`、`无系统提示词`、`提示注入`、`未触发注入`、`无模型`、上下文格式 `%1$s / %2$s`；收起按钮 `contentDescription = "收起详情"`（右栏形态换成「关闭」）。
【触发】选中该标签；或竖屏点顶栏「详情」（顶栏按钮同时提亮）。

**任务树 `TaskTree`**（= `SubAgentTaskTree(showTitle = false)`）→ 见第 4 节。

**本轮改动 `Changes`**（= `TurnChangesPane`）
【结构】`EditedFilesList(initiallyExpanded = true, isLastMessage = true)`，外层 `verticalScroll`。
【尺寸/阈值】`padding(h16, top4, bottom16)`；无改动时居中 `padding 24.dp` 的 bodySmall 提示。
【文案】「本轮还没有文件改动」。
【触发】本轮最后一次 assistant 消息里出现编辑过的文件时自动 `ensureOpen`（不抢当前看板，且同一轮只自动开一次）。

**清单与引导 `Status`**（= `ConversationStatusPane`）
【结构】`ConversationTodoCard(initiallyExpanded = true)`，可带「提醒」设置行，下面接引导列表。
【尺寸/阈值】`verticalScroll`，`padding(h12, v10)`；空态 `padding 24.dp` 居中。
【文案】空态「这个会话还没有任务清单或引导」。
【触发】`todos` 或 `guidances` 非空时自动 `ensureOpen`；横屏时输入区那张清单卡不再渲染。

**用量 `Usage`**
【结构】`ConversationUsageDetails(messages, contextLength)` 竖向滚动。
【尺寸/阈值】`padding(horizontal 20.dp)`，`top 4.dp / bottom 24.dp`，间距 `8.dp`。
【文案】「用量」。

**附件与工具 `Files`**（= `ChatFilesPickerContent`，与竖屏弹层同一份本体）
【结构】`FilesPicker`：附件入口（拍照/图片/视频/音频/文件/涂鸦）、工作区入口与 cwd、搜索模式、压缩上下文、扩展开关等。
【尺寸/阈值】右栏里 `fillMaxSize`；弹层里保持 `fillMaxWidth` 自适应高度。
【文案】工作区入口的 `contentDescription` 为「工作空间详情」/「工作空间终端」，选目录「选择工作目录」；压缩对话框走「上下文已满 / 发送前压缩对话历史，完成后自动发送 / 压缩并发送 / 取消」（注意资源里写的是「工作空间」，不是「工作区」）。
【触发】输入区「+」按钮、或文件相关回调。

**工作区文件 `Workspace`**（= `WorkspaceFilesPane`）
【结构】标题行（工作区名 + 刷新）→ 路径行（返回 + 路径文本）→ 列表 / 预览 / 空态。
【尺寸/阈值】标题行 `padding(start14, end4, top8)`、间距 `6.dp`，刷新按钮 `32.dp`（图标 16dp）；路径行 `padding(h6, v2)`，返回按钮 `30.dp`（图标 16dp）；文件行 `padding(h14, v8)`、间距 `10.dp`、图标 `17.dp`；文件大小右对齐 `labelSmall`（`%.1f MB` / `%.0f KB` / `N B`）；预览用 `JetbrainsMono 11sp / lineHeight 16sp`、`padding(h14, v6)`。
【文案】无绑定工作区「这个会话没有绑定工作区」；读目录失败「无法读取这个目录」；空目录「空目录」；读文件失败「无法读取这个文件」。
【触发】点目录逐级进入；点文件在栏内只读预览，返回键先从预览退回列表、再退上一级。

**工作区终端 `Terminal`**（= `WorkspaceTerminalPane`）
【结构】标题行（工作区名 + 标签页下拉「终端 N ▾」+ 新建 + 关闭）→ 终端内容（固定深色主题的 `Surface(Color.Black)`）。
【尺寸/阈值】整块 `imePadding()`；标题行 `padding(start14, end4, top8)`、间距 `2.dp`；新建/关闭按钮 `30.dp`（图标 16dp）；标签文字 `labelLarge`，`padding(h6, v6)`。
【文案】「终端 %1$d」、`contentDescription`「新建终端标签页」/「关闭终端 %1$d」；占位文案「正在加载工作区…」/「请先安装 Rootfs」/「没有打开的终端标签页」。
【触发】进入 pane 即 `ensureSession(workspace)`；标签状态由全局 `WorkspaceTerminalSessionManager` 持有，与整页终端页共享同一批标签。

**模型 `ModelPicker`**（= `ModelListContent`）、**后台任务 `BackgroundTasks`**（= `BackgroundTasksContent`，`padding(h16, bottom16)`）：两者都是现成列表/内容，标题同样由标签条承担（`模型`、`后台任务`）。

### 3.5 工具详情侧滑面板（不属于标签页）

【结构】`ToolDetailSidePanel`：一层全屏遮罩 + 右侧 `Surface` 面板；面板内是 52dp 标题行 + 内容区（渲染该工具自己的 Preview，或不可解析时用 `DefaultToolPreview`）。

【尺寸/阈值】仅横屏可见（`Orientation.LANDSCAPE`，转竖屏立即清空请求）；面板宽 = `maxWidth × 0.42f`，钳制 `360.dp..560.dp`；圆角 `topStart/bottomStart 24.dp`；`tonalElevation 3.dp`、`shadowElevation 8.dp`；遮罩 `scrim alpha 0.32f`；标题行高 `52.dp`、`padding(start20, end8)`。返回键先关面板。

【文案】标题 = 工具名（`toolName`，单行省略）；关闭按钮 `contentDescription = "关闭"`。

【触发】消息里的工具调用详情调用 `LocalToolDetailHost` 打开；它不是 Dialog，不会把已隐藏的状态栏带回来。

### 3.6 竖屏落点（同一批内容换宿主）

| pane | 竖屏呈现 | 尺寸 |
| --- | --- | --- |
| 详情 | 顶栏下方展开浮层 | 高 `screenHeight × 0.75`，水平 12dp 内边距，圆角 22dp |
| 用量 | `SaltModalSheet` | 水平 20dp，底部 24dp |
| 附件与工具 | `SaltModalSheet` | 内容自适应 |
| 任务树 | `SubAgentTaskSheet` | 见第 4 节 |
| 模型 | `ModelListSheet` | 组件默认 |
| 后台任务 | `BackgroundTasksSheet` | 组件默认 |
| 工作区文件 / 工作区终端 / 本轮改动 / 清单与引导 | 无竖屏入口（文件、终端从输入区与悬浮栏进整页；改动行与清单卡留在消息与输入区里） | — |

---

## 4. 子代理

### 4.1 任务树（右栏标签页 / 竖屏任务树弹层）

【结构】`SubAgentTaskTree` = 可选标题行 + 左右两列：左列平铺的代理关系（按 `depth` 缩进），中间 1dp 分隔线，右列选中代理的详情。

【尺寸/阈值】
- 弹层形态：主体高度 `min(560.dp, screenHeightDp × 0.75)`，再加标题行 `TaskTreeTitleHeight = 40.dp`；标题 `titleMedium` + `padding(h20, v8)`。
- 左列：`LazyColumn(width 140.dp)`，`contentPadding(h8, v8)`，行间距 `4.dp`。
- 代理行：`RoundedCornerShape(10.dp)`，选中 `primaryContainer`、否则 `surfaceContainerLow`；内 padding `start = 8.dp + depth × 10.dp`、`end 6.dp`、`top/bottom 6.dp`，行内间距 `2.dp`；状态圆点 `6.dp` + 间隔 `4.dp`；第一行是助手名（`labelMedium`，空则退回会话标题，再空则 `?`），第二行是会话标题或子代理任务（`labelSmall`，最多 2 行）。
- 右列详情：`LazyColumn(contentPadding 12.dp, spacedBy 8.dp)`；头部 = 助手名（`titleSmall`）+ 「第 %1$d 代」+ 状态徽章；标题 `bodySmall` 3 行；子代理才有「打开会话」一行；`任务` 用 `CodeBlock(maxLines = 10)`；有 todos 时插 `ConversationTodoCard(initiallyExpanded = true)`；`工具调用 · N` 分节；工具行卡片圆角 `10dp`、`padding(h10, v8)`、间距 `6.dp`、工具图标 `14.dp`、名字等宽 `labelMedium`、状态 `labelSmall`、展开箭头 `12.dp`；展开后输入最多 12 行、输出最多 16 行（`11sp / lineHeight 15sp`，超出显示「展开 / 收起」）。
- 树深上限 `MAX_TREE_DEPTH = 6`；树里跑着任务或主会话在生成时每 `1000ms` 轮询一次，否则停。

【文案】`任务树`、空态「暂无子代理任务。请在助手的本地工具中开启「派发子代理」，并将另一助手设为子代理」、`打开会话`、`任务`、`工具调用`、`无工具调用`、`第 %1$d 代`、`展开`、`收起`、`进行中`、`已完成`、`失败`。

【状态色】
- 任务树（`AgentRow` 圆点 / `StateBadge`）：`running → primary`、`completed → tertiary`、`failed → error`、其他 `→ outline`。
- 子代理小窗（`StateDot`）：`running → tertiary`、`completed → primary`、`failed → error`，其他 `outline`。两处 running/completed 的取色是反的，复刻时如需一致要单独决定（代码现状如此）。

【触发】横屏由右栏「任务树」标签进入（顶栏按钮在横屏隐藏）；竖屏由顶栏「任务树」按钮打开 `SubAgentTaskSheet`。

### 4.2 子代理会话小窗

【结构】`AnimatedDialog(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)`：全屏遮罩 + 从屏幕顶部滑下的面板（贴顶、只给下缘圆角）。

【尺寸/阈值】面板 `fillMaxWidth()` + `fillMaxHeight(0.62f)`，圆角 `bottomStart/bottomEnd 24.dp`，`surfaceContainerLow`，`tonalElevation 3.dp`；遮罩 `scrim alpha 0.32f`；进入/退出动画固定 `180ms`（`rememberFixedSpec`），退出先播完再移除（`delay(180)`）；跑动中的会话每 `1200ms` 刷新一次，结束即停。头部：标题 `titleSmall` 1 行 + 「打开完整会话」`labelSmall`（primary）+ 状态点 `8.dp` + 关闭图标 `20.dp`（左内边距 8dp）；下方 1dp 分割线（`outlineVariant 0.4f`）；正文只读 `LazyColumn(padding 8dp, spacedBy 6dp)`，消息按 `ChatMessage` 渲染（无回调）。

【文案】`打开完整会话`、`正在读取会话…`、`这个会话已不存在`、关闭 `contentDescription = "关闭"`。

【触发】任务树右列点「打开会话」；内部的「打开完整会话」关掉小窗并导航到该会话。

### 4.3 子代理结果通知（列表内的一条消息）

【结构】标题行（`AiBrain01` 图标 14dp + 「子代理任务完成」+ 「系统自动回传」+ 展开箭头 16dp）+ 可折叠正文卡片（`surfaceContainerLow`，`shapes.medium`，内 padding `h12/v10`，Markdown 渲染）。长按标题行弹出小菜单。

【尺寸/阈值】标题行 `padding(h12, v2)`、间距 `6.dp`；菜单 `widthIn(172.dp, 220.dp)`，从 `0.88f` 缩放展开，`tonalElevation 3.dp`、`shadowElevation 8.dp`，外边距 `8.dp`；菜单项 `padding(h16, v14)`、间距 `12.dp`、图标 `20.dp`。编辑对话框输入框 `heightIn(120.dp, 320.dp)`。

【文案】`子代理任务完成`、`系统自动回传`、`编辑内容`、`编辑子代理结果`、`删除`。

【触发】后台子代理跑完自动回投；无编辑/删除回调时整条只读、不给长按。

### 4.4 字段说明（重要）

【结论】上述子代理界面里**没有「模型」和「耗时」字段**：树里用「助手名 + 第 N 代」标识执行者，没有模型名，也没有时长或 token 统计；小窗只有标题、状态点、只读消息流。若 HTML 原型要展示模型/耗时，属于**新增设计**，不是复刻（代码中未定义）。

---

## 5. 过渡动效

### 5.1 三条基准曲线（全局）

| 名称 | cubic-bezier | 用途 |
| --- | --- | --- |
| `AlphaEasing` | `(0.4, 0.4, 0.17, 0.9)` | 透明度、遮罩、模糊半径 |
| `FancyTransitionEasing` | `(0.48, 0.19, 0.05, 1.03)` | 页面滑入滑出、整块滑入滑出（尾段约 3% 过冲） |
| `PointToPointEasing` | `(0.55, 0.55, 0, 1)` | 两点之间的变形、缩放落位 |

【时长档位】`slideDurationMillis()`：`SYSTEM → 300ms`、`FAST → 180ms`、`GENTLE → 420ms`。fade / slide / scale 三条取同一个值，同时开始同时结束。

### 5.2 页面栈转场

【结构】根栈 `l3HubStackAnimation` = `predictiveBackAnimation(fallback = fade + slide + scale)`；右列详情栈 `l3HubDetailAnimation` = 同样的 `fade + slide + scale`，但不接预测性返回（右列返回由 `BackHandler`/`NavPolicy` 接管）。

【尺寸/阈值】`fade(tween(duration, AlphaEasing)) + slide(tween(duration, FancyTransitionEasing)) + scale(tween(duration, PointToPointEasing))`；`duration` 取 `slideDurationMillis()`（默认 300ms）。

【触发】根栈任何页面进出；设置分栏的右列详情页进出。

### 5.3 三列与面板的动画参数

| 元素 | 动画 | 参数 |
| --- | --- | --- |
| 设置左栏宽度（0 ↔ 224dp） | `animateDpAsState` | `motionScheme.defaultSpatialSpec()` |
| 设置中列宽度 | `animateDpAsState` | 同上 |
| 设置右列比例 | `animateFloatAsState` | 同上；拖拽中 `snap()` |
| 聊天三栏左栏宽度 | `animateDpAsState` | `rememberSlideSpec()` = `tween(slideDurationMillis, FancyTransitionEasing)`；拖拽中 `snap()` |
| 聊天三栏中列宽度 | 直接按拖拽值 | 无动画 |
| 消息多选工具栏 | `slideInVertically / slideOutVertically` | `spatialSpec`，`initialOffsetY = it × 2`（整块从两倍高度处上来） |
| 消息跳转按钮 | `slideInHorizontally / slideOutHorizontally` | `spatialSpec`，`±it × 2` |
| 详情浮层（竖屏） | `expandVertically(Top) + fadeIn` / `shrinkVertically(Top) + fadeOut` | 尺寸用 `spatialSpec`，透明度用 `effectsSpec` |
| 工具详情侧滑面板 | 遮罩 `fadeIn/fadeOut(effectsSpec)`；面板 `slideInHorizontally(spatialSpec) + fadeIn(effectsSpec)` | 进出同参 |
| 子代理小窗 | `slideInVertically / slideOutVertically` | 固定 `180ms`，`initialOffsetY = -it`（从上方进来） |
| 限额看板 / 后台任务弹层 | Material3 内置（读 `MaterialTheme.motionScheme`） | 随动画档位切换 |

【档位到 MotionScheme 的映射】`SYSTEM → MotionScheme.expressive()`（Compose Material3 自带，数值不在本仓库）；`FAST → spring(damping 0.9, stiffness 900)` / `effects tween 120ms`；`GENTLE → spring(damping 0.8, stiffness 220)` / `effects tween 320ms`（`slowSpatial` 分别为 `0.95/1400` 与 `0.75/150`）。

【关闭动效】`displaySetting.disableAnimations` 为真时：`rememberSlideSpec` 直接返回 `snap()`，主题把 `motionScheme` 换成全 snap ——抽屉、侧栏、页面转场一律瞬切。

---

## 6. 选择模式（ChatSelection）

【结构】状态在 `ChatSelectionState`（`selecting` + `selectedItems`），工具条是 `ChatSelectionToolbar`，挂在消息列表的浮层 Box 里。

【尺寸/阈值】工具条为 `HorizontalFloatingToolbar(expanded = true)`，位置 `BottomCenter`、`offset y = -48.dp`；内容是「取消（图标按钮，Tooltip「清除选择」）+ 文字按钮（全选 / 取消全选）+ 确认（`FilledIconButton`，Tooltip「确认」）」。进入动画 `slideInVertically(spatialSpec, it × 2)`。

【顶栏变化】**顶栏不变**：`TopBar` 没有选择模式参数，多选期间不换标题、不加计数、不加"完成/取消"按钮（代码事实）。变化只发生在两处 —— 列表项被 `ListSelectableItem(enabled = selecting)` 包起来、底部浮出工具条。

【文案】`全选`、`取消全选`、`清除选择`、`确认`；`selectedItems` 为空或不全时按钮显示 `全选`，全选时为 `取消全选`。

【触发与出口】
- 进入：顶栏「更多 → 导出」→ `selectionState.start(全部消息 id)`（默认全选）；或某条消息的「分享」→ `start(该条及之前所有消息 id)`。
- 确认：`confirm()`（退出多选但保留集合）→ 若有选中项则打开 `ChatExportSheet`；导出表关掉时 `cancel()` 清空。
- 取消：`cancel()`（退出并清空）。
- 相关联动：打开「对话地图（概览）」前会先 `selectionState.cancel()`；导出入口会把 `previewMode` 置回 false。
