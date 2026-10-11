<div align="center">

<img src="../fastlane/metadata/android/en-US/images/icon.png" alt="XrayFA" width="120" />

# XrayFA

[Xray-core](https://github.com/XTLS/Xray-core) 기반의 오픈 소스 프록시 클라이언트입니다. Kotlin Multiplatform으로 개발되었습니다.

**Android 버전은 다운로드할 수 있으며, iOS 버전은 아직 정식 출시되지 않았습니다.**

[English](../README.md) · [简体中文](README_zh-CN.md) · [Русский](README_RU.md) · 한국어

</div>

## 스크린샷

<div align="center">

[![XrayFA Android 16: 노드 관리, 연결 상태, 설정 및 구독 화면](assets/android-overview.png)](assets/android-overview.png)

<sub>Config · Home · Settings · Subscription · Android 16</sub>

</div>

## 플랫폼 지원

<div align="center">

| 플랫폼 | 시스템 버전 | 배포 상태 |
|:---:|:---:|:---:|
| Android | Android 9.0(API 28) 이상 | 다운로드 가능 |
| iOS | - | - |

</div>

## 기능

- **노드 및 구독 관리**: 노드 링크 가져오기, 구독 추가·관리·업데이트.
- **다양한 프로토콜**: VLESS, VMess, Shadowsocks, Trojan, Hysteria2, SOCKS, HTTP 프록시 링크.
- **가져오기 방식**: 클립보드, QR 코드, 전체 Xray JSON 구성.
- **연결 상태 및 트래픽**: VPN 상태와 업로드·다운로드 속도 표시.
- **라우팅 및 DNS**: 라우팅, DNS와 관련 네트워크 옵션 설정.
- **노드 공유**: 일반 노드를 링크나 QR 코드로 공유.
- **UI 및 테마**: Compose Multiplatform 공유 UI, 다크 모드, 큰 화면 레이아웃.
- **Android 기능**: 앱별 프록시 설정, 로그 보기 등.

프로토콜, 전송 방식 및 구성 필드의 지원 범위는 현재 파서와 저장소에서 사용하는 Xray-core 버전에 따라 달라집니다.

## 다운로드 및 사용

### Android

- [GitHub Releases](https://github.com/Q7DF1/XrayFA/releases)
- [F-Droid](https://f-droid.org/packages/com.android.xrayfa/)

기기 아키텍처를 모르는 경우 범용 APK를 선택하세요. 배포 채널별로 서명이 다를 수 있으므로 채널 변경 시 재설치가 필요할 수 있습니다. 먼저 구성을 저장하세요.

### 빠른 시작

1. 앱을 설치하고 노드 링크를 가져오거나 구독을 추가합니다.
2. 구성 목록에서 사용할 노드를 선택합니다.
3. Home에서 연결을 시작하고 시스템 VPN 권한 요청을 허용합니다.
4. 연결 상태와 트래픽을 확인하고 필요한 경우 라우팅 및 DNS를 조정합니다.

전체 Xray JSON 구성을 사용하면 노드 링크 공유, 지연 시간 테스트 및 일부 트래픽 통계에 제한이 있습니다.

## 기술 및 빌드 시스템

Gradle Kotlin DSL과 버전 카탈로그로 의존성을 관리합니다. Kotlin Multiplatform으로 공통 로직을 구성하고 Compose Multiplatform으로 UI를 구현하며 Android VPN 서비스와 네이티브 기능을 연결합니다.

<div align="center">

| 계층 | 기술 |
|:---:|:---:|
| 공유 언어 및 로직 | Kotlin Multiplatform |
| 공유 UI | Compose Multiplatform, Material 3 |
| 탐색 및 상태 관리 | Decompose, Essenty |
| 의존성 주입 | Koin |
| 데이터 저장 | Room Multiplatform, DataStore |
| 네트워크 및 직렬화 | Ktor, kotlinx.serialization |
| 프록시 코어 | Xray-core, AndroidLibXrayLite |
| TUN 전달 | hev-socks5-tunnel |
| Android 호스트 | Android 앱, VpnService, JNI |

</div>

<details>
<summary>프로젝트 구조 보기</summary>

```text
XrayFA/
├── androidApp/          Android 앱 및 플랫폼 통합
├── shared/              공유 UI, 탐색 및 컴포넌트
├── common/              공통 타입 및 유틸리티
├── domain/              프로토콜 파싱 및 구성 모델
├── core/
│   ├── data/            데이터 저장소
│   ├── database/        Room 데이터베이스
│   ├── datastore/       설정 저장
│   ├── network/         네트워크 요청
│   └── native-bridge/   Xray 네이티브 브리지
├── platform/vpn/        플랫폼 VPN 인터페이스 및 구현
├── AndroidLibXrayLite/  Go 네이티브 라이브러리 서브모듈
├── tun2socks/           TUN 전달 및 Android JNI
├── scripts/             네이티브 라이브러리 및 빌드 준비
└── gradle/              Wrapper 및 의존성 버전 카탈로그
```

</details>

## 소스 코드에서 빌드

<details>
<summary>Android 빌드 방법 펼치기</summary>

### 소스 가져오기

```bash
git clone --recursive https://github.com/Q7DF1/XrayFA.git
cd XrayFA
```

이미 복제한 저장소라면 다음을 실행합니다.

```bash
git submodule update --init --recursive
```

### Android

필요한 환경:

- Android CI와 동일한 JDK 17. Java/JVM 바이트코드 대상은 버전 11입니다.
- Android SDK Platform 37, Platform Tools 및 해당 Build Tools.
- Android NDK `28.2.13676358`.
- Go: 네이티브 서브모듈은 `go 1.26`, 빌드 설정은 `GO_VERSION=1.27.1`을 기록합니다.
- Git. 저장소 스크립트 실행에는 Bash와 curl 또는 wget도 필요합니다.
- Android Studio로 프로젝트 동기화, 실행 및 디버깅을 할 수 있습니다.

Android SDK 경로와 올바른 NDK를 설정하세요. 첫 빌드에서는 Gradle, 의존성, Go 도구 및 라우팅 데이터를 다운로드합니다.

라우팅 데이터와 Xray 네이티브 AAR을 준비합니다.

```bash
bash scripts/fdroid.sh prebuild
bash scripts/fdroid.sh bind
```

앱을 빌드합니다.

```bash
# Debug
./gradlew :androidApp:assembleDebug

# Release
./gradlew :androidApp:assembleRelease
```

결과 디렉터리:

```text
androidApp/build/outputs/apk/debug/
androidApp/build/outputs/apk/release/
```

네이티브 준비 과정에서 `androidApp/libs/libv2ray.aar`이 생성됩니다. 일반 `preBuild` 작업은 이 AAR을 자동으로 컴파일하지 않습니다.

Release 빌드는 코드 및 리소스 축소를 사용합니다. 서명하려면 `androidApp/xrayfa.jks`와 다음 환경 변수가 필요합니다.

```text
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

> [!CAUTION]
> 키 저장소가 없으면 서명되지 않은 Release APK가 생성되며, 설치 전에 서명해야 합니다. 성능은 Release 빌드로 평가하세요. Debug에는 디버깅 오버헤드와 LeakCanary가 포함됩니다.

</details>

## 테스트 및 피드백

Android/JVM 단위 테스트:

```bash
./gradlew :common:testDebugUnitTest :domain:testDebugUnitTest :core:datastore:testDebugUnitTest
```

일부 모듈은 테스트 전에 네이티브 AAR을 준비해야 합니다.

문제 보고 시 앱 버전, 시스템 버전, 기기 아키텍처, 설치 채널 및 재현 단계를 포함하세요. 로그와 스크린샷에서 구독 주소와 인증 정보를 제거하세요.

[문제 보고](https://github.com/Q7DF1/XrayFA/issues)

## 기여하기

버그 보고, 수정, 번역 및 문서 개선을 환영합니다. 시작하기 전에 [기여 가이드 (English)](../CONTRIBUTING.md)를 읽어 주세요.

## 감사의 말

- [Xray-core](https://github.com/XTLS/Xray-core)
- [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)
- Kotlin Multiplatform, Compose Multiplatform, Decompose, Koin 및 기타 오픈 소스 의존성

## 라이선스

XrayFA는 [Apache-2.0 라이선스](../LICENSE)를 사용합니다. 타사 의존성에는 각각의 라이선스가 적용됩니다.
