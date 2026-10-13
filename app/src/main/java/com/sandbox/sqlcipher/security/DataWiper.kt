package com.sandbox.sqlcipher.security

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.core.content.getSystemService
import com.sandbox.sqlcipher.data.db.AppDatabase
import com.sandbox.sqlcipher.data.db.DatabaseHolder
import com.sandbox.sqlcipher.telephony.TelephonyPermissions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Уничтожение всех пользовательских данных после исчерпания попыток ввода ПИНа.
 *
 * Сначала данные удаляются вручную в порядке, устойчивом к смерти процесса, затем вызывается
 * системный [ActivityManager.clearApplicationUserData]: он стирает всё остальное, отзывает
 * runtime-разрешения и завершает процесс — так же, как «Очистить данные» в настройках.
 *
 * Вызывать не на main-потоке. При успехе метод не возвращает управление надолго: процесс
 * завершается системой или самим методом.
 */
@Singleton
class DataWiper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseHolder: DatabaseHolder,
) {
    fun wipe() {
        Log.w(TAG, "PIN attempts exhausted, wiping user data")
        runStep("database") {
            databaseHolder.close()
            context.deleteDatabase(AppDatabase.NAME)
        }
        // Счётчик попыток лежит здесь же. Пока файл не удалён, незавершённая очистка
        // повторится при следующем запуске; после удаления остаётся только мусор без ключа.
        runStep("secure prefs") { context.deleteSharedPreferences(SecurityModule.PREFS_NAME) }
        deleteKeystoreEntriesIfSafe()

        val cleared = runCatching {
            context.getSystemService<ActivityManager>()?.clearApplicationUserData() == true
        }.getOrDefault(false)
        if (cleared) return // система сама завершит процесс

        Log.w(TAG, "clearApplicationUserData failed, falling back to manual wipe")
        runStep("files") { appDirectories().forEach(::deleteContents) }
        deleteKeystoreEntriesIfSafe()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runStep("permissions") {
                context.revokeSelfPermissionsOnKill(TelephonyPermissions.required.toList())
            }
        }
        // Без перезапуска процесса в памяти остались бы ключ и открытые соединения
        Process.killProcess(Process.myPid())
    }

    /**
     * Ключи Keystore удаляются только после файла настроек: EncryptedSharedPreferences без
     * своего мастер-ключа не открываются, и приложение не смогло бы стартовать.
     */
    private fun deleteKeystoreEntriesIfSafe() {
        if (File(sharedPrefsDir(), "${SecurityModule.PREFS_NAME}.xml").exists()) return
        runStep("keystore") {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.aliases().toList().forEach(keyStore::deleteEntry)
        }
    }

    private fun sharedPrefsDir() = File(context.applicationInfo.dataDir, "shared_prefs")

    private fun appDirectories(): List<File> = listOfNotNull(
        context.getDatabasePath(AppDatabase.NAME).parentFile,
        sharedPrefsDir(),
        context.filesDir,
        context.noBackupFilesDir,
        context.cacheDir,
        context.codeCacheDir,
        context.getExternalFilesDir(null),
        context.externalCacheDir,
    )

    private fun deleteContents(dir: File) {
        dir.listFiles()?.forEach { it.deleteRecursively() }
    }

    // Ошибка одного шага не должна останавливать остальные
    private inline fun runStep(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "Wipe step '$name' failed", e)
        }
    }

    private companion object {
        const val TAG = "DataWiper"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
