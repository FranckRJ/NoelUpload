package com.franckrj.noelupload.history

import android.content.Context
import com.franckrj.noelupload.AppDatabase
import com.franckrj.noelupload.utils.Utils
import com.franckrj.noelupload.upload.UploadInfos
import com.franckrj.noelupload.upload.UploadInfosDao
import com.franckrj.noelupload.upload.UploadStatus
import com.franckrj.noelupload.utils.SafeLiveData
import com.franckrj.noelupload.utils.SafeMutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

class HistoryEntryRepository private constructor(private val appContext: Context) {
    companion object {
        private lateinit var instance: HistoryEntryRepository

        fun getInstance(newAppContext: Context): HistoryEntryRepository {
            if (!::instance.isInitialized) {
                instance = HistoryEntryRepository(newAppContext)
            }

            return instance
        }
    }

    private val _uploadInfosDao: UploadInfosDao = AppDatabase.instance.uploadInfosDao()
    private val _listOfHistoryEntries: MutableList<HistoryEntryInfos> = mutableListOf()
    private val _listOfHistoryEntriesChanges: SafeMutableLiveData<MutableList<HistoryEntryChangeInfos>> =
        SafeMutableLiveData(mutableListOf())

    val listOfHistoryEntriesChanges: SafeLiveData<out List<HistoryEntryChangeInfos>> = _listOfHistoryEntriesChanges

    init {
        runBlocking {
            _uploadInfosDao.getAllUploadInfos().mapTo(_listOfHistoryEntries) { uploadInfos: UploadInfos ->
                uploadInfosToHistoryEntry(uploadInfos, true)
            }
        }
    }

    private fun getPreviewFileFromUploadInfos(uploadInfos: UploadInfos): File {
        return File("${appContext.filesDir.path}/${uploadInfos.uploadTimeInMs}-${uploadInfos.imageName}")
    }

    private fun uploadInfosToHistoryEntry(uploadInfos: UploadInfos, fromDatabase: Boolean): HistoryEntryInfos {
        //todo faire les file.exists sur un background thread ?
        val previewFile = getPreviewFileFromUploadInfos(uploadInfos)

        return HistoryEntryInfos(
            uploadInfos.imageBaseLink,
            uploadInfos.imageName,
            uploadInfos.imageUri,
            uploadInfos.uploadTimeInMs,
            if (previewFile.exists()) {
                previewFile
            } else {
                null
            },
            Utils.noelshackLinkToPreviewLink(uploadInfos.imageBaseLink),
            if (fromDatabase) UploadStatus.FINISHED else UploadStatus.UPLOADING,
            if (fromDatabase) "" else "0"
        )
    }

    private fun getIndexOfUploadInfosInList(uploadInfos: UploadInfos?): Int {
        val historyEntry: HistoryEntryInfos? = _listOfHistoryEntries.lastOrNull()

        if (uploadInfos != null && historyEntry != null) {
            if (uploadInfos.imageUri == historyEntry.imageUri && uploadInfos.uploadTimeInMs == historyEntry.uploadTimeInMs) {
                return _listOfHistoryEntries.size - 1
            }
        }
        return -1
    }

    fun getACopyOfListOfHistoryEntries(): MutableList<HistoryEntryInfos> {
        val newListOfHistoryEntries: MutableList<HistoryEntryInfos> = mutableListOf()
        _listOfHistoryEntries.mapTo(newListOfHistoryEntries) { historyEntry: HistoryEntryInfos ->
            historyEntry.copy()
        }
        _listOfHistoryEntriesChanges.value.clear()
        return newListOfHistoryEntries
    }

    fun removeFirstHistoryEntryChange() {
        if (_listOfHistoryEntries.isNotEmpty()) {
            _listOfHistoryEntriesChanges.value.removeAt(0)
        }
    }

    fun postUpdateThisUploadInfosStatus(uploadInfos: UploadInfos?, newStatus: UploadStatus, newStatusMessage: String) =
        GlobalScope.launch(Dispatchers.Main) {
            val indexInList: Int = getIndexOfUploadInfosInList(uploadInfos)
            val historyEntry: HistoryEntryInfos? = _listOfHistoryEntries.getOrNull(indexInList)

            if (historyEntry != null && historyEntry.uploadStatus == UploadStatus.UPLOADING) {
                historyEntry.uploadStatus = newStatus
                historyEntry.uploadStatusMessage = newStatusMessage

                _listOfHistoryEntriesChanges.value.add(
                    HistoryEntryChangeInfos(
                        historyEntry.copy(),
                        HistoryEntryChangeType.CHANGED,
                        indexInList
                    )
                )
                _listOfHistoryEntriesChanges.updateValue()
            }
        }

    fun postUpdateThisUploadInfosLinkAndSetFinished(uploadInfos: UploadInfos?, newLink: String) =
        GlobalScope.launch(Dispatchers.Main) {
            val indexInList: Int = getIndexOfUploadInfosInList(uploadInfos)
            val historyEntry: HistoryEntryInfos? = _listOfHistoryEntries.getOrNull(indexInList)

            if (uploadInfos != null && historyEntry != null && historyEntry.uploadStatus == UploadStatus.UPLOADING) {
                historyEntry.imageBaseLink = newLink
                historyEntry.uploadStatus = UploadStatus.FINISHED
                historyEntry.uploadStatusMessage = ""

                withContext(Dispatchers.IO) {
                    _uploadInfosDao.insertUploadInfos(
                        UploadInfos(
                            newLink,
                            uploadInfos.imageName,
                            uploadInfos.imageUri,
                            uploadInfos.uploadTimeInMs
                        )
                    )
                }

                _listOfHistoryEntriesChanges.value.add(
                    HistoryEntryChangeInfos(
                        historyEntry.copy(),
                        HistoryEntryChangeType.FINISHED,
                        indexInList
                    )
                )
                _listOfHistoryEntriesChanges.updateValue()
            }
        }

    fun postUpdateThisUploadInfosPreview(uploadInfos: UploadInfos?) =
        GlobalScope.launch(Dispatchers.Main) {
            val indexInList: Int = getIndexOfUploadInfosInList(uploadInfos)
            val historyEntry: HistoryEntryInfos? = _listOfHistoryEntries.getOrNull(indexInList)

            if (uploadInfos != null && historyEntry != null) {
                val newPreviewFile: File = getPreviewFileFromUploadInfos(uploadInfos)

                if (newPreviewFile.exists()) {
                    historyEntry.fileForPreview = newPreviewFile
                    _listOfHistoryEntriesChanges.value.add(
                        HistoryEntryChangeInfos(
                            historyEntry.copy(),
                            HistoryEntryChangeType.CHANGED,
                            indexInList
                        )
                    )
                    _listOfHistoryEntriesChanges.updateValue()
                }
            }
        }

    fun postAddThisUploadInfos(uploadInfos: UploadInfos?) =
        GlobalScope.launch(Dispatchers.Main) {
            if (uploadInfos != null) {
                withContext(Dispatchers.IO) {
                    _uploadInfosDao.insertUploadInfos(uploadInfos)
                }
                _listOfHistoryEntries.add(uploadInfosToHistoryEntry(uploadInfos, false))
                _listOfHistoryEntriesChanges.value.add(
                    HistoryEntryChangeInfos(
                        _listOfHistoryEntries.last().copy(),
                        HistoryEntryChangeType.NEW,
                        _listOfHistoryEntries.size - 1
                    )
                )
                _listOfHistoryEntriesChanges.updateValue()
            }
        }

    enum class HistoryEntryChangeType {
        NEW,
        DELETED,
        CHANGED,
        FINISHED
    }

    data class HistoryEntryChangeInfos(
        val newHistoryEntry: HistoryEntryInfos,
        val changeType: HistoryEntryChangeType,
        val changeIndex: Int
    )
}
