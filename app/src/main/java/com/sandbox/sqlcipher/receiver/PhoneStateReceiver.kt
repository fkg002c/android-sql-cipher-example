package com.sandbox.sqlcipher.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.sandbox.sqlcipher.data.repository.EventRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Пишет в таблицу events каждое изменение состояния звонка
 * (android.intent.action.PHONE_STATE).
 */
@AndroidEntryPoint
class PhoneStateReceiver : BroadcastReceiver() {

    @Inject lateinit var eventRepository: EventRepository

    // Инъекция выполняется в onReceive сгенерированного Hilt-родителя:
    // Hilt Gradle plugin сам добавляет вызов super.onReceive в байткоде.
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        // Приложению с READ_CALL_LOG система шлёт broadcast дважды: с EXTRA_INCOMING_NUMBER
        // и без него. Берём только копию с номером, чтобы не дублировать события.
        if (hasCallLogPermission(context) &&
            !intent.hasExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        ) return

        val timestamp = System.currentTimeMillis()
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        val pending = goAsync()
        scope.launch {
            try {
                val saved = eventRepository.record(timestamp, state, number)
                if (!saved) Log.i(TAG, "PIN is not set yet, event skipped")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save phone event", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun hasCallLogPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "PhoneStateReceiver"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
