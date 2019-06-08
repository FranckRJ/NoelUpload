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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

//todo SaveStateHandle regarder où c'est ce que c'est etc
//todo sauvegarder la liste des images à upload
/**
 * ViewModel contenant les diverses informations pour upload un fichier.
 */
class UploadViewModel(private val app: Application) : AndroidViewModel(app) {
    private val _historyEntryRepo: HistoryEntryRepository = HistoryEntryRepository.instance
    private val _maxPreviewWidth: Int = app.resources.getDimensionPixelSize(R.dimen.maxPreviewWidth)
    private val _maxPreviewHeight: Int = app.resources.getDimensionPixelSize(R.dimen.maxPreviewHeight)
    private val _listOfFilesToUpload: MutableList<UploadInfos> = mutableListOf()
    private val _isUploading: AtomicBoolean = AtomicBoolean(false)
    private val _nbOfPendingsAddToUploadList: AtomicInteger = AtomicInteger(0)

    /**
     * Retourne le fichier servant de cache pour l'[uploadInfos].
     */
    private fun getUploadInfoCachedFile(uploadInfos: UploadInfos): File {
        return File("${app.cacheDir.path}/file-${uploadInfos.uploadTimeInMs}-${Utils.uriToFileName(uploadInfos.imageUri)}.nop")
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
     * Lance dans un background thread la création d'une miniature pour l'image représentée par [uploadInfos].
     * Met à jour la DB lorsque la création de la preview est terminée.
     */
    private suspend fun postCreatePreviewForThisUploadInfos(uploadInfos: UploadInfos) =
        viewModelScope.launch(Dispatchers.IO) {
            if (FileUriUtils.savePreviewOfUriToFile(
                    Uri.parse(uploadInfos.imageUri),
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
     * Créé une copie du fichier représenté par [uploadInfos]. Si cette copie possède des données EXIF les tags de GPS
     * seront supprimés, throw en cas d'erreur de création de la copie.
     * La copie est sauvegardées dans le cache, elle doit être supprimée après utilisation.
     */
    private suspend fun createCachedFileForUpload(uploadInfos: UploadInfos) = withContext(Dispatchers.IO) {
        val cachedFile = getUploadInfoCachedFile(uploadInfos)

        if (!FileUriUtils.saveUriContentToFile(Uri.parse(uploadInfos.imageUri), cachedFile, app)) {
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
    }

    /**
     * Fonction appelée lorsqu'un upload se termine. Elle s'occupe de le supprimer de la liste des uploads, de lancer
     * le prochain upload si nécessaire et de màj l'historique. Si [uploadStatus] vaut [UploadStatus.FINISHED] alors
     * l'historique sera màj avec le lien contenu dans [uploadStatusMessage], sinon s'il vaut [UploadStatus.ERROR] alors
     * l'historique sera màj avec l'erreur contenue dans [uploadStatusMessage].
     */
    private suspend fun uploadOfAnImageEnded(
        uploadInfos: UploadInfos,
        uploadStatus: UploadStatus,
        uploadStatusMessage: String
    ) = withContext(Dispatchers.Main) {
        _listOfFilesToUpload.remove(uploadInfos)

        if (uploadStatus == UploadStatus.FINISHED) {
            _historyEntryRepo.postUpdateThisUploadInfosLinkAndSetFinished(uploadInfos, uploadStatusMessage)
        } else if (uploadStatus == UploadStatus.ERROR) {
            _historyEntryRepo.postUpdateThisUploadInfosStatus(uploadInfos, UploadStatus.ERROR, uploadStatusMessage)
        }

        if (_listOfFilesToUpload.isNotEmpty()) {
            startUploadThisImage(_listOfFilesToUpload.first())
        }
    }

    /**
     * Upload l'image représentée par [uploadInfos].
     * Retourne true si l'upload a commencé, false si un upload était déjà en cours.
     */
    private fun startUploadThisImage(uploadInfos: UploadInfos): Boolean {
        if (!_isUploading.getAndSet(true)) {
            viewModelScope.launch(Dispatchers.IO) {
                var uploadStatus = UploadStatus.UPLOADING
                var uploadStatusMessage = ""

                try {
                    val fileToUpload = getUploadInfoCachedFile(uploadInfos)
                    if (fileToUpload.exists()) {
                        val linkOfImage =
                            uploadThisImage(
                                fileToUpload,
                                app.contentResolver.getType(Uri.parse(uploadInfos.imageUri)),
                                uploadInfos
                            )
                        uploadStatus = UploadStatus.FINISHED
                        uploadStatusMessage = linkOfImage
                        fileToUpload.delete()
                    } else {
                        throw Exception(app.getString(R.string.errorUploadFailed))
                    }
                } catch (e: Exception) {
                    uploadStatus = UploadStatus.ERROR
                    uploadStatusMessage = e.message.toString()
                } finally {
                    _isUploading.set(false)
                    uploadOfAnImageEnded(uploadInfos, uploadStatus, uploadStatusMessage)
                }
            }

            return true
        } else {
            return false
        }
    }

    /**
     * Ajoute l'image représentée par [newImageUri] à la liste des uploads et commence à uploader ces images.
     */
    fun addFileToListOfFilesToUploadAndStartUpload(newImageUri: Uri) {
        _nbOfPendingsAddToUploadList.incrementAndGet()

        viewModelScope.launch(Dispatchers.Main) {
            val newUploadInfos = UploadInfos(
                "",
                FileUriUtils.getFileName(newImageUri, app),
                newImageUri.toString(),
                System.currentTimeMillis()
            )

            _historyEntryRepo.blockAddThisUploadInfos(newUploadInfos)
            postCreatePreviewForThisUploadInfos(newUploadInfos)
            createCachedFileForUpload(newUploadInfos)
            _listOfFilesToUpload.add(newUploadInfos)
            _nbOfPendingsAddToUploadList.decrementAndGet()
            startUploadThisImage(newUploadInfos)
        }
    }

    /**
     * Retourne true si plus aucune image ne doit être upload, faux sinon.
     */
    fun uploadListIsEmpty(): Boolean {
        return (_listOfFilesToUpload.isEmpty() && _nbOfPendingsAddToUploadList.get() == 0)
    }
}
