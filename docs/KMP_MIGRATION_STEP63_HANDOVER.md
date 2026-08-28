# KMP 迁移交接文档 — Step 63 / 阶段 E.6o（2026-08-10）

本文档记录阶段 E 第 6o 项：**共享节点编辑/删除 BottomSheet** + iOS Config Tab 接入。

**前置**：Step 62 / E.6n 已 commit（`bb6a533`）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `shared/.../config/NodeEditor.kt` | 节点更新（remark/URL）与删除；URL 变更时 re-parse 并保留 id/订阅/收藏/选中状态 |
| `shared/.../navigation/ConfigState.kt` | 新增 `editTarget` / `deleteTarget` / `editError` |
| `shared/.../navigation/ConfigTabComponent.kt` | 编辑/删除 API |
| `shared/.../navigation/DefaultConfigComponent.kt` | 实现编辑/删除；fix combine 收集时保留 overlay 状态 |
| `shared/.../ui/config/SharedNodeEditSheet.kt` | 共享 BottomSheet（remark + 原始 URL） |
| `shared/.../ui/config/ConfigUiLabels.kt` | 编辑/删除文案 |
| `shared/.../ui/RootContent.kt` | iOS Config：编辑 Sheet + 删除对话框 |
| `shared/.../di/SharedServicesDiModule.kt` | 注册 `NodeEditor` |
| `androidApp/.../RememberAndroidConfigComponent.kt` | 注入 `NodeEditor` |

### 未改动

- Android 完整 `EditScreen`（协议表单构建器）仍走 Navigation3 `Detail`/`Edit`
- Logcat / Apps / 路由 Settings iOS parity
- Android `ConfigScreen` 嵌入 `SharedConfigImportMenu`（Android 已有 SplitButton 菜单）

---

## 设计决策

1. **轻量 Edit Sheet 而非完整 EditScreen 移植** — 550+ 行协议表单留 Android；iOS 共享壳用 remark + 原始 URL 编辑，满足常见修改场景。
2. **URL 变更 = delete + insert 同 id** — Room `updateNode` 仅更新 url/port/remark；完整 re-parse 需替换实体，保留 favorite/selected/subscriptionId。
3. **Android 仍导航到 Detail/Edit** — Strangler Fig；共享 `ConfigComponent` API 可供后续 Android 渐进迁移。

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
| E.6o | 共享节点编辑/删除 + iOS Config 接入 | ✅（本步，待 commit） |
| E.6p | Logcat/Apps/路由 Settings / Android Config 菜单统一 | ⬜ 下一步 |

---

## 手动验证清单

### iOS 共享壳
- [ ] Config Tab → 节点行 Edit → 修改 remark/URL → Save → 列表更新
- [ ] Config Tab → Delete → 确认 → 节点消失
- [ ] 修改已选中节点 URL → VPN 重启（若已连接）

### Android（回归）
- [ ] ConfigScreen → Edit 仍导航到 Detail/Edit 全屏表单
- [ ] 删除/QR/订阅流程无回归

---

## Commit 建议

```
feat(kmp): add shared node edit/delete sheet for iOS config tab (E.6o)
```

---
