# KMP 迁移交接文档 — Step 100 / iOS `measureOutboundDelay` ObjC shim（2026-08-27）

本文档记录：iOS `XrayBridge.measureOutboundDelay` 不再恒回 `-1L`。gomobile 的 `Libv2rayMeasureOutboundDelay` 带 `int64_t*` 出参，K/N cinterop 标记 **Unable to import**；用 ObjC 包装函数返回 `int64_t`，Kotlin 直接调用。未接线 UI 测速按钮，未做 GeoIP。

**前置**：Step 99（`1860393`）。

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `decodeNativeDelayMs` + `DecodeNativeDelayMsTest` | `(ok, ms)` → 失败 `-1`；`CoreController.measureDelay` 共用 |
| `XrayFAMeasureOutboundDelay.h/.m` | 调 `Libv2rayMeasureOutboundDelay`，失败回 `-1` |
| `libv2ray.def` | 把头文件纳入 cinterop |
| `Libv2rayXrayBridge.ios.kt` | `XrayFAMeasureOutboundDelay(config, url)` |
| `native-bridge` / `shared` Gradle | clang 编 `.m` 为 `.o`，iOS link 带上该 object |

### 未改动

- 共享 Home/Config **仍不传** `onTest`（与 Android `RootContent` 现状一致）；本步只打通 native 层
- iOS 无独立 `XrayCore` Koin 绑定
- GeoIP 仍恒 `""`
- Agent Phase C / C1

---

## 设计决策

1. **不能直调 cinterop 自由函数** — `Libv2rayMeasureOutboundDelay(...)` 编译报 `deprecated. Unable to import this declaration.`。`CoreController.measureDelay` 作为方法可以（已用 `LongVarOf`）。
2. **shim 返回标量** — 避免 K/N 再碰 `int64_t*`。
3. **`.o` 进 link** — cinterop 只生成绑定；`.m` 由 `compileDelayShim*` 用 clang 编进 `build/nativeDelayShim/<target>/`。

---

## 验证状态

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
./gradlew :core:native-bridge:testDebugUnitTest \
  --tests com.android.xrayfa.nativebridge.DecodeNativeDelayMsTest
# DecodeNativeDelayMsTest 2 tests, 0 failures
./gradlew :core:native-bridge:compileDelayShimIosSimulatorArm64 \
  :core:native-bridge:compileKotlinIosSimulatorArm64 \
  :shared:compileKotlinIosSimulatorArm64
# BUILD SUCCESSFUL
```

- [x] 映射单测：失败 → `-1`；成功保留原生值（含 `0`）
- [x] iOS simulator Kotlin 编译（含新 cinterop 符号）
- [ ] 真机/模拟器点测速：本步 UI 未接线，未手测 round-trip

---

## 下一步

1. iOS GeoIP（节点国家标记；`IosGeoIpProvider` 仍恒 `""`）
2. 共享 Home/Config 接 `onTest` + iOS `XrayCore` 委托本 shim（双端 RootContent 测速才可见）
3. **不要**做 Agent Phase C / C1，除非产品明确要求
