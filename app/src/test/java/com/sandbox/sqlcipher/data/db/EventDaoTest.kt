package com.sandbox.sqlcipher.data.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EventDaoTest : DaoTestBase() {

    private val dao get() = db.eventDao()

    @Test
    fun `insert сохраняет все поля`() = runTest {
        val id = dao.insert(
            EventEntity(timestamp = 1_000L, phone = "+79161234567", event = "RINGING", name = "Анна")
        )

        assertEquals(
            listOf(EventEntity(id, 1_000L, "+79161234567", "RINGING", "Анна")),
            dao.observeAll().first(),
        )
    }

    @Test
    fun `name может быть null, phone - пустой строкой`() = runTest {
        dao.insert(EventEntity(timestamp = 1L, phone = "", event = "IDLE", name = null))

        val event = dao.observeAll().first().single()
        assertEquals("", event.phone)
        assertNull(event.name)
    }

    @Test
    fun `observeAll отдаёт новые события первыми`() = runTest {
        dao.insert(EventEntity(timestamp = 2_000L, phone = "1", event = "OFFHOOK", name = null))
        dao.insert(EventEntity(timestamp = 3_000L, phone = "1", event = "IDLE", name = null))
        dao.insert(EventEntity(timestamp = 1_000L, phone = "1", event = "RINGING", name = null))

        val events = dao.observeAll().first()

        assertEquals(listOf(3_000L, 2_000L, 1_000L), events.map { it.timestamp })
        assertEquals(listOf("IDLE", "OFFHOOK", "RINGING"), events.map { it.event })
    }

    @Test
    fun `события с одинаковым timestamp не перезаписывают друг друга`() = runTest {
        val first = dao.insert(EventEntity(timestamp = 5L, phone = "1", event = "RINGING", name = null))
        val second = dao.insert(EventEntity(timestamp = 5L, phone = "1", event = "RINGING", name = null))

        assertNotEquals(first, second)
        assertEquals(2, dao.observeAll().first().size)
    }

    @Test
    fun `имя в событии не зависит от последующих изменений users`() = runTest {
        val userId = db.userDao().insert(UserEntity(name = "Анна", phone = "111111"))
        dao.insert(EventEntity(timestamp = 1L, phone = "111111", event = "RINGING", name = "Анна"))

        db.userDao().update(UserEntity(id = userId, name = "Анна Петровна", phone = "111111"))
        db.userDao().delete(UserEntity(id = userId, name = "", phone = ""))

        assertEquals("Анна", dao.observeAll().first().single().name)
    }
}
