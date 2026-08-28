# KMP 迁移交接文档 — Step 61 / 阶段 E.6m（2026-08-10）

本文档记录阶段 E 第 6m 项：**Settings 网络细节共享切片** + **iOS Config QR 扫码导入**。

**前置**：Step 60 / E.6l 已 commit（`356ae4b`）。

---

## 前置条件检查（E.6m 入口）

| 前置项 | 状态 |
|--------|------|
| E.6l 订阅管理 + Settings HWID 切片 | ✅ committed |
| `DefaultSettingsComponent` + `SettingsRepository` 网络字段 | ✅ |
| `ConfigLinkImporter.addLink` | ✅ E.6k |
| iOS `RootContent` Config Tab + `SharedConfigImportMenu` | ✅ E.6l |

**结论**：E.6m 前置已全部满足。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `shared/.../settings/SettingsValidators.kt` | KMP 校验（端口/IPv4/IPv6/SOCKS 凭据），对齐 Android `SettingsScreen` |
| `shared/.../settings/SharedSettingsEditDialog.kt` | 共享编辑对话框 |
| `shared/.../settings/SharedSettingsFieldRow.kt` | 共享字段行 |
| `shared/.../settings/SharedSettingsNetworkDetailsSection.kt` | HTTP/SOCKS 端口、SOCKS 凭据、DNS、IPv6 toggle |
| `shared/.../settings/SettingsUiLabels.kt` | 扩展网络/校验/QR 文案 |
| `shared/.../navigation/SettingsTabComponent.kt` | 新增 7 个网络 setter |
| `shared/.../navigation/DefaultSettingsComponent.kt` | 委托 `SettingsRepository` + VPN restart（对齐 ViewModel） |
| `shared/.../ui/settings/SharedSettingsGeneralSection.kt` | 嵌入 `SharedSettingsNetworkDetailsSection` |
| `androidApp/.../SettingsScreen.kt` | 移除重复网络行，改由共享切片负责 |
| `shared/.../navigation/ConfigTabComponent.kt` | 新增 `onImportFromLink` |
| `shared/.../navigation/DefaultConfigComponent.kt` | 委托 `ConfigLinkImporter.addLink` |
| `shared/.../ui/config/SharedConfigImportMenu.kt` | 可选「Scan QR code」菜单项 |
| `shared/.../ui/qr/SharedQrScannerScreen.kt` | expect/actual QR 全屏扫描 |
| `shared/.../ui/qr/SharedQrScannerScreen.ios.kt` | AVFoundation + UIKitView 扫码视图 |
| `shared/.../ui/qr/SharedQrScannerScreen.android.kt` | stub（Android 仍走 Navigation3） |
| `shared/.../platform/qr/IosQrCameraPermission.kt` | Swift 权限桥接回调 |
| `shared/.../ui/RootContent.kt` | Config Tab QR 扫描流程 |
| `iosApp/iosApp/ContentView.swift` | 注册相机权限桥接 |
| `iosApp/iosApp/Info.plist` | `NSCameraUsageDescription` |
| `shared/build.gradle.kts` | iOS 链接 `AVFoundation` |

### 未改动（留后续）

- Android `ConfigScreen` 嵌入 `SharedConfigImportMenu` 订阅/QR 入口（仍走 Navigation3 + ViewModel）
- Config Shared Element 动画恢复
- iOS 订阅 QR **分享**（Android ZXing Bitmap；iOS 可后续用 Clipboard 导出 URL）
- iOS QR 相册导入 / 闪光灯（Android `ScanQRScreen` 已有）
- Geo/路由/Apps 等平台依赖 Settings 项

---

## 设计决策与原因

1. **网络切片放入 `SharedSettingsGeneralSection`** — 与 LAN toggle 同组；Android `additionalNetworkContent` 保留 Geo/路由等仅 Android 项，避免重复分组。
2. **校验器放 commonMain** — 用 `SettingsUiLabels` 承载 format 模板（`%1$d` / `%2$s`），KMP 用 `replace` 而非 `String.format`（Native 不可用）。
3. **VPN restart 策略** — 端口/DNS/SOCKS 凭据变更调用 `vpnController.restartIfNeeded()`；`ipV6Enable` 不 restart（对齐 `SettingsViewmodel`）。
4. **iOS QR 权限走 Swift 桥接** — Kotlin/Native 对 `AVCaptureDevice.authorizationStatusForMediaType` 绑定不可用；`IosQrCameraPermission` + `ContentView.onAppear` 注册 handler，扫码视图仍用 Kotlin AVFoundation interop。
5. **Android QR 不迁移到 shared** — Strangler Fig：Android 继续 Navigation3 `ScanQR`；iOS 共享壳用 `SharedQrScannerScreen`。

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
| E.6m | Settings 网络切片 + iOS Config QR | ✅（本步，待 commit） |
| E.6n | Edit 节点 / Logcat / Apps / 路由 Settings 共享或 iOS parity | ⬜ 下一步建议 |
| E.5f（可选） | measureOutboundDelay 共享 | ⬜ |

---

## E.6n 建议范围

1. **共享 Edit 节点 BottomSheet** — 对齐 Android `EditScreen` / 长按编辑
2. **iOS Settings About 切片** — 版本/HWID/Core 版本（Android-only 信息可 platform actual）
3. **Android ConfigScreen 渐进嵌入 `SharedConfigImportMenu`**（可选）
4. **iOS 订阅 QR 分享** — URL 复制到剪贴板
5. **iOS CI** — `:shared:compileKotlinIosSimulatorArm64` workflow

---

## 待确认事项（请用户确认）

1. **iOS QR 范围**：当前仅相机实时扫描；是否需要本步一并做相册选图解码 / 闪光灯（与 Android 完全 parity）？
2. **SOCKS 用户名/密码在 iOS 展示**：已纳入共享切片（影响生成的 Xray 配置）；iOS 无 LAN 代理场景是否仍需展示？
3. **本步 commit 后**：是否继续 E.6n，或优先 iOS 真机 VPN + QR 手动验证？

---

## 手动验证清单

### iOS 共享壳
- [ ] Settings → 修改 SOCKS/HTTP 端口、DNS、IPv6 → 重连 VPN 后配置生效
- [ ] Config Tab → Add → Scan QR code → 授权相机 → 扫描节点链接 → 列表出现新节点
- [ ] 拒绝相机权限 → 显示 permission 提示文案

### Android（回归）
- [ ] SettingsScreen：端口/DNS/IPv6/SOCKS 凭据行为与改前一致（含校验错误文案）
- [ ] SettingsScreen：Geo/路由/Apps/Logcat 无回归
- [ ] ConfigScreen QR（Navigation3）仍正常

---

## Commit 建议

```
feat(kmp): add shared settings network slice and iOS config QR scan (E.6m)
```

---

## 子仓库备忘

| 仓库 | 事项 | 动作 |
|------|------|------|
| `AndroidLibXrayLite` | 无改动 | 无需 issue/PR |

---
