# KMP 迁移交接文档 — Step 108 / 还原 Android 迁移前功能（2026-08-27）

本文档记录：把 Android 主路径从 Step 92 的共享 `RootContent` 切回迁移前的 Navigation3 `XrayFAContainer`，补齐迁移后丢失的 Android 功能。iOS 仍走 `RootContent`；Android 专有能力在 iOS 上提示「开发中」。未做 Agent Phase C / C1。

**前置**：Step 107（`c2054df`）。

---

## 为什么会丢功能

Step 92 让 `MainActivity` → `AndroidAppShell` → `RootContent`。共享壳是 iOS 用的精简版，没有接上 Android 已有的 Navigation3 图：

| 迁移前（`XrayFAContainer`） | Step 92 后（`RootContent`） |
|-----------------------------|-----------------------------|
| 真实 `QRCodeScannerScreen`（相册 + 闪光灯） | Android `SharedQrScannerScreen` 是占位文案 |
| Config：搜索、定位选中、删除全部、Bug report、节点二维码分享 | 导入菜单只有剪贴板 / 扫码 / 订阅 |
| 设置：整页滚动；点「路由设置」进入 `RouteSettingsScreen` | `showRouteSettings` 置位后没有画面 |
| 平板 Home 双栏、快捷方式 / Tile / Agent 开屏落到真实路由 | Agent `Apps` / `RouteSettings` 只切到 Settings tab |

旧屏幕文件一直在，只是不再挂到 `MainActivity`。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `AndroidAppShell` + `MainActivity` | 再走 `XrayFAContainer`，不再创建 Decompose `RootComponent` |
| `XrayFAContainer` | 接 `AndroidRootActionCoordinator`：扫码 / 连 VPN / 开屏 |
| `AgentScreen.toDestination()` | Home/Config/Subscription/Settings/Apps/RouteSettings 落到真实目的地 |
| `SettingsScreen` | 去掉与共享网络区重复的 GeoLite / 测速 URL |
| `RootContent`（iOS） | 真正打开路由设置；设置页整页滚动；Config/Settings 标题本地化 |
| `PlatformRootHooks` 默认实现 | 分应用 → 「开发中」页；geoip / geosite / HexTun 点按提示开发中 |
| 四语文案 | `in_development` / `in_development_message` |

### 未改动

- iOS VPN / 测速 / GeoLite 门控
- Agent Phase C / C1
- 未删除 Step 92 留下的 `AndroidAppsScreen` / `AndroidLogcatScreen` 包装（已无主路径引用）

---

## 设计决策

1. **Android 壳回到 `XrayFAContainer`，而不是把功能逐项塞进 `RootContent`** — 迁移前功能已经在 Navigation3 图里；再写一遍共享壳会继续漏。`AGENT.md` 也仍写 Android 走 `XrayFAContainer`。
2. **Agent `openScreen` 不再折叠到三个 Tab** — 分应用 / 路由设置 / 订阅可以直接打开对应页。
3. **iOS 不实现 Android 专有项** — 分应用、geoip/geosite 导入、HexTun 给「开发中」；路由设置本身是共享屏，只是之前没挂上，本步接上。

---

## 验证状态

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
./gradlew :androidApp:compileDebugKotlin :androidApp:testDebugUnitTest \
  --tests com.android.xrayfa.ui.AgentScreenRootTabTest
./gradlew :shared:compileKotlinIosSimulatorArm64
```

- [x] `:androidApp:compileDebugKotlin` + `AgentScreenRootTabTest` + `:shared:compileKotlinIosX64` 通过
- [ ] 真机：Config 搜索 / 相册扫码 / 路由设置 / 分应用勾选
- [ ] 快捷方式「扫码」打开相机并导入
- [ ] Agent `openScreen` Apps / RouteSettings 进入对应页，而不是停在设置列表

---

## 下一步

1. 可选：删掉 Android 侧已无引用的 `RootContent` 包装（`AndroidAppsScreen` 等）
2. iOS 分应用 / geo 文件导入仍是产品缺口，不要假装可用
3. **不要**做 Agent Phase C / C1，除非产品明确要求
