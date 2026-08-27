# KMP 迁移交接文档 — Step 97 / Phase 7 A5 `adb cmd app_function` 手测（2026-08-26）

本文档记录 Phase 7 **A5**：在 **API 36** 真机上用系统 `cmd app_function` 验证 Phase A 只读函数的发现与执行，以及设置总开关门控。未改业务代码，未做 Phase B 写操作 / `setAppFunctionEnabled`。

**前置**：Step 96 / A4。交接：`docs/KMP_MIGRATION_STEP96_HANDOVER.md`。

---

## 环境

| 项 | 值 |
|----|----|
| 设备 | SM-S9110（`RFCX80298XK`） |
| `ro.build.version.sdk` | **36**（Android 16 REL） |
| 包名 | `com.android.xrayfa` |
| 安装 | `./gradlew :androidApp:installDebug` → `XrayFA-arm64-v8a-debug.apk` |
| 系统命令 | `adb shell cmd app_function help` 可用 |

Samsung/API 36 的子命令（**不是**计划草稿里的 `list` / `execute --id`）：

```
list-app-functions [--user <USER_ID>]
execute-app-function --package <PKG> --function <FUNCTION_ID> --parameters <JSON>
  [--timeout-duration <SECONDS>] [--brief-yaml]
set-enabled --package <PKG> --function <FUNCTION_ID> --state <enable|disable|default>
```

`list-app-functions` **没有** `--package`；从 JSON 里筛 `com.android.xrayfa`。

---

## 实测命令（可复现）

```bash
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"
PKG=com.android.xrayfa
FN_VPN='com.android.xrayfa.agent.appfunctions.XrayFAAppFunctions#getVpnStatus'

# 发现：应看到 8 个 functionId（另有 1 条 ComponentMetadata，不是函数）
adb shell cmd app_function list-app-functions --user 0

# 无参函数：--parameters 必须给 '{}'
adb shell "cmd app_function execute-app-function --package $PKG --function '$FN_VPN' --parameters '{}' --user 0 --brief-yaml"

# 有参：AppSearch 风格，标量包成单元素数组
adb shell "cmd app_function execute-app-function --package $PKG \
  --function 'com.android.xrayfa.agent.appfunctions.XrayFAAppFunctions#listNodes' \
  --parameters '{\"filterKind\":[\"All\"],\"subscriptionId\":[0]}' --user 0 --brief-yaml"
```

平铺 JSON `{"filterKind":"All","subscriptionId":0}` 会失败：`String cannot be converted to JSONObject`。

---

## 结果

| 检查 | 结果 |
|------|------|
| `list-app-functions` 含 8 个 Phase A ID | ✅ `getVpnStatus` / `getSelectedNode` / `listNodes` / `getNode` / `listSubscriptions` / `getSettingsSummary` / `getTrafficSpeeds` / `getAppInfo` |
| 开关 **开**：`getVpnStatus` | ✅ `connected: false`（当时未连 VPN） |
| 开关 **开**：`getAppInfo` | ✅ `versionName: 1.6.4` |
| 开关 **开**：`getSettingsSummary` | ✅ 含 `agentFunctionsEnabled: true`；无 SOCKS 密码字段 |
| 开关 **开**：`getSelectedNode` / `getNode` / `listNodes` | ✅ 摘要字段：id / remark / protocol / address / port / selected / favorite / subscriptionId / countryIso；**无** share-link / 节点 JSON / url |
| 开关 **开**：`listSubscriptions` | ✅ `androidAppfunctionsReturnValue: []`（该机无订阅） |
| 设置页关掉 **Agent 功能** 后再 execute | ✅ `AppFunctionException: AGENT_DISABLED (code 1000)`；`listNodes` **不**返回节点列表 |
| 再打开开关 | ✅ `getVpnStatus` 恢复成功 |
| `getTrafficSpeeds` | ⚠️ VPN 未启动 traffic 采样时 `execute-app-function --timeout-duration 5` → **Timed out**（见下） |
| API &lt; 36 启动不 crash | ⬜ 本步无 API 35 设备 |

`set-enabled` 未测（B2）。

---

## 发现（不阻塞 A5 关闭，留给修 Phase A 或 B1 前）

`DefaultXrayAgentFacade.getTrafficSpeeds()` 对 `trafficStatsSource.speedsKbps.first()` 挂起。`XrayCoreManager.trafficFlow` 是 `MutableSharedFlow(replay = 1)`，只在 `startTrafficDetection()`（VPN 跑起来）后才 emit。未连接时 replay 为空，`first()` 永不返回，系统侧 30s 默认超时。

建议：`withTimeoutOrNull` 后回退 `0.0 to 0.0`，或在 core 初始化时 emit 一次零值。A5 **没有**改代码。

---

## 未改动

- 无 Phase B `@AppFunction`（B1）
- 未调用 `AppFunctionManager.setAppFunctionEnabled` / `cmd app_function set-enabled`（B2）
- iOS 无 Agent 入口

---

## 期中后 / Phase 7 进度

Phase 7 必做 **7** 项。本步完成后还剩 **2** 个。

| 步骤 | 内容 | 状态 |
|------|------|------|
| 93 / A1 | domain 契约 + Catalog | ✅ |
| 94 / A2 | Android Facade + Koin | ✅ |
| 95 / A3 | DataStore 总开关 + 设置页 | ✅ |
| 96 / A4 | AppFunctions + Phase A 只读 | ✅ |
| **97 / A5** | `adb cmd app_function` 手测 | ✅ 本步 |
| B1 | connect / refresh + VPN prepare | ⬜ 下一步 |
| B2 | `setAppFunctionEnabled` 随设置 | ⬜ |

---

## 下一步（B1）待办清单

1. Facade：实现 `connectVpn` / `disconnectVpn` / `refreshSubscription` / `measureNodeDelay` / `openScreen`；`selectNode` / `setFavorite` 已有。
2. `connectVpn`：**必须** `VpnService.prepare`；未授权 → `NeedsUserConsent`，不要静默连。
3. `XrayFAAppFunctions` 增加对应 `@AppFunction`（仍走 `withAgentEnabled`）。
4. 顺手修 `getTrafficSpeeds` 超时（A5 发现）。
5. **不要**做 B2 `setAppFunctionEnabled`，除非与 B1 同一提交且计划允许。
6. 验证：JVM 单测 + API 36 `execute-app-function`（开关开/关）。

---

## Commit 建议

A5 若只有文档：

```
docs(agent): record API 36 AppFunctions adb verification
```
