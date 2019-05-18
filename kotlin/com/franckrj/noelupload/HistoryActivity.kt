package com.franckrj.noelupload

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import com.franckrj.noelupload.databinding.ActivityHistoryBinding

/**
 * Activité pour consulter l'historique des uploads.
 */
class HistoryActivity : AbsToolbarActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding: ActivityHistoryBinding = DataBindingUtil.setContentView(this, R.layout.activity_history)
        binding.lifecycleOwner = this
        binding.activity = this

        initToolbar(binding.toolbarHistory as Toolbar, homeIsBack = false, displayHome = false)
    }

    /**
     * Lance l'activité pour upload des images [UploadActivity].
     */
    fun goToUploadActivity() {
        startActivity(Intent(this, UploadActivity::class.java))
    }
}
