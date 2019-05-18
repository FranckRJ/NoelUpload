package com.franckrj.noelupload

import android.view.MenuItem
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

/**
 * Classe de base pour les [AppCompatActivity] avec une [Toolbar].
 */
abstract class AbsToolbarActivity : AppCompatActivity() {
    private var homeIsBack: Boolean = false

    protected fun initToolbar(toolbar: Toolbar, homeIsBack: Boolean = true, displayHome: Boolean = true) {
        val myActionBar: ActionBar?

        setSupportActionBar(toolbar)
        this.homeIsBack = homeIsBack

        myActionBar = supportActionBar
        if (myActionBar != null) {
            myActionBar.setHomeButtonEnabled(displayHome)
            myActionBar.setDisplayHomeAsUpEnabled(displayHome)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (homeIsBack && item.itemId == android.R.id.home) {
            onBackPressed()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }
}
