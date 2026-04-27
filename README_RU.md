<div align="center">

# 🚀 XrayFA

**Современный, мощный и удобный Android‑клиент для [Xray‑core](https://github.com/XTLS/Xray-core).**

XrayFA обеспечивает безопасное и быстрое прокси‑подключение с акцентом на простоту и производительность.

<p align="center">
   <a href="README.md">English</a> | <a href="README_zh-CN.md">简体中文</a> | <b>Русский</b> | <a href="README_KR.md">한글</a>
</p>

[![GitHub release](https://img.shields.io/github/v/release/Q7DF1/XrayFA?style=flat-square&color=blue)](https://github.com/Q7DF1/XrayFA/releases)
[![GitHub license](https://img.shields.io/github/license/Q7DF1/XrayFA?style=flat-square)](https://github.com/Q7DF1/XrayFA/blob/main/LICENSE)
[![GitHub top language](https://img.shields.io/github/languages/top/Q7DF1/XrayFA?style=flat-square)](https://github.com/Q7DF1/XrayFA)
[![GitHub stars](https://img.shields.io/github/stars/Q7DF1/XrayFA?style=flat-square)](https://github.com/Q7DF1/XrayFA/stargazers)

</div>

---

## 📸 Скриншоты

<div align="center">
    <h3>Интерфейс для телефона</h3>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="30%" />
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="30%" />
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="30%" />
    <br><br>
    <h3>Интерфейс для планшета / складных устройств</h3>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="85%" />
</div>

---

## ✨ Возможности

### 📡 Поддержка протоколов
| VLESS | VMESS | Shadowsocks | Trojan | Hysteria2 |
| :---: | :---: | :---: | :---: | :---: |
| ✅ | ✅ | ✅ | ✅ | ✅ |

### 🛠️ Основные функции
*   **Управление подписками**: простой импорт, управление и пакетное обновление ссылок подписок.  
*   **Интуитивная панель управления**: чистый мониторинг статуса подключения, скорости и трафика в реальном времени.  
*   **Расширенные настройки**: продвинутые правила маршрутизации и настройки DNS для продвинутых пользователей.  
*   **Удобный UX**: современный интерфейс на базе Material Design 3 с плавной анимацией и поддержкой тёмной темы.  
*   **Стабильный «движок»**: построен на актуальной версии **Xray‑core** для максимальной совместимости и безопасности.  

---

## 📥 Скачать

Готовы начать работу?  

<div style="display: flex; gap: 10px; align-items: center;">
    <a href="https://github.com/Q7DF1/XrayFA/releases">
        <img src="https://raw.githubusercontent.com/rubenpgrady/get-it-on-github/refs/heads/main/get-it-on-github.png" alt="Get it on GitHub" height="60">
    </a>
    <a href="https://f-droid.org/en/packages/com.android.xrayfa/">
        <img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="60">
    </a>
</div>

---

## 🔨 Сборка из исходников

### Предварительные требования
* **Android Studio**: последняя стабильная версия.  
* **JDK**: 11 и выше.  
* **Go (Golang)**: 1.21+ (требуется для сборки Xray‑core).  
* **Git**: для клонирования подмодулей.  

### Шаги сборки

1.  **Клонируйте репозиторий** (вместе с подмодулями):
    `
    git clone --recursive https://github.com/Q7DF1/XrayFA.git
    cd XrayFA
    `
    *Если пропустили подмодули:* `git submodule update --init --recursive`

2.  **Откройте в Android Studio**:
    Выберите папку `XrayFA` и дождитесь завершения синхронизации Gradle.

3.  **Соберите и запустите**:
    Подключите устройство и нажмите **Shift + F10**.

> [!CAUTION]
> 🚨 **ВАЖНО**: для корректного тестирования производительности установите конфигурацию сборки в режим **RELEASE**. [Подробнее о производительности Compose](https://medium.com/androiddevelopers/why-should-you-always-test-compose-performance-in-release-4168dd0f2c71).

---

## 📖 Быстрый старт

1.  **Импорт конфигурации**:
    *   Нажмите кнопку **+**, чтобы импортировать из буфера обмена (`vless://`, `vmess://`, и т.д.).  
    *   Или отсканируйте **QR‑код**.  

2.  **Управление подписками**:
    *   Перейдите в **Настройки подписки**, чтобы добавить ссылки провайдеров.  

3.  **Подключение**:
    *   Выберите узел и нажмите **кнопку-действие (FAB)**.  
    *   Подтвердите запрос разрешения VPN.  

---

## 🔗 Благодарности и указания

Особая благодарность проектам, без которых XrayFA был бы невозможен:
*   [Xray‑core](https://github.com/XTLS/Xray-core) — базовый сетевой «движок».  
*   [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)  
*   [hev‑socks5‑tunnel](https://github.com/heiher/hev‑socks5‑tunnel)  

## 📄 Лицензия

Распространяется под лицензией **Apache‑2.0**. Подробности в файле [LICENSE](LICENSE).

---
<div align="center">

### 🌟 История звёзд

[![Star History Chart](https://api.star-history.com/svg?repos=q7df1/xrayFA&type=Date)](https://star-history.com/q7df1/xrayFA)

</div>
