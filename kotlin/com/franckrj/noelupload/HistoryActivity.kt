package com.franckrj.noelupload

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.franckrj.noelupload.databinding.ActivityHistoryBinding

/**
 * Activité pour consulter l'historique des uploads.
 */
class HistoryActivity : AbsToolbarActivity() {
    val adapterForHistory = HistoryListAdapter()

    /**
     * Callback appelé lorsqu'un item est cliqué dans la liste, copie le lien direct associé dans
     * le presse-papier ou affiche une erreur.
     */
    private fun itemInHistoryListClicked(uploadInfos: UploadInfos) {
        val linkOfImage: String = Utils.noelshackToDirectLink(uploadInfos.imageBaseLink)

        if (Utils.checkIfItsANoelshackImageLink(linkOfImage)) {
            Utils.putStringInClipboard(this, linkOfImage)
            Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.invalid_link, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding: ActivityHistoryBinding = DataBindingUtil.setContentView(this, R.layout.activity_history)
        binding.lifecycleOwner = this
        binding.activity = this

        initToolbar(binding.toolbarHistory as Toolbar, homeIsBack = false, displayHome = false)

        adapterForHistory.itemClickedCallback = ::itemInHistoryListClicked

        binding.uploadhistoryListHistory.layoutManager = LinearLayoutManager(this)
        binding.uploadhistoryListHistory.adapter = adapterForHistory

        AppDatabase.instance.uploadInfosDao().getAllUploadInfos()
            .observe(this, Observer { newListOfUploadInfos: List<UploadInfos>? ->
                if (newListOfUploadInfos != null) {
                    adapterForHistory.listOfUploadInfos = newListOfUploadInfos
                    adapterForHistory.notifyDataSetChanged()
                    if (adapterForHistory.itemCount > 0) {
                        binding.uploadhistoryListHistory.scrollToPosition(adapterForHistory.itemCount - 1)
                    }
                }
            })
    }

    /**
     * Lance l'activité pour upload des images [UploadActivity].
     */
    fun goToUploadActivity() {
        startActivity(Intent(this, UploadActivity::class.java))
    }
}
