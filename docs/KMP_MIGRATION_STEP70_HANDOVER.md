# KMP 迁移交接文档 — Step 70 / 阶段 E.6v（2026-08-10）

本文档记录阶段 E 第 6v 项：**共享完整 EditScreen**，iOS 与 Android 节点创建/编辑统一为全屏表单。

**前置**：Step 69 / E.6u 已 commit（`1564c4d`）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `shared/.../NodeEditForm.kt` | 节点编辑表单数据类 |
| `shared/.../NodeFormEditor.kt` | 从 `DetailViewmodel` 迁出的 parse/encode/save 逻辑 |
| `shared/.../SharedEditScreen.kt` | 全屏共享编辑 UI（FilterChip 协议选择，CMP 兼容） |
| `shared/.../SharedEditField.kt` | 共享 TextField / DropdownField |
| `shared/.../ConfigState.kt` | `NodeEditTarget`（Create / Edit）；移除 sheet 状态 |
| `shared/.../ConfigTabComponent.kt` | 统一 `onCloseNodeEdit` / `onSaveNodeEdit(form)` API |
| `shared/.../DefaultConfigComponent.kt` | 全屏编辑状态机 + `NodeFormEditor` 保存 |
| `shared/.../RootContent.kt` | iOS Config：创建/编辑 → `SharedEditScreen`（替代轻量 sheet） |
| `shared/.../SharedServicesDiModule.kt` | 注册 `NodeFormEditor` |
| `androidApp/.../EditScreen.kt` | Strangler 薄包装：Shared Element + 委托 `SharedEditScreen` |
| `androidApp/.../XrayFAContainer.kt` | 移除 `DetailViewmodel` 依赖 |
| `androidApp/.../MainActivity.kt` | 移除 `DetailViewmodel` 构造/注入 |
| `androidApp/.../AppComponentDiModule.kt` | MainActivity 不再注入 `DetailViewmodelFactory` |
| `androidApp/.../AppViewModelDiModule.kt` | 移除 `DetailViewmodelFactory` |
| `androidApp/.../viewmodel/DetailViewmodel.kt` | **已删除**（逻辑迁入 `NodeFormEditor`） |
| `docs/KMP_MIGRATION_PLAN.md` | E.6v 标记完成 |

### 保留未删

- `shared/.../SharedNodeEditSheet.kt` — 暂留文件，RootContent 已不再引用；后续可删

### 未改动

- Android Config 仍通过 Navigation3 `Edit` / `Detail` 路由进入（行为与现网一致，底层 UI 已共享）
- Android 节点删除仍走 `XrayViewmodel.showDeleteDialog`（非 shared ConfigComponent）
- iOS 节点删除仍走 shared AlertDialog

---

## 设计决策

1. **`NodeFormEditor` 替代 `DetailViewmodel`** — 无 Android ViewModel 依赖；Koin 注入，`commonMain` 可编译。
2. **协议选择用 FilterChip** — 避开 `ExperimentalMaterial3ExpressiveApi`（ToggleButton / ButtonGroup），CMP material3 1.3.1 与 androidApp 1.5.0-alpha15 均可用。
3. **下拉选择用 Dialog** — 避开 `ExposedDropdownMenuBox`（与 E.6u ModalBottomSheet 同类 material3 版本冲突）。
4. **iOS 对齐 Android 全屏表单** — Config 顶栏 Edit / 空态添加 / 节点行 Edit 均打开 `SharedEditScreen`；轻量 remark+URL sheet 退出主路径。
5. **Android Strangler** — `EditScreen.kt` 仅保留 Shared Element 动画包装；保存逻辑与 iOS 相同走 `NodeFormEditor`。
6. **`ConfigState` 统一 `nodeEditTarget`** — `NodeEditTarget.Create | Edit(node)` 替代 `showCreateSheet` + `editTarget`。

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
| E.6v | 共享完整 EditScreen，iOS 与 Android 统一 | ✅（本步，**待你确认后 commit**） |
| E.6w+ | 后续：删 `SharedNodeEditSheet`、Android Config 接 Decompose、表单 i18n 等 | ⬜ 待定 |

---

## 手动验证清单

### Android
- [ ] Config 顶栏 Edit → 全屏 EditScreen（字段完整：协议/Basic/Transport）
- [ ] 空列表添加 → 全屏 EditScreen
- [ ] 节点行 Edit → Detail 路由全屏表单，字段从 URL 正确回填
- [ ] 保存后节点列表更新；编辑已选节点时 VPN 按需重启
- [ ] Shared Element 过渡动画无回归
- [ ] VLESS / VMess / SS / Trojan / Hysteria2 各协议保存后 URL 可再解析

### iOS
- [ ] Config 顶栏 Edit → 全屏 SharedEditScreen（**不再是 sheet**）
- [ ] 空态添加 → 全屏创建表单
- [ ] 节点行 Edit → 全屏编辑，字段回填正确
- [ ] 保存 / 返回 正常；列表刷新
- [ ] 导入/QR/订阅/删除/Settings 无回归

### 回归
- [ ] Settings → Route sheet 不 crash（E.6u Dialog sheet）
- [ ] Settings → Apps / Logcat 无回归

---

## Commit 建议（确认后执行）

```
feat(kmp): share full EditScreen and unify iOS/Android node editor (E.6v)
```

---
