# Liquid Glass Floating Nav Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the opaque floating nav chrome with liquid glass: Android matches the Backdrop `LiquidBottomTabs` demo, iOS 26 uses system liquid glass, and earlier iOS uses `UIBlurEffect`.

**Architecture:** `XrayFloatingNav` keeps shared layout and tab callbacks. Pill, slider, and search circle go through `expect/actual` chrome. Android samples `ChildPages` with `layerBackdrop` and draws the catalog lens slider. The bottom fade stays outside that layer.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, `io.github.kyant0:backdrop` 2.0.1 (Android only), UIKit visual effects (iOS).

## Global Constraints

- Android effect matches Backdrop catalog `LiquidBottomTabs`: bar `vibrancy` + `blur(8.dp)` + `lens(24.dp, 24.dp)`; slider lens with press scale, damped drag, chromatic aberration, snap to nearest tab.
- Backdrop dependency is `io.github.kyant0:backdrop:2.0.1` on `shared` `androidMain` only.
- Kotlin and Compose Multiplatform rise to versions Backdrop 2.0.1 requires: Kotlin `2.4.10`, Compose Multiplatform `1.12.0`. KSP version must match Kotlin `2.4.10`.
- Search circle uses the same glass surface and is not inside the drag gesture.
- Icon scale and selected label color stay as they are in `XrayFloatingNav`.
- Android samples `ChildPages` only. `FloatingNavBottomFade` is not part of the backdrop.
- If the backdrop is missing or the effect cannot run, paint `MaterialTheme.colorScheme.surfaceContainerHighest`. Tabs stay tappable.
- iOS 26 uses `UIGlassEffect`. Earlier iOS uses `UIBlurEffect`. Same drag-and-snap index.
- Shared code must not reference `com.kyant.backdrop` types.

---

### Task 1: Align toolchain and add the Android dependency

**Files:**
- Modify: `gradle/libs.versions.toml` (versions `kotlin`, `ksp`, `composeMultiplatform`)
- Modify: `shared/build.gradle.kts` (`androidMain.dependencies`)

**Interfaces:**
- Consumes: none
- Produces: `implementation("io.github.kyant0:backdrop:2.0.1")` available to `androidMain` only

- [ ] **Step 1: Set versions**

In `gradle/libs.versions.toml` set:

```toml
kotlin = "2.4.10"
ksp = "2.4.10-1.0.31"
composeMultiplatform = "1.12.0"
```

If `2.4.10-1.0.31` is not a published KSP release, replace `ksp` with the release whose version starts with `2.4.10-` from https://github.com/google/ksp/releases before compiling. Do not leave Kotlin and KSP on different language versions.

- [ ] **Step 2: Add the dependency**

In `shared/build.gradle.kts` inside `androidMain.dependencies`:

```kotlin
implementation("io.github.kyant0:backdrop:2.0.1")
```

Do not add it to `commonMain` or `iosMain`.

- [ ] **Step 3: Compile Android shared**

Run: `./gradlew :shared:compileDebugKotlinAndroid`

Expected: BUILD SUCCESSFUL. If the failure is a KSP/Kotlin mismatch, fix only the `ksp` version and rerun. If the failure is Compose 1.12 API drift in unrelated modules, stop and report the first error file instead of editing those call sites in this task.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml shared/build.gradle.kts
git commit -m "build: align Kotlin and Compose with Backdrop 2.0.1"
```

### Task 2: Snap-index math

**Files:**
- Create: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/nav/NavSliderMath.kt`
- Test: `shared/src/androidUnitTest/kotlin/com/android/xrayfa/shared/ui/nav/NavSliderMathTest.kt`

**Interfaces:**
- Consumes: none
- Produces: `fun snapTabIndex(value: Float, tabsCount: Int): Int`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.android.xrayfa.shared.ui.nav

import kotlin.test.Test
import kotlin.test.assertEquals

class NavSliderMathTest {
    @Test
    fun snapsToNearestTab() {
        assertEquals(0, snapTabIndex(0.4f, 2))
        assertEquals(1, snapTabIndex(0.5f, 2))
        assertEquals(1, snapTabIndex(1.4f, 2))
    }

    @Test
    fun clampsToRange() {
        assertEquals(0, snapTabIndex(-1f, 2))
        assertEquals(1, snapTabIndex(3f, 2))
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :shared:testDebugUnitTest --tests com.android.xrayfa.shared.ui.nav.NavSliderMathTest`

Expected: compile failure, unresolved `snapTabIndex`.

- [ ] **Step 3: Implement**

```kotlin
package com.android.xrayfa.shared.ui.nav

import kotlin.math.roundToInt

fun snapTabIndex(value: Float, tabsCount: Int): Int {
    val last = (tabsCount - 1).coerceAtLeast(0)
    return value.roundToInt().coerceIn(0, last)
}
```

- [ ] **Step 4: Run the test**

Run: `./gradlew :shared:testDebugUnitTest --tests com.android.xrayfa.shared.ui.nav.NavSliderMathTest`

Expected: BUILD SUCCESSFUL and the two tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/nav/NavSliderMath.kt shared/src/androidUnitTest/kotlin/com/android/xrayfa/shared/ui/nav/NavSliderMathTest.kt
git commit -m "test: snap liquid-nav drag to the nearest tab"
```

### Task 3: Android liquid chrome matching the catalog demo

**Files:**
- Create: `shared/src/androidMain/kotlin/com/android/xrayfa/shared/ui/nav/DampedDragAnimation.kt`
- Create: `shared/src/androidMain/kotlin/com/android/xrayfa/shared/ui/nav/InteractiveHighlight.kt`
- Create: `shared/src/androidMain/kotlin/com/android/xrayfa/shared/ui/nav/LiquidNavBar.kt`
- Create: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/nav/FloatingNavChrome.kt`
- Create: `shared/src/androidMain/kotlin/com/android/xrayfa/shared/ui/nav/FloatingNavChrome.android.kt`
- Create: `shared/src/iosMain/kotlin/com/android/xrayfa/shared/ui/nav/FloatingNavChrome.ios.kt` (stub only in this task)

**Interfaces:**
- Consumes: `snapTabIndex(value: Float, tabsCount: Int): Int`
- Produces:

```kotlin
@Composable
expect fun FloatingNavChrome(
    selectedIndex: Int,
    onIndexSettled: (Int) -> Unit,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)

@Composable
expect fun FloatingNavSearchChrome(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)
```

- [ ] **Step 1: Copy catalog helpers**

From tag `2.0.1` of https://github.com/Kyant0/AndroidLiquidGlass copy these files into the android paths above and change only the package to `com.android.xrayfa.shared.ui.nav`:

- `app/src/commonMain/kotlin/com/kyant/backdrop/catalog/utils/DampedDragAnimation.kt`
- `app/src/commonMain/kotlin/com/kyant/backdrop/catalog/utils/InteractiveHighlight.kt`

Keep their public constructors and fields (`value`, `pressProgress`, `scaleX`, `scaleY`, `velocity`, `modifier`, `animateToValue`, `updateValue`).

- [ ] **Step 2: Declare the expect API**

`FloatingNavChrome.kt`:

```kotlin
package com.android.xrayfa.shared.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun FloatingNavChrome(
    selectedIndex: Int,
    onIndexSettled: (Int) -> Unit,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)

@Composable
expect fun FloatingNavSearchChrome(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)
```

- [ ] **Step 3: iOS stub so common code compiles**

`FloatingNavChrome.ios.kt` paints `MaterialTheme.colorScheme.surfaceContainerHighest` with `RoundedCornerShape(32.dp)` for the bar and `CircleShape` for search, and calls `onIndexSettled` from a click on each half when `tabsCount == 2`. Task 5 replaces this body. Search calls `onClick`.

- [ ] **Step 4: Android bar and slider**

`LiquidNavBar.kt` is the catalog `LiquidBottomTabs` body with these fixed differences:

- Package `com.android.xrayfa.shared.ui.nav`.
- Parameters: `selectedIndex: Int`, `onIndexSettled: (Int) -> Unit`, `tabsCount: Int`, `backdrop: Backdrop`, `modifier`, `content`.
- On drag stop, `onIndexSettled(snapTabIndex(targetValue, tabsCount))`.
- Bar effects, copied from the catalog, stay:

```kotlin
effects = {
    vibrancy()
    blur(8f.dp.toPx())
    lens(24f.dp.toPx(), 24f.dp.toPx())
}
```

- Slider effects stay:

```kotlin
lens(
    10f.dp.toPx() * progress,
    14f.dp.toPx() * progress,
    chromaticAberration = true,
)
```

- Container color stays light `0xFFFAFAFA` at alpha `0.4` and dark `0xFF121212` at alpha `0.4`.
- Height stays `64.dp` for the bar and `56.dp` for the lens.

`FloatingNavChrome.android.kt` reads a composition local:

```kotlin
val LocalFloatingNavBackdrop = staticCompositionLocalOf<Backdrop?> { null }
```

Define that local in `androidMain` only. `FloatingNavChrome` uses `LocalFloatingNavBackdrop.current`. When it is null, draw `surfaceContainerHighest` and a clickable row of `tabsCount` slots that calls `onIndexSettled`. When it is non-null, call `LiquidNavBar`.

`FloatingNavSearchChrome` on Android draws a circle with the same `drawBackdrop` effects as the bar (`vibrancy`, `blur(8.dp)`, `lens(24.dp, 24.dp)`), then `content`, and `clickable` calls `onClick`. Null backdrop uses the solid circle.

- [ ] **Step 5: Compile**

Run: `./gradlew :shared:compileDebugKotlinAndroid`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add shared/src/androidMain/kotlin/com/android/xrayfa/shared/ui/nav shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/nav/FloatingNavChrome.kt shared/src/iosMain/kotlin/com/android/xrayfa/shared/ui/nav/FloatingNavChrome.ios.kt
git commit -m "feat: add Android liquid-glass nav chrome"
```

### Task 4: Sample tab pages and replace the shared surfaces

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/nav/XrayFloatingNav.kt`
- Modify: `shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/RootContent.kt`
- Modify: `shared/src/androidMain/kotlin/com/android/xrayfa/shared/ui/nav/FloatingNavChrome.android.kt` (export a provider)

**Interfaces:**
- Consumes: `FloatingNavChrome`, `FloatingNavSearchChrome`
- Produces: `RootContent` installs the backdrop behind the nav on Android

- [ ] **Step 1: Add an expect provider**

In `FloatingNavChrome.kt`:

```kotlin
@Composable
expect fun ProvideFloatingNavBackdrop(
    content: @Composable () -> Unit,
)
```

Android actual wraps `content` in `rememberLayerBackdrop()` and `Modifier.layerBackdrop`, and provides `LocalFloatingNavBackdrop`. iOS actual calls `content()` with no wrapper.

- [ ] **Step 2: Wrap tab pages**

In `RootContent` `IdleContent`, wrap the `ChildPages` call:

```kotlin
ProvideFloatingNavBackdrop {
    ChildPages(
        modifier = Modifier.fillMaxSize(),
        pages = component.pages,
        onPageSelected = component::onPageSelected,
        scrollAnimation = PagesScrollAnimation.Default,
    ) { _, child ->
        // existing when(child) unchanged
    }
}
```

Leave `FloatingNavBottomFade` as a sibling of the nav, outside `ProvideFloatingNavBackdrop`.

- [ ] **Step 3: Replace surfaces in `XrayFloatingNav`**

Remove the pill `Surface` and the indicator `Box` that uses `selectedColor.copy(alpha = 0.12f)`. Put icon row `content` inside:

```kotlin
FloatingNavChrome(
    selectedIndex = selectedIndex,
    onIndexSettled = { index ->
        items.getOrNull(index)?.let(onItemSelected)
    },
    tabsCount = itemCount,
    modifier = Modifier.width(barWidth).height(FloatingNavBarHeight),
) {
    // existing Row of icons and labels, without the offset indicator Box
}
```

Replace the search `Surface` with:

```kotlin
FloatingNavSearchChrome(
    onClick = { onTrailingClick?.invoke() },
    modifier = Modifier.size(FloatingNavBarHeight),
) {
    trailingContent()
}
```

Keep the 12.dp gap, icon scale, and label color animation.

- [ ] **Step 4: Compile**

Run: `./gradlew :shared:compileDebugKotlinAndroid`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/nav/XrayFloatingNav.kt shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/nav/FloatingNavChrome.kt shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/RootContent.kt shared/src/androidMain/kotlin/com/android/xrayfa/shared/ui/nav shared/src/iosMain/kotlin/com/android/xrayfa/shared/ui/nav/FloatingNavChrome.ios.kt
git commit -m "feat: sample tab pages behind the floating nav"
```

### Task 5: iOS glass and blur chrome

**Files:**
- Modify: `shared/src/iosMain/kotlin/com/android/xrayfa/shared/ui/nav/FloatingNavChrome.ios.kt`

**Interfaces:**
- Consumes: `FloatingNavChrome`, `FloatingNavSearchChrome`, `snapTabIndex`
- Produces: iOS 26 `UIGlassEffect`, earlier `UIBlurEffect`, drag snap

- [ ] **Step 1: Replace the stub**

Use `androidx.compose.ui.viewinterop.UIKitView` behind the Compose `content`.

```kotlin
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun glassOrBlurView(): UIVisualEffectView {
    val effect =
        if (UIDevice.currentDevice.systemVersion.substringBefore(".").toInt() >= 26) {
            UIGlassEffect()
        } else {
            UIBlurEffect.effectWithStyle(UIBlurEffectStyle.UIBlurEffectStyleSystemMaterial)
        }
    return UIVisualEffectView(effect)
}
```

`FloatingNavChrome` stacks that view clipped to a capsule (corner radius = height / 2) and overlays `content`. A horizontal drag tracks translation in tab widths, and on end calls `onIndexSettled(snapTabIndex(index, tabsCount))`. The lens view for the slider is a second `UIVisualEffectView`, width = bar width / `tabsCount`, offset by the drag index. It does not use Backdrop.

`FloatingNavSearchChrome` is one circular `UIVisualEffectView` plus `content` and a tap that calls `onClick`.

If `UIGlassEffect` is absent from the linked iOS SDK, keep the `systemVersion >= 26` branch and add the SDK import the Xcode version in this repo already uses. Do not fall back to blur on iOS 26.

- [ ] **Step 2: Compile the simulator target**

Run: `./gradlew :shared:compileKotlinIosSimulatorArm64`

Expected: BUILD SUCCESSFUL when `enableIosTargets` is true. If that property is false, skip this command and say so in the task note.

- [ ] **Step 3: Commit**

```bash
git add shared/src/iosMain/kotlin/com/android/xrayfa/shared/ui/nav/FloatingNavChrome.ios.kt
git commit -m "feat: use system glass and blur for the iOS nav"
```

### Task 6: Device check against the spec

**Files:**
- Modify: none unless a check fails

**Interfaces:**
- Consumes: the chrome from Tasks 3–5

- [ ] **Step 1: Android**

Install a debug build. On a device, drag the slider between Config and Home. Confirm press scale, damping, chromatic aberration, and snap match the Backdrop catalog `LiquidBottomTabs` demo. Confirm light and dark both show page content through the pill, not the bottom fade. Confirm search still opens. Hide the backdrop (temporary `null` local) and confirm the solid `surfaceContainerHighest` fallback still switches tabs, then remove that temporary change.

- [ ] **Step 2: iOS**

On an iOS 26 simulator or device, confirm the pill, slider, and search circle use system liquid glass and still switch tabs. On an earlier simulator, confirm `UIBlurEffect` and the same switching.

- [ ] **Step 3: Commit only if Step 1 or 2 required a code fix**

Use a message that states the fix. Do not create an empty commit.
