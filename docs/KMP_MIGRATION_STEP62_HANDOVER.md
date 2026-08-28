# KMP 迁移交接文档 — Step 62 / 阶段 E.6n（2026-08-10）

本文档记录阶段 E 第 6n 项（首批）：**iOS/Android Settings About 共享切片**、**订阅 URL 复制分享**、**iOS shared 编译 CI**。

**前置**：Step 61 / E.6m 已 commit（`1e4661b`）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `shared/.../platform/ClipboardWriter.kt` | 剪贴板写入接口 |
| `shared/.../platform/AndroidClipboardWriter.kt` / `IosClipboardWriter.kt` | 平台 actual |
| `shared/.../platform/AppMetadataProvider.kt` | 应用版本 + 打开外链 |
| `shared/.../platform/AndroidAppMetadataProvider.kt` / `IosAppMetadataProvider.kt` | 平台 actual |
| `shared/.../ui/settings/SharedSettingsAboutSection.kt` | 版本/HWID/Core/Repo |
| `shared/.../ui/settings/SettingsUiLabels.kt` | About 文案 |
| `shared/.../ui/subscription/SharedSubscriptionScreen.kt` | 订阅卡片「复制 URL」+ 提示 |
| `shared/.../ui/subscription/SubscriptionUiLabels.kt` | 分享文案 |
| `shared/.../ui/RootContent.kt` | iOS Settings 增加 About 切片 |
| `androidApp/.../SettingsScreen.kt` | About 改由共享切片负责 |
| `shared/.../di/AndroidSharedDiModule.kt` / `IosPlatformDiModule.kt` | 注册新 platform 服务 |
| `.github/workflows/ios-shared.yml` | iOS shared KMP 编译 CI |

### 未改动

- 共享 Edit 节点 BottomSheet（留 E.6o）
- Android ConfigScreen 嵌入 `SharedConfigImportMenu`
- iOS QR 相册/闪光灯

---

## 设计决策

1. **About 用 `AppMetadataProvider` 而非 expect/actual 函数** — Android 需 Context 读版本/启动浏览器，与现有 Koin DI 一致。
2. **订阅分享用复制 URL 替代 QR Bitmap** — iOS 无 ZXing；复制 URL 满足跨设备分享，Android 仍保留 Navigation3 QR 生成。
3. **iOS CI 仅 compile** — 不构建 xcframework / Xcode，降低 CI 复杂度。

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
| E.6n | About + 订阅 URL 复制 + iOS CI | ✅（本步，待 commit） |
| E.6o | 共享 Edit 节点 / Logcat / Apps / 路由 | ⬜ 下一步 |

---

## Commit 建议

```
feat(kmp): add shared settings about section and subscription URL copy (E.6n)
```

---
