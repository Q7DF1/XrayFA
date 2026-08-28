# KMP 迁移交接文档 — Step 65 / 阶段 E.6q（2026-08-10）

本文档记录阶段 E 第 6q 项：**Android Settings 平台切片统一** + **iOS 应用日志查看器**。

**前置**：Step 64 / E.6p 已 commit（`4ecd5b6`）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `common/.../AppLogStore.kt` | 共享内存日志环形缓冲（最多 1000 行） |
| `shared/.../SharedAppLogScreen.kt` | 共享日志查看 UI（Clear / Copy all） |
| `shared/.../SharedSettingsPlatformSection.kt` | 新增 `appsModifier` / `logcatModifier` / `routeModifier` 插槽 |
| `shared/.../SettingsUiLabels.kt` | 日志屏文案 |
| `shared/.../IosLogger.kt` | 写入 `AppLogStore` |
| `shared/.../RootContent.kt` | iOS Settings → Logcat 打开 `SharedAppLogScreen` |
| `androidApp/.../SettingsScreen.kt` | 移除 General/Network 内重复平台行，改用 `SharedSettingsPlatformSection` + Shared Element |

### 未改动

- Android 仍导航至完整 `LogcatScreen`（系统 logcat 录制），非 `SharedAppLogScreen`
- iOS Apps / Route 仍为禁用占位
- 完整 EditScreen 共享

---

## 设计决策

1. **Android 平台行迁出 General/Network** — Apps/Logcat/Route 归入独立 Platform 分组，与 iOS 结构一致；Shared Element modifier 通过插槽传入，保留过渡动画。
2. **iOS Logcat = 应用内日志** — 非系统 logcat；`IosLogger` 写入 `AppLogStore`，Settings 点击 Logcat 进入只读查看器。后续可接 os_log / NE 扩展。
3. **Android Logcat 不变** — 仍用 `XrayViewmodel.startLogcatRecording()` 的完整屏；共享 `SharedAppLogScreen` 仅供 iOS（及未来可选 Android 轻量入口）。

---

## 验证状态

```bash
./gradlew :androidApp:assembleDebug
./gradlew :shared:compileKotlinIosSimulatorArm64
# BUILD SUCCESSFUL
```

---

## 阶段 E 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.6q | Android Settings 平台切片统一 + iOS 日志查看 | ✅（本步，**待你确认后 commit**） |
| E.6r | iOS Apps/Route 桥接 或 EditScreen 渐进共享 | ⬜ 下一步 |

---

## 手动验证清单

### iOS
- [ ] Settings → Platform → Logcat 可进入日志屏
- [ ] 触发若干 shared 层日志（如导入配置）后列表有内容
- [ ] Clear / Copy all 可用
- [ ] Apps / Route 仍为灰色不可点

### Android
- [ ] Settings → Platform 分组显示 Apps / Logcat / Route
- [ ] 三项导航与 Shared Element 动画无回归
- [ ] Live update notification 仍在 General 分组

---

## Commit 建议（确认后执行）

```
feat(kmp): unify Android settings platform section and add iOS app log viewer (E.6q)
```

---
