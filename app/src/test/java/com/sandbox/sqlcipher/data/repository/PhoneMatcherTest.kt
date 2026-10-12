package com.sandbox.sqlcipher.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneMatcherTest {

    @Test
    fun `разные форматы одного номера дают одинаковый ключ`() {
        val expected = "9161234567"
        listOf(
            "+79161234567",
            "89161234567",
            "9161234567",
            "+7 (916) 123-45-67",
            "8 916 123 45 67",
        ).forEach { assertEquals(it, expected, PhoneMatcher.key(it)) }
    }

    @Test
    fun `длинный номер обрезается до последних 10 цифр`() {
        assertEquals("2025550123", PhoneMatcher.key("+1 202 555 0123"))
        assertEquals("4567890123", PhoneMatcher.key("00123 4567890123"))
    }

    @Test
    fun `номер короче 10 цифр возвращается целиком`() {
        assertEquals("12345", PhoneMatcher.key("12-345"))
        assertEquals("1234567", PhoneMatcher.key("123-45-67"))
    }

    @Test
    fun `номер короче 5 цифр не сравнивается`() {
        assertNull(PhoneMatcher.key("1234"))
        assertNull(PhoneMatcher.key("+7"))
    }

    @Test
    fun `пустая строка и строка без цифр дают null`() {
        assertNull(PhoneMatcher.key(""))
        assertNull(PhoneMatcher.key("   "))
        assertNull(PhoneMatcher.key("unknown"))
    }

    @Test
    fun `разные номера дают разные ключи`() {
        assertNotEquals(PhoneMatcher.key("+79161234567"), PhoneMatcher.key("+79161234568"))
    }

    @Test
    fun `известное ограничение - совпадение последних 10 цифр у разных стран`() {
        // Номера разных стран с одинаковым хвостом считаются одним (см. docs/call-events.md)
        assertEquals(PhoneMatcher.key("+7 916 123 45 67"), PhoneMatcher.key("+44 916 123 45 67"))
    }
}
