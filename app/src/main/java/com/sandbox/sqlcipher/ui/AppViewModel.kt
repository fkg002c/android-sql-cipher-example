package com.sandbox.sqlcipher.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandbox.sqlcipher.data.db.DatabaseHolder
import com.sandbox.sqlcipher.security.DataWiper
import com.sandbox.sqlcipher.security.PinManager
import com.sandbox.sqlcipher.security.PinManager.UnlockResult
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
    /** Идёт вычисление PBKDF2 (и, возможно, перешифрование БД) — это занимает заметное время. */
    val busy: Boolean = false,
)

/** Состояние уровня Activity: этап онбординга / блокировка / текущий экран. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val pinManager: PinManager,
    private val databaseHolder: DatabaseHolder,
    private val dataWiper: DataWiper,
) : ViewModel() {

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val stage = withContext(Dispatchers.IO) {
                when {
                    // Прошлая очистка не завершилась (процесс убит) — повторяем, остаёмся на LOADING
                    pinManager.isLockedOut() -> Stage.LOADING.also { dataWiper.wipe() }
                    pinManager.isPinSet() -> Stage.LOCKED
                    else -> Stage.PERMISSIONS
                }
            }
            _state.update { it.copy(stage = stage) }
        }
    }

    fun onPermissionsHandled() {
        _state.update { it.copy(stage = Stage.SETUP_PIN) }
    }

    fun setPin(pin: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { pinManager.setPin(pin.toCharArray()) }
            unlocked()
        }
    }

    fun unlock(pin: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val chars = pin.toCharArray()
                try {
                    pinManager.attemptUnlock(chars).also { result ->
                        when (result) {
                            UnlockResult.OK -> migrateKey(chars)
                            UnlockResult.LOCKED_OUT -> dataWiper.wipe()
                            UnlockResult.WRONG -> Unit
                        }
                    }
                } finally {
                    chars.fill('\u0000')
                }
            }
            when (result) {
                UnlockResult.OK -> unlocked()
                // Ни предупреждения, ни числа оставшихся попыток не показываем
                UnlockResult.WRONG -> _state.update { it.copy(pinError = true, busy = false) }
                // Процесс сейчас будет завершён; до этого UI ничего не принимает
                UnlockResult.LOCKED_OUT -> _state.update { it.copy(stage = Stage.LOADING) }
            }
        }
    }

    /** Перевод БД со старой схемы SHA-256 на PBKDF2 (однократно, при первом входе после обновления). */
    private fun migrateKey(pin: CharArray) {
        try {
            databaseHolder.migrateKeyIfNeeded(pin)
        } catch (e: Exception) {
            // БД остаётся на старом ключе и продолжает работать; попытка повторится при следующем входе
            Log.e(TAG, "Database key migration failed", e)
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
        it.copy(stage = Stage.UNLOCKED, destination = Destination.USERS, pinError = false, busy = false)
    }

    private companion object {
        const val TAG = "AppViewModel"
    }
}
