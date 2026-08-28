# KMP 迁移交接文档 — Step 67 / 阶段 E.6s（2026-08-10）

本文档记录阶段 E 第 6s 项：**共享 Route Settings 屏** + **iOS 路由设置接入**。

**前置**：Step 66 / E.6r 已 commit（`fec8dad`）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `shared/.../SettingsTabComponent.kt` | 路由 API：`onSetRoutingMode` / `onSetDomainStrategy` / `onSetRoutingRules` |
| `shared/.../DefaultSettingsComponent.kt` | 实现路由写入 + `vpnController.restartIfNeeded()` |
| `shared/.../RouteSettingsUiLabels.kt` | 路由屏文案 |
| `shared/.../SharedRouteSettingsScreen.kt` | 共享路由设置 UI（模式/策略/预设/自定义规则） |
| `shared/.../RootContent.kt` | iOS Settings → Route 打开共享路由屏 |
| `androidApp/.../RouteSettingsScreen.kt` | 瘦包装：嵌入 `SharedRouteSettingsScreen` + Shared Element |
| `androidApp/.../XrayFAContainer.kt` | 移除 `viewmodel` 参数 |

### 未改动

- iOS Apps 仍为禁用占位
- Android 完整 `EditScreen`
- Android Apps 代理选择

---

## 设计决策

1. **整屏迁入 shared** — 原 `RouteSettingsScreen.kt` ~526 行 UI 逻辑迁至 `SharedRouteSettingsScreen`，Android 仅保留 Shared Element 包装。
2. **SettingsComponent 承载路由** — 与 General/Network 切片一致，通过 `DefaultSettingsComponent` + `SettingsRepository` 写 settings，变更后重启 VPN。
3. **iOS 子屏导航** — Settings 内 `showRouteSettings` 局部状态（同 Logcat 模式），带返回按钮。

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
| E.6s | 共享 Route Settings + iOS 接入 | ✅（本步，**待你确认后 commit**） |
| E.6t | iOS Apps 桥接 或 EditScreen 渐进共享 | ⬜ 下一步 |

---

## 手动验证清单

### iOS
- [ ] Settings → Platform → Route settings 可进入
- [ ] 切换 Global/Route 模式、Domain Strategy 生效
- [ ] Quick Config 预设开关可切换
- [ ] 添加/删除自定义规则

### Android
- [ ] Settings → Route 导航与 Shared Element 无回归
- [ ] 路由设置行为与改前一致

---

## Commit 建议（确认后执行）

```
feat(kmp): share route settings screen and wire iOS settings route entry (E.6s)
```

---
