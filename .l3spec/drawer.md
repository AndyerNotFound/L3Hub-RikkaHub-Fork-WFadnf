# 会话列表与侧边抽屉规格（Android Compose → HTML 原型）

只读代码整理，未做任何修改。基准路径 `app/src/main/java/me/rerere/rikkahub/`。

## 0. 读码范围与两处与工单不符的实情

- 工单写的 `app/src/main/res/values-zh-rCN/strings.xml` **不存在**。本仓库简体中文在 `app/src/main/res/values-zh/strings.xml`（繁体为 `values-zh-rTW`）。本文所有中文均取自 `values-zh/strings.xml` 的 `<string>` 原文。
- 工单提到的 `ui/pages/chat/ChatListPreview.kt` **不在抽屉里**。`ChatListPreview` 只在 `ui/pages/chat/ChatList.kt:306` 被消息列表调用，是长按消息的行内预览弹层，与抽屉无关。本文按"不涉及"处理。
- 抽屉内容没有二级实现，只有一份 `ChatDrawerContent`（`ChatDrawer.kt:136`），竖屏当模态抽屉、横屏当常驻左栏，两种形态共用。
- 抽屉里**没有版本号**，也没有模型切换器（详见第 4 节）。

---

## 1. 抽屉整体

### 1.1 容器与尺寸

| 元素 | 位置/尺寸 | 文案/图标 | 显示条件 |
|---|---|---|---|
| 抽屉底板 | `ModalDrawerSheet`，宽 `minOf(300.dp * 1.2f, 屏宽)` = **min(360dp, 屏宽)**（`ChatDrawer.kt:119-120, 166`） | — | 竖屏模态形态 |
| 底板形状 | `RectangleShape`，四角**无圆角**（`ChatDrawer.kt:314`，注释说明左栏是一整块边，圆角会像浮卡） | — | 始终 |
| 底板着色 | `glassSurface(baseColor = surfaceContainerLow, minAlpha = 0.65f)`；`enableBlurEffect` 为真时 `drawerContainerColor = Color.Transparent`，否则 `surfaceContainerLow` | — | 始终；`minAlpha 0.65` 是下限，低于此正文会透上来 |
| 内容内边距 | 内层 `Column(Modifier.padding(8.dp), verticalArrangement = spacedBy(8.dp))`（`ChatDrawer.kt:337-340`） | — | 始终 |
| 横屏左栏宽度 | `widthOverride = sidebarWidthDp`，默认 **300.dp**（`ChatPage.kt:1434`），拖动范围 **200f..440f**（`ChatPage.kt:366-368`） | — | `threePaneLayout` 成立时 |
| 拖宽手柄 | `ColumnResizeHandle`：热区宽 **10.dp** `fillMaxHeight()`，视觉条宽 **3.dp**、高 30%（`fillMaxHeight(0.3f)`）、圆角 2.dp、色 `outlineVariant`（`ui/components/ui/ColumnResizeHandle.kt:25-55`） | — | 仅横屏左栏展开时 |
| 顶部高度分界 | `scrollableHeader = windowHeight < 640.dp`（`ChatDrawer.kt:167`）：窗口高 < 640dp 时，顶部区块（更新卡/备份卡/快捷入口/标题行）改为塞进会话列表当第 0 项，跟随列表一起滚动 | — | 条件见左 |
| 滚动位置记忆 | 滚动停下 300ms 后写 `SavedStateHandle`（`SCROLL_POSITION_SAVE_DELAY_MS = 300L`），存的是"不含 header 的下标" | — | 始终 |

### 1.2 展开与关闭

| 元素 | 位置/尺寸 | 文案/图标 | 显示条件 |
|---|---|---|---|
| 展开容器 | 竖屏：`ModalNavigationDrawer(drawerState = rememberDrawerState(DrawerValue.Closed), ...)`（`ChatPage.kt:399`）。未传 `gesturesEnabled`，即库默认允许**屏幕边缘横向拖拽**拉开——此为 Material3 默认值，非本仓库代码依据（推测项） | — | `!threePaneLayout` |
| 顶栏开抽屉按钮 | `Surface` **45.dp** 方形（`TopBarButtonSize`，`ChatTopBar.kt:78`），图标 **22.5.dp**（`TopBarButtonIconSize`，`ChatTopBar.kt:79`），形状 `CircleShape`（全宽模式改 `RoundedCornerShape(0.dp)`），底色 `surfaceContainerLow`+毛玻璃+`appButtonShadow(_, 2.dp)` | 图标 `HugeIcons.Menu03`；`contentDescription = "Messages"`（硬编码英文）；无可见文字 | 顶栏左侧导航位 |
| 顶栏按钮点亮态 | 横屏三栏且左栏开着时 tint = `colorScheme.primary`，否则 `LocalContentColor.current`（`ChatTopBar.kt:177-183`） | — | `threePane && sidebarOpen` |
| 返回键关闭 | `BackHandler(enabled = drawerState.isOpen) { drawerState.close() }`（`ChatPage.kt:196-198`） | — | 抽屉开着 |
| 打开时收键盘 | `LaunchedEffect(drawerState.isOpen)`：清焦点 + `softwareKeyboardController.hide()`（`ChatPage.kt:201-206`） | — | 抽屉开着 |
| 点会话后关闭 | `openConversation` 直接 `navigateToChatPage`，新会话页带自己的抽屉状态，旧的一起退场（`ChatDrawer.kt:180-184`） | — | 始终 |
| 新建会话时的关闭 | 竖屏先 `drawerState.close()` 再换会话（两段动画分开）；横屏直接换（`ChatPage.kt:228-240`） | — | 见左 |
| 进横屏自动收 | `LaunchedEffect(threePaneLayout)`：变成三栏且抽屉开着就 `close()`（转回竖屏不会还挂着） | — | 三栏成立 |

### 1.3 分区顺序（从顶到底，竖屏模态形态）

1. `UpdateCard` —— 发现新版本时的更新卡（条件：`BuildConfig.UPDATE_CHECK_ENABLED && updateChecksEnabled && !BuildConfig.IS_PLAY_STORE`）
2. `BackupReminderCard` —— 备份提醒卡（条件：开启提醒且距上次备份超过 `intervalDays`，且本次未被关掉）
3. `DrawerActions` —— 可整体折叠的"快捷入口"区块（6 个入口行）
4. 标题行 —— "会话历史" + 排序按钮
5. `ConversationSectionList` —— 文件夹 → 置顶 / 周分组的会话列表，`weight(1f)` 吃掉剩余高度
6. `MemoryDreamingEntry` —— 记忆整理进度行（条件：当前助手开启记忆且后台正在整理）
7. `AssistantPicker` —— 助手切换行
8. `DrawerBottomBar` —— 新聊天胶囊 + 用户头像（设置入口）

窗口高 < 640dp 时，第 1–4 项从"固定顶部"改为"列表第 0 项"，仍然在会话条目之上，但会随列表滚走。

---

## 2. 会话列表区块

### 2.1 标题行与排序

| 元素 | 位置/尺寸 | 文案/图标 | 显示条件 |
|---|---|---|---|
| 区块标题 | `Text`，`typography.labelMedium`，色 `onSurfaceVariant`，`weight(1f)`，`padding(start = 8.dp)`，所在 `Row` 为 `padding(horizontal = 4.dp)` | **会话历史**（`chat_page_conversation_history`） | 始终（矮屏时随列表滚动） |
| 排序按钮 | `Surface(CircleShape)`，底色 `glassColor(surfaceContainerLow)`，图标 `padding(8.dp).size(16.dp)`，带 `Tooltip` | 图标 `HugeIcons.Sorting01`；tooltip/`contentDescription` = **排序方式**（`chat_page_sort_conversations`） | 始终 |
| 排序菜单 | `DropdownMenu`，两项，当前项 trailing `HugeIcons.Tick01` 18.dp | 第 1 项 **按更新时间**（`chat_page_sort_by_update_time`）；第 2 项 **按创建时间**（`chat_page_sort_by_create_time`） | 点排序按钮 |
| 换排序后的行为 | 重建 `LazyListState` 回到顶部并清空保存的滚动下标（`ChatDrawer.kt:170-178`、`ChatDrawerVM.resetScrollPositions`） | — | 选项真的变了才触发 |

### 2.2 分组规则（不是单纯的"按时间"）

数据在 `ChatDrawerVM.buildVerticalItems`（`ChatDrawerVM.kt:130-176`）里先归堆再依次插入三类头：

1. **文件夹段**：会话按 `folderId` 分组。`null` 那堆是"未归类"，**排在最前**，且仅当它非空时才出现；其后按 `folders` 的顺序逐个出段，**空文件夹也保留标题**（拖拽排序时看得见落点）。
   - 文件夹顺序：按组内最新一条会话时间倒序，没有会话的沉到最后，彼此保持建立顺序（`ChatDrawerVM.kt:79-95`）。
   - 未归类段的标题文案 = **聊天**（`chat_page_folder_default`，注意原文就是"聊天"，不是"未归类"）。
2. **置顶段**：段内把 `isPinned` 的会话集中到一个"置顶聊天"头下，只出一次。
3. **周分组**：非置顶会话按 `sortDate` 的**周一**（`TemporalAdjusters.previousOrSame(MONDAY)`）分桶，桶内顺序沿用仓库层。
   - `sortDate` 由 `conversationSortOrder` 决定：`UPDATE_TIME → updateAt`，`CREATE_TIME → createAt`。
   - 周标签（`ChatDrawerVM.getWeekLabel`）：本周 → **本周**；上一周 → **上周**；其余 → **`%1$s – %2$s`**（起止日期，年份与本周不同年时带年）。

段的收起状态存 SharedPreferences（`drawer_collapsed_dates` / `drawer_collapsed_folders`），冷启动、重进、换主题都保持用户自己收的样子。

### 2.3 会话条目显示什么（这里和常见预期不同，按代码如实写）

`ConversationItem`（`ConversationList.kt:183-431`）：

| 元素 | 位置/尺寸 | 文案/图标 | 显示条件 |
|---|---|---|---|
| 条目容器 | `Box`，`clip(RoundedCornerShape(50f))`（**50 像素**，不是 50%），`combinedClickable` | — | 始终 |
| 选中态底色 | `glassColor(MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp))`，未选中 `Color.Transparent` | — | `conversation.id == currentConversationId` |
| 条目内边距 | `Row(fillMaxWidth, padding(horizontal = 12.dp, vertical = 6.dp))` | — | 始终 |
| 标题 | `Text`，`maxLines = 1`，`TextOverflow.Ellipsis`，无 `style` 覆盖（走默认 bodyLarge） | 会话标题；标题为空时显示 **新消息**（`chat_page_new_message`） | 始终 |
| 标题与图标的弹簧 | 标题后 `Spacer(weight(1f))`，右侧图标依次排 | — | 始终 |
| 置顶标记 | `Icon` **12.dp**，tint `primary`，带 `MotionExpandCollapse` 进出动画 | `HugeIcons.Pin`；`contentDescription = "Pinned"`（硬编码英文） | `conversation.isPinned` |
| 生成中标记 | `Box` **4.dp** 圆形（`clip(CircleShape)`），底色 `extendColors.green6` | 无文字，语义 `contentDescription = "Loading"` | 该会话在 `conversationJobs` 里 |
| 多选勾选圈 | `Box` **14.dp** 圆形，`padding(end = 6.dp)`；选中底色 `primary` + `HugeIcons.Tick01` **10.dp**（tint `onPrimary`）；未选中为 `outlineVariant` 实心圆 | — | `selectionMode == true` |

**条目里没有的东西（照实说明，别在原型里补）**：没有时间戳、没有最后一条消息预览、没有未读数、没有助手名/模型名、没有头像。整行只有"标题 + 可选置顶针 + 可选 4dp 绿点"。`Conversation` 的预览字段在这一层完全没用上。

### 2.4 点击与长按菜单

| 操作 | 触发 | 效果 |
|---|---|---|
| 打开会话 | 单击（非多选模式） | `navigateToChatPage(navController, conversation.id)` |
| 勾选/取消勾选 | 单击（多选模式） | 切换 `selectedIds` |
| 打开菜单 | 长按（非多选模式），先 `focusManager.clearFocus(force = true)` | 弹 `DropdownMenu` |
| 进入多选 | 长按（多选模式） | 切换勾选 |

长按菜单项（顺序即代码顺序，`ConversationList.kt:243-330`）：

| 序 | 文案（zh 原文） | 图标 | 动作 |
|---|---|---|---|
| 1 | **多选**（`chat_page_select_multiple`） | `HugeIcons.Tick01` | 进多选模式并选中本条 |
| 2 | **置顶聊天** / **取消置顶聊天**（`pin_chat` / `unpin_chat`，按当前状态二选一） | `HugeIcons.Pin` / `HugeIcons.PinOff` | 切换置顶 |
| 3 | **重命名**（`chat_page_rename`） | `HugeIcons.PencilEdit01` | 打开"编辑标题"对话框 |
| 4 | **重新生成标题**（`chat_page_regenerate_title`） | `HugeIcons.Refresh01` | `vm.generateTitle(it, true)`，无二次确认 |
| 5 | **移至助手**（`chat_page_move_to_assistant`） | `HugeIcons.Forward02`（`mirrorForRtl()`） | 打开"移至助手"Sheet |
| 6 | **移动到文件夹**（`chat_page_move_to_folder`） | `HugeIcons.Folder01` | 打开"移动到文件夹"Sheet |
| 7 | **删除**（`chat_page_delete`） | `HugeIcons.Delete01` | 直接删除，**无确认对话框**；删的是当前会话时跳回新会话页 |

重命名对话框（`ConversationList.kt:394-430`）：标题 **编辑标题**（`chat_page_edit_title`），`OutlinedTextField` 单行，按钮 **保存**（`chat_page_save`）/ **取消**（`chat_page_cancel`）。

### 2.5 分段标题与多选工具条

| 元素 | 位置/尺寸 | 文案/图标 | 显示条件 |
|---|---|---|---|
| 文件夹头 | `Row`，`padding(horizontal = 4.dp)`，`clip(RoundedCornerShape(12.dp))`，内层 `padding(horizontal = 8.dp, vertical = 8.dp)`，`spacedBy(6.dp)` | `HugeIcons.Folder01` 16.dp（`primary`）+ 文件夹名（`labelLarge` Bold `primary`，maxLines 1 省略）+ 会话条数（`labelMedium` `onSurfaceVariant`，仅 `count > 0`）+ 折叠箭头 16.dp（`ArrowDown01` 收起 / `ArrowUp01` 展开，`primary`） | 每个文件夹段一次；未归类段也有一条 |
| 文件夹头交互 | 单击=折叠/展开该段；**长按**=菜单（仅 `folderId != null`，未归类没有） | 菜单：**重命名**（`chat_page_rename`，`PencilEdit01`）/ **绑定工作区**（`chat_page_folder_bind_workspace`，`Codesandbox`）/ **删除**（`chat_page_delete`，`Delete01`） | 见左 |
| 周标题 | `Row`，`padding(horizontal = 4.dp)`，`clip(12.dp)`，`clickable`，内层 `padding(horizontal = 8.dp, vertical = 8.dp)`；**不铺底色** | 标签（`labelLarge` Bold `primary`）+ 右侧折叠箭头 16.dp | 每个周分组一次 |
| 置顶头 | `Row`，`fillMaxWidth`，`background(glassColor(surfaceContainerLow))`，`padding(horizontal = 12.dp, vertical = 8.dp)` | `HugeIcons.Pin` 16.dp（`primary`）+ `Spacer(8.dp)` + **置顶聊天**（`pinned_chats`，`labelLarge` Bold `primary`） | 段内有置顶会话 |
| 空态 | `Surface(fillMaxWidth, padding(16.dp), RoundedCornerShape(8.dp), surfaceContainerLow)`，内 `Text(padding(16.dp), bodyLarge)` | **没有对话记录**（`chat_page_no_conversations`） | `items.isEmpty()` |
| 多选工具条 | `Surface(fillMaxWidth, padding(horizontal = 8.dp, vertical = 4.dp), RoundedCornerShape(12.dp), glassColor(surfaceContainerHigh))`；内 `Row(padding(horizontal = 12.dp, vertical = 4.dp))`；三个图标各 18.dp、`padding(6.dp)`、圆角点击，中间 `Spacer(4.dp)` | 文字 **已选 %1$d 项**（`chat_page_selected_count`，`labelMedium`，`weight(1f)`）+ `Pin`（描述 **置顶聊天**）+ `Delete01`（描述 **删除**）+ `Cancel01`（描述 **取消**） | `selectionMode`，出现于列表上方（`MotionExpandCollapse`） |
| 多选批量置顶语义 | — | 选中的里只要有一条未置顶就全部置顶，否则全部取消置顶（`ChatDrawer.kt:243-248`） | 点工具条 Pin |
| 折叠联动 | 文件夹收起时整段（周标题/置顶头/条目）一起收，只剩文件夹标题；周收起时只收条目。收起用 220ms 高度动画（`SECTION_TOGGLE_DURATION_MS`），条目仍在列表里、高度为 0 | — | — |
| 折叠时清选择 | 被折叠隐藏的会话会从 `selectedIds` 里移除 | — | 折叠动作发生时 |
| 打开抽屉定位 | 首次组合时若当前会话不在可见范围，`scrollToItem` 到它；但当前会话所在段被用户折叠了就保持折叠、不滚 | — | 每次打开 |

---

## 3. 抽屉里的其他入口

### 3.1 顶部两张卡（顺序在最前）

| 元素 | 位置/尺寸 | 文案/图标 | 显示条件 |
|---|---|---|---|
| 更新卡 | `Card`，内 `Column(padding(8.dp), spacedBy(8.dp))`；标题与关闭按钮同行 `SpaceBetween` | 标题 **发现新版本 %s**（`update_card_new_version_found`，`titleMedium` `primary`）；关闭图标 `HugeIcons.Cancel01`，描述 **关闭**（`update_card_close`）；更新日志 `MarkdownBlock`，`heightIn(max = 200.dp)`，`bodySmall` | `BuildConfig.UPDATE_CHECK_ENABLED && updateChecksEnabled && !BuildConfig.IS_PLAY_STORE`；已关闭则本条不再显示；远端版本 > 当前版本才出卡 |
| 更新卡错误态 | 同容器 | **检查更新失败**（`update_card_check_failed`，`titleMedium` `error`）+ 错误消息或 **未知错误**（`update_card_unknown_error`，`bodyMedium` `error`） | 更新检查出错 |
| 备份提醒卡 | `Card`，`Row(fillMaxWidth, padding(horizontal = 12.dp, vertical = 8.dp), spacedBy(8.dp))`；关闭按钮 `IconButton` **32.dp**，图标 **16.dp** | 图标 `HugeIcons.DatabaseRestore` **20.dp**（`primary`）；标题 **备份提醒**（`backup_page_reminder_title`，`titleSmall` `primary`）；副行 **从未备份**（`backup_page_reminder_never_backed_up`）或 **上次备份于 %1$d 天前**（`backup_page_reminder_last_days`），`bodySmall`；关闭图标 `Cancel01`，描述 **忽略**（`backup_page_reminder_dismiss`） | `config.enabled` 且距上次备份超过 `intervalDays`，且本次未点忽略；点卡片进 `Screen.Backup` |

### 3.2 快捷入口区块（`DrawerActions`，`ChatDrawer.kt:686-763`）

| 元素 | 位置/尺寸 | 文案/图标 | 显示条件 |
|---|---|---|---|
| 折叠开关头 | `Surface(fillMaxWidth, padding(horizontal = 4.dp), shapes.medium, Color.Transparent)`；内 `Row(padding(horizontal = 8.dp, vertical = 6.dp))` | 文字 **快捷入口**（`chat_page_shortcuts`，`labelMedium` `onSurfaceVariant`，`weight(1f)`）+ 右侧箭头 **16.dp**（收起态 `HugeIcons.ArrowDown01`，展开态 `ArrowUp01`，`onSurfaceVariant`） | 始终 |
| 折叠状态持久化 | `rememberSharedPreferenceBoolean("drawer_actions_collapsed", false)`，写进 SharedPreferences | — | 始终 |
| 单行入口容器（`DrawerEntry`） | `Surface(fillMaxWidth, padding(horizontal = 4.dp), shape = shapes.medium, color = glassColor(surfaceContainerLow))`；内 `Row(weight(1f), padding(horizontal = 8.dp, vertical = 10.dp), spacedBy(12.dp))`；图标 **18.dp** `onSurface`；文字 `bodyMedium` `onSurface` | — | 区块展开时 |

六行入口（行序即代码顺序）：

| 序 | 文案（zh 原文 / key） | 左侧图标（代码里） | Material 近似（原型用，推测） | 点击去向 |
|---|---|---|---|---|
| 1 | **聊天历史**（`chat_page_history`） | `HugeIcons.TransactionHistory` | `Icons.Outlined.History` | `Screen.History` |
| 1 行尾 | 无文字，`contentDescription` = **搜索聊天**（`chat_page_search_chats`） | `HugeIcons.Search01`，热区 `Box(size(32.dp), padding(end = 4.dp), clip(CircleShape))`，图标 18.dp `onSurfaceVariant` | `Icons.Outlined.Search` | `Screen.MessageSearch`（点行尾图标，不是点整行） |
| 2 | **媒体创作**（`media_creation_title`） | `HugeIcons.ImageToVideo` | `Icons.Outlined.Movie` | `Screen.MediaCreationSessions` |
| 3 | **AI翻译**（`chat_page_menu_ai_translator`） | `HugeIcons.LanguageCircle` | `Icons.Outlined.Translate` | `Screen.Translator` |
| 4 | **助手设置**（`assistant_page_title`） | `HugeIcons.LookTop` | `Icons.Outlined.SmartToy` | `Screen.Assistant` |
| 5 | **收藏**（`favorite_page_title`） | `HugeIcons.InLove` | `Icons.Outlined.FavoriteBorder` | `Screen.Favorite` |
| 6 | **统计**（`chat_page_stats`） | `HugeIcons.ChartColumn` | `Icons.Outlined.BarChart` | `Screen.Stats` |

注意：这六行**都没有**行尾箭头、都没有数量角标；图标一律 18dp，行高由 `vertical = 10.dp` 撑，不是 48dp 列表项。

### 3.3 抽屉里没有的入口

- **设置**：不在入口区，只由底部用户头像触发（见第 4 节）。
- **关于**：抽屉里没有入口。`Screen.SettingAbout` 在 `Screen.kt:126` 存在，但 `ChatDrawer.kt` 里没有任何指向它的导航。
- **搜索**：只有"聊天历史"行尾那一个入口，指向全局消息搜索 `Screen.MessageSearch`，不是抽屉内的搜索框。
- **模型切换器**：抽屉里没有，模型选择在聊天页顶栏/右侧栏（`ModelListSheet` 由 `ChatPage` 组合）。

---

## 4. 底部区域

底部从下往上依次是：`DrawerBottomBar`（新聊天 + 头像）、`AssistantPicker`（助手切换行）、条件出现的 `MemoryDreamingEntry`。

| 元素 | 位置/尺寸 | 文案/图标 | 显示条件 |
|---|---|---|---|
| 底栏容器 | `Row(fillMaxWidth, padding(horizontal = 8.dp, vertical = 6.dp), spacedBy(10.dp))` | — | 始终（抽屉最底） |
| 新聊天胶囊 | `Surface(weight(1f), height(52.dp), shape = CircleShape)`，底色 `glassColor(primaryContainer)`，内容色 `onPrimaryContainer`；整体 `graphicsLayer` 缩放 **0.8f**，`transformOrigin = TransformOrigin(0f, 1f)`（锚点左下角，只缩绘制不改布局）；内 `Row(fillMaxSize, padding(horizontal = 18.dp), spacedBy(10.dp))` | 图标 `HugeIcons.PencilEdit01` **20.dp** + 文字 **新聊天**（`chat_page_new_chat`，`titleSmall`，maxLines 1 省略）；Material 近似 `Icons.Outlined.Edit` + `Icons.Outlined.Add`（推测） | 始终 |
| 用户头像 | `UIAvatar(avatarSize = 52.dp, videoPlayback = true)`，整体缩放 **0.8f**，`transformOrigin = TransformOrigin(1f, 1f)`（右下角锚点） | 显示 `displaySetting.userAvatar`；名字取 `userNickname`，为空时用 **用户**（`user_default_name`）作占位/头像文字 | 始终 |
| 头像点击 | `onOpenSettings` → `openSettingsFromDrawer` = `navController.navigate(Screen.Setting)`，**不先收抽屉**（设置页整屏盖上来时抽屉跟着退场，避免两段动画） | — | 始终 |
| 版本号 | **没有**。`DrawerBottomBar` 只有胶囊与头像，无版本字符串 | — | — |
| 记忆整理行 | `Surface(fillMaxWidth, padding(horizontal = 4.dp), shapes.medium, glassColor(surfaceContainerLow))`；内 `Row(padding(horizontal = 8.dp, vertical = 10.dp), spacedBy(12.dp))` | `ContainedLoadingIndicator` **18.dp** + 文字：有轮次进度时 **已整理 %1$d / %2$d 个对话**（`memory_dream_progress`），否则 **做梦中…**（`memory_dreaming`），`bodyMedium` | 当前助手 `enableMemory` 为真且记忆库 `status.running`；点进 `Screen.AssistantMemory(assistantId)` |

### 4.1 助手切换器（`AssistantPicker`，`ui/components/ai/AssistantPicker.kt:56-108`）

| 元素 | 位置/尺寸 | 文案/图标 | 显示条件 |
|---|---|---|---|
| 整行 | Material3 `NavigationDrawerItem`（默认高度 56dp，本处未覆写尺寸），`modifier = fillMaxWidth()`，`selected = false`（不显示选中态） | 左侧图标 `HugeIcons.LookTop`（默认 24dp，未覆写）；文字为当前助手名，为空时 **默认助手**（`assistant_page_default_assistant`），`maxLines = 1` 省略 | 始终 |
| 行内头像 | 标签行尾 `Spacer(weight(1f))` + `UIAvatar`（`avatarSize` 默认 **32.dp**） | 当前助手头像；点击 → `Screen.AssistantDetail(id = 当前助手)` | 始终 |
| 点整行 | 弹出助手选择 Sheet（`AppModalSheet` = `ui/components/SheetMotion.kt` 对 AndroidX Material3 `ModalBottomSheet` 的封装，`containerColor = LocalSeparateWindowColors.current?.sheet ?: BottomSheetDefaults.ContainerColor`）：`Column(fillMaxWidth, fillMaxHeight(0.8f), padding(16.dp), spacedBy(8.dp))` | — | 始终 |
| Sheet 标题 | `Text(titleLarge, padding(bottom = 8.dp))` | **助手设置**（`assistant_page_title`） | Sheet 打开 |
| Sheet 标签过滤 | `LazyRow` + `FilterChip(shape = RoundedCornerShape(50))`，`padding(bottom = 8.dp)`，`spacedBy(8.dp)` | 每个 `assistantTags` 一个 chip，文案为标签名 | `settings.assistantTags.isNotEmpty()` |
| Sheet 助手列表 | `LazyColumn(weight(1f), spacedBy(8.dp))`，每项 `Card(shape = shapes.large)`；选中项 `primaryContainer`/`onPrimaryContainer`，其余 `surface`/`onSurface` | `ListItem`：头像是 `UIAvatar(32.dp)`；标题为助手名或 **默认助手**；行尾 `IconButton` 图标 `HugeIcons.Edit03` → 先关 Sheet 再进 `Screen.AssistantDetail(id)` | Sheet 打开 |
| 选中助手后的跳转 | `vm.updateSettings { assistantId = id }`，随后：若偏好 `create_new_conversation_on_start`（默认 true）则**新建随机 Uuid 会话**，否则取该助手第一个会话（没会话也新建）→ `navigateToChatPage` | — | 选择某个助手 |

---

## 5. 历史页、收藏页与统计页

三页都不是抽屉内容，是从抽屉入口点进去的独立整屏页；这里只给复刻原型需要的那部分。

### 5.1 历史页（`ui/pages/history/HistoryPage.kt`）

| 元素 | 位置/尺寸 | 文案/图标 | 显示条件 |
|---|---|---|---|
| 顶栏 | `ui/style` 里的 `TopAppBar`（M3 标准高度 64dp，`styledComponents` 的默认 `expandedHeight = 64.dp`） | — | 始终 |
| 返回 | `BackButton()` = `FilledTonalIconButton` + `HugeIcons.ArrowLeft01` | 描述 **返回**（`back`） | 始终 |
| 顶栏标题 | `Text` 单行 | **聊天历史**（`history_page_title`） | 非多选 |
| 顶栏标题（多选） | 同位置 | **已选择 %1$d 项**（`history_page_selected_count`） | `selectedIds != null` |
| 搜索 | `IconButton`，图标 `HugeIcons.GlobalSearch` | 描述 **搜索消息**（`history_page_search_messages`）→ `Screen.MessageSearch` | 非多选 |
| 进多选 | `IconButton`，图标 `HugeIcons.CheckList`，`PlainTooltip` | 提示 **选择**（`history_page_select`） | 非多选且列表非空 |
| 删除全部 | `IconButton`，图标 `HugeIcons.Delete01` | 描述 **删除全部**（`history_page_delete_all`） | 非多选 |
| 列表 | 单 `LazyColumn`，`contentPadding = 16.dp`（四边），`spacedBy(8.dp)`，**无分组头、无分隔线、无 sticky** | — | — |
| 排序 | 由 DAO 决定：`is_pinned DESC, update_at DESC`（`data/db/dao/ConversationDAO.kt:22`） | — | — |
| 条目容器 | `Surface`，`RoundedCornerShape(25)`（**25dp 圆角**），`tonalElevation = 2.dp`，底色 `surface`；选中态 `secondaryContainer` | — | 始终 |
| 条目内部 | M3 `ListItem`（`containerColor = Color.Transparent`，`contentPadding` 用默认） | — | 始终 |
| 主行 | `Row(spacedBy(4.dp))`：置顶针 `HugeIcons.Pin` **16.dp** `primary`（仅 `isPinned`）+ 标题 `titleMedium`，`maxLines = 2`，省略号，`.trim()`，空则 **新对话**（`history_page_new_conversation`） | — | 始终 |
| 次行 | supporting `Text`，默认 `bodyMedium` / `onSurfaceVariant` | `createAt.toLocalDateTime()`，即**创建时间**（不是更新时间），本地化 `MEDIUM` 日期时间（`utils/TimeUtil.kt:23`） | 始终 |
| 行尾（普通态） | `IconButton`，图标 `HugeIcons.PinOff` / `HugeIcons.Pin` | 描述 **取消置顶**（`history_page_unpin`）/ **置顶**（`history_page_pin`） | 非多选 |
| 行尾（多选态） | `Checkbox(checked, onCheckedChange = null)`，只读、由整行处理 | — | 多选 |
| 点击 | 打开会话 `navigateToChatPage(id)`（不带 `nodeId`）；多选态下切换勾选 | — | — |
| 长按 | 普通态 = 进多选并选中本条；多选态 = 切换勾选 | — | — |
| 条目里没有 | 消息预览、助手名、模型名、消息数、更多菜单、滑动操作，**也没有长按菜单**（长按只用来进多选） | — | — |
| 空态 | **代码里没有空态分支**：列表为空时无 item，页面只剩顶栏（推测视觉上就是一片空白） | — | `conversations` 为空 |
| 多选底栏 | `Surface(fillMaxWidth, navigationBarsPadding(), padding(左右 8dp / 底 8dp), 色 surfaceContainerLow, 形状 shapes.largeIncreased, 描边 BorderStroke(1.dp, outlineVariant @50%))`；内 `Row(padding(8.dp), spacedBy(4.dp))` | 左 `IconButton` + `HugeIcons.Cancel01`，tooltip **退出选择**（`history_page_exit_selection`）；右 `Button`（`error`/`onError`）图标 `Delete01` + **删除**（`delete`），`enabled = 选中数 > 0` | `selectedIds != null` |
| 删除选中确认 | 确认对话框 | 标题 **确认删除**（`confirm_delete`）；正文 **选中的 %1$d 个对话将被删除。此操作无法撤销。**（`history_page_delete_selected_text`）；按钮 **删除** / **取消** | 点删除 |
| 删除全部确认 | `AnimatedAlertDialog` | 标题 **删除全部对话**（`history_page_delete_all_conversations`）；正文 **确定要删除所有对话吗？此操作无法撤销。**（`history_page_delete_all_confirmation`）；按钮 **删除** / **取消** | 点删除全部 |

### 5.2 收藏页（`ui/pages/favorite/FavoritePage.kt`）

| 元素 | 位置/尺寸 | 文案/图标 | 显示条件 |
|---|---|---|---|
| 顶栏 | M3 `LargeFlexibleTopAppBar` + `exitUntilCollapsedScrollBehavior()`，底色 `CustomColors.topBarColors`；`Scaffold.containerColor` 同色 | — | 始终 |
| 返回 | `BackButton()` + `HugeIcons.ArrowLeft01` | 描述 **返回** | 始终 |
| 标题 | `Text` 单行 | **收藏**（`favorite_page_title`） | 始终 |
| 顶栏动作 | **一个都没有**（无搜索、无多选、无清空） | — | — |
| 列表 | 单 `LazyColumn`，`innerPadding + PaddingValues(8.dp)`，`spacedBy(8.dp)`；每项再 `padding(horizontal = 8.dp)` | — | — |
| 排序 | DAO：`created_at DESC`（按**收藏时间**倒序） | — | — |
| 条目类型 | 只有"单条消息收藏"（`FavoriteType.NODE`），没有"收藏整个会话"的条目 | — | — |
| 条目容器 | `Card`（可点击），`fillMaxWidth`，`CardDefaults.shape`，配色 `CustomColors.cardColorsOnSurfaceContainer`，无描边 | — | 始终 |
| 条目内容 | `SelectionContainer` 包住 → `Column(padding(16.dp), spacedBy(4.dp))` | — | 始终 |
| 行 1 标题 | `titleMedium`，`maxLines = 1`，省略号；空则 **未命名对话**（`favorite_page_untitled_conversation`） | — | 始终 |
| 行 2 预览 | `item.preview`，`bodySmall`，**`maxLines = 4`**，省略号；内容是收藏那条消息的纯文本（只拼 `UIMessagePart.Text`，`\n` 连接、trim、截断 **160** 字符，`data/model/Favorite.kt:44-60`）；无文本时显示 `[User Message]` / `[Assistant Message]` / `[System Message]` / `[Tool Message]` —— 这四个是**英文硬编码** | — | 始终 |
| 行 3 时间 | `bodySmall`，色 `outline`，`maxLines = 1` | 收藏时间 `createdAt`，本地化 `MEDIUM` 格式（不是消息时间，也不是会话更新时间） | 始终 |
| 点击 | `navigateToChatPage(conversationId, nodeId = item.nodeId)` —— 跳进会话并定位到被收藏的那条消息 | — | — |
| 长按 | 无（未注册 `onLongClick`） | — | — |
| 取消收藏手势 | `SwipeToDismissBox`，**只允许向左滑**（`EndToStart`），`enableDismissFromStartToEnd = false`，滑过即删、**无二次确认**（`ui/components/ui/SwipeToDelete.kt:44-52`） | — | 始终 |
| 滑动背景 | 铺满，底色 `errorContainer`，形状同条目，左右 `padding(20.dp)`，内容右对齐居中：`HugeIcons.Delete01`（`onErrorContainer`），描述 **删除** | — | 滑动中 |
| 删除反馈 | `Snackbar`：消息 **收藏已移除**（`favorite_page_removed`），动作 **撤销**（`history_page_undo`），`withDismissAction = true`（带关闭按钮）；点撤销把原记录写回 | — | 删除后 |
| 空态 | `Box(fillMaxSize)` 居中 `Text`，色 `onSurface @65%`，无图标无按钮 | **暂无收藏**（`favorite_page_no_favorites`） | `favorites.isEmpty()`；此时不渲染列表 |

**历史页 vs 收藏页的条目差异（复刻时别混用一套）**

| 维度 | 历史页 | 收藏页 |
|---|---|---|
| 组件形态 | M3 `ListItem`（标题 + supporting 两行） | 自绘 `Card` + `Column` 三行 |
| 圆角 | `RoundedCornerShape(25)` | `CardDefaults.shape` |
| 行数 | 2 行（标题最多 2 行 + 时间） | 3 行（标题 1 行 + 预览最多 4 行 + 时间） |
| 时间含义 | **创建时间** | **收藏时间** |
| 右侧尾随 | 置顶切换按钮；多选态换 Checkbox | 无尾随，**支持性元数据全无**（无置顶、无复选框） |
| 文本可选中 | 否 | 是（`SelectionContainer` 整卡可长按复制） |
| 删除方式 | 多选 + 底栏删除（有确认弹窗） | 左滑删除 + Snackbar 撤销（无确认） |
| 点进会话 | 打开会话，不定位 | 打开会话并定位到该消息节点 |

### 5.3 统计页（`ui/pages/stats/StatsPage.kt`）

| 元素 | 位置/尺寸 | 文案/图标 | 显示条件 |
|---|---|---|---|
| 顶栏 | M3 `LargeFlexibleTopAppBar` + `exitUntilCollapsedScrollBehavior`，色 `CustomColors.topBarColors` | — | 始终 |
| 返回 | `BackButton()` + `ArrowLeft01` | 描述 **返回** | 始终 |
| 标题 | 单行 | **统计**（`stats_page_title`） | 始终 |
| 导出 | `IconButton` + `HugeIcons.Download04` | 描述 **导出**（`stats_page_export`）；导出中换成 `CircularProgressIndicator(20.dp, strokeWidth 2.dp)` | 非加载、非导出中时可用 |
| 加载态 | 全屏居中 `CircularProgressIndicator`，无文字 | — | `stats.isLoading` |
| 正文容器 | `Column` + `verticalScroll`，`padding(8.dp)`，`spacedBy(8.dp)`；卡片再各加左右 8dp（距屏边 16dp） | — | 始终 |

卡片顺序（自上而下，全部 8dp 间距、全宽、`CustomColors.cardColorsOnSurfaceContainer`）：

| 序 | 卡片/区块 | 图表类型与关键尺寸 | 文案（zh 原文） | 显示条件 |
|---|---|---|---|---|
| 1 | 聊天热力图 | **日历热力图**：53 列 × 7 行；单元格 `11.dp × 11.dp`，间隔 `2.dp`，圆角 `shapes.extraSmall`；水平可滚动，初始滚到最右；分档按非零天数四分位映射 `primary` 透明度 0.25/0.5/0.75/1.0，0 → `surfaceVariant`，未来日子 → `surfaceVariant @30%`；Y 轴只标"一/三/五"（列宽 12dp），X 轴标月份短名（1 月带年份）；图例右对齐：`少` + 5 个 10dp 方块 + `多`；卡片 `padding(16.dp)`、`spacedBy(12.dp)` | 标题 **聊天热力图**（`stats_page_heatmap_title`）；图例 **少** / **多**（`stats_page_heatmap_less/more`）；轴 **一/三/五**（`stats_page_dow_mon/wed/fri`） | 始终（不受时间范围影响，固定 53 周窗口） |
| 2 | 总对话数 | 半宽 `StatCard`（`weight(1f)`），卡片 `padding(16.dp)`、`spacedBy(8.dp)`，图标 20dp → 数值 `headlineSmall` → 标签 `bodySmall` | 图标 `HugeIcons.ChartColumn`；标签 **总对话数**（`stats_page_total_conversations`）；数值为紧凑量级格式（<1000 原样，否则 K/M/B/T 或 Ki/Mi/Gi/Ti） | 始终 |
| 3 | 总消息数 | 半宽，同规格 | 图标 `HugeIcons.Message01`；**总消息数**（`stats_page_total_messages`） | 始终 |
| 4 | 应用启动次数 | 整行 | 图标 `HugeIcons.Rocket01`；**应用启动次数**（`stats_page_launch_count`） | 始终 |
| 5 | 「Token 用量」筛选区 | 非卡片，仅标题 + 段控；`ConnectedChoiceButtons`，`minHeight = 48.dp`，未选中底色 `glassColor(surfaceContainerHigh)`，选中项 flex 1.3 | 标题 **Token 用量**（`stats_page_usage_title`）；选项 **7 天** / **30 天** / **90 天** / **全部**（`stats_page_range_7d/30d/90d/all`），默认 **30 天** | 始终；这是页面**唯一**筛选器 |
| 6 | 无用量空态 | 单卡，`Text` 内边距 `24.dp` | **此期间无 Token 使用量**（`stats_page_no_usage`） | `promptTokens + completionTokens == 0`，此时第 7–11 张卡都不渲染 |
| 7 | 估算费用 | 整行 | 图标 `HugeIcons.MoneyBag02`；**估算费用**（`stats_page_total_cost`）；数值按量级保留 2/4/6/8 位小数去尾零、**无货币符号**；无价目时显示 **—**，hint 换成 **本期用到的模型尚未设置价目，在模型编辑页填写单价后显示金额。**（`stats_page_cost_unset`）；有价目 hint 为 **费用按入账时的价目记录，未设置价目的请求不计入。**（`stats_page_cost_hint`） | 有用量 |
| 8 | 输入 Token | 半宽 | 图标 `HugeIcons.Upload02`；**输入 Token**（`stats_page_input_tokens`） | 有用量 |
| 9 | 输出 Token | 半宽 | 图标 `HugeIcons.Download04`；**输出 Token**（`stats_page_output_tokens`） | 有用量 |
| 10 | 缓存节省 Token | 半宽 | 图标 `HugeIcons.Zap`；**缓存节省 Token**（`stats_page_cached_tokens`） | 有用量 |
| 11 | 缓存命中率 | 半宽，数值 `"%.1f%%"`，`promptTokens == 0` 时显示 `-` | 图标 `HugeIcons.PercentCircle`；**缓存命中率**（`stats_page_cache_hit_rate`） | 有用量 |
| 12 | Token 分布 | **环形图（donut）**：`Canvas` `132.dp × 132.dp`，`drawArc` + `Stroke(width = 18.dp)`，起始角 -90°，每段两侧各留 1.5°，最多 8 段（前 7 + **其他**）；8 色循环 `primary → tertiary → secondary → error → primaryContainer → tertiaryContainer → secondaryContainer → outline`；环心上行总 Token、下行英文 `Token`；右侧图例：8dp 圆点 + 模型名（1 行省略）+ 百分比 `"%.0f%%"`，行距 6dp；无交互 | 标题 **Token 分布**（`stats_page_token_distribution`）；兜底 **其他**（`stats_page_other`） | 有用量 |
| 13 | 每日用量 / 每月用量 | **竖向柱状图**：`Canvas` 高 `180.dp`、`fillMaxWidth`；3 条水平参考线（0 / 1/2 / 上限），轴上界取 `niceCeil`；柱宽 `min(24.dp, 槽宽 - 间隙)`，间隙 `min(2.dp, 槽宽*0.3)`，顶角圆 4dp、底方；未选中全 `primary`，选中柱 `primary`、其余 `primary @35%`；X 轴最多 3 个标签（首/中/尾）；点柱选中、再点取消。上方读数区：标签 + 主数值 `headlineSmall` + FlowRow「输入 / 缓存 / 输出 /（费用）」 | 标题按 `byMonth` 切换为 **每日用量**（`stats_page_usage_daily`）或 **每月用量**（`stats_page_usage_monthly`）；未选中时标签 **本期总计**（`stats_page_usage_period_total`），选中时为该点日期（`M/d` 或 `yyyy-MM`）；小项 **输入 / 缓存 / 输出**（`chat_message_usage_input/cached/output`）、**费用**（`stats_page_cost`）；`byMonth = (范围 == 全部)` | 有用量 |
| 14 | 按模型 | **单色横向条形排行**（非图表库）：每行 = 标签行 + `8.dp` 高条，长度按占第一名比例缩放，右端圆角 4dp；行右文本 `"{tokens} · {share}%"`，`cost > 0` 时追加 `" · {费用}"`；最多 8 项（前 7 + 其他），名为空显示 **未知模型** | 标题 **按模型**（`stats_page_by_model`）；兜底 **其他**（`stats_page_other`）/ **未知模型**（`stats_page_unknown_model`） | 有用量 |
| 15 | 按来源 | 与「按模型」结构完全一致，数据换成按来源汇总 | 标题 **按来源**（`stats_page_by_source`）；来源标签 **聊天 / 压缩 / 标题 / 建议 / 翻译 / 记忆 / OCR**（`stats_page_source_chat/compression/title/suggestion/translation/memory/ocr`） | 有用量 |

导出弹窗（点顶栏下载图标）：`AnimatedAlertDialog`，标题 **导出统计**（`stats_page_export_title`），两个整宽按钮 **长图**（PNG → `Pictures/L3Hub`）/ **PDF**（A4 分页 → `Downloads/L3Hub`），取消 **取消**（`stats_page_export_cancel`）；结果 Toast **已保存至 %1$s**（`stats_page_export_done`）/ **导出失败：%1$s**（`stats_page_export_failed`）。

---

## 6. 横屏 / 宽屏表现

判据：`threePaneLayout = 窗口宽 > 窗口高 && 窗口宽 >= 600.dp`（`ChatPage.kt:209-211`）。

| 元素 | 位置/尺寸 | 说明 | 显示条件 |
|---|---|---|---|
| 布局形态 | 竖屏：`ModalNavigationDrawer`（抽屉滑出，有 scrim）。横屏三栏：`Row` 里并排放左栏 + 中栏聊天 + 右栏，抽屉不再是浮层 | 一份 `ChatDrawerContent` 两用 | 见判据 |
| 左栏开合 | `sidebarOpen` 默认 **true**（横屏默认展开，`rememberSaveable`）；宽度 `animateDpAsState` 在 `sidebarWidthDp.dp ↔ 0.dp` 之间动，收起后 `sidebarWidth == 0.dp` 时不组合内容（`ChatPage.kt:344-357`） | 顶栏菜单按钮此时执行 `sidebarOpen = !sidebarOpen` | 三栏 |
| 左栏宽度 | 默认 **300.dp**，拖手柄范围 **200f..440f**，宽度随进程重进保持（`rememberSaveable`） | 传给 `ChatDrawerContent(widthOverride = sidebarWidthDp.dp)`，覆盖 `min(360dp, 屏宽)` 那套算法 | 三栏 |
| 拖动反馈 | 拖动中 `sidebarWidth` 用 `snap()`，不拖时用 `rememberSlideSpec`（受 `disableAnimations` 影响） | — | 拖手柄时 |
| 整体缩放 | `CompositionLocalProvider(LocalDensity provides Density(density * LANDSCAPE_UI_SCALE, fontScale))`，`LANDSCAPE_UI_SCALE = 0.75f` —— 三列并排后字号、行高、控件一起缩到 75%，`dp` 值本身不变 | 所以原型里横屏左栏视觉宽 ≈ 300 × 0.75 = 225 CSS px（按同一套 dp 换算） | 三栏 |
| 中栏宽度 | `chatWidth = chatWidthDp`（拖过）或 `totalWidth * 0.4f`，`coerceIn(320.dp, maxChatWidth)`；`maxChatWidth = (totalWidth - 左栏宽 - 260.dp).coerceAtLeast(320.dp)`，`totalWidth = 屏宽 / 0.75` | — | 三栏 |
| 圆角 | 左栏 `RectangleShape`（无圆角），因为横屏它是一列常驻边而不是浮卡 | — | 始终 |
| 矮窗口（含横屏手机） | `windowHeight < 640.dp` 时顶部区块塞进列表随滚动，横屏高度通常远小于 640dp，实际表现就是"顶部区块跟着会话列表一起滚" | — | 见左 |
| 转屏清理 | 进入三栏时若模态抽屉还开着，`drawerState.close()`，避免转回竖屏后它仍开着 | — | 转进横屏 |
