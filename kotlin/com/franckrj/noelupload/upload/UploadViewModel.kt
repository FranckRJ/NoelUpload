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
        private const val SAVE_UPLOAD_INFOS = "SAVE_UPLOAD_INFOS"
        private const val SAVE_UPLOAD_STATUS_INFOS = "SAVE_UPLOAD_STATUS_INFOS"
    }

    private val _uploadInfosDao: UploadInfosDao = AppDatabase.instance.uploadInfosDao()
    private val _maxPreviewWidth: Int = app.resources.getDimensionPixelSize(R.dimen.maxPreviewWidth)
    private val _maxPreviewHeight: Int = app.resources.getDimensionPixelSize(R.dimen.maxPreviewHeight)
    private var _firstTimeRestoreIsCalled: Boolean = true

    private val _currUploadInfos: MutableLiveData<UploadInfos?> = MutableLiveData()
    private val _currUploadStatusInfos: MutableLiveData<UploadStatusInfos?> =
        MutableLiveData(UploadStatusInfos(UploadStatus.FINISHED))

    val currUploadInfos: LiveData<UploadInfos?> = _currUploadInfos
    val currUploadStatusInfos: LiveData<UploadStatusInfos?> = _currUploadStatusInfos

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
     * Callback appelé lorsque la progression de l'upload a changé, change le statut de l'upload par le pourcentage
     * de progression de la requête.
     */
    private fun uploadProgressChanged(bytesSended: Long, totalBytesToSend: Long) = viewModelScope.launch {
        //todo checker si ça peut pas override un postValue(FINISHED) (ou ERROR) par inadvertance.
        _currUploadStatusInfos.postValue(
            UploadStatusInfos(
                UploadStatus.UPLOADING,
                ((bytesSended * 100) / totalBytesToSend).toString()
            )
        )
    }

    /**
     * Upload l'image passée en paramètre sur noelshack et retourne la réponse du serveur ou throw si erreur.
     * La fonction doit être appelée dans un background thread.
     */
    @Suppress("RedundantSuspendModifier")
    private suspend fun uploadBitmapImage(fileContent: ByteArray, fileName: String, fileType: String): String {
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

        if (responseString.isNullOrEmpty()) {
            throw Exception(app.getString(R.string.errorMessage, null.toString()))
        } else {
            return responseString
        }
    }

    /**
     * Upload l'image passée en paramètre et retourne son lien noelshack ou throw en cas d'erreur.
     */
    private suspend fun uploadThisImage(imageUri: Uri, imageUploadInfos: UploadInfos): String {
        val fileContent = readUriContent(imageUri)

        return if (fileContent != null) {
            val uploadResponse: String = uploadBitmapImage(
                fileContent.toByteArray(),
                imageUploadInfos.imageName,
                app.contentResolver.getType(imageUri) ?: "image/*"
            )

            if (Utils.checkIfItsANoelshackImageLink(uploadResponse)) {
                Utils.noelshackToDirectLink(uploadResponse)
            } else {
                throw Exception(uploadResponse)
            }
        } else {
            throw Exception(app.getString(R.string.invalid_file))
        }
    }

    /**
     * Restaure la sauvegarde du [savedInstanceState] si nécessaire.
     */
    fun restoreSavedData(savedInstanceState: Bundle?) {
        if (savedInstanceState != null && _firstTimeRestoreIsCalled) {
            //todo gérer la sauvegarde / restauration
            /*_currUploadInfos.value = savedInstanceState.getParcelable(SAVE_UPLOAD_INFOS) as? UploadInfos
            _currUploadStatusInfos.value = savedInstanceState.getParcelable(SAVE_UPLOAD_STATUS_INFOS) as? UploadStatusInfos*/
        }
        _firstTimeRestoreIsCalled = false
    }

    /**
     * Sauvegarde les informations necessaires pour le [UploadViewModel] dans le [outState].
     */
    fun onSaveData(outState: Bundle) {
        //todo gérer la sauvegarde / restauration
        /*outState.putParcelable(SAVE_UPLOAD_INFOS, _currUploadInfos.value)
        outState.putParcelable(SAVE_UPLOAD_STATUS_INFOS, _currUploadStatusInfos.value)*/
    }

    /**
     * Initialise des trucs pour ajouter l'image pointée par [newImageUri] à l'historique et lance son upload.
     * Retourne true si l'upload a commencé, false si un upload était déjà en cours.
     */
    fun startUploadThisImage(newImageUri: Uri): Boolean {
        if (_currUploadStatusInfos.value?.status == UploadStatus.FINISHED) {
            _currUploadStatusInfos.value = (UploadStatusInfos(UploadStatus.UPLOADING, "0"))

            viewModelScope.launch(Dispatchers.IO) {
                var newUploadInfos = UploadInfos(
                    "",
                    getFileName(newImageUri),
                    System.currentTimeMillis()
                )
                val newRowIdForUploadInfos: Long = _uploadInfosDao.insertUploadInfos(newUploadInfos)

                //todo mieux gérer la non-présence de la miniature au début, un placeholder puis refresh dès qu'elle est dispo
                Glide.with(app)
                    .asBitmap()
                    .load(newImageUri)
                    .into(
                        SaveToFileTarget(
                            "${app.filesDir.path}/${newUploadInfos.uploadTimeInMs}-${newUploadInfos.imageName}",
                            _maxPreviewWidth,
                            _maxPreviewHeight
                        )
                    )

                newUploadInfos = _uploadInfosDao.findByRowId(newRowIdForUploadInfos)!!
                _currUploadInfos.postValue(newUploadInfos)

                try {
                    val linkOfImage = uploadThisImage(newImageUri, newUploadInfos)
                    newUploadInfos = UploadInfos(
                        linkOfImage,
                        newUploadInfos.imageName,
                        newUploadInfos.uploadTimeInMs,
                        newUploadInfos.id
                    )

                    _uploadInfosDao.insertUploadInfos(newUploadInfos)
                    _currUploadInfos.postValue(newUploadInfos)
                    _currUploadStatusInfos.postValue(UploadStatusInfos(UploadStatus.FINISHED))
                } catch (e: Exception) {
                    _currUploadStatusInfos.postValue(UploadStatusInfos(UploadStatus.ERROR, e.message.orEmpty()))
                }
            }

            return true
        } else {
            return false
        }
    }
}
