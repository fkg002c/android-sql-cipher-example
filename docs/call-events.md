# События звонков

## Broadcast

В ТЗ указан `PHONE_STATE_MODIFY`, но такого действия в Android нет. Используется системный `TelephonyManager.ACTION_PHONE_STATE_CHANGED` (`android.intent.action.PHONE_STATE`).

```xml
<receiver android:name=".receiver.PhoneStateReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.PHONE_STATE" />
    </intent-filter>
</receiver>
```

Система рассылает этот broadcast с флагом `FLAG_RECEIVER_INCLUDE_BACKGROUND`, поэтому ресивер из манифеста получает его и при закрытом приложении.

Экстры:

| Экстра | Константа | Значение |
|---|---|---|
| `state` | `TelephonyManager.EXTRA_STATE` | `IDLE`, `OFFHOOK`, `RINGING` |
| `incoming_number` | `TelephonyManager.EXTRA_INCOMING_NUMBER` (deprecated с API 29) | номер собеседника или пусто |

## Разрешения

| Разрешение | Для чего |
|---|---|
| `READ_PHONE_STATE` | получать сам broadcast |
| `READ_CALL_LOG` | получать `incoming_number` (с Android 9 без него номер не приходит) |
| `READ_PHONE_NUMBERS` (API 26+) | читать свой номер SIM для шапки бокового меню |

Список задан в `TelephonyPermissions.required`. Разрешения запрашиваются на первом шаге онбординга. Если пользователь откажет, приложение продолжит работать, но события будут без номеров или вообще не будут приходить.

## Дедупликация

Приложению с `READ_PHONE_STATE` и `READ_CALL_LOG` система отправляет каждое событие **дважды** (см. `TelephonyRegistry.broadcastCallStateChanged` в AOSP):

1. копию без экстры `incoming_number` (для владельцев `READ_PHONE_STATE`);
2. копию с экстрой `incoming_number` (для владельцев `READ_PHONE_STATE` + `READ_CALL_LOG`).

Если `READ_CALL_LOG` выдан, ресивер обрабатывает только intent, где `hasExtra(EXTRA_INCOMING_NUMBER)` истинно. Если не выдан, придёт только первая копия, и она будет записана с пустым номером.

## Обработка в ресивере

Код: `PhoneStateReceiver.onReceive()`.

1. Проверяет action и выполняет дедупликацию.
2. Фиксирует `timestamp = System.currentTimeMillis()` и читает `state` и `incoming_number`.
3. Вызывает `goAsync()` и запускает корутину на `Dispatchers.IO` (статический `SupervisorJob`-scope).
4. Вызывает `EventRepository.record()`, затем `pending.finish()` в блоке `finally`.

Hilt внедряет `EventRepository` в `onReceive` сгенерированного родителя `Hilt_PhoneStateReceiver`. Вызов `super.onReceive` в байткод вставляет Hilt Gradle plugin, вручную его писать не нужно.

`EventRepository.record()` возвращает `false` и ничего не пишет, если ПИН ещё не установлен (БД не существует).

## Поиск имени по номеру

Код: `UserRepository.findNameByPhone()`.

1. Пустой номер: имя `null`.
2. Точное совпадение через `SELECT ... WHERE phone = :phone` (по индексу).
3. Иначе нормализация через `PhoneMatcher.key()`: оставить только цифры и взять последние 10. Номера короче 5 цифр не сравниваются. Затем перебор всех пользователей с тем же ключом.

Поэтому `+7 916 123-45-67`, `89161234567` и `9161234567` считаются одним номером. Ограничение: номера разных стран с одинаковыми последними 10 цифрами тоже совпадут. Для точного сравнения можно взять `PhoneNumberUtils.areSamePhoneNumber()` (API 31+) или libphonenumber.

## Как проверить

- Реальный звонок на устройство.
- Эмулятор: Extended controls → Phone → Call device.
- adb на эмуляторе: `adb emu gsm call 89161234567`, затем `adb emu gsm accept 89161234567` и `adb emu gsm cancel 89161234567`.

Ожидаемая последовательность записей для входящего звонка: `RINGING`, `OFFHOOK`, `IDLE`. Для пропущенного: `RINGING`, `IDLE`.
