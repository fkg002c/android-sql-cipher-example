package com.sandbox.sqlcipher.security

import android.os.Build
import java.nio.ByteBuffer
import java.nio.CharBuffer
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

/**
 * PBKDF2-HMAC-SHA256 (RFC 8018).
 *
 * `SecretKeyFactory("PBKDF2WithHmacSHA256")` есть на Android только с API 26, а minSdk = 24.
 * На API 26+ используется платформенная (нативная, быстрая) реализация, на 24–25 —
 * [fallback] поверх `HmacSHA256`. Результаты идентичны, это проверяет `Pbkdf2Test`.
 */
internal object Pbkdf2 {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val HMAC = "HmacSHA256"

    fun derive(password: CharArray, salt: ByteArray, iterations: Int, keyLengthBytes: Int): ByteArray =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            platform(password, salt, iterations, keyLengthBytes)
        } else {
            fallback(password, salt, iterations, keyLengthBytes)
        }

    fun platform(password: CharArray, salt: ByteArray, iterations: Int, keyLengthBytes: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyLengthBytes * 8)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun fallback(password: CharArray, salt: ByteArray, iterations: Int, keyLengthBytes: Int): ByteArray {
        require(iterations > 0 && keyLengthBytes > 0)
        val passwordBytes = password.toUtf8()
        try {
            val mac = Mac.getInstance(HMAC).apply { init(SecretKeySpec(passwordBytes, HMAC)) }
            val hLen = mac.macLength
            val out = ByteArray(keyLengthBytes)
            val u = ByteArray(hLen)
            val t = ByteArray(hLen)
            var block = 1
            var offset = 0
            while (offset < keyLengthBytes) {
                // U1 = HMAC(P, S || INT_32_BE(i))
                mac.update(salt)
                mac.update(ByteBuffer.allocate(4).putInt(block).array())
                mac.doFinal(u, 0)
                u.copyInto(t)
                // Ui = HMAC(P, Ui-1); T = U1 ^ U2 ^ ... ^ Uc
                repeat(iterations - 1) {
                    mac.update(u)
                    mac.doFinal(u, 0)
                    for (j in t.indices) t[j] = t[j] xor u[j]
                }
                val len = minOf(hLen, keyLengthBytes - offset)
                t.copyInto(out, offset, 0, len)
                offset += len
                block++
            }
            return out
        } finally {
            passwordBytes.fill(0)
        }
    }

    private fun CharArray.toUtf8(): ByteArray {
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(this))
        return ByteArray(buffer.remaining()).also {
            buffer.get(it)
            buffer.array().fill(0)
        }
    }
}
