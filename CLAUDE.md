# CLAUDE.md

Песочница для Android: Compose + Hilt + Room + SQLCipher + EncryptedSharedPreferences. Обзор см. в `README.md`, подробности — в `docs/` (architecture, security, call-events, ui). Здесь записано только то, чего нет в коде и документации.

## Сборка и окружение

- Windows, Gradle wrapper 9.6.0, AGP 9.4.1. Системного `JAVA_HOME` нет, поэтому JDK берётся из Android Studio:
  `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew :app:assembleDebug`
- adb: `/c/Users/ruink/AppData/Local/Android/Sdk/platform-tools/adb.exe`. К машине подключён физический телефон пользователя, а не эмулятор. Установка и запуск допустимы, но выдавать разрешения, вводить ПИН и звонить без спроса нельзя.
- Версии библиотек хранятся только в `gradle/libs.versions.toml`.

## Решения, которые нельзя «исправлять»

- **Ключ БД доступен без ПИНа.** Это производный ключ PBKDF2 из EncryptedSharedPreferences, и ресивер пишет события при заблокированном UI. Это следствие ТЗ, а не баг. Ограничения описаны в `docs/security.md`.
- **PBKDF2-HMAC-SHA256, 600 000 итераций.** Изначально ТЗ требовало SHA-256, пользователь заменил его на PBKDF2. Ветку миграции со схемы v1 (`pin_salt`/`pin_sha256`) не удалять без согласия пользователя: её поведение на реальной БД проверено (см. «Статус проверки»).
- **Своя реализация PBKDF2 (`Pbkdf2.fallback`)** нужна только для API 24–25, где нет `PBKDF2WithHmacSHA256`. Её корректность закреплена в `Pbkdf2Test`.
- **Любая смена ключа БД** идёт через `preparePending` → `SqlCipherRekey.rekey` → `commitPending` (см. `DatabaseHolder.migrateKeyIfNeeded`). Иначе после смерти процесса посередине данные будут потеряны.
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
- Методы `PinManager` обращаются к Keystore, а PBKDF2 считается долго. Вызывать их только вне main-потока.
- Открывать SQLCipher-БД в обход Room только с no-op `DatabaseErrorHandler` (как в `SqlCipherRekey`). `DefaultDatabaseErrorHandler` при неверном ключе удаляет файл БД.
- `connectedAndroidTest` по умолчанию удаляет приложение вместе с данными. На телефоне пользователя запускать только с его согласия.
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
- Юнит-тесты (`app/src/test`, запуск `./gradlew :app:testDebugUnitTest`): `PhoneMatcherTest` (чистый JVM), `UserDaoTest` и `EventDaoTest` (Robolectric + in-memory Room **без** SQLCipher, т.к. нативная библиотека под JVM недоступна). Robolectric не поддерживает API 37, поэтому `src/test/resources/robolectric.properties` фиксирует `sdk=35`.
- Юнит-тесты безопасности: `Pbkdf2Test` (вектор RFC 7914 и сравнение с JDK), `PinManagerTest` (Robolectric, обычные SharedPreferences вместо EncryptedSharedPreferences; включает все шаги миграции).
- `SqlCipherRekeyTest` (`androidTest`) компилируется, но **ещё не запускался**: для этого нужно согласие пользователя (см. «Подводные камни»).
- **Миграция SHA-256 → PBKDF2 проверена на реальной БД** (телефон пользователя, 2026-10-13): данные сохранились, в logcat есть `Database key migrated to PBKDF2`. Установок со старой схемой больше не осталось, но ветку миграции оставляем, пока пользователь не решит иначе.
- Ручная проверка после миграции: вход по ПИНу и работа с данными на устройстве подтверждены пользователем.
- Ещё не покрыты тестами: `UserRepository.findNameByPhone`, `EventRepository.record`, ресивер, `DatabaseHolder.migrateKeyIfNeeded` целиком.
