package com.franckrj.noelupload.upload

import android.app.Application
import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import com.bumptech.glide.Glide
import com.franckrj.noelupload.AppDatabase
import com.franckrj.noelupload.R
import com.franckrj.noelupload.Utils

//todo SaveStateHandle regarder où c'est ce que c'est etc
/**
 * ViewModel contenant les diverses informations pour upload un fichier.
 */
class UploadViewModel(private val app: Application) : AndroidViewModel(app) {
    companion object {
        private const val SAVE_LAST_IMAGE_CHOOSED_URI = "SAVE_LAST_IMAGE_CHOOSED_URI"
        private const val SAVE_LAST_IMAGE_UPLOADED_INFO = "SAVE_LAST_IMAGE_UPLOADED_INFO"
    }

    private val _uploadInfosDao: UploadInfosDao = AppDatabase.instance.uploadInfosDao()
    private val _maxPreviewWidth: Int = app.resources.getDimensionPixelSize(R.dimen.maxPreviewWidth)
    private val _maxPreviewHeight: Int = app.resources.getDimensionPixelSize(R.dimen.maxPreviewHeight)
    private var _firstTimeRestoreIsCalled: Boolean = true
    private var _isInUpload: Boolean = false

    private val _currImageChoosedUri: MutableLiveData<Uri?> = MutableLiveData()
    private val _lastImageUploadedInfo: MutableLiveData<String?> = MutableLiveData()

    val currImageChoosedUri: LiveData<Uri?> = _currImageChoosedUri
    val currImageChoosedName: LiveData<String?> = currImageChoosedUri.map {
        getFileName(it ?: Uri.EMPTY)
    }
    val lastImageUploadedInfo: LiveData<String?> = _lastImageUploadedInfo

    /**
     * Retourne le nom du fichier pointé par [uri] via le [ContentResolver]. S'il n'est pas trouvé retourne
     * simplement la dernière partie de [uri].
     */
    private fun getFileName(uri: Uri): String {
        var result: String? = null

        if (uri.scheme == "content") {
            val queryCursor: Cursor? = app.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
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

    /**
     * Retourne un [ByteArrayOutputStream] avec le contenu de l'[uriToRead], ou null en cas d'erreur.
     */
    private suspend fun readUriContent(uriToRead: Uri): ByteArrayOutputStream? = withContext(Dispatchers.IO) {
        try {
            app.contentResolver.openInputStream(uriToRead)?.use { inputStream ->
                val streamResult = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var length = inputStream.read(buffer)
                while (length != -1) {
                    streamResult.write(buffer, 0, length)
                    length = inputStream.read(buffer)
                }
                streamResult
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Callback appelé lorsque la progression de l'upload a changé, change le message d'information de l'upload par
     * le pourcentage de progression de la requête. La fonction executera toujours les modifications de l'UI
     * dans le main thread.
     */
    private fun uploadProgressChanged(bytesSended: Long, totalBytesToSend: Long) = viewModelScope.launch {
        withContext(Dispatchers.Main) {
            _lastImageUploadedInfo.value = app.getString(
                R.string.uploadProgress,
                ((bytesSended * 100) / totalBytesToSend).toString()
            )
        }
    }

    /**
     * Upload l'image passée en paramètre sur noelshack et retourne la réponse du serveur ou une erreur.
     * La fonction doit être appelée dans un background thread.
     */
    private fun uploadImage(fileContent: ByteArray, fileName: String, fileType: String): String {
        try {
            val mediaTypeForFile = MediaType.parse(fileType)
            val req = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart(
                "fichier",
                fileName,
                ProgressRequestBody(mediaTypeForFile, fileContent, ::uploadProgressChanged)
            ).build()
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
                app.getString(R.string.errorMessage, null.toString())
            } else {
                responseString
            }
        } catch (e: Exception) {
            return app.getString(R.string.errorMessage, e.toString())
        }
    }

    /**
     * Set certaines informations après qu'un upload ai terminé. La fonction executera toujours les modifications
     * de l'UI dans le main thread.
     */
    private suspend fun updateInfosAfterImageUploadEnded(newUploadImageInfo: String) = withContext(Dispatchers.Main) {
        _lastImageUploadedInfo.value = if (Utils.checkIfItsANoelshackImageLink(newUploadImageInfo)) {
            Utils.noelshackToDirectLink(newUploadImageInfo)
        } else {
            newUploadImageInfo
        }
        _isInUpload = false
    }

    /**
     * Ajoute une entrée [UploadInfos] dans l'historique des uploads et créé une preview pour l'affichage de l'historique.
     */
    private suspend fun addUploadInfosToHistoryAndBuildPreview(
        newUploadImageInfo: String,
        imageUploadedName: String,
        imageUploadUri: Uri
    ) =
        withContext(Dispatchers.IO) {
            if (Utils.checkIfItsANoelshackImageLink(newUploadImageInfo)) {
                val currUploadInfos = UploadInfos(
                    newUploadImageInfo,
                    imageUploadedName,
                    System.currentTimeMillis()
                )
                _uploadInfosDao.insertUploadInfos(currUploadInfos)
                Glide.with(app)
                    .asBitmap()
                    .load(imageUploadUri)
                    .into(
                        SaveToFileTarget(
                            "${app.filesDir.path}/${currUploadInfos.uploadTimeInMs}-${currUploadInfos.imageName}",
                            _maxPreviewWidth,
                            _maxPreviewHeight
                        )
                    )
            }
        }

    /**
     * Restaure la sauvegarde du [savedInstanceState] si nécessaire.
     */
    fun restoreSavedData(savedInstanceState: Bundle?) {
        if (savedInstanceState != null && _firstTimeRestoreIsCalled) {
            _currImageChoosedUri.value = savedInstanceState.getParcelable(SAVE_LAST_IMAGE_CHOOSED_URI) as? Uri
            _lastImageUploadedInfo.value = savedInstanceState.getString(SAVE_LAST_IMAGE_UPLOADED_INFO, null)
        }
        _firstTimeRestoreIsCalled = false
    }

    /**
     * Sauvegarde les informations necessaires pour le [UploadViewModel] dans le [outState].
     */
    fun onSaveData(outState: Bundle) {
        outState.putParcelable(SAVE_LAST_IMAGE_CHOOSED_URI, _currImageChoosedUri.value)
        outState.putString(SAVE_LAST_IMAGE_UPLOADED_INFO, _lastImageUploadedInfo.value)
    }

    /**
     * Set l'uri vers le fichier actuellement sélectionné.
     */
    fun setCurrentUri(newUri: Uri) {
        _currImageChoosedUri.value = newUri
        _lastImageUploadedInfo.value = ""
    }

    /**
     * Commence à upload l'image sélectionnée, retourne null en cas de succès et un message d'erreur en cas d'erreur.
     */
    fun startUploadCurrentImage(): String? {
        val uri: Uri? = _currImageChoosedUri.value

        _lastImageUploadedInfo.value = ""
        if (uri != null) {
            if (!_isInUpload) {
                _isInUpload = true

                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val fileContent = readUriContent(uri)
                        val fileName = getFileName(uri)

                        if (fileContent != null) {
                            val uploadResponse: String = uploadImage(
                                fileContent.toByteArray(),
                                fileName,
                                app.contentResolver.getType(uri) ?: "image/*"
                            )

                            updateInfosAfterImageUploadEnded(uploadResponse)
                            addUploadInfosToHistoryAndBuildPreview(uploadResponse, fileName, uri)
                        } else {
                            updateInfosAfterImageUploadEnded(app.getString(R.string.invalid_file))
                        }
                    } catch (e: Exception) {
                        updateInfosAfterImageUploadEnded(app.getString(R.string.errorMessage, e.toString()))
                    }
                }
                return null
            } else {
                return app.getString(R.string.upload_already_running)
            }
        } else {
            return app.getString(R.string.invalid_file)
        }
    }
}
