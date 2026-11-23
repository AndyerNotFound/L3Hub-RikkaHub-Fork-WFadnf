# Kelivo 化移植方案（规格层换肤）

依据：`docs/kelivo-design-spec.md`（kelivo 源码静态调研，734 行）+ L3Hub 现有 ui 层结构。
原则：**toolbox-ui 内核与全部 Kotlin 底层不动，只换设计规格层**。所有数值出处见设计报告。

## 0. 目标长相

kelivo **移动端默认态**（legacy surface、defaultStyle 气泡、system 主题）：分层皮肤 / frosted / solid 只作为后续可选设置，首期不复刻。

## 1. Token 对照表（kelivo 值 → L3Hub 落点）

| 规格项 | kelivo 值（移动端默认态） | L3Hub 落点 | 改动方式 |
|---|---|---|---|
| 页面底色 | 明 `#F7F7F7` / 暗 `#121213` | `ToolboxColorScheme.kt` | 种子色 preset（`PalettePresets.kt` 新增 kelivo 组） |
| 卡片色 | 明 `#FFFFFF` / 暗 `#2A2A2B` | 同上 | surfaceContainer 五档**覆盖**为 legacy 实算值（见下），不用 M3 默认 tone 差 |
| 填充色 | 明 `#ECECEC` / 暗 `#373738` | 同上 | 同上 |
| 五档容器 | 明 0/+2/+3/+7/−11 相对 page；暗 white@3%→4.5%→6%→9% 步进 | 同上 | 在引擎输出后按 ladder 公式覆盖，公式照 `surface_ladder.dart:63-90` |
| hairline | 明 6% 黑 / 暗 8% 白 | 同上 outline 档 | alpha 覆盖 |
| 主色 | 明 `#4D5C92` / 暗 `#B6C4FF`（另 8 套调色板见报告 A.1.2） | `PalettePresets.kt` | 新增 9 组种子色 preset |
| 字重 | 保留全字重（决策 3：不照抄 kelivo 两档降级） | `Type.kt` | 不动 |
| 正文字号 | 助手 15.7 / 用户 15.5 / 行高 1.5、1.45 | `ChatFont.kt` + 聊天正文样式 | 直接替换 |
| 设置行字号 | 主 15 / 副 13 / 尾 12 | `AppSettingsList.kt` 行内样式 | 直接替换 |
| 圆角 | 卡 12 / 强调卡 18 / 气泡·菜单 16 / 面板顶·输入框 20 / 行 14 / 图标按钮 8 / 药丸 999 | `ToolboxIconShapes.kt` + `ToolboxSettingsBridge` 档位重标定 | 档位值替换 |
| 动效曲线 | 进/展开/按压 `easeOutCubic`，退/收起 `easeInCubic`（配对镜像） | `MotionSpecs.kt` / `MotionEasing.kt` | 新增 KelivoMotionScheme |
| 动效时长 | 按压 160–200 / 展开 220–260 / 页面进 300 退 240 / 对话框 200（对称） | 同上 | 同上 |
| 按压反馈 | 无涟漪；底色混 `onSurface` 明 12% / 暗 14%，200ms；**列表行不缩放**，卡片 0.98 | `StyledComponents.kt` + toolbox-ui 按压档 | 换反馈实现参数 |
| 对话框壳 | 圆角 14 + 描边 1dp + scale 0.98→1.0 + 200ms 对称 + scrim@0.25 | `FullWidthComponents.kt` / 对话框组件 | 照刻（不是 M3 默认 28） |
| 间距 | 4dp 网格；页边距/卡片内边距 12，元素间隙 8，区块 16 | 各页面局部 | 分阶段顺手对齐 |
| 用户气泡 | `primary`@8%（明）/15%（暗），r16，内边距 12，maxWidth 屏宽×0.75，无尾巴 | 聊天自绘气泡组件 | 直接替换 |
| 助手气泡 | 保留双边气泡（决策 2），仅换颜色/圆角参数 | 同上 | 参数替换 |
| 输入框 | r20、不透明度 0.8236/0.7396、叠 `primary`@2%/`onSurface`@7%、描边 outline@20% 1dp、毛玻璃 sigma 14 | 输入栏组件 | 直接替换 |
| 抽屉手感 | 250ms `easeOutCubic`、跟手拖拽、fling 365px/s、0.5 吸附、遮罩 0→0.5（首页 0.12） | Salt Sheet / 侧栏自绘**只调参数，不动结构** | 参数灌入（保留现有手势） |
| 长按菜单 | 宽 220、r16、150ms 纯淡入（无缩放无位移）、scrim@0.08、毛玻璃 sigma14 | 气泡长按菜单 | 照刻 |
| 滚动物理 | `BouncingScrollPhysics`（37 处，无 Clamping） | 滚动容器 physics | 全局替换 |

不照抄项：kelivo 手写 route 里零散的 300/240、360/280 等不一致值，统一为一套（进 300 / 退 240，见决策点 1）。

## 2. 决策（2026-11-23 已拍板）

1. **进退对称性 → kelivo 原味非对称**：进 300ms `easeOutCubic` / 退 240ms `easeInCubic`，镜像配对；覆盖原「同参同曲线」偏好。
2. **助手气泡 → 保留双边气泡**：不采用 kelivo 默认裸文本，聊天布局维持现状，只换颜色/圆角参数（用户气泡 primary@8%/15%、r16 照常生效）。
3. **字重 → 保留全字重**：不照抄 kelivo 的 Android 两档降级，`Type.kt` 不动。

## 3. 实施顺序（每阶段一次 CI 编译 + Canary 安装验证）

1. **规格层**（一次提交，全局生效）：PalettePresets kelivo 种子色组、ToolboxColorScheme 五档覆盖、Type.kt 字重映射、MotionSpecs KelivoMotionScheme、ToolboxSettingsBridge 档位重标定、滚动物理。
2. **全局组件**：StyledComponents 按压反馈（无涟漪换色）、AppSettingsList 行样式与 SectionCard 化（kelivo 外层大卡 + hairline 描边，替代 ImageToolbox 相邻行拼圆角组织）、对话框壳 14+0.98。
3. **聊天页**：气泡（用户 primary@8%/15%、r16，双边保留）、输入框（r20+毛玻璃）、消息列表（max 860 + gutter）、长按菜单。
4. **其余页面逐个过**：history / favorite / stats / imggen / translator / memory / search / setting 各页间距与字号对齐（规格层生效后多数自动达标，此阶段收尾查漏）。

抽屉/侧栏：只灌参数不改动效结构，玻璃自绘与拖动手势原样保留。
toolTheme 档位设置页 UI（toolbox 迁移遗留）并入阶段 2 一起暴露。

## 4. 验证策略

- Termux 禁 Gradle，编译走 CI（`./gradlew :app:compileDebugKotlin`）+ Canary 渠道安装（版本号不变）。
- 每阶段一次编译安装，共约 4 次；实算色值（卡片/填充/气泡叠色）安装后真机取色复核一次。
- 每阶段完成后逐项对照本表验收，不留「已改未验」。
