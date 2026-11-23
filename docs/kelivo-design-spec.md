# Kelivo 设计体系与交互规格（复刻用调研报告）

调研对象：`/data/data/com.termux/files/home/WorkSpace/kelivo`（Flutter，Material 3）
用途：把视觉与手感移植到另一个 Kotlin/Compose 项目
性质：**只读调研**。除本文档外未修改 kelivo 任何文件。

## 阅读须知

- 所有数值后跟 `文件:行号`，路径相对 kelivo 仓库根。
- 色值统一 `#RRGGBB` / `#AARRGGBB`（前者不透明，后者首位为 alpha）。
- 标 **「实算」** 的色值**不在源码里直接出现**，是我按 Flutter `Color.alphaBlend` 的公式（alpha 先取整到字节）复算的结果，用于让 Compose 端能直接写死常量。这类值需要真机取色复核。
- 标 **「推定」** 的结论是我的归纳，不是源码断言。
- 未验证项集中在文末《附录 B》。

---

# 前提：这个项目的"默认长相"是哪一套

Kelivo 的 surface 层有**两套算法**，由设置开关切换，默认走**旧版（legacy / 非分层）**：

| 开关 | 默认值 | 出处 |
|---|---|---|
| `useLayeredSurfaces`（分层表面） | `false` | `lib/core/providers/settings_provider.dart:565`、`:1235` |
| `useLayeredSheetTiles`（分组卡片分层） | `false` | 同上 `:569`、`:1236` |
| `usePureBackground`（纯白/纯黑背景） | `false`，**桌面端默认 `true`** | 同上 `:561`、`:1230` |
| `themeMode` | `system` | `lib/theme/theme_provider.dart:6` |

装配管线（`lib/main.dart:1193-1204`）：

```
palette.light / palette.dark            // palettes.dart，9 套静态调色板
  → _applyPageSurface()                 // 明色模式下把 surface 下压 4 tone（仅 layered）
  → _withDerivedSurfaceContainers()     // 用 SurfaceLadder 覆盖 surfaceContainer* 五档
  → AppSemanticColors.light/dark()      // 派生 surfaceFill / hairline / success / warning …
  → ThemeData(useMaterial3: true, ...)
```

出处：`lib/theme/theme_factory.dart:56-90`（两个派生函数）、`:262-334`（`buildLightThemeForScheme`）、`:437-510`（`buildDarkThemeForScheme`）。

> 复刻建议：**以 legacy 那套为准**（默认值），layered 只作为可选皮肤。下文 A.1 给 legacy 的实算色值，A.2 给两套公式。

---

# A. 设计体系（视觉 token）

## A.0 主题入口与"页面/卡片"关系

`SurfaceLadder` 的核心约定（`lib/theme/surface_ladder.dart:25-27` 注释原文）：调色板里声明的 `surface` 被当作**卡片色**，页面色是它下压 4 tone（明色、非纯白时）。

| 角色 | 定义 | 出处 |
|---|---|---|
| page | `ColorScheme.surface` | `surface_ladder.dart:60` |
| card | 明色 = page 叠白 96%；暗色 = page 叠白 10% | `surface_ladder.dart:68-70` |
| surfaceFill | page 叠 `onSurface` 5%（明）/16%（暗） | `surface_ladder.dart:71-74` |
| hairline | 明 `outlineVariant`@6%；暗 @8% | `surface_ladder.dart:80-81`（legacy）/ `:102-103`（layered） |

`AppSemanticColors` 把 ladder 的结果挂到 `ThemeExtension` 上，全局用 `context.appColors` 取（`lib/theme/app_semantic_colors.dart:24-40`、`:186-220`）。`overlaySurface`（对话框/底部面板/菜单的底）在 legacy 下 = `scheme.surface`，在 layered 下 = `surfaceCard`（`app_semantic_colors.dart:45-47`）。

## A.1 颜色 token

### A.1.1 默认调色板（`ThemePalettes.defaultPalette`）

明色 `lib/theme/palettes.dart:43-74`，暗色 `:75-106`。

| 角色 | Light | Dark | 出处 |
|---|---|---|---|
| primary | `#4D5C92` | `#B6C4FF` | palettes 47 / 77 |
| onPrimary | `#FFFFFF` | `#1D2D61` | 48 / 78 |
| primaryContainer | `#DCE1FF` | `#354479` | 49 / 79 |
| onPrimaryContainer | `#03174B` | `#DCE1FF` | 50 / 80 |
| secondary | `#595D72` | `#C2C5DD` | 51 / 81 |
| secondaryContainer | `#DEE1F9` | `#424659` | 53 / 83 |
| tertiary | `#75546F` | `#E3BADA` | 55 / 85 |
| tertiaryContainer | `#FFD7F6` | `#5B3D57` | 57 / 87 |
| error | `#BB0947` | `#FCB4BD` | 59 / 89 |
| errorContainer | `#FDDADE` | `#910034` | 61 / 91 |
| **surface（= 页面底）** | `#F7F7F7` | `#121213` | 63 / 93 |
| onSurface | `#202020` | `#F9F9F9` | 64 / 94 |
| onSurfaceVariant | `#646464` | `#CECECE` | 65 / 95 |
| outline | `#1A000000`（10% 黑） | `#1A000000` | 66 / 96 |
| outlineVariant | `#000000`（**纯黑**） | `#FFFFFF`（**纯白**） | 67 / 97 |
| inverseSurface | `#121213` | `#F7F7F7` | 70 / 99 |
| surfaceTint | `#4D5C92` | `#B6C4FF` | 73 / 102 |

⚠️ 关键陷阱：默认调色板的 `outlineVariant` 是纯黑/纯白，**不是**常见的灰。它只是被当作"分割线/描边的基色"，真实线色由 ladder 再叠 alpha 得到（见下）。直接照抄 `outlineVariant` 到 Compose 会画出纯黑边框。

### A.1.2 其余 8 套调色板（只给 primary 与 surface，便于复刻主体色）

| 调色板 | Light primary / surface | Dark primary / surface | 出处 |
|---|---|---|---|
| blue | `#3E5E98` / `#FDFBFF` | `#ACC7FF` / `#1B1B1D` | palettes 112/128、142/158 |
| green | `#166C47` / `#FBFDF8` | `#87D7AA` / `#191C1A` | 177/193、207/223 |
| purple | `#5D5698` / `#FFFBFF` | `#C8BFFF` / `#1C1B1F` | 242/258、272/288 |
| yellow | `#855304` / `#FFFBF9` | `#FDB967` / `#1F1B16` | 307/323、337/353 |
| smoky_rose | `#824E6C` / `#FFFBFF` | `#F5B3D6` / `#201A1D` | 372/388、402/418 |
| terracotta | `#8B4E3B` / `#FFFBFF` | `#FFB59E` / `#221A18` | 437/453、467/483 |
| monochrome | `#000000` / `#F7F7F7` | `#FFFFFF` / `#121213` | 501/517、531/547 |
| doc_theme | `#00B96B` / `#F7F7F7` | `#00B96B` / `#121213` | 566/582、586/602 |

自定义主题用 Material You TONAL_SPOT 生成，`neutralPalette` chroma 3.5、`neutralVariant` 5.0（`lib/theme/custom_theme.dart:117-135`）。

### A.1.3 派生表面色（legacy 默认态，**实算**）

公式在 `lib/theme/surface_ladder.dart:63-90`；下面是代入默认调色板后的结果：

| 层 | Light（实算） | Dark（实算） | 公式（源码） |
|---|---|---|---|
| page | `#F7F7F7` | `#121213` | `surface_ladder.dart:60` |
| card | `#FFFFFF` | `#2A2A2B` | `:68-70` |
| surfaceFill | `#ECECEC` | `#373738` | `:71-74` |
| surfaceCardFill | = surfaceFill | = surfaceFill | `:75` |
| surfaceContainerLowest | `#FDFDFD` | `#0D0D0E` | `:82` |
| surfaceContainerLow | `#FBFBFB` | `#19191A` | `:83` |
| surfaceContainer | `#FAFAFA` | `#1C1C1D` | `:84` |
| surfaceContainerHigh | `#FEFEFE` | `#202021` | `:85` |
| surfaceContainerHighest | `#ECECEC` | `#272728` | `:86-88` |

### A.1.4 分割线 / 描边（带 alpha，非实算色）

| token | Light | Dark | 出处 |
|---|---|---|---|
| hairline | `#0F000000`（6% 黑） | `#14FFFFFF`（8% 白） | `surface_ladder.dart:80` |
| hairlineStrong | `#61000000`（38% 黑） | `#42FFFFFF`（26% 白） | `:81` |
| layered hairline | `onSurface`@8% | `onSurface`@12% | `:102` |
| layered hairlineStrong | `onSurface`@20% | `onSurface`@26% | `:103` |
| 分组卡片内 Divider | `outlineVariant`@18%，height 10，thickness 0.6 | 同 | `shared/widgets/section_card.dart:73-76` |
| 桌面浮层 | `surface`@28%（暗）/ 56%（明） | | `lib/theme/design_tokens.dart:16-27` |

### A.1.5 语义色

`lib/theme/app_semantic_colors.dart:57-96`（light）、`:98-140`（dark）。所有 success/warning 都过 `harmonizeWith(cs.primary)`，即**会随主题色偏移**，不是固定值。

| token | Light 基色 | Dark 基色 | 出处 |
|---|---|---|---|
| success | `#2E7D32` →harmonize | `#81C784` →harmonize | 60 / 101 |
| successContainer | `#A5D6A7` →harmonize | `#1B5E20` →harmonize | 61 / 102 |
| warning | `#F57C00` →harmonize | `#FFB74D` →harmonize | 62 / 103 |
| warningContainer | `#FFE0B2` →harmonize | `#8D4E00` →harmonize | 63 / 104 |
| searchHighlight | `#FFD700`@55% | `#B8860B`@55% | 65 / 106 |
| chartSeries[0..7] | `#2563EB #0F8F83 #EA580C #8B5CF6 #E11D48 #16A34A #CA8A04 #0891B2` | `#60A5FA #5EEAD4 #FB923C #A78BFA #FB7185 #86EFAC #FACC15 #67E8F9` | 67-76 / 108-117 |

### A.1.6 前景文字色阶（聊天正文，legacy 默认态）

| 用途 | Light | Dark | 出处 |
|---|---|---|---|
| strong | `secondary` | `secondary` | `features/chat/widgets/chat_surface.dart:217` |
| medium | `secondary`@90% | 同 | `:218` |
| muted | `onSurface`@50% | 同 | `:219` |
| body | `onSurface`@70% | 同 | `:220` |
| divider | `outline`@15% | `onSurface`@24% | `:221-223` |
| accent | `primary` | `primary` | `:224` |
| 聊天内安静填充 | `onSurface`@4% | `onSurface`@6% | `:130-135` |

Frosted/solid 气泡时改用 `气泡文字色` 的 alpha 阶梯：strong 78%/88%、medium 66%/76%、muted 46%/56%、body 60%/72%、divider 14%/16%、accent 74%/84%（`chat_surface.dart:232-241`）。

### A.1.7 用户气泡底色与输入框底色

| 项 | Light | Dark | 出处 |
|---|---|---|---|
| 用户气泡（defaultStyle） | `primary`@8% | `primary`@15% | `features/chat/widgets/chat_message_widget.dart:2871-2878` |
| 用户气泡叠底后（实算） | `#EAEBEF` | `#2A2D36` | 同上代入默认 primary |
| 输入框默认不透明度 | 0.8236 | 0.7396 | `core/providers/settings_provider.dart:5637-5638` |
| 输入框叠色 | `primary`@2% | `onSurface`@7%（有背景图时 9%） | `features/home/widgets/chat_input_bar.dart:989-997` |
| 输入框描边 | `outline`@20%，1dp | `onSurface`@10%，1dp | `chat_input_bar.dart:4722-4736` |
| 输入框毛玻璃 | `BackdropFilter` sigma 14 | | `chat_input_bar.dart:4715-4718` |

## A.2 Surface ladder 规则

### A.2.1 legacy（默认）

```
page              = scheme.surface
card              = alphaBlend(white, 0.96) over page   // 暗色: alphaBlend(white, 0.10)
surfaceFill       = alphaBlend(onSurface, 0.05) over page // 暗色: 0.16
surfaceCardFill   = surfaceFill
hairline          = outlineVariant @ 0.06  // 暗色 0.08
hairlineStrong    = outlineVariant @ 0.38  // 暗色 0.26
Lowest            = white@0.72 over page   // 暗色 black@0.28
Low               = white@0.55             // 暗色 white@0.03
Container         = white@0.35             // 暗色 white@0.045
High              = white@0.85             // 暗色 white@0.06
Highest           = onSurface@0.05         // 暗色 white@0.09
```
出处：`lib/theme/surface_ladder.dart:63-90`。

特征：**明色是"往上加白"，暗色是"往上加白"**——暗色里越高的层越亮，但步进很小（3%→4.5%→6%→9%），视觉上是 4~8 个色阶的微差，不是 Material 默认那种固定 tone 差。

### A.2.2 layered（可选皮肤）

HCT tone 平移，保持 hue/chroma（`surface_ladder.dart:5-27` 提供 `shiftTone` / `atTone` / `lerpTone`）：

| 层 | Dark Δtone | Light |
|---|---|---|
| page | 0（`theme_factory.dart:71`：明色 page = surface −4） | surface −4 |
| card | +11 | +4；若 `100 − tone(page) < 4.5` 则 −3.5（`:97`） |
| surfaceFill | +9 | −2.5 |
| surfaceCardFill | card +9 | card −3.2 |
| Lowest | −3 | `atTone(page, 100)`（纯白） |
| Low | +4 | `lerp(page, card, 0.50)` |
| Container | +7 | `lerp(page, card, 0.75)` |
| High | +11 | = card |
| Highest | +14 | = surfaceFill |

出处：`surface_ladder.dart:93-134`。

> 复刻建议：Compose 端不必实现 HCT。legacy 的五档差值（明：0 / +2 / +3 / +7 / −11 相对 page，见 A.1.3 实算表）直接写常量即可；layered 只在需要复刻"分层"皮肤时再引入 HCT（`material_color_utilities` 的 Kotlin 版 `material-color-utilities` 有 Hct）。

## A.3 字体体系

### A.3.1 结构事实

- **没有集中式 TextTheme 覆盖**。`theme_factory.dart` 只设置了 `appBarTheme.titleTextStyle`（18 / semibold，`:253-257`、`:421-425`；`ForScheme` 变体在 `:330`、`:493`）、`snackBarTheme`（14 / medium，`:237`、`:405`）。其余全部是局部 inline `TextStyle`。
- 唯一的"排版 token 文件"是字重：`lib/theme/app_font_weights.dart`。

### A.3.2 字重 token（含 Android 降级，**复刻关键**）

`lib/theme/app_font_weights.dart:9-27`：

| token | 定义 | Android 实际值 |
|---|---|---|
| regular | w400 | w400 |
| medium | w500 | **w400** |
| semibold | w600 | **w500** |
| emphasis / strong | w700 | **w500** |
| heavy | w800 | **w500** |
| black | w900 | **w500** |

规则（`:17-28`）：非 Android 原样；Android 上 `w500→w400`，`≥w600→w500`。
> 这是整套设计最重要的"手感一致"约束：Android 上全应用最多只有 w400 / w500 两档。Compose 端若直接用 FontWeight.SemiBold，屏上会比 Kelivo 粗一档。

### A.3.3 字号 token（实测分布，`grep -rho "fontSize: N"`，共 1800+ 处）

| 名次 | 字号 | 次数 | 典型用途（推定） |
|---|---|---|---|
| 1 | 12 | 463 | 元信息、标签、图注 |
| 2 | 13 | 356 | 次级文字、副标题 |
| 3 | 14 | 274 | 正文（桌面）/ Snackbar |
| 4 | 15 | 259 | 设置行标题、表单字段 |
| 5 | 16 | 109 | 强调正文、卡片标题 |
| 6 | 11 | 83 | 角标、极小说明 |
| 7 | 12.5 | 71 | 输入栏辅助行 |
| 8 | 13.5 | 57 | 列表副行 |
| 9 | 18 | 35 | AppBar 标题 / 区块标题 |
| 10 | 11.5 | 24 | 计数 |
| — | 17 / 14.5 / 22 | 20/16/7 | 特殊 |
| — | 15.7 | 1 | **助手正文（移动端）** |
| — | 15.5 | 1 | **用户正文（移动端）** |

具体锚点：

| 用途 | 值 | 出处 |
|---|---|---|
| 助手正文（移动 / 桌面） | 15.7 / 14.0 | `features/chat/widgets/chat_message_widget.dart:2911` |
| 用户正文（移动 / 桌面） | 15.5 / 14.0 | `:2370` |
| 输入框文字（移动 / 桌面） | 15.0 / 14.0 × chatFontScale | `features/home/widgets/chat_input_bar.dart:4615` |
| AppBar 标题 | 18 | `theme_factory.dart:255`、`:423` |
| Snackbar 内容 | 14 | `theme_factory.dart:237`、`:405` |
| 设置行主/副/尾 | 15 / 13 / 12 | `shared/widgets/ios_settings_rows.dart:99,130,142` |
| 表单字段 | 15（多行行高 1.25 / 单行 1.0） | `shared/widgets/ios_form_text_field.dart:157-165` |
| 操作表标题 | 15 / semibold / `onSurface`@70% | `shared/widgets/action_sheet.dart:130-133` |
| 操作表行文字 | 15 | `action_sheet.dart:179` |

### A.3.4 行高规则

实测 `height:` 值分布（仅列 1.0–2.0 区间）：`1.4` 76 次、`1.35` 48、`1.25` 35、`1.45` 34、`1.2` 24、`1.5` 23、`1.3` 23。

| 场景 | 行高 | 出处 |
|---|---|---|
| 助手正文 | 1.5 | `chat_message_widget.dart:2945, 2999` |
| 用户正文 | 1.45（气泡内）/ 1.4（纯文本分支） | `:2386, 2430` |
| 表单多行 | 1.25 | `ios_form_text_field.dart:160` |
| 表单单行 | 1.0 | `:160, 165` |

> 推定规律：**代码/密排 1.2–1.25，UI 标签 1.3–1.35，长文本阅读 1.4–1.5**。中文长文（助手回答）给到 1.5，比 Material 默认的 1.43 更松。

### A.3.5 字体族

`theme_factory.dart:12-30`：

| 平台 | fallback 链 |
|---|---|
| 默认（iOS/macOS） | `PingFang SC` → `Heiti SC` → `Hiragino Sans GB` → `Roboto` |
| Android | `sans-serif` |
| Windows | `Twemoji Country Flags` → `Segoe UI` → `Microsoft YaHei` → `SimHei` |

无 `fontFamily` 指定，全靠系统默认 + fallback 保字重稳定。

## A.4 圆角体系

实测 `BorderRadius.circular(N)` 分布：`12` 358 次、`10` 172、`14` 171、`16` 149、`999` 126、`8` 85、`20` 24、`18` 23、`6` 16。

| 组件 | 圆角 | 出处 |
|---|---|---|
| 分组卡片 SectionCard（标准） | **12** | `shared/widgets/section_card.dart:60` |
| 分组卡片（emphasized） | **18** | `:60` |
| 聊天气泡 / 工具卡 / 长按菜单 / 表单 sheet 顶部 | **16** | `chat_message_widget.dart:2865, 1848`；`chat_bubble_style.dart:236`；`shared/widgets/form_sheet.dart:26-28` |
| 底部操作表顶部 | **20** | `shared/widgets/action_sheet.dart:53-55` |
| 自定义可拖拽面板顶部 | **20** | `shared/widgets/custom_bottom_sheet.dart:231` |
| 输入框（composer） | **20** | `chat_input_bar.dart:4713, 4720` |
| 操作表行 tile | **14** | `action_sheet.dart:164` |
| 会话历史行 | **14** | `features/chat/pages/chat_history_page.dart:272` |
| 图标按钮底 | **8**（默认） | `shared/widgets/ios_tactile.dart:23` |
| 列表行按钮 | **12** | `shared/widgets/ios_tile_button.dart:96` |
| 表单字段填充框 | **12**（小件 10） | `ios_form_text_field.dart:291 / :245` |
| 药丸 / chip | **999** | `chat_input_bar.dart:5457` |
| 胶囊 token | `AppRadii.capsule = 28` | `design_tokens.dart:28` |
| 分段控件外壳 | **12**（内 padding 4，间隙 6） | `shared/widgets/segmented_tabs.dart:43-45` |
| 拖拽把手 | 32×4，r **2** | `custom_bottom_sheet.dart:576-584` |
| Snackbar | **12** | `theme_factory.dart:241` |

**对话框**：`DialogThemeData` 只覆盖了背景色，**没设 shape**（`theme_factory.dart:74-76`）→ 用 M3 默认 **28**。全库唯一显式改对话框圆角的地方是 `shared/dialogs/reasoning_budget_custom_dialog.dart:31-32`（**16**）。

> 复刻建议的表（Compose）：卡片 12 / 强调卡片 18 / 气泡 16 / 操作表与面板顶部 20 / 输入框 20 / 行 14 / 图标按钮底 8 / 药丸 999 / 对话框 28。不要把所有东西统一成 16。

## A.5 聊天气泡样式（`lib/theme/chat_bubble_style.dart` 全量）

### A.5.1 结构

- 三个目标：`user` / `assistant` / `thinkingToolCard`（`chat_bubble_style.dart:6`），每类各自一份 overrides。
- 三种背景风格：`defaultStyle` / `frosted` / `solid`（`core/providers/settings_provider.dart:6810`）。默认 `defaultStyle`（`:3189-3190`）。
- 所有 overrides 字段可空，`null` = 保持硬编码默认值（`chat_bubble_style.dart:10-42`）。

### A.5.2 解析结果（`resolveBubbleStyle`，`chat_bubble_style.dart:195-238`）

| 输出 | defaultStyle | frosted | solid |
|---|---|---|---|
| background | `surfaceContainerHigh` @ 1.0 | `surfaceContainerHigh` @ **0.66** | `surfaceContainerHigh` @ 1.0 |
| border | `outlineVariant` @ 0.16 | `outlineVariant` @ **0.14** | `outlineVariant` @ 0.16 |
| text | `onSurface` | `onSurface` | `onSurface` |
| borderWidth | **0.8** | 0.8 | 0.8 |
| radius | **16** | 16 | 16 |
| blurSigma | — | **14** | — |

默认值来源：`chat_bubble_style.dart:209-216`（opacity 与 borderOpacity）、`:224-226`（几何）。

叠加后实际观感（**实算**，默认调色板，叠在 page 上）：frosted 明 `#FCFCFC`、暗 `#1B1B1C`。

### A.5.3 默认态下"助手没有气泡"

这是最容易复刻错的点。`chat_message_widget.dart:2862-2886` + `core/providers/settings_provider.dart:573-575`：

```
assistantBodyBubbleVisible
  = (chatMessageBackgroundStyle != defaultStyle) && _showAssistantBodyBubble
```

而 `defaultStyle` 分支里，`defaultColor == null` 时直接 `return content;`（无任何底色，`chat_surface.dart:118-120`）。

**结论**：出厂默认（defaultStyle）下，**只有用户消息有底色气泡，助手消息是裸文本直接铺在页面色上**。助手气泡只在你把风格切成 frosted/solid 时才出现。

### A.5.4 几何与间距

| 项 | 值 | 出处 |
|---|---|---|
| 用户消息最大宽度 | 屏宽 × **0.75** | `chat_message_widget.dart:2170-2172` |
| 正文气泡内边距 | `EdgeInsets.all(**12**)` | `chat_message_widget.dart:2875` |
| 工具卡内边距 | `fromLTRB(16, 12, 12, 12)` | `:7286`、`:7614` |
| 媒体与文字之间 | 8 | `chat_message_widget.dart:2178-2179` |
| 同组助手气泡间隙 | 8 | `:3040` |
| 气泡与操作行 | 8 / 6 | `chat_message_widget.dart:2186` |
| 消息列表内容最大宽 | **860** | `features/home/utils/chat_layout_constants.dart:4` |
| 列表内边距 | 居中 gutter = `(宽 − 860)/2` | `features/home/widgets/message_list_view.dart:1840-1852` |
| 删除消息淡出+收合时长 | **240ms** | `chat_layout_constants.dart:10-12` |

气泡形状：纯圆角矩形，**没有尾巴/尖角**，四个角同值 16（`chat_message_widget.dart:2865`）。全库 `BorderRadius.only(...)` 在 chat 下只出现在 sheet 顶部（`bottom_tools_sheet.dart:108`、`context_management_sheet.dart:42`），不用于气泡。

### A.5.5 frosted 渲染三档（若要复刻"毛玻璃"）

`features/chat/widgets/frosted/frosted_surface.dart:8-100`：
- Tier 0（均匀/无 scope/sigma ≤ 0）：纯 `DecoratedBox` 染色
- Tier 1（cached）：预先模糊好的快照裁剪 + 同一 tint
- live：真 `BackdropFilter`（`ImageFilter.blur(sigma)`），仅在主题切换/背景动画时启用
- 快照失败 → 只显示 tint，**绝不回退到 live glass**

> Compose 端对应 `Modifier.blur()`（API 31+）或 `RenderEffect`；sigma 14 与 0.66 的 tint 叠加是观感的核心，缺一不可。

## A.6 间距与内边距

### A.6.1 显式 token（`lib/theme/design_tokens.dart:31-37`）

`AppSpacing`：`xxs=4` / `xs=8` / `sm=12` / `md=16` / `lg=20`。

阴影（`design_tokens.dart:3-12`）：`AppShadows.soft` = `black`@5%，blur 18，offset (0,6)。全库唯一共享阴影。

### A.6.2 实测分布（全库 grep）

| 形式 | Top 值 |
|---|---|
| `EdgeInsets.all(N)` | 12（87）、16（39）、6（34）、8（28）、14（26）、4（17）、24（16） |
| `horizontal: N` | 12（312）、8（107）、10（81）、24（63）、16（61）、14（54） |
| `vertical: N` | 8（123）、10（109）、6（104）、12（84）、4（62）、24（56） |
| `SizedBox(height: N)` | 12（470）、8（291）、10（185）、6（165）、16（153）、4（104） |

> 推定：**基准网格 4dp**，常用档 4 / 6 / 8 / 10 / 12 / 14 / 16 / 24。12 是默认页边距与卡片内边距，8 是元素间隙，16 是区块间距。14 只出现在次级面板的内衬（`carried_options_section.dart:154,164`、`form_sheet.dart:56-63` 用 10/12/16）。

### A.6.3 关键布局常数

| 项 | 值 | 出处 |
|---|---|---|
| 聊天内容最大宽 | 860 | `chat_layout_constants.dart:4` |
| 响应式断点 | mobile 600 / tablet 900 / desktop 1200 / wide 1600 | `shared/responsive/breakpoints.dart:4-7` |
| 工具图卡高度 | `kToolImageCardHeight` | `chat_message_widget.dart:250-256` |
| 操作表行高 | 48（上下外边距 4，左右内衬 12） | `action_sheet.dart:158-170` |
| 抽屉最大遮罩 | 0.5 | `shared/widgets/interactive_drawer.dart:115` |

---

# B. 交互规格（手感）

## B.1 动画时长分布

全库 `Duration(milliseconds: N)` 频次（`grep -rho` 统计，共 700+ 处）：

| 名次 | 时长 | 次数 | 场景（已核实） |
|---|---|---|---|
| 1 | **180** | 77 | 推定：控件状态切换 / `AnimatedSize` 展开（如 `assistant_settings_edit_memory_tab.dart:218`）。注意 180 也恰是 `kAnimFast` |
| 2 | **220** | 73 | 底部面板吸附 settle（`custom_bottom_sheet.dart:527-530`） |
| 3 | **160** | 71 | 图标按钮底色 / 列表行按压（`ios_tactile.dart:169`、`ios_tile_button.dart:91`） |
| 4 | **200** | 66 | 卡片按压缩放/换色默认值（`ios_tactile.dart:380`）；图标颜色 tween（`:97`） |
| 5 | **260** | 60 | 输入栏切换（`chat_input_bar.dart:5088`）；工具卡按压（`chat_message_widget.dart:7280`）；面板内容拖拽回位（`custom_bottom_sheet.dart:321`）；操作表行按压（`action_sheet.dart:166`） |
| 6 | **120** | 49 | 轻量淡入淡出（`selection_result_panel.dart:111`） |
| 7 | **110** | 42 | 桌面菜单（`browser_menu.dart:217`） |
| 8 | **100** | 23 | 瞬态反馈 |
| 9 | **300** | 22 | 图片查看器进入（`image_viewer_page.dart:315-317`） |
| 10 | **140** | 20 | 小按钮（`chat_input_bar.dart:4528`） |
| 11 | **80** | 14 | 极短反馈 |
| 12 | **150** | 13 | 长按菜单淡入（`chat_message_widget.dart:4446`） |
| 13 | **500** | 12 | 多为 Timer / 防抖，**不全是动效**（如 `core/services/provider_balance_refresh.dart:109`） |
| 14 | **60 / 240** | 11 / 11 | 240 = `kAnim` 标准档 / 删除消息淡出（`chat_layout_constants.dart:10-12`）；也是图片查看器退出时长 |
| 15 | **250** | 9 | 抽屉 animateTo（`interactive_drawer.dart:70, 112`） |
| 16 | **320** | 8 | 输入框展开（`chat_input_bar.dart:904`） |
| 17 | **280 / 900 / 360 / 350** | 7/6/6/5 | 特殊（900 为提示气泡 showDuration） |

### B.1.1 显式时间令牌（源码里真实存在的三档）

`lib/shared/animations/widgets.dart:6-8`：

| 令牌 | 值 | 说明 |
|---|---|---|
| `kAnimFast` | `180ms` | 快速档（同时是 AnimatedIconSwap 之外最常用的裸值） |
| `kAnim` | `240ms` | 标准档（`AnimatedIconSwap` 默认值，`:15`） |
| `kAnimSlow` | `320ms` | 慢速档 |

⚠️ 注意：这三个常量**只被少数地方引用**，全库绝大多数动画仍写裸 `Duration(milliseconds: …)`。所以"时长体系"事实上是**隐性的高频值**（180/220/160/200/260），而不是引用 token。

**归纳（推定）**：

| 语义层 | 时长 |
|---|---|
| 按下即时反馈 | 100–160（常用 160） |
| 状态切换 / 展开收起 | 200–260（常用 220 / 260） |
| 面板 / 页面进出 | 250–320 |
| 强调 / 大元素位移 | 300–500 |

## B.2 缓动曲线

全库 `Curves.*` 频次：

| 曲线 | 次数 | 用途（已核实） |
|---|---|---|
| **`Curves.easeOutCubic`** | **392** | **事实上的唯一标准**：进入、展开、按压、吸附全部用它 |
| `Curves.easeOut` | 35 | 轻量淡入 |
| **`Curves.easeInCubic`** | **27** | **配对的反向曲线**，只在 `reverseCurve` 里出现（`chat_input_bar.dart:909`、`model_select_sheet.dart:2471`） |
| `Curves.easeInOutCubic` | 18 | 对称的双向位移（`browser_page.dart:1234`、`chat_message_widget.dart:8700`） |
| `Curves.easeInOut` | 10 | 同上，轻量处 |
| `Curves.easeOutBack` | 4 | 仅"弹出感"：`providers_page.dart:1755`、`effort_slider.dart:117`、`ios_checkbox.dart:172`、`assistant_settings_page.dart:80`、`side_drawer.dart:2290` |
| `Curves.fastEaseInToSlowEaseOut` + `.flipped` | 2 | 浏览器管理页双向（`browser_management_route.dart:138-139`） |
| `Curves.easeInOutCubicEmphasized` | 1 | `chat_message_widget.dart:5308` |
| `Curves.linear` | 2 | 进度 |

**分工结论**：

| 场景 | 曲线 |
|---|---|
| 进入 / 展开 | `easeOutCubic` |
| 退出 / 收起 | `easeInCubic`（显式配对，仅在手写 route/animation 里） |
| 双向对称位移 | `easeInOutCubic` / `easeInOut` |
| 弹跳落位 | `easeOutBack`（极少数，慎用） |
| 点击反馈（颜色/缩放） | `easeOutCubic` |

## B.3 页面 / 面板过渡（含"进出是否对称"核对）

### B.3.1 常规页面：走平台默认，对称

- 全库 **135 处 `MaterialPageRoute`**；**没有**自定义 `pageTransitionsTheme`（`grep pageTransitionsTheme` 零命中）。
- 因此：Android = Zoom 过渡，iOS/macOS = Cupertino 滑动，**进入与退出时长、曲线完全对称**（框架保证），时长取平台默认（Android 300ms / iOS 400ms）。
- `CupertinoPageRoute` 子类共 **3 处**（`browser_management_route.dart:26`、`ui_web_preview.dart:118`、`genui_expansion.dart:427`），其中两处显式对称：
  - `features/intelligent_ui/host/ui_web_preview.dart:122-125`：`reverseTransitionDuration => transitionDuration`
  - `features/intelligent_ui/widgets/genui_expansion.dart:431-434`：同上

### B.3.2 手写 `PageRouteBuilder`：**多处不对称**

这是本次调研最需要提醒的一点——**不能假设"进出同参"**。

| 位置 | 进入 | 退出 | 曲线 | 对称? |
|---|---|---|---|---|
| 图片查看器 `features/chat/pages/image_viewer_page.dart:313-329` | **300ms** | **240ms** | 进入 = `animation.drive(CurveTween(easeOutCubic))`；退出走同一 CurveTween 反向（等效 `easeOutCubic` 的镜像，约 `easeInCubic`） | ⚠️ **仅时长不对称**（300 vs 240），曲线互为镜像 |
| 模型选择 → 桌面页 `features/model/widgets/model_select_sheet.dart:2462-2474` | **220ms** | **200ms** | `easeOutCubic` / `easeInCubic` | ❌ 时长+曲线都不同 |
| 文件预览 `features/workspace/widgets/preview/file_preview.dart:212-231` | **360ms** | **280ms** | `easeOutCubic` / `easeInCubic`，另有 `SlideTransition(Offset(0, 0.02) → 0)` | ❌ 0.78 倍 |
| 浏览器标签总览 `features/browser/browser_tabs_route.dart:183-188` | **360ms** | **320ms** | 手写 `PageRoute`，整页→卡片 | ❌ 0.89 倍 |
| 浏览器桌面态 `features/browser/browser_desktop_route.dart:11-19` | **180ms** | **150ms** | `easeInOut` | ❌ 0.83 倍 |
| 设置搜索页 `features/settings/pages/settings_search_page.dart:134-138` | **300ms** | **300ms** | `easeOutCubic` / `easeInCubic` | ✅ 时长对称、曲线镜像 |
| 浏览器菜单 `features/browser/widgets/browser_menu.dart:217` | 110ms | 默认(=进入) | — | ✅ |
| 浏览器页 `features/browser/pages/browser_page.dart:1925` | 160ms | 默认 | — | ✅ |
| 推理档位浮层 `desktop/reasoning_level_popover.dart:27` | 180ms | 默认 | — | ✅ |
| 助手面板 `desktop/setting/assistants_pane.dart:340` | 160ms | 默认 | — | ✅ |
| 技能浮层 `desktop/skills_popover.dart:35` | 260ms | 默认 | — | ✅ |
| 选择结果面板 `features/chat/widgets/selection_result_panel.dart:111` | 120ms | 默认 | — | ✅ |
| 浏览器管理 `features/browser/browser_management_route.dart:138-139` | — | — | `fastEaseInToSlowEaseOut` / `.flipped` | ✅ 显式对称 |

另：全部 route 都读 `MediaQuery.disableAnimationsOf(context)`，系统"移除动画"开启时时长归零（如 `image_viewer_page.dart:314-321`、`form_sheet.dart:23-25`）。

> 若要"精确复刻"，建议**不照抄这些不一致**，而是统一为：进入 300ms `easeOutCubic`、退出 240ms `easeInCubic`（与图片查看器一致的"快出慢进"手感），并把不动画的降级开关带上。

### B.3.3 对话框

会话中"App 对话框"不是 `AlertDialog` 的默认外观，而是自研壳 `RawDialogRoute`（`lib/features/settings/widgets/custom_theme_widgets.dart:45-51`、`:107-114`）：

| 项 | 值 | 出处 |
|---|---|---|
| 时长 | **200ms**（只设 `transitionDuration`，**退出同长 = 对称**） | `custom_theme_widgets.dart:45-48` |
| 曲线 | `easeOutCubic`（`CurvedAnimation`） | `:108-109` |
| 缩放 | **0.98 → 1.0**（`ScaleTransition`） | `:110` |
| 淡入 | `FadeTransition` 同一 curved | `:109` |
| 遮罩 | `scrim` @ **0.25** | `:51` |
| 面板圆角 / 描边 | **14** / 1dp（暗 `onSurface`@8%，明 `outlineVariant`@25%） | `:77-82`、`:88-89` |
| 无障碍降级 | `disableAnimations` → `Duration.zero` | `:46-47` |
| 键盘跟随 | `AnimatedPadding` 160ms | `features/chat/widgets/context_dialog.dart:21-23` |
| 对话框内按钮按压 | `pressedScale: .98` | `context_dialog.dart:59` |

> 与 A.4 的"对话框 28"并不矛盾：28 是 `ThemeData.dialogTheme` 未设 shape 时的 M3 默认值，走 `showDialog<AlertDialog>` 的少数地方是这个值；**应用自研的对话框壳是 14 + 0.98 缩放**。复刻时应以后者为准。

### B.3.4 底部面板

| 项 | 值 | 出处 |
|---|---|---|
| `showModalBottomSheet` 用量 | **103 处** | `grep -rn` |
| 操作表：顶部圆角 | 20 | `action_sheet.dart:53-55` |
| 操作表：行高 / 圆角 / 按压时长 | 48 / 14 / 260ms | `action_sheet.dart:158-166` |
| 表单 sheet：顶部圆角 | 16 | `form_sheet.dart:26-28` |
| 表单 sheet：最大高度 | 屏高 × 0.9 | `:30` |
| 表单 sheet 内衬 | L10 R10 T12 B(insets+16) | `:60-71` |
| 自定义面板：顶部圆角 / 把手 | 20 / 32×4 r2，色 `onSurface`@12% | `custom_bottom_sheet.dart:231, 576-584` |
| 面板拖拽吸附时长 | 220ms `easeOutCubic` | `:527-530` |
| 内容拖拽回位 | 260ms | `:321` |
| 关闭判定阈值 | 进度 **0.7** | `:91`、`:514` |
| fling 阈值 | **700 px/s** | `:90`、`:478-483` |

面板内部结构（`custom_bottom_sheet.dart:468-497`）：上拖 → expandedTop；下拖超阈值 → partialTop 或 dismiss；否则吸附最近档（`_nearestVisibleTop`）。**上滑展开与下滑收起用同一 `_animateToTop`，对称**。

### B.3.5 侧边栏 / 抽屉

`shared/widgets/interactive_drawer.dart`：

| 项 | 值 | 出处 |
|---|---|---|
| 时长 / 曲线 | **250ms / `easeOutCubic`** | `:70-71`（animateTo）、`:112-113`（widget 默认） |
| open / close | `fling(velocity: ±2.0)` —— 两侧都用同一条 fling，**完全对称** | `:48-57` |
| 拖拽归一化 | `delta / drawerWidth`，clamp 0..1 | `:257-265` |
| fling 判定 | **365 px/s** | `:197, :259-267` |
| 吸附判定 | 进度 ≥ 0.5 开，否则关 | `:269-273` |
| 遮罩 | 透明度 0..**0.5** 线性随进度 | `:115, :283-285` |
| 抽屉宽度 | 默认 `max(300, 宽 × 0.80)`；**首页覆写为 宽 × 0.75** | `:411-413`；`features/home/pages/home_mobile_layout.dart:97` |
| 首页遮罩覆写 | `onSurface`@**0.12**（不是默认 0.5） | `home_mobile_layout.dart:98-99` |
| 框架 `Drawer` 用量 | 仅 **1 处**（`side_drawer.dart:2999`，桌面侧栏内）；移动端抽屉完全自研，`Scaffold.drawer` 参数仅出现 1 次且指向自研组件 | `features/home/pages/home_mobile_layout.dart:101` |
| 主内容位移 | `Transform.translate(dx = ±drawerWidth × value)` | `:291-292` |
| 点遮罩关闭 | `barrierDismissible`，可挂钩 haptic | `:296-302` |
| 平板模式 | 内容不位移、无遮罩 | `:277-280` |

> 手感要点：抽屉是**跟手的**（拖到哪停到哪），松手才按速度/半程决定归属；没有 Material `Drawer` 的固定曲线进场。

### B.3.6 长按菜单（气泡上下文菜单）

`chat_message_widget.dart:1765-1840`：

| 项 | 值 |
|---|---|
| 宿主 | `showGeneralDialog`，`barrierColor = scrim`@**0.08** |
| 菜单宽 | **220** |
| 估算高 | 140（无可编辑）/ 184（可编辑） |
| 与气泡间距 | **10** |
| 安全内缩 | 四边 12 |
| 定位 | 右对齐气泡右缘，水平/垂直双向 clamp；上方优先 |
| 出现动画 | `AnimatedOpacity` **150ms** `easeOutCubic`，**只有淡入，无缩放、无位移**（`:4443-4451`） |
| 面板样式 | r16，描边 1dp（`onSurface`@8% 暗 / `outlineVariant`@20% 明），`BackdropFilter` sigma 14，底 `surfaceContainerHigh`@**0.66**（`:1836-1860`） |

## B.4 点击 / 长按反馈

**没有涟漪**。全应用用两个自研组件替掉 `InkWell`：

### B.4.1 `IosIconButton`（图标按钮）

`shared/widgets/ios_tactile.dart:9-215`，注释原文："no ripple, color tween on press, no scale"。

| 项 | 值 | 出处 |
|---|---|---|
| 按压识别 | `Listener.onPointerDown/Up/Cancel`（不用 `onTapDown`，避免与 Tooltip 抢手势） | `ios_tactile.dart:126-165` |
| 图标颜色 tween | 200ms `easeOutCubic`，按下向 `onSurface` lerp **0.35** | `:97-98`、`:86` |
| 悬停颜色 | 向 `onSurface` lerp **0.20** | `:88` |
| 底色 tween | **160ms** `easeOutCubic` | `:169-170` |
| 按下底色 | `onSurface`@**12%**（暗）/ **8%**（明） | `:110-113` |
| 悬停/聚焦底色 | `onSurface`@**8%**（暗）/ **6%**（明） | `:114-117` |
| 圆角 / padding | 8 / `EdgeInsets.all(6)` | `:23`、`:18` |
| 默认图标尺寸 | 20 | `:17` |
| 禁用态 | 颜色 @45% | `:68-76` |

### B.4.2 `IosCardPress`（卡片 / 列表行按压）

`ios_tactile.dart:226-470`，注释："background color tween on press, no ripple, no scale"。

| 项 | 值 | 出处 |
|---|---|---|
| 底色 | `baseColor` 默认取 `appColors.surfaceCard` | `:367` |
| 按压混合强度 k | **0.12（明）/ 0.14（暗）** | `:369` |
| 按下色 | `Color.lerp(base, onSurface, k)`；base 透明时用 `onSurface`@k | `:369-370, 463-470` |
| 悬停色 | `k × 0.7`（≈0.084 / 0.098） | `:371-375` |
| 时长 | **200ms**（可覆盖，调用方常用 260ms） | `:380` |
| 曲线 | `easeOutCubic` | `:402`、`:415` |
| 缩放 | `pressedScale` 默认 **1.0（无缩放）**，注释举例 0.98 | `:256-258`、`:379`、`:381` |
| 圆角 | 默认 **12** | `:393`、`:406` |
| 触感 | 点击时若设置开启 → `Haptics.soft()` | `:356-361` |
| 手势 | `TapGestureRecognizer` + `LongPressGestureRecognizer`（可自定义超时） | `:377-390` |

调用方常用参数：操作表行 `borderRadius 14 + duration 260ms`（`action_sheet.dart:163-166`）；工具卡 `r16 + 260ms + pressedScale 1.0`（`chat_message_widget.dart:7276-7281`）。

### B.4.3 实际使用的缩放比例（全库 `pressedScale:` 取值统计）

| 比例 | 次数 | 场景 |
|---|---|---|
| **0.98** | 28 | 卡片、附件卡、对话框按钮——"主"按压比例 |
| **1.0** | 45 | 设置行 / 列表行 / 操作表行：**只变色不缩放** |
| 0.97 | 10 | 设置项、凭证卡 |
| 0.985 | 4 | 气泡附件、提交按钮 |
| 0.99 | 3 | 附件卡 |
| 0.995 | 2 | 搜索服务行 |
| 0.95 / 0.94 | 1 / 1 | 极小按钮（如输入框图片删除，配 140ms） |

出处抽样：`shared/widgets/ios_settings_rows.dart:73-79`（`1.0`）、`features/provider/pages/provider_detail_page.dart:2254`（`0.98`）、`chat_message_widget.dart:2539`（`0.985`）、`chat_input_bar.dart:4525-4529`（`0.94` + 140ms）。

> 复刻结论：**列表/设置行不缩放，只有卡片和强调按钮缩放 0.98**。全库没有任何 0.9 以上的"重压"效果。

### B.4.4 触感分级

`core/services/haptics.dart`：

| 方法 | iOS | Android | 场景 |
|---|---|---|---|
| `light()` | `HapticsType.light` | `HapticFeedback.lightImpact()` | 小 UI 点击、成功打勾 |
| `medium()` | `medium` | `mediumImpact()` | 开关抽屉、toggle |
| `soft()` | `soft` | `selectionClick()` | 卡片点击（`:356-360`） |
| `drawerPulse()` | `soft` | `selectionClick()` | 抽屉专用 |

出处：`haptics.dart:22-32`（light）、`:34-44`（medium）、`:46-57`（soft）、`:59-66`（drawerPulse）。全局开关 `setEnabled`（`:16-19`），各调用点还各自受设置项二次门控（如 `hapticsOnCardTap`）。

## B.5 滑动 / 拖动手势

| 场景 | 实现 | 关键参数 | 出处 |
|---|---|---|---|
| 抽屉 | `GestureDetector.onHorizontalDrag*`（包在位移后的主内容上） | 归一化 progress、365 px/s fling、0.5 吸附 | `interactive_drawer.dart:292-294`、`:387-389`、`:242-273` |
| 底部面板 | `onVerticalDrag*`（把手）+ 内容区 `Listener` 次级拖拽 | 700 px/s、0.7 关闭阈值、220ms 吸附 | `custom_bottom_sheet.dart:241-302, 468-530` |
| 内容区纵向拖拽 | 先做方向判定：`dy.abs() >= dx.abs()` 才接管，避免与横向滑动抢 | `:371-389` | |
| 会话列表左滑删除 | `Dismissible`，**全库仅 1 处** | `direction: endToStart`；红底 `errorContainer` r14；仅移动端启用 | `features/chat/pages/chat_history_page.dart:266-303` |
| 图片查看器 | `PageView` + 自建 drag offset 动画（可下拉关闭） | — | `image_viewer_page.dart:340-350` |
| 滚动回弹 | `BouncingScrollPhysics` **37 处**（`ClampingScrollPhysics` **0 处**）；`physics:` 总命中 50 | 聊天工具面板、侧栏、选择面板等 | `chat_message_widget.dart:6073, 8788`；`side_drawer.dart:469, 3159, 4830` |
| 侧滑返回（自研） | 仅 `primaryDelta > 0` 起手；`didStartUserGesture()` 接管；判定 `velocity.abs() ≥ 700 ? velocity > 0 : controller.value < 0.5`；回弹/提交 220ms `easeOutCubic` | — | `features/settings/pages/settings_search_page.dart:55-94` |
| 可拖拽伸缩面板 | `DraggableScrollableSheet` **15 处**；典型 `initial 0.55 / min 0.3 / max 0.70`、`expand: false`；抓手 40×4 `onSurface`@20% r999 | — | `features/chat/widgets/message_export_sheet.dart:2180-2200` |
| 图片查看器下拉关闭 | `dismissDistance 140`、`dismissVelocity 900`；背景透明度 `1 − min(dy/300, 0.7)`；回弹 190ms / 复位 230ms | — | `features/chat/pages/image_viewer_page.dart:987-989, 1034, 450` |
| 消息列表 | `SuperListView`，**不 reverse**，`cacheExtent 600`，`keyboardDismissBehavior: manual` | — | `message_list_view.dart:1855-1872` |

## B.6 列表项与卡片的悬浮 / 按压

| 元素 | 静止 | 悬停 | 按压 | 出处 |
|---|---|---|---|---|
| 设置行 / 列表行 | `surfaceCard` | `onSurface`@6%（明）/8%（暗） | `onSurface`@8%（明）/12%（暗） | `ios_tactile.dart:110-117`、`:369` |
| 图标按钮 | 透明 | 同上 | 同上（+图标色 lerp 0.35） | `:110-117`、`:86` |
| 操作表行 | `sheetTileColor`（默认 `Colors.transparent`） | k×0.7 | k | `action_sheet.dart:164-166`；`section_card.dart:8-14` |
| 分组卡片 | `surfaceCard` + `hairline` 描边 | 无 | 无（卡片本身不可按） | `section_card.dart:60-87` |
| 工具卡 | `primaryContainer`@25%（暗）/30%（明）为底，或走气泡风格 | — | 有按压（260ms，无缩放） | `chat_message_widget.dart:7282-7292` |
| 侧栏会话条目入场 | — | — | 错峰：`fadeIn(220ms)` + `moveY(begin 6 或 8, 240/260ms, easeOutCubic)`，每项延迟 16ms（置顶区 20ms） | `side_drawer.dart:4237-4245`、`:150` |

**没有任何地方给卡片做 elevation 增长**；`AppShadows.soft`（blur 18 / offset 0,6 / 5% 黑）只在少数浮层用（`design_tokens.dart:3-12`）。

---

# 附录 A：Compose 复刻的最小参数集

| 类别 | 值 |
|---|---|
| 页面 / 卡片 / 填充（明） | `#F7F7F7` / `#FFFFFF` / `#ECECEC` |
| 页面 / 卡片 / 填充（暗） | `#121213` / `#2A2A2B` / `#373738` |
| 描边 | `#0F000000`（明 6% 黑）、`#14FFFFFF`（暗 8% 白） |
| 主色 | `#4D5C92`（明）/ `#B6C4FF`（暗） |
| 字重 | 仅 `Normal` / `Medium`（Android 降级） |
| 正文 | 15.7sp / 行高 1.5 / 助手裸文本无底色 |
| 圆角 | 卡片 12、气泡 16、面板顶 20、输入框 20、药丸 999、自研对话框 14 |
| 时长 | 按压 160–200ms、展开 220–260ms、页面进入 300/360ms、退出 240/280ms、对话框 200ms（对称） |
| 曲线 | 进入 `FastOutSlowInEasing` 近似 `easeOutCubic`；退出 `easeInCubic` |
| 按压反馈 | 无 ripple，换色 `onSurface`@12%（明）/14%（暗），无缩放 |
| 抽屉 | 250ms `easeOutCubic`，跟手拖拽，365 px/s fling，遮罩 0→0.5（首页 0.12），宽度 0.75~0.80 屏宽 |
| 对话框 | 200ms 对称，`scale 0.98→1.0` + 淡入，`scrim`@0.25 |

# 附录 B：证据边界与未验证项

**已实际执行并核对的命令**（均只读）：

```
grep -rn "fontSize: *[0-9.]*"        → 字号分布
grep -rho "BorderRadius.circular(…)"  → 圆角分布
grep -rho "EdgeInsets.all(…)" / "horizontal: …" / "vertical: …" / "SizedBox(height: …)"
grep -rho "Duration(milliseconds: …)" → 时长分布（700+ 命中）
grep -rho "Curves\.[a-zA-Z]*"        → 曲线分布（492 命中）
grep -rn "MaterialPageRoute"          → 135 命中
grep -rn "pageTransitionsTheme"       → 0 命中（关键：无全局过渡覆盖）
grep -rn "PageRouteBuilder|reverseTransitionDuration|transitionDuration"  → 逐条核对
grep -rn "splashColor|highlightColor|splashFactory" → 仅少数处显式关闭涟漪
grep -rn "Dismissible(" → 1 命中
grep -rn "BouncingScrollPhysics"      → 37 命中（Clamping 0 命中）
grep -rn "DraggableScrollableSheet"    → 15 命中
grep -rho "pressedScale: [0-9.]*"      → 12 种取值
sed -n 抽查 20+ 处关键行号原文
```

**未验证 / 需要真机复核的项**：

1. **A.1.3 与 A.1.7 的实算色值**：由 `Color.alphaBlend` 公式复算，源码中不存在这些十六进制字面量。真机取色可能有 ±1 通道误差。
2. **`harmonizeWith` 后的 success/warning 实际值**：依赖 `dynamic_color` 包的 HCT 算法，按主题色变化，未逐主题计算。
3. **平台默认过渡的具体画面**（Android Zoom / iOS Cupertino）未实机录制，只从"无 `pageTransitionsTheme` + 135 处 `MaterialPageRoute`"推得。
4. **180ms 的 77 处**只抽样确认了部分场景，未逐条归类；B.1 表中 180 的场景说明标了"推定"。
5. **`kToolImageCardHeight` 等常量**未展开取值（`chat_message_widget.dart:250-256` 只确认了引用位置）。
6. 桌面端（`lib/desktop/**`）的动效只做了关键字命中，未系统梳理；本次重点在移动端聊天主界面。
7. 未运行 Flutter、未构建、未截图。所有结论来自源码静态阅读。
8. 本报告的 Part B 由两轮独立 grep 交叉核对（一轮主调查、一轮复核）。**凡写入正文的行号与数值，我都在 shell 里用 `sed -n` / `grep -n` 复看过原文**；存在分歧的两处已按原文修正：`BouncingScrollPhysics` 实际 37 处（不是 38 的合并计数）、`DraggableScrollableSheet` 实际 15 处（不是"20+"）。
9. `InkWell` 39 处、`GestureDetector` 339 处、`showDialog` 183 处这类**总量计数**只做了 `grep | wc -l`，未逐处判读语义，不要当作"每种用法各多少"的依据。
10. `pressedScale` 的分布（0.98 / 1.0 / 0.97 …）是字面值统计；调用方是否真的传入、以及在什么组件上，只抽样确认了 5 处（见 B.4.3 出处）。
