package com.franckrj.noelupload.upload

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data class contenant les informations sur un upload.
 * Le [imageBaseLink] est lien vers l'image avec l'overlay du site web.
 */
@Entity(primaryKeys = ["imageUri", "uploadTimeInMs"])
data class UploadInfos(
    val imageBaseLink: String,
    val imageName: String,
    val imageUri: String,
    val uploadTimeInMs: Long
)

/**
 * Enum représentant le statut d'un upload.
 */
enum class UploadStatus {
    UPLOADING,
    FINISHED,
    ERROR
}

/**
 * DAO pour accéder aux informations sur les uploads.
 */
@Dao
interface UploadInfosDao {
    @Query("SELECT * FROM uploadinfos")
    suspend fun getAllUploadInfos(): List<UploadInfos>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUploadInfos(uploadInfos: UploadInfos): Long

    @Delete
    suspend fun deleteUploadInfos(vararg uploadInfos: UploadInfos)

    @Query("DELETE FROM uploadinfos")
    suspend fun deleteAllUploadInfos()

    @Query("SELECT * FROM uploadinfos WHERE rowid = :rowIdToSearch")
    suspend fun findByRowId(rowIdToSearch: Long): UploadInfos?

    @Query("SELECT * FROM uploadinfos WHERE imagebaselink = :baseLinkToSearch")
    suspend fun findByBaseLink(baseLinkToSearch: String): UploadInfos?
}
