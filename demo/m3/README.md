# L3Hub · Material 3 Web Preview

一份**可交互**的 L3Hub 前端演示：不是把 Android 页面截图搬进浏览器，也不是
`demo/index.html` 那版 Compose 复刻的换皮，而是按 Material 3 的表达重新排的一遍。

- 只用 M3：48 个 color roles、type scale、shape、elevation、state layer、motion；
- 真组件：界面骨架由 [`@material/web`](https://github.com/material-components/material-web) 2.5.0 的
  `md-*` 组件搭起来，颜色由官方 `material-color-utilities` 从种子色生成；
- 无构建：ES module + import map，`python3 -m http.server` 就能跑；
- 依赖已 vendor 到 `vendor/`，断网也能开（字体回退到系统字体）。

> 演示数据、模型名、配额、终端输出都是编的，不连接任何真实账户；
> 这个目录不会被打进 APK，也不改动 `demo/index.html` 那版旧原型。

## 跑起来

```bash
cd demo/m3
python3 -m http.server 8799
# 浏览器打开 http://127.0.0.1:8799/
```

需要支持 ES module、`color-mix()`、`backdrop-filter` 的现代浏览器。
菜单优先用 Popover API，不支持时会退回 `fixed` 定位。

## 五个路由

| 路由 | 内容 |
| --- | --- |
| `#/chat` | 会话列表 + 顶栏三分区（对话 / 任务 / 文件）+ 详情栏；三档布局，抽屉 / rail / 底部导航 |
| `#/assistants` | 助手卡片、标签筛选、详情与新建（真的会加到列表里） |
| `#/models` | 提供商配额、模型清单筛选、七天用量柱状图 |
| `#/workspace` | 文件树、可输入的终端、任务树与权限开关 |
| `#/settings` | 颜色模式、调色板、九种配色风格、对比度、圆角、模糊、动效 |

## 可以点的东西

- **发消息**：Enter 发送；回复会流式写入，带推理卡、工具卡、子代理回传、用量行；
  “停止”会保留已经写出的内容。
- **重新生成 / 复制 / 分支 / 更多**：都在助手消息的操作行里；复制走剪贴板 API。
- **工具卡**：展开入参与输出，或打开工具详情对话框跳到工作区。
- **顶栏标签**：Inline 主标签栏——不显示图标、只显示标签、Gravity Fill（三等分
  整宽）、底部 3px 整宽圆角线指示器；Elastic 播放时指示器会先拉长再收回。
  面板切换走 View 同轴 Y 转场（不支持时退回 CSS 动画）；`←` `→` 也能切。
- **详情栏**：概览（助手、供应商配额、上下文、本轮工具权限）；
  任务与文件移到了顶栏的另外两个分区。
- **会话历史**：分组可以折叠，会话用缩进 + 竖线挂在组下面；折叠状态存
  `localStorage`。置顶 / 本周 / 上周 / 更早之外，还有默认收起的「已归档」。
- **双击会话**：WebView 里长按不可靠，所以用双击 / 双点代替。Menu 带图标：
  多选 / 移动到 / 复制到 / 重新生成标题 / 归档 / 删除。
- **多选**：进入后会话前出现勾选框，顶部栏变成批量操作——移动到、复制到、
  归档、重命名、删除，全选与退出都在同一行。
- **移动到 / 复制到**：底部 Bottom Sheet；目标助手是单选 Chips（必须选一个），
  文件夹是 Expandable List，可以「保留原文件夹」或指定分类。
- **归档**：会话进入「已归档」分组，详情顶上出现横幅，输入区换成锁定条；
  解除归档之前不能继续对话。复制出来的副本默认不归档。
- **主题**：设置里换调色板、风格、对比度、圆角、模糊、动效，全部实时生效并写入
  `localStorage`；“自定义种子色”会打开系统取色器。
- **命令面板**：`⌘K` / `Ctrl+K`；搜索会话、助手或命令，`↑↓` 选择，`Enter` 打开。
- **助手切换**：底部助手行会新建一个该助手的会话；新助手对话框会真的创建卡片。
- **模型**：按提供商与能力筛选；点“测试选中模型”会给出延迟。
- **工作区**：点目录展开、点文件看只读预览；终端可以输入 `ls`、`tree`、`git status`、
  `node -v`、`pnpm build`、`clear`、`help`。
- **关于页**：导出备份会真的下载一个 JSON；恢复默认外观会重置整套主题。

## 三档布局

| 视口 | 壳层 | 会话列表 | 详情栏 |
| --- | --- | --- | --- |
| ≥ 1500 | 272 抽屉 | 296 常驻 | 344 常驻 |
| 1180–1499 | 272 抽屉 | 296 常驻 | 可召回浮层 |
| 840–1179 | 88 rail | 可召回浮层 | 可召回浮层 |
| < 840 | 抽屉 + 底部导航 | 浮层 | 浮层（满宽） |

## 会话历史怎么排

```
置顶                    ⌄  2      ← 点标题折叠整组
  ├ 工作区重构计划
  └ M3 前端演示
本周                    ⌄  4
  ├ 京都三日：不赶路的版本
  └ …
已归档                  ›  1      ← 默认收起；双击打开菜单可解除归档
```

缩进和左侧竖线表示「这些会话属于这一组」，收起后只留组标题与计数。
归档会话在列表里带归档图标，选中后不能发送；「移动到」会改助手与文件夹，
「复制到」会生成一条标题带 `· 副本` 的新会话，原会话不动。

## M3 的落点

- 颜色：`js/theme.js` 用 HCT 生成 48 个 `--md-sys-color-*`，界面里不写十六进制
  （品牌兔标的黑白除外）。`@material/web` 的组件读的是同一批变量。
- 字体：Roboto Flex / Noto Sans SC，type scale 直接落成 utility class。
- 形状：`--l3-radius` 由设置驱动，卡片、气泡、输入框一起变；pill 保持 M3 的 full。
- 层级：elevation 0–5 与 state layer 由 `tokens.css` 定义，不用随手阴影。
- 动效：emphasized / standard easing + 四档时长；`data-motion="off"` 时全部收掉。
- 布局：M3 的 navigation drawer / rail / navigation bar 三形态，列表-详情模式，
  搜索用 search bar 的形态，输入区用 surface-container-high + hairline。
- 标签：主标签栏按 M3 tabs 的组合来做——labels on / icons off / gravity fill /
  inline / full-width indicator / rounded line / elastic；指示器是贴底的 3px
  圆角线，宽度等于整格，移动时用 Web Animations 走一段拉伸关键帧。
- 弹层：Bottom Sheet 从底部进入，目标助手用单选 Chips，文件夹用可展开列表。

## 文件结构

```
demo/m3/
  index.html          壳 + import map + 启动页
  tokens.css          reset、M3 字阶/形状/层级/动效、md-* 组件统一
  app.css             壳层、五个页面、响应式、对话与归档、Bottom Sheet
  assets/mark.svg     品牌兔标（黑底白兔，深浅色都成立）
  js/
    app.js            入口：组件注册、壳、路由、命令面板、全局事件
    core.js           共享状态、主题应用、会话/助手选择
    theme.js          HCT 色板生成（6 预设 × 9 风格 × 明暗 × 对比度）
    data.js           演示数据（会话、助手、模型、工作区、用量）
    ui.js             Markdown 子集、对话框、菜单、Snackbar、图表、图标
    icons.js          Material Symbols 路径（123 枚）
    views/            chat / assistants / models / workspace / settings
  vendor/             只保留会被加载的 @material/web 与 lit 运行时
```

## 键盘

| 键 | 动作 |
| --- | --- |
| `⌘K` / `Ctrl+K` | 命令面板 |
| `Enter` | 发送消息 / 打开命令面板当前项 |
| `Shift+Enter` | 输入框换行 |
| `←` `→` / `Home` `End` | 切换顶栏分区（焦点在标签上时） |
| `Esc` | 关抽屉 / 关浮层 / 关菜单 |

## 依赖与许可

运行时依赖清单与版本见 [`vendor/SOURCES.md`](vendor/SOURCES.md)，许可证在
[`vendor/licenses/`](vendor/licenses/)。全部为 Apache-2.0 / BSD-3-Clause / 0BSD。

没有执行 Gradle、npm build 或任何编译；vendor 只是发布包里的 JS 文件搬运，
以及 `material-color-utilities` 相对 import 的 `.js` 扩展名修正。

## 验证过什么

在这个 Termux 环境里没有可用的图形浏览器，所以做的是静态与 DOM 级验证：

- 所有 `js/**/*.js` 通过 `node --check`；
- 自写的 import 检查器确认 demo 与 vendor 的每条相对/裸 import 都能落到文件；
- 五个视图在 Node 里用桩 DOM 渲染，输出交给 parse5 解析，0 个 parse error；
- 用 jsdom 跑通：启动 → 五个路由 → 筛选 → 工具卡展开 → 设置换色/圆角/密度 →
  发消息与流式回复 → 命令面板过滤；
- 会话专项：三分区切换、分组折叠、双击菜单、进入多选、批量复制、归档锁定与
  解除、批量删除、批量重命名，全部有断言；
- 主题矩阵（6 预设 × 9 风格 × 明暗 × 对比度）全部生成成功。

真机上的视觉、字体度量与手势仍以浏览器实际表现为准。
