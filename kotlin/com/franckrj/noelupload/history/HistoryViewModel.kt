package com.franckrj.noelupload.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.franckrj.noelupload.AppDatabase
import com.franckrj.noelupload.upload.UploadInfos
import com.franckrj.noelupload.upload.UploadInfosDao
import com.franckrj.noelupload.Utils
import com.franckrj.noelupload.upload.UploadStatus
import com.franckrj.noelupload.upload.UploadStatusInfos
import java.io.File

/**
 * ViewModel contenant les diverses informations sur l'historique des uploads.
 */
class HistoryViewModel(private val app: Application) : AndroidViewModel(app) {
    private val _uploadInfosDao: UploadInfosDao = AppDatabase.instance.uploadInfosDao()

    private val _listOfHistoryEntries: MutableList<HistoryEntryInfos> = mutableListOf()
    private val _listOfUploadInfos: LiveData<List<UploadInfos>?> = _uploadInfosDao.getAllUploadInfos()
    private val _liveListOfHistoryEntries: MediatorLiveData<List<HistoryEntryInfos>?> = MediatorLiveData()
    private val _currHistoryEntryHasBeenChanged: MutableLiveData<Boolean?> = MutableLiveData(true)
    private var _currUploadInfos: UploadInfos? = null
    private var _currUploadStatusInfos: UploadStatusInfos? = null

    val liveListOfHistoryEntries: LiveData<List<HistoryEntryInfos>?> = _liveListOfHistoryEntries
    /**
     * [LiveData] qui est modifié à chaque fois que les informations sur l'[HistoryEntryInfos] actuelle sont modifiées.
     */
    val currHistoryEntryHasBeenChanged: LiveData<Boolean?> = _currHistoryEntryHasBeenChanged

    init {
        _liveListOfHistoryEntries.addSource(_listOfUploadInfos) { newListOfUploadInfos: List<UploadInfos>? ->
            _listOfHistoryEntries.clear()

            //todo optimiser ça et ne pas faire de file.exists à chaque modif de list ?
            //todo faire les file.exists sur un background thread ?
            newListOfUploadInfos?.mapTo(_listOfHistoryEntries) { uploadInfos: UploadInfos ->
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
            updateCurrHistoryEntry()
            _liveListOfHistoryEntries.value = _listOfHistoryEntries
        }
    }

    /**
     * Met à jour l'[HistoryEntryInfos] actuelle avec les informations de [_currUploadStatusInfos].
     */
    private fun updateCurrHistoryEntry() {
        _listOfHistoryEntries.find { currElem: HistoryEntryInfos ->
            currElem.uploadInfos.id == _currUploadInfos?.id
        }?.let { findElem: HistoryEntryInfos ->
            val currUploadStatusInfos: UploadStatusInfos? = _currUploadStatusInfos

            if (currUploadStatusInfos?.status == UploadStatus.UPLOADING) {
                findElem.uploadProgression = currUploadStatusInfos.message.toIntOrNull() ?: -1
            } else {
                findElem.uploadProgression = -1
            }
        }
    }

    fun setCurrUploadInfos(newUploadInfos: UploadInfos?) {
        val oldUploadStatusInfos: UploadStatusInfos? = _currUploadStatusInfos
        _currUploadStatusInfos = null
        updateCurrHistoryEntry()
        _currUploadStatusInfos = oldUploadStatusInfos
        _currUploadInfos = newUploadInfos
        updateCurrHistoryEntry()
        _currHistoryEntryHasBeenChanged.value = true
    }

    fun setCurrUploadStatusInfos(newUploadStatusInfos: UploadStatusInfos?) {
        _currUploadStatusInfos = newUploadStatusInfos
        updateCurrHistoryEntry()
        _currHistoryEntryHasBeenChanged.value = true
    }
}
