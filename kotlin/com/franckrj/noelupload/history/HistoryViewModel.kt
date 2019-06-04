package com.franckrj.noelupload.history

import android.app.Application
import android.graphics.Point
import android.view.Display
import androidx.lifecycle.AndroidViewModel
import com.franckrj.noelupload.R
import com.franckrj.noelupload.utils.SafeLiveData

/**
 * ViewModel contenant les diverses informations sur l'historique des uploads.
 */
class HistoryViewModel(private val app: Application) : AndroidViewModel(app) {
    private val _historyEntryRepo: HistoryEntryRepository = HistoryEntryRepository.instance
    private val _listOfHistoryEntries: MutableList<HistoryEntryInfos> =
        _historyEntryRepo.getACopyOfListOfHistoryEntries()

    val listOfHistoryEntries: List<HistoryEntryInfos> = _listOfHistoryEntries
    val listOfHistoryEntriesChanges: SafeLiveData<out List<HistoryEntryRepository.HistoryEntryChangeInfos>> =
        _historyEntryRepo.listOfHistoryEntriesChanges

    /**
     * Retourne le nombre de colonnes à afficher pour afficher le plus de miniatures tout en respectant la taille
     * minimale de [R.dimen.minPreviewWidth].
     */
    fun computeNumberOfColumnsToShow(display: Display): Int {
        val minPreviewWidth: Int = app.resources.getDimensionPixelSize(R.dimen.minPreviewWidth)
        val previewCardMargin: Int = app.resources.getDimensionPixelSize(R.dimen.historyCardMargin)
        val minPreviewCardWidth: Int = minPreviewWidth + (previewCardMargin * 2)
        val size = Point()

        display.getSize(size)
        return ((size.x - (previewCardMargin * 2)) / minPreviewCardWidth).coerceAtLeast(1)
    }

    /**
     * Retourne la taille d'une miniature, calculée via le nombre de colonnes [numberOfColumns].
     */
    fun computeHeightOfItems(numberOfColumns: Int, display: Display): Int {
        val previewCardAllMargins: Int = (app.resources.getDimensionPixelSize(R.dimen.historyCardMargin) * 2)
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
                    return true
                }
                return false
            }
            HistoryEntryRepository.HistoryEntryChangeType.DELETED -> {
                if (historyEntryChange.changeIndex in (0 until _listOfHistoryEntries.size)) {
                    _listOfHistoryEntries.removeAt(historyEntryChange.changeIndex)
                    return true
                }
                return false
            }
            HistoryEntryRepository.HistoryEntryChangeType.CHANGED -> {
                if (historyEntryChange.changeIndex in (0 until _listOfHistoryEntries.size)) {
                    _listOfHistoryEntries[historyEntryChange.changeIndex] = historyEntryChange.newHistoryEntry
                    return true
                }
                return false
            }
        }
    }

    /**
     * Supprime le 1er élement de la [listOfHistoryEntriesChanges] si elle n'est pas vide.
     */
    fun removeFirstHistoryEntryChange() {
        _historyEntryRepo.removeFirstHistoryEntryChange()
    }
}
