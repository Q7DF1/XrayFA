# 🚀 [XrayFA](https://github.com/Q7DF1/XrayFA)

**XrayFA** is a powerful and user-friendly Android client for [Xray-core](https://github.com/XTLS/Xray-core). It provides a secure and fast proxy experience on Android devices, supporting multiple protocols including VLESS, VMESS, Shadowsocks, and Trojan.

**English|[简体中文](docs/README.zh-CN.md)**。

![GitHub release (latest by date)](https://img.shields.io/github/v/release/Q7DF1/XrayFA)
![GitHub license](https://img.shields.io/github/license/Q7DF1/XrayFA)
![GitHub top language](https://img.shields.io/github/languages/top/Q7DF1/XrayFA)

##  📸  Screenshots

<p aligin="center">
    <img src="docs/config_xrayFA.jpg" width="30%" />
    <img src="docs/home_xrayFA.jpg" width="30%" />
    <img src="docs/settings_xrayFA.jpg" width="30%" />
</p>



## ✨ Features

* **Multi-Protocol Support**: Full support for VLESS, VMESS, SHADOWSOCKS, and TROJAN.
* **Subscription Management**: Easily manage and update subscription links.
* **Intuitive Dashboard**: A simple, clean dashboard to view connection status and traffic.
* **Rich Configuration**: Advanced settings for power users to fine-tune the connection.
* **Smooth UI**: Modern Material Design with rich animations.
* **Core Powered**: Built on top of the robust Xray-core.

## 📥 Download

You can download the latest APK from the **[Releases Page](https://github.com/Q7DF1/XrayFA/releases)**.

## 🛠️ Build from Source

To build XrayFA from source, you need Android Studio and a basic understanding of Android development.

_**IMPORTANT: Ensure the build configuration is set to RELEASE; do NOT submit or deploy a DEBUG package. [moreinformation](https://medium.com/androiddevelopers/why-should-you-always-test-compose-performance-in-release-4168dd0f2c71)**_

### Prerequisites
* Android Studio (latest stable version recommended)
* **JDK 11 or higher**
* **Go (Golang) environment** (Version 1.21 or higher is recommended for Xray-core compilation)
* Git

### Steps

1.  **Clone the repository**
    Make sure to clone with submodules to include the necessary libraries (like `AndroidLibXrayLite` and `tun2socks`).
    ```bash
    git clone --recursive https://github.com/Q7DF1/XrayFA.git
    cd XrayFA
    ```

    *If you already cloned without submodules, run:*
    ```bash
    git submodule update --init --recursive
    ```

2.  **Open in Android Studio**
    * Open Android Studio.
    * Select "Open an existing project".
    * Navigate to the `XrayFA` folder and select it.

3.  **Sync Gradle**
    * Wait for Android Studio to download dependencies and sync the project.

4.  **Build and Run**
    * Connect your Android device or start an emulator.
    * Click the green **Run** button (Shift + F10).

## 📖 Usage

1.  **Import Config**:
    * Copy your `vless://`, `vmess://`, etc., link.
    * Open XrayFA and click the **+** (Add) button or Import from Clipboard.
    * Alternatively, scan a QR code.
2.  **Add Subscription**:
    * Go to the Subscription settings.
    * Paste your subscription URL and update.
3.  **Connect**:
    * Select your desired profile from the list.
    * Tap the **Connect** (Floating Action Button) to start the VPN service.
    * Grant the necessary VPN permissions when prompted.

## 🤝 Contributing

Contributions are welcome! If you find a bug or have a feature request, please open an issue.

1.  Fork the Project
2.  Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3.  Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4.  Push to the Branch (`git push origin feature/AmazingFeature`)
5.  Open a Pull Request

## 🔗 Credits & Acknowledgements

This project wouldn't be possible without the following open-source projects:

* **[Xray-core](https://github.com/XTLS/Xray-core)**: The core network engine.
* **[AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)**
* **[hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)**

## 📄 License

Distributed under the Apache-2.0 License. See `LICENSE` for more information.

## 🌟 Star History

[![Star History Chart](https://api.star-history.com/svg?repos=q7df1/xrayFA&type=Date)](https://star-history.com/q7df1/xrayFA&Date)
