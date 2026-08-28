# KMP 迁移交接文档 — Step 2（2026-08-01）

本文档记录本次会话的两项工作：

1. **计划内**：阶段 A 第 2 项 —— Base64 统一为 `kotlin.io.encoding.Base64`
2. **非计划内**：修复点击"开始"即崩溃的 JNI 签名不匹配（hev-socks5-tunnel 子模块超前导致），
   该问题阻塞真机验证，必须先行修复

验证状态：`./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL**
（仅剩与本次无关的 `Link` data class deprecation 存量警告）。

---

## 第一部分：Base64 统一（阶段 A 第 2 项 ✅）

全项目 **8 处** Base64 用法统一收敛到新封装类
`common/src/main/java/com/android/xrayfa/common/utils/Base64Compat.kt`，
消除所有 `java.util.Base64` / `android.util.Base64` import。

### ⚠️ 关键发现：Kotlin 2.0.21 Base64 与 JDK 行为不等价（已实证）

在 kotlin-stdlib 2.0.21 jar 上用 Java 实测：

| 输入 | `java.util.Base64.getDecoder()` | `kotlin.io.encoding.Base64`（默认） |
|------|------|------|
| 缺 padding（`aGVsbG8`） | ✅ 容忍解码 | ❌ **抛异常**（padding option = PRESENT） |
| 含 `\n` / `-` / `_` | ❌ 抛异常 | ❌ 抛异常（严格字母表，与 JDK 相同） |
| 错误 padding / padding 后还有数据 | ❌ 抛异常 | ❌ 抛异常 |
| 空串 / 单字符 | 空数组 / 抛异常 | 空数组 / 抛异常 |

**结论**：Kotlin 默认实例字母表严格性与 JDK 一致，但**强制要求 padding**。
若直接裸替换，野外常见的无 padding vmess/ss 链接会当场崩解析——属于静默行为回归。

**解法**（2.0.21 已内置 `Base64.PaddingOption`，无需升级 Kotlin）：

- 解码 → `Base64.Default.withPadding(ABSENT_OPTIONAL)`：8 组边界用例与 JDK 逐一 MATCH
- URL-safe 无 padding 编码 → `Base64.UrlSafe.withPadding(ABSENT)`：等价
  `android.util.Base64(URL_SAFE|NO_PADDING)` 与 `getUrlEncoder().withoutPadding()`

`Base64Compat` 上 `@OptIn(ExperimentalEncodingApi::class)` 已封装，**调用方无需 opt-in**。
放在 `:common` 模块是因为 `:common` 自身也有 1 处用法，且 `:app` 依赖 `:common`，两处都可见。

### 改动清单

**新增**
- `common/.../utils/Base64Compat.kt`：`decode` / `encode` / `encodeUrlSafeNoPadding` 三个方法

**app 模块（7 文件）**

| 文件 | 原用法 | 现用法 |
|------|--------|--------|
| `parser/SubscriptionParser.kt` | `getDecoder()` 解码订阅 | `Base64Compat.decode` |
| `parser/VMESSConfigParser.kt` | 解/编码 vmess JSON | `decode` / `encode` |
| `parser/SocksConfigParser.kt` | `ProxyLinkUtils.tryBase64Decode` | `decode`（严格性一致，兜底逻辑不变） |
| `parser/ShadowSocksConfigParser.kt` | ss 主段解码 + userInfo 重编码（4 处） | `decode` / `encode` |
| `utils/HttpResponseUtils.kt` | base64 响应头解码（带兜底） | `decode` |
| `utils/LinkUtils.kt` | cleanVmess 解/编码 | `decode` / `encode` |
| `utils/Device.kt` | `android.util.Base64(NO_PADDING\|URL_SAFE)` 编码 ANDROID_ID | `encodeUrlSafeNoPadding` |

**common 模块（1 文件，Step 1 交接清单遗漏）**
- `common/.../utils/SocksConfigGenerator.kt`：`getUrlEncoder().withoutPadding()` → `encodeUrlSafeNoPadding`

注：`di/GlobalModule.kt` 的 `provideBase64Parser` 仅是函数名含 "Base64"，无实际依赖，未动。

---

## 第二部分：hev-socks5-tunnel JNI 崩溃修复（非计划内 ✅）

### 现象

安装 debug 包后点击"开始"，进程直接 native abort（无 Java 崩溃弹窗）。

### 根因（crash.log 分析）

```
Failed to register native method xrayfa.tun2socks.TProxyService.TProxyStartService(Ljava/lang/String;I)Z
JNI DETECTED ERROR: NoSuchMethodError ... → System.loadLibrary 时 abort
```

因果链：

1. 工作区 `hev-socks5-tunnel` 子模块 checkout 在 `64cc609`（2.16.0-24），
   而 superproject 记录的是 `180cda8`（`git status` 中子模块显示 ` M` 即此意：超前未固化）
2. 上游新版改了 JNI 接口：`TProxyStartService` / `TProxyStopService` 返回值 `void → boolean`，
   并**新增** `TProxyIsRunning`；且 `64cc609 "HevJNI: Add registration result checking"`
   让注册失败从静默变为致命
3. `RegisterNatives` 要求注册的 4 个方法在 Kotlin 类中**全部存在且签名一致**，
   Kotlin 侧还是旧声明（2 个 void + 缺 1 个方法）→ 注册失败 → abort

**与 Base64 迁移无关**：崩溃点在 `TProxyService.<clinit>` 的 `System.loadLibrary`，纯 Kotlin 改动不会触及。

**libv2ray 无风险**：`AndroidLibXrayLite` 子模块同样超前，但 app 用的是预编译 AAR
（`app/libs/libv2ray.aar` 与子模块内是同一文件，53MB 逐字节相同），
gomobile 重绑需手动跑 `bindXrayLib` 任务，正常构建不受子模块 bump 影响。

### 修复内容（`tun2socks/.../TProxyService.kt`）

```kotlin
external fun TProxyStartService(configPath: String, fd: Int): Boolean  // void → Boolean
external fun TProxyStopService(): Boolean                              // void → Boolean
external fun TProxyIsRunning(): Boolean                                // 新增（必须声明，否则注册失败）
external fun TProxyGetStats(): LongArray                               // 不变
```

### 关键语义决策：`running` 保持无条件 `= true`（经 review 讨论）

曾尝试 `running = TProxyStartService(path, fd)`，**被 review 驳回**，原因：

`running` 标志的唯一消费者是停止路径（`XrayBaseService.stopXrayCoreService`：
`if (tun2SocksService.isRunning()) tun2SocksService.stopTun2Socks()`）；
UI 状态来自 `xrayCoreManager.startXrayCore` 的返回值，与该标志无关。

| 场景 | 原语义（无条件 true） | `running = 返回值` |
|------|------|------|
| 正常启动 | ✓ | ✓ 相同 |
| 原生层真失败（线程创建失败） | 停止时空调 `TProxyStopService()`，安全 ✓ | 跳过停止，也安全 ✓ |
| 已运行时再 start（快速双击磁贴 CONNECT 竞态） | 停止路径正常 ✓ | **标志=false 但隧道活着 → 停止被跳过 → 隧道泄漏** ✗ |

原生返回 false 有"真失败"和"已在运行"两种含义，直接赋值会把后者误判为"未运行"。
而无条件 `true` 在所有场景自洽，因为 `TProxyStopService()` 对"未运行"本身安全
（`thread_joinable=0` 直接返回）。最终方案：**恢复原语义，返回值仅用于错误日志**。

### 遗留事项

- 两个子模块超前尚未提交。真机验证 VPN 正常后，提交会固化新 submodule 指针
  （与仓库 "chore: update submodules" 系列提交一致）
- **反向选项**：若想回滚子模块（`git submodule update --checkout tun2socks/src/main/jni/hev-socks5-tunnel`），
  必须同步 revert `TProxyService.kt` 的签名改动（旧 .so 是 void 签名），否则同样崩

---

## 下一步（Step 3）待办清单

阶段 A 第 3 项：`java.net.URI/URLDecoder/URLEncoder` → 纯 Kotlin（JVM-only，阻碍进 commonMain）：

- `parser/SocksConfigParser.kt`（`ProxyLinkUtils` 全部三个方法都依赖）
- `parser/ShadowSocksConfigParser.kt`（tag 的 URL 编解码，`java.net.URLDecoder/URLEncoder` 内联调用）
- 其他 parser 待 grep 确认（`java.net.` 前缀）

**注意点**：
1. `URLDecoder.decode(s, "UTF-8")` 会把 `+` 解码为空格（application/x-www-form-urlencoded 语义），
   而 `%20` 也是空格——纯 Kotlin 实现必须保留 `+`→空格的语义差异。
2. `URLEncoder.encode` 把空格编码为 `+` 而非 `%20`，同样要保留。
3. `URI(url).host/port/userInfo/fragment` 的解析需用纯 Kotlin 重写（可抽 `UrlCodec`/`UriParser` 工具类），
   注意 IPv6 host（`[::1]`）与 `-1` 端口（缺失时）语义。
4. 另注意 `common/.../SocksConfigGenerator.kt` 还有 `java.security.SecureRandom`（JVM-only），
   属后续 SecureRandom 抽象事项，本次未动。
