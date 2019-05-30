package com.franckrj.noelupload.history

import com.franckrj.noelupload.upload.UploadInfos
import java.io.File

/**
 * Contient un [UploadInfos] ainsi que les informations nécessaires pour afficher la preview liée.
 * Si [fileForPreview] ne vaut pas null il contient l'image à utiliser pour la preview, sinon il faudra
 * utiliser [fallbackPreviewUrl] pour récupérer la preview depuis noelshack.com.
 */
data class HistoryEntryInfos(
    val uploadInfos: UploadInfos,
    val fileForPreview: File?,
    val fallbackPreviewUrl: String,
    var uploadProgression: Int = -1
)
