<div align="center">

<img src="../fastlane/metadata/android/en-US/images/icon.png" alt="XrayFA" width="120" />

# XrayFA

基于 [Xray-core](https://github.com/XTLS/Xray-core) 的开源代理客户端，采用 Kotlin Multiplatform 构建。

**Android 已提供下载；iOS 尚未正式发布。**

[English](../README.md) · 简体中文 · [Русский](README_RU.md) · [한국어](README_KR.md)

</div>

## 界面预览

<div align="center">

[![XrayFA Android 16 界面展示：节点管理、连接状态、设置与订阅](assets/android-overview.png)](assets/android-overview.png)

<sub>节点管理 · 连接状态 · 设置 · 订阅 · Android 16</sub>

</div>

## 平台支持

<div align="center">

| 平台 | 系统版本 | 状态 |
|:---:|:---:|:---:|
| Android | Android 9.0（API 28）及以上 | 已提供发布渠道 |
| iOS | - | - |

</div>

## 功能

- **节点与订阅管理**：导入节点链接，添加、管理和更新订阅。
- **多协议支持**：支持 VLESS、VMess、Shadowsocks、Trojan、Hysteria2、SOCKS 和 HTTP 代理链接。
- **多种导入方式**：剪贴板、二维码，以及完整 Xray JSON 配置。
- **连接状态与流量显示**：查看 VPN 连接状态、上传和下载速度。
- **路由与 DNS 设置**：配置代理路由、DNS 和相关网络选项。
- **节点分享**：通过链接或二维码分享普通节点。
- **界面与主题**：共享 Compose Multiplatform 界面，支持深色模式及大屏布局。
- **Android 平台能力**：分应用代理、日志查看等。

具体协议、传输方式和配置字段的支持情况，以当前解析器及仓库所使用的 Xray-core 为准。

## 下载与使用

### Android

- [GitHub Releases](https://github.com/Q7DF1/XrayFA/releases)
- [F-Droid](https://f-droid.org/packages/com.android.xrayfa/)

不确定设备架构时，可选择通用 APK。不同渠道的安装包可能使用不同签名，切换渠道时可能需要卸载重装，请先保存配置。

### 快速开始

1. 安装应用，导入节点链接或添加订阅。
2. 在节点列表中选择需要使用的节点。
3. 在首页启动连接，并允许系统 VPN 权限。
4. 连接后查看状态和流量；需要时调整路由及 DNS 设置。

使用完整 Xray JSON 时，节点链接分享、延迟测试及部分流量统计能力存在限制。

## 技术与构建体系

XrayFA 使用 Gradle Kotlin DSL 和版本目录管理依赖，通过 Kotlin Multiplatform 组织共享逻辑、Compose Multiplatform 构建界面，并接入 Android VPN 服务及原生能力。

<div align="center">

| 层次 | 技术 |
|:---:|:---:|
| 共享语言与逻辑 | Kotlin Multiplatform |
| 共享界面 | Compose Multiplatform、Material 3 |
| 导航与状态管理 | Decompose、Essenty |
| 依赖注入 | Koin |
| 数据存储 | Room Multiplatform、DataStore |
| 网络与序列化 | Ktor、kotlinx.serialization |
| 代理内核 | Xray-core、AndroidLibXrayLite |
| TUN 转发 | hev-socks5-tunnel |
| Android 宿主 | Android 应用、VpnService、JNI |

</div>

<details>
<summary>查看项目目录</summary>

```text
XrayFA/
├── androidApp/          Android 应用及平台接入
├── shared/              共享界面、导航和组件
├── common/              公共类型与基础工具
├── domain/              协议解析与配置模型
├── core/
│   ├── data/            数据仓库
│   ├── database/        Room 数据库
│   ├── datastore/       设置存储
│   ├── network/         网络请求
│   └── native-bridge/   Xray 原生桥接
├── platform/vpn/        平台 VPN 接口与实现
├── AndroidLibXrayLite/  Go 原生库子模块
├── tun2socks/           TUN 转发及 Android JNI
├── scripts/             原生库与构建准备脚本
└── gradle/              Wrapper 与依赖版本目录
```

</details>

## 从源码构建

<details>
<summary>展开 Android 构建步骤</summary>

### 获取源码

```bash
git clone --recursive https://github.com/Q7DF1/XrayFA.git
cd XrayFA
```

如果已经克隆仓库：

```bash
git submodule update --init --recursive
```

### Android

准备以下环境：

- JDK 17，与当前 Android CI 一致；源码的 Java／JVM 字节码目标为 11。
- Android SDK Platform 37、Platform Tools 及相应 Build Tools。
- Android NDK `28.2.13676358`。
- Go：当前子模块声明 `go 1.26`，构建配置记录 `GO_VERSION=1.27.1`。
- Git；使用仓库脚本时还需要 Bash 和 curl 或 wget。
- Android Studio 可用于同步、运行和调试项目。

配置 Android SDK 路径，并确保原生构建使用正确的 NDK。首次构建需下载 Gradle、依赖、Go 工具和规则数据。

先准备规则数据及 Xray 原生 AAR：

```bash
bash scripts/fdroid.sh prebuild
bash scripts/fdroid.sh bind
```

随后构建：

```bash
# 调试版本
./gradlew :androidApp:assembleDebug

# Release 版本
./gradlew :androidApp:assembleRelease
```

输出目录：

```text
androidApp/build/outputs/apk/debug/
androidApp/build/outputs/apk/release/
```

原生库准备步骤会生成 `androidApp/libs/libv2ray.aar`，普通 `preBuild` 不会自动编译该 AAR。

打包前，`preBuild` 会检查 Xray AAR 是否包含所需架构的 `libgojni.so`。普通构建要求四个 Android 架构完整；有意只构建单一架构时需使用 `-PfdroidAbi=<abi>`。生成通用 APK 前，应通过 `gomobile bind -target=android` 重建完整架构的 AAR。

需要签名时，提供 `androidApp/xrayfa.jks`，并通过环境变量或项目根目录的 `.release-signing.properties` 文件设置以下值。非空环境变量优先；本地配置文件已排除 Git 跟踪，无须每次构建前导入环境变量。

```text
KEYSTORE_PASSWORD=密钥库密码
KEY_ALIAS=密钥别名
KEY_PASSWORD=私钥密码
```

</details>

> [!CAUTION]
> 未提供密钥库时会生成未签名的 Release APK，需要签名后安装。评估性能时请使用 Release 构建；Debug 包包含调试开销及 LeakCanary。

## 测试与反馈

运行 Android／JVM 单元测试：

```bash
./gradlew :common:testDebugUnitTest :domain:testDebugUnitTest :core:datastore:testDebugUnitTest
```

部分模块测试需要先准备原生 AAR。

报告问题时，请附上应用版本、系统版本、设备架构、安装渠道及复现步骤。提交日志或截图前，请移除订阅地址、认证信息等敏感内容。

[提交问题](https://github.com/Q7DF1/XrayFA/issues)

## 贡献指南

欢迎反馈问题、修复缺陷、完善翻译和文档。开始前请阅读 [贡献指南（英文）](../CONTRIBUTING.md)。

## 鸣谢

- [Xray-core](https://github.com/XTLS/Xray-core)
- [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)
- Kotlin Multiplatform、Compose Multiplatform、Decompose、Koin 及其他开源依赖

## 许可证

XrayFA 使用 [Apache-2.0 许可证](../LICENSE)。第三方依赖遵循各自的许可证。
