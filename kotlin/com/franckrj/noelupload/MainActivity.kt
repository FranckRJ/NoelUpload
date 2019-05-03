package com.franckrj.noelupload

import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import com.franckrj.noelupload.databinding.ActivityMainBinding
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val CHOOSE_IMAGE_REQUEST_CODE : Int = 38
    }

    private lateinit var mainViewModel: MainViewModel

    private fun putStringInClipboard(textToCopy: String) {
        val clipboard: ClipboardManager? = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

        if (clipboard != null) {
            val clip = ClipData.newPlainText(textToCopy, textToCopy)
            clipboard.primaryClip = clip
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding: ActivityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        mainViewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)
        binding.lifecycleOwner = this
        binding.activity = this
        binding.viewmodel = mainViewModel

        val tmpIntent: Intent? = intent
        if (savedInstanceState == null && tmpIntent != null) {
            if (tmpIntent.action == Intent.ACTION_SEND && tmpIntent.hasExtra(Intent.EXTRA_STREAM)) {
                val newUri: Uri? = tmpIntent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

                if (newUri != null) {
                    mainViewModel.setCurrentUri(newUri)
                } else {
                    Toast.makeText(this, R.string.invalid_file, Toast.LENGTH_SHORT).show()
                }
            }
        }

        curr_image_choosed_edit_main.keyListener = null

        mainViewModel.restoreSavedData(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        if (outState != null) {
            mainViewModel.onSaveData(outState)
        }
    }

    override fun onNewIntent(newIntent: Intent?) {
        super.onNewIntent(newIntent)

        if (newIntent != null) {
            if (newIntent.action == Intent.ACTION_SEND && newIntent.hasExtra(Intent.EXTRA_STREAM)) {
                val newUri: Uri? = newIntent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

                if (newUri != null) {
                    mainViewModel.setCurrentUri(newUri)
                } else {
                    Toast.makeText(this, R.string.invalid_file, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val newUri: Uri? = data?.data

        if (resultCode == Activity.RESULT_OK && newUri != null) {
            mainViewModel.setCurrentUri(newUri)
        } else {
            Toast.makeText(this, R.string.invalid_file, Toast.LENGTH_SHORT).show()
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    fun chooseAnImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        try {
            startActivityForResult(intent, CHOOSE_IMAGE_REQUEST_CODE)
        } catch (e : Exception) {
            Toast.makeText(this, R.string.file_manager_not_found, Toast.LENGTH_LONG).show()
        }
    }

    fun startUploadCurrentImage() {
        val errorMessage: String? = mainViewModel.startUploadCurrentImage()

        if (errorMessage != null) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    fun copyLastImageUploadedLinkToClipboard() {
        val lastLink: String? = mainViewModel.lastImageUploadedInfo.value
        if (!lastLink.isNullOrEmpty() && lastLink.startsWith("http")) {
            putStringInClipboard(lastLink)
            Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.invalid_link, Toast.LENGTH_SHORT).show()
        }
    }
}
