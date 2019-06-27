package com.franckrj.noelupload

import android.app.Application
import com.franckrj.noelupload.history.HistoryEntryRepository

/**
 * Appliacation principale ayant pour but d'initialiser la [AppDatabase].
 */
class MainApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        AppDatabase.initDatabase(applicationContext)
        HistoryEntryRepository.initRepository(applicationContext)
    }
}
