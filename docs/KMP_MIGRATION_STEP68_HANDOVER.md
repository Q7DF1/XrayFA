# KMP 迁移交接文档 — Step 68 / 阶段 E.6t（2026-08-10）

本文档记录阶段 E 第 6t 项：**iOS Apps 信息桥接屏** + **Settings 暴露 allow list**。

**前置**：Step 67 / E.6s 已 commit（`7627503`）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `core/datastore/.../SettingsRepository.kt` | `SettingsState.allowedPackages` |
| `shared/.../DefaultSettingsComponent.kt` | `combine(settingsFlow, packagesFlow)` |
| `shared/.../SharedAppsInfoScreen.kt` | iOS Apps 只读信息屏（说明 + 包名列表） |
| `shared/.../SettingsUiLabels.kt` | Apps 信息文案 |
| `shared/.../RootContent.kt` | iOS Settings → Apps 打开信息屏 |

### 未改动

- Android `AppsScreen` 完整 per-app 管理
- 完整 `EditScreen` 共享
- iOS 尚不能编辑 allow list

---

## 设计决策

1. **桥接 ≠ 完整移植** — iOS 无 PackageManager/NE per-app API；先提供说明屏 + 读取 DataStore 中已有 allow list（跨设备同步 settings 时可查看）。
2. **`allowedPackages` 并入 SettingsState** — `DefaultSettingsComponent` 统一暴露，供未来共享 Apps UI 复用。
3. **Platform 三项均可点击** — iOS Settings Platform 分组 Apps/Logcat/Route 全部可用。

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
| E.6t | iOS Apps 信息桥接 + allow list 暴露 | ✅（本步，**待你确认后 commit**） |
| E.6u | ModalBottomSheet 崩溃修复；Android Edit 保持全屏 | 见 Step 69 |
| E.6v | 共享完整 EditScreen，iOS 对齐 Android | ⬜ 下一步 |

---

## 手动验证清单

### iOS
- [ ] Settings → Platform → Per-app proxy 可进入
- [ ] 显示 iOS 说明文案
- [ ] 若 DataStore 有 allow list，只读展示包名
- [ ] Logcat / Route 无回归

### Android
- [ ] Apps 屏与管理行为无回归
- [ ] Settings 共享切片无回归

---

## Commit 建议（确认后执行）

```
feat(kmp): add iOS apps info bridge and expose allowed packages in settings (E.6t)
```

---
