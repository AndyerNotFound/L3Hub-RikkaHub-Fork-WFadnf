# 设置页结构与设计 token 规格（Android Compose → HTML 原型）

只读代码整理，未修改任何仓库文件。基准路径 `app/src/main/java/me/rerere/rikkahub/`。
所有中文取自 `app/src/main/res/values-zh/strings.xml` 的 `<string>` 原文。

## 0. 读码范围与三处与工单不符的实情

- 工单写的 `app/src/main/res/values-zh-rCN/strings.xml` **不存在**。简体中文在 `values-zh/strings.xml`（`values-zh-rTW` 是繁体）。本文中文一律取自 `values-zh`。
- `SettingRail.kt` 的文件头注释自相矛盾：类注释说「横屏两栏」，但同一文件里已经出现右列判据 `hostsDetailColumn()`。**实情是三栏**（左栏常驻 + 中列分区页 + 右列下钻页），以 `RouteActivity.kt:461-535` 的实现为准。
- Salt UI（`io.github.moriafly:salt-ui:3.0.0`，`gradle/libs.versions.toml:81,121`）是外部依赖，源码不在仓库、Gradle 缓存为空。**首页那些 Salt 行的高度、内边距、分隔线、右箭头只能给结构 + 推测值**，第 2 节逐条标了「推测」。
- 另一个会影响「色值」的事实：`Settings.dynamicColor` 默认 **true**（`data/datastore/Settings.kt:38`），Android 12+ 默认走系统壁纸取色。**默认状态下整套配色没有固定十六进制值**，第 4 节给了三条链路和各自的取数办法。

---

## 1. 设置首页（`SettingPage.kt`）

### 1.1 容器与滚动

| 元素 | 值 | 依据 |
|---|---|---|
| 页面底色 | `CustomColors.topBarColors.containerColor` = `LocalUiSlots.topBar` = `surfaceContainer` | `SettingPage.kt:124`（`containerColor`）+ `ui/theme/UiSlots.kt` `uiSlotsFrom()` |
| 顶栏 | M3 顶栏，高 **64dp**，标题「设置」用 titleLarge，左 `BackButton`（FilledTonalIconButton，底色 = `listItem` 槽 = `surfaceBright`） | `ui/style/StyledComponents.kt:468-491`、`ui/components/nav/BackButton.kt` |
| 列表 | `LazyColumn`，`contentPadding = 系统内边距 + PaddingValues(vertical = 8.dp)`，条目间距 **16dp** | `SettingPage.kt:129-130` |
| 横向内边距 | 头像区/告警卡自己加 `padding(horizontal = 16.dp)`；Salt 分组**不再补**，Salt 的 `RoundedColumn` 自带 16dp 内外距（代码注释明说再补会变 32dp） | `SettingPage.kt:126-130, 143`；`ui/components/ui/SaltSettingsList.kt:16-19` |

### 1.2 分区顺序（自上而下）

| # | `item()` key | 分区标题（无=裸块） | 显示条件 | 行号 |
|---|---|---|---|---|
| 1 | `profile` | — | 始终 | 132 |
| 2 | （匿名） | — | `settings.isNotConfigured()` | 187 |
| 3 | `localNetworkPermission` | — | 局域网权限未授予 | 196 |
| 4 | `notificationPermission` | — | 通知权限未授予 | 207 |
| 5 | `generalSettings` | **通用设置** | 始终 | 232 |
| 6 | `modelServices` | **模型与服务** | 始终 | 294 |
| 7 | `dataSettings` | **数据设置** | 始终 | 347 |
| 8 | `aboutSettings` | **关于** | 始终 | 375 |

### 1.3 顶部资料区（`profile`）

`Column(horizontalAlignment = CenterHorizontally, spacedBy(6.dp))`，`padding(horizontal = 16.dp, vertical = 12.dp)`：

| 元素 | 规格 | 文案 |
|---|---|---|
| 头像 | `UIAvatar`，`avatarSize = 88.dp`，点按可换图 | 取昵称首字/emoji |
| 昵称 | `titleMedium`（16sp/24，w500），`CircleShape` 水波纹，`padding(horizontal = 12.dp, vertical = 4.dp)`，点按弹「编辑昵称」对话框 | 空值回退 `user_default_name` = **用户** |

对话框：标题「编辑昵称」，`OutlinedTextField`（占位「请输入昵称」），按钮「保存」/「取消」。

### 1.4 各分区条目清单

控件类型说明：**跳转** = 整行可点、右侧 Salt 自绘箭头；**开关** = M3 `Switch`；**分段** = `ConnectedChoiceButtons`（胶囊按钮组，未选中项可长出选中项，见 2.3）；**滑块** = M3 `Slider` + 右侧 `CardGroupValue` 数值胶囊。

#### 通用设置（`SaltGroup` 标题「通用设置」）

| 条目 | 副标题 | 右侧控件 | 图标函数名 |
|---|---|---|---|
| 颜色模式 | —（行内只有图标 + 文字，控件另起一行） | **分段**：跟随系统 / 浅色 / 深色（当前项带图标） | 随当前模式变：`HugeIcons.SmartPhone01` / `HugeIcons.Sun01` / `HugeIcons.Moon02`（`SettingPage.kt` 末尾 `ColorMode.icon()`） |
| 偏好设置 | 主题、通知、界面和常规设置 | 跳转 → `Screen.SettingPreferences` | `HugeIcons.Settings03` |
| 助手 | 设置个性化助手 (智能体) | 跳转 → `Screen.Assistant` | `HugeIcons.LookTop` |
| 扩展管理 | 管理提示词注入、技能等扩展 | 跳转 → `Screen.Extensions` | `HugeIcons.Package` |

颜色模式这一行是 `ItemContainer` 手拼：`Row(spacedBy(16.dp))` 里图标 `Modifier.size(SaltTheme.dimens.itemIcon)` + `Text`，下面接分段控件，整块 `Column(spacedBy(12.dp))`。

#### 模型与服务

| 条目 | 副标题 | 右侧控件 | 图标函数名 |
|---|---|---|---|
| 默认模型和提示词 | 设置各个功能的默认模型 | 跳转 → `Screen.SettingModels` | `HugeIcons.AiMagic` |
| 提供商 | 配置AI提供商 | 跳转 → `Screen.SettingProvider` | `HugeIcons.Brain02` |
| 搜索服务 | 设置搜索服务 | 跳转 → `Screen.SettingSearch` | `HugeIcons.GlobalSearch` |
| 语音服务 | 配置语音合成与语音识别服务提供商 | 跳转 → `Screen.SettingSpeech` | `HugeIcons.Megaphone01` |
| 媒体 | 图像与视频生成提供商、素材上传 | 跳转 → `Screen.SettingMedia` | `HugeIcons.Image02` |
| MCP | 配置MCP服务器 | 跳转 → `Screen.SettingMcp` | `HugeIcons.McpServer` |
| 工具覆写 | 查看并覆写工具定义与工具说明 | 跳转 → `Screen.ToolOverrides` | `HugeIcons.Tools` |
| Web服务器 | 允许你通过网页访问 RikkaHub | 跳转 → `Screen.SettingWeb` | `HugeIcons.ServerStack01` |

#### 数据设置

| 条目 | 副标题 | 右侧控件 | 图标函数名 |
|---|---|---|---|
| 数据备份 | 备份和恢复应用数据 | 跳转 → `Screen.Backup` | `HugeIcons.Database02` |
| 存储管理 | 统计中「计算中...」，统计完 `%1$d 个文件，%2$.2f MB`（异步 `produceState` 数聊天文件） | 跳转 → `Screen.SettingFiles` | `HugeIcons.ImageUpload` |

#### 关于

| 条目 | 副标题 | 右侧控件 | 图标函数名 |
|---|---|---|---|
| 关于（自绘行，非 `SaltNavItem`） | 关于本APP | 文字块本身是跳转（→ `Screen.SettingAbout`）；右侧另有**两个 `IconButton`**：QQ（`TencentQQIcon`，tint `secondary`，弹群列表 BottomSheet）、Discord（`DiscordIcon`，tint `secondary`，外链） | `HugeIcons.Clapping01` |
| 使用文档 | 查看应用使用说明和帮助文档 | 跳转（外链 `docs.rikka-ai.com`，中文环境带 `/zh`） | `HugeIcons.Book01` |
| 请求日志 | 查看 HTTP 请求日志 | 跳转 → `Screen.Log` | `HugeIcons.Bookshelf01` |
| 分享 | 分享本APP给朋友 | 系统分享 Chooser（Salt 仍画箭头，因为 `SaltNavItem` 一律有） | `HugeIcons.Share04` |

#### 条件卡片（不在任何 SaltGroup 里）

| 卡片 | 结构 | 颜色 |
|---|---|---|
| `ProviderConfigWarningCard`（未配置 API 时） | `Surface(shape = shapes.extraLarge)` → 56dp `Cookie9Sided` 多边形图标底 + 标题「请配置API和模型」（`titleMediumEmphasized`）+ 说明「你还没有配置API和模型，请先配置」（`bodyMedium`）+ 右下「配置」按钮 | `errorContainer` / `onErrorContainer`；图标底 `error`，图标 `onError` |
| `LocalNetworkPermissionCard` / `NotificationPermissionCard` | 见 `ui/components/ui/permission/`，本工单不展开 | — |

### 1.5 二级页（供原型做第二屏，摘要）

**偏好设置**（`SettingPreferencesPage.kt`，`LargeFlexibleTopAppBar` + 单张 `CardGroup`，`padding(horizontal = 16.dp, vertical = 8.dp)`，间距 16dp）：5 行，全部 `CardGroupIcon`（40dp 圆底，20dp 图标，`primaryContainer` / `onPrimaryContainer`）+ 标题 + 副标题 + 跳转。

| 条目 | 副标题 | 跳转 | 图标 |
|---|---|---|---|
| 常规 | 交互行为、滚动、输入设置 | `SettingPreferencesGeneral` | `HugeIcons.Settings03` |
| 主题 | 动态配色、主题、AMOLED深色模式 | `SettingPreferencesTheme` | `HugeIcons.Sun01` |
| 界面偏好设置 | 消息显示、字体、代码块 | `SettingPreferencesUI` | `HugeIcons.PaintBoard` |
| 通知 | 更新提醒、消息生成通知、后台悬浮窗 | `SettingPreferencesNotification` | `HugeIcons.Notification01` |
| 网络 | User-Agent 和网络请求设置 | `SettingPreferencesNetwork` | `HugeIcons.CloudServer` |

**主题**（`SettingPreferencesThemePage.kt:60-95`，单张 `CardGroup`，4 行）：

| 条目 | 副标题 | 控件 |
|---|---|---|
| 动态颜色 | 是否基于壁纸动态生成 app 主题配色 | **开关** |
| 主题 | 自定义应用配色方案 | 跳转 + `ArrowRight01` |
| 调色板 | 选择内置调色板，或逐项自定义颜色 | 跳转 + `ArrowRight01` |
| AMOLED 暗色模式 | 在暗色主题中使用纯黑背景，适合 AMOLED 屏幕 | **开关** |

**常规**（`SettingPreferencesGeneralPage.kt`，9 张 `CardGroup`，顺序）：①资料行 ②语言（trailing 值胶囊） ③启动时新建对话 / 按Enter发送 / 消息队列（开关） ④消息导航按钮 / 消息导航按钮位置（依赖上一项）/ 自动滚动（开关） ⑤全局特效（分段：关闭/模糊/玻璃）+ 模糊不透明度/玻璃透明度（滑块，按特效类型择一显示）+ 全宽组件（开关）+ 输入框布局（分段：单行/双行）+ 消息生成触觉反馈（开关）+ 触感强度（分段：跟随系统/轻/重）+ 形状类型（FilterChip）、边角大小（滑块 %）、边角动画节流（数字滑块 ms）、开关类型（8 个 FilterChip）、滑块类型（4 个 FilterChip） ⑥跳过图片编辑 / 粘贴长文本为文件 / 文本长度阈值 ⑦音量键翻页 / 滚动幅度 ⑧「TTS设置」：默认播放速度 / TTS仅朗读引用内容 / TTS不朗读括号内的内容 / 生成后自动播放TTS / 语音模式回复时继续聆听 ⑨「隐私」：应用锁 / 离开后锁定（分段：立即 / 1 / 5 / 15 分钟）。

**界面偏好**（`SettingPreferencesUIPage.kt`，8 张 `CardGroup`）：①显示模式（trailing 值胶囊 + 选择弹窗） ②「消息显示」：显示用户头像、显示助手消息气泡、按段落拆分气泡（依赖上一项）、新对话标语、气泡不透明度 ③：聊天列表模型图标、显示模型名称、按钮阴影、完成后收起任务清单、在消息中显示日期和时间、使用 L3Hub 思考强度图标、显示Token和上下文统计、限额显示、以二进制量计数、用量饼图、上下文预警 ④显示工具调用输出、界面圆角（数字滑块 0–32）、输入框左右内边距（滑块 0–16）、关闭所有动画、动画速度（跟随系统/轻快/柔和三选一，`optionGroup` 随「关闭所有动画」收起） ⑤「动态模糊」：启用/滚动动态模糊/模糊半径/过渡时长/滚动行程 ⑥「悬浮栏」：显示悬浮栏、自动隐藏、悬浮栏大小、槽位管理 ⑦聊天字体（四格 `ConnectedChoiceButtons`，`minHeight = 64.dp`）、应用到全局、自定义字体、界面缩放、聊天字体大小 ⑧「代码显示」：代码块自动换行、代码块自动折叠、显示行号。

---

## 2. 设置条目行的视觉

首页用 **Salt 行**，子页用 **`CardGroup` 行**（`ui/components/ui/CardGroup.kt`）。两套不是同一个东西，原型里要分开做。

### 2.1 Salt 行（首页，`SaltNavItem` → `com.moriafly.salt.ui.Item`）

| 项 | 值 | 依据 |
|---|---|---|
| 结构 | 图标 + 主标题 +（可选）副标题，右侧 Salt 自绘箭头 | `SaltSettingsList.kt:42-56` |
| 图标尺寸 | `SaltTheme.dimens.itemIcon`，**推测 24dp**（库里默认值，仓库只引用不定义） | `SettingPage.kt:244` |
| 图标色 | `SaltTheme.colors.text` = `onBackground` | `ui/theme/SaltThemeBridge.kt:73-81` |
| 主标题字号 | `SaltTheme.textStyles.main` → **bodyLarge 16sp/24 w400** | `SaltThemeBridge.kt:41-50` |
| 副标题 | `SaltTheme.textStyles.sub` → **bodyMedium 14sp/20**，色 `SaltTheme.colors.subText` = `onSurfaceVariant` | 同上 + `SettingPage.kt:408` 手写行用的就是 `bodyMedium` |
| 行高 | **推测 56dp（单行）/ 72dp（双行）**，Salt 未定义源码可查 | — |
| 左右内边距 | Salt 分组自带 **16dp 内外距**（注释确证）；行内自身再加多少**推测 4–8dp** | `SaltSettingsList.kt:16-19` |
| 分隔线 | **推测** 相邻行之间有 1px（1dp）hairline，色 `SaltTheme.colors.stroke` = `outlineVariant` | `SaltThemeBridge.kt:78` |
| 分组卡片底 | `SaltTheme.colors.subBackground` = `surfaceContainer` | `SaltThemeBridge.kt:76` |
| 分组卡片圆角 | `SaltTheme.shapes` 的 small/medium/large，**推测 16dp（large）**，Salt 内部选哪一档无源码 | `SaltThemeBridge.kt:52-55` |
| 分组外标题 | `ItemOuterTitle`，色/字号 Salt 内部定，**推测 labelLarge 14sp w500 + onSurfaceVariant**，位于卡片上方左对齐 | `SaltSettingsList.kt:28` |

### 2.2 `CardGroup` 行（子页）

| 项 | 值 | 依据 |
|---|---|---|
| 圆角 | 组首尾 **20dp**，中间 **4dp**，间距 **2dp**；按下时整行四角变 20dp | `CardGroup.kt:61-63, 325-340` |
| 卡片底色 | `CustomColors.listItemColors.containerColor` = `listItem` 槽 = **`surfaceBright`**（开毛玻璃特效时改 `Color.Transparent` + `glassSurface(baseColor = surfaceBright)`） | `CardGroup.kt:361-390`、`UiSlots.kt` |
| 行内边距 | M3 `ListItem`：左右 **16dp**、上下 **16dp** | M3 默认（`ListItemDefaults.ContentPadding`） |
| 最小高度 | 56dp 单行 / 72dp 双行 / 88dp 三行（M3 默认） | M3 默认 |
| 主标题 | `bodyLarge` 16sp/24 w400，色 `onSurface` | `CardGroup.kt` 里未覆盖 ListItem 默认 |
| 副标题 | `bodyMedium` 14sp/20，色 **`onSurfaceVariant`** | `CardGroup.kt:298-306` |
| 标题/副标题行距 | `spacedBy(2.dp)` | `CardGroup.kt:288` |
| 前后件间距 | `spacedBy(16.dp)`；有副标题时左右内容**靠上对齐**（`Alignment.Top`），否则居中 | `CardGroup.kt:282-297` |
| 分隔线 | **没有分隔线**，靠 2dp 间距 + 首尾大圆角分组 | `CardGroup.kt:459-462` |
| 组标题 | `titleSmallEmphasized`，色 **`primary`**，`padding(start = 4.dp, top = 8.dp, bottom = 8.dp)` | `CardGroup.kt:425-433` |
| 图标（`CardGroupIcon`） | 40dp 圆底 + 20dp 图标，底/内容色默认 `secondaryContainer` / `onSecondaryContainer`（设置页调用点传 `primaryContainer` / `onPrimaryContainer`） | `CardGroup.kt:222-256` |
| 值胶囊（`CardGroupValue`） | `CircleShape` 胶囊，底 `secondaryContainer`、字 `onSecondaryContainer`，`labelLarge` + `fontFeatureSettings = "tnum"`，`padding(horizontal = 10.dp, vertical = 4.dp)` | `CardGroup.kt:258-280` |
| 开关 | M3 `Switch`（`SwitchType.MATERIAL_YOU` 默认），行尾；点整行等同切换 | `AppSwitch.kt:47-56`、`CardGroup.kt:196-220` |

**开关配色**（M3 `Switch` 默认，库内常量，本仓库未覆盖 → 以下为 M3 标准值，**推测项仅在 alpha 上**）：选中轨道 `primary` / 选中圆点 `onPrimary`；未选中轨道 `surfaceContainerHighest`、未选中圆点 `outline`、未选中描边 `outline`；禁用态 12%（容器）/38%（内容）。开关可选加 `1dp` 阴影（`ambientColor black 6%`、`spotColor black 10%`，`AppSwitch.kt:38-46`）。

**滑块配色**（M3 `Slider` 默认）：active 轨 `primary`、thumb `primary`、inactive 轨 `secondaryContainer`、刻度/overlay 走 `onPrimary` 系。

### 2.3 分段控件（`ConnectedChoiceButtons`）

- 高度 `minHeight = 48.dp`（聊天字体那组传 64dp），相邻按钮间距 `ButtonGroupDefaults.ConnectedSpaceBetween`（8dp）。
- 选中项 `weight = 1.3f`（未选中 1f），首/中/末分别是 leading/middle/trailing 连接形；底色 `glassColor(surfaceContainerHigh)`；选中文字前带图标（`ConnectedChoiceLabel`）。
- 交互：点未选中项才触发，`Role.RadioButton`。

### 2.4 圆角与底色 token 对照（做原型时直接查这张）

| 位置上 | 取的角色 | 浅色（Sakura，代码实值） | 深色（Sakura，代码实值） |
|---|---|---|---|
| 页面底 | `background` | `#FFF8F7` | `#1A1112` |
| 顶栏底 | `surfaceContainer` | `#FBEAEB` | `#261D1E` |
| Salt 分组卡片底 | `surfaceContainer` | `#FBEAEB` | `#261D1E` |
| `CardGroup` 行底 | `surfaceBright` | `#FFF8F7` | `#413738` |
| 弹窗/菜单 | `surfaceContainerHigh` | `#F6E4E5` | `#312828` |
| 分隔线 | `outlineVariant` | `#D7C1C3` | `#524345` |
| 主文字 | `onSurface` / `onBackground` | `#22191A` | `#F0DEDF` |
| 副文字 | `onSurfaceVariant` | `#524345` | `#D7C1C3` |
| 强调/箭头/组标题 | `primary` | `#8E4955` | `#FFB2BC` |

注意：浅色 Sakura 里 `surfaceBright == background == #FFF8F7`，**首页 Salt 卡片（surfaceContainer）与页面底只差一档**；`CardGroup` 行底也是近白，子页的行是靠 2dp 间距和圆角分组区分，不靠底色对比。深色下 `CardGroup` 行底 `#413738` 明显亮于页面底 `#1A1112`。

---

## 3. 横屏三栏

判据与尺寸全部在 `RouteActivity.kt:456-535`。

| 项 | 值 |
|---|---|
| 何时出现左栏 | `landscape && windowWidth >= 700.dp && currentScreen.isSettingBoard()`（`RouteActivity.kt:459-462`） |
| 左栏宽 | **224dp**，进出用 `animateDpAsState`（0dp ↔ 224dp，`motionScheme.defaultSpatialSpec()`），宽为 0 时整列不参与布局 |
| 左栏底 | `Surface(color = surfaceContainerLow)`，`width(224.dp).fillMaxHeight()`（`SettingRail.kt:184-190`） |
| 左栏内边距 | `statusBarsPadding()` + `navigationBarsPadding()` |
| 左栏标题 | 「设置」，`titleMedium`，`padding(start = 20.dp, top = 14.dp, bottom = 6.dp)`（`SettingRail.kt:196-201`） |
| 左栏列表 | `LazyColumn`，`contentPadding = horizontal 10.dp / vertical 4.dp`，项间距 **2dp**（`SettingRail.kt:202-205`） |
| 分组标题 | `labelSmall`（11sp/16 w500），色 `onSurfaceVariant`，`padding(start = 12.dp, top = 10.dp, bottom = 2.dp)`（`SettingRail.kt:206-212`） |
| 每项 | `Surface(onClick, shape = RoundedCornerShape(14.dp))`；`Row(padding(horizontal = 12.dp, vertical = 7.dp), spacedBy(10.dp))`；图标 **17dp**；文字 `labelLarge`，单行省略（`SettingRail.kt:233-266`） |
| 选中态 | 底色 **`secondaryContainer`**，内容色 **`onSecondaryContainer`**；未选中：**透明**底 + `onSurfaceVariant` 文字 |
| 未选中项是否有水波纹 | 有（`Surface(onClick)` 默认 indication） |

**左栏分组与顺序**（`SettingRail.kt:71-96`，与首页不完全一致：多出「更多」组，且没有首页的「颜色模式」行）：

1. **通用设置**：偏好设置(Settings03) / 助手(LookTop) / 扩展管理(Package)
2. **模型与服务**：默认模型和提示词(AiMagic) / 提供商(Brain02) / 搜索服务(GlobalSearch) / 语音服务(Megaphone01) / 媒体(Image02) / MCP(McpServer) / 工具覆写(Tools) / Web服务器(ServerStack01)
3. **数据设置**：数据备份(Database02) / 存储管理(ImageUpload)
4. **更多**：关于(Clapping01) / 请求日志(Bookshelf01)

**中列 / 右列分工**：

| 列 | 放什么 | 尺寸 |
|---|---|---|
| 中列 | 根栈当前页 = 设置首页（`Screen.Setting`）或某个一级分区页（`Screen.isSettingSectionPage()` 的 15 个路由之一）；分三栏时用 `sectionColumn` 导航，未分栏时用 `rootColumn` | 分三栏时固定 `contentWidth * (1 - 0.34)` = **66%**（`minRightFraction = 0.34`），不随右列变宽被压缩，右列更宽时从右边盖上来 |
| 右列 | 只放该分区的下钻页（`hostsDetailColumn()` 为真的路由的下级），自带 `detailStack` + `ColumnResizeHandle` 分隔条 | 默认 `1 - settingSplit` = **58%**（`settingSplit = 0.42f`），拖拽范围 `settingSplit` 0.28–0.66；`align(TopEnd)`，从右边覆盖中列 |
| 左栏在哪些路由显示 | `Screen.isSettingBoard()`：`Setting` 或任何 `settingSection() != null` 的路由；**`ModelEditor` 例外**（它要占满三栏宽度，不挂设置栏） |
| 右列什么时候不存在 | `Screen.Setting` 首页本身：`hostsDetailColumn()` 为 false，右列宽 0 |

补充：横屏时整块内容让开侧边挖孔（`WindowInsets.displayCutout.only(Horizontal)`）；`HideStatusBarInLandscape(landscape)` 让顶栏贴到屏幕顶边。

---

## 4. 设计 token

### 4.1 配色来源（三条链路，先定原型走哪条）

`RikkahubTheme`（`ui/theme/Theme.kt:128-152`）按顺序判断：

1. **调色板启用**（`settings.palette.presetId != null`）→ 走 `PaletteSetting.generateColorScheme()`：material-color-utilities，`Variant.TONAL_SPOT` + **`ColorSpec.SpecVersion.SPEC_2025`** + `contrastLevel = 0`，五个种子 = 预设的 primary/secondary/tertiary/error/neutral（`ui/theme/Palette.kt:688-720`）。默认 **未启用**（`PaletteSetting()` 的 `presetId` 为 null）。
2. **默认（本仓库出厂状态）** → `rememberToolboxColorScheme()`：`dynamicColor = true`（默认）且系统 ≥ Android 12 时，**整条配色由系统壁纸取色**，没有固定十六进制；系统 < 12 或用户关掉动态取色时，从当前 `themeId`（默认 `sakura`，`Settings.kt:39` + `PresetThemes[0]`）反推 6 个种子，再按 `ToolboxPaletteStyle.TonalSpot` + `contrastLevel 0.0` 重算（`ui/theme/ToolboxColorScheme.kt:60-85`、`data/datastore/ToolThemeSetting.kt:28-40`）。重算后**绝对色值会与第 4.2 节的手写值不同**（只保证同一族色相与明度阶梯）。
3. **AMOLED**（`amoledDarkMode` 且深色）→ 表面族整体压 `#000000`（调色板链路）/ 由引擎压黑（引擎链路）。

因此原型要「看起来像」，用 **Sakura 手写方案**（4.2 表 A）最稳；要「像默认紫」就用 **M3 baseline**（表 B）。

### 4.2 色值

**表 A：Sakura（`themeId = sakura`，默认主题；这是代码里唯一逐角色写死的完整配色，`ui/theme/presets/SakuraTheme.kt`）**

| 角色 | 浅色 | 深色 |
|---|---|---|
| primary | `#8E4955` | `#FFB2BC` |
| onPrimary | `#FFFFFF` | `#561D28` |
| primaryContainer | `#FFD9DD` | `#72333E` |
| onPrimaryContainer | `#72333E` | `#FFD9DD` |
| secondary | `#76565A` | `#E5BDC1` |
| onSecondary | `#FFFFFF` | `#43292D` |
| secondaryContainer | `#FFD9DD` | `#5C3F43` |
| onSecondaryContainer | `#5C3F43` | `#FFD9DD` |
| tertiary | `#785831` | `#EABF8F` |
| onTertiary | `#FFFFFF` | `#452B07` |
| tertiaryContainer | `#FFDDB8` | `#5E411C` |
| onTertiaryContainer | `#5E411C` | `#FFDDB8` |
| error | `#BA1A1A` | `#FFB4AB` |
| onError | `#FFFFFF` | `#690005` |
| errorContainer | `#FFDAD6` | `#93000A` |
| onErrorContainer | `#93000A` | `#FFDAD6` |
| background | `#FFF8F7` | `#1A1112` |
| onBackground | `#22191A` | `#F0DEDF` |
| surface | `#FFF8F7` | `#1A1112` |
| onSurface | `#22191A` | `#F0DEDF` |
| surfaceVariant | `#F3DDDF` | `#524345` |
| onSurfaceVariant | `#524345` | `#D7C1C3` |
| outline | `#847374` | `#9F8C8E` |
| outlineVariant | `#D7C1C3` | `#524345` |
| surfaceDim | `#E7D6D7` | `#1A1112` |
| surfaceBright | `#FFF8F7` | `#413738` |
| surfaceContainerLowest | `#FFFFFF` | `#140C0D` |
| surfaceContainerLow | `#FFF0F1` | `#22191A` |
| surfaceContainer | `#FBEAEB` | `#261D1E` |
| surfaceContainerHigh | `#F6E4E5` | `#312828` |
| surfaceContainerHighest | `#F0DEDF` | `#3D3233` |
| inverseSurface | `#382E2F` | `#F0DEDF` |
| inverseOnSurface | `#FEEDED` | `#382E2F` |
| inversePrimary | `#FFB2BC` | `#8E4955` |
| scrim | `#000000` | `#000000` |

**表 B：M3 baseline（调色板「默认紫」预设的种子 `primary #6750A4` / `secondary #625B71` / `tertiary #7D5260` / `error #B3261E`；数值是 M3 官方 baseline 语义色，本仓库运行时还会经 SPEC_2025 重算，**逐格精确值属推测**）**

| 角色 | 浅色 | 深色 |
|---|---|---|
| primary | `#6750A4` | `#D0BCFF` |
| secondary | `#625B71` | `#CCC2DC` |
| tertiary | `#7D5260` | `#EFB8C8` |
| error | `#B3261E` | `#F2B8B5` |
| background | `#FEF7FF` | `#141218` |
| surface | `#FEF7FF` | `#141218` |
| onSurface | `#1D1B20` | `#E6E0E9` |
| onSurfaceVariant | `#49454F` | `#CAC4D0` |
| surfaceVariant | `#E7E0EC` | `#49454F` |
| outline | `#79747E` | `#938F99` |
| outlineVariant | `#CAC4D0` | `#49454F` |
| surfaceContainerLowest | `#FFFFFF` | `#0F0D13` |
| surfaceContainerLow | `#F7F2FA` | `#1D1B20` |
| surfaceContainer | `#F3EDF7` | `#211F26` |
| surfaceContainerHigh | `#ECE6F0` | `#2B2930` |
| surfaceContainerHighest | `#E6E0E9` | `#36343B` |
| surfaceBright | `#FEF7FF` | `#3B383E` |
| surfaceDim | `#DED8E1` | `#141218` |

**位置槽（`UiSlot`）→ 角色映射**（`ui/theme/UiSlots.kt` `uiSlotsFrom()`，做原型时按位置取色，别直接取角色）：`pageBackground ← background`、`topBar ← surfaceContainer`、`card ← surfaceContainerHighest`、`listItem ← surfaceBright`、`panel ← surface`、`inputBar ← surfaceContainerLow`、`aiBubble ← surfaceContainerHigh`、`userBubble ← primaryContainer`、`titleText ← onSurface`、`bodyText / captionText ← onSurfaceVariant`、`accentText / accentIcon / button ← primary`、`buttonText ← onPrimary`、`divider ← outlineVariant`、`border ← outline`、`errorText ← error`、`errorBackground ← errorContainer`、`errorOnBackground ← onErrorContainer`、`scrim ← scrim`。

另有一套 **扩展色板**（`ui/theme/Color.kt`，`red/orange/green/blue/gray` 各 10 级，浅深各一套），设置页不直接用，原型做图表/用量色时可用：浅色 gray1 `#F7F8FA` … gray10 `#1D2129`；blue6 `#3491FA`；green6 `#00B42A`；red6 `#F53F3F`；orange6 `#FF7D00`。

### 4.3 字号层级

`ui/theme/Type.kt` 用的是 **`Typography()` 纯 M3 默认**（`RikkahubTheme` 只在「聊天字体应用到全局」时换 fontFamily，不改字号/字重/行高）。下面按 M3 baseline 列；**emphasized 变体的具体字号是推测**（库内值，仓库未覆盖），设置页只用到 `titleMediumEmphasized`（告警卡标题）与 `titleSmallEmphasized`（`CardGroup` 组标题）两处。

| 档位 | 字号 | 行高 | 字重 | 字距 | 本页用在哪 |
|---|---|---|---|---|---|
| displayTitle（M3 无此名，指 displayLarge） | 57sp | 64sp | 400 | -0.25sp | 无 |
| displayMedium | 45sp | 52sp | 400 | 0 | 无 |
| displaySmall | 36sp | 44sp | 400 | 0 | 无 |
| headlineLarge | 32sp | 40sp | 400 | 0 | 无 |
| headlineMedium | 28sp | 36sp | 400 | 0 | 无 |
| headlineSmall | 24sp | 32sp | 400 | 0 | Salt `largeTitle`（本页未用） |
| titleLarge | 22sp | 28sp | 400 | 0 | 顶栏标题 |
| titleMedium | 16sp | 24sp | 500 | 0.15sp | 首页昵称、左栏「设置」 |
| titleMediumEmphasized | 16sp | 24sp | 500 + Emphasized 字面 | 0.15sp | 告警卡标题（推测字重更粗） |
| titleSmall | 14sp | 20sp | 500 | 0.1sp | — |
| titleSmallEmphasized | 14sp | 20sp | 500 + Emphasized | 0.1sp | `CardGroup` 组标题 |
| bodyLarge | 16sp | 24sp | 400 | 0.5sp | 设置行主标题（Salt `main` / `ListItem` 默认） |
| bodyMedium | 14sp | 20sp | 400 | 0.25sp | 设置行副标题（Salt `sub` / `supporting`） |
| bodySmall | 12sp | 16sp | 400 | 0.4sp | — |
| labelLarge | 14sp | 20sp | 500 | 0.1sp | 左栏项文字、值胶囊、按钮 |
| labelMedium | 12sp | 16sp | 500 | 0.5sp | — |
| labelSmall | 11sp | 16sp | 500 | 0.5sp | 左栏分组标题 |

全局缩放：`displaySetting.uiScale` 默认 1.0，范围 0.5–2.0，通过改 `LocalDensity` 同时缩放 dp 与 sp（`Theme.kt:196-201`）——原型里对应 `html { zoom }` 或根字号缩放。

### 4.4 形状与圆角

M3 五档来自 `MaterialTheme.shapes`，默认值：`extraSmall 4dp`、`small 8dp`、`medium 12dp`、`large 16dp`、`extraLarge 28dp`。`RikkahubTheme` 把五档按「形状类型 × 边角大小」重建（`styledShape(style, radius, scale)`）：

| 形状类型（`CornerStyle`） | 实现 | scaleRatio |
|---|---|---|
| `ROUNDED`（默认） | `RoundedCornerShape` | 1.0 |
| `CUT` | `CutCornerShape` | 1.0 |
| `SQUIRCLE` | `SquircleShape`（`CornerSmoothing.Medium`） | 1.0 |
| `SMOOTH` | `ContinuousRoundedRectangle`（`Continuity.Default`） | 1.0 |
| `WAVY` | `WavyShape`（直边正弦起伏） | 1.0 |
| `SCOOP` | `ScoopShape`（圆角向内挖） | 0.65 |
| `NOTCH` | `NotchShape`（圆角处切直角缺口） | 0.65 |

`cornerScale`（「边角大小」）默认 **1f**，范围 0–2，实际半径 = `半径 × cornerScale × scaleRatio`。「全宽组件」打开 → 五档全变 `0dp`，且 `adaptiveCornerRadius()` 直接返回 0。

具体圆角清单（原型最常用）：

| 元素 | 圆角 | 依据 |
|---|---|---|
| 首页 Salt 分组卡片 | **推测 16dp**（Salt `shapes.large`） | `SaltThemeBridge.kt:52-55` |
| `CardGroup` 组首/尾 | **20dp** | `CardGroup.kt:61` |
| `CardGroup` 中间行 | **4dp** | `CardGroup.kt:63` |
| `CardGroup` 按下整行 | 20dp（四角） | `CardGroup.kt:330-338` |
| 左栏选中项 | **14dp**（硬编码，不跟轮廓设置走） | `SettingRail.kt:236` |
| 告警卡 | `shapes.extraLarge` = 28dp | `SettingPage.kt:493` |
| `CardGroupIcon` 图标底 | 圆（40dp 圆底） | `CardGroup.kt:240` |
| 值胶囊 | `CircleShape` 全圆 | `CardGroup.kt:264` |
| 分段控件 | M3 `ButtonGroupDefaults.connected*ButtonShapes()`（首/中/末连接形） | `ConnectedChoiceButtons.kt:120-124` |
| 顶栏返回键 | M3 `FilledTonalIconButton` 圆形 | `BackButton.kt` |

### 4.5 动效

| token | 值 | 依据 |
|---|---|---|
| 空间曲线（位移/尺寸/展开） | `MaterialTheme.motionScheme.defaultSpatialSpec()` | `MotionSpecs.kt:128-130` |
| 快空间曲线（吸附、按压缩放） | `fastSpatialSpec()`，选中项权重 1.0↔1.3 用它 | `MotionSpecs.kt:149-151` |
| 效果曲线（透明度/颜色/淡入淡出） | `defaultEffectsSpec()` | `MotionSpecs.kt:139-141` |
| **SYSTEM 档**（默认） | `MotionScheme.expressive()`（material3 库内弹簧/时长，**具体数值推测**：spring dampingRatio 0.8、stiffness 380 一类） | `MotionSpecs.kt:114-120` |
| **FAST 档** | spatial `spring(0.9, 900)` / fast `spring(0.95, 1400)` / slow `spring(0.9, 600)`；effects `tween(120)` / `90` / `180` | `MotionSpecs.kt:79-94` |
| **GENTLE 档** | spatial `spring(0.8, 220)` / fast `spring(0.8, 420)` / slow `spring(0.75, 150)`；effects `tween(320)` / `240` / `420` | `MotionSpecs.kt:96-112` |
| 关闭所有动画 | 整套 `MotionScheme` 换成全 `snap()`（一帧到位） | `Theme.kt:76-84` |
| 页面位移时长 | SYSTEM **300ms** / FAST **180ms** / GENTLE **420ms** | `MotionSpecs.kt:172-176` |
| 页面位移曲线 | `FancyTransitionEasing = cubic-bezier(0.48, 0.19, 0.05, 1.03)`（尾段 3% 过冲） | `MotionEasing.kt:27` |
| 透明度/遮罩/模糊半径 | `AlphaEasing = cubic-bezier(0.4, 0.4, 0.17, 0.9)` | `MotionEasing.kt:29` |
| 容器两点变形 | `PointToPointEasing = cubic-bezier(0.55, 0.55, 0, 1)` | `MotionEasing.kt:31` |
| 跟手位移 | `FastInvokeEasing = cubic-bezier(0, 0, 0, 1)` | `MotionEasing.kt:33` |
| 越界回弹 | `OverslideEasing = cubic-bezier(0.5, 0.5, 1, 0.25)` | `MotionEasing.kt:35` |
| 旋转 | `RotationEasing = cubic-bezier(0.46, 0.03, 0.52, 0.96)` | `MotionEasing.kt` |
| 长距离缓入缓出 | `SoftEasing = cubic-bezier(0.46, 0.41, 0.29, 0.92)` | `MotionEasing.kt` |
| 动态模糊 | 默认 `enabled = true`、`radius = 6dp`、`durationMillis = 280`、`travel = 32dp`、`scrollBlur = true`；曲线 `FastOutSlowInEasing`；关掉即 `snap()` | `data/datastore/Settings.kt:343-353`、`MotionSpecs.kt:61-72` |
| 换主题/拖对比度 | 全量颜色 `animateAllColors(tween(300))` | `ToolboxColorScheme.kt:84` |
| `CardGroup` 展开/收起 | 尺寸 `defaultSpatialSpec<IntSize>()` + 淡入 `fastEffectsSpec<Float>()` + 模糊同档 | `CardGroup.kt:402-416` |

原型建议：位移用 `cubic-bezier(.48,.19,.05,1.03)` 300ms，透明度 240ms 用 `cubic-bezier(.4,.4,.17,.9)`，颜色 120ms `ease`；「关闭动画」开时全改 `0s`。

---

## 5. 主题预设

### 5.1 `PalettePresets`（`ui/theme/PalettePresets.kt`，6 个；只给 4+1 个种子，其余 43 个角色由 M3 音调规则生成）

| id | 中文名（`palette_name_*`） | primary | secondary | tertiary | error | neutral |
|---|---|---|---|---|---|---|
| `default` | 默认紫 | `#6750A4` | `#625B71` | `#7D5260` | `#B3261E` | 跟随 primary |
| `ocean` | 海洋蓝 | `#116682` | `#4D616C` | `#5D5B7D` | `#BA1A1A` | 跟随 primary |
| `forest` | 森林绿 | `#4C662B` | `#586249` | `#386663` | `#BA1A1A` | 跟随 primary |
| `sunset` | 落日橙 | `#8B5000` | `#755843` | `#8C4A60` | `#BA1A1A` | 跟随 primary |
| `rose` | 玫瑰红 | `#8E4955` | `#76565A` | `#785831` | `#BA1A1A` | 跟随 primary |
| `monochrome` | 单色灰 | `#5C5C5C` | `#5C5C5C` | `#5C5C5C` | `#BA1A1A` | `#5C5C5C`（唯一显式给中性色） |

生成参数固定：`Variant.TONAL_SPOT`、`ColorSpec.SpecVersion.SPEC_2025`、`contrastLevel = 0.0`（`Palette.kt:688-720`）。生成后还会经 `withSurfaceFamily()` 把整族表面色按种子表面色的色相/彩度/音调平移（表面彩度上限 `SURFACE_CHROMA_MAX = 12.0`）。

另：`PaletteStyle`（同一个种子展开成不同气质，调色板编辑面板用）六档参数——`MONO`(0°,0°,×1.0,中性彩度4) / `ANALOGOUS`(+25°,+50°,×1.15,6) / `TRIAD`(+120°,+240°,×1.0,5) / `COMPLEMENT`(+180°,+180°,×1.0,4) / `VIVID`(+25°,+50°,×1.6,10) / `MUTED`(+25°,+50°,×0.45,3)。

### 5.2 附：`PresetThemes`（`themeId` 那七个，不是 PalettePresets，别混）

`ui/theme/presets/`，每个是一整套手写 `ColorScheme`（浅/深各 48 角色）：

| id | 名称 | primary（浅/深） | 底色（浅/深） |
|---|---|---|---|
| `sakura`（默认） | 樱花粉 | `#8E4955` / `#FFB2BC` | `#FFF8F7` / `#1A1112` |
| `ocean` | 海湾蓝 | `#116682` / `#8BD0EF` | `#F6FAFD` / `#0F1417` |
| `spring` | 原野绿 | `#4C662B` / `#B1D18A` | `#F9FAEF` / `#12140E` |
| `autumn` | 秋黄 | `#735C0C` / `#E3C46D` | `#FFF8F1` / `#16130B` |
| `black` | 中性黑 | `#606060` / `#EBEBEB` | `#FFFFFF` / `#1C1C1C` |
| `minimal` | 极简白 | `#2563EB` / `#8CB0FF` | `#FFFFFF` / `#0F1012` |
| `claude` | Claude | `#C96442` / `#E4906E` | `#FAF9F5` / `#1F1E1D` |

（七个文件的完整 48 角色都在 `ui/theme/presets/*.kt` 里逐行写死，上表只摘 primary 与 surface；`sakura` 的全量值见 4.2 表 A。注意 `themeId` 那一层会先被拆成 6 个种子再交给引擎重算，最终渲染值不等于这里的原值。）
