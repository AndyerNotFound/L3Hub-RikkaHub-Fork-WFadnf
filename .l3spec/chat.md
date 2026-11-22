# 聊天页界面规格（Compose → HTML 复刻用）

来源：只读阅读本仓库 `app/src/main/java/me/rerere/rikkahub/` 下的 chat 页与 message/ai 组件，未运行编译、未修改任何既有文件。
中文文案取自 `app/src/main/res/values-zh/strings.xml`（本仓库没有 `values-zh-rCN`，简体中文只在 `values-zh`；`values/strings.xml` 为英文原文）。下文「文案」一律给简体中文原值，括号里是 string key。
颜色/形状凡是主题驱动的，只写 token 名（`colorScheme.xxx`）；尺寸写代码里的字面值。

阅读范围（行数）：
`ui/pages/chat/ChatTopBar.kt(494)`、`ChatEmptyState.kt(174)`、`ChatList.kt(1006)`、`ChatPage.kt(部分: 930-1069)`、
`ui/components/ai/ChatInput.kt(1078)`、`ChatInputFields.kt(99-338)`、`ChatInputSingleLine.kt(283)`、`ContextUsageMeter.kt(531)`、`AttachmentChips.kt(301)`、`MessageQueuePanel.kt(193)`、`ChatCapsule.kt(96)`、`ChatQuotaStrip.kt(130-329)`、`TokenSpeed.kt(节选)`、
`ui/components/message/*` 全部（ChatMessage 1528、ChatMessageActions 633、ChatMessageTools 683、ChatMessageReasoning 430、ChatMessageCot 102、ChatMessageSubAgentNotice 368、ChatMessageEditedFiles 567、ChatMessageNerdLine 420、Avatar/Branch/Notices/Cot 全文）。
未读：`web-ui/`、`web/`（按约束跳过）。

---

## 1. 顶栏

顶栏是 `TopAppBar`，`containerColor = Color.Transparent`（`ChatTopBar.kt`）。**这个分支的顶栏没有「会话标题 + 副标题」两行文字**：标题位放的是「文件夹 + 模型」胶囊（`ChatModelFolderPill`）。原来上游那种「标题/小字」在这里不存在，复刻时不要把标题文字画进去。顶栏下面另有一条可选的供应商配额条（见 1.4），它才是视觉上「顶栏下方那一行小字」。

统一尺寸：按钮 45dp（`TopBarButtonSize`），图标 22.5dp（`TopBarButtonIconSize`），按钮:图标 = 2:1。

### 1.1 导航位（最左）
- 菜单按钮 → 左边缘，`start = adaptiveHorizontalPaddingValue(12dp)`，右侧同排间距 `spacedBy(6dp)`；按钮 45dp，形状 `CircleShape`（全宽组件模式改 `RoundedCornerShape(0dp)`）；底色 `surfaceContainerLow`；图标 `HugeIcons.Menu03` 22.5dp；语言标签 "Messages"。
- 显示条件：始终显示。横屏三栏时它是左栏开合，竖屏是抽屉；三栏且侧栏展开时图标 tint = `primary`（否则 `LocalContentColor`）。

### 1.2 标题位（中间）
胶囊 `ChatCapsule`，高度 45dp，形状药丸（`RoundedCornerShape(50)`；全宽模式 0dp），底色 `surfaceContainerLow`，投影 `appButtonShadow(shape, 2dp)`，BLUR 档加 1dp 描边 `outlineVariant alpha 0.5`。
- 左半：文件夹图标 `HugeIcons.Folder01` 16dp，tint `onSurfaceVariant` → 4dp 间距 → 文件夹名（`labelLarge`，单行省略），内边距 `start 8dp / end 6dp`；点开文件夹选单。
  - 文案：`folder?.name`，空则「未归类」（`chat_page_folder_unfiled`）。
- 中间：`ChatCapsuleDivider()` = 1dp 宽 × 18dp 高，`outlineVariant alpha 0.6`。
- 右半：`AutoAIIcon(model.modelId, color = Transparent)`（模型图标），内边距 `start 6dp / end 8dp`；点击打开模型选择面板。
- 显示条件：`!threePane && chatModel != null`。横屏三栏时标题位为空（模型在右栏标签页里）。
- 胶囊宽度天然被「菜单按钮与右侧按钮之间的剩余宽度」限制，文件夹名过长按此宽度省略号截断。

文件夹选单（`DropdownMenu`）条目顺序：
1. 小标题「会话文件夹」（`chat_page_folder_pill`，`labelMedium`，`onSurfaceVariant`，`padding h16 v8`）
2. 「未归类」（`chat_page_folder_unfiled`），选中时尾部 `Tick01` 18dp
3. 各文件夹（`Folder01` 18dp 前置图标，选中打勾）
4. 若无文件夹：「暂无文件夹」（`chat_page_folder_empty`，`enabled = false`）
5. `HorizontalDivider()`
6. 「新建文件夹」（`chat_page_create_folder`），前置 `FolderAdd`

### 1.3 右侧操作区
`Row`，`end = adaptiveHorizontalPaddingValue(8dp)`，`spacedBy(6dp)`，里面一颗 45dp 高的 `ChatCapsule`，入口之间用同一个 `ChatCapsuleDivider()` 分隔：

| 序 | 图标 | 尺寸 | 文案/语义 | 显示条件 |
|---|---|---|---|---|
| 1 | `HugeIcons.Layers01` | 22.5dp | 「详情」（`chat_page_details_button`） | `!threePane`；详情面板展开时 tint `primary` |
| 2 | `HugeIcons.Flowchart01` | 22.5dp | 「任务树」（`chat_task_tree_button`） | `!threePane` |
| 3 | `HugeIcons.PencilEdit01` | 22.5dp | contentDescription 硬编码英文 "New Message"（未走资源） | 始终 |
| 4 | `HugeIcons.MoreVertical` | 22.5dp | 「更多选项」（`more_options`） | 始终 |

右上角「更多」下拉（`DropdownMenuPopup`，分两组，组间距 `MenuDefaults.GroupSpacing`）：
- 组一：`LeftToRightListBullet` +「对话地图」（`chat_page_conversation_map`）→ `Share04` +「导出」（`export_title`）
- 组二：`Delete01` +「删除」（`delete`），destructive：文字与图标色 `error`（item 容器仍是 `MenuDefaults.itemColors()` 的默认底色）
- 显示条件：整个菜单 `enabled = hasMessages`，空对话时四条全部置灰。

### 1.4 顶栏下方的配额条（可选）
`ChatQuotaStrip`（`ChatPage.kt:1009` 渲染在 `TopBar` 之后，同一个 `topBar` Column 里）。
- 尺寸/形状：整宽，左右 `adaptiveHorizontalPaddingValue(12dp)`，`bottom 6dp`；圆角 `RoundedCornerShape(20dp)`（全宽 0dp）；内边距 `h14 v9`；底色 `surfaceContainerLow` + `appButtonShadow(2dp)`，BLUR 档 1dp `outlineVariant alpha 0.5` 描边；`spacedBy(8dp)`。
- 左图标：18dp，`HugeIcons.DashboardSpeed02` tint `primary`；告警态换 `Alert02` + `error`。
- 主文字：`provider.name`，`titleSmallEmphasized`，单行省略。
- 次文字（「那行小字」）：`labelSmall`，正常 `onSurfaceVariant` / 告警 `error`，单行省略。取值：锁定中 →「已锁定，%1$s 解锁」（`setting_provider_guard_lock_current`）；有限流/限额 → `RPM 42% · TPM 12% · …` 最多 4 段 ` · ` 连接；加载中 →「正在加载用量」（`setting_provider_page_usage_loading`）；无额度 →「无用量限额」（`setting_provider_page_usage_none`）；只有余额 →「余额」（`chat_quota_bar_balance`）；全空 →「当前渠道未配置限流或限额」（`chat_quota_bar_empty`）。
- 右侧余额格：`MoneyBag02` 14dp（低余额换 `Alert02` + `error`）+ 余额文本 `labelMedium`，最大宽度由 `BalanceCellMaxWidth` 封顶。
- 显示条件：`quotaProvider != null && quotaData != null`。

---

## 2. 消息气泡

### 2.1 一条消息的整体结构（自上而下）
外层 `Column`：`fillMaxWidth()`，横向对齐用户 `End` / 助手 `Start`，子项间距 `spacedBy(4dp)`。子块之间还有一层 `MessageBlockGap = 4dp`。

1. **头部行**（头像 + 名字）→ 占满整宽，`spacedBy(8dp)` 且整行靠 `End` 排；左侧 `ChatMessageAssistantAvatar(weight 1f)`，右侧 `ChatMessageUserAvatar(weight 1f)`。
   - 助手侧：头像 `28dp`；`useAssistantAvatar == true` 时用 `UIAvatar(assistant.avatar)`，否则用 `AutoAIIcon(model.modelId)`；生成中带 loading 动画。名字文本 `labelLargeEmphasized`，单行：助手模式取 `assistant.name`（空则「默认助手」），模型模式取 `model.displayName`（`maxLines = 2`）。
   - 用户侧：昵称文本 `labelLargeEmphasized` 单行 + 8dp 间距 + `UIAvatar` 28dp；昵称空则「用户」（`user_default_name`）。
   - 显示条件：`!message.parts.isEmptyUIMessage() && !joinedToPrevious`（同一角色连续时只有组首显示头像行）。各自还受 `showModelIcon` / `showModelName` / `showUserAvatar` 开关控制。
2. **气泡本体**（见 2.2~2.3）。
3. **操作行**（仅助手消息，见 2.5）。
4. **文件变更卡**（`EditedFilesList`，见 3.5）。
5. **统计行**（`ChatMessageNerdLine`，见 2.6）。

消息间距（`ChatList.kt`）：同角色相邻 `MESSAGE_GAP_JOINED = 2dp`，角色切换 `MESSAGE_GAP_ROLE_CHANGE = 16dp`；列表 `contentPadding` = 水平 `adaptiveHorizontalPaddingValue(16dp)`，垂直 `16dp`，额外顶部 `innerPadding.top`、底部 `32dp + innerPadding.bottom`；`verticalArrangement = spacedBy(0dp)`。

### 2.2 气泡几何
`ChatMessageBubble`（`ChatMessage.kt`）：
- 圆角基准 `corner = adaptiveCornerRadius(uiCornerRadius.dp)`（取设置里的圆角半径）。常量：`BubbleCorner = 24dp`、`BubbleTailCorner = 6dp`、`BubblePressedCorner = 12dp`、`BubbleJoinedCorner = 8dp`。
  - 尾巴角 `tail = min(6dp, radius * 0.25)`；按下时 `corner` 收到 `min(radius, 12dp)`；与同组相邻气泡相接的一侧用 `join = min(8dp, corner)`。
- 用户气泡：`topStart = corner（同组则 join）`、`topEnd = tail`、`bottomEnd/bottomStart = corner（同组则 join）`。
- 助手气泡：镜像 —— `topStart = tail`、`topEnd = corner`。
- 内边距：`padding(horizontal = 10dp, vertical = 10dp)`；内部 `MarkdownBlock` 自带 4dp 水平内边距。
- 底色：`LocalUiSlots.userBubble` / `LocalUiSlots.aiBubble`，各自 `.copy(alpha = bubbleOpacity)`；开启背景特效（BLUR/其它档）时着色由 `chatInputHaze` 叠在模糊上，BLUR 档再加 1dp `outlineVariant alpha 0.5` 描边。
- 宽度：
  - 用户气泡 `Modifier.padding(start = adaptiveHorizontalPaddingValue(24dp))` —— 起始侧留白，气泡不铺满整行，右侧贴父容器右缘（外层 Alignment.End）。
  - 助手气泡宽度由内容决定，最多占满父宽。
  - 全宽组件模式（`LocalFullWidthComponents`）：两者都 `fillMaxWidth()`，左右贴屏幕边。
- 背景/文字色：文字色 = `contentColorFor(bubbleColor)`。

### 2.3 用户气泡内容
- 附件卡片：`FlowRow`，间距 `6dp`；最多显示 3 个，第 3 个叠 `+N` 遮罩（黑 `alpha 0.55`，白字 `labelSmall` 粗体）。
  - 单卡：高 `44dp`，圆角 `18dp`，底色 `surface`，描边 1dp `outlineVariant alpha 0.35`，内边距 `start 8dp / end 10dp`，`spacedBy(8dp)`；缩略图/图标块 `34dp`、圆角 `10dp`、底色 `surfaceContainerHigh`（图片 `Crop`，视频 `Video01` 18dp，音频 `MusicNote03` 18dp，其它 `Files02` 18dp）；标题 `bodyMedium`，单行省略，宽度 `40..`（`widthIn(max = 180dp)`）。
- OCR / 视频识别结果卡：圆角 `18dp`，`surface`，1dp `outlineVariant alpha 0.35`，内边距 `h12 v10`，`spacedBy(6dp)`；标题 `labelMedium` `onSurfaceVariant`，文案「图片文字（OCR）」（`chat_message_ocr_result`）或「识别结果」（`chat_message_recognition_result`）；多图时小标签「第 %1$d 张图片」（`chat_message_ocr_result_image`，`labelSmall`）；视频：「视频识别结果」（`chat_message_video_recognition_result`）/「视频识别失败」（`chat_message_video_recognition_failed`，`error` 色）。
- 正文：用户长文默认折叠 **3 行**（`USER_MESSAGE_COLLAPSED_LINES = 3`），折叠高度按实测行高 × 3；右下角小字「展开」（`chat_message_expand_content`）/「收起」（`chat_message_collapse_content`），`labelSmall`，`LocalContentColor alpha 0.7`，内边距 `h4 v2`；只在真的溢出时出现。
- 交互：点气泡 = 编辑该消息；长按 = 弹就地菜单（触感 `LongPress`，`onLongClickLabel` =「长按查看操作」`chat_message_long_press_actions`）。用户气泡**没有**正文下方的常驻操作按钮行。

### 2.4 助手气泡内容
- 正文：`MarkdownBlock`。`showAssistantBubble` 开则套气泡；`splitAssistantBubbleByParagraph` 开则按段拆成多个气泡，段间距 `4dp`（首段继承 `joinedToPrevious`，末段继承 `joinedToNext`）。
- 流式时最后一段带 `streaming = true`（着色退回静态底，不重采样模糊）。
- 非文本部件：图片 `ZoomableAsyncImage` 高 `72dp`、圆角 `MaterialTheme.shapes.large`；载入中占位 `72dp` + `surfaceVariant` + shimmer；视频/音频为 `ChatMessageMediaTile`；文档为文件 chip（`docx`/`pdf` 图标或 `File02`，20dp）。

### 2.5 操作行（仅助手侧）
`FlowRow`，`horizontalArrangement = spacedBy(4dp)`，按钮用 `IconButtonDefaults.extraSmallContainerSize()`，图标 `ActionIconSize = 18dp`，tint `onSurfaceVariant`；最小触控尺寸被压到 0dp。

顺序与文案：

| 序 | 图标 | 文案（contentDescription） | 显示条件 |
|---|---|---|---|
| 1 | `Copy01` | 「复制」(`copy`) | 始终 |
| 2 | `Refresh03` | 「重新生成」(`regenerate`) | 始终（用户消息走确认框） |
| 3 | `VolumeHigh` / 朗读中 `StopCircle` | 「语音」(`tts`) | 仅助手消息，且 TTS 可用；朗读中容器色 `secondaryContainer`、内容色 `onSecondaryContainer` |
| 4 | `Translate` | 「翻译」(`translate`) | 仅助手消息，且 `onTranslate != null` |
| 5 | `MoreVertical` | 「更多选项」(`more_options`) | 始终 |
| 6 | 分支选择器 `ArrowLeft01` / `ArrowRight01` 各 16dp + 文本 `"1/2"`（`bodySmall`，`onSurfaceVariant`） | — | `node.messages.size > 1`；到头的一端 `alpha 0.5` |
| 7 | 时间文本 | `toMessageTimeString()`，`labelSmall`，`onSurfaceVariant alpha 0.6`，`padding h4` | 仅 `showDateTimeInMessage` 开启 |

- 操作行整体显示条件：`showActions && role != USER`，其中 `showActions = if (lastMessage) !loading else !parts.isEmptyUIMessage()`。
- 操作行进出带 `slideInVertically(it/2) + fadeIn`（反向退出）。

操作弹窗（助手：底部 `SaltModalSheet`；用户：贴气泡的原地 `Popup`）共用同一份条目，顺序固定：
1.「选择复制」`select_and_copy`（`TextSelection`）
2.「网页视图渲染」`render_with_webview`（`WebDesign01`；仅有非空文本时）
3.「编辑」`edit`（`Edit01`）
4.「分享」`share`（`Share04`）
5.「创建分支」`create_fork`（`GitFork`）
6.「添加收藏」`chat_message_add_favorite` / 「取消收藏」`chat_message_remove_favorite`（`FavouriteCircle`；仅当传入 `onToggleFavorite`）
7.「删除」`delete`（`Delete01`），destructive：容器 `errorContainer`、内容 `onErrorContainer`

弹窗结构：`padding(16dp)`，`spacedBy(16dp)`；用户消息顶部先挂一排快捷胶囊（`Surface` 药丸 `RoundedCornerShape(50)`，底色 `surfaceContainerHigh`，内边距 `h6 v4`，`spacedBy(2dp)`：复制 / 重新生成 / 分支箭头 / 时间）；底部信息区 `labelSmall` 居中两行：完整时间（`toLocalString()`）+ 模型名。
用户就地菜单：宽 `min(300dp, 窗口宽 - 16dp)`，最大高 `窗口高 × 0.6`，圆角 `shapes.extraLarge`，底色 `surfaceContainer`，`tonalElevation 3dp`、`shadowElevation 8dp`，展开从 `scale 0.86` 长出来。

### 2.6 统计行（token 用量 / 费用 / 耗时入口）
`ChatMessageNerdLine`：`labelSmall`，颜色 `secondary alpha 0.5`，`fontFeatureSettings = "tnum"`（等宽数字），圆角 `8dp`，内边距 `h4 v2`，点击弹 `RichTooltip`（标题「用量」`chat_message_usage_title`，位置 Above）。
固定两行（`Column spacedBy(2dp)`，行高按行高兜底，避免生成中反复长高/塌陷）：
- 第 1 行：`Repeat` 图标 +「%1$d 步」（`chat_message_usage_steps`，仅 `steps > 1`）；`MoneyBag02` + 费用（`chat_message_usage_cost`，仅 `cost.total > 0`，`formatCost()`）。
- 第 2 行：`Layers01` +「%1$s 上下文」（`chat_message_usage_context_short`）+ 若命中缓存「（已缓存 %1$s）」（`chat_message_usage_cached_short`）；`ChartColumn` +「总计 %1$s」（`chat_message_usage_total_tokens`，仅 `steps > 1`）。
- 显示条件：`showTokenUsage && message.usage != null`（无 usage 时整行不占位）。
- Tooltip 明细行：上下文分段条 + 「步数」「总输入」「缓存总量」（有缓存时）「总输出」+ 费用行 + 「速度 X tok/s」（`chat_message_usage_speed`）+ 「时长 X.Xs」（`chat_message_usage_duration`）；无数据时「暂无用量数据」（`chat_message_usage_empty`）。
- 上下文分段条（`ContextUsageBar`）：高 `8dp`，段间留 `2dp`，段圆角 = 高度/2；三段颜色 `primary`（缓存）/ `primary alpha 0.45`（未命中输入）/ `tertiary`（输出），轨道 `surfaceContainerHighest`；右侧文本 `"used / limit · N%"`，`fraction >= 0.9` 时转 `error`。另有饼图样式由 `usagePieChart` 切换。

---

## 3. 工具调用、推理过程与各类卡片

所有「思考/工具」都收在同一种时间线卡片 `ChainOfThought` 里（`ui/components/ui/ChainOfThought.kt`），每步一行 + 状态行 + 展开内容。

### 3.1 卡片外壳
- 卡片：`Card`，形状 `adaptiveShape(20dp)`，底色默认 `surfaceContainerHigh`；消息里实际传的是：
  - 无模糊源时 `chainBaseColor.copy(alpha = bubbleOpacity)`（特效关闭）或 `.copy(alpha = effectSurfaceAlpha)`；
  - 有 haze 且非流式时透明，由 `chatInputHaze` 上色。
  - BLUR 档加 1dp `outlineVariant alpha 0.5` 描边。
- 内容内边距：`padding(horizontal = 12dp, vertical = 4dp)`（整卡）。
- 折叠控制条（只有 `steps.size > collapsedVisibleCount` 才出现，默认保留尾部 `collapsedVisibleCount = 2` 步）：一行可点，`padding(vertical = 4dp)`；左 24dp 宽图标位内放 16dp 的 `ArrowDown01`/`ArrowUp01`，tint `primary`；8dp 后接文字 `labelMedium`、`primary`：
  - 收起时「再显示 %d 步」（`chain_of_thought_show_more_steps`）
  - 展开时「收起」（`chain_of_thought_collapse`）

### 3.2 单步（工具/推理/通知）折叠头
一行：`padding(vertical = 8dp)`，`spacedBy(8dp)`，垂直居中。
- 图标位：`Box(width = 24dp)` 居中放 `Box(20dp)`，里面 14dp 容器包 16dp 图标；无图标时画 8dp 圆点，色 `onSurfaceVariant`。
- 标题文本：`titleSmall`，颜色 `secondary`，`maxLines = 2`，省略号；运行中叠 shimmer（`isLoading = loading`）。
- 右侧 `extra`（工具审批时是两颗 28dp 的 `FilledTonalIconButton`，图标 14dp：`Cancel01`「拒绝」`chat_message_tool_deny`、`Tick01`「批准」`chat_message_tool_approve`）。
- 末尾指示器：16dp；`onClick` 存在时 `ArrowRight01`，有可展开内容时 `ArrowUp01`/`ArrowDown01`，tint `onSurfaceVariant`。
- 时间线：非末步从 `x = 12dp` 画 1dp 竖线，色 `outlineVariant`。

状态行（`progress != null` 时）：`padding(start = 32dp, end = 2dp, bottom = 8dp)`，`spacedBy(8dp)`。
- 左：波形进度条（`WaveProgressBar`，`weight(1f)`），色 `secondary`（失败 `error`），轨道同色 `alpha 0.15`；运行中流动，完成后落成 100%，失败时停在失败比例（无比例则 0）。
- 右：耗时文本 `labelSmall`，`widthIn(min = 40dp)`，右对齐，色 `secondary` / 失败 `error`，运行中 shimmer；格式 `spent.toString(DurationUnit.SECONDS, 1)`（例如 `3.4`）。推理卡同样格式。

展开内容：`padding(start = 32dp, top = 4dp, bottom = 8dp)`（缩进与标题对齐），进入/退出 `expandVertically + fadeIn`，`maxBlurRadius = 5dp`。

### 3.3 工具标题文案（折叠头主文字）
`renderer.title(context)`，未注册渲染器的工具走默认标题「调用工具 %1$s」（`chat_message_tool_call_generic`，`%1$s` 为 `tool.toolName`）。
已注册渲染器的实际文案（`values-zh`）：

| 工具 | 标题文案 | 图标 |
|---|---|---|
| `workspace_edit_file` | 「编辑：%s」（`tool_ui_edit_file`）/ 无路径时「编辑文件」（`tool_ui_edit_file_default`） | `HugeIcons`（渲染器自定） |
| `workspace_read_file` | 「读取: %s」（`tool_ui_read_file`）/ 默认「读取文件」 | — |
| `workspace_write_file` | 「写入: %s」（`tool_ui_write_file`）/ 默认「写入文件」 | — |
| `workspace_shell` | 「Shell: %s」（命令截断）/ 默认「Shell」 | — |
| `search_web` | 「搜索网页: %1$s」（`chat_message_tool_search_web`）+ 摘要「共 %1$d 条结果」（`chat_message_tool_search_results_count`） | `Search01` |
| `scrape_web` | 「抓取网页」（`chat_message_tool_scrape_web`，无参数） | `GlobalSearch` |
| `get_time_info` | 「获取时间信息」（`chat_message_tool_get_time`） | `Time02` |
| `clipboard_tool` | 「读取剪贴板」/「写入剪贴板」（`chat_message_tool_clipboard_read/write`） | `Clipboard` |
| `text_to_speech` | 「朗读: %s」（`tool_ui_speaking`） | `VolumeHigh` |
| `use_skill` | 技能名（`use_skill` 渲染器；标题硬编码为 `"Skill: $skillName"`，带 path 时为 `"Skill: $skillName / $path"`） | `MagicWand01` |
| `recent_chats` / `conversation_search` | 「查看最近的对话」/「搜索历史：%1$s」（`chat_message_tool_recent_chats` / `chat_message_tool_conversation_search`） | `Message02` / `Search01` |
| `get_screen_time` / `calendar_query` / `calendar_create` | 「读取屏幕时间」/「查询日历事件」/「创建事件: %1$s」 | `SmartPhone01` / `Calendar03` / `CalendarAdd01` |
| `chart_display` | 「显示图表」/「显示图表：%1$s」（`chat_message_tool_chart_display(_with_title)`） | — |
| `workspace_apply_patch` / `workspace_test` | 见 `WorkspaceReviewToolUIs.kt`（同套 `tool_ui_*` 文案） | — |
| 记忆类（`MemoryFileToolUI`） | 「读取了记忆」「列出了记忆」「创建了记忆」「更新了记忆」「删除了记忆」 | — |

- 拒绝态：折叠头下方加一行 `labelSmall`、`error`：「已拒绝」（`chat_message_tool_denied`）+ 有原因时 `": $reason"`。
- 工具输出里的图片：`LazyRow spacedBy(4dp)`，每张 `height(64dp)`、宽度自适应。
- 折叠头点按：横屏且存在右侧详情宿主时把详情推进右栏（`ToolDetailRequest`），否则弹 `SaltModalSheet`；输出不是 JSON 时详情退回默认原文预览。
- 工具入参/结果在**详情面板**（`DefaultToolPreview` / 各渲染器 `Preview`）里呈现：入参按单列字段展示（`ToolArgsFieldList`，字段名 + 值，代码类字段用等宽字体），解析不出字段时回落 JSON 高亮；输出按内容识别（JSON 格式化 / Markdown 富文本 / 纯文本可选中）。

### 3.4 推理（Thinking）区块
`ChatMessageReasoningStep`：
- 折叠头图标：`HugeIcons.Idea01` 16dp，tint `secondary`。
- 标题（`titleSmall`，`secondary`，`chatFontFamily`，运行中 shimmer）三种形态：
  1. 开启「思考摘要」时：「深度思考 · 」固定前缀 + 会滚动的短句（新句从下往上滚入，`TITLE_ROLL_MILLIS = 420`，位移 `10dp`，模糊 `3dp`）；
  2. 关闭摘要且正在写：整行加粗标题（`extractThinkingTitle()`）；
  3. 都拿不到：「深度思考」（`deep_thinking`）。
- 状态行右侧：耗时（同上格式，`widthIn(min = 40dp)`），失败 `error`。
- 展开内容：`bodySmall × fontSizeRatio` 的 Markdown，`fillMaxWidth`；
  - 「预览」态：`heightIn(max = 100dp)` + 可滚动 + 上下 `64f` 渐隐遮罩（`fadeHeight = 64f`，`BlendMode.DstIn`），`alpha 0.99f`；
  - 「展开」态：整段显示。
- 三个状态：`Collapsed`（收起）、`Preview`（生成中展开时只给 100dp 预览）、`Expanded`。
- 行为：生成中若 `showThinkingContent` 开，自动进入预览并贴底滚动；生成结束后按 `autoCloseThinking` 决定收起或保持展开；断在半路的段显示为失败态（进度条转 `error`）。

### 3.5 文件变更卡（`EditedFilesList` / 编辑文件卡片）
- 容器：`Surface`，`fillMaxWidth()`，圆角 `MaterialTheme.shapes.large`，底色 `surfaceContainerLow`。
- 头部行（可点折叠）：`padding h12 v10`，`spacedBy(8dp)`：`HugeIcons.File02` 16dp tint `primary` →「文件变更」（`chat_message_edited_files_title`，`labelLarge`，`onSurfaceVariant`）→「%1$d 个文件」（`chat_message_edited_files_count`，`labelMedium`，`primary`）→ 尾部 16dp `ArrowUp01`/`ArrowDown01`，`onSurfaceVariant`。
- 展开体：内边距 `start 8dp / end 8dp / bottom 8dp`，行间距 `2dp`；路径树按 `EDITED_PATH_INDENT = 20dp` 缩进，目录用 `ArrowRight01`/`ArrowDown01` 折叠。
- 底部（有可撤销目标时）：「撤销本轮修改」（`workspace_revert_turn`，`labelMedium`，前置 `Refresh01` 14dp），点开确认框：标题同文案，`workspace_revert_confirm` 说明，确认「撤销」（`workspace_revert_confirm_action`），成功/失败 toast：`workspace_revert_done` / `workspace_revert_nothing` / `workspace_revert_failed`。
- 显示条件：该消息含文件变更部件；横屏三栏且是最后一条时这一行收进右栏（`LocalTurnChangesInSidePanel`），消息下方不再渲染。

### 3.6 子代理卡片
两种情况，代码里是两套东西：
1. **派发调用 `dispatch_sub_agent`**（`SUB_AGENT_TOOL_NAME`，`data/ai/tools/local/SubAgentTool.kt`）：`ToolUIRegistry` 里没有为它注册渲染器（注册表见 `ToolUI.kt:121-141`），所以它走默认渲染：图标 `HugeIcons.Tools` 16dp，标题「调用工具 dispatch_sub_agent」（`chat_message_tool_call_generic`）。「推测」：产品上想要的「子代理卡片」若指这一步，需要新增渲染器，当前代码没有。
2. **子代理回传通知卡**（`ChatMessageSubAgentNotice`，`isSubAgentNotice` 的消息）：
   - 标题行（可点折叠，长按出菜单）：`padding(horizontal = 12dp, vertical = 2dp)`，`spacedBy(6dp)`：`HugeIcons.AiBrain01` 14dp tint `primary` →「子代理任务完成」（`chat_message_sub_agent_notice_title`，`labelMedium`，`primary`）→「系统自动回传」（`chat_message_sub_agent_notice_hint`，`labelSmall`，`onSurfaceVariant`，`weight(1f)`）→ 16dp `ArrowUp01`/`ArrowDown01`。
   - 正文容器（展开后）：`Surface` fillMaxWidth，底色 `surfaceContainerLow`，圆角 `MaterialTheme.shapes.medium`，内边距 `h12 v10`，内容为该消息文本的 Markdown。
   - 默认收起。长按菜单两项：编辑（弹「编辑子代理结果」`chat_message_sub_agent_notice_edit_title` 对话框，输入框 `heightIn(min = 120dp, max = 320dp)`，确认「确定」`common_confirm`，取消「取消」`cancel`）、删除。
   - 外层 Column `spacedBy(6dp)`。

### 3.7 图片生成卡片
代码里**没有**独立的「图片生成」卡片渲染器：`BuiltInTools.ImageGeneration` 只是模型能力枚举（`ai/src/main/java/me/rerere/ai/provider/Model.kt:88`），`ToolUIRegistry` 中也没有图片生成渲染器。生图结果以 `UIMessagePart.Image` 出现：
- 在消息正文里：`ZoomableAsyncImage`，高 `72dp`，圆角 `shapes.large`；URL 未就绪时占位 `size(72dp)` + `surfaceVariant` + shimmer。
- 在工具输出里：折叠头展开后的 `LazyRow`，每张高 `64dp`。
- 折叠头文本仍是「调用工具 %1$s」（`chat_message_tool_call_generic`）。
「推测」：若原型需要独立的生图卡片（标题/尺寸/进度/重试），需要新增渲染器，现有代码没有对应尺寸与文案可抄。

### 3.8 其它步内卡片（同套折叠头）
- 服务端工具：图标运行中 `DotLoading(size = 10dp)`，否则 `HugeIcons.Tools` 16dp（`onSurfaceVariant alpha 0.7`）；标题「调用工具 %1$s」`titleSmall` `secondary`，`maxLines = 2`。
- 运行时通知（`notice`）：`Notification01` 16dp；标题 `notice.title`；展开显示正文 `bodySmall` 等宽字体，`onSurfaceVariant`。
- 应用注入的引导：`Notification01`，标题「引导」（`guidance_tool_label`），正文 `bodySmall` `onSurfaceVariant`。
- 任务清单提醒：`Tick01`，标题「任务清单」（`todo_reminder_tool_label`），正文同上。
- `ask_user` 问答：`BubbleChatQuestion`；标题为第一个问题原文（多问题时「询问 %d 个问题」`chat_message_tool_ask_questions`），`titleSmall` `secondary`；展开后每个问题 `bodySmall` + 选项 `FilterChip`（`labelSmall`）+ 多行输入框（`minLines 1 / maxLines 3`）；底部 `FilledTonalButton`「提交」（`chat_message_tool_submit`，`Tick01` 16dp），已回答的问题显示 `bodySmall` `primary` 文本。
- 图表：`chart_display` 成功时在正文位置直接渲染 `ChartCard`（不走折叠卡）。

---

## 4. 空状态（无消息）

渲染位置：`ChatList.kt` 里 `conversation.currentMessages.isEmpty() && emptyHero != null` 时，`Box(align = Center, padding(bottom = 32dp))`；同时 `ChatPage` 把输入区整体上移到英雄卡下方（`translationY = emptyLift`）。

- 布局容器（`EmptyChatContent`）：`BoxWithConstraints` fillMaxSize + `innerPadding`；内层 `Column` fillMaxWidth、`verticalScroll(enabled = scrollEnabled)`、`heightIn(min = maxHeight)`、`padding(vertical = 16dp)`，水平居中、垂直居中。
- 英雄卡（`EmptyChatHero`）：
  - 外边距 `padding(horizontal = 40dp)`；圆角 `RoundedCornerShape(28dp)`；底色 `surfaceContainerLow`；特效开启时用 `chatInputHaze` 上色，BLUR 档加 1dp `outlineVariant alpha 0.5` 描边；内边距 `horizontal = 28dp, vertical = 24dp`；`spacedBy(12dp)`，内容水平居中。
  - 头像：`UIAvatar(name = assistant.name, value = assistant.avatar, avatarSize = 88dp, shape = CircleShape)`。
  - 文案：`slogan`（助手配置的口号），空则「今天想聊点什么？」（`chat_empty_default_slogan`）；样式 `titleMedium`，`onSurface`，居中。
- **没有任何快捷提示按钮/建议 chip**：`ChatEmptyState.kt` 里只有头像 + 一句口号。回复建议（`ChatSuggestionsColumn`）是会话产生后追加在列表尾部的，不属于空状态。
- 动效：进场 `fadeIn`，首条消息进来时 `fadeOut` + 模糊（`maxRadius = 8dp`）；键盘弹起时整卡 `translationY = -(imeBottom / 2)` 上移。

---

## 5. 输入区

`ChatInput` 的根是一个 `Surface(color = Transparent)`，包一层 `drawBehind` 的底部渐变遮罩（`background` → `alpha 0f..scrimAlpha()`），再是 `Column`：`imePadding().navigationBarsPadding()`、`adaptiveHorizontalPadding(8dp)`、`padding(bottom = 8dp + liftedBy)`、内容贴底对齐、`spacedBy(8dp)`。

### 5.1 从上到下每一层
1. **任务清单 / 引导卡**（`ConversationTodoCard`）→ `fillMaxWidth()` + `adaptiveHorizontalPadding(12dp)` + `padding(vertical = 4dp)`；清单与引导共用一张卡、一次折叠。显示条件：`!LocalConversationStatusInSidePanel`（非三栏）且（有未完成清单 或 有引导）。
2. **消息队列面板**（`MessageQueuePanel`，见 6.4）→ 仅有排队项时。
3. **世界书提示**：`Text`「已注入世界书 %1$d 条」（`chat_page_worldbook_injected`），`labelSmall`，`onSurfaceVariant`，`padding(horizontal = 8dp)`；条件 `injectedLorebookCount > 0`。
4. **上下文计量条**（`ContextMeterSwitcher`）→ 条件 `chatModel != null && contextMeterStyle != OFF`，见 5.3。
5. **输入卡片**（`Surface`）→ 见 5.2。
6. **悬浮栏**（`ChatFloatingBar`，`ChatFloatingBar.kt`）→ 输入卡片之后，条件 `enableFloatingBar && floatingBarItems 非空`；聚焦时收起、上滑输入框 24dp 阈值重新拉起，自动隐藏开启时静默 5s 收起。

### 5.2 输入卡片内部（自上而下，`spacedBy(2dp)`）
外壳：`fillMaxWidth()`，`clip(containerShape)`，`chatInputHaze`；`shape = adaptiveShape(uiCornerRadius.dp)`（全宽模式 0dp）；`tonalElevation = 0dp`；描边 `BorderStroke(1dp, outlineVariant.copy(alpha = 0.5f))`；底色 `LocalUiSlots.inputBar`（特效开启时透明，由 haze 上色）；内边距 `horizontal = inputPadding.dp, vertical = 4dp`。内层形状 `innerShape = adaptiveShape((uiCornerRadius - 8).coerceAtLeast(2).dp)`。

1. **计划模式提示**（`assistant.planMode`）：`labelSmall`，`primary`，「计划模式 · 只读探索 · 点击退出」（`chat_input_plan_mode_hint`），`padding(horizontal = 4dp, vertical = 2dp)`，点击退出。
2. **语音模式行**（`VoiceModeRow`）+ `HorizontalDivider(outlineVariant alpha 0.5)`；条件 `voiceState.phase != Off`。
3. **附件 chips**（`MediaFileInputRow`，`AttachmentChips.kt`）→ 条件 `state.messageContent.isNotEmpty()`。
   - 容器：`Row`，`fillMaxWidth()`，`padding(horizontal = 6dp, vertical = 6dp)`，`spacedBy(8dp)`，横向滚动。
   - 单个 chip：高 `44dp`，圆角 `18dp`，底色 `surface`，`tonalElevation 1dp`，描边 1dp `outlineVariant alpha 0.35`，内边距 `start 8dp / end 6dp`，`spacedBy(8dp)`；前置块 `34dp`、圆角 `10dp`、`surfaceContainerHigh`（图片 Crop，视频 `Video01`，音频 `MusicNote03`，文档 `Files02`）；文件名 `bodyMedium`，宽度 `40..180dp`，单行省略；尾部移除按钮 `26dp` 圆形 + `Cancel01` 16dp（`onSurfaceVariant`）。
4. **编辑旧消息提示条**（`EditingBanner`）→ 条件 `state.isEditing()`。
   - `Surface(shape = innerShape, color = surfaceContainerHigh)`，内边距 `h14 v4`：「编辑中」（`editing`）+ 右侧若存在思考则 `TextButton`「编辑思维过程」（`chat_input_edit_reasoning`）+ `Cancel01` 图标（`cancel_edit`）点击清空。
5. **文字输入框**（`TextInputRow`）：
   - `TextField` fillMaxWidth，`shape = MaterialTheme.shapes.largeIncreased`（全宽 0dp），透明容器/指示线；`heightIn(max = 5 × 行高 + 24dp)`，`TextFieldLineLimits.MultiLine(maxHeightInLines = 5)`。
   - placeholder：默认「输入消息与AI聊天」（`chat_input_placeholder`，`maxLines = 1`，省略号）；单行布局展开后「向 %1$s 描述你的想法…」（`chat_input_placeholder_expanded`，`%1$s` = 模型显示名）。单行布局的占位符是逐字渐变（旧字上浮模糊淡出、新字自下而上补位）。
   - `trailingIcon`：`Fullscreen` 图标按钮（有文本，或（单行布局下溢出 / 多行布局下聚焦）时）；`leadingIcon`：快捷消息按钮（`QuickMessageButton`，`HugeIcons.Zap` 18dp，容器 30dp；点击开快捷消息/引导菜单，长按把输入内容当引导发出去），条件 `showQuickMessageButton`。
   - 输入行 `spacedBy(4dp)`；`sendOnEnter` 开启时回车即发送。
6. **按钮行**（两种布局，见 5.4）。

按钮尺寸：`ActionIconButton` = `Surface(onClick)` + `size(30dp)`，形状 `CircleShape`（全宽 0dp），透明底色 + `appButtonShadow(shape, 2dp)`。

### 5.3 上下文计量条（两条进度条）
`ContextMeterSwitcher` 把「直条」和「环形」两种视图叠在同一位置，左右滑动切换（阈值：位移 `60px` 或速度 `900px/s`；切换时各自叠最多 `6dp` 动态模糊）。

**直条样式（BAR，默认）** `ContextUsageMeter`：
- 容器：`fillMaxWidth()`，`clip(shapes.small)`，可点（打开用量看板），`padding(horizontal = 4dp, vertical = 4dp)`，`spacedBy(8dp)`；左侧 `Column(weight 1f, spacedBy 4dp)`，右端放速度表盘。
- 每条一行（`ContextMeterRow`，`spacedBy(8dp)`）：
  - 标签：`labelSmall` + `FontWeight.Medium`，`widthIn(min = 34dp)`；文案「WARN」（`chat_page_context_meter_warn`，中文包里仍是 `WARN`）/「MAX」（`chat_page_context_meter_max`）。
  - 进度条：`WaveProgressBar(weight 1f)`，轨道 `onSurface alpha 0.18`；生成中流动、停止后静止。
  - 数值：`labelMedium` + `FontFamily.Monospace`，右对齐，`widthIn(min = 128dp)`，格式「%1$s / %2$s · N%」（`chat_page_context_meter_absolute` + ` · $percent%`）；百分比不封顶，可 >100%。
- 颜色（`contextMeterColor`）：`fraction >= 1f` → MAX 条 `error`、WARN 条 `tertiary`；`fraction >= 0.8f`（`ALERT_FRACTION`）→ `tertiary`；否则 `primary`。文本色：`fraction >= 0.8` 时用填充色，否则 `onSurfaceVariant`。
- 两条条的分母：WARN 条 = `warnContextTokens`（填满即最佳性能点之后）；MAX 条 = `maxContextTokens`（填满即禁止发送）。
- 超限提示：`labelSmall`、`error`「上下文已达上限，请压缩历史或新建对话」（`chat_page_context_limit_reached`）。

**环形样式（RING）** `ContextUsageRing`：
- 容器 `fillMaxWidth()`，`clip(shapes.small)`，`padding(h8 v2)`，`spacedBy(10dp)`。
- 左侧 `Box(46dp)`：外环 4dp 描边（主进度，起点 -90°，`StrokeCap.Round`），内环 2dp（WARN 环，仅两条都存在时画）；轨道 `onSurface alpha 0.15`（内环 `alpha 0.6`）；圆心百分比文字 `labelSmall`、`fontSize = 9.sp`、等宽。
- 右侧 `Column(spacedBy(1dp))`：两行「WARN / MAX」+ 数值（`labelSmall` 等宽，`fraction >= 0.8` 时上色），最大高 46dp 对齐。

**百分比/绝对值切换**：两种样式都同时显示「绝对值 / 绝对值 · 百分比」，没有单独的切换按钮；切换的是「直条 ↔ 环形」视图（左右滑动手势），落位后写回 `contextMeterStyle`。

**速度表盘**（`TokenSpeedGauge`，`TokenSpeed.kt`）：直径 `30dp`，弧线 `2.5dp`，指针 `1.6dp`，中心点 `1.4dp`，针长 = 半径 - 描边 - `1.5dp`；颜色取 `colorScheme.primary` 等主题色；无实时速度时回落到 `fallbackSpeed`（最近一次 usage 的 tok/s）。位置固定在计量条最右端。

### 5.4 底部按钮行的两种布局与顺序
**多行布局（默认，`InputLayoutStyle` 非 SINGLE）**：`Row` `fillMaxWidth()`、`padding(horizontal = 4dp)`、`spacedBy(4dp)`，垂直居中。
- 左侧可滚动组 `Row(weight 1f, horizontalScroll, spacedBy 2dp)`：① 模型选择按钮（`ModelSelectorButton`，仅图标）② 推理档位按钮（`ReasoningButton`，仅图标；条件模型 `abilities` 含 `REASONING`）。
- 右侧固定组（按下列顺序）：③「+」`Add01`（`more_options`）→ 打开附件面板 ④ ASR 语音输入按钮（条件 `asrState.isAvailable || isRecording`，录音中发送按钮淡出）⑤「插话」`MessageAdd01`（`chat_page_interject`；条件 `loading && enableMessageQueue && 输入非空`）⑥ 发送/停止按钮。

**单行布局（`InputLayoutStyle.SINGLE`）**：文字与按钮同一行，卡片收起时高 1 行、聚焦或有内容时展开成 2 行（`actionRowHeight = 48dp`，展开曲线 `CubicBezierEasing(0.32, 0.72, 0, 1)`，380ms）。
- 行内左侧：`+`（`Add01`）→ 快捷消息按钮（`Zap`）。
- 中间左：模型胶囊 `ChatInputModelPill`（高 `30dp`，`CircleShape`，底色 `surfaceContainerHigh`，内边距 `h9`，`spacedBy(6dp)`：6dp 圆点 `primary` + 模型名 `labelMedium`（空则「选择模型」`model_list_select_model`）+ 推理档位标记（L3 风格是 `labelSmall` 粗体 `9sp/10sp` 文字，否则 14dp 图标）+ `ArrowDown01` 12dp；仅展开时可见；可思考模型点开先给强度面板）。
- 右侧：ASR → 插话 → 发送/停止（顺序同多行布局）。

**附件/相机**：底部按钮行里没有独立的相机/附件按钮，`+` 打开附件面板（`FilesPicker`，`ChatSidePane.Files`），面板内的动作依次是：拍照（`take_picture`）、照片（`photo`）、草图（`sketch`）、视频（`video`）、音频（`audio`，仅 Google 渠道）、上传文件（`upload_file`）、语音模式（`chat_page_voice_title`）；下方才是搜索/工作区/MCP/扩展/计划模式/压缩上下文等分段列表项。

**发送/停止按钮**（`SendButton`）：`Box(size = 30dp)`，形状 `CircleShape`（全宽 0dp），内含 `Surface` + `appButtonShadow(2dp)`，图标 18dp。
| 状态 | 容器色 | 内容色 | 图标 | contentDescription |
|---|---|---|---|---|
| 生成中且输入为空（停止） | `errorContainer` | `onErrorContainer` | `Cancel01` | 「停止」（`stop`） |
| 输入为空（禁用） | `surfaceContainerHigh` | `onSurface alpha 0.38` | `ArrowUp02` | 「发送」（`send`） |
| 有内容（可发送） | `primary` | `onPrimary` | `ArrowUp02` | 「发送」（`send`） |
- 长按 = 发送但不等待回答（`onLongSendClick`）；生成中长按同样是停止。

---

## 6. 流式生成中的状态

### 6.1 列表底部的「等待中」指示
`ChatList.kt` 的常驻 item（`LoadingIndicatorKey`）：
- 行：`Row`，`padding(start = 8dp, end = 8dp, bottom = 8dp, top = MESSAGE_GAP_ROLE_CHANGE = 16dp)`，`spacedBy(8dp)`，垂直居中。
- 左侧：`ContainedLoadingIndicator`，`size(28dp)`；颜色 `tokenSpeedRatioColor(speedRatio)` —— 停住/极慢为灰、中等冰蓝、摸到本轮峰值转红（`speedRatio` = 本轮 `generationSpeed.peakRatio`，动画补齐两档之间）。
- 右侧：处理状态文本，`labelMedium`，`onSurfaceVariant`；内容来自 `ChatService` 的 `processingStatus`：
  - 压缩上下文时：「正在压缩……」（`chat_page_compressing`）
  - 网络重试时：「%1$s，正在重试（%2$d/%3$d）…」（`chat_generation_network_retrying`；`%1$s` 为「无法解析服务器地址」`chat_generation_network_unknown_host` / 「连接超时」`chat_generation_network_timeout` / 不可达 / 已断网）
  - 其余时刻为空字符串（只有转圈）。
- 显示条件：`loading`（整行用 `MotionExpandCollapse(visible = loading, maxBlurRadius = 6dp)` 收放，压缩中也复用这一行）。

### 6.2 生成期间的界面行为
- 停止按钮：见 5.4（输入框为空时发送位变 `Cancel01` + `errorContainer`）。
- 输入框非空时多出「插话」按钮（`MessageAdd01`），打断当前回复立刻发送。
- 列表自动贴底：`enableAutoScroll` 开时每帧按缺口 `scrollBy`；新助手消息追加时先 `animateScrollToItem`。
- 为省帧：生成中撤掉列表的 `hazeSource`、顶部渐隐（`topFadingEdge(enabled = !loading)`）与滚动模糊（`followScrollBlurPaused`）；气泡与思考卡在流式期间改用静态底色（不重采样模糊）。

### 6.3 错误提示
`ErrorCardsDisplay`（`ui/components/ui/ErrorCard.kt`），挂在列表 Box 的 `BottomCenter`，`zIndex(5f)`，另有 `padding(horizontal = 16dp, vertical = 8dp)`；进入/退出 `slideInVertically + fadeIn`（反向）。
- 多条错误时顶部一颗「清除全部」按钮：圆角 `8dp`，底色 `errorContainer alpha 0.8`，内边距 `h12 v6`，`Delete01` 14dp + 文案「清除全部」（`chat_page_clear_all_errors`），`labelSmall`，`onErrorContainer`。
- 单张错误卡：`fillMaxWidth()`，圆角 `12dp`，底色 `errorContainer`，`shadowElevation = 4dp`，内边距 `12dp`，`spacedBy(12dp)`；**5 秒后自动消失**（`delay(5000)`）。卡片内文案键包括「检查快速模型设置」（`chat_page_check_fast_model_settings`）等（`ChatError.solutions`）；正文里有可点链接（`primary`）。
- 用户消息的「回复失败」另有 `ChatMessageNotices` 卡片：`Surface(onClick = 展开/收起)`，`fillMaxWidth()`，圆角 `shapes.large`，底色 `surfaceContainerLow`，内边距 `h12 v10`，`spacedBy(8dp)`；每行 `Notification01` 16dp + `notice.title`（`labelMedium`，`onSurfaceVariant`，`maxLines = 2`）；展开后逐条 `bodySmall` 等宽正文 + 右下两个 `TextButton`：「重试」（`chat_page_notice_retry`）、「删除」（`delete`）。

### 6.4 消息队列面板
`MessageQueuePanel`（`MessageQueuePanel.kt`，位于输入卡片上方第 2 层）：
- 外壳：`Surface` fillMaxWidth，圆角 `MaterialTheme.shapes.large`，底色 `surfaceContainer`，内边距 `h12 v4`。
- 标题行：左文本 `labelMedium`、`onSurfaceVariant`、`weight(1f)`、`padding(vertical = 8dp)`：
  - 未暂停：「待发送 · %1$d」（`chat_page_queue_pending_count`）
  - 已暂停：「队列已暂停 · %1$d」（`chat_page_queue_paused_count`）
- 暂停时右侧 `TextButton`「继续发送」（`chat_page_queue_resume`）。
- 列表：`LazyColumn(heightIn(max = 180dp))`，每条一行 `spacedBy(4dp)`：
  - 普通待发消息：`"${index+1}. "` + 各部件文本；附件用占位词「[图片]」（`chat_page_queue_image`）/「[文件]」/「[音频]」/「[视频]」，`bodySmall`，`maxLines = 2` 省略；右侧 `TextButton`「编辑」（`edit`，编辑中显示「编辑中」`chat_page_queue_editing` 且禁用）+「撤销」（`chat_page_queue_remove`）。
  - 通知类（`QueuedNotice`）：`Notification01` 14dp + `notice.title`（`bodySmall`，`maxLines = 2`）+「撤销」。
- 编辑队列项：`AnimatedAlertDialog`，标题「编辑待发送消息」（`chat_page_queue_edit_title`）；正文为附件行（若有）+ 多行 `TextField`（`minHeightInLines = 3, maxHeightInLines = 8`）；确认「保存」（`chat_page_save`，输入非空才可点），取消「取消」（`cancel`）。

### 6.5 其它运行中提示
- 工具步：运行中图标位换 `DotLoading(size = 10dp)`，标题 shimmer；耗时每 `100ms` 刷新。
- 服务端工具：运行中 `DotLoading(10dp)`，进度条画不定进度；失败转 `error` 并归零。
- 生成的正文/思考：`shimmer` 只用于标题与耗时数字，正文本身不加遮罩。

---

## 附：本文没有写的内容（避免编造）
- 主题里 `adaptiveShape` / `adaptiveCornerRadius` / `appButtonShadow` / `chatInputHaze` 的具体实现（取色、模糊半径、阴影参数）在 `ui/theme/` 与 `ui/components/ai/ChatInputHaze.kt`，本次未读，只写调用点与传入值。
- `LocalUiSlots.userBubble` / `aiBubble` / `inputBar` / `pageBackground` 的具体色值（由 UI 槽位/主题决定），只记 token 名。
- 回复建议（`ChatSuggestionsColumn`）、对话地图、导出表、任务树/右栏等页面级浮层只提了入口，未逐项展开。
- 图片生成的独立卡片：代码中不存在，见 3.7 的说明。
