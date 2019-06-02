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

/**
 * Le repo qui gère l'historique et sa synchronisation avec la DB.
 */
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

    /**
     * Une liste des changements qui doivent être approtés à la copie de l'historique.
     */
    val listOfHistoryEntriesChanges: SafeLiveData<out List<HistoryEntryChangeInfos>> = _listOfHistoryEntriesChanges

    init {
        runBlocking {
            _uploadInfosDao.getAllUploadInfos().mapTo(_listOfHistoryEntries) { uploadInfos: UploadInfos ->
                uploadInfosToHistoryEntry(uploadInfos, true)
            }
        }
    }

    /**
     * Convertis un [UploadInfos] en un [HistoryEntryInfos] en prenant en compte le fait qu'il vient de la DB ou d'un
     * nouvel upload.
     */
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

    /**
     * Retourne l'index dans la [_listOfHistoryEntriesChanges] de l'élement qui représente l'[uploadInfos] ou -1 s'il
     * n'existe pas.
     */
    private fun getIndexOfUploadInfosInList(uploadInfos: UploadInfos?): Int {
        val historyEntry: HistoryEntryInfos? = _listOfHistoryEntries.lastOrNull()

        if (uploadInfos != null && historyEntry != null) {
            if (uploadInfos.imageUri == historyEntry.imageUri && uploadInfos.uploadTimeInMs == historyEntry.uploadTimeInMs) {
                return _listOfHistoryEntries.lastIndex
            }
        }
        return -1
    }

    /**
     * Retourne le fichier de la preview d'une entrée de l'historique.
     */
    fun getPreviewFileFromUploadInfos(uploadInfos: UploadInfos): File {
        return File("${appContext.filesDir.path}/prev-${uploadInfos.uploadTimeInMs}-${Utils.uriToFileName(uploadInfos.imageUri)}")
    }

    /**
     * Retourne une copie de la [_listOfHistoryEntries] actuelle et reset la [listOfHistoryEntriesChanges].
     */
    fun getACopyOfListOfHistoryEntries(): MutableList<HistoryEntryInfos> {
        val newListOfHistoryEntries: MutableList<HistoryEntryInfos> = mutableListOf()
        _listOfHistoryEntries.mapTo(newListOfHistoryEntries) { historyEntry: HistoryEntryInfos ->
            historyEntry.copy()
        }
        _listOfHistoryEntriesChanges.value.clear()
        return newListOfHistoryEntries
    }

    /**
     * Supprime le 1er élement de la [listOfHistoryEntriesChanges] si elle n'est pas vide.
     */
    fun removeFirstHistoryEntryChange() {
        if (_listOfHistoryEntries.isNotEmpty()) {
            _listOfHistoryEntriesChanges.value.removeAt(0)
        }
    }

    /**
     * Met à jour le statut de l'entrée de [_listOfHistoryEntries] correspondant au [uploadInfos] sur le main thread
     * et dispatch les modifications.
     */
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

    /**
     * Passe le statut à [UploadStatus.FINISHED] et met à jour le lien de l'image de l'entrée de [_listOfHistoryEntries]
     * correspondant au [uploadInfos] sur le main thread et dispatch les modifications.
     */
    fun postUpdateThisUploadInfosLinkAndSetFinished(uploadInfos: UploadInfos?, newLink: String) =
        GlobalScope.launch(Dispatchers.Main) {
            val indexInList: Int = getIndexOfUploadInfosInList(uploadInfos)
            val historyEntry: HistoryEntryInfos? = _listOfHistoryEntries.getOrNull(indexInList)

            if (uploadInfos != null && historyEntry != null && historyEntry.uploadStatus == UploadStatus.UPLOADING) {
                historyEntry.imageBaseLink = newLink
                historyEntry.uploadStatus = UploadStatus.FINISHED
                historyEntry.uploadStatusMessage = ""

                GlobalScope.launch(Dispatchers.IO) {
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
                        HistoryEntryChangeType.CHANGED,
                        indexInList
                    )
                )
                _listOfHistoryEntriesChanges.updateValue()
            }
        }

    /**
     * Met à jour les infos sur la preview de l'entrée de [_listOfHistoryEntries] correspondant au [uploadInfos] sur
     * le main thread et dispatch les modifications.
     */
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

    /**
     * Ajoute une nouvelle entrée dans [_listOfHistoryEntries] correspondant au [uploadInfos] sur le main thread et
     * dispatch les modifications.
     */
    suspend fun blockAddThisUploadInfos(uploadInfos: UploadInfos?) =
        withContext(Dispatchers.Main) {
            if (uploadInfos != null) {
                GlobalScope.launch(Dispatchers.IO) {
                    _uploadInfosDao.insertUploadInfos(uploadInfos)
                }
                _listOfHistoryEntries.add(uploadInfosToHistoryEntry(uploadInfos, false))
                _listOfHistoryEntriesChanges.value.add(
                    HistoryEntryChangeInfos(
                        _listOfHistoryEntries.last().copy(),
                        HistoryEntryChangeType.NEW,
                        _listOfHistoryEntries.lastIndex
                    )
                )
                _listOfHistoryEntriesChanges.updateValue()
            }
        }

    /**
     * Type d'un changement dans [_listOfHistoryEntries].
     */
    enum class HistoryEntryChangeType {
        NEW,
        DELETED,
        CHANGED
    }

    /**
     * Détails sur un changemenbt dans [_listOfHistoryEntries].
     */
    data class HistoryEntryChangeInfos(
        val newHistoryEntry: HistoryEntryInfos,
        val changeType: HistoryEntryChangeType,
        val changeIndex: Int
    )
}
