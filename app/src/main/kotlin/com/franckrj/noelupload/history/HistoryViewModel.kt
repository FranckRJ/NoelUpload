package com.franckrj.noelupload.history

import android.app.Application
import android.graphics.Point
import android.graphics.Rect
import android.view.Display
import androidx.lifecycle.AndroidViewModel
import com.franckrj.noelupload.R
import com.franckrj.noelupload.upload.UploadInfos
import com.franckrj.noelupload.upload.UploadStatus
import com.franckrj.noelupload.utils.SafeLiveData
import com.franckrj.noelupload.utils.SafeMutableLiveData
import com.franckrj.noelupload.utils.Utils

/**
 * ViewModel contenant les diverses informations sur l'historique des uploads.
 */
class HistoryViewModel(private val app: Application) : AndroidViewModel(app) {
    private val _previewCardMargin: Int = app.resources.getDimensionPixelSize(R.dimen.historyCardMargin)
    private val _fabMargin: Int = app.resources.getDimensionPixelSize(R.dimen.fabMargin)
    private val _historyEntryRepo: HistoryEntryRepository = HistoryEntryRepository.instance
    private val _listOfHistoryEntries: MutableList<HistoryEntryInfos> =
        _historyEntryRepo.getACopyOfListOfHistoryEntries()
    private val _currentGroupOfUploads: MutableList<UploadInfos> = mutableListOf()
    private val _historyListPadding: SafeMutableLiveData<Rect> =
        SafeMutableLiveData(Rect(_previewCardMargin, _previewCardMargin, _previewCardMargin, _previewCardMargin))

    val listOfHistoryEntries: List<HistoryEntryInfos> = _listOfHistoryEntries
    val listOfHistoryEntriesChanges: SafeLiveData<out List<HistoryEntryRepository.HistoryEntryChangeInfos>> =
        _historyEntryRepo.listOfHistoryEntriesChanges
    val historyListPadding: SafeLiveData<Rect> = _historyListPadding
    var windowInsetTop: Int = 0
        set(value) {
            field = value
            updateHistoryListPadding()
        }
    var fabHeight: Int = 0
        set(value) {
            field = value
            updateHistoryListPadding()
        }

    /**
     * Met à jour le padding de la liste contenant l'historique en fonction de la taille du FAB + de la statusbar.
     */
    private fun updateHistoryListPadding() {
        _historyListPadding.value = Rect(
            _previewCardMargin,
            _previewCardMargin + windowInsetTop,
            _previewCardMargin,
            fabHeight + (_fabMargin * 2) - _previewCardMargin
        )
    }

    /**
     * Convertis un [HistoryEntryInfos] en un [UploadInfos].
     */
    private fun historyEntryToUploadInfo(historyEntry: HistoryEntryInfos): UploadInfos {
        return UploadInfos(
            historyEntry.imageBaseLink,
            historyEntry.imageName,
            historyEntry.imageUri,
            historyEntry.uploadTimeInMs
        )
    }

    /**
     * Met à jour l'[UploadInfos] représenté par [historyEntry] dans [_currentGroupOfUploads] s'il est présent.
     * Le supprime si jamais le status de l'upload vaut [UploadStatus.ERROR] et màj l'historique.
     */
    private fun updateElementInCurrentUploadGroup(historyEntry: HistoryEntryInfos) {
        val uploadInfos: UploadInfos = historyEntryToUploadInfo(historyEntry)

        for (idx: Int in _currentGroupOfUploads.indices) {
            val currUploadInfos: UploadInfos? = _currentGroupOfUploads.getOrNull(idx)

            if (currUploadInfos != null && currUploadInfos.representSameUpload(uploadInfos)) {
                if (historyEntry.uploadStatus == UploadStatus.ERROR) {
                    _historyEntryRepo.postRemoveTheseUploadInfosFromCurrentGroup(listOf(currUploadInfos))
                    _currentGroupOfUploads.removeAt(idx)
                } else {
                    _currentGroupOfUploads[idx] = uploadInfos
                }
                return
            }
        }
    }

    /**
     * Retourne le nombre de colonnes à afficher pour afficher le plus de miniatures tout en respectant la taille
     * minimale de [R.dimen.minPreviewWidth].
     */
    fun computeNumberOfColumnsToShow(display: Display): Int {
        val minPreviewWidth: Int = app.resources.getDimensionPixelSize(R.dimen.minPreviewWidth)
        val minPreviewCardWidth: Int = minPreviewWidth + (_previewCardMargin * 2)
        val size = Point()

        display.getSize(size)
        return ((size.x - (_previewCardMargin * 2)) / minPreviewCardWidth).coerceAtLeast(1)
    }

    /**
     * Retourne la taille d'une miniature, calculée via le nombre de colonnes [numberOfColumns].
     */
    fun computeHeightOfItems(numberOfColumns: Int, display: Display): Int {
        val previewCardAllMargins: Int = (_previewCardMargin * 2)
        val size = Point()

        display.getSize(size)
        return (((size.x - previewCardAllMargins) / numberOfColumns) - previewCardAllMargins).coerceAtLeast(1)
    }

    /**
     * Applique le changement [historyEntryChange] à la liste [_listOfHistoryEntries] s'il est valide.
     * Retourne true si le changement est valide, false s'il est invalide.
     */
    fun applyHistoryChange(historyEntryChange: HistoryEntryRepository.HistoryEntryChangeInfos): Boolean {
        when (historyEntryChange.changeType) {
            HistoryEntryRepository.HistoryEntryChangeType.NEW -> {
                if (historyEntryChange.changeIndex == _listOfHistoryEntries.size) {
                    _listOfHistoryEntries.add(historyEntryChange.newHistoryEntry)
                    if (historyEntryChange.newHistoryEntry.isInCurrentUploadGroup) {
                        _currentGroupOfUploads.add(historyEntryToUploadInfo(historyEntryChange.newHistoryEntry))
                    }
                    return true
                }
            }
            HistoryEntryRepository.HistoryEntryChangeType.DELETED -> {
                if (historyEntryChange.changeIndex in (0 until _listOfHistoryEntries.size)) {
                    _listOfHistoryEntries.removeAt(historyEntryChange.changeIndex)
                    return true
                }
            }
            HistoryEntryRepository.HistoryEntryChangeType.CHANGED -> {
                updateElementInCurrentUploadGroup(historyEntryChange.newHistoryEntry)
                if (historyEntryChange.changeIndex in (0 until _listOfHistoryEntries.size)) {
                    _listOfHistoryEntries[historyEntryChange.changeIndex] = historyEntryChange.newHistoryEntry
                    return true
                }
            }
        }
        return false
    }

    /**
     * Supprime le 1er élement de la [listOfHistoryEntriesChanges] si elle n'est pas vide.
     */
    fun removeFirstHistoryEntryChange() {
        _historyEntryRepo.removeFirstHistoryEntryChange()
    }

    /**
     * Retourne la liste des liens directs vers les images de [_currentGroupOfUploads] puis vide la liste. Met aussi
     * à jours le status de l'historique en supprimant les [UploadInfos] du groupe d'upload.
     * Retourne une liste vide si jamais tous les uploads de [_currentGroupOfUploads] n'ont pas terminés.
     */
    fun getDirectLinksOfUploadGrouptAndClearIt(): List<String> {
        val listOfLinks: MutableList<String> = mutableListOf()

        for (currUploadInfos: UploadInfos in _currentGroupOfUploads) {
            if (currUploadInfos.imageBaseLink.isEmpty()) {
                return listOf()
            } else {
                listOfLinks.add(Utils.noelshackToDirectLink(currUploadInfos.imageBaseLink))
            }
        }

        _historyEntryRepo.postRemoveTheseUploadInfosFromCurrentGroup(_currentGroupOfUploads.toList())
        _currentGroupOfUploads.clear()
        return listOfLinks
    }
}
