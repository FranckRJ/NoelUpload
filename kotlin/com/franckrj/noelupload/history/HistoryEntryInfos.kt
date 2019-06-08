package com.franckrj.noelupload.history

import com.franckrj.noelupload.upload.UploadInfos
import com.franckrj.noelupload.upload.UploadStatus
import java.io.File

/**
 * Contient les informations d'un [UploadInfos] ainsi que ce qui est nécessaires pour son affichage dans la liste.
 * Si [fileForPreview] ne vaut pas null il contient l'image à utiliser pour la preview, sinon il faudra
 * utiliser [fallbackPreviewUrl] pour récupérer la preview depuis noelshack.com.
 */
data class HistoryEntryInfos(
    var imageBaseLink: String = "",
    var imageName: String = "",
    var imageUri: String = "",
    var uploadTimeInMs: Long = -1,
    var fileForPreview: File? = null,
    var fallbackPreviewUrl: String = "",
    var uploadStatus: UploadStatus = UploadStatus.ERROR,
    var uploadStatusMessage: String = "",
    var isInCurrentUploadGroup: Boolean = false
) {
    fun copy(): HistoryEntryInfos {
        return HistoryEntryInfos(
            imageBaseLink,
            imageName,
            imageUri,
            uploadTimeInMs,
            fileForPreview,
            fallbackPreviewUrl,
            uploadStatus,
            uploadStatusMessage,
            isInCurrentUploadGroup
        )
    }
}
