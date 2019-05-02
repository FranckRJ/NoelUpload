package com.franckrj.noelupload

import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.widget.Toast
import android.util.Log
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

    private suspend fun uploadImage(fileContent: ByteArray, fileName: String, fileType: String): String? {
        try {
            val mediaTypeForFile = MediaType.parse(fileType)

            Log.d("uploadinfos", "Upload request building...")

            val req = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("fichier", fileName, RequestBody.create(mediaTypeForFile, fileContent)).build()

            val request = Request.Builder()
                .url("http://www.noelshack.com/api.php")
                .post(req)
                .build()

            Log.d("uploadinfos", "Upload request uploading...")

            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
            val response = client.newCall(request).execute()
            val responseString: String = response.body()?.string() ?: ""

            Log.d("uploadinfos", "Upload response : $responseString")
            withContext(Dispatchers.Main) {
                lastImageUploadedLink.value = responseString
            }
        } catch (e: Exception) {
            Log.d("uploadinfos", "Error : $e")
        }

        return null
    }

    private fun startUploadThisImage(uri: Uri?) {
        Log.d("uploadinfos", "Upload filepath : $uri")

        if (uri != null) {
            GlobalScope.launch {
                try {
                    Log.d("uploadinfos", "Upload filename : " + getFileName(uri))
                    Log.d("uploadinfos", "Upload filetype : " + contentResolver?.getType(uri))

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
                        uploadImage(
                            result.toByteArray(),
                            getFileName(uri),
                            contentResolver?.getType(uri) ?: "image/*"
                        )
                    } else {
                        Log.d("uploadinfos", "GlobError : lolresultnull")
                    }
                } catch (e: Exception) {
                    Log.d("uploadinfos", "GlobError : $e")
                }
            }
        } else {
            Log.d("uploadinfos", "GlobError : lolurinull")
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tmpIntent: Intent? = intent
        if (savedInstanceState == null && tmpIntent != null) {
            if (tmpIntent.action == Intent.ACTION_SEND) {
                Log.d("uploadinfos", "From start intent")
            }
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
            last_image_choosed_text_main.text = getFileName(newUri ?: Uri.EMPTY)
        })

        lastImageUploadedLink.observe(this, Observer { newLink: String? ->
            link_of_last_image_uploaded_text_main.text = newLink ?: ""
        })

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
            startUploadThisImage(lastImageChoosedUri.value)
        }

        copy_last_image_uploaded_link_button_main.setOnClickListener {
            val lastLink: String? = lastImageUploadedLink.value
            if (!lastLink.isNullOrEmpty()) {
                putStringInClipboard(lastLink)
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

        Log.d("uploadinfos", "From new intent")
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
        Log.d("uploadinfos", "From activity result")
        if (resultCode == Activity.RESULT_OK && data?.data != null) {
            lastImageChoosedUri.value = data.data
        } else {
            Toast.makeText(this, R.string.invalid_file, Toast.LENGTH_SHORT).show()
        }

        super.onActivityResult(requestCode, resultCode, data)
    }
}
