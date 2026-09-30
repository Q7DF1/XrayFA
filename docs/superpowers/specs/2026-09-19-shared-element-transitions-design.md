# Spec: Shared Element Transitions 转场体系

日期：2026-09-19
状态：待实施

## 1. 目标

把当前的 Decompose `stackAnimation(slide())` 横向滑动替换为 Material 3 容器变换（container
transform）体系：点击一个入口元素时，该元素的边界连续扩张成对应的全屏页面；返回时反向回缩。

覆盖的转场：

1. 节点列表行 → 全屏节点编辑
2. 浮动底栏放大镜图标 → 全屏搜索
3. 首页齿轮图标 → 全屏设置
4. Config 顶栏溢出按钮 → 全屏订阅管理
5. 设置页的行 → Apps / 日志 / 路由三个子页
6. Config 顶栏 Edit 图标 → 新建节点
7. 溢出菜单 / 订阅页按钮 → 二维码扫描

非目标（明确排除）：

- 不做内容级 shared element（例如把节点行的备注文字与编辑页的备注输入框配对）。只做容器级变换 +
  页面内容淡入，这也是 M3 container transform 的标准形态。
- 不改 `ChildPages` 的 tab 横向翻页动画（`PagesScrollAnimation.Default` 保留）。
- 不动 `androidApp` 里那套已标 `@Deprecated` 的 Navigation3 屏幕（`SettingsScreen`、`AppsScreen`、
  `LogcatScreen`）。它们编译在 androidx Compose BOM 上，不受本次 CMP 升级影响。
- 不做与本目标无关的重构。

## 2. 核心约束（决定了整体架构）

### 2.1 shared element 要求两端处于同一个动画作用域

Compose 的 `sharedBounds` / `sharedElement` 需要一个 `AnimatedVisibilityScope`。Decompose 只从
`extensions-compose-experimental` 模块的 `ChildStack` 暴露该作用域（3.2.0-alpha03 起；3.4.0-alpha04
起升级为 `StackAnimationScope : AnimatedVisibilityScope`，额外提供 `stackAnimationDirection`）。
稳定版 `Children` + `stackAnimation` 不暴露、且不会暴露。`ChildPages` 暴露的是 `PagerScope`，拿不到。

当前 `RootContent` 的结构是并列的：`ChildPages`（Config / Home 两个 tab）与 `Children`（全屏覆盖栈）
是同一个 `Box` 的兄弟节点。按此结构，7 个目标转场里有 6 个跨越了 `ChildPages` / `ChildStack` 的边界，
只有第 5 项（设置页 → 子页）两端同在栈内。

**决策：改为嵌套结构** —— 把 `ChildPages` 移入 `ChildStack` 的 `Idle` 分支，全 app 只有一个
`SharedTransitionLayout`。所有转场都走 Decompose 官方支持的「同一 `ChildStack` 的两个子节点之间配对」
路径，不使用 `sharedElementWithCallerManagedVisibility` 逃生口。

已评估并否决的替代方案：

- 起点侧用 `sharedElementWithCallerManagedVisibility`：改动小、滚动状态天然保留，但需手动管理
  `visible` 并在转场结束后把退场副本移出组合树，属于走得比较少的路，调试成本高。
- 给起点元素包一层本地 `AnimatedVisibility` 来凭空造作用域：起点与终点是两个独立 transition，
  预测返回手势拖到一半时两侧不同步。

### 2.2 shared element 穿不过 `Dialog`

`SharedTransitionLayout` 的 overlay 只在自己那棵组合树内生效。当前搜索是 `SharedSearchChrome` 里的
一个全屏 `Dialog`（独立窗口），且完全没有动画——只是 `expanded` 布尔值一翻。

**决策：搜索改为 Decompose 栈目的地** `RootStackConfig.Search`，与其它全屏页共用同一套机制，
顺带白拿返回键与预测返回手势。

### 2.3 订阅与二维码只能从 Popup 菜单进入

`DropdownMenuItem` 位于 Popup 内，同样穿不过。

**决策：锚定到触发菜单的那个 `IconButton`**。菜单先 dismiss，再导航；容器从右上角那个图标扩张。
不新增可见入口，不改 UI 结构。

## 3. 依赖升级

`gradle/libs.versions.toml`：

```toml
composeMultiplatform = "1.9.3"   # was 1.7.3；基于 Jetpack Compose 1.9.4
decompose = "3.5.0"              # was 3.2.2
essenty = "2.5.0"                # was 2.3.0（Decompose 3.5.0 要求）
materialIcons = "1.7.3"          # 新增；显式固定
```

`shared/build.gradle.kts` 的 `commonMain`：

- 新增 `com.arkivanov.decompose:extensions-compose-experimental`
- 显式加 `org.jetbrains.compose.material:material-icons-extended:1.7.3` 与
  `org.jetbrains.compose.material:material-icons-core:1.7.3`
- `compose.material3` 保持不动（详见第 7 节：刻意不引入更新的 material3）

保持不变：Kotlin 2.1.10、KSP 2.1.10-1.0.31、AGP 8.10.0、`appfunctions` 1.0.0-alpha08、
`androidApp` 的 androidx Compose BOM 2026.03.00。

升级依据与注意点：

- CMP 1.9.3 的最低 Kotlin 要求是 2.1.0，当前 2.1.10 已满足，因此 Kotlin / KSP / AGP 链条无需变动，
  `libs.versions.toml` 里「appfunctions 必须留在 alpha08」的约束也不受影响。
- CMP 1.9 起 Material3 采用独立版本线，`compose.material3` 指向 Material3 1.8.2（相对当前仍是升级）。
  刻意不引入更新的 material3，理由见第 7 节。
- CMP 1.8.2 起移除了 `material-icons-core` 的隐式传递依赖，`compose.materialIconsExtended` 也已
  标记废弃（固定在 1.7.3 且不再更新）。显式声明这两个产物即可，**代码中的 `Icons.*` 用法完全不用迁移**。
- Compose 1.8 对 shared transition API 做过参数重命名。因为升到 1.9.4，统一使用新名：
  `sharedContentState =`、`placeholderSize`、`ResizeMode.ScaleToBounds(...)`
  （大写开头且必须调用；小写的 `scaleToBounds()` 在本项目锁定的版本上不存在，已由编译探针实测确认）。
  `@OptIn(ExperimentalSharedTransitionApi::class)` 在 1.9.4 仍需要（1.10 才转正）。

## 4. `RootContent` 结构重构

```kotlin
SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
        ChildStack(                                   // extensions-compose-experimental
            stack = component.stack,
            animation = stackAnimation(
                animator = fade() + scale(),
                predictiveBackParams = {
                    if (!platformHooks.usesDecomposePredictiveBack) {
                        null
                    } else {
                        PredictiveBackParams(
                            backHandler = component.backHandler,
                            onBack = component::navigateBack,
                            animatable = ::materialPredictiveBackAnimatable,
                        )
                    }
                },
            ),
        ) { child ->
            CompositionLocalProvider(LocalStackAnimationScope provides this) {
                when (val instance = child.instance) {
                    RootComponent.StackChild.Idle -> IdleContent(...)   // ChildPages + 浮动底栏
                    RootComponent.StackChild.Settings -> SettingsTabScreen(...)
                    is RootComponent.StackChild.NodeEdit -> ...
                    RootComponent.StackChild.Search -> SharedSearchScreen(...)
                    // Subscriptions / QrScanner / Apps / Logcat / RouteSettings
                }
            }
        }
    }
}
```

要点：

- 底座动画改为 `fade() + scale()`。容器变换承担主要视觉，底座必须"安静"，否则页面横向滑动与容器
  扩张两个运动会互相打架。
- `predictiveBackParams` 返回 `null` 即关闭手势，替代现在 `if (usesDecomposePredictiveBack)` 的
  `animation =` 二选一分支。必须显式传 `animatable = ::materialPredictiveBackAnimatable`，
  新 API 默认不带 M3 那种缩小 + 圆角 + 侧移的观感。
- **浮动底栏搬进 `Idle` 分支内部**。理由：底栏里的放大镜图标是搜索转场的起点，而它现在被
  `AnimatedVisibility(showBottomNav)` 管着，`!stackIdle` 时会被直接移出组合树，起点在转场刚开始就
  消失。搬进 `Idle` 后，图标与搜索目的地成为同一 `ChildStack` 的两个子节点（官方支持的配对场景），
  底栏靠 `animateEnterExit(exit = slideOutVertically + fadeOut)` 得到与容器扩张同步的滑出，
  退场期间生命周期自动正确。
- 底栏需加 `renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)`。一是避免被提到 overlay 的
  共享容器盖住，二是把它从 `Idle` 的 `scale` graphicsLayer 中提出来，避免跟着整页缩放。
- 因底栏迁入 `Idle`，`stackIdle` 与 `searchExpandedCoversNav` 两个状态变量删除。

## 5. 状态提升

`ChildStack` 只组合活跃子节点，`Idle` 在压栈后会被释放，其中的 `remember` 全部丢失。以下状态上移到
`RootContent`（始终存活）：

| 状态 | 现位置 | 必须提升的原因 |
|---|---|---|
| `listState: LazyListState` | `ConfigTabScreen` 内 `rememberLazyListState()` | 硬依赖。返回时列表若跳回顶部，容器回缩的目标边界就是错的，会看到方块飞向屏幕外 |
| Config 页 `TitleCollapseState` | `SharedListScaffold` 内 `rememberTitleCollapseState()` | 否则每次返回标题重新展开，列表内容跟着跳一下 |
| `pendingOverlayScroll` | `ConfigTabScreen` 内 | 搜索选中结果后要滚动定位，跨了一次导航 |

不提升：`shareNode`、`showBugReport`。它们是瞬态对话框开关，全屏页打开时本就该关闭。
`HomeLayouts` 的 `rememberScrollState()` 也不提升 —— 首页只有 hero 与两三张卡片，几乎滚不动。
（已确认 `SharedConfigFilterBar` 不含任何 `remember` 状态，筛选栏不受影响。）

另需配套修一处会被这次重构暴露出来的既有问题：`HomeConnectButton` 的 `LaunchedEffect(isConnected)`
没有首帧保护，首次组合时也会执行。`Idle` 被释放后每次返回首页都会重放一次 1→1.2→1 的弹跳。
加一个首帧标记即可，且因为 `remember` 在新组合中同样重置，正好能区分「新组合」与「真的切换了连接状态」：

```kotlin
var skipInitial by remember { mutableStateOf(true) }
LaunchedEffect(isConnected) {
    if (skipInitial) { skipInitial = false; return@LaunchedEffect }
    // 原有弹跳动画
}
```

实现方式：新增 `ConfigTabChromeState` holder 类与 `rememberConfigTabChromeState()`，持有上述三项，
在 `RootContent` 中 remember 一次后往下传。`SharedConfigSection` 已有 `listState` 参数，直接传即可；
`SharedListScaffold` 增加可选参数 `titleCollapseState: TitleCollapseState? = null`，为 null 时保持
现有的内部 `remember`，其它调用方零改动。

## 6. 转场编排

### 6.1 Key 模型

`ui/transitions/SharedKeys.kt`：

```kotlin
data class SharedContainerKey(val destination: String)
```

用 data class 而非裸字符串：官方明确建议，字符串拼错会导致静默不匹配（没有动画，也没有报错）。
因为本次只做容器级变换（见第 1 节非目标），key 只需 destination 一个维度；将来若要加内容级配对，
再补一个 element 维度即可。

`destination` 取稳定串：`"node-edit-{id}"`、`"node-edit-new"`、`"settings"`、`"subscriptions"`、
`"search"`、`"apps"`、`"logcat"`、`"route"`、`"qr"`。同一时刻 `LazyColumn` 中只有一行匹配某个
`node-edit-{id}`，因此唯一。

### 6.2 作用域下传

`ui/transitions/TransitionScopes.kt` 提供两个 CompositionLocal：`LocalSharedTransitionScope` 与
`LocalStackAnimationScope`。不用参数透传，是因为「齿轮 → 设置」「溢出按钮 → 订阅」「Edit → 新建」
三个转场的起点要穿过 `ChildPages` 的 `PagerScope` 再套两层 Screen 函数，透传会污染七八个函数签名。

### 6.3 统一的修饰符编排

封装成 `ui/transitions/XrayMotion.kt` 里的一个扩展函数，两端共用同一实现，避免手抄出错：

```kotlin
@Composable
fun Modifier.sharedContainer(
    destination: String,
    shape: Shape,
): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val avScope = LocalStackAnimationScope.current ?: return this
    return with(sharedScope) {
        this@sharedContainer
            .sharedBounds(
                sharedContentState = rememberSharedContentState(SharedContainerKey(destination)),
                animatedVisibilityScope = avScope,
                enter = fadeIn(XrayMotion.effectsSpec()),
                exit = fadeOut(XrayMotion.effectsSpec()),
                resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(),
                boundsTransform = XrayMotion.containerBoundsTransform(),
            )
            // 背景与裁剪必须在 sharedBounds 之后，才会跟着元素进入 overlay
            .background(MaterialTheme.colorScheme.surface, shape)
            .clip(shape)
    }
}
```

作用域缺失时返回原 `Modifier`（而不是抛异常），这样任何尚未接入的调用点只是没有动画，不会崩。

强制规则（不遵守就会出现元素"跳"一下）：

- 两端的 destination 必须一致，修饰符相对 `sharedBounds` 的顺序也必须一致 —— 由上面的封装保证。
- 尺寸与 padding 修饰符放在 `.sharedContainer()` 之后。放在之前会参与初始/目标边界的推导，
  两端不一致就跳。
- 目的地页面内部**不需要**再逐个加 `animateEnterExit`。本设计里共享容器挂在屏幕根节点上，
  `sharedBounds` 自带的 `enter = fadeIn()` / `exit = fadeOut()` 已经覆盖整棵子树，正文会随容器
  一起淡入。官方示例中之所以要对正文单独加 `animateEnterExit`，是因为那些场景里只有局部元素
  （例如一张图）参与共享，屏幕其余部分不在 `sharedBounds` 之内。
  唯一需要 `animateEnterExit` 的是浮动底栏 —— 它被 `renderInSharedTransitionScopeOverlay`
  提到了 overlay，脱离了根容器的淡入淡出（见第 4 节）。
- 仅当子内容会溢出容器边界时才额外传 `clipInOverlayDuringTransition = OverlayClip(shape)`；
  默认的 `ParentClip` 配合上面的 `.clip(shape)` 已足够。

### 6.4 转场清单

| # | 转场 | 起点挂载处 | 作用域来源 |
|---|---|---|---|
| 1 | 节点行 → 编辑 | `SharedConfigNodeRow` 根 `Box` | Idle 子节点 ↔ NodeEdit 子节点 |
| 2 | 放大镜 → 搜索 | `XrayFloatingNav` 的 `trailingContent` | 同上（底栏已在 Idle 内） |
| 3 | 齿轮 → 设置 | `HomeTopBar` 的 `IconButton` | 同上，经 CompositionLocal 穿过 `PagerScope` |
| 4 | 溢出按钮 → 订阅 | `SharedConfigImportMenu` 的 `IconButton` | 同 3（菜单先 dismiss 再导航） |
| 5 | 设置行 → Apps/日志/路由 | `SharedSettingsFieldRow`，走已有的 `appsModifier` / `logcatModifier` / `routeModifier` | Settings 子节点 ↔ 子页子节点 |
| 6 | 顶栏 Edit → 新建节点 | Config 顶栏 `IconButton` | 同 3 |
| 7 | 菜单 / 订阅页按钮 → 二维码 | 对应触发 `IconButton` | 同 4 |

第 5 项零结构改动：`SharedSettingsPlatformSection` 已经预留了三个 modifier 参数（此前那套
Navigation3 屏幕就是拿它们挂 shared element 的）。

### 6.5 形状处理

- 节点行 → 编辑：行与全屏都是直角矩形，无需形状变化。**节点列表现有的平铺样式不用改**。
  `sharedBounds` 会同时渲染两侧，进场那侧（编辑页）自带不透明背景，所以观感是一个矩形从行的位置
  纵向涨满屏幕。
- 小图标起点（转场 3/4/6/7）：圆角从 20dp 插值到 0dp。Compose 不支持自动形状插值，必须手写。
  做法是借 `AnimatedVisibilityScope.transition` 这个公开的 `Transition<EnterExitState>` 驱动圆角：

  ```kotlin
  val corner by avScope.transition.animateDp(transitionSpec = { XrayMotion.spatialDp() }) { state ->
      if (state == EnterExitState.Visible) 0.dp else 20.dp
  }
  ```

  把 `RoundedCornerShape(corner)` 传给 `sharedContainer(shape = ...)` 即可，`.clip()` 在
  `sharedBounds` 之后，所以圆角在 overlay 渲染期间同样生效。
- 所有转场的 `ContentScale` 两端保持一致（该属性不参与动画，会直接吸附到终值）。

## 7. 动效令牌

`ui/transitions/XrayMotion.kt`。采用 M3 Expressive 方案的数值，但**硬编码为 `spring()` 常量，
不使用 `MaterialTheme.motionScheme`**。

| 用途 | 对应 M3 令牌 | 阻尼比 / 刚度 |
|---|---|---|
| 全屏容器扩张的 bounds | expressive slow spatial | 0.8 / 200 |
| 所有淡入淡出 | effects default | 1.0 / 1600 |
| 底栏滑出、圆角插值等局部位移 | expressive default spatial | 0.8 / 380 |
| 底座 `fade() + scale()` | effects default | 1.0 / 1600 |

`boundsTransform` 需要 `FiniteAnimationSpec<Rect>`，spring 的 `visibilityThreshold` 取
`Rect.VisibilityThreshold`。第 6.5 节的圆角插值用表中第三行的参数。

**为什么不用 `MotionScheme`**：本项目已有两处明确记录的教训 —— `SharedModalBottomSheet` 避开
`ModalBottomSheet`、`SharedSearchChrome` 避开 `SearchBar`，注释均写明原因是「CMP material3 与
androidx material3 二进制不匹配导致 Android 上 NoSuchMethodError」。根因是 `androidApp` 同时依赖
androidx Compose BOM，Android 运行时由 androidx material3（当前 1.5.0-alpha15）胜出，而 `:shared`
是按 JB material3 编译的。`MaterialTheme.motionScheme` 与 `MotionScheme.expressive()` 正属于这类较新
material3 API（`MotionScheme.expressive()` 见于 1.5.0-alpha2x，比 alpha15 更新），属于同一风险类别。

硬编码 `spring()` 只依赖 `androidx.compose.animation.core` —— 与提供 `sharedBounds` 的是同一个产物，
完全不碰 material3 命名空间，且视觉结果与令牌方案等价。同时可以不引入 `jbMaterial3` 依赖，
少一个变动面。

佐证：项目当前就是 CMP 1.7.3 编译、运行在 BOM 2026.03.00 的 androidx Compose 上，跨 5 个版本正常工作；
已踩的坑全部集中在 material3，animation / foundation / ui 一直稳定。

选 spring 而非 tween 的理由：预测返回手势会把进度直接喂给同一个 transition，spring 被打断和反向时
自然，tween 会顿。

## 8. 搜索改造

唯一涉及 component 层的改动。

- `RootStackConfig` 增加 `@Serializable data object Search`
- `RootComponent` 增加 `openSearch()` 与 `StackChild.Search`
- `DefaultRootComponent.openSearch()` 走现有的 `bringOrPush(Search)` 并切到 Config tab
  （与 `openSubscriptions()` 同构）
- 查询状态本就在 `ConfigComponent`（`onSearch` / `searchQuery`），新屏幕复用现有 component，
  **不新增状态源**
- 新增 `SharedSearchScreen`：接管原 `ActualConfigSearchFab` 的防抖逻辑与结果列表，根容器挂
  `"search"` 的共享容器修饰符
- `SharedSearchChrome` 去掉 `Dialog`，改为普通全屏 `Surface`；折叠态的圆形触发器随之删除
  （Config 页本来就传 `showCollapsedTrigger = false`）。**继续使用 `OutlinedTextField`，
  不得改用 Material3 `SearchBar` / `DockedSearchBar`** —— 该文件原有注释记录的 NoSuchMethodError
  约束依然有效
- 结果选中后：写入 `pendingOverlayScroll` → `navigateBack()`
- 删除以下参数与状态：`ConfigTabScreen` 的 `forceCollapseSearch`、`openSearch`、
  `onOpenSearchConsumed`、`onSearchExpanded`，以及 `RootContent` 的 `requestOpenSearch`、
  `searchExpandedCoversNav`

## 9. 文件改动清单

新增：

- `shared/.../ui/transitions/SharedKeys.kt` —— key 模型与 destination id
- `shared/.../ui/transitions/XrayMotion.kt` —— 动效令牌、`containerBoundsTransform`、共享容器修饰符封装
- `shared/.../ui/transitions/TransitionScopes.kt` —— 两个 CompositionLocal 及取值辅助
- `shared/.../ui/ConfigTabScreen.kt`、`HomeTabScreen.kt`、`SettingsTabScreen.kt` —— 从 `RootContent` 拆出
- `shared/.../ui/config/ConfigTabChromeState.kt` —— 状态 holder
- `shared/.../ui/config/SharedSearchScreen.kt` —— 新的搜索目的地

修改：

- `gradle/libs.versions.toml`、`shared/build.gradle.kts`
- `shared/.../ui/RootContent.kt` —— 结构重构（当前 580 行，拆出三个 Screen 后显著变薄）
- `shared/.../navigation/RootStackConfig.kt`、`RootComponent.kt`、`DefaultRootComponent.kt` —— Search 目的地
- `shared/.../ui/chrome/SharedListScaffold.kt` —— 可选 `titleCollapseState` 参数
- `shared/.../ui/chrome/SharedSearchChrome.kt` —— 去 Dialog
- `shared/.../ui/config/SharedConfigNodeRow.kt`、`ui/nav/XrayFloatingNav.kt`、`ui/home/HomeTopBar.kt`、
  `ui/config/SharedConfigImportMenu.kt` —— 起点元素接受共享容器修饰符参数
- `shared/.../ui/theme/XrayTheme.kt` —— `motionScheme`
- `shared/.../ui/platform/PlatformRootHooks.kt` 及 Android / iOS 两侧实现 ——
  `AppsScreen` / `LogcatScreen` / `QrScannerScreen` 增加 `modifier: Modifier = Modifier` 参数
- 各全屏页（`SharedEditScreen`、`SharedSubscriptionScreen`、`SharedRouteSettingsScreen`、
  `SharedAppLogScreen`、`SharedAppsInfoScreen`）—— 根部承载共享容器修饰符

删除：

- `shared/.../ui/config/ActualConfigSearchFab.kt` —— 防抖与结果列表迁入 `SharedSearchScreen` 后不再需要

`Idle` 分支的内容（`ChildPages` + 浮动底栏）作为一个私有 composable 留在 `RootContent.kt` 内，
不单独建文件 —— 它就是 `RootContent` 的骨架，拆出去反而割裂。

## 10. 降级与边界

| 情况 | 行为 |
|---|---|
| 返回时源节点行已滚出视口 | 找不到匹配 key，自动退化为 `fade() + scale()` 底座动画，不崩不卡。`listState` 已提升，正常操作下不会发生 |
| 在编辑页里删掉该节点后返回 | 同上降级 |
| 连续快点两个不同节点行 | key 不同，spring 支持打断重插值，第二个转场接管 |
| 转场进行中触发预测返回 | `StackAnimationScope` 由 Decompose 驱动，手势进度喂给同一 transition |
| 作用域 CompositionLocal 为 null | `sharedContainer()` 返回原 `Modifier`，该处只是没有动画，不崩（见第 6.3 节） |
| `sharedBounds` 在 Android 上二进制不匹配 | 若 checkpoint 1/3 装机出现 `NoSuchMethodError`，收敛办法是把 `androidApp` 的 `composeBom` 对齐到与 CMP 1.9.3 匹配的版本。属于受控的单点改动 |

### 残余风险：返回时的重组成本

嵌套结构下 `Idle` 在压栈时被释放，返回时要重新组合整棵 tab 树，这个成本落在返回动画的第一帧上。
当前并列结构没有这个成本（tabs 始终存活），因此这是本次重构新引入的。量级上 `LazyColumn` 只组合
可见的十几行，预期无感，但可能掉 1~2 帧。

checkpoint 2 专门用于测量它 —— 那一步只做结构重构、不接任何 shared element，可以干净地对比手感。

若实测卡顿明显，退路是改用第 2.1 节否决的方案二：`ChildPages` 留在 `ChildStack` 外，起点改用
`sharedElementWithCallerManagedVisibility`。该方案零状态丢失、零重组成本，代价是需手动管理
`visible` 并在转场结束后移除退场副本。切换退路不影响其它设计：转场 5（设置 → 子页）两端本就在栈内，
第 3、7、8 节（依赖、结构、搜索改造）也都保持不变。

## 11. 验证策略

三个 checkpoint，每个都能独立回滚：

1. **只升依赖，不碰 UI 代码。** 跑 `:shared:compileDebugKotlinAndroid`、`:androidApp:assembleDebug`、
   全量 `commonTest`，装机跑一遍确认无回归。目的是把「升级引入的问题」与「重构引入的问题」隔离开。
2. **只做结构重构**（`ChildStack` 嵌套 + 状态提升 + 搜索改栈目的地），不加任何 shared element。
   验证点：返回后列表滚动位置保持、标题折叠比例保持、预测返回手势正常、扩充后的
   `DefaultRootComponentStackTest` 全绿。
3. **逐个接入转场**，每接一个装机观察一次。

自动化覆盖：`shared/src/commonTest/.../navigation/DefaultRootComponentStackTest.kt` 扩充搜索目的地
用例（`openSearch` 的压栈、`navigateBack` 返回、`selectTab` 清栈），沿用现有的 `testRootComponent()`
harness。动画观感无法单测，靠 checkpoint 3 的装机验证。

调试手段：`rememberSharedContentState(...).isMatchFound` 为 false 即表示 key 没配上，
这是排查「没有动画」最快的入口。

**已知验证缺口**：`enableIosTargets` 默认仅在 macOS 开启，因此 iOS 侧只能保证 commonMain 不引入
Android 专有 API，无法实际编译或运行验证。iOS 需要在 Mac 上单独过一遍。

Android 侧还有一个需装机确认的点：`androidApp` 同时依赖 androidx Compose BOM 2026.03.00 与
`:shared`（编译期 JB Compose 1.9.4）。Android target 上 JB 产物会重定向到 androidx，Gradle 解析取高
版本，所以 shared 模块按 1.9.4 编译、按 BOM 版本运行。这是升级前就已存在的状况，但本次新用了
shared transition API，需实机确认无 `NoSuchMethodError`。
