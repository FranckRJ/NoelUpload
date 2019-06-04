package com.franckrj.noelupload.upload

import android.app.Application
import android.location.Location
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.franckrj.noelupload.R
import com.franckrj.noelupload.history.HistoryEntryRepository
import com.franckrj.noelupload.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

//todo SaveStateHandle regarder où c'est ce que c'est etc
//todo mais est-ce vraiment utile de save des trucs si l'upload échoue à cause du manque de mémoire ?
/**
 * ViewModel contenant les diverses informations pour upload un fichier.
 */
class UploadViewModel(private val app: Application) : AndroidViewModel(app) {
    private val _historyEntryRepo: HistoryEntryRepository = HistoryEntryRepository.instance
    private val _maxPreviewWidth: Int = app.resources.getDimensionPixelSize(R.dimen.maxPreviewWidth)
    private val _maxPreviewHeight: Int = app.resources.getDimensionPixelSize(R.dimen.maxPreviewHeight)

    private var _isUploading: AtomicBoolean = AtomicBoolean(false)

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
    private suspend fun uploadBitmapImage(fileContent: ByteArray, fileType: String, uploadInfos: UploadInfos): String {
        val mediaTypeForFile = MediaType.parse(fileType)
        val req = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart(
            "fichier",
            uploadInfos.imageName,
            ProgressRequestBody(mediaTypeForFile, fileContent, uploadInfos, ::uploadProgressChanged)
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
    private suspend fun uploadThisImage(imageFile: File, fileType: String?, uploadInfos: UploadInfos): String {
        val fileContent = FileUriUtils.readFileContent(imageFile)

        return if (fileContent != null) {
            val uploadResponse: String = uploadBitmapImage(
                fileContent.toByteArray(),
                fileType ?: "image/*",
                uploadInfos
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
     * Lance dans un background thread la création d'une miniature pour l'image représentée par [baseUri] et [uploadInfos].
     * Met à jour la DB lorsque la création de la preview est terminée.
     */
    private suspend fun postCreatePreviewForThisUploadInfos(baseUri: Uri, uploadInfos: UploadInfos) =
        viewModelScope.launch(Dispatchers.IO) {
            if (FileUriUtils.savePreviewOfUriToFile(
                    baseUri,
                    _historyEntryRepo.getPreviewFileFromUploadInfos(uploadInfos),
                    (_maxPreviewWidth * 1.5).roundToInt(),
                    (_maxPreviewHeight * 1.5).roundToInt(),
                    app
                )
            ) {
                _historyEntryRepo.postUpdateThisUploadInfosPreview(uploadInfos)
            }
        }

    /**
     * Retourne une copie du fichier représenté par [baseUri]. Si cette copie possède des données EXIF les tags de GPS
     * seront supprimés, throw en cas d'erreur de création de la copie.
     * La copie est sauvegardées dans le cache, elle doit être supprimée après utilisation.
     */
    private suspend fun createCachedFileForUpload(baseUri: Uri, uploadInfos: UploadInfos): File =
        withContext(Dispatchers.IO) {
            val cachedFile =
                File("${app.cacheDir.path}/file-${uploadInfos.uploadTimeInMs}-${Utils.uriToFileName(uploadInfos.imageUri)}.nop")

            if (!FileUriUtils.saveUriContentToFile(baseUri, cachedFile, app)) {
                throw Exception(app.getString(R.string.errorUploadFailed))
            }

            try {
                val exifInterface = ExifInterface(cachedFile)
                exifInterface.setGpsInfo(Location(""))
                exifInterface.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
                exifInterface.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, null)
                exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
                exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
                exifInterface.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)
                exifInterface.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, null)
                exifInterface.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, null)
                exifInterface.setAttribute(ExifInterface.TAG_GPS_AREA_INFORMATION, null)
                exifInterface.setAttribute(ExifInterface.TAG_GPS_DEST_BEARING, null)
                exifInterface.setAttribute(ExifInterface.TAG_GPS_DEST_BEARING_REF, null)
                exifInterface.setAttribute(ExifInterface.TAG_GPS_DEST_DISTANCE, null)
                exifInterface.setAttribute(ExifInterface.TAG_GPS_DEST_DISTANCE_REF, null)
                exifInterface.setAttribute(ExifInterface.TAG_GPS_DEST_LATITUDE, null)
                exifInterface.setAttribute(ExifInterface.TAG_GPS_DEST_LATITUDE_REF, null)
                exifInterface.setAttribute(ExifInterface.TAG_GPS_DEST_LONGITUDE, null)
                exifInterface.setAttribute(ExifInterface.TAG_GPS_DEST_LONGITUDE_REF, null)
                exifInterface.saveAttributes()
            } catch (e: Exception) {
                /* Le fichier ne supporte pas les EXIF c'est pas grave. */
            }

            cachedFile
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
                    FileUriUtils.getFileName(newImageUri, app),
                    newImageUri.toString(),
                    System.currentTimeMillis()
                )

                _historyEntryRepo.blockAddThisUploadInfos(newUploadInfos)
                postCreatePreviewForThisUploadInfos(newImageUri, newUploadInfos)

                try {
                    val fileToUpload = createCachedFileForUpload(newImageUri, newUploadInfos)
                    val linkOfImage =
                        uploadThisImage(fileToUpload, app.contentResolver.getType(newImageUri), newUploadInfos)
                    _historyEntryRepo.postUpdateThisUploadInfosLinkAndSetFinished(newUploadInfos, linkOfImage)
                    fileToUpload.delete()
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
