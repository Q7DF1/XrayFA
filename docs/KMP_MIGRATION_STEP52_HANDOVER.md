# KMP 迁移交接文档 — Step 52 / 阶段 E.6d（2026-08-07）

本文档记录阶段 E 第 6d 项：从 Android `HomeScreen` 抽取共享 Home UI 切片
（连接状态、流量卡片、选中节点卡片）；iOS 流量暂 stub 为 0。

**前置**：Step 51 / E.6c 已 commit（`dbbe6db`）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `shared/.../home/HomeConnectionStatusLabel.kt` | 连接状态指示 |
| `shared/.../home/HomeTrafficStatusCard.kt` | 上传/下载速度卡片 |
| `shared/.../home/HomeSelectedNodeCard.kt` | 选中节点摘要 + 空状态 |
| `shared/.../home/HomeSectionHeader.kt` | 区块标题 |
| `shared/.../SharedHomeSection.kt` | 组装完整 Home 区块 |
| `shared/.../AppShell.kt` | 布局改为 top-aligned 以容纳滚动 |
| `shared/build.gradle.kts` | 添加 `materialIconsExtended` |

### 未改动

- `:androidApp` `HomeScreen.kt` / `NodeCard.kt`（Android UI 零变化）
- 流量检测业务逻辑（留 E.5f / 后续 `TrafficStatsSource`）
- Decompose 导航

---

## 设计决策

1. **字符串以参数传入 composable** —— 避免 KMP 依赖 `stringResource(R.string.*)`。
2. **iOS 流量 stub 0 KB/s** —— `VpnController` 尚无 traffic flow；UI 骨架先就位。
3. **简化版节点卡片** —— 只读展示，不含测速/收藏/编辑（Android 完整 `NodeCard` 仍留 app 模块）。
4. **`AppShell` 改为顶部布局 + 垂直滚动** —— 容纳 E.6d 多卡片 Home。

---

## 验证状态

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64
./gradlew :shared:compileDebugKotlinAndroid
./gradlew :androidApp:assembleDebug
# BUILD SUCCESSFUL

xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -configuration Debug -arch arm64 \
  ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build
# BUILD SUCCEEDED
```

---

## 阶段 E 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.6d | 共享 Home 节点卡片 + 流量 UI | ✅（本步，待 commit） |
| E.6e | Decompose 导航骨架 / Android 嵌入 SharedHomeSection | ⬜ 下一步 |
| E.5f（可选） | measureOutboundDelay + traffic stats shim | ⬜ |

---

## 下一步（E.6e）待办

1. **Decompose 根导航 skeleton**（Config / Settings 占位）
2. **（可选）Android 嵌入 `SharedHomeSection` 对照验证**
3. **`TrafficStatsSource` expect/actual** 接 XrayCore traffic flow

---

## Commit 建议

```
feat(kmp): extract shared home status, traffic and node cards (E.6d)
```

---
