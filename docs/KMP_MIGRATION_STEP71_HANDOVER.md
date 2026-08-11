# KMP 迁移交接文档 — Step 71 / 阶段 E.6w（2026-08-11）

本文档记录阶段 E 第 6w 项：**删除废弃 `SharedNodeEditSheet`** + **为 `SharedEditScreen` 引入 i18n 标签体系**。

**前置**：Step 70 / E.6v 已 commit（`52a1ff9`）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `shared/.../EditUiLabels.kt` | **新增** — 节点编辑表单全部 UI 文案标签 |
| `shared/.../SharedEditScreen.kt` | 接受 `labels: EditUiLabels` 参数，替换硬编码英文字符串 |
| `shared/.../SharedEditField.kt` | `SharedEditDropdownField` 新增 `noneOptionLabel` 参数 |
| `shared/.../SharedNodeEditSheet.kt` | **已删除** — E.6v 后无引用，轻量 sheet 正式下线 |
| `shared/.../RootContent.kt` | iOS Config 编辑屏传入 `EditUiLabels()`（默认英文） |
| `androidApp/.../EditScreen.kt` | 从 `strings.xml` 构建 `EditUiLabels` 并传入 |
| `androidApp/.../values/strings.xml` | 新增 32 条 `edit_*` 字符串 |
| `androidApp/.../values-zh-rCN/strings.xml` | 简体中文翻译 |
| `androidApp/.../values-ko/strings.xml` | 韩文翻译 |
| `androidApp/.../values-ru-rRU/strings.xml` | 俄文翻译 |
| `docs/KMP_MIGRATION_PLAN.md` | E.6w 标记完成；更新 iOS EditScreen 状态描述 |

### 未改动

- Android Config 仍走 Navigation3 `Edit` / `Detail` 路由（底层已是共享 UI）
- iOS Config 仍走 Decompose `nodeEditTarget` 全屏编辑（E.6v 行为不变）
- 共享模块 **未引入** compose-resources / stringResource（延续 `*UiLabels` 注入模式）

---

## 设计决策

1. **`EditUiLabels` 独立于 `ConfigUiLabels`** — 编辑表单字段多（30+），单独 data class 更清晰；与 `SettingsUiLabels` / `HomeUiLabels` 模式一致。
2. **Android 本地化，iOS 暂用英文默认** — Android 通过 `stringResource` 注入四语言；iOS `RootContent` 仍用 `EditUiLabels()` 默认值（与 Settings/Config 其他标签相同策略）。
3. **删除 `SharedNodeEditSheet`** — E.6v 已将 iOS 创建/编辑切到全屏 `SharedEditScreen`；该文件零引用，安全删除。
4. **`protocolSettingsTitleFormat`** — 支持 `%1$s Settings` / `%1$s 设置` 等 locale 差异格式。

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
| E.6w | 删 SharedNodeEditSheet + EditScreen i18n | ✅（本步，**待你确认后 commit**） |
| E.6x+ | 后续：ConfigUiLabels Android 补全、iOS 多语言、Android Config 接 Decompose 等 | ⬜ 待定 |

---

## 手动验证清单

### Android（切换系统语言后验证）
- [ ] 中文系统：Config → 添加/编辑节点，表单字段标签显示中文（基本设置、地址、端口等）
- [ ] 英文系统：同上，标签为英文
- [ ] 创建节点保存正常；编辑已有节点字段回填正确
- [ ] 下拉选项空值显示为「无/none」（`noneOptionLabel`）
- [ ] Shared Element 过渡动画无回归

### iOS
- [ ] Config 顶栏 Edit / 节点行 Edit → 全屏编辑（英文标签，与改前一致）
- [ ] 保存 / 返回正常；列表刷新
- [ ] 导入/QR/订阅/Settings 无回归

### 回归
- [ ] Settings → Route sheet 不 crash
- [ ] Config 节点删除 Dialog 正常

---

## Commit 建议（确认后执行）

```
feat(kmp): remove deprecated SharedNodeEditSheet and add EditScreen i18n labels (E.6w)
```

---
