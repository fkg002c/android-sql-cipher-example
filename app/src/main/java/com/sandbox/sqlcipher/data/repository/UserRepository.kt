package com.sandbox.sqlcipher.data.repository

import com.sandbox.sqlcipher.data.db.DatabaseHolder
import com.sandbox.sqlcipher.data.db.UserEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val holder: DatabaseHolder,
) {
    private val dao get() = holder.database.userDao()

    fun observeUsers(): Flow<List<UserEntity>> = dao.observeAll()

    suspend fun add(name: String, phone: String) {
        dao.insert(UserEntity(name = name.trim(), phone = phone.trim()))
    }

    suspend fun update(user: UserEntity) = dao.update(user.copy(name = user.name.trim(), phone = user.phone.trim()))

    suspend fun delete(user: UserEntity) = dao.delete(user)

    /**
     * Ищет пользователя по номеру. Сначала точное совпадение, затем сравнение
     * по последним значащим цифрам — система может прислать «+79161234567»,
     * а в таблице сохранено «8 (916) 123-45-67».
     */
    suspend fun findNameByPhone(phone: String): String? {
        if (phone.isBlank()) return null
        dao.findByPhone(phone)?.let { return it.name }
        val key = PhoneMatcher.key(phone) ?: return null
        return dao.getAll().firstOrNull { PhoneMatcher.key(it.phone) == key }?.name
    }
}

internal object PhoneMatcher {
    /** Количество хвостовых цифр, по которым сравниваются номера (национальная часть). */
    private const val SIGNIFICANT_DIGITS = 10
    private const val MIN_DIGITS = 5

    fun key(phone: String): String? {
        val digits = phone.filter(Char::isDigit)
        if (digits.length < MIN_DIGITS) return null
        return digits.takeLast(SIGNIFICANT_DIGITS)
    }
}
