# KMP 迁移交接文档 — Step 15 / 阶段 C.2（2026-08-04）

本文档记录阶段 C 第 2 项：`Rule` 与 `List<String>`（allow_packages）从 Gson 试点迁移至
kotlinx.serialization，验证与 legacy Gson JSON 双向兼容。

验证状态：
- `./gradlew :common:compileDebugKotlinAndroid` **BUILD SUCCESSFUL**
- `./gradlew :common:testDebugUnitTest` **全部通过**（含 `RuleSerializationTest`）
- `./gradlew :common:compileKotlinIosSimulatorArm64` **BUILD SUCCESSFUL**
- `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL**

---

## 改动概要

**目标**：在 `commonMain` 引入 `@Serializable` 模型与共享 `AppJson`，Settings 层
路由规则 / 包名列表读写改走 kotlinx.serialization；Gson 仍可解析新 JSON（app 层
`AbstractConfigParser` 无需改动）。

**跨平台意义**：`Rule` 进入 `commonMain`，iOS 可直接复用；为后续 `RuleObject` 合并
与 `:domain` 迁入打样。

### 改动清单

**新增（`commonMain`）**
- `repository/Rule.kt`：`@Serializable data class Rule` + `defaultRouteList` / `defaultRoutes`
- `repository/AppJson.kt`：`AppJson` 配置 + `encodeRules` / `decodeRules` /
  `encodeStringList` / `decodeStringList`

**修改（`common`）**
- `build.gradle.kts`：`kotlin-serialization` 插件 + `kotlinx-serialization-json`
- `SettingsRepository.kt`：删除内联 `Rule` / Gson 路由与包列表序列化；`gson` 注入保留（DI 未动）

**修改（`app`）**
- `RouteSettingsScreen.kt`：`decodeRules()` 替代 `Gson.fromJson<List<Rule>>`

**测试（`androidUnitTest`）**
- `RuleSerializationTest.kt`：Gson ↔ kotlinx 往返、defaultRoutes 兼容

**grep 复查（Step 15 验收）**
- `SettingsRepository` **零 Gson 用于 Rule / allow_packages** ✅
- `defaultRoutes` **Gson 可解析 kotlinx 输出** ✅
- `AbstractConfigParser` **仍用 Gson 读 `RuleObject`**（JSON 兼容，无需改） ✅

**仍用 Gson（后续步骤）**
- `app/.../model/*` 全部 Xray 配置模型
- `AbstractConfigParser` 整体 config JSON
- 各 `*ConfigParser`

---

## 设计决策

1. **`Rule` 留 `com.android.xrayfa.common.repository` 包** —— app 已有大量 import，
   减少 diff；与 `RuleObject` 并存，注释说明结构一致。
2. **`AppJson` 配置对齐 Gson 习惯** —— `ignoreUnknownKeys`、`encodeDefaults=false`、
   `explicitNulls=false`，升级用户 DataStore 中旧 Gson JSON 可直接 `decodeRules`。
3. **`defaultRoutes` 改 lazy** —— 依赖 `encodeRules`，首次访问时生成；内容与旧默认规则一致。
4. **试点含 `List<String>`** —— `allow_packages` 结构与 Gson 简单数组相同，一并迁移
   验证 KMP 序列化路径；`SettingsRepository` 对 Gson 依赖仅剩未使用的 DI 参数。

---

## 阶段 C 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| C.1 | `:common` 转 KMP multiplatform | ✅ |
| C.2 | Gson → kotlinx.serialization（Rule + String list 试点） | ✅ |
| C.3 | DataStore KMP | ⬜ |
| C.4 | parser + model 物理迁入 `:domain` | ⬜ |

---

## 下一步（C.3）待办清单

### C.3 DataStore KMP

**目标**：`SettingsRepository` 使用 KMP DataStore API；`createDataStore` expect/actual。

**关键文件**：
- `app/.../SettingsDataStore.kt`（Android 工厂）
- `common/.../SettingsRepository.kt`
- 新增 `iosMain` DataStore actual（App Group 路径）

**注意**：
- `SETTINGS_DATA_STORE_NAME` 文件名必须保持不变
- Android 升级用户文件路径不变
- C.3 完成后可考虑移除 `SettingsRepository` 未使用的 `Gson` 注入

**验证命令**：
```bash
./gradlew :common:compileDebugKotlinAndroid :common:testDebugUnitTest
./gradlew :common:compileKotlinIosSimulatorArm64
./gradlew :app:compileDebugKotlin
```

---

## 阶段 D 预览（阶段 C 不做）

- Dagger → Koin
- Compose Multiplatform UI
- iOS VPN Network Extension
