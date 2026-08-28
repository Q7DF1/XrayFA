# KMP 迁移交接文档 — Step 58 / 阶段 E.6j（2026-08-07）

本文档记录阶段 E 第 6j 项：`DefaultConfigComponent` + `SharedConfigSection` 共享 Config Tab 节点列表；
iOS Config Tab 替换 Placeholder；Android `ConfigScreen` 嵌入共享列表（TopBar/搜索/对话框仍留 androidApp）。

**前置**：Step 57 / E.6i 已 commit（`9ea5b7e`）。

---

## 前置条件检查（E.6j 入口）

| 前置项 | 状态 |
|--------|------|
| E.6i iOS TrafficStatsSource + Expanded Home 统一 | ✅ committed |
| E.6g/E.6h HomeComponent + SharedHomeSection 模式 | ✅ |
| iOS Koin Room/Subscription/NodeRepository | ✅ E.6c |
| Android ConfigScreen 节点列表 + 订阅 Filter 逻辑 | ✅ 本步迁移源 |

**结论**：E.6j 前置已全部满足。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `shared/.../navigation/ConfigFilterIds.kt` | SUB_ALL / SUB_MANUAL / SUB_FAVORITE 常量 |
| `shared/.../navigation/ConfigState.kt` | Config Tab 展示状态 |
| `shared/.../navigation/ConfigTabComponent.kt` | `ConfigComponent` 接口 |
| `shared/.../navigation/DefaultConfigComponent.kt` | 节点过滤/选中/收藏/VPN restart（对齐 ViewModel） |
| `shared/.../navigation/ConfigComponentFactory.kt` | Koin 工厂 |
| `shared/.../ui/config/SharedConfigNodeRow.kt` | 列表行 UI（对齐 NodeCard list 模式） |
| `shared/.../ui/config/SharedConfigSection.kt` | Filter Chips + LazyColumn + 空状态 |
| `shared/.../ui/config/ConfigUiLabels.kt` | 可注入字符串 |
| `shared/.../navigation/RootComponent.kt` | Config child → `ConfigComponent` |
| `shared/.../navigation/DefaultRootComponent.kt` | 创建 `DefaultConfigComponent` |
| `shared/.../ui/RootContent.kt` | iOS/Android 共享壳 Config Tab |
| `androidApp/.../config/RememberAndroidConfigComponent.kt` | Compose 生命周期内创建 Component |
| `androidApp/.../component/ConfigScreen.kt` | 嵌入 SharedConfigSection；搜索/Add/QR 仍本地 |
| `androidApp/build.gradle.kts` | 添加 `decompose.extensions.compose`（subscribeAsState） |

### 未改动

- ConfigScreen 搜索栏 / Add SplitButton / QR 导入 / BugReport 对话框
- Config Tab Shared Element 动画（rowModifier 暂移除，避免非 Composable lambda 限制）
- Settings Tab 仍为 Placeholder
- 子仓库 `AndroidLibXrayLite`

---

## 设计决策与原因

1. **`DefaultConfigComponent` 复制 ViewModel 过滤逻辑** — `combine(allNodes, favorites, subscriptions)` + filter id + reversed()，与 `XrayViewmodel.nodes` 行为一致，不 regress 订阅/Manual/Favorite 筛选。
2. **`onSelectNode` 等价 `setSelectedNode`** — clearSelection → updateSelectById → `vpnController.restartIfNeeded()`；导航留 UI 层（Android → Home，iOS → selectTab Home）。
3. **Android 双轨最小化** — 列表/Filter/选中/收藏走 Component；搜索仍用 ViewModel `queryNodes`；`LaunchedEffect` 同步 filter id 到 ViewModel 供搜索筛选。
4. **TopBar/平台对话框留 androidApp** — QR/CameraX/Toast 等平台 API 不强行塞进 commonMain，符合 Strangler Fig 增量策略。
5. **iOS Config Tab 首屏可用** — 节点列表/Filter/选中/收藏跨平台；Add/QR/订阅管理仍待后续 E.6k。

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
| E.6j | Config Tab 共享节点列表 + Decompose Component | ✅（本步，待 commit） |
| E.6k | Config Add/QR/订阅入口 + Settings Tab 迁移 | ⬜ 下一步 |
| E.5f（可选） | measureOutboundDelay 共享 | ⬜ |

---

## 下一步（E.6k）待办

1. **Config Tab Add/Import 动作** — 剪贴板/QR/订阅导航 expect/actual 或 Android 保留 + iOS 逐步实现
2. **Settings Tab** — `SettingsComponent` + 共享 Settings 切片
3. **恢复 Config 列表 Shared Element**（可选）— `@Composable rowModifier` 或在 items 块内直接应用
4. **Home NodeCard 测速** — 接入共享 delay 测试或 `HomeComponent` 扩展

---

## 子仓库备忘

| 仓库 | 事项 | 动作 |
|------|------|------|
| `AndroidLibXrayLite` | 无改动 | 无需 issue/PR |

---

## Commit 建议

```
feat(kmp): add shared Config tab node list with DefaultConfigComponent (E.6j)
```

---
