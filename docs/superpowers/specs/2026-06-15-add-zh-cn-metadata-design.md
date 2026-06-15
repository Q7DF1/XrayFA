# Spec: Add Simplified Chinese Metadata

Add Simplified Chinese (`zh-CN`) metadata for the Android application to support the Play Store and other distribution platforms using Fastlane.

## Context
The project currently has metadata for `en-US`, `ko-KR`, and `ru-RU`. Simplified Chinese translations exist in the app strings and README, but are missing from the Fastlane metadata directory.

## Requirements
- Create a new directory `fastlane/metadata/android/zh-CN/`.
- Add `short_description.txt` with a concise summary.
- Add `full_description.txt` with a detailed feature list and description.
- Ensure the tone matches the existing `README_zh-CN.md`.

## Proposed Content

### short_description.txt
```text
XrayFA 是一款快速、安全且易用的 Xray-core Android 客户端。
```

### full_description.txt
```text
XrayFA 是一款功能强大且易用的 Xray-core Android 客户端。它在 Android 设备上提供安全且快速的代理体验，支持多种协议，包括 VLESS、VMESS、Shadowsocks、Trojan、Hysteria2 和 Reality。

主要功能：
- 支持多种协议：VLESS、VMESS、Shadowsocks、Trojan、Hysteria2 和 Reality。
- 高性能：基于最新的 Xray-core 构建，低延迟、高速度。
- 易于使用：通过二维码或订阅链接导入配置。
- 自定义路由：根据规则灵活地绕过或代理流量。
- 现代化 UI：简洁清爽的界面，带来轻松的用户体验。

XrayFA 通过加密流量和绕过本地限制，确保您的互联网隐私和安全。立即在您的 Android 设备上通过 XrayFA 体验 Xray 的强大功能！
```

## Success Criteria
- Files exist in the correct location.
- Content is encoded in UTF-8.
- Content accurately reflects the app's features in Simplified Chinese.
