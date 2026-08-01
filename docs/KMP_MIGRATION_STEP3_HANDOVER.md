# KMP 迁移交接文档 — Step 3（2026-08-02）

本文档记录阶段 A 第 3 项：`java.net.URI / URLDecoder / URLEncoder` → 纯 Kotlin。

验证状态：`./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL**；
`./gradlew :common:testDebugUnitTest` **全部通过**（含 ~5500 用例的 JDK 对照 fuzz）。
（仅剩与本次无关的 `Link` data class deprecation 存量警告。）

---

## 改动概要

全项目 parser 层 **6 个文件** 的 java.net 调用收敛到新封装类
`common/src/main/java/com/android/xrayfa/common/utils/UrlCodec.kt`，
parser 包现已**零 `java.*` import**（仅剩 javax.inject，属 Dagger→Koin 阶段）。
方法与 Step 2 相同：先用本机 JDK 实证探测全部边界行为，再实现纯 Kotlin 版本，
并用**永久性 JVM 对照单测**（`common/src/test/.../UrlCodecTest.kt`，直接以 JDK 类为基准
逐用例断言相等）锁定等价性。

### ⚠️ 关键发现（全部为实证，非推测）

**URLDecoder / URLEncoder 侧：**

1. `URLDecoder` 的 escape 解析直接泄漏 `Integer.parseInt(两位, 16)` 的怪癖：
   **`%+a` 合法**（=0x0A，正号被接受）、`%-a` 抛异常（负值被拒）但 `%-0` 合法（=0）、
   **全角数字/字母 `%４１` 也能解**（Character.digit 接受全角）。
   Kotlin 侧的 `digitToIntOrNull(16)` 恰好同样接受全角，已复刻全部怪癖。
2. 非法 UTF-8 字节序列（`%ff`、`%c3%28`）**不抛异常**，输出 U+FFFD 替换符——
   Kotlin `decodeToString()` 默认行为一致。
3. `URLEncoder` 仅 `[A-Za-z0-9.*-_]` 原样保留，空格→`+`，`~!()` 等全部大写 %XX 编码。

**java.net.URI 侧（比预期复杂得多）：**

4. **getter 会自动解码**：`getUserInfo()/getQuery()/getFragment()` 返回 %XX 已解码形式
   （`#%E4%B8%AD` → `中文`），但 **`+` 不转空格**。parser 随后又调一次 URLDecoder——
   双重解码链必须原样保留（对已解码串幂等，对 `%25E4` 这类双重编码有实际效果）。
5. **非法 escape（`%zz`、残缺 `%`）在构造期抛 URISyntaxException**；控制字符、
   ASCII 排除集（空格 `"<>{|}\^` 等）、Unicode 空格（NBSP、figure space）同样构造期抛。
   非 ASCII 文字（中文）则完全合法。
6. **authority 解析失败有两种结局**：hostname 非法（下划线、纯数字顶标签、
   虚线开头结尾、空标签）/ 端口非数字或溢出 / 多 `@` / host 含 escape
   → 退化为 registry-based（host=null、userInfo=null、port=-1）；
   而**方括号错位**（userinfo 里、path 里、多 `@` 且带括号）→ 直接抛异常。
   query/fragment 里的方括号反而合法。
7. **IPv6 host 保留方括号**（`[::1]`），支持内嵌 IPv4 与 `%zone`；
   zone 里的 `%` **不是** escape（escape 校验须跳过方括号段）。
8. **hostname 语法**：多标签时顶标签必须字母开头（`a.1`、`example.123`、`12a.34b` 全被拒），
   纯数字点分必须恰好是合法 IPv4（`1.2.3.4` ✓、`999.1.1.1`/`1.2.3` ✗），
   单标签无限制（`123`、`1host` ✓），允许一个尾点（`host.com.` ✓ 且保留在返回值里，
   但尾点存在时不走 IPv4 分支：`1.2.3.4.` ✗）。
9. `socks://` 末尾无内容抛异常，但 `socks://#f` / `socks://?q` 是合法 registry（host=null）。
10. scheme 大小写保留（`SOCKS`）；opaque URI（`trojan:foo`）scheme 有效、authority 全 null。

**异常类型决策**：`parseUri` 对 JDK 抛 URISyntaxException 的场景改抛
`IllegalArgumentException`——已 grep 确认全部上游 catch 均为泛型
`catch (e: Exception)`，行为等价。`UrlCodec.decode` 抛的本来就是
IllegalArgumentException（与 URLDecoder 相同类型）。

### 改动清单

**新增**
- `common/.../utils/UrlCodec.kt`：`decode` / `encode` / `parseUri` + `ParsedUri`
  （约 300 行，含 hostname 语法、IPv6 校验、JDK 怪癖复刻，注释标明 JDK 等价语义）
- `common/src/test/.../utils/UrlCodecTest.kt`：~130 个手选用例（全部探测结果）
  + **fuzz**：URI 特征符号表（含全角 `４`/`ｆ`）长度 1–3 全组合 ~5200 串 ×
  decode/encode/parseUri 三方对照，永久回归护栏

**app 模块（6 文件）**

| 文件 | 原用法 | 现用法 |
|------|--------|--------|
| `parser/SocksConfigParser.kt` | `URI()` + URLDecoder/URLEncoder（ProxyLinkUtils，HttpConfigParser 共用） | `UrlCodec.parseUri/decode/encode` |
| `parser/TrojanConfigParser.kt` | `URI()` + 逐字段 URLDecoder + URLEncoder | 同上（解码链顺序不变） |
| `parser/ShadowSocksConfigParser.kt` | 内联 `java.net.URLDecoder/URLEncoder`（tag） | `UrlCodec.decode/encode` |
| `parser/Hysteria2ConfigParser.kt` | 整串 `URLDecoder.decode(url)` 再拆分 | `UrlCodec.decode(url)`（顺序不变） |
| `parser/VLESSConfigParser.kt` | 同上 | 同上 |
| `viewmodel/XrayViewmodel.kt` | URLEncoder 构造 GitHub issue URL | `UrlCodec.encode` |

fuzz 曾抓到 2 处初版实现的偏差（`socks://#f` 误抛、 `%+a` 误拒），均已修复并锁定。

**未动（属后续阶段）**
- `di/NetworkModule.kt`（OkHttp 代理 Authenticator/ProxySelector）→ Ktor 迁移阶段
- `ui/component/SubscriptionScreen.kt`（URI/URL）、`utils/Device.kt`（InetAddress）→ 平台抽象阶段
- `common/.../SocksConfigGenerator.kt` 的 `java.security.SecureRandom` → 见下

---

## 下一步（Step 4）待办清单

阶段 A 收尾：消除 common 模块与剩余共享逻辑里的 JVM-only API：

1. `common/.../utils/SocksConfigGenerator.kt` —— `java.security.SecureRandom`
   （KMP 无内置 CSPRNG，可 `expect/actual`：Android 侧仍 SecureRandom，
   iOS 侧 SecRandomCopyBytes；或先抽接口注入）
2. `common/.../utils/FileUtils.kt` —— `java.io.File/FileInputStream` +
   `java.security.MessageDigest`（MD5/SHA 需 expect/actual 或 okio 的 HashingSink）
3. grep 复查 `java.util.UUID` / `java.time` / `java.text` 在共享候选代码里的残留
4. common 的 `di/qualifier/*.java` 是 Dagger 注解（Java 源），不属本阶段

**注意点**：
- SecureRandom 的替换不能弱化为 `kotlin.random.Random`（安全语义变化），
  必须 expect/actual 桥接平台 CSPRNG
- MessageDigest 注意算法名大小写与输出大小写（hex 格式）逐一对齐

之后即可进入阶段 B（抽接口：XrayCore/GeoIpProvider/Settings），
parser 包届时已具备整体迁入 `domain/src/commonMain` 的条件
（Gson 模型类是下一阶段 C 的主要障碍）。
