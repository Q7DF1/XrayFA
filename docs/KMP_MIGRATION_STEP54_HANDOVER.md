# KMP 迁移交接文档 — Step 54 / 阶段 E.6f（2026-08-07）

本文档记录阶段 E 第 6f 项：Android `HomeScreen` 改用 `:shared` 共享 Home UI 切片
（连接状态、流量卡片、区块标题）；Connect / NodeCard 逻辑不变。

**前置**：Step 53 / E.6e 已 commit（`ef9dee6`）。

---

## 前置条件检查（E.6f 入口）

| 前置项 | 状态 |
|--------|------|
| E.6e Decompose + iosX64 | ✅ committed |
| E.6d 共享 composable 已存在于 `:shared` | ✅ |
| `:androidApp` 依赖 `:shared` | ✅（本步新增） |

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `androidApp/build.gradle.kts` | 添加 `implementation(project(":shared"))` |
| `androidApp/.../HomeScreen.kt` | 用 `HomeConnectionStatusLabel` / `HomeTrafficStatusCard` / `HomeSectionHeader` 替换本地副本 |
| 删除 `ConnectionStatusLabel` / `StatusCard` / `SpeedItem` / `SectionHeader` | 减少 Android 重复 UI 代码 |

### 未改动

- `V2rayStarterLarge` / Connect 流程 / `XrayViewmodel`
- 完整 `NodeCard`（含测速/收藏/编辑）
- iOS `SharedHomeSection` / Decompose 导航
- `SharedHomeSection` 整体嵌入（Connect 仍走 ViewModel，非 Koin coordinator）

---

## 设计决策与原因

1. **只替换 E.6d 已抽取的 UI 切片** —— 视觉与 E.6d 抽取源一致；字符串仍来自 `stringResource(R.string.*)` 传入 composable。
2. **保留 Android `NodeCard`** —— 共享版为只读简化卡片；完整交互留 `:androidApp` 直至 nodes 模块迁移。
3. **Expanded 布局 connection detail 标题仍用本地 `Text(headlineSmall)`** —— 与原 expanded 排版一致，避免 tablet 布局变化。
4. **不嵌入完整 `SharedHomeSection`** —— 避免 `AndroidVpnConnectCoordinator` stub 与 ViewModel 双轨 Connect。

---

## 验证状态

```bash
./gradlew :androidApp:assembleDebug
# BUILD SUCCESSFUL

# iOS Simulator launch (Intel x86_64 verified)
xcrun simctl launch booted com.android.xrayfa.ios
```

---

## iOS 运行时修复（E.6e 后）

| 问题 | 原因 | 修复 |
|------|------|------|
| 启动即崩溃 `PlistSanityCheck` | CMP 1.7+ 强制要求高刷 plist 键 | `Info.plist` 添加 `CADisableMinimumFrameDurationOnPhone` |
| Koin `NodeRepository` 创建失败 | 新增 `iosX64` 未配置 Room KSP | `kspIosX64` in `:core:database` |

---

## 阶段 E 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.6f | Android HomeScreen 共享 UI 切片 | ✅（本步，待 commit） |
| E.6g | `DefaultHomeComponent` 抽离 Connect 状态 | ⬜ 下一步 |
| E.5f（可选） | TrafficStatsSource + measureOutboundDelay | ⬜ |

---

## 下一步（E.6g）待办

1. **`DefaultHomeComponent`** —— VPN/节点/Connect 状态迁入 Decompose Component
2. **`SharedHomeSection` 改接 `HomeComponent`** —— iOS + 后续 Android 统一
3. **`TrafficStatsSource` expect/actual** —— 替换 iOS 流量 stub
4. **Config Tab 迁移节点列表**

---

## Commit 建议

```
feat(kmp): wire Android HomeScreen to shared home UI composables (E.6f)
```

---
