package com.sandbox.sqlcipher.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

object TelephonyPermissions {

    /** Разрешения, запрашиваемые при первом запуске. */
    val required: Array<String> = buildList {
        add(Manifest.permission.READ_PHONE_STATE) // состояние звонка
        add(Manifest.permission.READ_CALL_LOG)    // номер текущего звонка
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Manifest.permission.READ_PHONE_NUMBERS) // свой номер SIM
        }
    }.toTypedArray()

    fun allGranted(context: Context) = required.all { isGranted(context, it) }

    fun isGranted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/**
 * Собственный номер SIM-карты. Многие операторы не прописывают его на SIM,
 * поэтому null — штатный результат.
 */
object OwnPhoneNumber {

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun read(context: Context): String? {
        val numberPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Manifest.permission.READ_PHONE_NUMBERS
        } else {
            Manifest.permission.READ_PHONE_STATE
        }
        if (!TelephonyPermissions.isGranted(context, numberPermission)) return null

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val sm = context.getSystemService<SubscriptionManager>() ?: return null
                val subId = SubscriptionManager.getDefaultSubscriptionId()
                sm.getPhoneNumber(subId)
            } else {
                context.getSystemService<TelephonyManager>()?.line1Number
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
