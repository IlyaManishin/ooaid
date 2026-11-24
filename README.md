# TrackFit

Офлайн-приложение Android для планирования тренировок. Программа состоит из тренировочных дней и дней отдыха. Цикл привязан к календарю, а тренировки могут начинаться вручную или по расписанию.

## Сборка

Нужны JDK 17, Android SDK Platform 37.2 и Build Tools 36.0.0. Минимальная версия Android — 8.0 (API 26). Установка SDK и настройка проекта описаны в [SETUP](docs/SETUP.md).

```bash
./gradlew :app:assembleDebug
```

APK сохраняется в `app/build/outputs/apk/debug/app-debug.apk`. Для устройства с USB-отладкой можно выполнить `./gradlew :app:installDebug`. Флаг `-Ptrackfit.seedExamples=true` добавляет три демонстрационные программы при первом запуске debug-сборки с пустой базой.

## Первый запуск

1. Откройте «Мои программы» и добавьте программу с тренировочным днём и хотя бы одним упражнением.
2. Сохраните программу и выберите стартовый день.
3. Откройте тренировку с «Главной», отметьте выполненные упражнения и завершите её. Итоги появятся на «Главной».
4. Выберите тренера на соответствующей вкладке. В «Профиле» доступны фото, личные рекорды и переключатель уведомлений.

## Проверка и релиз

```bash
./gradlew checkArchitecture :domain:test :domain:jacocoTestCoverageVerification \
  :infra:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:assembleRelease
```

Релизная сборка использует R8 для оптимизации. Для подписи укажите через окружение `TRACKFIT_KEYSTORE_PATH`, `TRACKFIT_STORE_PASSWORD`, `TRACKFIT_KEY_ALIAS` и `TRACKFIT_KEY_PASSWORD`. При отсутствии этих значений Gradle собирает неподписанный APK в `app/build/outputs/apk/release/`.

Текущие этапы и проверки описаны в [журнале прогресса](docs/PROGRESS.md). Открытые продуктовые решения отмечены статусом «ПОДТВЕРДИТЬ» в [журнале решений](docs/DECISIONS.md).
