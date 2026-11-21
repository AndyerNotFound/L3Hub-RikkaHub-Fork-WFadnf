# 从 ImageToolbox 移植视觉交互（工作笔记）

源工程：`~/WorkSpace/ImageToolbox`（T8RIN，Apache-2.0，HEAD `c9fe6a0c6b`）。
目标工程：本仓库。分支 `port/imagetoolbox`，基线 `aa26efbd7`（`master`）。

## 1 结论先行：导航不移植，视觉分四期

两个工程的导航是两套语义，不存在逐行搬运的可能：

| 维度 | ImageToolbox | 本仓库 |
|---|---|---|
| 引擎 | Decompose 3.6.2 `StackNavigation` + `childStack` + `Children()` | navigation3 1.2.1 `NavDisplay` + `NavKey` back stack |
| 路由 | `sealed class Screen`（`@Serializable`，100+ 项，带 `id/title/subtitle`） | `sealed interface Screen : NavKey`（`@Serializable`，61 项） |
| 分发 | `ChildProvider.kt` 一个 `when` + Hilt assisted factory → `NavigationChild` | `RouteActivity.appNavEntries()` 的 `entry<T>` / `heroEntry<T>` |
| 转场 | `Animations.kt`：`fade(300)+slide(400)+scale(500)` 兜底 + `androidPredictiveBackAnimatableV1` | `ui/navigation/NavTransitions.kt`：容器变换 / 滑入两族，返回是进场的逆 |
| 共享元素 | 无 | 有，且是转场主体（`HeroPage` + `sharedBounds`） |
| tab 栈 | 单栈，tab 只是 `rememberSaveable` 索引 | 单栈；设置三栏靠切栈 |

真正能搬的是手感：曲线、触感、按压轮廓、形状体系、折叠顶栏、预测性返回。

## 2 分期

| 期 | 内容 | 状态 |
|---|---|---|
| P1 | 七条曲线 + 滑入换 `FancyTransitionEasing`；触感强度三档；`rememberShapeByInteraction` / `MorphShape` | 已落地（未编译） |
| P2a | 七种轮廓 + 边角大小 + 边角动画节流；`styledShape` 接进 `Theme.kt` / `CardGroup` / `adaptiveShape` | 已落地（未编译） |
| P2b-开关 | 八档开关（跟随皮肤 / Compose / Pixel / Fluent / iOS / 液态玻璃 / HyperOS / OneUI） | 已落地（未编译） |
| P2b-顶栏 | `EnhancedTopAppBar` 大标题折叠（NATIVE 走 M3 `LargeTopAppBar`，MIUIX 手写等价实现） | 待做 |
| P2c | 四档滑块（MaterialYou / Fancy / Material / HyperOS） | 待做 |
| P2d | 十二档滑动曲线 + 动效时长标尺 | 待做 |
| P4 | 换 Decompose 引擎（见第 4 节） | 待做 |
| P3 | 预测性返回进度 | 放在 P4 之后：Decompose 自带 `predictiveBackAnimation` |

## 3 P2a 的形状体系要注意什么

`shapes` 一动，三处语义同时受影响，必须一起改：

1. `ui/theme/Theme.kt` 的 `shapes = when { fullWidthComponents -> SquareShapes; else -> MaterialTheme.shapes }`
2. `ui/components/ui/CardGroup.kt` 的 `cardGroupItemShape()` 与逐项圆角（相邻项合并）
3. `ui/hooks/HeroAnimation.kt` 的 `adaptiveCornerRadius(HeroSourceCorner)`——容器变换的起始形状

漏掉第三处会看到「展开的第一帧圆角对不上」。另外 Miuix 皮肤有自己的一份
`LocalMiuixCornerRadius` + `MiuixTheme` 圆角，`UiStyle.MIUIX` 下形状要映射过去，
不能只改 Material 的 `shapes`。

两个第三方依赖已按源工程版本引入（`io.github.kyant0:capsule:2.1.3`、
`com.stoyanvuchev:squircle-shape-android:5.4.0`）。

## 4 P4：换 Decompose 的五个子步

1. 依赖：`com.arkivanov.decompose:decompose` + `extensions-compose`。
2. `nav/RootComponent.kt`：`StackNavigation<Screen>` + `childStack(handleBackButton = true)`。
   `Screen` 已经全部 `@Serializable`（61 项，字段只有 `String/Boolean/List<String>`），
   可以直接 `Screen.serializer()`，路由模型不用动，只需摘掉 `: NavKey`。
3. `nav/ScreenProvider.kt`：61 个分支的 `when`，对应源工程的 `ChildProvider`。
4. `ui/context/NavContext.kt` 的 `Navigator` 保留同名 API 当薄封装，调用点尽量不改。
5. hero 层重写：现有 `heroAnimation` / `heroContainer` / `HeroPage` 依赖
   `LocalNavAnimatedContentScope`（= `AnimatedContentTransitionScope`，同时是
   `AnimatedVisibilityScope`）与 `transition.animateDp { it == EnterExitState.Visible }`。
   Decompose 的 `Children` 不给这个 scope，也不给 `EnterExitState`：
   要在每个 child 外面自建 `AnimatedVisibility(visibleState = remember { MutableTransitionState(true) })`
   产出 `sharedBounds` 需要的 scope，并把 `EnterExitState` 判据换成 Boolean 可见性。

### 设置三栏

现状是把**同一个栈切片**给两个 `NavDisplay`：

```kotlin
val middleStack = backStackSnapshot.subList(0, sectionIndex + 1).toList()
val rightStack = backStackSnapshot.subList(sectionIndex + 1, backStackSnapshot.size)
```

Decompose 的 child 身份由 config + 栈位置决定，同一个 child 出现在两个 `Children` 里会重建。
本项目已选定**继续切片**（方案 A），接受两列交界处切换时右列重建、跨列 hero 配对失效。
代价落在：右列滚动位置与输入态、从右列条目进子页的容器变换。要回收这两项就改成
源工程 `FiltersComponent` 那种内嵌 `StackNavigation`（方案 B）。

## 5 验证与回滚

- 验证：Termux 侧禁止编译，每期只做静态检查（括号 / import / API 存在性），真实编译交 CI。
- 回滚：`git checkout master`（本分支基线 `aa26efbd7`）；每期一个 commit。
