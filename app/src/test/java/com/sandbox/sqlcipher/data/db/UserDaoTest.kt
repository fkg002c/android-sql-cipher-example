package com.sandbox.sqlcipher.data.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserDaoTest : DaoTestBase() {

    private val dao get() = db.userDao()

    @Test
    fun `insert генерирует уникальные id`() = runTest {
        val first = dao.insert(UserEntity(name = "Анна", phone = "111111"))
        val second = dao.insert(UserEntity(name = "Борис", phone = "222222"))

        assertTrue(first > 0)
        assertNotEquals(first, second)
        assertEquals(2, dao.getAll().size)
    }

    @Test
    fun `observeAll сортирует по имени без учёта регистра`() = runTest {
        dao.insert(UserEntity(name = "charlie", phone = "3"))
        dao.insert(UserEntity(name = "Alice", phone = "1"))
        dao.insert(UserEntity(name = "bob", phone = "2"))

        val names = dao.observeAll().first().map { it.name }

        assertEquals(listOf("Alice", "bob", "charlie"), names)
    }

    @Test
    fun `observeAll на пустой таблице отдаёт пустой список`() = runTest {
        assertEquals(emptyList<UserEntity>(), dao.observeAll().first())
    }

    @Test
    fun `update меняет только указанную запись`() = runTest {
        val id = dao.insert(UserEntity(name = "Анна", phone = "111111"))
        val otherId = dao.insert(UserEntity(name = "Борис", phone = "222222"))

        dao.update(UserEntity(id = id, name = "Анна Петровна", phone = "333333"))

        val byId = dao.getAll().associateBy { it.id }
        assertEquals(UserEntity(id, "Анна Петровна", "333333"), byId[id])
        assertEquals(UserEntity(otherId, "Борис", "222222"), byId[otherId])
    }

    @Test
    fun `delete удаляет запись по id`() = runTest {
        val id = dao.insert(UserEntity(name = "Анна", phone = "111111"))
        dao.insert(UserEntity(name = "Борис", phone = "222222"))

        dao.delete(UserEntity(id = id, name = "", phone = ""))

        assertEquals(listOf("Борис"), dao.getAll().map { it.name })
    }

    @Test
    fun `observeAll отражает изменения таблицы`() = runTest {
        val flow = dao.observeAll()
        assertEquals(0, flow.first().size)

        val id = dao.insert(UserEntity(name = "Анна", phone = "111111"))
        assertEquals(listOf("Анна"), flow.first().map { it.name })

        dao.delete(UserEntity(id = id, name = "Анна", phone = "111111"))
        assertEquals(0, flow.first().size)
    }

    @Test
    fun `findByPhone находит точное совпадение`() = runTest {
        dao.insert(UserEntity(name = "Анна", phone = "+79161234567"))
        dao.insert(UserEntity(name = "Борис", phone = "89169999999"))

        assertEquals("Анна", dao.findByPhone("+79161234567")?.name)
        assertEquals("Борис", dao.findByPhone("89169999999")?.name)
    }

    @Test
    fun `findByPhone не нормализует номер`() = runTest {
        // Нормализация — задача UserRepository/PhoneMatcher, DAO ищет строго по строке
        dao.insert(UserEntity(name = "Анна", phone = "+79161234567"))

        assertNull(dao.findByPhone("89161234567"))
        assertNull(dao.findByPhone(""))
    }
}
