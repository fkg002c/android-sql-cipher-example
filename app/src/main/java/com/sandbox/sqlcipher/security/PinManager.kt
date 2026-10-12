package com.sandbox.sqlcipher.security

import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранит соль, число итераций и PBKDF2-HMAC-SHA256(ПИН, соль) в EncryptedSharedPreferences.
 *
 * Производный ключ выполняет две роли:
 *  1. эталон для проверки ПИНа при входе в UI;
 *  2. пароль SQLCipher для Room-БД.
 *
 * Ключ доступен без ввода ПИНа (он защищён ключом из Android Keystore), поэтому
 * [com.sandbox.sqlcipher.receiver.PhoneStateReceiver] может писать события в БД,
 * пока UI заблокирован.
 *
 * Установки, где ПИН задан старой схемой SHA-256(соль ‖ ПИН), переводятся на PBKDF2
 * при первом успешном входе: см. [preparePending] / [commitPending] и
 * [com.sandbox.sqlcipher.data.db.DatabaseHolder.migrateKeyIfNeeded].
 *
 * Все методы обращаются к Keystore — вызывать не на main-потоке.
 */
@Singleton
class PinManager @Inject constructor(
    @SecurePrefs private val prefsProvider: dagger.Lazy<SharedPreferences>,
) {
    private val prefs: SharedPreferences get() = prefsProvider.get()

    private data class Record(val salt: ByteArray, val hash: ByteArray, val iterations: Int)

    fun isPinSet(): Boolean = read(CURRENT) != null || readLegacy() != null

    /** ПИН задан по старой схеме SHA-256 — при входе нужно перешифровать БД. */
    fun needsMigration(): Boolean = read(CURRENT) == null && readLegacy() != null

    /** Устанавливает ПИН. Допускается только один раз: смена ПИНа потребовала бы rekey БД. */
    fun setPin(pin: CharArray) {
        check(!isPinSet()) { "PIN already set" }
        val record = newRecord(pin)
        prefs.edit(commit = true) { write(CURRENT, record) }
    }

    fun verifyPin(pin: CharArray): Boolean {
        read(CURRENT)?.let { return it.hash.contentEqualsConstantTime(derive(pin, it.salt, it.iterations)) }
        readLegacy()?.let { return it.hash.contentEqualsConstantTime(legacySha256(it.salt, pin)) }
        return false
    }

    /** Пароль для SQLCipher. Пока миграция не завершена — старый SHA-256. */
    fun databaseKey(): ByteArray =
        checkNotNull(read(CURRENT) ?: readLegacy()) { "PIN is not set" }.hash

    // --- Миграция SHA-256 → PBKDF2 ---------------------------------------------------------

    /**
     * Шаг 1: вычисляет новый ключ и сохраняет его как «ожидающий» ДО перешифрования БД.
     * Если процесс умрёт посередине, по ожидающему ключу можно понять, чем зашифрована БД.
     */
    fun preparePending(pin: CharArray): ByteArray {
        val record = newRecord(pin)
        prefs.edit(commit = true) { write(PENDING, record) }
        return record.hash
    }

    fun pendingKey(): ByteArray? = read(PENDING)?.hash

    /** Шаг 2: БД уже перешифрована — ожидающий ключ становится основным, старый удаляется. */
    fun commitPending() {
        val record = checkNotNull(read(PENDING)) { "No pending key" }
        prefs.edit(commit = true) {
            write(CURRENT, record)
            remove(PENDING.salt); remove(PENDING.hash); remove(PENDING.iterations)
            remove(LEGACY_SALT); remove(LEGACY_HASH)
        }
    }

    /** БД не была перешифрована — забываем ожидающий ключ. */
    fun discardPending() {
        prefs.edit(commit = true) {
            remove(PENDING.salt); remove(PENDING.hash); remove(PENDING.iterations)
        }
    }

    // --- Хранение -------------------------------------------------------------------------

    private class Keys(val salt: String, val hash: String, val iterations: String)

    private fun read(keys: Keys): Record? {
        val salt = prefs.getString(keys.salt, null) ?: return null
        val hash = prefs.getString(keys.hash, null) ?: return null
        val iterations = prefs.getInt(keys.iterations, 0).takeIf { it > 0 } ?: return null
        return Record(salt.fromBase64(), hash.fromBase64(), iterations)
    }

    private fun SharedPreferences.Editor.write(keys: Keys, record: Record) {
        putString(keys.salt, record.salt.toBase64())
        putString(keys.hash, record.hash.toBase64())
        putInt(keys.iterations, record.iterations)
    }

    private fun readLegacy(): Record? {
        val salt = prefs.getString(LEGACY_SALT, null) ?: return null
        val hash = prefs.getString(LEGACY_HASH, null) ?: return null
        return Record(salt.fromBase64(), hash.fromBase64(), iterations = 1)
    }

    // --- Криптография ---------------------------------------------------------------------

    private fun newRecord(pin: CharArray): Record {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        return Record(salt, derive(pin, salt, ITERATIONS), ITERATIONS)
    }

    private fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray =
        Pbkdf2.derive(pin, salt, iterations, KEY_SIZE)

    private fun legacySha256(salt: ByteArray, pin: CharArray): ByteArray {
        val pinBytes = String(pin).toByteArray(Charsets.UTF_8)
        return try {
            MessageDigest.getInstance("SHA-256").run {
                update(salt)
                update(pinBytes)
                digest()
            }
        } finally {
            pinBytes.fill(0)
        }
    }

    // Сравнение за постоянное время
    private fun ByteArray.contentEqualsConstantTime(other: ByteArray) = MessageDigest.isEqual(this, other)

    private fun ByteArray.toBase64() = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    internal companion object {
        /** Рекомендация OWASP (2023) для PBKDF2-HMAC-SHA256. */
        const val ITERATIONS = 600_000
        const val SALT_SIZE = 16
        const val KEY_SIZE = 32

        private val CURRENT = Keys("pin_salt_v2", "pin_pbkdf2", "pin_iterations")
        private val PENDING = Keys("pending_salt", "pending_pbkdf2", "pending_iterations")

        // Схема v1: SHA-256(соль ‖ ПИН)
        private const val LEGACY_SALT = "pin_salt"
        private const val LEGACY_HASH = "pin_sha256"
    }
}
