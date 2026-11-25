# 从 Rikkahub Revised 移植高级外观与聊天渲染（工作笔记）

源工程：`~/WorkSpace/Rikkahub-Revised`（AGPL-3.0）。
提交 `6f33a51c25484f8ca77a9fbec560fa49a84c9b21`，树 `a9afd37f10c206adbc1d9f148cbeb266ad944199`，
提交时间 2026-11-22 15:33:43 +0800，远端 `https://github.com/YaeNovin/Rikkahub-Revised`。

目标工程：本仓库。工作树版本 `L.3.3.1.3` / `versionCode 398`，
`namespace = me.rerere.rikkahub`、`applicationId = com.rikkahub.l3hub.wfadnf`。

## 0 署名与许可

| 项 | 值 |
|---|---|
| 上游基线 | RikkaHub 2.4.8，提交 `8824e0e841f2008b322ca8214a27a978e4b4abaa` |
| 源分发名 | Rikkahub Revised，`applicationId = me.rerere.rikkahub.revised` |
| 源 `NOTICE` 修改声明日期 | 2026-08-18 |
| 源 `docs/MODIFICATIONS.md` 声明日期 | 2026-11-20；高级外观条目记在 `2.4.8-revised.6` 维护变更里 |
| 许可 | 两工程均为 AGPL-3.0，见本仓库 `LICENSE` 与 `README.md` 的许可说明 |
| 代码署名 | `AdvancedAppearanceSetting.kt` 和 `LiquidGlassSettings.kt` 均注明来源为 Rikkahub Revised（AGPL-3.0） |

源工程那份声明按“修改分发”写：它要求保留上游署名、标明修改日期、不冒充上游官方版本。
本仓库作为二次开发版，同样只用 AGPL-3.0 的既有条款，不新增或替换许可文本。

## 1 接入位置

下表 App 内 Kotlin 路径相对于 `app/src/main/java/me/rerere/rikkahub/`。

| 层 | 文件 | 作用 |
|---|---|---|
| 设置 | `data/datastore/{Settings,PreferencesStore,SettingsFields,SettingsNormalization,AdvancedAppearanceSetting}.kt`、`data/datastore/migration/{PreferenceStoreV6Migration,SettingsJsonMigrator}.kt` | `advanced_appearance_setting` 独立持久化和归一化；旧 `DisplaySetting` 缺失的新字段使用默认值；V6 把旧全局特效开关搬进高级外观，偏好与备份 JSON 共用同一个纯函数 |
| 预设 | `data/model/{LiquidGlassSettings,InspirationAppearance}.kt` | 玻璃渲染器与预设、灵感卡片样式 |
| 路由与设置页 | `Screen.kt`、`ui/navigation/ScreenPages.kt`、`RouteActivity.kt`、`ui/pages/setting/` | 「偏好设置 → 高级外观设置」；各列独立背景；`WebView` 增加离线标志 |
| 主题与材质 | `ui/theme/{Theme,AdvancedAppearanceTheme,AppearanceBackground}.kt`、`ui/components/ui/{AdvancedMaterial,GlassSurface,CardGroup}.kt` | 配色、背景取样、各面材质和降级 |
| 聊天 | `ui/pages/chat/`、`ui/components/{ai,message}/` | 背景、顶栏与胶囊、悬浮栏、输入区、气泡、建议条、灵感卡片与附件 |
| 富文本 | `ui/components/richtext/`、`ui/components/message/MarkdownParagraphs.kt`、`:ui` 的 `ChartCard` | BetterText 上叠加源码保护、公式回退、ANSI、diff 和原生图表子集 |
| 预览 | `ui/pages/webview/`、`ui/components/ui/{ImagePreviewDialog,VideoPlayerDialog}.kt`、`:ui` 的 `WebViewContentCache` | 隔离的离线全文预览、图片预览与本地视频播放 |
| 独立窗口 | `:ui` 的 `SeparateWindowAppearance`、`ConfirmDialog`、`DialogMotion`、`SheetMotion`、`Select` 等 | 对话框、菜单和 Sheet 的静态色及降级 |
| 双语资源 | `app/src/main/res/{values,values-zh}/{advanced_appearance,chat_media_strings}.xml` | 英文与简体中文 |
| 回归源码 | `app/src/test/`、`ui/src/test/` | 设置兼容、三栏投影、旧路由、材质参数、富文本及缓存，验证状态见第 16 节 |

## 2 默认关闭；旧「全局特效」已并入高级外观（V6）

`enableAdvancedAppearance = false` 时**一条 haze 都不挂**：页卡、列表项、气泡、输入区、顶栏、胶囊、
配额条、抽屉和浮层一律落到实色或主题底色，受管全局背景也不绘制。

原来的「常规 → 全局特效」（关闭 / 模糊 / 玻璃三档）和它背后的旧运行时已经整体删除：
`chatInputHaze`、`effectSurface`、`glassSurface`、`canGlassPane`、`glassOverlayAlpha`、
`effectiveBackgroundEffect` 这些入口都不在了。现在打开高级外观是让面材质重新采样背景的唯一入口，
关闭它不会再退回「旧模糊路径」，而是直接回到实色。

`DisplaySetting` 里的 `enableBlurEffect`、`backgroundEffectType`、`blurOpacity`、`glassOpacity`
仍然保留，只服务三件事：降级版本读盘、备份导出与导入、V6 迁移的输入。界面不再读写这四个字段。

**Markdown 渲染、媒体附件和离线消息预览不受总开关限制**，关闭不代表回到移植前的渲染行为或逐像素外观。
开启会立即应用高级设置的默认值，不用再逐项打开。

旧偏好缺失高级外观键时用默认设置；独立的外观 JSON 解码器把未知枚举值回落到该字段默认值，
外观 JSON 畸形时只重置外观这一字段。`textColorMode` 默认 `THEME`（Revised 为 `AUTO_CLEAR`）；
全屏背景开关默认 `true`，但仍须满足第 4 节的生效条件。

### 2.1 V6：旧开关 → 高级外观

`PreferenceStoreV6Migration`（DataStore，`data_version < 6`）与 `SettingsJsonMigrator` 的 V6 钩子
（备份恢复）调用同一个纯函数 `migrateLegacyDisplayEffects()`，一次迁移落两份文档：
`display_setting` 补输入区与顶栏的继承值并写标记，`advanced_appearance_setting` 补各面材质。

| 旧状态 | 结果 |
|---|---|
| `enableBlurEffect = true` 且类型 `blur` | 高级外观**原本关着**才开启；页面 / 输入区 / 浮层 / 导航 / 聊天坞 / 气泡 / 富内容卡片置磨砂，模糊半径 **12dp**，不透明度取 `blurOpacity`（默认 .45） |
| `enableBlurEffect = true` 且类型 `glass` | 同上，材质置液态玻璃，半径 **16dp**，不透明度取 `glassOpacity`（默认 .35），不指定玻璃预设 |
| `enableBlurEffect = false`、类型 `off`、认不出来的类型值 | 一个字不动 |
| 高级外观本来就开着 | 高级那份整份保留，旧开关不覆盖它；只在 `display_setting` 记「已经搬过」 |
| 透明度不是 0..1 的合法数 | 用旧档自己的默认值（.45 / .35），避免迁完没有底色 |
| `display_setting` 或 `advanced_appearance_setting` 读不出对象 | 两份都原样保留；不把坏数据当成缺失去补一份看起来正常的新文档 |

- 迁移**只开材质**：`showTopBarBackground`、`showAssistantBubble` 这类「这个部件显不显示」的开关不碰。
  导航玻璃与 Dock 玻璃原本就在旧全局效果的覆盖范围内，所以这两项要顺手启用。
- 半径按档位分字段写：磨砂写 `pageSurfaceBlurRadius` / `overlaySurfaceBlurRadius` /
  `navigationGlassBlurRadius` 这一族，液态玻璃写 `*LiquidGlassBlurRadius` 这一族。
  壁纸图片自己的预模糊（`globalBackgroundBlurRadius`）和页面卡片的模糊半径是两份独立字段。
- 已开高级外观的老文档如果只有 `globalBackgroundBlurRadius`、还没有 `pageSurfaceBlurRadius`，
  有一条一次性回填把前者抄过去（只写这一个键），让既有磨砂页面与壁纸强度不变。
- `display_setting.legacyEffectsMigrated` 是「已搬过一次」的标记：用户之后自己关掉高级外观、
  再从备份恢复，旧开关不会把特效重新打开。
- 旧字段一律不删：降级回来的版本照样读它们。

主题依原顺序取基准：API 31+ 且已开启的系统动态色、调色板、预设或自定义主题。高级配色可用图片强调色
替换种子（需启用自动强调色且采样有效），或根据基准强调色应用风格和对比度。其后保持深色 AMOLED 表面处理，
并按文字模式调整 `onBackground`、`onSurface`、`onSurfaceVariant`；依赖图片或壁纸的模式没有有效种子时保留主题文字色。

## 3 受管背景图：导入、监听与取色

偏好只存 `filesDir/upload/` 下的相对路径，不保存 URL。加载前会检查规范路径，只接收 `upload/` 的直属文件，
拒绝越界或目录重定向。导入用系统图片选择器，接收 `content://`，流式限读至 16 MiB；检查图片 MIME 和两轴尺寸
（各不超过 `16_384`），并验证小图解码成功后，才由 `FilesManager.saveManagedFromBytes(FileFolders.UPLOAD, …)` 保存。

`Theme.kt` 在全局背景有配置时解析、取样一次，通过 `LocalAppearanceImage` 在各列共享状态；各列独立绘制，
不共享 Haze 源。`AppearanceBackground.kt` 同时监听 `filesDir` 下 `upload` 条目的变化，以及 `upload` 内目标文件
和目录自身的移除、移动。首次挂载（即使目录不存在）后会解析一次，后续目录重建或整体替换会请求重挂并重新解析；
监听随状态退出而释放。Coil 内存键包含路径、修改时间、长度和 `Os.stat` 得到的文件身份（`st_dev xor st_ino`，
取不到时为 0），用于避免同名、同大小、同时间戳但换了文件时沿用旧位图。这不是内容哈希，恢复时序仍待真机验证。

取色用小图离线采样，忽略 alpha < 128 的像素，强调色按色相投票，另算平均色。
图像缺失、越界或解码失败时走无图路径；单纯缺少有效色样并不意味着图片不可绘制。

## 4 背景的生效范围

`isGlobalBackgroundActive()` 只看设定：高级外观开启、全局背景开启、路径非空；不检查文件或不透明度。
`isGlobalBackgroundVisible()` 还要求不透明度 `> 0`。以下场景真正绘图时还须有可用图片：

| 场景 | 在全局背景可见的基础上还需 |
|---|---|
| 普通页 | 页卡材质不是 `OPAQUE`（实色），无额外的普通页开关 |
| 聊天 | `applyGlobalBackgroundToChat` 开启；不受页卡 `OPAQUE` 限制 |
| 全屏图片和第 14 节的 WebView 预览 | 页卡非实色，且 `applyGlobalBackgroundToFullscreenPreview` 开启；实色时该开关禁用并提示原因 |
| 设置左栏 | `enableNavigationGlass` 开启且导航材质非实色；不沿用页卡或聊天开关 |

不透明度 0% 只停绘，图片仍可用于自动强调色或“应用背景”文字取色。没有可绘图片时，普通页 `MICA` 可用主题色渐变，
其余普通页回到无图底色；全屏预览不走 `MICA` 主题渐变。聊天缺图、解码失败、0% 或未选“在聊天中使用”时，
`AssistantBackground()` 依次尝试助手网格渐变、助手视频/裁剪图/原图，最后在高级外观模式使用页面底色。
可绘的全局图片优先于这些，不删除助手配置；设置页在适用时列出已配置助手背景的数量，0% 时则提示回退。
本地全屏视频播放器是黑底，不在全屏图片背景的适用范围内。

## 5 Android 版本与玻璃参数

最低 API 26。`resolvedLiveAppearanceRadius()` 同时用于出图侧和设置页滑杆范围；表里是能力上限，
实际还受各控件范围、材质、模糊源与第 8 节性能条件约束。

| API | 模糊通用上限 | 液态玻璃折射 | 高级网格渐变 |
|---|---|---|---|
| 26–30 | 0dp | 无 | Kotlin |
| 31–32 | 12dp | 无，非零半径仍可普通模糊 | Kotlin |
| 33+ | 40dp | 有源、实时档且折射值大于 0 时启用 | `AUTO` / `AGSL` 尝试 shader；`KOTLIN` 直接绘制 |

页卡、浮层、导航与悬浮栏各自的液态玻璃半径存储上限为 24dp，普通导航模糊为 32dp；
这些值不会因为 33+ 支持到 40dp 而放大。液态玻璃渲染器选 `STANDARD` 时用静态材质，
不同于网格渐变的渲染器选项。设置页按 API、材质和相关开关隐藏或禁用不适用控件。

折射滑杆范围 `0..16`，与参数归一化共用上限。`withAppearanceRefraction()` 把 Haze `refractionStrength`
映为 `amount / 16`、`depth` 映为 `min(amount / 6, 1)`，都在 `0..1`。深度在 6 饱和，强度仍可调；
6 以上（含 `CLEAR` 预设的 9）不传越界深度，0 不走折射分支。

### 5.1 液态玻璃的两种光学实现

`LiquidGlassSettings.style` 取 `SALT`（默认）或 `MATERIAL3`。旧数据没有这个字段，落到 `SALT`，
观感与加入该字段前一致。这一档只替换 `LIQUID_GLASS` 材质，磨砂 / 亚克力 / 云母不受影响。

| 项 | `SALT` | `MATERIAL3`（RikkaHub 原版 Haze Material3） |
|---|---|---|
| 参数来源 | `LiquidGlassParameters`：预设、边缘高光、雾化、折射、内阴影、描边宽度 | 固定 `depth = .5`、模糊半径 **16dp**、tint alpha **.3**；不读 Salt 的 preset / highlight / refraction / edgeWidth / innerShadow，设置页把这些滑杆隐藏 |
| 响应参数 | `SaltGlassStyles.regularSurface(dark)` | 按**应用主题**显式写入 response（深色数值等同库内 `regularDark`），不跟系统夜间模式走，避免应用内深色被挑成浅色预设 |
| 折射 | API 33+、有源、实时刻且折射值 > 0 | 不参与折射分支 |
| 不可实时时 | 渐变描边，强度按实时刻与 SDK 分档 | 1.5dp 中性描边（固定高光 .18 / 阴影 .12） |

能力上限仍按上表：API 33+ 可折射，31–32 最多 12dp 普通模糊，26–30 一条模糊都不挂；
静态、无源或系统不支持时只留着色与边缘。**纯色背景糊一遍还是同一个颜色，所以不承诺可见虚化。**

网格 shader 与 Kotlin 绘制共享四份 `MeshLight` 坐标、半径和颜色；shader 初始化或绘制失败、软件 Canvas 时
回到 Kotlin。壁纸文字取色在 API 26 只用可用的已存种子；API 27+ 可取壁纸色，API 31+ 还可尝试系统强调色。

## 6 Decompose 分栏隔离

主栈和右列的 `NavChildContent()` 各自经 `RouteAppearance`。普通页有图片或 `MICA` 底时，
`RouteBackdrop` 创建本页 Haze 源，限制在所在容器内；转场过程中也不采别栏。`LocalRouteBackdropVisible`
让已接入的 `Scaffold` 用透明容器，露出本页底层。设置左栏用 `SettingRailAppearance`，按第 4 节独立判断；
聊天路由跳过普通页背景，自己处理。

## 7 聊天的背景源与组件开关

`ChatPage.kt` 准备两份 Haze 源：`backgroundHazeState`（页面背景 / 助手背景）与
`chromeHazeState`（背景 + 消息正文）。高级材质消费的是后者，气泡拿的是前者。
高级外观关闭时 `LocalGlassHazeState` 给 `null`，没有消费者，任何面都不采样。

| 设置 | 生效前提与范围 |
|---|---|
| 整条顶栏和顶栏胶囊 | `showTopBarBackground` 只控制整条顶栏的高级底层；胶囊独立，取页卡材质和顶栏着色浓度。整条顶栏自己采样时胶囊保持**静态**着色——同一片区域对同一个源采两遍会叠深一层 |
| 输入区 | 可显式选材质；未选且玻璃未应用到输入区时走页卡材质。旧输入区 haze 路径已随旧运行时删除，关着高级外观就只有实色底 |
| 聊天坞玻璃 | 只装饰原有悬浮栏，要求已启用悬浮栏且快捷入口非空；有自己的材质档（`chatDockSurfaceStyle`，默认液态玻璃）、不透明度与半径，不跟随输入区滑杆 |
| 气泡与富内容框 | 分别控制外观；代码块和表格并不因此更换解析器。消息样式为「默认」时气泡用所选高级气泡材质，显式「实色」时不采样背景；定制的思考链跟富内容材质走 |
| 助手正文气泡 | 默认**不显示**气泡底（`showAssistantBubble = false`），要的话到 界面偏好 打开 |
| 正文排版、建议条、灵感卡片 | 正文设行高和段间距；建议条可改面、边框和最大高度（72–220dp）；灵感样式仅作用于相应的空会话内容 |

### 7.1 采样源：只给该采样的一层

| 消费方 | 取哪份源 |
|---|---|
| 会话抽屉底板 | 只用背景源；它背后是页面背景，不采会话列表 |
| 气泡、富内容卡片、思考链 | 只用背景源 |
| 整条顶栏、顶栏胶囊、配额条与看板、会话详情浮层、同窗工具详情侧栏 | `chrome` 源，要糊出压着的正文 |
| 空态概览、生成中、上下文压缩 | 不录正文 |
| 分窗的 Dialog / 菜单 / Sheet | 不跨窗采样，只给静态着色 |
| 浮在正文上的小控件（消息跳转、建议回复、下拉新建胶囊） | 只静态着色，不各自挂源 |

## 8 性能条件：什么时候退回静态

| 条件 | 降级范围 |
|---|---|
| 相应的效果开关关闭，或系统省电 | 高级面材质不做实时模糊/折射，可保留静态着色、渐变或描边 |
| 应用关动效，或按设置遵循系统且系统禁用动画 | 只停动画：网格渐变不漂移，**模糊与折射照旧**。模糊是渲染效果不是动效，`LocalAnimationsEnabled` 已不参与材质降级判断 |
| 聊天滚动、输入法可见或生成中 | 自适应性能开启时，高级面材质按忙态降级；不按滚动偏移逐帧取值 |
| 内容仍在增长 | 流式气泡、公式和表格，以及未闭合的代码围栏禁用该处的实时材质，不依赖自适应开关 |
| 独立窗口或无 Haze 源 | 不跨窗口或列借源；按场景走静态底色或合成底色，只有着色与边缘 |

网格渐变还有自己的动效开关；生成中先暂停漂移。`AppearanceBackdrop` 对选中图片本身的模糊仍按
第 5 节的 API 半径计算，**不受上述面材质省电降级直接控制**。页内浮层（如工具详情）可使用自己的效果开关；
独立窗口始终按下节处理，不会因为启用浮层性能效果而开始模糊父窗口。

## 9 独立窗口：只给静态材质

`Theme.kt` 基于当前主题色与浮层样式计算 `LocalSeparateWindowColors`：对话框、Sheet、普通菜单和分组菜单
分别取基色，再把着色合到不透明的页面底色；液态玻璃样式另给菜单静态描边。这里不读取父窗口像素；换图只有在
图片实际影响主题强调色等配色时才可能改窗口颜色。总开关关闭时不提供这些颜色，组件保留默认样式。

`SeparateWindowContent` 标记独立窗口，`SheetWindowTheme` 把静态色交给 Salt 实际的面板容器。
共享对话框、两类 Select 下拉、聊天菜单、子代理通知和 Emoji 肤色 Popup、暂停更新提示等消费这些机制。
`advancedMaterial` 在被标记的窗口忽略父 `HazeState`，只拿静态色（旧 `GlassSurface.effectSurface` 已删除）。
全屏图片预览如符合第 4 节，
是独立窗口自己绘制同一张受管图，不是采样父窗口。

## 10 保留的既有依赖，没有换栈

| 依赖 | 版本 | 在这块里的角色 |
|---|---|---|
| Compose BOM / Material3 | `2026.09.00` / `1.5.0-rc01` | 主题、动效、形状与对话框 |
| BetterText | `v0.1.6` | Markdown 解析与排版；ANSI 块也用它的 `BetterText` 以保持同一选择容器 |
| Coil | `3.6.3` | 背景图、生成图、视频封面；`coil-svg` 仍在 |
| Salt UI | `3.0.0` | `SaltTheme` / `SaltDynamicColors` / Sheet；独立窗口用它换面板色 |
| Haze | `2.1.0`（`haze` / `haze-blur` / `haze-glass` + material3 变体） | 模糊与玻璃；Salt 样式走 `SaltGlassStyles`，原版玻璃走 `glass.material3` 变体 |
| Decompose | `3.6.2` | 分栏与 child 身份（第 6 节） |
| Media3 | `1.11.1` | 复用本地全屏播放器 |

没有新增第三方依赖、修改构建任务或调整最低 API、应用版本。

## 11 富文本：只取子集，其余保留原路

源工程的渲染器是一整套离线资源包（`assets/html/renderers/` 下 40+ 个文件、`assets/html/maps/`）。
本仓库不搬这些，只挑能纯 Kotlin 落地的四类，加上原有渲染路径。

### 11.1 围栏语言归一化

`CodeFenceLanguage.kt` 的 `normalizeCodeFenceLanguage()`：`trim` → 小写 → 去 `language-` /
`{`…`}` / 前导 `.` → 在第一个空格、逗号、分号处截断 → 别名映射
（`application/json` → `json`、`yml` → `yaml`、`kt` → `kotlin`、`sh/shell/zsh/bash` → `shell` 等）。
只影响“选哪个渲染器”，原始语言标签与源码照旧给复制、源码视图、导出使用。

### 11.2 展示预处理与 LaTeX 回退

`Markdown.kt` 在整段或流式解析前调用 `preprocessMarkdownForDisplay()`。用户消息的高置信度源码段（如 `#include`
与赋值声明）围成代码块，正文里的不支持标签（如 `List<String>` 的尖括号）按文本保护。预处理只改展示副本，
不改消息存储、发送、整条复制或导出；消息超过 100,000 或有一行超过 4,096 个 UTF-16 码元时跳过应用层预处理。

公式扫描只识别固定分隔符、常见数学环境和少量整行裸命令。它跳过基本围栏（包括直接在引用或列表标记后打开的围栏）、
缩进代码、完整行内代码及识别出的 `$` / `$$` 公式，保护流式末段未闭合的代码。新识别的跨行公式通常折成一行，
保留 TeX 的 `\\` 行分隔；有未转义 `%` 时不合行。未配对的 `\(` / `\[` 在展示副本转为括号实体，
防止 BetterText 再次规范分隔符时跨区误配。这是有限的启发式，不是完整 CommonMark 或 TeX 解析器。

块状与行内公式均接入 BetterText 的布局结果检查：先试原式，再试 Unicode 符号、宏和环境的兼容候选，
例如 `\dfrac` → `\frac`。返回布局错误或无法使用时，最后显示可读近似式（如分数 `(a)/(b)`）或源码；
超过 4,096 个码元不尝试额外布局，显示源码。成功的行内公式仍用 BetterText 原生折行。
关闭 `enableLatexRendering` 会跳过应用新增的公式规范化、不注册公式插槽；BetterText 已识别的公式按源码显示。

### 11.3 ANSI / SGR

`AnsiSgr.kt` 只做 SGR（`ESC[…m`）的颜色与字形：16 色基础表、256 色、truecolor、
粗体/暗淡/斜体/下划线/反显/删除线。其它控制序列一律丢弃——OSC（标题、超链接、剪贴板）、
DCS（含图片）、设备查询、光标与模式设置都不参与显示；未终止的字符串直接丢到结尾，
不会把内容当正文露出来。上限 `MAX_ANSI_SOURCE_CHARS = 128 * 1024`、`MAX_ANSI_SEGMENTS = 4000`。

围栏识别：`ansi` / `terminal` / `console` / `console-output` / `xterm` 直接认；
`""` / `text` / `plaintext` / `plain` / `txt` / `log` / `shell` / `bash` 这类泛型围栏
只在内容里真出现 `ESC[` 时才转换。`AnsiCodeBlock.kt` 用 `BetterText` 渲染，不经过 WebView：
没有输入、没有 PTY、没有超链接、没有图片协议，也就没有需要拦截的桥。

### 11.4 diff

`CodeFenceLanguage.kt` + `DiffCodeBlock.kt`：

- 显式别名：`diff` / `patch` / `udiff` / `unified-diff` / `git-diff` / `gitdiff`。
- 泛型围栏（`""` / `text` / `plaintext` / `plain` / `txt` / `log`）只在
  `looksLikeStandaloneUnifiedDiff()` 成立时转换：首个非空行必须是 `diff --git ` / `--- ` /
  `@@ ` / `@@-`，且整体满足 `looksLikeUnifiedDiff()`（有增删行，且有 hunk 头或一对 `---`/`+++`）。
  这样正文里顺带提到的 `- 列表项` 不会被误判成补丁。
- 上限：`MAX_DIFF_SOURCE_CHARS = 256 * 1024`（超了不转换），
  `MAX_DIFF_RENDER_LINES = 600`（超出折叠），`MAX_DIFF_DETECT_LINES = 200`（嗅探只看前 200 行）。
- 只做展示：不解析成可应用补丁，不写文件。源码仍完整留在 `HighlightCodeBlock` 的复制与下载路径里。
- 整条消息本身就是一段补丁时，`MarkdownParagraphs.kt` 不再切段，
  `Markdown.kt` 直接把整条交给 `HighlightCodeBlock(code, "diff")`。

### 11.5 echarts：只认严格 JSON，画原生图

`RevisedChartBlock.kt` 把 `echarts` / `chart` 围栏映射到现有的 `ChartCard`（`:ui`），
**不执行模型给的任何脚本**：

| 约束 | 值 / 规则 |
|---|---|
| 源码上限 | `32 * 1024`（`MAX_REVISED_CHART_SOURCE_CHARS`） |
| 系列数 | ≤ 12（`MAX_REVISED_CHART_SERIES`，与 `chart_display` 工具一致） |
| 每系列点数 | ≤ 2000（`MAX_REVISED_CHART_VALUES`） |
| 类目标签数 | ≤ 2000（`MAX_REVISED_CHART_CATEGORIES`） |
| 标题/轴名/系列名/标签长度 | ≤ 120 字符 |
| JSON 嵌套深度 | ≤ 64（解析前先挡，避免递归炸栈） |
| 图形类型 | 只 `line` / `bar` / `scatter`，且同一图里必须一致 |
| 轴范围 | `min`/`max` 必须是数字、区间正向；对数轴出现非正数直接拒绝 |
| 样式 | `itemStyle` 只接受 `{color: "#rgb"\|"#rrggbb"}` |

解析用 `kotlinx-serialization` 的 JSON：不接受 JS 对象字面量、`const option = {…}`、注释、尾逗号，
也不执行完整 ECharts 选项。遇到不支持的数据或选项时返回 `Declined` 并落回原始代码块。

围栏识别不做内容嗅探：`isRevisedChartFenceLanguage()` 只认归一化后的
`echarts` / `chart`，泛型 `json` / `javascript` 不算，免得把普通 JSON 抢成图表。

`Markdown.kt` 把 `block.closed` 传给 `HighlightCodeBlock` 的 `completeCodeBlock`。围栏未闭合时显示源码；
闭合后即尝试解析，即使只新增结束标记而正文未变，成功时也默认显示 `RichChartCard`，不必等整条回复结束。
用户仍可切回或复制源码。

### 11.6 保留的现有渲染器

| 能力 | 位置 | 状态 |
|---|---|---|
| Mermaid | `richtext/Mermaid.kt` + `assets/html/mermaid.min.js` | 保留，仍是 WebView 内渲染，`completeCodeBlock` 为真时直接画 |
| 公式块与行内公式 | `MathBlock` / `LatexInline.cached()` | 保留 BetterText 并接第 11.2 节回退；高级外观只控制公式块外框 |
| HTML / SVG 内联预览 | `PREVIEWABLE_LANGUAGES = setOf("html", "svg")` → `CodeBlockPreview` | 保留，`baseUrl = "https://rikkahub.local"` |
| 十六进制色块 | `Markdown.kt` `parseColorCode()` → `ColorSwatch` | 保留，行内与行间两处 |
| 表格 | `TableCard` | 保留，新增 `streaming` 参数用于材质降级 |
| 代码高亮 | Atom One Light/Dark + BetterText `highlight` | 保留 |

## 12 图片与视频卡片

**生成图**（`ChatMessageGeneratedImage.kt`）：用 82% 宽度比例和 320dp 宽高上限约束，按原图比例收框。
空地址或只有 `data:image/…;base64,` 头时显示骨架，不发 Coil 请求。失败可点重试，重试跳过 Coil 内存和磁盘缓存读取。
用户图片附件仍用原缩略图路径。

**视频**只做聊天附件预览，不涉及生成、识别或 Revised 行内播放。用户视频保留矩形附件条、长按手势和第三项 `+N`；
助手视频地址形状为绝对路径或 `file:` 时用 16:9、最大 240dp 卡片，其余用图标块。路径形状只决定助手卡片样式，
不代表文件可访问。图片和视频不被折进「更早的步骤」。

| 来源 | 封面和点按行为 |
|---|---|
| 受管本地文件 | 只对 `filesDir/upload/` 规范化直属、非空、可读文件尝试取帧与应用内播放。API 26 无封面；API 27+ 抽取最长边不超过 480px 的缩放帧 |
| `content://` | 不预取；点按时仅允许有显式 URI 读授权、且非本 App FileProvider 的地址进入应用内播放器，不复制整条视频 |
| `http://` / `https://` | 不取帧、不发应用内媒体请求；点按才打开系统外部视频应用选择器 |
| 其他地址或不满足条件 | 不读该资源，点按提示无法播放 |

封面写临时 JPEG 后改名，按 32 MiB 预算清理 `cacheDir/video-thumbnails`。本地黑底全屏 `VideoPlayerDialog` 只用
文件或 Content 数据源的 `ProgressiveMediaSource`，不按后缀猜播放清单。`ON_STOP` 暂停，退出后解绑并释放；
设备无法解码时可手动选择外部应用。这里不改变原媒体文件的清理或备份策略。

## 13 离线消息预览

用户长按或助手消息的更多菜单中，「消息预览」只取非空文本部件，不加载附件。`OfflinePreview.kt` 将文本转义成
固定 HTML，支持标题、段落、引用、列表、表格、分隔线、围栏、粗斜体和行内代码。Markdown 图片保留可见文字，
不加载图片；识别到的 `http`、`https`、`mailto` 地址只显示为可复制文字，不生成 `href`，其他地址保留 Markdown 文本。

专用 `OfflineMessagePreviewPage` 用 `loadDataWithBaseURL(null, …)`，CSP 禁脚本和外部资源；WebView 禁用 JavaScript、
DOM 存储、文件、Content 和网络访问，并拦截导航与子资源，退出时停载销毁。

| 项 | 值 |
|---|---|
| 正文 / 单块代码 | 200,000 / 20,000 UTF-16 码元，渲染截断避开代理对，超长提示 `aa_preview_truncated` |
| 引用 / 行内嵌套 | 各 8 层 |
| 地址扫描 / 单张表格 | 2,048 码元扫描窗口；64 列、128 行数据 |
| 结构预算 | 8,192，超限的内容回退为转义文本 |
| 离线缓存 | 单项 800,004 字节；最多 16 MiB、128 项；7 天未用过期 |

`WebViewContentCache.storeOfflineMessage()` 只写 `cacheDir/offline_message_preview/<sha256>`。路由带 ID 和离线标志，
不带正文；ID 限 64 位小写十六进制，缓存检查规范路径、临时文件改名、流式限读，成功读取刷新修改时间。
读取不到、过期或超限时显示 `aa_preview_missing`，**不回退到旧 `webview_content`**。

## 14 旧路由与离线路由分开

`Screen.WebView(url = "", contentId = "", offlineMessagePreview = false)` 保持旧默认；只有 `offlineMessagePreview`
为 `true` 才进入隔离页。HTML/SVG 代码块和 Mermaid 全屏入口仍走旧 `WebViewPage`，外链也不走离线页。
无脚本、无网络、无可点地址的承诺只适用于离线消息预览，不覆盖普通 Markdown 图片及旧 HTML/SVG/Mermaid。

`RouteAppearance` 的视觉判据更宽：离线标志为 `true` 或 WebView 的 URL 为空，都使用全屏预览背景条件；
一般带 URL 的 WebView 用普通页条件。这不改变渲染器或缓存的选择。旧 `MarkdownWeb.kt` 的
`buildMarkdownPreviewHtml()` 与它读取的 `assets/html/mark.html` 目前没有现用入口，本次保留不删除。

## 15 没有移植的部分

- 未引入 Revised 的完整渲染资源、模型可发现的 `RendererCatalog`、Leaflet 地图与联网瓦片、Vega、Graphviz、
  乐谱、SMILES、WaveDrom 等。`echarts` / `chart` 仅指第 11 节的原生图表子集。
- 未移植来源工程的独立能力策略、全屏背景脚本、静态预览脚本或视频行内播放器。版本和效果能力用本仓库现有机制与上述分支。

## 16 静态验收与验证限制

- `git diff --check` 与未跟踪文本空白扫描没有报错。扫描了 41 个 Android `values` XML；新增外观 112 条、媒体 6 条，
  英文与简中键和格式占位符一致，无新增撞名；App 的 `R.*` 引用清单未发现未解析项。简中既有 8 个缺项与 `HEAD` 一致。
- `tools/check-file-budget.sh` **退出 1**。`HEAD`（`f54eb70c6`）的 8 个超限文件中，`ChatMessage.kt`、`ChatPage.kt`、
  `ChatInput.kt`、`ChatList.kt` 已降回上限内；其余四项的行数和上限均未变化，未调高预算：

  | 文件 | 工作树 / 上限 |
  |---|---|
  | `ChatService.kt` | 1815 / 1798 |
  | `WorkspaceTools.kt` | 383 / 335 |
  | `TTSProviderConfigure.kt` | 1413 / 1403 |
  | `ImgGenPage.kt` | 883 / 880 |

- 回归源码覆盖设置兼容与范围、分栏投影、旧路由、Haze 参数、富文本及离线缓存。核对了相关调用点和依赖 API，
  未运行单测。源码核查不能代替 Kotlin 编译、设备视觉或生命周期验证。
- 现有 `.github/workflows/ci.yml` 含 `:app:testGithubDebugUnitTest`、`:ui:testDebugUnitTest` 并排除 `:web:buildWebUi`；
  本次未触发 CI。未运行 Gradle、PNPM 或编译，未安装依赖、上机、出 APK、部署或发版。未改依赖、版本或构建配置；
  工作树原有的 Web UI 等非移植改动保持原状。

### 16.1 本轮（全局特效并入高级外观，V6）

- 本轮文档部分只动这四份：`docs/port-rikkahub-revised.md`、`docs/fork-features.md`、`FORK.md`、`.l3spec/settings-theme.md`；
  本节只说文档与静态事实，不代其它改动下验证结论。
- `git diff --check` 无输出、退出 0（`git diff --check -- docs .l3spec FORK.md` 同样退出 0）。
  该命令只看已跟踪文件的空白问题，未跟踪的新文件不在其范围内。
- 未运行 Gradle / PNPM / 编译 / 单测，未触发 CI，未安装依赖、未上机、未出包。
  `app/src/test/.../PreferenceStoreV6MigrationTest.kt` 是新增源码但本轮没有执行它——
  「迁移源码存在」不等于「迁移行为已验证」，本节不报告任何 CI 或真机结论。
- 未改依赖、版本号或构建配置。

## 17 手工回归矩阵（全部待执行）

在获批的测试环境中准备 `githubDebug` 变体，覆盖 API 26、31/32、33+，以及窄屏、横屏三栏、深浅色与中英文。
恢复和缓存用例使用可丢弃的测试数据。下列是预期，不是观察结果。

### 迁移（V6）与消息样式

| 项 | 操作和预期 |
|---|---|
| 升级：模糊档 | 旧设置 `enableBlurEffect` 开、类型 blur、`blurOpacity` 65%；升级后高级外观为开，页面 / 输入区 / 浮层 / 导航 / 聊天坞 / 气泡 / 富内容卡片为磨砂、12dp、65%，顶栏与输入区继承同一浓度 |
| 升级：玻璃档 | 类型 glass、`glassOpacity` 40%；材质为液态玻璃、16dp、40%，玻璃预设未被改写，`showTopBarBackground` 与助手气泡显示开关保持原值 |
| 升级：OFF | 旧开关关、类型 off、或写成认不出来的值；高级外观仍关，界面全程实色，一条 haze 都不挂 |
| 已开不覆盖 | 高级外观已开且自己调过半径与不透明度；升级后这些值不变，只在 `display_setting` 出现标记 |
| 坏数据 | 把两份 JSON 分别换成畸形字符串、`null`、数组；不补新文档，盘上原文保留，应用照常启动 |
| 备份恢复 | 用旧备份（开关开 + blur）恢复后，导出这份已迁移设置；手动关掉高级外观，再恢复这份新版备份，不应重新打开（`legacyEffectsMigrated`）。再次导入那份未带标记的旧备份仍会按旧规则迁移 |
| 降级 | 装回旧版本仍按旧字段与旧浓度运行；再升回新版本，旧开关也不会二次重开 |
| 壁纸与页面 | 壁纸预模糊与页面磨砂半径分别调，改一个不动另一个；旧盘只有 `globalBackgroundBlurRadius` 且非 20dp 时，页面半径被回填一次 |
| 消息样式位置 | 高级外观设置内改三档样式与角色覆盖，返回重进保持；旧路由 `Screen.SettingPreferencesMessageStyle` 仍能直达同一块内容；数据仍在 `display_setting` |
| 实色不采样 | 消息样式选「实色」；气泡不采样背景，透明度与半径滑杆不影响它。选「默认」时走所选高级气泡材质 |
| 助手正文气泡 | 全新安装默认没有助手气泡底；到 界面偏好 打开「显示助手消息气泡」后才出现 |
| 液态玻璃样式 | 在 Salt 与 RikkaHub 原版之间切换，Salt 的预设与光学滑杆随之隐藏/显示；切回 Salt 参数不丢 |

### 外观与导航

| 项 | 操作和预期 |
|---|---|
| 默认与旧盘 | 全新安装、升级无高级外观键的旧设置；总开关默认关，主题与旧路由可用，各面是实色底；富文本、附件、消息预览仍可用。开启后高级默认值生效，再关闭回到实色（已经不存在旧模糊路径） |
| 旧字段异常 | 用测试备份分别输入未知外观枚举、畸形外观 JSON；未知枚举用该字段默认，畸形 JSON 只回退外观，保留无关偏好 |
| 导入 | 导入正常 JPEG 并重启；只存受管相对路径。换大于 16 MiB、任一边大于 16,384 或无效的图，应提示失败，不替换已保存设置 |
| 缺图与恢复 | 移走图或换成不可解码文件；普通页退无图底，MICA 可留主题渐变，聊天退助手背景。还原同名有效图后应恢复 |
| 目录整体替换 | 让应用在 `upload` 缺失时先完成回退，再创建目录；再换整个目录，用同名、同大小、同时间戳但不同内容的图片。应重新挂载、取样并绘制新图，不沿用旧 Coil 内存项 |
| 不透明度 0% | 启用自动强调色、配置可用彩色图并先设为可见；降到 0%，图片不绘制，聊天恢复助手背景，图片强调色仍可用 |
| 范围 | 非实色页卡下分别切换聊天与全屏开关，普通页不随它们开关；全屏开关同时检查图片和消息预览。再换实色页卡，普通页和全屏预览不绘图，启用的聊天背景仍绘图 |
| 分栏 | 左栏选「偏好设置」，中列进入高级外观，右列打开、返回并试转屏；各列只采自身背景。左栏随导航材质；聊天三栏无普通遮罩，开启聊天可读性时可加遮罩 |
| 主题优先级 | 深浅色下比较动态色、调色板与主题基色，切换图片强调色、对比、文字模式及 AMOLED；缺取色种子时保留主题文字色，AMOLED 底槽仍按深色模式压黑 |
| API 26 | 缺少实时模糊和折射控件的支持；材质可退静态，网格走 Kotlin，受管视频无封面但仍可点播 |
| API 31/32 | 用非零模糊半径和有源表面；最大 12dp，折射不可用，网格走 Kotlin |
| API 33+ | 通用上限 40dp，液态玻璃专属半径与导航半径仍有各自上限。折射试 0、6、16 和 `CLEAR`；在有源、无降级条件时检查 0 不折射、非零能调整且不抛错 |
| 网格渲染器 | 分别选 `AUTO`、`AGSL`、`KOTLIN`；33+ 硬件画布尝试 AGSL，软件画布或 shader 失败时仍有 Kotlin 光斑 |
| 聊天材质 | 开、关高级外观分别滚动长消息；开时只糊背景，抽屉只采背景，顶栏与配额条取含正文的 chrome 源。整条顶栏在用同一个源时胶囊保持静态；悬浮栏关闭或无快捷入口时 Dock 设置不自行创建栏 |
| 忙态与省电 | 在 API 33+ 启用自适应，试滚动、键盘及生成；再试省电与关闭动效。省电使相关面退静态、条件恢复后复原；关闭动效只停动画，模糊与折射不退；图片本身的模糊不因此取消 |
| 独立窗口 | 打开确认框、两类 Select、菜单、Sheet 与 Popup；静态颜色不采父窗口，原交互仍可用。全屏图片仅在其背景条件满足时自己绘图 |

### 富文本

| 项 | 操作和预期 |
|---|---|
| 公式与回退 | 试 `\(x\)`、`\[y\]`、常见多行环境、裸分式及已配对但布局失败的公式。观察正常布局、兼容候选与文本回退；超过 4,096 码元退源码。关闭公式开关后不启用新增规范化与布局 |
| 流式与源码保护 | 分段输出公式、行内代码和未闭合的普通/引用/列表围栏，含空行及尖括号；观察围栏内不误转公式、残留 opener 不误配后文。用户粘贴 `#include` 与行内泛型，检查展示与复制/导出原文分别正确 |
| 图表 | 用 `echarts` / `chart` 围栏测试 line/bar/scatter 严格 JSON 与切回源码。流式先传完整 JSON，再单发关闭围栏，预览应从源码切到图而不等整条回复结束；非法选项、混合类型或超限仍显示源码 |
| ANSI 与 diff | 测基础色、256 色、真彩与去除非 SGR 控制序列；测试显式 diff、泛型围栏和整段补丁、普通列表、超限补丁；不执行命令或应用补丁 |
| 旧渲染路径 | 检查表格、色块、普通 Markdown 图片及 Mermaid、HTML/SVG 旧预览，确保未被误路由到隔离消息预览；展开长代码块检查下半部分可交互 |

### 媒体

| 项 | 操作和预期 |
|---|---|
| 生成图 | 测空占位、宽图/长图、失败重试；骨架先出现，图片等比收框，失败后可重试 |
| 受管本地视频 | 同时试用户四个附件和助手视频；用户保留长按及第三项 `+N`，助手本地路径用 16:9、最大 240dp。API 27+ 支持的格式应有封面；点按黑底全屏，切后台暂停，退出后不继续播放 |
| 授权 Content | 用测试提供方显式授权的 `content://`，点按前无取帧或读取，点按后进本地播放器。无 URI 读授权或本 App FileProvider 地址不走此分支 |
| 远程与无效地址 | 对远程 HTTP(S) 视频验证无应用内预取，点按才打开系统选择器；无效文件或其他协议不打开播放器、不静默转交 |

### 离线消息预览

| 项 | 操作和预期 |
|---|---|
| 离线与地址 | 断网后分别从用户长按和助手更多打开；标题、列表、代码等可读，附件不加载；普通链接也不可点，地址可选择复制 |
| 原始标记 | 放入原始 HTML、外部 Markdown 图片和普通文字；预览不执行原始 HTML、不请求子资源，文字仍可读 |
| 文本及结构边界 | 用超长正文/代码，在截断边界附近放 emoji；不切开有效代理对并提示截断。表格、行内与引用超限用文字降级 |
| 缓存边界 | 在测试环境检查 800,004 字节、16 MiB、128 条及 7 天未用的边界；取被清理、过期、超限或仅存在于旧 HTML 缓存的 ID，应显示重新打开提示，不读取旧缓存 |

## 18 回滚

尚未暂存或提交。临时停用面材质可关总开关：现在关掉就是回到实色底，已经没有旧特效运行时可以退回去。
V6 迁移写进 `display_setting.legacyEffectsMigrated` 的标记不会随代码回退自动撤销：
降级版本仍按旧字段运行，但再升回新版本时旧开关不会再打开一次高级外观。
需要撤回源码时，先保全当前工作树和移植前的修改记录，再确认并反向应用仅属于本次移植的补丁，
逐一核对只属于本次的新增文件和依赖引用。不要按目录或整个共享文件恢复到 `HEAD`，
也不要把所有未跟踪文件视为移植产物；模型、上下文门限、导出、图标、文档及 Web UI 等原有修改须保留。
