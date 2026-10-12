package com.sandbox.sqlcipher.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Pbkdf2Test {

    @Test
    fun `fallback совпадает с тестовым вектором RFC 7914`() {
        // RFC 7914, раздел 11: PBKDF2-HMAC-SHA256, P = "passwd", S = "salt", c = 1, dkLen = 64
        val expected = hex(
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc" +
                "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783"
        )
        val actual = Pbkdf2.fallback("passwd".toCharArray(), "salt".toByteArray(), 1, 64)
        assertArrayEquals(expected, actual)
    }

    @Test
    fun `fallback совпадает с платформенной реализацией`() {
        val cases = listOf(
            Triple("1234", 1, 32),
            Triple("00000000", 2, 32),
            Triple("987654", 1_000, 32),
            Triple("4321", 10_000, 32),
            Triple("5555", 3, 20),  // длина меньше блока HMAC
            Triple("5555", 3, 70),  // несколько блоков
        )
        val salt = ByteArray(PinManager.SALT_SIZE) { it.toByte() }
        cases.forEach { (pin, iterations, length) ->
            assertArrayEquals(
                "pin=$pin, c=$iterations, dkLen=$length",
                Pbkdf2.platform(pin.toCharArray(), salt, iterations, length),
                Pbkdf2.fallback(pin.toCharArray(), salt, iterations, length),
            )
        }
    }

    @Test
    fun `fallback не изменяет переданный ПИН`() {
        val pin = "1234".toCharArray()
        Pbkdf2.fallback(pin, ByteArray(16), 1, 32)
        assertEquals("1234", String(pin))
    }

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
