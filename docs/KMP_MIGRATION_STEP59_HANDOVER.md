# KMP 迁移交接文档 — Step 59 / 阶段 E.6k（2026-08-07）

本文档记录阶段 E 第 6k 项：共享 `DefaultSettingsComponent` + Settings Tab 首屏切片；iOS Config Tab 剪贴板导入（`ConfigLinkImporter` + `ClipboardReader`）。

**前置**：Step 58 / E.6j 已 commit（`b183498`）。

---

## 前置条件检查（E.6k 入口）

| 前置项 | 状态 |
|--------|------|
| E.6j Config Tab 共享节点列表 | ✅ committed |
| iOS Koin SettingsRepository / NodeRepository / ParserFactory | ✅ E.6c |
| Android SettingsScreen 通用/网络设置逻辑 | ✅ 本步迁移源（首片） |

**结论**：E.6k 前置已全部满足。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `shared/.../platform/ClipboardReader.kt` | 跨平台剪贴板接口 |
| `shared/.../platform/AndroidClipboardReader.kt` | Android 实现 |
| `shared/.../platform/IosClipboardReader.kt` | iOS UIPasteboard 实现 |
| `shared/.../config/ConfigLinkImporter.kt` | 剪贴板/链接 → preParse → addNode（对齐 ViewModel） |
| `shared/.../di/SharedServicesDiModule.kt` | Koin 注册 ConfigLinkImporter |
| `shared/.../navigation/SettingsTabComponent.kt` | SettingsComponent 接口 |
| `shared/.../navigation/DefaultSettingsComponent.kt` | 主题/开机/隐藏/ LAN 代理 toggles |
| `shared/.../navigation/SettingsComponentFactory.kt` | Koin 工厂 |
| `shared/.../ui/settings/*` | SharedSettingsGroup/Switch/Select + GeneralSection |
| `shared/.../ui/config/SharedConfigImportMenu.kt` | iOS Config TopBar Add 菜单 |
| `shared/.../navigation/ConfigTabComponent.kt` | 新增 `onImportFromClipboard()` |
| `shared/.../navigation/DefaultConfigComponent.kt` | 委托 ConfigLinkImporter |
| `shared/.../navigation/RootComponent.kt` | Settings child → SettingsComponent |
| `shared/.../navigation/DefaultRootComponent.kt` | 创建 DefaultSettingsComponent |
| `shared/.../ui/RootContent.kt` | Settings Tab 真实 UI；Config 导入入口 |
| `androidApp/.../RememberAndroidConfigComponent.kt` | 注入 ConfigLinkImporter |
| `androidApp/.../di/KoinModules.kt` | 加载 sharedServicesDiModule |
| `shared/.../di/AndroidSharedDiModule.kt` | ClipboardReader (Android) |
| `shared/.../di/IosPlatformDiModule.kt` | ClipboardReader (iOS) |
| `shared/.../IosKoinInit.kt` | 加载 sharedServicesDiModule |
| `shared/build.gradle.kts` | androidMain 添加 koin-android |

### 未改动

- Android `SettingsScreen` 仍完整保留（Geo 下载、路由、Apps、Logcat 等）
- iOS Config QR / 订阅管理
- Android Config Add SplitButton / QR / 搜索（仍走 ViewModel）
- Config Shared Element 动画
- 子仓库 `AndroidLibXrayLite`

---

## 设计决策与原因

1. **`ConfigLinkImporter` 提取 ViewModel `addLink` 逻辑** — 使用 `ParserFactory` + `NodeRepository` + `ConfigFilterIds.SUB_MANUAL`，iOS/Android 共用，避免在 Component 内重复解析代码。
2. **`ClipboardReader` 平台 DI** — Android 用 `ClipboardManager` + `androidContext()`；iOS 用 `UIPasteboard.generalPasteboard`，符合 Strangler Fig 增量策略。
3. **Settings 首片迁移** — 仅 General（主题/开机/隐藏）+ Network（LAN SOCKS/HTTP）两节；复杂 Android-only 功能（ActivityResult、Geo 下载、通知权限）留 androidApp。
4. **`DefaultSettingsComponent` 直接订阅 `SettingsRepository.settingsFlow`** — 与 ViewModel 数据源一致，LAN 代理变更触发 `vpnController.restartIfNeeded()`。
5. **iOS Config 导入 UX** — TopBar `SharedConfigImportMenu` + 空状态按钮均调用 `onImportFromClipboard()`；QR/订阅留 E.6l。

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
| E.6k | Settings 首片 + Config 剪贴板导入 | ✅（本步，待 commit） |
| E.6l | Config QR/订阅 iOS + Settings 剩余切片 | ⬜ 下一步 |
| E.5f（可选） | measureOutboundDelay 共享 | ⬜ |

---

## E.6l 建议范围

1. **iOS Config 订阅管理** — 复用 `KmpSubscriptionRepository` + shared UI
2. **Settings 网络/DNS/路由切片** — 逐步从 SettingsScreen 提取到 SharedSettingsSection
3. **Android SettingsScreen 嵌入 SharedSettingsGeneralSection**（可选，减少重复）
4. **Config Shared Element 恢复**（可选）

---

## 手动验证清单

### iOS 共享壳
- [ ] Settings Tab：主题切换写入 DataStore
- [ ] Settings Tab：LAN SOCKS/HTTP toggle，连接 VPN 后 restart 行为
- [ ] Config Tab：复制 vless/vmess 链接 → Add → Import from clipboard → 节点出现
- [ ] Config 空状态「Create configuration」触发剪贴板导入

### Android（回归）
- [ ] ConfigScreen 列表/搜索/Add/QR 仍正常
- [ ] SettingsScreen 完整功能无回归

---

## Commit 建议

```
feat(kmp): add shared Settings component and Config clipboard import (E.6k)
```
