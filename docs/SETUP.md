# SETUP: окружение для сборки TrackFit

У владельца Android SDK **нет**. Здесь — установка без обязательного Android Studio (достаточно командной строки). Android Studio по желанию ставится отдельно и удобен для эмулятора/отладки.

Агент: сначала проверь, что уже есть (`java -version`, `echo $ANDROID_HOME`/`%ANDROID_HOME%`, `sdkmanager --version`). Устанавливай только недостающее. Если в твоей среде нет сети/прав — не имитируй сборку: запиши блокер в `docs/QUESTIONS.md` и попроси владельца выполнить команды ниже самому.

Версии ниже — ориентиры. Актуальные номера cmdline-tools, platform и build-tools **бери из официальных источников на момент установки** (`sdkmanager --list`, страница https://developer.android.com/studio#command-line-tools-only).

---

## 1. JDK

Нужен JDK, который требует выбранная версия AGP (сейчас — 17 или новее; свериться с таблицей совместимости AGP в документации Android). Рекомендуется Temurin 17 или 21.

**Windows (PowerShell):**
```powershell
winget install EclipseAdoptium.Temurin.17.JDK
# перезапусти терминал, затем:
java -version
```

**macOS:** `brew install --cask temurin@17`
**Ubuntu/Debian:** `sudo apt update && sudo apt install -y openjdk-17-jdk unzip curl`

Проверь, что `JAVA_HOME` указывает на JDK (если Gradle не находит Java).

## 2. Android command-line tools

1. Скачай архив `commandlinetools-<os>-<номер>_latest.zip` со страницы Android Studio → *Command line tools only*.
2. Создай каталог SDK и распакуй так, чтобы получилось `<SDK>/cmdline-tools/latest/bin/sdkmanager`.

**Windows (PowerShell):**
```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
New-Item -ItemType Directory -Force "$sdk\cmdline-tools" | Out-Null
# скачанный zip распакуй в $sdk\cmdline-tools, затем переименуй папку cmdline-tools\cmdline-tools в latest
[Environment]::SetEnvironmentVariable("ANDROID_HOME", $sdk, "User")
[Environment]::SetEnvironmentVariable("Path", "$env:Path;$sdk\cmdline-tools\latest\bin;$sdk\platform-tools", "User")
# перезапусти терминал
```

**Linux/macOS:**
```bash
export ANDROID_HOME="$HOME/Android/Sdk"          # macOS: $HOME/Library/Android/sdk
mkdir -p "$ANDROID_HOME/cmdline-tools"
unzip commandlinetools-*_latest.zip -d "$ANDROID_HOME/cmdline-tools"
mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
echo 'export ANDROID_HOME="$HOME/Android/Sdk"' >> ~/.bashrc
echo 'export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"' >> ~/.bashrc
```

## 3. Пакеты SDK и лицензии

```bash
sdkmanager --licenses            # принять все (y)
sdkmanager --list | head -n 60   # посмотреть актуальные версии
sdkmanager "platform-tools" "platforms;android-37.2" "build-tools;36.0.0"
```
Версии для M0 зафиксированы в `gradle/libs.versions.toml`. В `compileSdk` используется полный уровень 37.2, а в `targetSdk` — основной уровень 37.

## 4. Привязка проекта к SDK

В корне проекта создай `local.properties` (**не коммитить**, в `.gitignore`):
```
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk      # Windows (экранирование обязательно)
# sdk.dir=/home/<user>/Android/Sdk                           # Linux
```
Либо достаточно переменной окружения `ANDROID_HOME`.

## 5. Gradle

Используется **Gradle wrapper** из репозитория (`gradlew`/`gradlew.bat`); отдельная установка Gradle не нужна. Если wrapper ещё не создан (M0), сгенерируй через временно установленный Gradle (`gradle wrapper --gradle-version <версия, совместимая с AGP>`) и закоммить `gradle/wrapper/*`, `gradlew`, `gradlew.bat`.

Проверка:
```bash
./gradlew --version
./gradlew :app:assembleDebug
```
(Windows: `gradlew.bat`.)

## 6. Эмулятор / устройство (для ручной проверки)

**Физическое устройство:** включи «Параметры разработчика» → «Отладка по USB», подключи, `adb devices`, `./gradlew :app:installDebug`.

**Эмулятор (опционально, нужна аппаратная виртуализация):**
```bash
sdkmanager "emulator" "system-images;android-<API>;google_apis;x86_64"
avdmanager create avd -n trackfit -k "system-images;android-<API>;google_apis;x86_64" -d pixel_6
emulator -avd trackfit
```
Windows: в BIOS должна быть включена виртуализация; включи компонент «Платформа гипервизора Windows» (WHPX). Если эмулятор недоступен — это не блокер: домен и логика проверяются JVM-тестами; в PR укажи «ручная проверка не выполнена».

## 7. Типовые проблемы

| Симптом | Решение |
|---|---|
| `SDK location not found` | Создай `local.properties` или задай `ANDROID_HOME`. |
| `Failed to install the following SDK components… licenses` | `sdkmanager --licenses`. |
| `Unsupported class file major version` / Java-ошибка | Версия JDK не соответствует AGP/Gradle — поставь JDK из таблицы совместимости. |
| Windows: `sdkmanager` не найден | Путь `cmdline-tools\latest\bin` не в `Path`; перезапусти терминал. |
| Нет сети в песочнице агента | Блокер → `docs/QUESTIONS.md`, команды выполняет владелец. |

## 8. Что должно получиться (критерий готовности окружения)

`java -version` OK · `sdkmanager --list_installed` показывает `platform-tools`, нужные `platforms` и `build-tools` · `./gradlew :app:assembleDebug` собирает APK (после M0) · `.gitignore` содержит `local.properties`, `.gradle/`, `build/`, `*.apk`, `*.keystore`.
