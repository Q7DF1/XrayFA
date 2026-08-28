# KMP 迁移交接文档 — Step 42 / 阶段 E.3b（2026-08-07）

本文档记录阶段 E 第 3 项（第二批）：ViewModel / Tile / BootReceiver 迁移至 `VpnController`；
移除 UI 对 `XrayBaseService.statusFlow` 的直接依赖。

**前置**：Step 41 / E.3 已 commit（`8544cea`）。

---

## 前置条件检查（E.3b 入口）

| 前置项 | 状态 |
|--------|------|
| E.3 `AppVpnController` Koin 注册 | ✅ committed |
| `VpnController` 委托 `XrayBaseServiceManager` | ✅ |

---

## 改动概要

**目标**：Android 业务层统一经 `VpnController` 控制 VPN；行为与迁移前一致（仍委托同一 Manager/Service）。

### 变更 — `:platform:vpn`

| 文件 | 变更 |
|------|------|
| `VpnState.kt` | 新增 `VpnState.isConnected` 扩展 |

### 变更 — `:app`

| 文件 | 变更 |
|------|------|
| `XrayViewmodel` | `VpnController` 替代 `XrayBaseServiceManager`；collect `vpnController.state` |
| `SettingsViewmodel` | 同上 + 暴露 `isVpnConnected: StateFlow<Boolean>` |
| `SettingsScreen` | Geo 下载启用条件改 `viewmodel.isVpnConnected` |
| `QuickStartTileService` | collect `vpnController.state` 更新 Tile；connect/disconnect |
| `BootBroadcastReceiver` | boot/shutdown 走 `vpnController` |
| `AppViewModelDiModule` / `AppComponentDiModule` | Factory 注入 `VpnController` |

### 保留

- `XrayBaseServiceManager` —— 仍由 `AppVpnController` 内部使用
- `XrayBaseService.statusFlow` —— 仍由 Service 更新；`AppVpnController` 映射为 `VpnState`
- `qsStateCallBack` on Manager —— 暂未删除（Quick Settings 改 collect state）

---

## 设计决策与原因

1. **ViewModel 不再直接依赖 Manager** —— 跨平台入口统一为 `VpnController`；iOS 未来替换 `IosVpnController` 即可。
2. **`isVpnConnected` 而非 UI 读 statusFlow** —— Settings 页与 commonMain 契约对齐。
3. **Quick Settings 用 state collect** —— 替代 `qsStateCallBack`，与 ViewModel 状态源一致。
4. **Manager 暂不删除** —— `AppVpnController` 薄包装，降低 E.3b 风险；清理留后续步。

---

## 验证状态

```bash
./gradlew :platform:vpn:compileKotlinIosSimulatorArm64   # BUILD SUCCESSFUL
./gradlew :app:assembleDebug                            # BUILD SUCCESSFUL
./gradlew :domain:testDebugUnitTest                      # BUILD SUCCESSFUL
```

**待用户确认后再 commit。**

**建议手动回归（本步有调用路径变更）**：
- 首页连接 / 断开
- 设置页修改路由/DNS 后 VPN 自动重启
- Geo 文件下载按钮（需 VPN 连接时可用）
- Quick Settings Tile 开关
- 开机自启 / 关机停止（如可测）

---

## 阶段 E 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.1–E.2 | native-bridge + app 接线 | ✅ committed |
| E.3 | platform:vpn + AppVpnController | ✅ committed |
| E.3b | ViewModel 迁移 VpnController | ✅（本步，待 commit） |
| E.4 | iOS xcframework 构建验证 | ⬜ 下一步 |
| E.5 | Compose Multiplatform / iOS 壳 | ⬜ |

---

## 下一步（E.4）待办清单

1. **验证 gomobile iOS xcframework**
   ```bash
   cd AndroidLibXrayLite
   gomobile bind -target ios,iossimulator -o LibXrayLite.xcframework ./
   ```
2. **评估 iOS Network Extension 内存** —— NE 15MB 限制
3. **（可选）清理 dead code** —— `qsStateCallBack`、`TProxyService` 若确认无引用
4. **（可选）扩展 `VpnState`** —— Connecting / Error

**验证命令**：
```bash
./gradlew :core:native-bridge:compileKotlinIosSimulatorArm64
./gradlew :platform:vpn:compileKotlinIosSimulatorArm64
```

---

## Commit 建议

```
feat(kmp): migrate ViewModels and components to VpnController (E.3b)
```
