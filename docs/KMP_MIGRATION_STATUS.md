# KMP 迁移活清单（Phase 9 收尾）

> 每步交接只更新本文件 + [`IOS_STUBS.md`](./IOS_STUBS.md) + 该步 `KMP_MIGRATION_STEPNNN_HANDOVER.md`。  
> 详细历史仍在 [`KMP_MIGRATION_PLAN.md`](./KMP_MIGRATION_PLAN.md)。  
> 移植后 backlog：[`KMP_POST_MIGRATION.md`](./KMP_POST_MIGRATION.md)。  
> 日期：2026-08-28。当前：Phase 9（Android 可发版 / iOS 保底）。

---

## 硬约束（Phase 9，沿用 Phase 8）

- Android 主路径冻结：`MainActivity` → `AndroidAppShell` → 共享 `RootContent` + `AndroidPlatformRootHooks`。底栏只有 Config | Home；Settings 等是 overlay。
- iOS 只填 `IosPlatformRootHooks`，不改 `RootContent` / `DefaultRootComponent` overlay 语义，不把 iOS 实现写进 `commonMain` 挤掉 Android hook。
- **不再换壳**（禁止再切回 `XrayFAContainer` 作为主路径；Phase 9 已删除该壳）。
- **不做** Agent Phase C / C1、iOS AppFunctions、三星 Live Update / Now Bar 的 App 侧改动。
- 不要把 `IosStubTunBridge` / 宿主 `startXrayCore`「做真」—— TUN/Xray 在 PacketTunnel。
- 编译闸：`:androidApp:compileDebugKotlin` + `AgentScreenRootTabTest` + `:common` / `:domain` `testDebugUnitTest` + `assembleRelease`。
- **KMP 移植已收尾。** 新功能只进 `:shared` + `PlatformRootHooks`；Android 是参考实现。iOS 后续按 [`KMP_POST_MIGRATION.md`](./KMP_POST_MIGRATION.md) 迭代。

---

## 已完成（不必再做）

| 步骤 | 内容 |
|------|------|
| 骨架 | KMP 模块、Room KMP + migration、DataStore、Ktor、Koin、Decompose、gomobile xcframework、PacketTunnel（Xray + hev-socks5）、App Group（含 Room） |
| 73–92 | parser `commonTest`、CI 子集、去重、断 `:domain → datastore`、`:core:data`、四屏薄封装、compose-resources、`AGENT.md`、iOS 底栏/流量/NE 错误/Go 内存、Room 索引、Android 进 `RootContent` |
| 93–98 | Phase 7 Agent A1–A5、B1–B2（Android only）。C1 未做 |
| 99–107 | iOS `darkMode`、outbound delay shim、GeoIP MMDB、GeoLite 下载（后关 iOS 钮）、Home/Config 测速、CommonCrypto digest、宿主链 LibXrayLite、共享 delay URL |
| 108–109 | Android 功能回到共享壳 + hooks；导航对齐原生底栏 |
| **110** | 本清单 + `IOS_STUBS.md` + `IosPlatformRootHooks` 骨架 |
| **111** | iOS `ShareNode` 二维码 + 剪贴板导出 |
| **119** | iOS `BugReport` 共享表单 + GitHub issue URL |
| **118** | Phase 9：Android 发版收尾文档、Home 测速统一 `DelayProbe`、删除无主路径 Navigation3 壳 |

核心已打通、不要当桩修：`IosXrayConfigEncoder`（kotlinx）、GeoIP、digest、`measureOutboundDelay`。

---

## Phase 9 Android P0 真机清单

对照旧 Android **功能**，不记动画差异。用 release / profileable 包。本表由工程侧列出；勾选需真机。

| # | 流程 | 结果 |
|---|------|------|
| 1 | 冷启动 → Home 连接 / 断开 VPN；通知栏流量；权限只问一次 | ⬜ 真机 |
| 2 | Home 测速（已连接）；Config「测全部」同一节点结果合理 | ⬜ 真机（代码已统一 `DelayProbe`） |
| 3 | Config：筛选、搜索、选节点回 Home、剪贴板导入、定位已选、删一条/删全部 | ⬜ 真机 |
| 4 | 节点分享：二维码 + 剪贴板；导出 URL 无 `allowInsecure` | ⬜ 真机 |
| 5 | Config 菜单 bug report：能开、能提交或取消 | ⬜ 真机 |
| 6 | 扫码：相机 + 相册；从订阅页扫完回到订阅 | ⬜ 真机 |
| 7 | 设置（Home 齿轮）：主题 / 自启 / IPv6 / DNS / SOCKS / 隐藏最近任务；返回仍在原 Tab | ⬜ 真机 |
| 8 | 设置 → 路由规则 → 返回设置 → 返回 Home | ⬜ 真机 |
| 9 | 设置 → 分应用：授权、搜索、勾选，重连 VPN 后仍生效 | ⬜ 真机 |
| 10 | 设置 → 日志：录制起停、时长、内容出现 | ⬜ 真机 |
| 11 | GeoLite 下载（需已连 VPN）；geoip.dat / geosite.dat 导入；HexTun | ⬜ 真机 |
| 12 | 订阅：增删改、刷新、筛选回 Config | ⬜ 真机 |
| 13 | 快捷方式「扫码」；磁贴开关 VPN；开机自启（若设置开了） | ⬜ 真机 |
| 14 | Agent（总开关开）：`openScreen` 进 Apps / RouteSettings / Subscriptions | ⬜ 真机（单测已锁 `toRootNavigation`） |
| 15 | 系统返回：overlay 栈；编辑节点返回列表 | ⬜ 真机 |
| 16 | 进程被杀后设置仍在；再连 VPN 仍用当前节点 | ⬜ 真机 |

**iOS 抽检（不挡 Android 发版）：** 连接 → Home 流量 → 断开。崩溃才修。相册 / 分应用继续「开发中」。bug report 已接 GitHub。⬜

---

## 刻意停放（不要再捡进收尾）

- **移植后 iOS（原 Step 112–116）：** geo 文件导入、扫码相册+闪光灯、分应用（先拍板）、overlay 返回 / NE 日志桥、GeoLite 手动放入 App Group（Bug report 已在 Step 119）
- **Android：** Widget、On-Demand VPN、Agent Phase C / 写删除/改路由
- **三星 Live Update / Now Bar：** One UI 开发者选项「允许所有应用的动态通知」；不改 `NotificationHelper`
- **页面保真：** 全屏搜索、共享元素、滚动藏底栏、平板 Config 分栏
- **工程：** `build-logic`、拆 `feature/*`

---

## Step 111+ 顺序

| 步骤 | 内容 | 状态 |
|------|------|------|
| 110 | 活清单 + 桩清单 + `IosPlatformRootHooks` | ✅ |
| 111 | iOS `ShareNode` | ✅ |
| 112–116 | 移植后 iOS backlog（本阶段不排） | ⏭ 见 [`KMP_POST_MIGRATION.md`](./KMP_POST_MIGRATION.md) |
| 119 | iOS `BugReport` | ✅ |
| 117 / 118 | 删除无主路径 `XrayFAContainer` + Phase 9 收尾 | ✅ |
