package com.franckrj.noelupload

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.franckrj.noelupload.upload.UploadInfos
import com.franckrj.noelupload.upload.UploadInfosDao

/**
 * Database de l'application, contenant notamment les informations sur les uploads.
 */
@Database(entities = [UploadInfos::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        lateinit var instance: AppDatabase

        fun initDatabase(appContext: Context) {
            instance = Room.databaseBuilder(appContext, AppDatabase::class.java, "noelupload-main-db")
                .fallbackToDestructiveMigration()
                .build()
        }
    }

    abstract fun uploadInfosDao(): UploadInfosDao
}
