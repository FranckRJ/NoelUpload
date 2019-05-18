package com.franckrj.noelupload

import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

/**
 * Classe de base pour les [AppCompatActivity] avec une [Toolbar].
 */
abstract class AbsToolbarActivity : AppCompatActivity() {
    protected fun initToolbar(toolbar: Toolbar, displayHome: Boolean = true) {
        setSupportActionBar(toolbar)

        val myActionBar: ActionBar? = supportActionBar
        if (myActionBar != null) {
            myActionBar.setHomeButtonEnabled(displayHome)
            myActionBar.setDisplayHomeAsUpEnabled(displayHome)
        }
    }
}
