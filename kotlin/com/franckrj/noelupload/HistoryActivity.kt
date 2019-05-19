package com.franckrj.noelupload

import android.content.Intent
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding: ActivityHistoryBinding = DataBindingUtil.setContentView(this, R.layout.activity_history)
        binding.lifecycleOwner = this
        binding.activity = this

        initToolbar(binding.toolbarHistory as Toolbar, homeIsBack = false, displayHome = false)

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
