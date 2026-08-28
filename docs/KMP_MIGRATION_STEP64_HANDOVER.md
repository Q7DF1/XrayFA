# KMP 迁移交接文档 — Step 64 / 阶段 E.6p（2026-08-10）

本文档记录阶段 E 第 6p 项：**Android Config 菜单统一** + **iOS Settings 平台项占位**。

**前置**：Step 63 / E.6o 已 commit（`8f55303`）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `shared/.../config/SharedConfigImportMenu.kt` | 新增 `additionalMenuItems` 插槽（Android 专属项） |
| `shared/.../settings/SharedSettingsPlatformSection.kt` | Apps / Logcat / 路由 Settings 平台切片 |
| `shared/.../settings/SettingsUiLabels.kt` | Platform 文案 |
| `shared/.../ui/RootContent.kt` | iOS Settings 展示平台项（禁用，Android only 提示） |
| `androidApp/.../ConfigScreen.kt` | 用 `SharedConfigImportMenu` 替代 SplitButton 导入菜单；Create 改为独立 Edit 按钮 |

### 未改动

- Android Settings 仍用 Navigation3 + Shared Element（Apps/Logcat/Route 保留在 `additionalGeneralContent`）
- iOS 平台项暂不可点击（待 NE/系统 API 接入）
- 完整 EditScreen 共享

---

## 设计决策

1. **`SharedConfigImportMenu.additionalMenuItems`** — 共享导入三件套（剪贴板/QR/订阅），Android 通过 lambda 追加 locate/delete all/bug report，避免重复菜单项。
2. **Android Config 导入改走 `ConfigComponent`** — 剪贴板/QR 使用 `onImportFromClipboard` / `onImportFromLink`，与 iOS 共享路径一致。
3. **iOS Platform 切片只读占位** — 无 handler 时显示 "Available on Android only"，让用户知晓功能存在但未移植。

---

## 验证状态

```bash
./gradlew :androidApp:assembleDebug
./gradlew :shared:compileKotlinIosSimulatorArm64
# 待本地验证
```

---

## 阶段 E 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.6p | Config 菜单统一 + Settings 平台占位 | ✅（本步，**待你确认后 commit**） |
| E.6q | Android Settings 平台切片统一 / iOS Logcat 桥接 | ⬜ 下一步 |

---

## 手动验证清单

### iOS
- [ ] Settings → Platform 分组显示 Apps/Logcat/Route，灰色不可点

### Android
- [ ] ConfigScreen → + 菜单：剪贴板/QR/订阅/locate/delete all/bug report 行为与改前一致
- [ ] ConfigScreen → Edit 按钮仍导航到创建配置页
- [ ] Settings Apps/Logcat/Route 导航与 Shared Element 无回归

---

## Commit 建议（确认后执行）

```
feat(kmp): unify config import menu and add iOS settings platform placeholders (E.6p)
```

---
