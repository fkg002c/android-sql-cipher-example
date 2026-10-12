package com.sandbox.sqlcipher.data.db

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.sandbox.sqlcipher.security.PinManager
import dagger.hilt.android.qualifiers.ApplicationContext
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ленивое открытие зашифрованной БД и смена её ключа.
 *
 * Пароль появляется только после установки ПИНа, поэтому [AppDatabase] нельзя
 * создавать на старте процесса — БД открывается при первом обращении.
 */
@Singleton
class DatabaseHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pinManager: PinManager,
) {
    private val lock = Any()

    @Volatile
    private var instance: AppDatabase? = null

    val database: AppDatabase
        get() = instance ?: synchronized(lock) {
            instance ?: open().also { instance = it }
        }

    /** БД можно открывать только когда ключ уже существует. */
    fun isReady(): Boolean = pinManager.isPinSet()

    /**
     * Переводит ключ БД со схемы SHA-256 на PBKDF2. Вызывается после успешной проверки ПИНа,
     * т.к. новый ключ выводится из самого ПИНа.
     *
     * Порядок устойчив к смерти процесса: новый ключ сохраняется как «ожидающий» →
     * БД перешифровывается → ключ фиксируется. Незавершённая миграция разбирается в
     * [recoverInterruptedMigration] при следующем открытии.
     */
    fun migrateKeyIfNeeded(pin: CharArray): Unit = synchronized(lock) {
        recoverInterruptedMigration()
        if (!pinManager.needsMigration()) return

        val file = dbFile()
        if (!file.exists()) {
            // БД ещё не создавалась — перешифровывать нечего
            pinManager.preparePending(pin)
            pinManager.commitPending()
            return
        }

        // rekey требует единственного соединения: закрываем Room, при следующем
        // обращении он откроется уже с новым ключом
        instance?.close()
        instance = null

        val oldKey = pinManager.databaseKey()
        val newKey = pinManager.preparePending(pin)
        SqlCipherRekey.rekey(file, oldKey, newKey)
        pinManager.commitPending()
        Log.i(TAG, "Database key migrated to PBKDF2")
    }

    private fun open(): AppDatabase {
        recoverInterruptedMigration()
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(pinManager.databaseKey()))
            .build()
    }

    /** Ожидающий ключ остался от прерванной миграции: выясняем, успела ли БД перешифроваться. */
    private fun recoverInterruptedMigration() {
        val pending = pinManager.pendingKey() ?: return
        val file = dbFile()
        if (file.exists() && SqlCipherRekey.canOpen(file, pending)) {
            pinManager.commitPending()
        } else {
            pinManager.discardPending()
        }
    }

    private fun dbFile(): File = context.getDatabasePath(AppDatabase.NAME)

    private companion object {
        const val TAG = "DatabaseHolder"
    }
}

/** Низкоуровневые операции SQLCipher в обход Room. */
internal object SqlCipherRekey {

    /**
     * Стандартный обработчик SQLCipher при «повреждении» удаляет файл БД, а неверный ключ
     * выглядит как повреждение. Здесь файл трогать нельзя ни при каких ошибках.
     */
    private val keepFile = DatabaseErrorHandler { _, _ -> }

    fun rekey(file: File, oldKey: ByteArray, newKey: ByteArray) {
        val db = open(file, oldKey)
        try {
            // Сливаем WAL в основной файл и выходим из WAL, чтобы rekey переписал все страницы
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            db.rawQuery("PRAGMA journal_mode = DELETE").use { it.moveToFirst() }
            db.changePassword(newKey)
        } finally {
            db.close()
        }
    }

    fun canOpen(file: File, key: ByteArray): Boolean = runCatching {
        val db = open(file, key)
        try {
            // Неверный ключ проявляется только при первом чтении страниц
            db.rawQuery("SELECT count(*) FROM sqlite_master").use { it.moveToFirst() }
        } finally {
            db.close()
        }
    }.isSuccess

    private fun open(file: File, key: ByteArray): SQLiteDatabase =
        SQLiteDatabase.openDatabase(file.path, key, null, SQLiteDatabase.OPEN_READWRITE, keepFile, null)
}
