package com.sandbox.sqlcipher.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before

/**
 * In-memory Room без SQLCipher: проверяется логика запросов, а не шифрование
 * (нативная libsqlcipher.so под JVM недоступна).
 */
abstract class DaoTestBase {

    protected lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }
}
