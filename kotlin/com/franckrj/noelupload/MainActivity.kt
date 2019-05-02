package com.franckrj.noelupload

import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import android.provider.OpenableColumns
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    companion object {
        private const val CHOOSE_IMAGE_REQUEST_CODE : Int = 38

        private const val SAVE_LAST_IMAGE_CHOOSED_URI = "SAVE_LAST_IMAGE_CHOOSED_URI"
        private const val SAVE_LAST_IMAGE_UPLOADED_LINK = "SAVE_LAST_IMAGE_UPLOADED_LINK"
    }

    private var lastImageChoosedUri: MutableLiveData<Uri?> = MutableLiveData()
    private var lastImageUploadedLink: MutableLiveData<String?> = MutableLiveData()

    private fun putStringInClipboard(textToCopy: String) {
        val clipboard: ClipboardManager? = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

        if (clipboard != null) {
            val clip = ClipData.newPlainText(textToCopy, textToCopy)
            clipboard.primaryClip = clip
        }
    }

    private fun noelshackToDirectLink(baseLink: String): String {
        var link = baseLink
        if (link.contains("noelshack.com/")) {
            link = link.substring(link.indexOf("noelshack.com/") + 14)
        } else {
            return link
        }

        link = if (link.startsWith("fichiers/") || link.startsWith("fichiers-xs/") || link.startsWith("minis/")) {
            link.substring(link.indexOf("/") + 1)
        } else {
            link.replaceFirst("-", "/").replaceFirst("-", "/")
        }

        //moyen dégueulasse pour checker si le lien utilise le nouveau format (deux nombres entre l'année et le timestamp au lieu d'un)
        if (link.contains("/")) {
            var checkForNewStringType = link.substring(link.lastIndexOf("/") + 1)

            if (checkForNewStringType.contains("-")) {
                checkForNewStringType = checkForNewStringType.substring(0, checkForNewStringType.indexOf("-"))

                if (checkForNewStringType.matches("[0-9]{1,8}".toRegex())) {
                    link = link.replaceFirst("-", "/")
                }
            }
        }

        return "http://image.noelshack.com/fichiers/$link"
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val queryCursor: Cursor? = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            result = queryCursor?.use { cursor: Cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }
        }
        if (result == null) {
            result = uri.path
            if (result != null) {
                val cut = result.lastIndexOf('/')
                if (cut != -1) {
                    result = result.substring(cut + 1)
                }
            } else {
                result = uri.toString()
            }
        }
        return result
    }

    private fun uploadImage(fileContent: ByteArray, fileName: String, fileType: String): String {
        try {
            val mediaTypeForFile = MediaType.parse(fileType)
            val req = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("fichier", fileName, RequestBody.create(mediaTypeForFile, fileContent)).build()
            val request = Request.Builder()
                .url("http://www.noelshack.com/api.php")
                .post(req)
                .build()

            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
            val responseString: String? = client.newCall(request).execute().body()?.string()

            return if (responseString.isNullOrEmpty()) {
                getString(R.string.errorMessage, null.toString())
            } else {
                noelshackToDirectLink(responseString)
            }
        } catch (e: Exception) {
            return getString(R.string.errorMessage, e.toString())
        }
    }

    private fun startUploadThisImage(uri: Uri?) {
        if (uri != null) {
            GlobalScope.launch {
                try {
                    val result = contentResolver?.openInputStream(uri)?.use { inputStream ->
                        val tmpResult = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var length = 0
                        while ({ length = inputStream.read(buffer); length }() != -1) {
                            tmpResult.write(buffer, 0, length)
                        }
                        tmpResult
                    }

                    if (result != null) {
                        val uploadResponse: String = uploadImage(result.toByteArray(), getFileName(uri), contentResolver?.getType(uri) ?: "image/*")

                        withContext(Dispatchers.Main) {
                            lastImageUploadedLink.value = uploadResponse
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            lastImageUploadedLink.value = getString(R.string.invalid_file)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        lastImageUploadedLink.value = getString(R.string.errorMessage, e.toString())
                    }
                }
            }
        } else {
            Toast.makeText(this, R.string.invalid_file, Toast.LENGTH_SHORT).show()
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tmpIntent: Intent? = intent
        if (savedInstanceState == null && tmpIntent != null) {
            if (tmpIntent.action == Intent.ACTION_SEND && tmpIntent.hasExtra(Intent.EXTRA_STREAM)) {
                val newUri: Uri? = tmpIntent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

                if (newUri != null) {
                    lastImageChoosedUri.value = newUri
                } else {
                    Toast.makeText(this, R.string.invalid_file, Toast.LENGTH_SHORT).show()
                }
            }
        }

        lastImageChoosedUri.observe(this, Observer { newUri: Uri? ->
            last_image_choosed_edit_main.setText(getFileName(newUri ?: Uri.EMPTY))
        })

        lastImageUploadedLink.observe(this, Observer { newLink: String? ->
            link_of_last_image_uploaded_text_main.text = newLink ?: ""
        })

        last_image_choosed_edit_main.keyListener = null

        choose_image_button_main.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            try {
                startActivityForResult(intent, CHOOSE_IMAGE_REQUEST_CODE)
            } catch (e : Exception) {
                Toast.makeText(this, R.string.file_manager_not_found, Toast.LENGTH_LONG).show()
            }
        }

        upload_button_main.setOnClickListener {
            lastImageUploadedLink.value = ""
            startUploadThisImage(lastImageChoosedUri.value)
        }

        copy_last_image_uploaded_link_button_main.setOnClickListener {
            val lastLink: String? = lastImageUploadedLink.value
            if (!lastLink.isNullOrEmpty() && lastLink.startsWith("http")) {
                putStringInClipboard(lastLink)
                Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.invalid_link, Toast.LENGTH_SHORT).show()
            }
        }

        if (savedInstanceState != null) {
            lastImageChoosedUri.value = savedInstanceState.getParcelable(SAVE_LAST_IMAGE_CHOOSED_URI) as? Uri
            lastImageUploadedLink.value = savedInstanceState.getString(SAVE_LAST_IMAGE_UPLOADED_LINK, null)
        }
    }

    public override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.putParcelable(SAVE_LAST_IMAGE_CHOOSED_URI, lastImageChoosedUri.value)
        outState?.putString(SAVE_LAST_IMAGE_UPLOADED_LINK, lastImageUploadedLink.value)
    }

    public override fun onNewIntent(newIntent: Intent?) {
        super.onNewIntent(newIntent)

        if (newIntent != null) {
            if (newIntent.action == Intent.ACTION_SEND && newIntent.hasExtra(Intent.EXTRA_STREAM)) {
                val newUri: Uri? = newIntent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

                if (newUri != null) {
                    lastImageChoosedUri.value = newUri
                } else {
                    Toast.makeText(this, R.string.invalid_file, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data?.data != null) {
            lastImageChoosedUri.value = data.data
        } else {
            Toast.makeText(this, R.string.invalid_file, Toast.LENGTH_SHORT).show()
        }

        super.onActivityResult(requestCode, resultCode, data)
    }
}
