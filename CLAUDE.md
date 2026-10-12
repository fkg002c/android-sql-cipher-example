# CLAUDE.md

Песочница для Android: Compose + Hilt + Room + SQLCipher + EncryptedSharedPreferences. Обзор см. в `README.md`, подробности — в `docs/` (architecture, security, call-events, ui). Здесь записано только то, чего нет в коде и документации.

## Сборка и окружение

- Windows, Gradle wrapper 9.6.0, AGP 9.4.1. Системного `JAVA_HOME` нет, поэтому JDK берётся из Android Studio:
  `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew :app:assembleDebug`
- adb: `/c/Users/ruink/AppData/Local/Android/Sdk/platform-tools/adb.exe`. К машине подключён физический телефон пользователя, а не эмулятор. Установка и запуск допустимы, но выдавать разрешения, вводить ПИН и звонить без спроса нельзя.
- Версии библиотек хранятся только в `gradle/libs.versions.toml`.

## Решения, которые нельзя «исправлять»

- **Ключ БД доступен без ПИНа.** Это SHA-256 из EncryptedSharedPreferences, и ресивер пишет события при заблокированном UI. Это следствие ТЗ, а не баг. Ограничения описаны в `docs/security.md`.
- **SHA-256 вместо PBKDF2.** Так требует ТЗ. Замена хэша меняет ключ БД, поэтому на неё нужно согласие пользователя и миграция через `PRAGMA rekey`.
- **Действие `android.intent.action.PHONE_STATE`.** В ТЗ указано `PHONE_STATE_MODIFY`, но такого действия нет.
- **Колонки `id` в `users` и `events`.** В ТЗ их нет, но Room требует первичный ключ.
- **`events.name` — снимок имени**, а не внешний ключ.
- **Без navigation-compose.** Экраны переключаются через `AppUiState.destination`, и после разблокировки всегда открывается `USERS` (требование ТЗ).
- **ПИН задаётся один раз.** Смена ПИНа не реализована.

## Подводные камни

- `PhoneStateReceiver`: **не** добавлять `super.onReceive()`. Вызов вставляет Hilt Gradle plugin, а в исходнике метод абстрактный, и сборка упадёт.
- AGP 9 содержит Kotlin, поэтому плагин `org.jetbrains.kotlin.android` подключать не нужно.
- Room/Hilt работают через KSP. Схема экспортируется в `app/schemas/`, её надо коммитить. При изменении entity нужно поднять `version` и добавить миграцию: деструктивная миграция удалит данные пользователя.
- Не обращаться к `DatabaseHolder.database` до установки ПИНа, так как `databaseKey()` бросит исключение. В фоновом коде сначала вызывать `isReady()`.
- `System.loadLibrary("sqlcipher")` выполняется в `SandboxApp.onCreate()`. Нельзя открывать БД раньше этого вызова.
- При наличии `READ_CALL_LOG` broadcast `PHONE_STATE` приходит дважды. Дедупликация идёт по `hasExtra(EXTRA_INCOMING_NUMBER)`.
- Bash-heredoc с кириллицей и апострофами в этом окружении ломался. Файлы лучше писать инструментом Write.

## Соглашения

- Строки UI на русском, только через `res/values/strings.xml`.
- Комментарии и KDoc в коде на русском.
- Документация в `docs/` на русском. При изменении поведения нужно обновлять соответствующий файл.

## Статус проверки

- `assembleDebug` проходит, приложение стартует на устройстве без падений, `libsqlcipher.so` загружается.
- **Не проверено вручную:** онбординг с выдачей разрешений, установка и ввод ПИНа, CRUD пользователей, запись событий при реальном звонке, блокировка при уходе в фон.
- Тесты существуют только шаблонные (`ExampleUnitTest`, `ExampleInstrumentedTest`). Кандидаты на юнит-тесты: `PhoneMatcher`, `PinManager` (через Robolectric), DAO (in-memory Room).
