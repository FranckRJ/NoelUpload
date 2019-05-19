package com.franckrj.noelupload

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Database de l'application, contenant notamment les informations sur les uploads.
 */
@Database(entities = [UploadInfos::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        lateinit var instance: AppDatabase

        fun initDatabase(appContext: Context) {
            instance = Room.databaseBuilder(appContext, AppDatabase::class.java, "noelupload-main-db").build()
        }
    }

    abstract fun uploadInfosDao(): UploadInfosDao
}
