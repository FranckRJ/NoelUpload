package com.franckrj.noelupload

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.franckrj.noelupload.history.HistoryActivity
import com.franckrj.noelupload.upload.UploadActivity

/**
 * Activité ayant pour but de lancer les bonnes activités au démarrage.
 */
class MainActivity : AppCompatActivity() {
    companion object {
        const val ACTION_UPLOAD_IMAGE: String = "com.franckrj.noelupload.ACTION_UPLOAD_IMAGE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launchIntent: Intent? = intent
        val historyIntent = Intent(this, HistoryActivity::class.java)

        historyIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(historyIntent)
        if (launchIntent != null) {
            if (launchIntent.action == Intent.ACTION_SEND && launchIntent.hasExtra(Intent.EXTRA_STREAM)) {
                val uploadIntent = Intent(this, UploadActivity::class.java)
                uploadIntent.action = Intent.ACTION_SEND
                uploadIntent.putExtra(Intent.EXTRA_STREAM, launchIntent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
                startActivity(uploadIntent)
            } else if (launchIntent.action == ACTION_UPLOAD_IMAGE) {
                startActivity(Intent(this, UploadActivity::class.java))
            }
        }

        finish()
    }
}
