package com.franckrj.noelupload.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.franckrj.noelupload.AppDatabase
import com.franckrj.noelupload.upload.UploadInfos
import com.franckrj.noelupload.upload.UploadInfosDao
import com.franckrj.noelupload.Utils
import java.io.File

/**
 * ViewModel contenant les diverses informations sur l'historique des uploads.
 */
class HistoryViewModel(private val app: Application) : AndroidViewModel(app) {
    private val _uploadInfosDao: UploadInfosDao = AppDatabase.instance.uploadInfosDao()

    //todo optimiser ça et ne pas faire de file.exists à chaque modif de list ?
    //todo faire les file.exists sur un background thread ?
    /**
     * Une liste des entrées de l'historique ([UploadInfos] + infos pour preview).
     */
    val listOfHistoryEntries: LiveData<List<HistoryEntryInfos>?> =
        _uploadInfosDao.getAllUploadInfos().map { listOfUploadInfos: List<UploadInfos> ->
            listOfUploadInfos.map { uploadInfos: UploadInfos ->
                val imagePreviewFile =
                    File("${app.filesDir.path}/${uploadInfos.uploadTimeInMs}-${uploadInfos.imageName}")
                HistoryEntryInfos(
                    uploadInfos,
                    if (imagePreviewFile.exists())
                        imagePreviewFile
                    else
                        null,
                    Utils.noelshackLinkToPreviewLink(uploadInfos.imageBaseLink)
                )
            }
        }
}
