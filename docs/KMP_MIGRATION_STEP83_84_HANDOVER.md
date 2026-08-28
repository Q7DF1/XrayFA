# KMP 迁移交接 — Step 83–84 / i18n + AGENT.md（2026-08-21）

期中 **R-9 / R-10** 收口。Android `res/values*/strings.xml` 仍保留（Manifest、通知、非 Compose）。

**前置**：80–82 已 commit（`b85d52e`）。

## 83 compose-resources

- `shared/src/commonMain/composeResources/values{,-zh-rCN,-ko,-ru-rRU}/strings.xml`
- `remember*UiLabels()` 读 `Res.string.*`；iOS `RootContent` 与 Android 薄封装都改走这套
- 新增路由预设 / 分应用搜索等 key（zh-rCN 有中文；ko/ru 暂英文本）

## 84 AGENT.md

按当前 KMP 模块、Koin、CI、R-1…R-9 重写。版本以 `gradle/libs.versions.toml` / `gradle.properties` 为准。

编译：`:shared:compileDebugKotlin` + `:androidApp:compileDebugKotlin` 通过。

P2 未做：build-logic、catalog 硬编码清理、iOS App Group、ProGuard 冒烟、`xcodebuild`。
