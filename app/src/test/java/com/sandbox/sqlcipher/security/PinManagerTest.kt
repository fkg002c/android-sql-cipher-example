package com.sandbox.sqlcipher.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.MessageDigest

/**
 * Логика PinManager на обычных SharedPreferences: EncryptedSharedPreferences требуют
 * Android Keystore, которого под Robolectric нет.
 */
@RunWith(RobolectricTestRunner::class)
class PinManagerTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var manager: PinManager

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("test_secure_prefs", Context.MODE_PRIVATE)
        prefs.edit(commit = true) { clear() }
        manager = PinManager { prefs }
    }

    @Test
    fun `до установки ПИН не задан и проверка не проходит`() {
        assertFalse(manager.isPinSet())
        assertFalse(manager.needsMigration())
        assertFalse(manager.verifyPin("1234".toCharArray()))
    }

    @Test
    fun `установленный ПИН проверяется, неверный - нет`() {
        manager.setPin("1234".toCharArray())

        assertTrue(manager.isPinSet())
        assertFalse(manager.needsMigration())
        assertTrue(manager.verifyPin("1234".toCharArray()))
        assertFalse(manager.verifyPin("1235".toCharArray()))
        assertFalse(manager.verifyPin("12345".toCharArray()))
    }

    @Test
    fun `ключ БД - PBKDF2 с сохранёнными солью и числом итераций`() {
        manager.setPin("1234".toCharArray())

        val salt = Base64.decode(prefs.getString("pin_salt_v2", null), Base64.NO_WRAP)
        assertEquals(PinManager.SALT_SIZE, salt.size)
        assertEquals(PinManager.ITERATIONS, prefs.getInt("pin_iterations", 0))

        val expected = Pbkdf2.platform("1234".toCharArray(), salt, PinManager.ITERATIONS, PinManager.KEY_SIZE)
        assertArrayEquals(expected, manager.databaseKey())
    }

    @Test
    fun `ПИН и соль не хранятся в открытом виде`() {
        manager.setPin("1234".toCharArray())
        assertFalse(prefs.all.values.any { it.toString().contains("1234") })
        assertNull(prefs.getString("pin_sha256", null))
    }

    @Test(expected = IllegalStateException::class)
    fun `повторная установка ПИНа запрещена`() {
        manager.setPin("1234".toCharArray())
        manager.setPin("5678".toCharArray())
    }

    // --- Миграция со схемы SHA-256 ----------------------------------------------------------

    @Test
    fun `старая схема SHA-256 распознаётся и проверяется`() {
        val legacyKey = writeLegacy("1234")

        assertTrue(manager.isPinSet())
        assertTrue(manager.needsMigration())
        assertTrue(manager.verifyPin("1234".toCharArray()))
        assertFalse(manager.verifyPin("0000".toCharArray()))
        assertArrayEquals(legacyKey, manager.databaseKey())
    }

    @Test
    fun `до commitPending ключ БД остаётся старым`() {
        val legacyKey = writeLegacy("1234")

        val pending = manager.preparePending("1234".toCharArray())

        assertArrayEquals(pending, manager.pendingKey())
        assertArrayEquals(legacyKey, manager.databaseKey())
        assertTrue(manager.needsMigration())
    }

    @Test
    fun `commitPending переводит на PBKDF2 и удаляет старый хэш`() {
        writeLegacy("1234")
        val pending = manager.preparePending("1234".toCharArray())

        manager.commitPending()

        assertFalse(manager.needsMigration())
        assertNull(manager.pendingKey())
        assertArrayEquals(pending, manager.databaseKey())
        assertTrue(manager.verifyPin("1234".toCharArray()))
        assertNull(prefs.getString("pin_sha256", null))
        assertNull(prefs.getString("pin_salt", null))
    }

    @Test
    fun `discardPending возвращает к старой схеме`() {
        val legacyKey = writeLegacy("1234")
        manager.preparePending("1234".toCharArray())

        manager.discardPending()

        assertNull(manager.pendingKey())
        assertTrue(manager.needsMigration())
        assertArrayEquals(legacyKey, manager.databaseKey())
        assertTrue(manager.verifyPin("1234".toCharArray()))
    }

    /** Записывает ПИН так, как это делала версия с SHA-256(соль ‖ ПИН). */
    private fun writeLegacy(pin: String): ByteArray {
        val salt = ByteArray(16) { (it * 7).toByte() }
        val hash = MessageDigest.getInstance("SHA-256").run {
            update(salt)
            update(pin.toByteArray())
            digest()
        }
        prefs.edit(commit = true) {
            putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            putString("pin_sha256", Base64.encodeToString(hash, Base64.NO_WRAP))
        }
        return hash
    }
}
