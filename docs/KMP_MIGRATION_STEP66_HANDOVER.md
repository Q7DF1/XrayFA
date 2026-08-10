# KMP 迁移交接文档 — Step 66 / 阶段 E.6r（2026-08-10）

本文档记录阶段 E 第 6r 项：**iOS Config 轻量创建节点**（EditScreen 渐进共享第一步）。

**前置**：Step 65 / E.6q 已 commit（`4203de8`）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `shared/.../NodeEditor.kt` | 新增 `createNode(remark, link)` |
| `shared/.../ConfigState.kt` | `showCreateSheet` / `createError` |
| `shared/.../ConfigTabComponent.kt` | `onOpenCreateNode` / `onCloseCreateNode` / `onSaveCreateNode` |
| `shared/.../DefaultConfigComponent.kt` | 创建 sheet 状态机 |
| `shared/.../SharedNodeEditSheet.kt` | 泛化为 `initialRemark` / `initialLink` / `sheetTitle` |
| `shared/.../ConfigUiLabels.kt` | `createNodeTitle` |
| `shared/.../RootContent.kt` | iOS Config 顶栏 Edit 按钮 + 创建 sheet；空态改为打开创建 |

### 未改动

- Android 仍导航至完整 `EditScreen` 创建/编辑协议表单
- iOS Apps / Route 仍为禁用占位
- 共享 Route Settings 屏

---

## 设计决策

1. **复用 `SharedNodeEditSheet`** — 创建与编辑共用 remark + URL 表单，仅标题与初始值不同；避免再抽一层 wrapper。
2. **`NodeEditor.createNode`** — 与 `updateNode` 对称，走 `ParserFactory.preParse` + `NodeRepository.addNode`，subscriptionId 固定 `SUB_MANUAL`。
3. **Android 行为不变** — 创建仍走 Navigation3 `Edit`；共享 API 供 iOS（及未来 Android 轻量入口可选接入）。

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
| E.6r | iOS Config 轻量创建节点 | ✅（本步，**待你确认后 commit**） |
| E.6s | 共享 Route Settings 或 iOS Apps 桥接 | ⬜ 下一步 |

---

## 手动验证清单

### iOS
- [ ] Config 顶栏 Edit 按钮打开创建 sheet
- [ ] 空列表点击添加打开创建 sheet
- [ ] 输入合法 URL + remark 保存后节点出现在列表
- [ ] 非法 URL 显示错误提示
- [ ] 编辑已有节点仍正常（E.6o 回归）

### Android
- [ ] Config Edit 按钮仍导航完整 EditScreen
- [ ] 导入/QR/订阅菜单无回归

---

## Commit 建议（确认后执行）

```
feat(kmp): add iOS config create node sheet via shared NodeEditor (E.6r)
```

---
