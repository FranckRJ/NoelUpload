package com.franckrj.noelupload.upload

import android.app.Application
import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
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
import com.franckrj.noelupload.R
import com.franckrj.noelupload.utils.Utils
import com.franckrj.noelupload.history.HistoryEntryRepository
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

//todo SaveStateHandle regarder où c'est ce que c'est etc
//todo mais est-ce vraiment utile de save des trucs si l'upload échoue à cause du manque de mémoire ?
/**
 * ViewModel contenant les diverses informations pour upload un fichier.
 */
class UploadViewModel(private val app: Application) : AndroidViewModel(app) {
    private val _historyEntryRepo: HistoryEntryRepository = HistoryEntryRepository.getInstance(app)
    private val _maxPreviewWidth: Int = app.resources.getDimensionPixelSize(R.dimen.maxPreviewWidth)
    private val _maxPreviewHeight: Int = app.resources.getDimensionPixelSize(R.dimen.maxPreviewHeight)
    private var _listOfCurrentTargets: MutableList<SaveToFileTarget> = mutableListOf()

    private var _isUploading: AtomicBoolean = AtomicBoolean(false)

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
    private fun uploadProgressChanged(bytesSended: Long, totalBytesToSend: Long, linkedUploadInfos: UploadInfos) {
        _historyEntryRepo.postUpdateThisUploadInfosStatus(
            linkedUploadInfos,
            UploadStatus.UPLOADING,
            ((bytesSended * 100) / totalBytesToSend).toString()
        )
    }

    /**
     * Upload l'image passée en paramètre sur noelshack et retourne la réponse du serveur ou throw si erreur.
     * La fonction doit être appelée dans un background thread.
     */
    @Suppress("RedundantSuspendModifier")
    private suspend fun uploadBitmapImage(fileContent: ByteArray, fileType: String, imageUploadInfos: UploadInfos): String {
        val mediaTypeForFile = MediaType.parse(fileType)
        val req = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart(
            "fichier",
            imageUploadInfos.imageName,
            ProgressRequestBody(mediaTypeForFile, fileContent, imageUploadInfos, ::uploadProgressChanged)
        ).build()
        val request = Request.Builder()
            .url("http://www.noelshack.com/api.php")
            .post(req)
            .build()

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        val responseString: String? = client.newCall(request).execute().body()?.string()

        if (responseString.isNullOrEmpty()) {
            throw Exception(null.toString())
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
                app.contentResolver.getType(imageUri) ?: "image/*",
                imageUploadInfos
            )

            if (Utils.checkIfItsANoelshackImageLink(uploadResponse)) {
                Utils.noelshackToDirectLink(uploadResponse)
            } else {
                throw Exception(uploadResponse)
            }
        } else {
            throw Exception(app.getString(R.string.fileIsInvalid))
        }
    }

    /**
     * Callback executé lorsqu'une miniature a terminée d'être construite.
     */
    private fun targetFinished(target: SaveToFileTarget, linkedUploadInfos: UploadInfos) {
        target.onFinishCallBack = null
        _listOfCurrentTargets.remove(target)

        _historyEntryRepo.postUpdateThisUploadInfosPreview(linkedUploadInfos)

        //todo c'est potentiellement pas propre, checker pour faire mieux ?
        viewModelScope.launch {
            delay(10)
            Glide.with(app).clear(target)
        }
    }

    override fun onCleared() {
        for (target in _listOfCurrentTargets) {
            target.onFinishCallBack = null
        }
        _listOfCurrentTargets.clear()
        super.onCleared()
    }

    /**
     * Initialise des trucs pour ajouter l'image pointée par [newImageUri] à l'historique et lance son upload.
     * Retourne true si l'upload a commencé, false si un upload était déjà en cours.
     */
    fun startUploadThisImage(newImageUri: Uri): Boolean {
        if (!_isUploading.getAndSet(true)) {
            viewModelScope.launch(Dispatchers.IO) {
                val newUploadInfos = UploadInfos(
                    "",
                    getFileName(newImageUri),
                    newImageUri.path.orEmpty(),
                    System.currentTimeMillis()
                )

                _historyEntryRepo.postAddThisUploadInfos(newUploadInfos)

                withContext(Dispatchers.Main) {
                    _listOfCurrentTargets.add(
                        Glide.with(app)
                            .asBitmap()
                            .load(newImageUri)
                            .into(
                                SaveToFileTarget(
                                    "${app.filesDir.path}/${newUploadInfos.uploadTimeInMs}-${newUploadInfos.imageName}",
                                    _maxPreviewWidth,
                                    _maxPreviewHeight,
                                    newUploadInfos,
                                    ::targetFinished
                                )
                            )
                    )
                }

                try {
                    val linkOfImage = uploadThisImage(newImageUri, newUploadInfos)
                    _historyEntryRepo.postUpdateThisUploadInfosLinkAndSetFinished(newUploadInfos, linkOfImage)
                } catch (e: Exception) {
                    _historyEntryRepo.postUpdateThisUploadInfosStatus(
                        newUploadInfos,
                        UploadStatus.ERROR,
                        e.message.toString()
                    )
                } finally {
                    _isUploading.set(false)
                }
            }

            return true
        } else {
            return false
        }
    }
}
