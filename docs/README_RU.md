<div align="center">

<img src="../fastlane/metadata/android/en-US/images/icon.png" alt="XrayFA" width="120" />

# XrayFA

Прокси-клиент с открытым исходным кодом на основе [Xray-core](https://github.com/XTLS/Xray-core) и Kotlin Multiplatform.

**Версия для Android доступна для загрузки; iOS пока официально не выпущена.**

[English](../README.md) · [简体中文](README_zh-CN.md) · Русский · [한국어](README_KR.md)

</div>

## Скриншоты

<div align="center">

[![Интерфейс XrayFA на Android 16: узлы, подключение, настройки и подписки](assets/android-overview.png)](assets/android-overview.png)

<sub>Config · Home · Settings · Subscription · Android 16</sub>

</div>

## Поддержка платформ

<div align="center">

| Платформа | Версия системы | Доступность |
|:---:|:---:|:---:|
| Android | Android 9.0 (API 28) и новее | Доступна для загрузки |
| iOS | - | - |

</div>

## Возможности

- **Узлы и подписки**: импорт ссылок на узлы, добавление, управление и обновление подписок.
- **Протоколы**: ссылки VLESS, VMess, Shadowsocks, Trojan, Hysteria2, SOCKS и HTTP.
- **Способы импорта**: буфер обмена, QR-коды и полные конфигурации Xray JSON.
- **Состояние и трафик**: состояние VPN, скорость загрузки и отправки данных.
- **Маршрутизация и DNS**: правила маршрутизации, DNS и другие параметры сети.
- **Обмен узлами**: ссылки и QR-коды для обычных узлов.
- **Интерфейс и темы**: Compose Multiplatform, тёмная тема и адаптация к большим экранам.
- **Функции Android**: настройки прокси для отдельных приложений, просмотр журналов и другие возможности платформы.

Поддержка протоколов, транспортов и полей конфигурации определяется текущими парсерами и используемой в репозитории версией Xray-core.

## Загрузка и использование

### Android

- [GitHub Releases](https://github.com/Q7DF1/XrayFA/releases)
- [F-Droid](https://f-droid.org/packages/com.android.xrayfa/)

Если архитектура устройства неизвестна, выберите универсальный APK. Пакеты из разных источников могут иметь разные подписи; для смены источника может потребоваться переустановка. Сначала сохраните конфигурации.

### Быстрый старт

1. Установите приложение, импортируйте ссылки на узлы или добавьте подписку.
2. Выберите узел в списке конфигураций.
3. Запустите подключение на странице Home и подтвердите системный запрос разрешения VPN.
4. Проверьте состояние и трафик; при необходимости настройте маршрутизацию и DNS.

Для полных конфигураций Xray JSON действуют ограничения на обмен ссылками, проверку задержки и часть статистики трафика.

## Архитектура и система сборки

Проект использует Gradle Kotlin DSL и каталог версий для зависимостей, Kotlin Multiplatform для общей логики, Compose Multiplatform для интерфейса и интеграцию с VPN и нативными библиотеками Android.

<div align="center">

| Уровень | Технологии |
|:---:|:---:|
| Общий язык и логика | Kotlin Multiplatform |
| Общий интерфейс | Compose Multiplatform, Material 3 |
| Навигация и состояние | Decompose, Essenty |
| Внедрение зависимостей | Koin |
| Хранение данных | Room Multiplatform, DataStore |
| Сеть и сериализация | Ktor, kotlinx.serialization |
| Прокси-ядро | Xray-core, AndroidLibXrayLite |
| Передача через TUN | hev-socks5-tunnel |
| Приложение Android | Android, VpnService, JNI |

</div>

<details>
<summary>Посмотреть структуру проекта</summary>

```text
XrayFA/
├── androidApp/          Приложение Android и интеграция с платформой
├── shared/              Общий интерфейс, навигация и компоненты
├── common/              Общие типы и утилиты
├── domain/              Парсеры протоколов и модели конфигураций
├── core/
│   ├── data/            Репозитории данных
│   ├── database/        База данных Room
│   ├── datastore/       Хранение настроек
│   ├── network/         Сетевые запросы
│   └── native-bridge/   Нативный мост Xray
├── platform/vpn/        Интерфейсы и реализации VPN
├── AndroidLibXrayLite/  Подмодуль нативной библиотеки Go
├── tun2socks/           Передача через TUN и Android JNI
├── scripts/             Подготовка библиотек и сборки
└── gradle/              Wrapper и каталог версий зависимостей
```

</details>

## Сборка из исходного кода

<details>
<summary>Развернуть инструкции по сборке Android</summary>

### Получение исходников

```bash
git clone --recursive https://github.com/Q7DF1/XrayFA.git
cd XrayFA
```

Для уже клонированного репозитория:

```bash
git submodule update --init --recursive
```

### Android

Требования:

- JDK 17, как в Android CI; целевая версия байткода Java/JVM — 11.
- Android SDK Platform 37, Platform Tools и соответствующие Build Tools.
- Android NDK `28.2.13676358`.
- Go: подмодуль указывает `go 1.26`, конфигурация сборки — `GO_VERSION=1.27.1`.
- Git; для скриптов также нужны Bash и curl или wget.
- Android Studio для синхронизации проекта, запуска и отладки.

Укажите путь к Android SDK и используйте требуемую NDK. При первой сборке загружаются Gradle, зависимости, инструменты Go и данные маршрутизации.

Подготовьте данные маршрутизации и нативную библиотеку Xray AAR:

```bash
bash scripts/fdroid.sh prebuild
bash scripts/fdroid.sh bind
```

Соберите приложение:

```bash
# Debug
./gradlew :androidApp:assembleDebug

# Release
./gradlew :androidApp:assembleRelease
```

Каталоги результатов:

```text
androidApp/build/outputs/apk/debug/
androidApp/build/outputs/apk/release/
```

Подготовка нативной библиотеки создаёт `androidApp/libs/libv2ray.aar`; обычная задача `preBuild` не компилирует этот AAR автоматически.

Release включает оптимизацию кода и удаление неиспользуемых ресурсов. Для подписи нужны `androidApp/xrayfa.jks` и переменные окружения:

```text
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

> [!CAUTION]
> Без хранилища ключей Release APK остаётся неподписанным; перед установкой его необходимо подписать. Проверяйте производительность в Release: Debug содержит отладочные накладные расходы и LeakCanary.

</details>

## Тестирование и обратная связь

Тесты Android/JVM:

```bash
./gradlew :common:testDebugUnitTest :domain:testDebugUnitTest :core:datastore:testDebugUnitTest
```

Некоторым модулям перед тестированием нужен нативный AAR.

При сообщении о проблеме укажите версию приложения и системы, архитектуру устройства, источник установки и шаги воспроизведения. Удалите адреса подписок и данные авторизации из журналов и скриншотов.

[Сообщить о проблеме](https://github.com/Q7DF1/XrayFA/issues)

## Участие в проекте

Приветствуются сообщения об ошибках, исправления, переводы и улучшения документации. Перед началом прочитайте [руководство для участников (English)](../CONTRIBUTING.md).

## Благодарности

- [Xray-core](https://github.com/XTLS/Xray-core)
- [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)
- Kotlin Multiplatform, Compose Multiplatform, Decompose, Koin и другие зависимости с открытым исходным кодом

## Лицензия

XrayFA распространяется по [лицензии Apache-2.0](../LICENSE). Зависимости сохраняют собственные лицензии.
