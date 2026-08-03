# KMP 迁移交接文档 — Step 12（2026-08-03）

本文档记录阶段 B 第 8 项：`SettingsRepository` 中 `@IntDef` 改为纯 Kotlin `enum`，
消除 `:common` 对 `androidx.annotation.IntDef` 的依赖。

验证状态：
- `./gradlew :common:compileDebugKotlin` **BUILD SUCCESSFUL**
- `./gradlew :common:testDebugUnitTest` **全部通过**
- `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL**

---

## 改动概要

**目标**：`Theme` / `RoutingMode` / `DomainStrategy` 从 Android `@IntDef` annotation
改为 KMP 可编译的 `enum class`；DataStore 仍持久化 `Int` code，用户数据兼容。

### 改动清单

**修改（`common`）**
- `SettingsRepository.kt`：删除 3 个 `@IntDef` annotation class
- 新增 `enum class Theme / RoutingMode / DomainStrategy`（各带 `code: Int` + `fromCode()`）
- `SettingsState` 默认值、`settingsFlow` 映射改用 `.code`
- `setDarkMode` / `setRoutingMode` / `setDomainStrategy` 参数改为 enum 类型

**修改（`app`，类型适配）**
- `SettingsViewmodel`：`setDomainStrategy` / `setRoutingMode` 收 enum；`setDarkMode(Int)` 内调 `Theme.fromCode`
- `RouteSettingsScreen`：Selector 组件改 enum 参数；读 state 时 `fromCode(int)`
- `AbstractConfigParser`：`when` / 比较改用 `.code`
- `XrayFAApplication` / `XrayBaseActivity` / `NotificationHelper`：Theme 比较改用 `.code`

**grep 复查（Step 12 验收）**
- `:common` **零 `androidx.annotation.IntDef`** ✅
- enum `code` 值 **与旧 const 完全一致**（0/1/2）✅
- DataStore 读写 **仍存 Int**，升级用户无感知 ✅

**仍属 Android 绑定（`:common`，后续阶段）**
- `androidx.datastore` —— 阶段 C DataStore KMP
- `Gson` —— 阶段 C kotlinx.serialization
- `javax.inject` on `SettingsRepository` —— 阶段 D

**本步未动（app 层 `@IntDef`，不阻塞 common KMP）**
- `SettingsViewmodel.GEOFileType` —— 留 app 层或随 UI 迁移

---

## 设计决策

1. **`SettingsState` 字段仍为 `Int`** —— UI / parser 大量 Int 比较；Repository 边界
   用 enum，State 暂保留 Int 减少 diff；读取时用 `fromCode` 转 enum 展示。
2. **`code` + `fromCode` 模式** —— 与 DataStore Int 存储解耦；未知值回退默认 enum
   （Theme→AUTO、RoutingMode→ROUTE、DomainStrategy→IP_IF_NON_MATCH）。
3. **SettingsScreen 主题选择仍传 Int 0/1/2** —— 与现有 `SettingsSelectBox` options map 兼容；
   ViewModel 内 `Theme.fromCode` 转换。
4. **enum 名保留原 const 名** —— `LIGHT_MODE` / `GLOBAL` / `ASIS` 等，调用方改动最小。

---

## 阶段 B 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| Step 5–11 | （见前序交接文档） | ✅ |
| Step 12 | `@IntDef` → enum | ✅ |
| Step 13 | `XrayCoreManager` filesDir 路径抽象 | ⬜ |

---

## 下一步（Step 13）待办清单

### 13.1 XrayCoreManager filesDir 路径抽象

**目标**：`XrayCoreManager` 启动 Xray 时仍直读 `context.filesDir`，与 geo 路径接口化对称。

**建议步骤**：
1. 扩展 `XrayAssetPaths` 或新建 `XrayRuntimePaths` 提供 xray 工作目录
2. `AndroidXrayAssetPaths` 实现；`XrayCoreManager` 注入使用
3. 路径格式与现有 `filesDir.absolutePath` 保持一致

**涉及文件（预估）**：
- `common/.../core/XrayAssetPaths.kt`（或新接口）
- `app/.../core/XrayCoreManager.kt`
- `app/.../core/AndroidXrayAssetPaths.kt`

### 13.2 后续（阶段 C 入口）

| 步骤 | 内容 | 关键文件 |
|------|------|----------|
| 阶段 C | Gson → kotlinx.serialization | `model/`、`SettingsRepository` |
| 阶段 C | `:common` 转 multiplatform plugin | `common/build.gradle.kts` |
| 阶段 C | `SettingsState` 字段改 enum 类型（可选） | `SettingsRepository.kt` |

**Step 13 验证命令**：
```bash
./gradlew :common:compileDebugKotlin :common:testDebugUnitTest
./gradlew :app:compileDebugKotlin
# 手动：VPN 连接、XUDP 密钥、geo 路由仍正常
```

---

## 阶段 C/D 预览（更远，Step 13 不做）

- DataStore / Room KMP
- parser + model 迁入 `:domain`
- Dagger → Koin
