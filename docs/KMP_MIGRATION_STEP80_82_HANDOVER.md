# KMP 迁移交接文档 — Step 80–82 / R-1 底栏、日志、分应用收敛（2026-08-21）

对照期中评审 **R-1**：Android 已有屏幕改为共享 UI 薄封装。本会话一次做完 80–82，i18n（83）和 `AGENT.md`（84）留给后续。

**前置**：Step 79 已 commit（`e5c6333`）。

---

## 改动概要

| 步骤 | Android | Shared | Android 仍保留 |
|------|---------|--------|----------------|
| 80 | 删除 `XrayBottomNav.kt` / `XrayModernFloatingNav` | `XrayFloatingNav` 泛化为 `FloatingNavItem`，补选中标签 `AnimatedVisibility` | Navigation3 + Pager 切页逻辑 |
| 81 | `LogcatScreen` 薄封装 | `SharedAppLogScreen(lines, recording, …)`；iOS 走 `SharedInProcessAppLogScreen` | `XrayViewmodel` logcat 录制 / 导出 / `LogcatActionButton` |
| 82 | `AppsScreen` 薄封装 | `SharedAppsPickerScreen`：搜索、勾选、清空、权限槽 | `QUERY_ALL_PACKAGES` 权限页、应用图标 `Painter`、`AppsViewmodel` |

### 行为差异（有意）

- 底栏：Android 原先用 `LocalConfiguration` 算 pill 宽，共享改用 `BoxWithConstraints`（CMP 可移植）。
- 日志：共享 UI 接 Android 录制控件；iOS 仍是进程内 `AppLogStore`（清/复制），不是系统 logcat。
- 分应用：Android 全屏 SearchBar 改为共享 `OutlinedTextField`；图标通过 `leadingContent` 注入。iOS 仍是只读允许列表。

### 自检

```
rg -l 'com.android.xrayfa.shared.ui' androidApp/src/main/java
```

应包含 `XrayFAContainer`、`LogcatScreen`、`AppsScreen`（以及既有 Settings / Subscription / Route / Config）。

编译：`:androidApp:compileDebugKotlin` + `:shared:compileDebugKotlin` BUILD SUCCESSFUL。

---

## 下一步（83–84，本会话不做）

| 步骤 | 内容 |
|------|------|
| 83 | `strings.xml` ×4 → `commonMain/composeResources` |
| 84 | `AGENT.md` 全量重写 |

P2/P3（build-logic、Room index、iOS App Group、version catalog、ProGuard、xcodebuild）仍不在本轮。
