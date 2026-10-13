# Архитектура

## Структура пакетов

Корневой пакет: `com.sandbox.sqlcipher` (`app/src/main/java/com/sandbox/sqlcipher/`).

```
SandboxApp.kt                     Application: @HiltAndroidApp, System.loadLibrary("sqlcipher")
security/
  PinManager.kt                   соль + PBKDF2 ПИНа в EncryptedSharedPreferences; ключ БД; шаги миграции; счётчик попыток
  DataWiper.kt                    уничтожение данных и разрешений после 7 неверных ПИНов
  Pbkdf2.kt                       PBKDF2-HMAC-SHA256: платформенный (API 26+) и fallback (API 24–25)
  SecurityModule.kt               Hilt-модуль: @SecurePrefs EncryptedSharedPreferences
data/
  db/
    Entities.kt                   UserEntity (users), EventEntity (events)
    Daos.kt                       UserDao, EventDao
    AppDatabase.kt                @Database, version = 1
    DatabaseHolder.kt             ленивое открытие Room + SQLCipher, миграция ключа; SqlCipherRekey
  repository/
    UserRepository.kt             CRUD пользователей, поиск имени по номеру, PhoneMatcher
    EventRepository.kt            запись и чтение событий
receiver/
  PhoneStateReceiver.kt           BroadcastReceiver для android.intent.action.PHONE_STATE
telephony/
  TelephonyPermissions.kt         список разрешений, OwnPhoneNumber (свой номер SIM)
ui/
  MainActivity.kt                 единственная Activity, корневой composable AppRoot
  AppViewModel.kt                 этап (онбординг / блокировка), текущий экран
  lock/LockScreens.kt             разрешения, установка ПИНа, ввод ПИНа
  main/MainScreen.kt              ModalNavigationDrawer + TopAppBar
  users/UsersScreen.kt, UsersViewModel.kt
  events/EventsScreen.kt          экран и EventsViewModel
  theme/                          тема Material 3 из шаблона
```

## Слои

```
 UI (Compose)  ──►  ViewModel (@HiltViewModel)  ──►  Repository (@Singleton)  ──►  DAO (Room)
                                                            ▲                         │
 PhoneStateReceiver (@AndroidEntryPoint) ───────────────────┘                  DatabaseHolder
                                                                                      │
                                                         PinManager ──► ключ ──► SupportOpenHelperFactory (SQLCipher)
```

- Почти все зависимости внедряет Hilt через `@Inject constructor`. Единственный модуль, `SecurityModule`, предоставляет `@SecurePrefs SharedPreferences`. `PinManager` получает их через `dagger.Lazy`, поэтому в тестах можно подставить обычные SharedPreferences.
- Репозитории и `DatabaseHolder` являются синглтонами, поэтому UI и ресивер работают с одним экземпляром БД внутри процесса.
- Данные отдаются через `Flow`. ViewModel превращают их в `StateFlow` (`stateIn(WhileSubscribed(5_000))`), где значение `null` означает «ещё загружается».

## Схема БД

Файл `sandbox.db`. JSON-схема экспортируется в `app/schemas/` (аргумент KSP `room.schemaLocation`).

### `users`

| Колонка | Тип | Примечание |
|---|---|---|
| `id` | INTEGER PK autoincrement | добавлена: Room требует первичный ключ, по нему же идут редактирование и удаление |
| `name` | TEXT NOT NULL | |
| `phone` | TEXT NOT NULL | индекс `index_users_phone` |

### `events`

| Колонка | Тип | Примечание |
|---|---|---|
| `id` | INTEGER PK autoincrement | добавлена по той же причине |
| `timestamp` | INTEGER NOT NULL | `System.currentTimeMillis()` в момент получения broadcast |
| `phone` | TEXT NOT NULL | пустая строка, если номер не передан |
| `event` | TEXT NOT NULL | `IDLE` / `OFFHOOK` / `RINGING` |
| `name` | TEXT NULL | имя из `users` на момент события (снимок, не внешний ключ) |

Имя в `events` хранится копией: если пользователя потом переименуют или удалят, старые события сохранят прежнее имя.

## Жизненный цикл БД

Ключ шифрования появляется только после установки ПИНа, поэтому БД нельзя создавать на старте процесса.

- `DatabaseHolder.database` создаёт Room-инстанс при первом обращении (double-checked locking). Перед открытием `recoverInterruptedMigration()` разбирает незавершённую миграцию ключа.
- `DatabaseHolder.migrateKeyIfNeeded(pin)` под той же блокировкой закрывает Room, перешифровывает файл (`SqlCipherRekey`) и сбрасывает инстанс. Следующее обращение откроет БД новым ключом. Подробности в [security.md](security.md#миграция-со-схемы-v1-sha-256).
- UI обращается к БД только на этапе `UNLOCKED`, а к нему можно попасть лишь после установки ПИНа.
- Ресивер сначала проверяет `DatabaseHolder.isReady()` (то есть `PinManager.isPinSet()`) и до установки ПИНа пропускает события.
- `SandboxApp.onCreate()` загружает нативную библиотеку `sqlcipher` раньше любого открытия БД.

## Зависимости

Версии задаются в `gradle/libs.versions.toml`.

| Библиотека | Версия | Назначение |
|---|---|---|
| `com.google.dagger:hilt-android` + плагин | 2.60.1 | DI |
| `com.google.devtools.ksp` | 2.3.12 | кодогенерация Hilt и Room |
| `androidx.hilt:hilt-lifecycle-viewmodel-compose` | 1.4.0 | `hiltViewModel()` |
| `androidx.room:room-runtime / room-ktx / room-compiler` | 2.8.4 | ORM |
| `androidx.sqlite:sqlite` | 2.6.2 | `SupportSQLite` API |
| `net.zetetic:sqlcipher-android` | 4.19.1 | шифрование SQLite |
| `androidx.security:security-crypto` | 1.1.0 | EncryptedSharedPreferences |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.10.0 | `collectAsStateWithLifecycle` |
| `androidx.compose.material:material-icons-core` | 1.7.8 | иконки |

AGP 9 содержит встроенную поддержку Kotlin, поэтому плагин `org.jetbrains.kotlin.android` не подключается.
