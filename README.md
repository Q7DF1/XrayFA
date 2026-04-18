<div align="center">

# 🚀 XrayFA

**A modern, powerful, and user-friendly Android client for [Xray-core](https://github.com/XTLS/Xray-core).**

XrayFA provides a secure, high-speed proxy experience with a focus on simplicity and performance.

<p align="center">
  <b>English</b> | <a href="docs/README.zh-CN.md">简体中文</a>
</p>

[![GitHub release](https://img.shields.io/github/v/release/Q7DF1/XrayFA?style=flat-square&color=blue)](https://github.com/Q7DF1/XrayFA/releases)
[![GitHub license](https://img.shields.io/github/license/Q7DF1/XrayFA?style=flat-square)](https://github.com/Q7DF1/XrayFA/blob/main/LICENSE)
[![GitHub top language](https://img.shields.io/github/languages/top/Q7DF1/XrayFA?style=flat-square)](https://github.com/Q7DF1/XrayFA)
[![GitHub stars](https://img.shields.io/github/stars/Q7DF1/XrayFA?style=flat-square)](https://github.com/Q7DF1/XrayFA/stargazers)

</div>

---

## 📸 Screenshots

<div align="center">
    <h3>Phone UI</h3>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="30%" />
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="30%" />
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="30%" />
    <br><br>
    <h3>Tablet / Foldable UI</h3>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="85%" />
</div>

---

## ✨ Features

### 📡 Protocol Support
| VLESS | VMESS | Shadowsocks | Trojan | Hysteria2 |
| :---: | :---: | :---: | :---: | :---: |
| ✅ | ✅ | ✅ | ✅ | ✅ |

### 🛠️ Core Capabilities
*   **Subscription Management**: Easily import, manage, and batch-update subscription links.
*   **Intuitive Dashboard**: Clean real-time monitoring of connection status, speed, and traffic.
*   **Rich Configuration**: Advanced routing rules and DNS settings for power users.
*   **Smooth UX**: Modern Material Design 3 interface with fluid animations and Dark Mode support.
*   **Stable Engine**: Built on the latest **Xray-core** for maximum compatibility and security.

---

## 📥 Download

Ready to get started? 
👉 **[Download the latest APK from Releases](https://github.com/Q7DF1/XrayFA/releases)**

---

## 🔨 Build from Source

### Prerequisites
* **Android Studio**: Latest stable version.
* **JDK**: 11 or higher.
* **Go (Golang)**: 1.21+ (Required for Xray-core compilation).
* **Git**: For cloning submodules.

### Build Steps

1.  **Clone the repository** (with submodules):
    `ash
    git clone --recursive https://github.com/Q7DF1/XrayFA.git
    cd XrayFA
    `
    *If you missed submodules:* `git submodule update --init --recursive`

2.  **Open in Android Studio**:
    Select the `XrayFA` folder and wait for Gradle sync.

3.  **Build and Run**:
    Connect your device and press **Shift + F10**.

> [!CAUTION]
> 🚨 **IMPORTANT**: For accurate performance testing, ensure the build configuration is set to **RELEASE**. [Learn more about Compose performance](https://medium.com/androiddevelopers/why-should-you-always-test-compose-performance-in-release-4168dd0f2c71).

---

## 📖 Quick Start

1.  **Import Configuration**:
    *   Click the **+** button to import from Clipboard (`vless://`, `vmess://`, etc.).
    *   Or scan a **QR Code**.
2.  **Manage Subscriptions**:
    *   Navigate to **Subscription Settings** to add provider URLs.
3.  **Connect**:
    *   Select a node and tap the **Floating Action Button**.
    *   Accept the VPN permission request.

---

## 🔗 Credits & Acknowledgements

Special thanks to these projects that make XrayFA possible:
*   [Xray-core](https://github.com/XTLS/Xray-core) - The core network engine.
*   [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)
*   [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)

## 📄 License

Distributed under the **Apache-2.0 License**. See [LICENSE](LICENSE) for details.

---
<div align="center">

### 🌟 Star History

[![Star History Chart](https://api.star-history.com/svg?repos=q7df1/xrayFA&type=Date)](https://star-history.com/q7df1/xrayFA)

</div>
