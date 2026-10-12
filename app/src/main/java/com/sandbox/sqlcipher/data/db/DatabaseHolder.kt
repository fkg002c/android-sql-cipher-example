package com.sandbox.sqlcipher.data.db

import android.content.Context
import androidx.room.Room
import com.sandbox.sqlcipher.security.PinManager
import dagger.hilt.android.qualifiers.ApplicationContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ленивое открытие зашифрованной БД.
 *
 * Пароль (SHA ПИНа) появляется только после установки ПИНа, поэтому [AppDatabase]
 * нельзя создавать на старте процесса — БД открывается при первом обращении.
 */
@Singleton
class DatabaseHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pinManager: PinManager,
) {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(pinManager.databaseKey()))
            .build()
    }

    /** БД можно открывать только когда ключ уже существует. */
    fun isReady(): Boolean = pinManager.isPinSet()
}
