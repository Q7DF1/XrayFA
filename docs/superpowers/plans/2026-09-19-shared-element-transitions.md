# Shared Element Transitions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 XrayFA 的全屏页导航动画从 Decompose 横向滑动换成 Material 3 容器变换（container transform）——点击入口元素时该元素边界连续扩张成全屏页，返回时反向回缩。

**Architecture:** 把 `ChildPages`（底部 tab）嵌入 `ChildStack` 的 `Idle` 分支，使全 app 只有一个 `SharedTransitionLayout`，所有转场都在同一个 `ChildStack` 的两个子节点之间配对——这是 Decompose 官方支持的路径。因 `Idle` 会在压栈时被释放，列表滚动位置等状态必须提升到 `RootContent`。搜索从 `Dialog` 改为栈目的地（shared element 穿不过 Dialog）。

**Tech Stack:** Kotlin 2.1.10 / Compose Multiplatform 1.9.3 / Decompose 3.5.0（`extensions-compose-experimental`）/ Essenty 2.5.0 / Koin 4.0.1

**设计文档：** `docs/superpowers/specs/2026-09-19-shared-element-transitions-design.md`

## Global Constraints

- **禁止在 `commonMain` 引用较新的 Material 3 API。** 本项目已有两处记录在案的 `NoSuchMethodError`（`SharedModalBottomSheet` 避开 `ModalBottomSheet`、`SharedSearchChrome` 避开 `SearchBar`），根因是 `androidApp` 同时依赖 androidx Compose BOM，Android 运行时由 androidx material3（1.5.0-alpha15）胜出而 `:shared` 按 JB material3 编译。**不得使用 `MaterialTheme.motionScheme` / `MotionScheme.expressive()` / `SearchBar` / `DockedSearchBar` / `ModalBottomSheet`。**
- 动效数值一律硬编码为 `androidx.compose.animation.core.spring` 常量，集中在 `XrayMotion`。M3 Expressive 取值：容器扩张 `dampingRatio = 0.8f, stiffness = 200f`；淡入淡出 `dampingRatio = 1f, stiffness = 1600f`；局部位移 `dampingRatio = 0.8f, stiffness = 380f`。
- Kotlin 保持 `2.1.10`、KSP 保持 `2.1.10-1.0.31`、AGP 保持 `8.10.0`、`appfunctions` 保持 `1.0.0-alpha08`（`libs.versions.toml` 中已有注释说明 alpha09+ 需要 compileSdk 37 / AGP 9.1）。**本计划不得改动这四项。**
- `androidApp` 的 `composeBom = "2026.03.00"` 与 `material3 = "1.5.0-alpha15"` 不动。
- `androidApp/src/main/java/com/android/xrayfa/ui/component/SettingsScreen.kt`、`AppsScreen.kt`、`LogcatScreen.kt` 是已标 `@Deprecated` 的 Navigation3 遗留屏幕，**不在本计划范围内，不要改动**。
- 所有 shared transition API 使用 Compose 1.8+ 的新参数名：`sharedContentState =`、`placeholderSize`、`ResizeMode.scaleToBounds(...)`。需要 `@OptIn(ExperimentalSharedTransitionApi::class)`。
- Windows 环境下 Gradle 命令用 `.\gradlew.bat`。PowerShell 5.1 **不支持 `&&`**，多条命令用 `;` 分隔。
- iOS 目标默认只在 macOS 启用（`enableIosTargets`）。在 Windows 上无法编译 iOS，但**所有 `commonMain` 改动不得引入 Android 专有 API**，且 `iosMain` 的 `IosPlatformRootHooks` 必须与接口变更同步修改。

---

## File Structure

**新增**

| 文件 | 职责 |
|---|---|
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/transitions/SharedKeys.kt` | 共享容器 key 的数据类与 destination 常量 |
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/transitions/XrayMotion.kt` | 全部动效 spring 常量与 `BoundsTransform` |
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/transitions/TransitionScopes.kt` | 两个 CompositionLocal |
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/transitions/SharedContainer.kt` | `Modifier.sharedContainer()`、`rememberDestinationShape()`、`Modifier.floatingNavOverlay()` |
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/ConfigTabScreen.kt` | Config tab 屏幕（从 `RootContent` 拆出） |
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/HomeTabScreen.kt` | Home tab 屏幕（从 `RootContent` 拆出） |
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/SettingsTabScreen.kt` | 设置屏幕（从 `RootContent` 拆出） |
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/config/ConfigTabChromeState.kt` | 跨导航存活的 Config tab UI 状态 holder |
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/config/SharedSearchScreen.kt` | 搜索栈目的地屏幕 |

**修改**

| 文件 | 改动 |
|---|---|
| `gradle/libs.versions.toml` | 版本与新依赖声明 |
| `shared/build.gradle.kts` | 新依赖接线 |
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/navigation/RootStackConfig.kt` | 加 `Search` |
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/navigation/RootComponent.kt` | 加 `openSearch()` 与 `StackChild.Search` |
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/navigation/DefaultRootComponent.kt` | 实现 `openSearch()` |
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/RootContent.kt` | 嵌套结构重构 |
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/chrome/SharedListScaffold.kt` | 可选外部 `titleCollapseState` |
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/chrome/SharedSearchChrome.kt` | 去 `Dialog` |
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/home/HomeConnectButton.kt` | 首帧保护 |
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/home/HomeTopBar.kt` | 齿轮按钮接受 `modifier` |
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/platform/PlatformRootHooks.kt` | 三个屏幕 hook 加 `modifier` |
| `shared/src/iosMain/kotlin/com/android/xrayfa/shared/ui/platform/IosPlatformRootHooks.kt` | 同步签名 |
| `androidApp/src/main/java/com/android/xrayfa/ui/AndroidPlatformRootHooks.kt` | 同步签名 |
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/settings/SharedAppLogScreen.kt` | 根部接受 `modifier` |
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/settings/SharedAppsInfoScreen.kt` | 根部接受 `modifier` |
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/settings/SharedRouteSettingsScreen.kt` | 根部接受 `modifier` |
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/subscription/SharedSubscriptionScreen.kt` | 根部接受 `modifier` |
| `shared/src/commonTest/kotlin/com/android/xrayfa/shared/navigation/DefaultRootComponentStackTest.kt` | 搜索目的地用例 |

**删除**

| 文件 | 原因 |
|---|---|
| `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/config/ActualConfigSearchFab.kt` | 防抖与结果列表迁入 `SharedSearchScreen` |

**已有的现成挂载点（不需要改签名）**

- `SharedConfigSection` 已有 `rowModifier: (Node) -> Modifier = { Modifier }` —— 节点行转场直接用它
- `SharedConfigSection` 已有 `listState: LazyListState` —— 状态提升直接传
- `SharedSettingsPlatformSection` 已有 `appsModifier` / `logcatModifier` / `routeModifier`
- `SharedConfigImportMenu` 已有 `modifier: Modifier`（挂在 `IconButton` 上）
- `SharedEditScreen` 已有 `modifier` 并下传给 `SharedListScaffold`

---

# Phase 1 — 依赖升级（Checkpoint 1）

## Task 1: 升级 Compose Multiplatform / Decompose / Essenty

本任务**不改任何 UI 代码**，目的是把「升级引入的问题」与「重构引入的问题」彻底隔离。

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `shared/build.gradle.kts`
- Create (临时，任务结束时删除): `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/transitions/TransitionApiProbe.kt`

**Interfaces:**
- Produces: 依赖别名 `libs.decompose.extensions.compose.experimental`、`libs.compose.material.icons.core`、`libs.compose.material.icons.extended`；以及经编译验证过的 Decompose 实验版 API 导入路径，后续 Task 5 直接沿用。

- [ ] **Step 1: 更新 `gradle/libs.versions.toml` 的 `[versions]`**

把这三行改成新值，并新增一行 `materialIcons`：

```toml
kotlin = "2.1.10"
composeMultiplatform = "1.9.3"
decompose = "3.5.0"
essenty = "2.5.0"
materialIcons = "1.7.3"
```

（`kotlin` 一行原样保留，此处列出只为确认不要动它。）

- [ ] **Step 2: 在 `[libraries]` 新增三个别名**

加在现有 `decompose-extensions-compose` 那一行之后：

```toml
decompose-extensions-compose-experimental = { module = "com.arkivanov.decompose:extensions-compose-experimental", version.ref = "decompose" }
compose-material-icons-core = { module = "org.jetbrains.compose.material:material-icons-core", version.ref = "materialIcons" }
compose-material-icons-extended = { module = "org.jetbrains.compose.material:material-icons-extended", version.ref = "materialIcons" }
```

- [ ] **Step 3: 更新 `shared/build.gradle.kts` 的 `commonMain.dependencies`**

把这一行：

```kotlin
            implementation(compose.materialIconsExtended)
```

替换为：

```kotlin
            implementation(libs.compose.material.icons.core)
            implementation(libs.compose.material.icons.extended)
```

并在 `implementation(libs.decompose.extensions.compose)` 之后新增一行：

```kotlin
            implementation(libs.decompose.extensions.compose.experimental)
```

`implementation(compose.material3)` **保持不动**（见 Global Constraints）。

- [ ] **Step 4: 写 API 探针，验证实验版 API 的导入路径与参数名**

本计划中 Decompose 实验版的包路径与参数名来自官方文档而非本地产物，必须先用编译器证实。创建
`shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/transitions/TransitionApiProbe.kt`：

```kotlin
package com.android.xrayfa.shared.ui.transitions

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.navigation.RootComponent
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.experimental.stack.ChildStack
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.PredictiveBackParams
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.plus
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.scale
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.materialPredictiveBackAnimatable

/**
 * 临时探针：只为在 Task 1 用编译器证实实验版 API 的包路径与参数名。
 * Task 1 的最后一步会删除本文件。
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalDecomposeApi::class)
@Composable
private fun Probe(component: RootComponent) {
    SharedTransitionLayout {
        val sharedScope: SharedTransitionScope = this
        ChildStack(
            stack = component.stack,
            animation =
                stackAnimation(
                    animator = fade() + scale(),
                    predictiveBackParams = {
                        PredictiveBackParams(
                            backHandler = component.backHandler,
                            onBack = component::navigateBack,
                            animatable = ::materialPredictiveBackAnimatable,
                        )
                    },
                ),
        ) { _ ->
            val avScope: AnimatedVisibilityScope = this
            val corner by avScope.transition.animateDp(
                transitionSpec = { spring(dampingRatio = 0.8f, stiffness = 380f, visibilityThreshold = Dp.VisibilityThreshold) },
                label = "probeCorner",
            ) { state -> if (state == EnterExitState.Visible) 0.dp else 20.dp }

            with(sharedScope) {
                Box(
                    modifier =
                        Modifier
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState(SharedContainerKeyProbe(corner.value)),
                                animatedVisibilityScope = avScope,
                                enter = fadeIn(spring(dampingRatio = 1f, stiffness = 1600f)),
                                exit = fadeOut(spring(dampingRatio = 1f, stiffness = 1600f)),
                                boundsTransform = { _, _ ->
                                    spring(
                                        dampingRatio = 0.8f,
                                        stiffness = 200f,
                                        visibilityThreshold = Rect.VisibilityThreshold,
                                    )
                                },
                                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                            )
                            .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
                            .then(with(avScope) { Modifier.animateEnterExit() }),
                )
                Box(modifier = Modifier.then(with(sharedScope) { Modifier }))
            }
            Box(modifier = Modifier)
        }
    }
    Box(modifier = Modifier)
    @Suppress("UNUSED_EXPRESSION")
    RectangleShape
}

private data class SharedContainerKeyProbe(val v: Float)
```

- [ ] **Step 5: 编译探针**

```powershell
.\gradlew.bat :shared:compileDebugKotlinAndroid
```

Expected: `BUILD SUCCESSFUL`。

如果失败，**不要猜**。按报错逐项修正探针里的导入路径 / 参数名，并把修正后的正确写法记录下来——
后续 Task 5、Task 6 必须使用探针验证过的写法。常见偏差：
- `ChildStack` 可能在 `...experimental.stack.ChildStack` 之外还需 `...experimental.stack.animation.*`
- Decompose 3.5.0 的 content receiver 是 `StackAnimationScope`（继承 `AnimatedVisibilityScope`），
  赋值给 `AnimatedVisibilityScope` 应当成立；若不成立，改为 `StackAnimationScope` 并记录其导入路径
- `plus` 运算符可能需要显式导入 `...experimental.stack.animation.plus`

- [ ] **Step 6: 跑全量单元测试**

```powershell
.\gradlew.bat :shared:testDebugUnitTest :domain:allTests :common:allTests :core:datastore:allTests
```

Expected: 全部 `BUILD SUCCESSFUL`，无失败用例。

- [ ] **Step 7: 构建 Android APK**

```powershell
.\gradlew.bat :androidApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`。

若出现 `Icons.Outlined.*` 之类的未解析引用，说明 Step 3 的 material-icons 依赖没生效，回查 Step 2/3。

- [ ] **Step 8: 删除探针文件**

```powershell
Remove-Item shared\src\commonMain\kotlin\com\android\xrayfa\shared\ui\transitions\TransitionApiProbe.kt
.\gradlew.bat :shared:compileDebugKotlinAndroid
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 9: 装机冒烟验证（Checkpoint 1）**

安装 APK 并逐项确认——此时应与升级前**完全一致**，任何差异都是升级引入的回归：

1. 首页显示，连接按钮可点，连接状态正常
2. 底部导航在 Config / Home 之间切换正常
3. 齿轮进入设置，返回正常
4. 设置里 Apps / 日志 / 路由三个子页可进可回
5. Config 页节点列表滚动流畅，点节点行的编辑图标能进编辑页
6. 底栏放大镜能打开搜索（此时仍是 Dialog），输入能过滤
7. Android 返回手势（预测返回）在各页正常
8. **logcat 中没有 `NoSuchMethodError` / `NoSuchFieldError`**

若出现 `NoSuchMethodError`，按 spec 第 10 节的收敛办法：把 `androidApp` 的 `composeBom` 对齐到与
CMP 1.9.3 匹配的版本。

- [ ] **Step 10: Commit**

```powershell
git add gradle/libs.versions.toml shared/build.gradle.kts
git commit -m "build: upgrade to Compose Multiplatform 1.9.3 and Decompose 3.5.0"
```

---

# Phase 2 — 结构重构（Checkpoint 2）

## Task 2: 把搜索改成 Decompose 栈目的地

本任务在**旧结构**（`Children`）上完成，与后续嵌套重构解耦。完成后搜索获得系统返回键与预测返回手势。

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/navigation/RootStackConfig.kt`
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/navigation/RootComponent.kt`
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/navigation/DefaultRootComponent.kt`
- Create: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/config/SharedSearchScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/chrome/SharedSearchChrome.kt`
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/RootContent.kt`
- Delete: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/config/ActualConfigSearchFab.kt`
- Test: `shared/src/commonTest/kotlin/com/android/xrayfa/shared/navigation/DefaultRootComponentStackTest.kt`

**Interfaces:**
- Consumes: Task 1 的依赖版本。
- Produces:
  - `RootStackConfig.Search`（`data object`）
  - `RootComponent.openSearch()`、`RootComponent.StackChild.Search`（`data object`）
  - `fun SharedSearchScreen(component: ConfigComponent, labels: ConfigUiLabels, backContentDescription: String, onBack: () -> Unit, onResultChosen: (nodeId: Int) -> Unit, modifier: Modifier = Modifier)`
  - `internal fun ConfigSearchOverlayResults(searchQuery: String, nodes: List<Node>, searchNoResultsLabel: String, onResultChosen: (Node) -> Unit)` —— 从 `ActualConfigSearchFab.kt` 迁到 `SharedSearchScreen.kt`，签名不变
  - `internal fun SharedSearchChrome(query: String, onQueryChange: (String) -> Unit, searchLabel: String, onImeSearch: (String) -> Unit, onBack: () -> Unit, backContentDescription: String, modifier: Modifier = Modifier, results: @Composable ColumnScope.() -> Unit)` —— 删掉 `expanded`、`onExpandedChange`、`showCollapsedTrigger`，新增 `onBack`、`backContentDescription`
  - `internal fun ConfigTabScreen(component: ConfigComponent, onNodeSelectedNavigateHome: () -> Unit, onOpenNodeEdit: (Int) -> Unit, onOpenSubscriptions: () -> Unit, onOpenQrScanner: () -> Unit, pendingOverlayScroll: OverlayScrollPending?, onPendingOverlayScrollHandled: () -> Unit)` —— 本任务的中间形态，Task 3 会把后两个参数换成 `chromeState`

- [ ] **Step 1: 写失败的测试**

在 `DefaultRootComponentStackTest.kt` 末尾（最后一个 `}` 之前）追加：

```kotlin
    @Test
    fun openSearchPushesOnIdleAndSelectsConfigTab() {
        val root = testRootComponent()
        root.openSearch()
        assertEquals(
            listOf(RootStackConfig.Idle, RootStackConfig.Search),
            configs(root),
        )
        assertEquals(
            RootTab.Config,
            root.pages.value.items[root.pages.value.selectedIndex].configuration,
        )
    }

    @Test
    fun searchPopsOnBack() {
        val root = testRootComponent()
        root.openSearch()
        root.navigateBack()
        assertEquals(RootStackConfig.Idle, active(root))
    }

    @Test
    fun secondOpenSearchDoesNotStackTwice() {
        val root = testRootComponent()
        root.openSearch()
        root.openSearch()
        assertEquals(
            listOf(RootStackConfig.Idle, RootStackConfig.Search),
            configs(root),
        )
    }

    @Test
    fun selectTabClearsSearch() {
        val root = testRootComponent()
        root.openSearch()
        root.selectTab(RootTab.Home)
        assertEquals(listOf(RootStackConfig.Idle), configs(root))
    }
```

- [ ] **Step 2: 跑测试确认失败**

```powershell
.\gradlew.bat :shared:testDebugUnitTest --tests "com.android.xrayfa.shared.navigation.DefaultRootComponentStackTest"
```

Expected: 编译失败，报 `Unresolved reference: Search` 与 `Unresolved reference: openSearch`。

- [ ] **Step 3: 加 `RootStackConfig.Search`**

在 `RootStackConfig.kt` 的 `RouteSettings` 那行之后插入：

```kotlin
    @Serializable data object Search : RootStackConfig
```

`toRootNavigation()` 不需要改（`AgentScreen` 没有搜索入口）。

- [ ] **Step 4: 加 `RootComponent` 的声明**

在 `RootComponent.kt` 的 `fun openNodeEdit(nodeId: Int)` 之后插入：

```kotlin
    fun openSearch()
```

在 `sealed class StackChild` 的 `data object RouteSettings : StackChild()` 之后插入：

```kotlin
        data object Search : StackChild()
```

- [ ] **Step 5: 实现 `DefaultRootComponent.openSearch()`**

在 `childStack` 的 `childFactory` 的 `RootStackConfig.RouteSettings -> ...` 那行之后插入：

```kotlin
                    RootStackConfig.Search -> RootComponent.StackChild.Search
```

在 `override fun openNodeEdit(nodeId: Int) = bringOrPush(RootStackConfig.NodeEdit(nodeId))` 之后插入
（与 `openSubscriptions()` 同构——搜索只作用于 Config 列表，所以要切到 Config tab）：

```kotlin
    override fun openSearch() {
        bringOrPush(RootStackConfig.Search)
        navigation.select(index = RootTab.Config.ordinal)
    }
```

`openAgentScreen` 里的 `when (val dest = target.stack)` 是对 `RootStackConfig` 的穷尽匹配，需补一支。
在 `RootStackConfig.Logcat -> openLogcat()` 之后插入：

```kotlin
            RootStackConfig.Search -> openSearch()
```

- [ ] **Step 6: 跑测试确认通过**

```powershell
.\gradlew.bat :shared:testDebugUnitTest --tests "com.android.xrayfa.shared.navigation.DefaultRootComponentStackTest"
```

Expected: PASS（含既有 14 个用例 + 新增 4 个）。

此时 `RootContent.kt` 的 `when (val instance = child.instance)` 还没处理 `StackChild.Search`，
Kotlin 会报穷尽性错误——下一步解决。

- [ ] **Step 7: 改造 `SharedSearchChrome`，去掉 Dialog**

用以下内容**整体替换** `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/chrome/SharedSearchChrome.kt`：

```kotlin
package com.android.xrayfa.shared.ui.chrome

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.resources.Res
import com.android.xrayfa.shared.resources.search_clear
import org.jetbrains.compose.resources.stringResource

/**
 * 全屏搜索外壳。作为 Decompose 栈目的地的内容渲染，**不是** Dialog —— shared element 的 overlay
 * 穿不过 Dialog 的独立窗口。
 *
 * Do not use Material3 [androidx.compose.material3.SearchBar] /
 * [androidx.compose.material3.DockedSearchBar] in commonMain — CMP material3 vs
 * androidx material3 is a NoSuchMethodError on Android, same class of issue as
 * [com.android.xrayfa.shared.ui.widgets.SharedModalBottomSheet].
 */
@Composable
internal fun SharedSearchChrome(
    query: String,
    onQueryChange: (String) -> Unit,
    searchLabel: String,
    onImeSearch: (String) -> Unit,
    onBack: () -> Unit,
    backContentDescription: String,
    modifier: Modifier = Modifier,
    results: @Composable ColumnScope.() -> Unit,
) {
    val clearLabel = stringResource(Res.string.search_clear)
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .imePadding(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(focusRequester),
                placeholder = { Text(searchLabel) },
                leadingIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backContentDescription,
                        )
                    }
                },
                trailingIcon =
                    if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = clearLabel)
                            }
                        }
                    } else {
                        {
                            Icon(Icons.Outlined.Search, contentDescription = searchLabel)
                        }
                    },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onImeSearch(query) }),
            )
            results()
        }
    }
}
```

- [ ] **Step 8: 创建 `SharedSearchScreen`**

创建 `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/config/SharedSearchScreen.kt`。
防抖逻辑与结果列表从 `ActualConfigSearchFab.kt` 迁来，行为保持一致（300ms debounce）：

```kotlin
package com.android.xrayfa.shared.ui.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.android.xrayfa.model.Node
import com.android.xrayfa.shared.navigation.ConfigComponent
import com.android.xrayfa.shared.ui.chrome.SharedSearchChrome
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 全屏搜索目的地。查询状态归 [ConfigComponent] 所有，本屏幕只做输入防抖与结果呈现。
 */
@OptIn(FlowPreview::class)
@Composable
fun SharedSearchScreen(
    component: ConfigComponent,
    labels: ConfigUiLabels,
    backContentDescription: String,
    onBack: () -> Unit,
    onResultChosen: (nodeId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    var query by remember { mutableStateOf(state.searchQuery) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(300)
            .distinctUntilChanged()
            .collectLatest { component.onSearch(it) }
    }

    SharedSearchChrome(
        query = query,
        onQueryChange = { query = it },
        searchLabel = labels.searchLabel,
        onImeSearch = {
            focusManager.clearFocus()
            keyboard?.hide()
        },
        onBack = onBack,
        backContentDescription = backContentDescription,
        modifier = modifier,
        results = {
            ConfigSearchOverlayResults(
                searchQuery = state.searchQuery,
                nodes = state.nodes,
                searchNoResultsLabel = labels.searchNoResultsLabel,
                onResultChosen = { node ->
                    onResultChosen(node.id)
                    query = ""
                    component.onSearch("")
                    onBack()
                },
            )
        },
    )
}

@Composable
internal fun ConfigSearchOverlayResults(
    searchQuery: String,
    nodes: List<Node>,
    searchNoResultsLabel: String,
    onResultChosen: (Node) -> Unit,
) {
    if (searchQuery.isNotBlank() && nodes.isEmpty()) {
        Text(searchNoResultsLabel, modifier = Modifier.padding(16.dp))
    } else {
        LazyColumn {
            items(nodes, key = { it.id }) { node ->
                ListItem(
                    headlineContent = { Text(node.remark?.ifBlank { node.url } ?: node.url) },
                    modifier = Modifier.clickable { onResultChosen(node) },
                )
            }
        }
    }
}
```

- [ ] **Step 9: 删除 `ActualConfigSearchFab.kt`**

```powershell
Remove-Item shared\src\commonMain\kotlin\com\android\xrayfa\shared\ui\config\ActualConfigSearchFab.kt
```

`OverlayScrollPending.kt` 与 `OverlayScrollPendingTest.kt` **保留不动**。

- [ ] **Step 10: 更新 `RootContent.kt`**

改动四处。

（a）删掉两个状态变量与那个 `LaunchedEffect`。删除这些行：

```kotlin
    var searchExpandedCoversNav by remember { mutableStateOf(false) }
    var requestOpenSearch by remember { mutableStateOf(false) }
```
```kotlin
    LaunchedEffect(selectedTab) {
        if (selectedTab != RootTab.Config) {
            searchExpandedCoversNav = false
        }
    }
```

并把 `showBottomNav` 简化为：

```kotlin
    val showBottomNav = stackIdle
```

（b）`pendingOverlayScroll` 必须上移到 `RootContent`：写入方（搜索屏幕）与消费方（Config 列表）
现在是两个不同的栈子节点，`ConfigTabScreen` 内部的 `var` 已经接不到了。在 `RootContent` 里加：

```kotlin
    var pendingOverlayScroll by remember { mutableStateOf<OverlayScrollPending?>(null) }
```

（c）`ConfigTabScreen` 的签名：删掉 `onSearchExpanded` / `forceCollapseSearch` / `openSearch` /
`onOpenSearchConsumed` 四个参数，新增 `pendingOverlayScroll` 与 `onPendingOverlayScrollHandled`：

```kotlin
private fun ConfigTabScreen(
    component: ConfigComponent,
    onNodeSelectedNavigateHome: () -> Unit,
    onOpenNodeEdit: (Int) -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenQrScanner: () -> Unit,
    pendingOverlayScroll: OverlayScrollPending?,
    onPendingOverlayScrollHandled: () -> Unit,
)
```

函数体里删掉整个 `ActualConfigSearchFab(...)` 调用块以及 `var pendingOverlayScroll by remember { ... }`
声明，把外层 `Box` 恢复成只包 `SharedConfigSection`。消费 pending 的那个 `LaunchedEffect` **保留**，
但改为读参数、并在处理完后调 `onPendingOverlayScrollHandled()` 而不是自己置 null。

（d）调用处：

```kotlin
                is RootComponent.Child.Config ->
                    ConfigTabScreen(
                        component = child.component,
                        onNodeSelectedNavigateHome = { component.selectTab(RootTab.Home) },
                        onOpenNodeEdit = component::openNodeEdit,
                        onOpenSubscriptions = component::openSubscriptions,
                        onOpenQrScanner = component::openQrScanner,
                        pendingOverlayScroll = pendingOverlayScroll,
                        onPendingOverlayScrollHandled = { pendingOverlayScroll = null },
                    )
```

（e）在 `Children` 的 `when` 中新增 `Search` 分支：

```kotlin
                RootComponent.StackChild.Search -> {
                    val cfg = configComponent
                    if (cfg != null) {
                        SharedSearchScreen(
                            component = cfg,
                            labels = configLabels,
                            backContentDescription = settingsLabels.cancelLabel,
                            onBack = component::navigateBack,
                            onResultChosen = { nodeId ->
                                pendingOverlayScroll =
                                    OverlayScrollPending(
                                        nodeId = nodeId,
                                        nodesAtTap = cfg.state.value.nodes,
                                        queryWasBlankAtTap = cfg.state.value.searchQuery.isBlank(),
                                    )
                            },
                            modifier = fill,
                        )
                    }
                }
```

（f）底栏的 `onTrailingClick` 改为直接开栈目的地：

```kotlin
                    onTrailingClick = component::openSearch,
```

- [ ] **Step 11: 编译并跑测试**

```powershell
.\gradlew.bat :shared:compileDebugKotlinAndroid :shared:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`，全部用例 PASS。

- [ ] **Step 12: 同步 iOS 源集编译检查（静态检查）**

无法在 Windows 上编译 iOS，改为人工确认：`SharedSearchScreen.kt` 与 `SharedSearchChrome.kt` 的
导入里没有 `android.` 或 `androidx.compose.ui.platform.LocalContext` 之类的 Android 专有引用。

```powershell
Select-String -Path shared\src\commonMain\kotlin\com\android\xrayfa\shared\ui\config\SharedSearchScreen.kt,shared\src\commonMain\kotlin\com\android\xrayfa\shared\ui\chrome\SharedSearchChrome.kt -Pattern "^import android"
```

Expected: 无输出。

- [ ] **Step 13: 装机验证**

1. 点底栏放大镜 → 全屏搜索打开，输入法自动弹出，光标在输入框
2. 输入关键字 → 列表过滤（约 300ms 延迟）
3. 点一条结果 → 回到 Config 列表，且自动滚动定位到该节点
4. 再次打开搜索 → 按系统返回键 / 返回手势能关闭（**这是本任务新增的能力**）
5. 搜索打开时底栏自动隐藏
6. 搜索打开时切到 Home tab → 栈被清空，搜索关闭

- [ ] **Step 14: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/android/xrayfa/shared/navigation/ shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/ shared/src/commonTest/kotlin/com/android/xrayfa/shared/navigation/
git commit -m "feat: make search a Decompose stack destination instead of a Dialog"
```

---

## Task 3: 状态提升的承载物

**Files:**
- Create: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/config/ConfigTabChromeState.kt`
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/chrome/SharedListScaffold.kt:67`
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/RootContent.kt`

**Interfaces:**
- Consumes: Task 2 的 `pendingOverlayScroll` 提升位置。
- Produces:
  - `class ConfigTabChromeState(val listState: LazyListState, val titleCollapseState: TitleCollapseState)`，
    含 `var pendingOverlayScroll: OverlayScrollPending?`（`mutableStateOf` 支撑）
  - `@Composable fun rememberConfigTabChromeState(): ConfigTabChromeState`
  - `SharedListScaffold` 新增参数 `titleCollapseState: TitleCollapseState? = null`（默认 null 时沿用内部 `remember`）

- [ ] **Step 1: 创建 holder**

创建 `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/config/ConfigTabChromeState.kt`：

```kotlin
package com.android.xrayfa.shared.ui.config

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.xrayfa.shared.ui.chrome.TitleCollapseState
import com.android.xrayfa.shared.ui.chrome.rememberTitleCollapseState

/**
 * Config tab 里必须跨导航存活的 UI 状态。
 *
 * 必须在 `RootContent` 层 remember：`ChildStack` 只组合活跃子节点，Config tab 所在的 `Idle`
 * 子节点在压栈时会被释放。其中 [listState] 是容器回缩动画正确性的硬依赖 —— 若返回时列表跳回顶部，
 * 共享容器的目标边界就是错的，会看到方块飞向屏幕外。
 */
@Stable
class ConfigTabChromeState(
    val listState: LazyListState,
    val titleCollapseState: TitleCollapseState,
) {
    var pendingOverlayScroll: OverlayScrollPending? by mutableStateOf(null)
}

@Composable
fun rememberConfigTabChromeState(): ConfigTabChromeState {
    val listState = rememberLazyListState()
    val titleCollapseState = rememberTitleCollapseState()
    return remember(listState, titleCollapseState) {
        ConfigTabChromeState(listState = listState, titleCollapseState = titleCollapseState)
    }
}
```

- [ ] **Step 2: `SharedListScaffold` 支持外部 `titleCollapseState`**

在参数列表里，`titleExpandKey: Any? = null,` 之后插入：

```kotlin
    titleCollapseState: TitleCollapseState? = null,
```

并把函数体第一行：

```kotlin
    val titleCollapse = rememberTitleCollapseState()
```

替换为：

```kotlin
    val titleCollapse = titleCollapseState ?: rememberTitleCollapseState()
```

> 这里的条件 `remember` 是安全的：调用方在整个生命周期内要么一直传、要么一直不传，
> 不会在两者之间切换。

- [ ] **Step 3: `RootContent` 改用 holder**

在 `RootContent` 中，把 Task 2 引入的 `pendingOverlayScroll` 局部变量替换为：

```kotlin
    val configChromeState = rememberConfigTabChromeState()
```

`ConfigTabScreen` 的签名改为接收 holder，替换掉 Task 2 加的
`pendingOverlayScroll` / `onPendingOverlayScrollHandled` 两个参数：

```kotlin
private fun ConfigTabScreen(
    component: ConfigComponent,
    chromeState: ConfigTabChromeState,
    onNodeSelectedNavigateHome: () -> Unit,
    onOpenNodeEdit: (Int) -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenQrScanner: () -> Unit,
)
```

函数体内：
- 删除 `val listState = rememberLazyListState()`，所有用到处改为 `chromeState.listState`
- 消费 pending 的 `LaunchedEffect` 改为读 `chromeState.pendingOverlayScroll`，处理完后
  `chromeState.pendingOverlayScroll = null`
- `SharedListScaffold(...)` 增加 `titleCollapseState = chromeState.titleCollapseState,`
- `SharedConfigSection(...)` 传 `listState = chromeState.listState`

调用处改为 `chromeState = configChromeState`，删掉那两个 pending 参数。

`Search` 分支里写入 pending 的那一行改为：

```kotlin
                                configChromeState.pendingOverlayScroll =
                                    OverlayScrollPending(
                                        nodeId = nodeId,
                                        nodesAtTap = cfg.state.value.nodes,
                                        queryWasBlankAtTap = cfg.state.value.searchQuery.isBlank(),
                                    )
```

- [ ] **Step 4: 编译并跑测试**

```powershell
.\gradlew.bat :shared:compileDebugKotlinAndroid :shared:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`，全部 PASS。

- [ ] **Step 5: 装机验证**

行为应与 Task 2 结束时**完全一致**（这是纯粹的状态搬家）：

1. Config 列表滚到中部 → 打开搜索 → 返回 → 滚动位置保持
2. Config 列表滚动后标题折叠 → 打开搜索 → 返回 → 折叠状态保持
3. 搜索选中结果 → 自动滚动定位仍然生效

- [ ] **Step 6: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/
git commit -m "refactor: hoist Config tab chrome state above the navigation stack"
```

---

## Task 4: 把三个屏幕从 `RootContent` 拆出

纯搬移，**不改任何行为**。目的是给 Task 5 的结构重构腾出空间（`RootContent.kt` 当前 580 行）。

**Files:**
- Create: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/ConfigTabScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/HomeTabScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/SettingsTabScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/RootContent.kt`

**Interfaces:**
- Consumes: Task 3 的 `ConfigTabChromeState`。
- Produces: 三个 `internal` 顶层 composable，包名同为 `com.android.xrayfa.shared.ui`：
  - `internal fun ConfigTabScreen(component: ConfigComponent, chromeState: ConfigTabChromeState, onNodeSelectedNavigateHome: () -> Unit, onOpenNodeEdit: (Int) -> Unit, onOpenSubscriptions: () -> Unit, onOpenQrScanner: () -> Unit)`
  - `internal fun HomeTabScreen(component: HomeComponent, onSettingsClick: () -> Unit)`
  - `internal fun SettingsTabScreen(component: SettingsComponent, onBack: () -> Unit, onAppsClick: () -> Unit, onLogcatClick: () -> Unit, onRouteClick: () -> Unit, modifier: Modifier = Modifier)`

- [ ] **Step 1: 搬 `ConfigTabScreen`**

把 `RootContent.kt` 中 `private fun ConfigTabScreen(...)` 整个函数（含它用到的
`AlertDialog` 删除确认块、`shareNode`、`showBugReport`）剪切到新文件
`shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/ConfigTabScreen.kt`，
`private` 改 `internal`，`package com.android.xrayfa.shared.ui`，按编译器提示补齐 import。

- [ ] **Step 2: 搬 `HomeTabScreen`**

同上，搬到 `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/HomeTabScreen.kt`。

- [ ] **Step 3: 搬 `SettingsTabScreen`**

同上，搬到 `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/SettingsTabScreen.kt`，
并额外加一个 `modifier: Modifier = Modifier` 参数下传给 `SharedListScaffold`（Task 10 需要）：

```kotlin
internal fun SettingsTabScreen(
    component: SettingsComponent,
    onBack: () -> Unit,
    onAppsClick: () -> Unit,
    onLogcatClick: () -> Unit,
    onRouteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // ...
    SharedListScaffold(
        title = stringResource(Res.string.settings_title),
        modifier = modifier,
        lockCollapsedTitle = true,
        // ... 其余原样
```

- [ ] **Step 4: 编译并跑测试**

```powershell
.\gradlew.bat :shared:compileDebugKotlinAndroid :shared:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`，全部 PASS。

- [ ] **Step 5: 确认 `RootContent.kt` 显著变薄**

```powershell
(Get-Content shared\src\commonMain\kotlin\com\android\xrayfa\shared\ui\RootContent.kt).Count
```

Expected: 明显低于 300 行。

- [ ] **Step 6: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/
git commit -m "refactor: extract tab screens out of RootContent"
```

---

## Task 5: `RootContent` 嵌套结构重构（Checkpoint 2）

本任务是整个计划的枢纽。**不加任何 shared element**——只把结构改成能承载它们的样子。

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/RootContent.kt`
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/home/HomeConnectButton.kt:58-71`

**Interfaces:**
- Consumes: Task 1 探针验证过的实验版 API 导入路径；Task 3 的 `ConfigTabChromeState`；Task 4 的三个屏幕。
- Produces: `RootContent` 内的 `private fun IdleContent(...)`，承载 `ChildPages` 与浮动底栏。

- [ ] **Step 1: 重写 `RootContent`**

结构如下。**导入路径必须使用 Task 1 探针验证过的写法**：

```kotlin
@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun RootContent(
    component: RootComponent,
    modifier: Modifier = Modifier,
) {
    val stack by component.stack.subscribeAsState()
    val stackIdle = stack.active.configuration is RootStackConfig.Idle
    val configChromeState = rememberConfigTabChromeState()
    val configLabels = rememberConfigUiLabels()
    val settingsLabels = rememberSettingsUiLabels()
    val routeSettingsLabels = rememberRouteSettingsUiLabels()
    val platformHooks = LocalPlatformRootHooks.current
    val pages by component.pages.subscribeAsState()
    val configComponent =
        pages.items
            .map { it.instance }
            .filterIsInstance<RootComponent.Child.Config>()
            .firstOrNull()
            ?.component

    if (!platformHooks.usesDecomposePredictiveBack) {
        platformHooks.SystemBackHandler(
            enabled = !stackIdle,
            onBack = component::navigateBack,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        ChildStack(
            stack = component.stack,
            modifier = Modifier.fillMaxSize(),
            animation =
                stackAnimation(
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
            val fill = Modifier.fillMaxSize()
            when (val instance = child.instance) {
                RootComponent.StackChild.Idle ->
                    IdleContent(
                        component = component,
                        chromeState = configChromeState,
                        configLabels = configLabels,
                    )
                RootComponent.StackChild.Settings ->
                    SettingsTabScreen(
                        component = component.settingsComponent,
                        onBack = component::navigateBack,
                        onAppsClick = component::openApps,
                        onLogcatClick = component::openLogcat,
                        onRouteClick = component::openRouteSettings,
                        modifier = fill,
                    )
                is RootComponent.StackChild.Subscriptions ->
                    SharedSubscriptionScreen(
                        component = instance.component,
                        // 其余实参原样照抄重构前的写法
                        modifier = fill,
                    )
                RootComponent.StackChild.QrScanner ->
                    platformHooks.QrScannerScreen(
                        // 四个实参（onResult / onBack / title / permissionRequiredMessage）
                        // 原样照抄重构前的写法
                    )
                RootComponent.StackChild.Apps ->
                    platformHooks.AppsScreen(
                        component = component.settingsComponent,
                        onBack = component::navigateBack,
                    )
                RootComponent.StackChild.Logcat ->
                    platformHooks.LogcatScreen(onBack = component::navigateBack)
                RootComponent.StackChild.RouteSettings ->
                    SharedRouteSettingsScreen(
                        component = component.settingsComponent,
                        onBack = component::navigateBack,
                        labels = routeSettingsLabels,
                        modifier = fill,
                    )
                RootComponent.StackChild.Search -> {
                    val cfg = configComponent
                    if (cfg != null) {
                        SharedSearchScreen(
                            component = cfg,
                            labels = configLabels,
                            backContentDescription = settingsLabels.cancelLabel,
                            onBack = component::navigateBack,
                            onResultChosen = { nodeId ->
                                configChromeState.pendingOverlayScroll =
                                    OverlayScrollPending(
                                        nodeId = nodeId,
                                        nodesAtTap = cfg.state.value.nodes,
                                        queryWasBlankAtTap = cfg.state.value.searchQuery.isBlank(),
                                    )
                            },
                            modifier = fill,
                        )
                    }
                }
                is RootComponent.StackChild.NodeEdit ->
                    SharedEditScreen(
                        nodeId = instance.nodeId,
                        // 其余实参保持 Task 3 结束时的原样，只把 modifier 换成 fill
                        modifier = fill,
                    )
            }
        }
    }
}
```

> 上面各分支的实参（`onSave`、`nodeFormEditor`、`labels` 等）沿用重构前 `RootContent.kt`
> 中已有的写法，本任务**只改结构不改实参**。`QrScannerScreen` / `AppsScreen` / `LogcatScreen`
> 此时还没有 `modifier` 参数（Task 7 才加），所以不传。

`IdleContent` 承载 `ChildPages` 与底栏：

```kotlin
@OptIn(ExperimentalDecomposeApi::class)
@Composable
private fun IdleContent(
    component: RootComponent,
    chromeState: ConfigTabChromeState,
    configLabels: ConfigUiLabels,
) {
    val pages by component.pages.subscribeAsState()
    val selectedTab = pages.items.getOrNull(pages.selectedIndex)?.configuration ?: RootTab.Home

    Box(modifier = Modifier.fillMaxSize()) {
        ChildPages(
            modifier = Modifier.fillMaxSize(),
            pages = component.pages,
            onPageSelected = component::onPageSelected,
            scrollAnimation = PagesScrollAnimation.Default,
        ) { _, child ->
            when (child) {
                is RootComponent.Child.Home ->
                    HomeTabScreen(
                        component = child.component,
                        onSettingsClick = component::openSettings,
                    )
                is RootComponent.Child.Config ->
                    ConfigTabScreen(
                        component = child.component,
                        chromeState = chromeState,
                        onNodeSelectedNavigateHome = { component.selectTab(RootTab.Home) },
                        onOpenNodeEdit = component::openNodeEdit,
                        onOpenSubscriptions = component::openSubscriptions,
                        onOpenQrScanner = component::openQrScanner,
                    )
            }
        }

        val navItems =
            listOf(
                FloatingNavItem(
                    id = RootTab.Config.name,
                    icon = RootTab.Config.toFloatingNavItem().icon,
                    label = stringResource(Res.string.config),
                ),
                FloatingNavItem(
                    id = RootTab.Home.name,
                    icon = RootTab.Home.toFloatingNavItem().icon,
                    label = stringResource(Res.string.home),
                ),
            )
        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            FloatingNavBottomFade(modifier = Modifier.align(Alignment.BottomCenter))
            XrayFloatingNav(
                items = navItems,
                selectedId = selectedTab.name,
                onItemSelected = { item ->
                    component.selectTab(
                        if (item.id == RootTab.Config.name) RootTab.Config else RootTab.Home,
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = configLabels.searchLabel,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(26.dp),
                    )
                },
                onTrailingClick = component::openSearch,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = FloatingNavBottomMargin, start = 16.dp, end = 16.dp),
            )
        }
    }
}
```

删除内容：外层的 `AnimatedVisibility(visible = showBottomNav, ...)` 包裹、`showBottomNav` 变量、
以及 `Children` / `predictiveBackAnimation` / `slide` 的旧导入。

- [ ] **Step 2: `HomeConnectButton` 加首帧保护**

`Idle` 被释放后每次返回首页都会重放连接按钮的弹跳。把 `HomeConnectButton.kt:58` 的
`LaunchedEffect(isConnected) {` 块替换为：

```kotlin
    // Idle 子节点在压栈时会被释放，返回时是一次全新组合。没有这个保护，每次从设置页返回
    // 都会重放一次入场弹跳。
    var skipInitialBounce by remember { mutableStateOf(true) }
    LaunchedEffect(isConnected) {
        if (skipInitialBounce) {
            skipInitialBounce = false
            return@LaunchedEffect
        }
        scale.animateTo(
            targetValue = 1.2f,
            animationSpec = tween(durationMillis = 150),
        )
        scale.animateTo(
            targetValue = 1.0f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
        )
    }
```

补 import：`androidx.compose.runtime.mutableStateOf`、`androidx.compose.runtime.remember`、
`androidx.compose.runtime.setValue`、`androidx.compose.runtime.getValue`（按编译器提示取用）。

- [ ] **Step 3: 编译并跑测试**

```powershell
.\gradlew.bat :shared:compileDebugKotlinAndroid :shared:testDebugUnitTest :androidApp:assembleDebug
```

Expected: 三项全部 `BUILD SUCCESSFUL`。

- [ ] **Step 4: 装机验证（Checkpoint 2——本任务最关键的一步）**

此时页面切换应是 **淡入淡出 + 轻微缩放**（不再横向滑动），且**没有**容器变换。逐项确认：

1. **滚动位置保持**：Config 列表滚到中部 → 进设置 → 返回 → 列表仍在原位置
2. **标题折叠保持**：列表滚动使标题折叠 → 进节点编辑 → 返回 → 折叠状态未重置
3. **tab 保持**：停在 Config tab → 进设置 → 返回 → 仍在 Config tab
4. **连接按钮不再乱弹**：首页 → 进设置 → 返回 → 连接按钮**没有**重放弹跳
5. **底栏**：进入任意全屏页时底栏消失，返回时出现
6. **预测返回手势**：在设置页、编辑页、搜索页从屏幕边缘右划，页面跟手，松手完成返回
7. **重组成本**（spec 第 10 节的残余风险）：反复「进设置 → 返回」十余次，观察返回动画**起始瞬间**
   是否有可感知的卡顿
8. logcat 无 `NoSuchMethodError`

**第 7 项若卡顿明显**：停止后续任务，回报结果。收敛办法见 spec 第 10 节「残余风险」——
退回 `sharedElementWithCallerManagedVisibility` 方案（`ChildPages` 留在 `ChildStack` 外）。

- [ ] **Step 5: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/
git commit -m "refactor: nest ChildPages inside ChildStack to enable shared element scopes"
```

---

# Phase 3 — 转场接入（Checkpoint 3）

## Task 6: 转场基础设施与底栏 overlay

**Files:**
- Create: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/transitions/SharedKeys.kt`
- Create: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/transitions/XrayMotion.kt`
- Create: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/transitions/TransitionScopes.kt`
- Create: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/transitions/SharedContainer.kt`
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/RootContent.kt`

**Interfaces:**
- Consumes: Task 5 的 `ChildStack` 结构。
- Produces:
  - `data class SharedContainerKey(val destination: String)`
  - `object TransitionDestinations`，成员：`SETTINGS`、`SUBSCRIPTIONS`、`SEARCH`、`APPS`、`LOGCAT`、`ROUTE`、`QR`、`NODE_EDIT_NEW`（均为 `const val String`）与 `fun nodeEdit(nodeId: Int): String`
  - `object XrayMotion`，成员：`ContainerBounds: BoundsTransform`、`EffectsFloat: FiniteAnimationSpec<Float>`、`SpatialDp: FiniteAnimationSpec<Dp>`、`SpatialIntOffset: FiniteAnimationSpec<IntOffset>`
  - `val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?>`
  - `val LocalStackAnimationScope: ProvidableCompositionLocal<AnimatedVisibilityScope?>`
  - `@Composable fun Modifier.sharedContainer(destination: String, shape: Shape, containerColor: Color): Modifier`
  - `@Composable fun rememberDestinationShape(startCorner: Dp): Shape`
  - `@Composable fun Modifier.floatingNavOverlay(): Modifier`

- [ ] **Step 1: `SharedKeys.kt`**

```kotlin
package com.android.xrayfa.shared.ui.transitions

/**
 * 共享容器的配对 key。用 data class 而非裸字符串：字符串拼错会导致静默不匹配
 * （没有动画，也没有报错）。
 */
data class SharedContainerKey(val destination: String)

object TransitionDestinations {
    const val SETTINGS = "settings"
    const val SUBSCRIPTIONS = "subscriptions"
    const val SEARCH = "search"
    const val APPS = "apps"
    const val LOGCAT = "logcat"
    const val ROUTE = "route"
    const val QR = "qr"
    const val NODE_EDIT_NEW = "node-edit-new"

    fun nodeEdit(nodeId: Int): String = if (nodeId > 0) "node-edit-$nodeId" else NODE_EDIT_NEW
}
```

- [ ] **Step 2: `XrayMotion.kt`**

```kotlin
package com.android.xrayfa.shared.ui.transitions

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

/**
 * M3 Expressive 动效取值，硬编码为 spring 常量。
 *
 * 刻意不使用 `MaterialTheme.motionScheme`：本项目在 commonMain 引用较新的 material3 API 会在
 * Android 上抛 NoSuchMethodError（`androidApp` 同时依赖 androidx Compose BOM，运行时由 androidx
 * material3 胜出，而 `:shared` 按 JB material3 编译）。已有先例见 `SharedModalBottomSheet` 与
 * `SharedSearchChrome` 的注释。本文件只依赖 `androidx.compose.animation.core`。
 *
 * 选 spring 而非 tween：预测返回手势把进度直接喂给同一个 transition，spring 被打断和反向时自然。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
object XrayMotion {
    /** expressive slow spatial（0.8 / 200）—— 全屏级容器扩张。 */
    val ContainerBounds: BoundsTransform =
        BoundsTransform { _, _ ->
            spring(
                dampingRatio = 0.8f,
                stiffness = 200f,
                visibilityThreshold = Rect.VisibilityThreshold,
            )
        }

    /** effects default（1.0 / 1600）—— 透明度与颜色，永不过冲。 */
    val EffectsFloat: FiniteAnimationSpec<Float> =
        spring(dampingRatio = 1f, stiffness = 1600f)

    /** expressive default spatial（0.8 / 380）—— 圆角插值等局部尺寸变化。 */
    val SpatialDp: FiniteAnimationSpec<Dp> =
        spring(dampingRatio = 0.8f, stiffness = 380f, visibilityThreshold = Dp.VisibilityThreshold)

    /** expressive default spatial（0.8 / 380）—— 底栏滑出等局部位移。 */
    val SpatialIntOffset: FiniteAnimationSpec<IntOffset> =
        spring(
            dampingRatio = 0.8f,
            stiffness = 380f,
            visibilityThreshold = IntOffset.VisibilityThreshold,
        )
}
```

- [ ] **Step 3: `TransitionScopes.kt`**

```kotlin
package com.android.xrayfa.shared.ui.transitions

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 作用域经 CompositionLocal 下传，而非参数透传：多个转场的起点要穿过 `ChildPages` 的 `PagerScope`
 * 再套两层 Screen 函数，透传会污染七八个函数签名。
 *
 * 用 `staticCompositionLocalOf`（与 `LocalPlatformRootHooks` 一致）：值变化时整棵子树重组，
 * 这让 [Modifier.sharedContainer] 里的条件式 `remember` 是安全的。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** Decompose `ChildStack` 交出的作用域。3.5.0 的 `StackAnimationScope` 继承自本类型。 */
val LocalStackAnimationScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }
```

- [ ] **Step 4: `SharedContainer.kt`**

```kotlin
package com.android.xrayfa.shared.ui.transitions

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 容器变换的统一编排。两端共用同一实现，避免手抄修饰符顺序出错。
 *
 * 修饰符顺序是强制的：
 * - 背景与 `clip` 必须在 `sharedBounds` **之后**，才会跟着元素进入 overlay 渲染
 * - 调用方的尺寸与 padding 修饰符必须加在 `.sharedContainer()` **之后**；加在之前会参与
 *   初始/目标边界的推导，两端不一致就会看到元素"跳"一下
 *
 * 作用域缺失时返回原 `Modifier`：未接入的调用点只是没有动画，不会崩。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedContainer(
    destination: String,
    shape: Shape,
    containerColor: Color,
): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val avScope = LocalStackAnimationScope.current ?: return this
    return with(sharedScope) {
        this@sharedContainer
            .sharedBounds(
                sharedContentState = rememberSharedContentState(SharedContainerKey(destination)),
                animatedVisibilityScope = avScope,
                enter = fadeIn(XrayMotion.EffectsFloat),
                exit = fadeOut(XrayMotion.EffectsFloat),
                boundsTransform = XrayMotion.ContainerBounds,
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
            )
            .background(containerColor, shape)
            .clip(shape)
    }
}

/**
 * 目的地侧的形状：从 [startCorner] 插值到直角。
 *
 * Compose 不支持自动形状插值，这里借 `AnimatedVisibilityScope.transition` 手动驱动。
 * 只用在目的地侧 —— 起点侧传自己的固有形状即可（起点会淡出，不需要变形）。
 */
@Composable
fun rememberDestinationShape(startCorner: Dp): Shape {
    val avScope = LocalStackAnimationScope.current ?: return RectangleShape
    val corner by avScope.transition.animateDp(
        transitionSpec = { XrayMotion.SpatialDp },
        label = "sharedContainerCorner",
    ) { state ->
        if (state == EnterExitState.Visible) 0.dp else startCorner
    }
    return RoundedCornerShape(corner)
}

/**
 * 浮动底栏的 overlay 处理。
 *
 * `renderInSharedTransitionScopeOverlay` 有两个作用：避免被提到 overlay 的共享容器盖住，
 * 以及把底栏从 `Idle` 子节点的 `scale` graphicsLayer 里提出来，免得它跟着整页缩放。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.floatingNavOverlay(): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val avScope = LocalStackAnimationScope.current ?: return this
    return with(sharedScope) {
        this@floatingNavOverlay.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
    }.then(
        with(avScope) {
            Modifier.animateEnterExit(
                enter =
                    fadeIn(XrayMotion.EffectsFloat) +
                        slideInVertically(XrayMotion.SpatialIntOffset) { it },
                exit =
                    fadeOut(XrayMotion.EffectsFloat) +
                        slideOutVertically(XrayMotion.SpatialIntOffset) { it },
            )
        },
    )
}
```

- [ ] **Step 5: 在 `RootContent` 提供两个作用域**

对 Task 5 写好的 `RootContent` 做两处**包裹**，不动 `ChildStack` 的实参、也不动 `when` 的任何分支。

（a）把最外层的

```kotlin
    Box(modifier = modifier.fillMaxSize()) {
        ChildStack(
```

替换为

```kotlin
    SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            ChildStack(
```

并在文件末尾相应补上一个闭合括号（`SharedTransitionLayout` 的 lambda 里多了一层
`CompositionLocalProvider`）。

（b）把 `ChildStack` 的 content lambda 从

```kotlin
        ) { child ->
            val fill = Modifier.fillMaxSize()
            when (val instance = child.instance) {
```

替换为

```kotlin
        ) { child ->
            CompositionLocalProvider(LocalStackAnimationScope provides this) {
                val fill = Modifier.fillMaxSize()
                when (val instance = child.instance) {
```

同样补闭合括号。

`RootContent` 的注解改为：

```kotlin
@OptIn(ExperimentalDecomposeApi::class, ExperimentalSharedTransitionApi::class)
```

新增 import：`androidx.compose.animation.ExperimentalSharedTransitionApi`、
`androidx.compose.animation.SharedTransitionLayout`、
`androidx.compose.runtime.CompositionLocalProvider`、
`com.android.xrayfa.shared.ui.transitions.LocalSharedTransitionScope`、
`com.android.xrayfa.shared.ui.transitions.LocalStackAnimationScope`。

- [ ] **Step 6: 底栏接上 overlay**

`IdleContent` 里 `XrayFloatingNav` 的 `modifier` 改为（`floatingNavOverlay()` 必须在
尺寸/padding 修饰符**之前**）：

```kotlin
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .floatingNavOverlay()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = FloatingNavBottomMargin, start = 16.dp, end = 16.dp),
```

`FloatingNavBottomFade` 不加 overlay —— 它是背景渐变，被容器盖住是正确的。

- [ ] **Step 7: 编译**

```powershell
.\gradlew.bat :shared:compileDebugKotlinAndroid :androidApp:assembleDebug
```

Expected: 两项 `BUILD SUCCESSFUL`。

- [ ] **Step 8: 装机验证**

此时仍然**没有**容器变换（还没有任何调用点用 `sharedContainer`），但底栏行为应有变化：

1. 进入全屏页时底栏**向下滑出 + 淡出**（不是直接消失）
2. 返回时底栏**向上滑入 + 淡入**
3. 页面本体仍是淡入淡出 + 轻微缩放
4. 底栏滑出过程中**不会**跟着整页一起缩小
5. logcat 无 `NoSuchMethodError`

- [ ] **Step 9: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/
git commit -m "feat: add shared transition infrastructure and floating nav overlay"
```

---

## Task 7: 转场 —— 设置行到 Apps / 日志 / 路由

先做这三个：两端同在 `ChildStack` 内，是最标准的场景，用来验证基础设施。

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/platform/PlatformRootHooks.kt`
- Modify: `shared/src/iosMain/kotlin/com/android/xrayfa/shared/ui/platform/IosPlatformRootHooks.kt`
- Modify: `androidApp/src/main/java/com/android/xrayfa/ui/AndroidPlatformRootHooks.kt`
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/settings/SharedAppsInfoScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/settings/SharedAppLogScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/settings/SharedRouteSettingsScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/SettingsTabScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/RootContent.kt`

**Interfaces:**
- Consumes: Task 6 的 `sharedContainer`、`rememberDestinationShape`、`TransitionDestinations`。
- Produces: `PlatformRootHooks.AppsScreen` / `LogcatScreen` / `QrScannerScreen` 三个方法各新增末位参数 `modifier: Modifier = Modifier`。

- [ ] **Step 1: `PlatformRootHooks` 接口加 `modifier`**

三处签名改为：

```kotlin
    @Composable
    fun AppsScreen(
        component: SettingsComponent,
        onBack: () -> Unit,
        modifier: Modifier = Modifier,
    )

    @Composable
    fun LogcatScreen(
        onBack: () -> Unit,
        modifier: Modifier = Modifier,
    )

    @Composable
    fun QrScannerScreen(
        onResult: (String) -> Unit,
        onBack: () -> Unit,
        title: String,
        permissionRequiredMessage: String,
        modifier: Modifier = Modifier,
    )
```

`DefaultPlatformRootHooks` 的三个 `override` 同步加参数，并把 `modifier` 下传：
`SharedInDevelopmentScreen(..., modifier = modifier)`、`SharedInProcessAppLogScreen(..., modifier = modifier)`、
`SharedQrScannerScreen(..., modifier = modifier)`。若这些 composable 尚无 `modifier` 参数，
一并加上 `modifier: Modifier = Modifier` 并下传到其根 `SharedListScaffold`。

- [ ] **Step 2: 两个平台实现同步签名**

`IosPlatformRootHooks.kt` 与 `AndroidPlatformRootHooks.kt` 的三个 `override` 加同样的
`modifier: Modifier = Modifier` 参数并下传。`AndroidAppsScreen` / `AndroidLogcatScreen` /
`QRCodeScannerScreen` 若无 `modifier` 参数，加上并下传到其根布局。

- [ ] **Step 3: 三个共享屏幕根部接受 `modifier`**

`SharedAppsInfoScreen.kt`、`SharedAppLogScreen.kt`、`SharedRouteSettingsScreen.kt`：
确认公开 composable 有 `modifier: Modifier = Modifier` 参数，并把它传给根 `SharedListScaffold(modifier = modifier, ...)`。

- [ ] **Step 4: 起点 —— 设置页三行挂共享容器**

`SettingsTabScreen.kt` 里，`SharedSettingsPlatformSection` 改为传三个 modifier
（利用已有的 `appsModifier` / `logcatModifier` / `routeModifier` 参数）：

```kotlin
            val rowColor = MaterialTheme.colorScheme.surfaceContainerLow
            SharedSettingsPlatformSection(
                labels = settingsLabels,
                onAppsClick = onAppsClick,
                onLogcatClick = onLogcatClick,
                onRouteClick = onRouteClick,
                appsModifier =
                    Modifier.sharedContainer(
                        destination = TransitionDestinations.APPS,
                        shape = RectangleShape,
                        containerColor = rowColor,
                    ),
                logcatModifier =
                    Modifier.sharedContainer(
                        destination = TransitionDestinations.LOGCAT,
                        shape = RectangleShape,
                        containerColor = rowColor,
                    ),
                routeModifier =
                    Modifier.sharedContainer(
                        destination = TransitionDestinations.ROUTE,
                        shape = RectangleShape,
                        containerColor = rowColor,
                    ),
            )
```

- [ ] **Step 5: 终点 —— 三个目的地挂同 key 的共享容器**

`RootContent.kt` 的 `when` 分支里：

```kotlin
                RootComponent.StackChild.Apps ->
                    platformHooks.AppsScreen(
                        component = component.settingsComponent,
                        onBack = component::navigateBack,
                        modifier =
                            fill.sharedContainer(
                                destination = TransitionDestinations.APPS,
                                shape = RectangleShape,
                                containerColor = MaterialTheme.colorScheme.background,
                            ),
                    )
                RootComponent.StackChild.Logcat ->
                    platformHooks.LogcatScreen(
                        onBack = component::navigateBack,
                        modifier =
                            fill.sharedContainer(
                                destination = TransitionDestinations.LOGCAT,
                                shape = RectangleShape,
                                containerColor = MaterialTheme.colorScheme.background,
                            ),
                    )
                RootComponent.StackChild.RouteSettings ->
                    SharedRouteSettingsScreen(
                        component = component.settingsComponent,
                        onBack = component::navigateBack,
                        labels = routeSettingsLabels,
                        modifier =
                            fill.sharedContainer(
                                destination = TransitionDestinations.ROUTE,
                                shape = RectangleShape,
                                containerColor = MaterialTheme.colorScheme.background,
                            ),
                    )
```

注意 `fill.sharedContainer(...)` —— `fillMaxSize()` 在前是刻意的：它是**约束**而非尺寸覆盖，
两端保持一致即可。

- [ ] **Step 6: 编译**

```powershell
.\gradlew.bat :shared:compileDebugKotlinAndroid :androidApp:assembleDebug
```

Expected: 两项 `BUILD SUCCESSFUL`。

- [ ] **Step 7: 装机验证**

1. 设置页点「Apps」→ **该行边界扩张成全屏**，行内容淡出、Apps 页内容淡入
2. 返回 → 全屏回缩到那一行的位置
3. 「日志」「路由」同样
4. 转场中底栏不露头、内容不溢出
5. 若**没有**动画（只是淡入淡出），说明 key 没配上。临时在起点加
   `rememberSharedContentState(SharedContainerKey(TransitionDestinations.APPS)).isMatchFound` 打日志排查

- [ ] **Step 8: Commit**

```powershell
git add shared/src/commonMain/ shared/src/iosMain/ androidApp/src/main/java/com/android/xrayfa/ui/AndroidPlatformRootHooks.kt
git commit -m "feat: container transform from settings rows to their sub-pages"
```

---

## Task 8: 转场 —— 节点行到全屏编辑

本计划的主角转场。

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/ConfigTabScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/RootContent.kt`

**Interfaces:**
- Consumes: Task 6 的 `sharedContainer`、`TransitionDestinations.nodeEdit(nodeId)`；`SharedConfigSection` 已有的 `rowModifier: (Node) -> Modifier`。
- Produces: 无新公开 API。

- [ ] **Step 1: 起点 —— 用现成的 `rowModifier`**

`ConfigTabScreen.kt` 的 `SharedConfigSection(...)` 调用里新增：

```kotlin
                rowModifier = { node ->
                    Modifier.sharedContainer(
                        destination = TransitionDestinations.nodeEdit(node.id),
                        shape = RectangleShape,
                        containerColor = Color.Transparent,
                    )
                },
```

`containerColor = Color.Transparent` 是刻意的：节点行在平铺列表里本来没有背景，加背景会破坏
现有视觉。转场期间的不透明填充由进场那侧（编辑页）提供 —— `sharedBounds` 会同时渲染两侧。

- [ ] **Step 2: 终点 —— 编辑页挂同 key**

`RootContent.kt` 的 `NodeEdit` 分支里，`SharedEditScreen` 的 `modifier` 改为：

```kotlin
                        modifier =
                            fill.sharedContainer(
                                destination = TransitionDestinations.nodeEdit(instance.nodeId),
                                shape = RectangleShape,
                                containerColor = MaterialTheme.colorScheme.background,
                            ),
```

`instance.nodeId` 与起点的 `node.id` 必须是同一个值 —— `TransitionDestinations.nodeEdit()`
对 `nodeId <= 0` 统一返回 `NODE_EDIT_NEW`，所以新建场景不会和任何节点行误配。

- [ ] **Step 3: 编译**

```powershell
.\gradlew.bat :shared:compileDebugKotlinAndroid :androidApp:assembleDebug
```

Expected: 两项 `BUILD SUCCESSFUL`。

- [ ] **Step 4: 装机验证**

1. Config 页点某个节点行的编辑图标 → **该行纵向扩张成全屏编辑页**
2. 返回 → 回缩到原来那一行的位置
3. **列表滚到中部**再点某行的编辑 → 返回 → 回缩位置正确（这一项直接检验 Task 3 的状态提升）
4. 点不同的两行 → 各自回缩到各自的位置（key 唯一性）
5. 进编辑页后**返回手势**拖到一半松手 → 容器跟手回弹，不跳变
6. 顶栏 Edit 图标（新建节点）此时仍无容器变换 —— Task 10 处理

**边界确认**：把列表滚到很远使源行离开视口，再返回 —— 应退化为淡入淡出，**不崩、不卡死**。

- [ ] **Step 5: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/
git commit -m "feat: container transform from node row to full-screen edit"
```

---

## Task 9: 转场 —— 底栏放大镜到全屏搜索

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/RootContent.kt`

**Interfaces:**
- Consumes: Task 6 的 `sharedContainer`、`rememberDestinationShape`；Task 2 的 `SharedSearchScreen`。
- Produces: 无新公开 API。

- [ ] **Step 1: 起点 —— 底栏的放大镜**

`IdleContent` 里 `XrayFloatingNav` 的 `trailingContent` 改为把共享容器挂在图标外层的 `Box` 上。
放大镜按钮是 `FloatingNavBarHeight`（64dp）的圆形，所以起点形状用 `CircleShape`：

```kotlin
                trailingContent = {
                    Box(
                        modifier =
                            Modifier
                                .sharedContainer(
                                    destination = TransitionDestinations.SEARCH,
                                    shape = CircleShape,
                                    containerColor = Color.Transparent,
                                )
                                .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = configLabels.searchLabel,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(26.dp),
                        )
                    }
                },
```

`containerColor = Color.Transparent`：`XrayFloatingNav` 自己的 `Surface` 已经画了圆形底色，
再叠一层会变色。

- [ ] **Step 2: 终点 —— 搜索页用动画圆角**

`Search` 分支里 `SharedSearchScreen` 的 `modifier` 改为：

```kotlin
                            modifier =
                                fill.sharedContainer(
                                    destination = TransitionDestinations.SEARCH,
                                    shape = rememberDestinationShape(startCorner = 28.dp),
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
```

`28.dp` 对应 64dp 圆形按钮的半径，起点看起来才是圆的。

- [ ] **Step 3: 编译**

```powershell
.\gradlew.bat :shared:compileDebugKotlinAndroid :androidApp:assembleDebug
```

Expected: 两项 `BUILD SUCCESSFUL`。

- [ ] **Step 4: 装机验证**

1. 点底栏放大镜 → **圆形从图标位置扩张成全屏**，圆角逐渐拉直
2. 底栏同步向下滑出，两者不互相遮挡
3. 输入法自动弹出，光标在输入框
4. 返回 → 全屏回缩成圆形回到放大镜位置，底栏滑回
5. 返回手势拖到一半松手 → 跟手回弹

- [ ] **Step 5: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/
git commit -m "feat: container transform from nav search icon to full-screen search"
```

---

## Task 10: 转场 —— 齿轮到设置、溢出按钮到订阅、Edit 到新建、菜单到二维码（Checkpoint 3）

这四个共用同一套「小图标起点」模式。

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/home/HomeTopBar.kt`
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/HomeTabScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/ConfigTabScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/subscription/SharedSubscriptionScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/RootContent.kt`

**Interfaces:**
- Consumes: Task 6 的全部产物。
- Produces: `HomeTopBar(onSettingsClick: () -> Unit, settingsIconModifier: Modifier = Modifier)`。

- [ ] **Step 1: `HomeTopBar` 让齿轮接受 modifier**

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    onSettingsClick: () -> Unit,
    settingsIconModifier: Modifier = Modifier,
) {
    TopAppBar(
        title = {
            Text(
                text = "XrayFA",
                fontWeight = FontWeight.Bold,
            )
        },
        actions = {
            IconButton(onClick = onSettingsClick, modifier = settingsIconModifier) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = MaterialTheme.colorScheme.surface,
            ),
    )
}
```

补 import `androidx.compose.ui.Modifier`。

- [ ] **Step 2: 齿轮 → 设置**

`HomeTabScreen.kt` 的 `HomeTopBar` 调用改为：

```kotlin
            HomeTopBar(
                onSettingsClick = onSettingsClick,
                settingsIconModifier =
                    Modifier.sharedContainer(
                        destination = TransitionDestinations.SETTINGS,
                        shape = CircleShape,
                        containerColor = Color.Transparent,
                    ),
            )
```

`RootContent.kt` 的 `Settings` 分支：

```kotlin
                        modifier =
                            fill.sharedContainer(
                                destination = TransitionDestinations.SETTINGS,
                                shape = rememberDestinationShape(startCorner = 20.dp),
                                containerColor = MaterialTheme.colorScheme.background,
                            ),
```

- [ ] **Step 3: 溢出按钮 → 订阅 / 二维码**

两个目的地共用同一个起点按钮（用户点的就是它），但 key 不同，所以要挂两层。
`sharedContainer` 是 `Modifier` 扩展，可以链式叠加 —— 外层是订阅、内层是二维码：

`ConfigTabScreen.kt` 的 `SharedConfigImportMenu(...)` 增加：

```kotlin
                modifier =
                    Modifier
                        .sharedContainer(
                            destination = TransitionDestinations.SUBSCRIPTIONS,
                            shape = CircleShape,
                            containerColor = Color.Transparent,
                        )
                        .sharedContainer(
                            destination = TransitionDestinations.QR,
                            shape = CircleShape,
                            containerColor = Color.Transparent,
                        ),
```

`RootContent.kt` 的两个分支：

```kotlin
                is RootComponent.StackChild.Subscriptions ->
                    SharedSubscriptionScreen(
                        // ... 其余参数不变
                        modifier =
                            fill.sharedContainer(
                                destination = TransitionDestinations.SUBSCRIPTIONS,
                                shape = rememberDestinationShape(startCorner = 20.dp),
                                containerColor = MaterialTheme.colorScheme.background,
                            ),
                    )
                RootComponent.StackChild.QrScanner ->
                    platformHooks.QrScannerScreen(
                        // ... 其余参数不变
                        modifier =
                            fill.sharedContainer(
                                destination = TransitionDestinations.QR,
                                shape = rememberDestinationShape(startCorner = 20.dp),
                                containerColor = MaterialTheme.colorScheme.background,
                            ),
                    )
```

`SharedSubscriptionScreen` 需确认有 `modifier: Modifier = Modifier` 参数并传给根
`SharedListScaffold`；没有则加上。

> 订阅页自己的「扫码」action 按钮也能开二维码。它不加共享容器 —— 从订阅页进二维码时
> `QR` 这个 key 在起点侧没有匹配，会自动退化为淡入淡出。这是可接受的降级，避免
> 一个 key 出现两个可见起点。

- [ ] **Step 4: 顶栏 Edit → 新建节点**

`ConfigTabScreen.kt` 的 `actions` 里那个 `IconButton(onClick = { onOpenNodeEdit(0) })`：

```kotlin
            IconButton(
                onClick = { onOpenNodeEdit(0) },
                modifier =
                    Modifier.sharedContainer(
                        destination = TransitionDestinations.NODE_EDIT_NEW,
                        shape = CircleShape,
                        containerColor = Color.Transparent,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = configLabels.createConfigLabel,
                )
            }
```

终点已由 Task 8 的 `TransitionDestinations.nodeEdit(instance.nodeId)` 覆盖
（`nodeId = 0` → `NODE_EDIT_NEW`），但它当时传的是 `RectangleShape`。改为按 nodeId 区分形状：

```kotlin
                        modifier =
                            fill.sharedContainer(
                                destination = TransitionDestinations.nodeEdit(instance.nodeId),
                                shape =
                                    if (instance.nodeId > 0) {
                                        RectangleShape
                                    } else {
                                        rememberDestinationShape(startCorner = 20.dp)
                                    },
                                containerColor = MaterialTheme.colorScheme.background,
                            ),
```

空状态那个「添加」按钮（`onEmptyAddClick`）不加共享容器，同样走降级。

- [ ] **Step 5: 编译并跑全量测试**

```powershell
.\gradlew.bat :shared:compileDebugKotlinAndroid :shared:testDebugUnitTest :androidApp:assembleDebug
```

Expected: 三项 `BUILD SUCCESSFUL`，全部用例 PASS。

- [ ] **Step 6: 装机验证（Checkpoint 3 —— 全量回归）**

七个转场逐一确认「扩张 → 全屏」与「回缩 → 原位」：

1. 节点行 → 编辑
2. 底栏放大镜 → 搜索
3. 首页齿轮 → 设置
4. Config 溢出按钮 →（菜单）→ 订阅
5. Config 溢出按钮 →（菜单）→ 二维码扫描
6. Config 顶栏 Edit → 新建节点
7. 设置行 → Apps / 日志 / 路由

再确认：

8. 每个转场的返回手势都能拖到一半跟手回弹
9. 降级路径不崩：订阅页里点扫码（淡入淡出）、空状态点添加（淡入淡出）
10. 反复快速进出十余次，无残影、无元素卡在屏幕上、无 ANR
11. logcat 全程无 `NoSuchMethodError` / `NoSuchFieldError`
12. 深色模式下各转场的容器底色正确，无白闪

- [ ] **Step 7: iOS 静态检查**

无法在 Windows 上编译 iOS。人工确认 `commonMain` 新增/修改的文件没有 Android 专有引用：

```powershell
Select-String -Path shared\src\commonMain\kotlin\com\android\xrayfa\shared\ui\transitions\*.kt -Pattern "^import android"
```

Expected: 无输出。

并确认 `shared/src/iosMain/.../IosPlatformRootHooks.kt` 的三个 `override` 签名已与
Task 7 Step 1 的接口一致。**最终需在 macOS 上执行 `./gradlew :shared:compileKotlinIosSimulatorArm64` 验证。**

- [ ] **Step 8: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/
git commit -m "feat: container transforms for settings, subscriptions, QR and new node"
```

---

## 完成后的收尾

- [ ] 在 macOS 上执行 `./gradlew :shared:compileKotlinIosSimulatorArm64` 并在 iOS 模拟器上跑一遍七个转场
- [ ] 若 Task 5 Step 4 第 7 项（返回动画起始卡顿）被判定为不可接受，按 spec 第 10 节「残余风险」切换到 `sharedElementWithCallerManagedVisibility` 方案
