# KMP 迁移交接文档 — Step 69 / 阶段 E.6u（2026-08-10）

本文档记录阶段 E 第 6u 项：**SharedModalBottomSheet 崩溃修复** + **EditScreen 路线调整（写进计划）**。

**前置**：Step 68 / E.6t 已 commit（`11538e8`）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `shared/.../SharedModalBottomSheet.kt` | Dialog 底部 sheet（避开 ModalBottomSheet 版本冲突） |
| `shared/.../SharedNodeEditSheet.kt` 等 | 改用 SharedModalBottomSheet |
| `docs/KMP_MIGRATION_PLAN.md` | E.6 进度表 + EditScreen「iOS 对齐 Android」决策 |
| `androidApp/.../ConfigScreen.kt` | **已恢复**：顶栏 Edit / 空态添加仍走 `Edit` 全屏（不接轻量 sheet） |

**崩溃根因**：shared 编译依赖 CMP material3 1.3.1，androidApp 运行时为 1.5.0-alpha15，`ModalBottomSheet` 签名不兼容 → `NoSuchMethodError`。

---

### 未改动

- Android 创建/编辑仍走完整 `EditScreen`（`Edit` / `Detail` 路由）
- iOS Config 仍用轻量 sheet（临时，直至 E.6v 共享完整 EditScreen）

---

## 设计决策

1. **Android 不改创建入口** — 轻量 remark+URL 与剪贴板导入重复；全屏 EditScreen 才是 Android 独有价值。
2. **iOS 最终对齐 Android** — 完整 `EditScreen` 共享化列入 E.6v；Android 为参照实现。
3. **SharedModalBottomSheet 用 Dialog** — commonMain 避开 `ModalBottomSheet` 二进制不兼容；供 iOS sheet 及 shared 路由/订阅屏使用。

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
| E.6u | ModalBottomSheet 崩溃修复 + 计划更新 | ✅（本步，**待你确认后 commit**） |
| E.6v | 共享完整 EditScreen，iOS 与 Android 统一 | ⬜ 下一步 |

---

## 手动验证清单

### Android
- [ ] Config 顶栏 Edit → 全屏 EditScreen（非 sheet）
- [ ] 空列表添加 → 全屏 EditScreen
- [ ] 节点行 Edit → Detail 全屏表单
- [ ] Settings → Route 添加规则 sheet 不 crash
- [ ] 导入/QR/订阅/删除无回归

### iOS（回归）
- [ ] Config 创建/编辑 sheet 仍可用

---

## Commit 建议（确认后执行）

```
fix(kmp): use Dialog-based shared sheet to fix Android material3 crash (E.6u)
```

---
