# KMP 迁移交接文档 — Step 79 / 期中 R-1 订阅页收敛（2026-08-21）

本文档记录期中评审 **R-1** 的第一刀：已存在的并行实现按顺序收敛。Android `SubscriptionScreen` 改为薄封装，注入 `stringResource` 标签，调用共享 `SharedSubscriptionScreen`。

**前置**：Step 78 已 commit（`b0c0670`）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `androidApp/.../SubscriptionScreen.kt` | **重写为薄封装**（~80 行）；删 650 行 Android 专用 UI |
| `androidApp/.../SubscriptionViewmodel.kt` | **删除** — 逻辑已在 `DefaultSubscriptionComponent` |
| `MainActivity` / `XrayFAContainer` / DI | 不再构造 / 注入 `SubscriptionViewmodel` |
| `SharedSubscriptionScreen` | 可选 `onScanQr`；Android 顶栏扫码，iOS 仍从 Config 扫 |
| `SubscriptionUiLabels.scanQr` | 新字段 |
| `docs/KMP_MIGRATION_PLAN.md` | Step 79 从表里拆出 |

### 未改动 / 已知差异

- 未切 Android 根到 `RootContent`（仍走 Navigation3 `XrayFAContainer`）
- **分享从「二维码 Dialog」改为复制 URL**（与 iOS 共享实现一致）
- 未迁 `XrayBottomNav` / `LogcatScreen` / `AppsScreen`

---

## 设计决策

1. **照 `RouteSettingsScreen` 样板** — 逻辑在 Decompose component，Android 只注入标签 + 平台扫码回调。
2. **扫码留在 Android 导航栈** — `ScanQR` 仍是 Navigation3 路由；共享层只收 `onScanQr`。
3. **删 ViewModel 而不是留空壳** — 否则 R-1 只是多了一层包装，债务还在。

---

## 验证状态

```bash
./gradlew :androidApp:compileDebugKotlin :shared:compileDebugKotlin
# BUILD SUCCESSFUL（exit 0）
```

- [x] 上述命令通过
- [x] `rg -l 'com.android.xrayfa.shared.ui' androidApp/src/main/java` 含 `SubscriptionScreen.kt`
- [ ] 真机：添加 / 编辑 / 删除 / 刷新订阅；扫码导入（人工）

---

## 期中后进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| 73–78 | P0/P1 测试、CI、去重、分层 | ✅ |
| 79 | Android 订阅页收敛共享 UI | ✅ 本步 |
| 80 | `XrayBottomNav` → 共享底栏 | ⬜ |
| 81 | `LogcatScreen` → 共享日志 | ⬜ |
| 82 | `AppsScreen` → 共享分应用代理 | ⬜ |
| 83+ | i18n / AGENT 重写 / 性能项 | ⬜ |

---

## 手动验证清单

### Android
- [ ] 订阅列表加载
- [ ] 手动添加 URL 后回到 Config 且过滤器选中该订阅
- [ ] 编辑 / 删除
- [ ] 顶栏扫码导入
- [ ] 分享按钮复制 URL（不再弹二维码图）

### iOS
- [ ] 行为不变（未改 RootContent 调用，仅多一个可选参数默认 null）

---

## Commit 建议（确认后执行）

```
refactor(kmp): reuse SharedSubscriptionScreen on Android
```
