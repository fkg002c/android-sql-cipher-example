package com.sandbox.sqlcipher.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Перешифрование SQLCipher-БД на устройстве (нативная библиотека под JVM недоступна).
 * Работает с отдельным файлом в cacheDir и не трогает рабочую БД приложения.
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherRekeyTest {

    private val oldKey = ByteArray(32) { 1 }
    private val newKey = ByteArray(32) { 2 }
    private lateinit var file: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("sqlcipher")
        file = File(context.cacheDir, "rekey-test.db")
        deleteFiles()
        SQLiteDatabase.openOrCreateDatabase(file, oldKey, null, null, null).use { db ->
            db.enableWriteAheadLogging()
            db.execSQL("CREATE TABLE t (v TEXT)")
            db.execSQL("INSERT INTO t VALUES ('hello')")
        }
    }

    @After
    fun tearDown() = deleteFiles()

    @Test
    fun rekeyChangesKeyAndKeepsData() {
        assertTrue(SqlCipherRekey.canOpen(file, oldKey))
        assertFalse(SqlCipherRekey.canOpen(file, newKey))

        SqlCipherRekey.rekey(file, oldKey, newKey)

        assertFalse(SqlCipherRekey.canOpen(file, oldKey))
        assertTrue(SqlCipherRekey.canOpen(file, newKey))
        SQLiteDatabase.openDatabase(file.path, newKey, null, SQLiteDatabase.OPEN_READONLY, null, null).use { db ->
            db.rawQuery("SELECT v FROM t").use {
                assertTrue(it.moveToFirst())
                assertEquals("hello", it.getString(0))
            }
        }
    }

    @Test
    fun canOpenWithWrongKeyDoesNotDeleteFile() {
        assertFalse(SqlCipherRekey.canOpen(file, newKey))
        assertTrue(file.exists())
        assertTrue(SqlCipherRekey.canOpen(file, oldKey))
    }

    private fun deleteFiles() {
        listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
    }
}
