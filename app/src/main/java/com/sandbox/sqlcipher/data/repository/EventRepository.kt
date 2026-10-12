package com.sandbox.sqlcipher.data.repository

import com.sandbox.sqlcipher.data.db.DatabaseHolder
import com.sandbox.sqlcipher.data.db.EventEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val holder: DatabaseHolder,
    private val userRepository: UserRepository,
) {
    private val dao get() = holder.database.eventDao()

    fun observeEvents(): Flow<List<EventEntity>> = dao.observeAll()

    /** @return false, если БД ещё не создана (ПИН не установлен) и событие не записано. */
    suspend fun record(timestamp: Long, state: String, phone: String?): Boolean {
        if (!holder.isReady()) return false
        val number = phone.orEmpty()
        dao.insert(
            EventEntity(
                timestamp = timestamp,
                phone = number,
                event = state,
                name = userRepository.findNameByPhone(number),
            )
        )
        return true
    }
}
