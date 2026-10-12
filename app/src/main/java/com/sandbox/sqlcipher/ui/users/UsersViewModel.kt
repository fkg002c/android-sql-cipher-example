package com.sandbox.sqlcipher.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandbox.sqlcipher.data.db.UserEntity
import com.sandbox.sqlcipher.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UsersViewModel @Inject constructor(
    private val repository: UserRepository,
) : ViewModel() {

    /** null — данные ещё загружаются. */
    val users: StateFlow<List<UserEntity>?> = repository.observeUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Сохранение формы: id == 0 — новая запись, иначе — редактирование. */
    fun save(id: Long, name: String, phone: String) {
        viewModelScope.launch {
            if (id == 0L) repository.add(name, phone)
            else repository.update(UserEntity(id = id, name = name, phone = phone))
        }
    }

    fun delete(user: UserEntity) {
        viewModelScope.launch { repository.delete(user) }
    }
}
