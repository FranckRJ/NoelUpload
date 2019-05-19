package com.franckrj.noelupload

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Activité ayant pour but de lancer les bonnes activités au démarrage.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launchIntent: Intent? = intent
        val historyIntent = Intent(this, HistoryActivity::class.java)

        historyIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(historyIntent)
        if (launchIntent != null && launchIntent.action == Intent.ACTION_SEND && launchIntent.hasExtra(Intent.EXTRA_STREAM)) {
            val uploadIntent = Intent(this, UploadActivity::class.java)
            uploadIntent.action = Intent.ACTION_SEND
            uploadIntent.putExtra(Intent.EXTRA_STREAM, launchIntent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            startActivity(uploadIntent)
        }

        finish()
    }
}
