package com.sandbox.sqlcipher.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранит соль и SHA-256(соль + ПИН) в EncryptedSharedPreferences.
 *
 * Хэш выполняет две роли:
 *  1. эталон для проверки ПИНа при входе в UI;
 *  2. пароль SQLCipher для Room-БД.
 *
 * Хэш доступен без ввода ПИНа (он защищён ключом из Android Keystore), поэтому
 * [com.sandbox.sqlcipher.receiver.PhoneStateReceiver] может писать события в БД,
 * пока UI заблокирован.
 */
@Suppress("DEPRECATION") // security-crypto 1.1.0 помечен deprecated, но остаётся рабочим
@Singleton
class PinManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun isPinSet(): Boolean = prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT)

    /** Устанавливает ПИН. Допускается только один раз: смена ПИНа потребовала бы rekey БД. */
    fun setPin(pin: CharArray) {
        check(!isPinSet()) { "PIN already set" }
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val hash = hash(salt, pin)
        prefs.edit(commit = true) {
            putString(KEY_SALT, salt.toBase64())
            putString(KEY_HASH, hash.toBase64())
        }
    }

    fun verifyPin(pin: CharArray): Boolean {
        val salt = prefs.getString(KEY_SALT, null)?.fromBase64() ?: return false
        val stored = prefs.getString(KEY_HASH, null)?.fromBase64() ?: return false
        // Сравнение за постоянное время
        return MessageDigest.isEqual(stored, hash(salt, pin))
    }

    /** Пароль для SQLCipher. Каждый вызов возвращает новую копию. */
    fun databaseKey(): ByteArray {
        val hash = checkNotNull(prefs.getString(KEY_HASH, null)) { "PIN is not set" }
        return hash.fromBase64()
    }

    private fun hash(salt: ByteArray, pin: CharArray): ByteArray {
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

    private fun ByteArray.toBase64() = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val PREFS_NAME = "secure_prefs"
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_sha256"
        const val SALT_SIZE = 16
    }
}
