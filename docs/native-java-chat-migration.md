# Java/XML 聊天界面渐进迁移（第一批）

## 设计与回滚基线

- 设计来源：用户的 Neko Chat Demo，目录 `~/WorkSpace/DbWctbWrcDAv7Mww`，参考提交 `0d4c75d`。
- 迁移前 L3Hub 基线：`b9f996fa6d6dc2c452cd590e6499ea232a878a5b`。
- 工作分支：`feat/native-java-chat`。
- Demo 的 MIT 许可完整保留于 [`licenses/neko-chat-demo-MIT.txt`](licenses/neko-chat-demo-MIT.txt)，并以同样内容放入 `app/src/main/assets/licenses/neko-chat-demo-MIT.txt`，随 APK 保留。
- 不搬 Demo 的 Activity、包名、签名、假回复、假录音、假模型、固定用量或固定任务数据。

这一批采用 **Java/XML View + Kotlin 薄桥 + 既有 Compose 富内容**，不是重新用 Compose 仿写整个 Demo，也不是把 Kotlin 业务层一次性翻成 Java。

## 已接入范围

| 区域 | 实现 | 数据与操作来源 |
| --- | --- | --- |
| 竖屏 56dp 顶栏、48dp 固定四标签 | `NativeChatHeader.java`、`NativeChatTabs.java`、XML | 会话标题、运行态、真实文件夹 ID；侧栏、新建、模型、详情、预览、导出、删除、设置继续调用原导航/VM |
| 竖屏多行输入卡 | `NativeChatComposer.java`、XML | 同一份 `ChatInputState.textContent`；发送、停止、长按不回答、插话、ASR、附件仍走原处理链 |
| 分支地图 | `NativeConversationMap.java` + Kotlin 投影/宿主 | `Conversation.messageNodes` 与每个节点的候选消息；点选回原 VM 切分支并定位聊天 |
| 任务标签 | `ChatPortraitTabs.kt`（Compose） | 真实 todo、引导、待答 ask_user、绑定工作区、子代理树和后台任务入口 |
| 用量标签 | `ChatPortraitTabs.kt`（Compose） | 复用 `ConversationUsageDetails` 的真实用量及全局统计入口 |

四标签只响应点击，不使用 ViewPager、不接受横向滑页。输入卡常驻 bottomBar，切标签不清草稿。竖屏空会话按 Demo 输入贴底；横屏三栏沿用既有 Compose 顶栏、输入与布局。

### 保留的既有能力

- Room → Repository → ChatService → ChatVM 的状态/持久化链保持不变。
- `ChatSendActions` 与发送门限保持不变：真正受理后才清草稿，不绕过上下文限制/压缩确认。
- 历史消息编辑仍由 `ChatInputState` 合并原始 parts；签名思考、原附件的保护不变。
- workspace 内联补全与 Compose 文字框共享采集、排序和应用逻辑；快捷消息、引导菜单共用原实现。
- 全屏正文/思考编辑、模型/推理面板、媒体附件、语音模式、消息队列、工具批准、复杂 Markdown/公式/代码渲染继续复用 Compose。
- IME 贴图、图片粘贴/拖拽、长文本转附件先经 `FilesManager` 复制到受管目录，不直接持久化临时 `content://` URI。

## 主题、生命周期与性能边界

- Material Views 1.14.0 + AppCompat 1.7.1 仅供 View 控件，使用独立 `ContextThemeWrapper`，不替换 Activity 主题。
- 颜色由当前 Compose `ColorScheme` 下发；Demo 青绿/陶土色仅作 XML fallback，不改写用户主题偏好。
- 原生资源统一 `l3_native_` 前缀；简中与默认英文分表。
- 顶栏由 Compose 应用顶部及水平 safeDrawing insets；输入沿用原系统导航栏/IME inset 管理，不重复消费。
- View 在 `AndroidView.onRelease` 清理回调；临时 detach 只关闭窗口相关状态，避免重挂后失联。
- 地图按真实主链和候选投影，不捏造父子关系、时间、数量、费用或进度。动画只在前台、可见、已挂载、运行中且允许动画时进行；离开地图标签停止。
- 非当前标签解除组合，避免隐藏地图/内容继续刷新；`SaveableStateHolder` 保存可保存的标签状态，聊天列表滚动与草稿位于外层。只用 `remember` 的消息卡片局部展开态不保证跨标签保留。
- 根 ChatPage 仍只订阅派生字段；完整逐 token 正文只在窄内容组件读取。

## 本批不包含

- 消息列表、Markdown/思考工具卡、侧栏、设置页的完整 Java 化。
- Demo 自带的模拟业务、工作区 Git/磁盘等虚构状态。
- 全套 Demo 配色预设或强制替换用户高级外观设置。
- 新导航栈、额外 Activity、权限扩张、安装、覆盖安装或发布。
- 版本号变更：本批保持 `399 / L.3.3.1.4`，没有生成或部署 APK。

## 验证与待验收

手机 Termux **不执行 Gradle/javac/原生编译**。本地只做文本级检查（XML/TOML 解析、资源引用和重复键、diff whitespace、源码审阅）；它们不能代表 Android 资源链接或编译通过。

单元测试交给既有 GitHub Actions `CI`，其中 `:app:testGithubDebugUnitTest` 覆盖混合源码编译、资源链接与新增状态/地图投影测试。最终构建结果以对应提交的 Actions 运行记录为准。

新增状态边界测试覆盖反向选区、选区越界夹取、附件/思考不丢、共享补全应用/关闭；地图投影测试覆盖真实主链与候选。它们不替代设备测试。

真机验收仍需另行安排，不能把本次静态检查或 CI 当成已验收：

1. 亮屏且解锁后，核对顶部/底部 inset、刘海、窄屏与大字体、深色及自定义主题。
2. 默认 SINGLE 设置下竖屏仍显示原生多行卡；横屏三栏不变。
3. 中文输入法 composing、双向选区、补全、IME 表情/图片、拖拽与长文本粘贴。
4. 发送/停止/长按/插话、队列暂停、上下文拒绝/压缩确认后草稿留存。
5. 正文和思考编辑、原附件保护、签名思考不可编辑。
6. 标签来回切换草稿/滚动不丢；地图选择真实候选后准确定位；后台或切标签不持续地图刷新。
7. 抽屉与弹层优先处理返回键；配置/主题切换及临时 detach 后控件仍可操作。

验收结束必须关闭所有测试弹层；没有安装授权前不部署 APK。
