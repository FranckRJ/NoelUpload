package com.franckrj.noelupload.history

import android.content.Context
import com.franckrj.noelupload.AppDatabase
import com.franckrj.noelupload.upload.UploadInfos
import com.franckrj.noelupload.upload.UploadInfosDao
import com.franckrj.noelupload.upload.UploadStatus
import com.franckrj.noelupload.utils.SafeLiveData
import com.franckrj.noelupload.utils.SafeMutableLiveData
import com.franckrj.noelupload.utils.Utils
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
        lateinit var instance: HistoryEntryRepository

        fun initRepository(newAppContext: Context) {
            instance = HistoryEntryRepository(newAppContext)
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
            if (previewFile.exists() || (fromDatabase && uploadInfos.imageBaseLink.isEmpty())) {
                previewFile
            } else {
                null
            },
            Utils.noelshackLinkToPreviewLink(uploadInfos.imageBaseLink),
            if (fromDatabase) UploadStatus.FINISHED else UploadStatus.UPLOADING,
            if (fromDatabase) "" else "0",
            !fromDatabase
        )
    }

    /**
     * Retourne l'index dans la [_listOfHistoryEntriesChanges] de l'élement qui représente l'[uploadInfos] ou -1 s'il
     * n'existe pas.
     */
    private fun getIndexOfUploadInfosInList(uploadInfos: UploadInfos?): Int {
        if (uploadInfos != null) {
            for (idx: Int in _listOfHistoryEntries.lastIndex downTo 0) {
                val historyEntry: HistoryEntryInfos? = _listOfHistoryEntries.getOrNull(idx)

                if (historyEntry != null && uploadInfos.imageUri == historyEntry.imageUri && uploadInfos.uploadTimeInMs == historyEntry.uploadTimeInMs) {
                    return idx
                }
            }
        }
        return -1
    }

    /**
     * Retourne le fichier de la preview pour l'[uploadInfos].
     */
    fun getPreviewFileFromUploadInfos(uploadInfos: UploadInfos): File {
        return File("${appContext.filesDir.path}/prev-${uploadInfos.uploadTimeInMs}-${Utils.uriToFileName(uploadInfos.imageUri)}")
    }

    /**
     * Retourne le fichier servant de cache pour l'[uploadInfos].
     */
    fun getCachedFileFromUploadInfo(uploadInfos: UploadInfos): File {
        return File("${appContext.cacheDir.path}/file-${uploadInfos.uploadTimeInMs}-${Utils.uriToFileName(uploadInfos.imageUri)}.nop")
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
        if (_listOfHistoryEntriesChanges.value.isNotEmpty()) {
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
                historyEntry.fallbackPreviewUrl = Utils.noelshackLinkToPreviewLink(historyEntry.imageBaseLink)
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
    fun postUpdateThisUploadInfosPreview(uploadInfos: UploadInfos?) = GlobalScope.launch(Dispatchers.Main) {
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
     * Passe le [HistoryEntryInfos.isInCurrentUploadGroup] à false des entrées de [_listOfHistoryEntries] correspondant
     * à chaque [UploadInfos] de [listOfUploadInfos] sur le main thread et dispatch les modifications.
     */
    fun postRemoveTheseUploadInfosFromCurrentGroup(listOfUploadInfos: List<UploadInfos>) =
        GlobalScope.launch(Dispatchers.Main) {
            var changeHasOccured = false

            for (uploadInfos: UploadInfos in listOfUploadInfos) {
                val indexInList: Int = getIndexOfUploadInfosInList(uploadInfos)
                val historyEntry: HistoryEntryInfos? = _listOfHistoryEntries.getOrNull(indexInList)

                if (historyEntry != null) {
                    historyEntry.isInCurrentUploadGroup = false
                    _listOfHistoryEntriesChanges.value.add(
                        HistoryEntryChangeInfos(
                            historyEntry.copy(),
                            HistoryEntryChangeType.CHANGED,
                            indexInList
                        )
                    )
                    changeHasOccured = true
                }
            }

            if (changeHasOccured) {
                _listOfHistoryEntriesChanges.updateValue()
            }
        }

    /**
     * Supprime une entrée dans [_listOfHistoryEntries] correspondant au [uploadInfos] sur le main thread et dispatch
     * les modifications.
     */
    fun postDeleteThisUploadInfos(uploadInfos: UploadInfos?) = GlobalScope.launch(Dispatchers.Main) {
        val indexInList: Int = getIndexOfUploadInfosInList(uploadInfos)
        val historyEntry: HistoryEntryInfos? = _listOfHistoryEntries.getOrNull(indexInList)

        if (uploadInfos != null && historyEntry != null) {
            _listOfHistoryEntries.removeAt(indexInList)

            GlobalScope.launch(Dispatchers.IO) {
                _uploadInfosDao.deleteUploadInfos(uploadInfos)
                runCatching { getPreviewFileFromUploadInfos(uploadInfos).delete() }
                runCatching { getCachedFileFromUploadInfo(uploadInfos).delete() }
            }

            _listOfHistoryEntriesChanges.value.add(
                HistoryEntryChangeInfos(
                    historyEntry.copy(),
                    HistoryEntryChangeType.DELETED,
                    indexInList
                )
            )
            _listOfHistoryEntriesChanges.updateValue()
        }
    }

    /**
     * Ajoute une nouvelle entrée dans [_listOfHistoryEntries] correspondant au [uploadInfos] sur le main thread et
     * dispatch les modifications.
     */
    suspend fun blockAddThisUploadInfos(uploadInfos: UploadInfos?) = withContext(Dispatchers.Main) {
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
     * Remet le status de l'[HistoryEntryInfos] correspondant à [uploadInfos] à [UploadStatus.UPLOADING] et le rajoute
     * au groupe courant des uploads sur le main thread en dispatchant les modifications.
     */
    suspend fun blockReAddUploadInfosToCurrentGroup(uploadInfos: UploadInfos?) = withContext(Dispatchers.Main) {
        val indexInList: Int = getIndexOfUploadInfosInList(uploadInfos)
        val historyEntry: HistoryEntryInfos? = _listOfHistoryEntries.getOrNull(indexInList)

        if (historyEntry != null) {
            historyEntry.uploadStatus = UploadStatus.UPLOADING
            historyEntry.uploadStatusMessage = "0"
            historyEntry.isInCurrentUploadGroup = true
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
