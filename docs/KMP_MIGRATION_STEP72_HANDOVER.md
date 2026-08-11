# KMP 迁移交接文档 — Step 72 / 阶段 E.6x（2026-08-11）

本文档记录阶段 E 第 6x 项：**Android ConfigUiLabels / ConfigFilterLabels 本地化补全**。

**前置**：Step 71 / E.6w 已 commit（`61df721`）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `androidApp/.../ConfigLabels.kt` | **新增** — `rememberConfigUiLabels()` + `rememberConfigFilterLabels()` |
| `androidApp/.../ConfigScreen.kt` | 移除硬编码 Manual/All/Favorite/Testing…；改用 helper |
| `androidApp/.../values/strings.xml` | 新增 10 条 `config_*` 字符串 |
| `androidApp/.../values-zh-rCN/strings.xml` | 简体中文 |
| `androidApp/.../values-ko/strings.xml` | 韩文 |
| `androidApp/.../values-ru-rRU/strings.xml` | 俄文 |
| `docs/KMP_MIGRATION_PLAN.md` | E.6x 标记完成 |

### 本地化覆盖

| 标签 | 字符串 key |
|------|-----------|
| 筛选 Manual / All / Favorite | `import_manually` / `config_filter_all` / `config_filter_favorite` |
| 测速中 | `config_testing` |
| 节点 CRUD 对话框 | `config_create_node` / `config_edit_node` / `config_delete_node_*` |
| 全部测速无障碍 | `config_speed_test_all_cd` |
| Bug Report 菜单 | `bug_report_header`（已有，此前硬编码） |

### 未改动

- iOS `RootContent` 仍用 `ConfigUiLabels()` 英文默认
- Android Config 导航/Decompose 架构不变
- `NodeCard.kt` 搜索路径内 `Testing...` 仍硬编码（非 SharedConfigSection 主路径，留后续）

---

## 设计决策

1. **集中 helper** — `ConfigLabels.kt` 与 `EditScreen` / `SettingsScreen` 的 labels 注入模式一致，避免 `ConfigScreen` 内 30 行重复。
2. **复用已有 key** — Manual 筛选用已有 `import_manually`；remark 用 `nick_name`。
3. **ConfigFilterLabels 与 ConfigUiLabels 同步** — 两者 filter 文案来源相同，避免 chip 与空态文案不一致。

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
| E.6x | Android ConfigUiLabels 本地化补全 | ✅（本步，**待你确认后 commit**） |
| E.6y+ | 后续：iOS Config/Home/Settings 多语言、Android Config 接 Decompose 等 | ⬜ 待定 |

---

## 手动验证清单

### Android（切换系统语言）
- [ ] Config 筛选 chip：手动 / 全部 / 收藏 显示对应语言
- [ ] 节点行测速中显示「测试中…」等本地化文案
- [ ] 空态「创建首个配置」按钮文案正确
- [ ] 导入菜单 Bug Report 显示本地化标题
- [ ] 全部测速按钮无障碍描述正确

### 回归
- [ ] 筛选切换、节点选中、导入/QR/订阅正常
- [ ] EditScreen 中文标签仍正常（E.6w）

---

## Commit 建议（确认后执行）

```
feat(kmp): localize Android ConfigUiLabels and filter chips (E.6x)
```

---
