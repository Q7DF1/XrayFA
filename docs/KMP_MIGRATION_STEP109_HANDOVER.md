# KMP 迁移交接文档 — Step 109 / 单壳补齐 Android 缺口（2026-08-27）

本文档记录：Android 主路径从 Step 108 的 `XrayFAContainer` **回到共享 `RootContent`**，把迁移前 Android 功能补进共享壳和 `PlatformRootHooks`。iOS 仍走同一套 `RootContent`；Android 专有能力在 iOS 上提示「开发中」。导航对齐原生 Android：底栏只有 Config | Home，Settings 等是 overlay。未做 Agent Phase C / C1。

**前置**：Step 108。用户明确不要两套壳。

---

## 为什么要改回来

Step 108 用两套壳补功能（Android Navigation3 / iOS Decompose）。产品要求 **一套 UI**：共享 `RootContent`。Android 只做薄包装 + hooks。

Step 92 的共享壳把 Settings 当成第三 Tab，和原生 `list_navigation = [Config, Home]` 不一致。Settings 应从 Home 齿轮推进 overlay，返回时回到底下的 Tab。

---

## 导航（对齐原生 Android）

| 原生 Navigation3 | 共享 `RootContent` |
|------------------|---------------------|
| 底栏 Config \| Home | `RootTab.Config` \| `RootTab.Home` |
| Settings 从 Home 齿轮 push | `RootOverlay.Settings` |
| Subscriptions / QR / Apps / Logcat / RouteSettings 非 Tab | 对应 `RootOverlay` |
| 嵌套页隐藏底栏 | `overlay != None` 或 Config 编辑时隐藏 |
| 选中节点 → Home | `selectTab(Home)` |
| 系统返回弹出嵌套 | overlay 栈 `navigateBack()`；Apps 从设置打开则回到设置 |

`ChildPages.onPageSelected` 走 `onPageSelected`（不清 overlay）。底栏点击走 `selectTab`（清空 overlay）。打开 overlay 时先入栈再切 Config，避免 pager 回调把 overlay 清掉。

Agent `openScreen` 走 `AgentScreen.toRootNavigation()`：Apps / RouteSettings / Subscriptions 落到真实 overlay，不再折叠成 Settings Tab。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `RootTab` / `RootOverlay` / `DefaultRootComponent` | 两栏 pager + overlay 栈 |
| `RootContent` | 底栏 Config\|Home；overlay 宿主；Config 搜索 / 定位 / 删全部 / 分享 / bug report |
| `PlatformRootHooks` | QR / Home / Share / BugReport / 系统返回 |
| `AndroidAppShell` + `MainActivity` | 再走 `RootContent` + `AndroidPlatformRootHooks` |
| `AndroidPlatformRootHooks` | VPN prepare、平板 Home、CameraX QR、geo 导入、分应用、logcat 录制、二维码分享、bug report |
| iOS 默认 hooks | 分应用 / geo 导入 / HexTun / 分享 / bug report → 「开发中」 |
| `AgentScreenRootTabTest` | 断言 `toRootNavigation()` 与两栏底栏 |

### 未改动

- Agent Phase C / C1
- iOS VPN / 相册扫码 / 闪光灯 / 分应用真实实现
- `XrayFAContainer` 仍在仓库，主路径不再使用

---

## 设计决策

1. **一套壳** — Android 不再走 Navigation3 主路径。
2. **Settings 不是 Tab** — 与 `NavigateDestination.list_navigation` 一致。
3. **overlay 栈而不是单值** — 订阅里扫码返回订阅；设置里打开分应用返回设置。Agent 从 Home 开 Apps 则栈上只有 Apps，返回 Home。
4. **iOS 不假装可用** — Android 专有项给「开发中」；iOS 相机扫码本身可用，仍走 `SharedQrScannerScreen`。

---

## 验证状态

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
./gradlew :androidApp:compileDebugKotlin :androidApp:testDebugUnitTest \
  --tests com.android.xrayfa.ui.AgentScreenRootTabTest
./gradlew :shared:compileKotlinIosX64
```

- [x] `:androidApp:compileDebugKotlin` + `AgentScreenRootTabTest` + `:shared:compileKotlinIosX64` 通过
- [ ] 真机：底栏只有 Config / Home；设置从齿轮进、返回仍在原 Tab
- [ ] Config 搜索 / 相册扫码 / 节点二维码 / bug report
- [ ] 快捷方式「扫码」打开相机并导入
- [ ] Agent `openScreen` Apps / RouteSettings 进入对应页

---

## 下一步

1. 可选：删除或冻结无主路径引用的 `XrayFAContainer` Navigation3 图
2. iOS 分应用 / geo 文件导入 / 节点分享仍是产品缺口，不要假装可用
3. **不要**做 Agent Phase C / C1，除非产品明确要求
