package com.franckrj.noelupload

import android.app.Application

/**
 * Appliacation principale ayant pour but d'initialiser la [AppDatabase].
 */
class MainApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        AppDatabase.initDatabase(applicationContext)
    }
}
