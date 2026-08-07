# KMP 迁移交接文档 — Step 60 / 阶段 E.6l（2026-08-07）

本文档记录阶段 E 第 6l 项：iOS Config 订阅管理（`DefaultSubscriptionComponent` + `SharedSubscriptionScreen`）；Settings 订阅切片（`send_hwid`）；Android SettingsScreen 嵌入共享 General/Network/Subscription 切片。

**前置**：Step 59 / E.6k 已 commit（`d718578`）。

---

## 前置条件检查（E.6l 入口）

| 前置项 | 状态 |
|--------|------|
| E.6k Settings 首片 + Config 剪贴板导入 | ✅ committed |
| E.6j Config Tab 共享节点列表 | ✅ committed |
| iOS `KmpSubscriptionRepository` + Room | ✅ E.6c |
| `DefaultSettingsComponent` + `SharedSettingsGeneralSection` | ✅ E.6k |
| `SharedConfigImportMenu` 剪贴板入口 | ✅ E.6k |

**结论**：E.6l 前置已全部满足。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `shared/.../subscription/SubscriptionUrlValidator.kt` | KMP 订阅 URL 校验（对齐 Android `validateUrl`） |
| `shared/.../navigation/SubscriptionState.kt` | 订阅 Tab 展示状态 |
| `shared/.../navigation/SubscriptionTabComponent.kt` | `SubscriptionComponent` 接口 |
| `shared/.../navigation/DefaultSubscriptionComponent.kt` | 增删改/刷新逻辑（对齐 `SubscriptionViewmodel`） |
| `shared/.../navigation/SubscriptionComponentFactory.kt` | Koin 工厂 |
| `shared/.../navigation/RememberSubscriptionComponent.kt` | Compose 生命周期内创建 Component |
| `shared/.../ui/subscription/*` | 共享订阅列表 + 编辑 BottomSheet + 删除对话框 |
| `shared/.../ui/config/SharedConfigImportMenu.kt` | 新增「Manage subscriptions」菜单项 |
| `shared/.../ui/RootContent.kt` | Config Tab → 订阅全屏；Settings Tab 增加订阅切片 |
| `shared/.../navigation/SettingsTabComponent.kt` | 新增 `onSetSendHwid` |
| `shared/.../navigation/DefaultSettingsComponent.kt` | 委托 `SettingsRepository.setSendHwid` |
| `shared/.../ui/settings/SharedSettingsSubscriptionSection.kt` | send_hwid toggle |
| `shared/.../ui/settings/SharedSettingsGeneralSection.kt` | `scrollEnabled` + Android 平台项插槽 |
| `androidApp/.../settings/RememberAndroidSettingsComponent.kt` | Android Settings Component 工厂 |
| `androidApp/.../component/SettingsScreen.kt` | 嵌入共享切片，去除重复 toggle |

### 未改动

- Android `SubscriptionScreen` 完整保留（QR 扫描/分享 QR/Navigation3）
- Android `ConfigScreen` Add SplitButton / QR 仍走 ViewModel + Navigation3
- Config Shared Element 动画
- iOS Config QR 扫码（留 E.6m）
- 子仓库 `AndroidLibXrayLite`

---

## 设计决策与原因

1. **`DefaultSubscriptionComponent` 复制 ViewModel 核心路径** — `addOrUpdateSubscription` / `refreshSubscription` / 重复 mark 校验 / 失败 rollback，与 Android 行为一致；QR 生成/ZXing 留 androidApp（平台 API）。
2. **`validateSubscriptionUrl` 放 commonMain** — 避免 `java.net.URI` 在 Native 不可用，用 regex 对齐 http/https + host 校验。
3. **iOS 订阅导航用 Compose 局部 state** — `RootContent` 内 `showSubscriptions` 切换全屏，成功后 `onSelectFilter(subscriptionId)` 回到 Config；符合 Strangler Fig，不强行改 Decompose 根栈。
4. **Settings 订阅切片仅 `send_hwid`** — 网络端口/DNS/Geo/路由仍留 androidApp（ActivityResult、Geo 下载等平台依赖）；iOS Settings 现含 General + Network(LAN) + Subscription。
5. **Android Settings 嵌入共享切片** — `DefaultSettingsComponent` 与 `SettingsViewmodel` 同源 `SettingsRepository.settingsFlow`，逻辑不变；`additionalGeneralContent` / `additionalNetworkContent` 保留 Apps/Logcat/Geo 等 Android-only UI，避免重复分组标题。

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
| E.6l | iOS 订阅管理 + Settings 订阅切片 + Android Settings 嵌入 | ✅（本步，待 commit） |
| E.6m | iOS Config QR 扫码 + Settings 网络端口/DNS 切片 | ⬜ 下一步 |
| E.5f（可选） | measureOutboundDelay 共享 | ⬜ |

---

## E.6m 建议范围

1. **iOS Config QR 导入** — `expect/actual QrScanner` 或 AVFoundation UIKitView 包装
2. **Settings 网络细节切片** — SOCKS/HTTP 端口、DNS IPv4/IPv6、IPv6 toggle → 扩展 `DefaultSettingsComponent` + 共享 Edit 对话框
3. **Android ConfigScreen 嵌入 `SharedConfigImportMenu` 订阅入口**（可选，与 Navigation3 Subscription 并存过渡）
4. **Config Shared Element 恢复**（可选）
5. **订阅 QR 分享** — iOS 用 `ClipboardReader` 导出 URL（Android 仍用 ZXing Bitmap）

---

## 子仓库备忘

| 仓库 | 事项 | 动作 |
|------|------|------|
| `AndroidLibXrayLite` | 无改动 | 无需 issue/PR |

---

## 手动验证清单

### iOS 共享壳
- [ ] Config Tab → Add → Manage subscriptions → 列表/添加/编辑/删除
- [ ] 添加订阅 URL → 刷新 → Config Filter 出现对应 chip → 节点列表有数据
- [ ] Settings Tab → send_hwid toggle 写入 DataStore

### Android（回归）
- [ ] SettingsScreen：主题/开机/LAN 代理/send_hwid 行为与改前一致
- [ ] SettingsScreen：Apps/Logcat/Geo/路由/About 无回归
- [ ] SubscriptionScreen（Navigation3）仍完整可用
- [ ] ConfigScreen 列表/搜索/QR 仍正常

---

## Commit 建议

```
feat(kmp): add shared subscription management and settings HWID slice (E.6l)
```

---
