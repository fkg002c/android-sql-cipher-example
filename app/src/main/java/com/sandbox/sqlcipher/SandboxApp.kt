package com.sandbox.sqlcipher

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SandboxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Нативная часть SQLCipher должна быть загружена до первого открытия БД
        System.loadLibrary("sqlcipher")
    }
}
