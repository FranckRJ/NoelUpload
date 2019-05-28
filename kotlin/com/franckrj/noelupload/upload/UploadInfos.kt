package com.franckrj.noelupload.upload

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Data class contenant les informations sur un upload.
 * Le [imageBaseLink] est lien vers l'image avec l'overlay du site web.
 */
@Entity
data class UploadInfos(
    @PrimaryKey val imageBaseLink: String,
    val imageName: String,
    val uploadTimeInMs: Long
)

/**
 * DAO pour accéder aux informations sur les uploads.
 */
@Dao
interface UploadInfosDao {
    @Query("SELECT * FROM uploadinfos")
    fun getAllUploadInfos(): LiveData<List<UploadInfos>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUploadInfos(uploadInfos: UploadInfos)

    @Delete
    suspend fun deleteUploadInfos(vararg uploadInfos: UploadInfos)

    @Query("DELETE FROM uploadinfos")
    suspend fun deleteAllUploadInfos()

    @Query("SELECT * FROM uploadinfos WHERE imagebaselink = :baseLinkToSearch")
    suspend fun findByBaseLink(baseLinkToSearch: String): UploadInfos?
}
