package com.sandbox.sqlcipher.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandbox.sqlcipher.security.PinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class Stage {
    /** Читаем EncryptedSharedPreferences (Keystore) — это не мгновенно. */
    LOADING,

    /** Первый запуск: запрос разрешений телефонии. */
    PERMISSIONS,

    /** Первый запуск: установка ПИНа. */
    SETUP_PIN,

    /** ПИН установлен, UI заблокирован. */
    LOCKED,
    UNLOCKED,
}

enum class Destination { USERS, EVENTS }

data class AppUiState(
    val stage: Stage = Stage.LOADING,
    val destination: Destination = Destination.USERS,
    val pinError: Boolean = false,
)

/** Состояние уровня Activity: этап онбординга / блокировка / текущий экран. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val pinManager: PinManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val pinSet = withContext(Dispatchers.IO) { pinManager.isPinSet() }
            _state.update { it.copy(stage = if (pinSet) Stage.LOCKED else Stage.PERMISSIONS) }
        }
    }

    fun onPermissionsHandled() {
        _state.update { it.copy(stage = Stage.SETUP_PIN) }
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { pinManager.setPin(pin.toCharArray()) }
            unlocked()
        }
    }

    fun unlock(pin: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { pinManager.verifyPin(pin.toCharArray()) }
            if (ok) unlocked() else _state.update { it.copy(pinError = true) }
        }
    }

    fun clearPinError() = _state.update { it.copy(pinError = false) }

    /** Вызывается, когда UI уходит с экрана — следующий вход снова потребует ПИН. */
    fun lock() = _state.update {
        if (it.stage == Stage.UNLOCKED) it.copy(stage = Stage.LOCKED) else it
    }

    fun navigate(destination: Destination) = _state.update { it.copy(destination = destination) }

    // После входа всегда открываем «Пользователи»
    private fun unlocked() = _state.update {
        it.copy(stage = Stage.UNLOCKED, destination = Destination.USERS, pinError = false)
    }
}
