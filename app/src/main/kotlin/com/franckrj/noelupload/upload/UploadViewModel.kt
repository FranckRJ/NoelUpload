package com.franckrj.noelupload.upload

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.franckrj.noelupload.R
import com.franckrj.noelupload.history.HistoryEntryRepository
import com.franckrj.noelupload.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

//todo SaveStateHandle regarder où c'est ce que c'est etc
//todo sauvegarder la liste des images à upload
/**
 * ViewModel contenant les diverses informations pour upload un fichier.
 */
class UploadViewModel(private val app: Application) : AndroidViewModel(app) {
    companion object {
        private const val MAX_NUMBER_OF_UPLOADS_IN_SHORT_TIME: Int = 5
        private const val SHORT_TIME_DURATION_IN_MS: Long = 5_000
    }

    private val _historyEntryRepo: HistoryEntryRepository = HistoryEntryRepository.instance
    private val _maxPreviewWidth: Int = app.resources.getDimensionPixelSize(R.dimen.maxPreviewWidth)
    private val _maxPreviewHeight: Int = app.resources.getDimensionPixelSize(R.dimen.maxPreviewHeight)
    private val _listOfFilesToUpload: MutableList<UploadInfos> = mutableListOf()
    private val _isUploading: AtomicBoolean = AtomicBoolean(false)
    private val _nbOfPendingsAddToUploadList: AtomicInteger = AtomicInteger(0)
    private val _listOfLastUploadsTimeInMs: MutableList<Long> = mutableListOf()

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
    private suspend fun uploadBitmapImage(fileContent: ByteArray, fileType: String, uploadInfos: UploadInfos): String =
        withContext<String>(Dispatchers.IO) {
            val mediaTypeForFile: MediaType? = fileType.toMediaTypeOrNull()
            val req = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart(
                "fichier",
                uploadInfos.imageName,
                ProgressRequestBody(mediaTypeForFile, fileContent, uploadInfos, ::uploadProgressChanged)
            ).build()
            val request = Request.Builder()
                .url("https://www.noelshack.com/api.php")
                .post(req)
                .build()

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
            val responseString: String? = client.newCall(request).execute().body?.string()

            if (responseString.isNullOrEmpty()) {
                throw Exception(null.toString())
            } else {
                return@withContext responseString
            }
        }

    /**
     * Upload l'image passée en paramètre et retourne son lien noelshack ou throw en cas d'erreur.
     */
    private suspend fun uploadThisImage(imageFile: File, fileType: String?, uploadInfos: UploadInfos): String {
        val fileContent = FileUriUtils.readFileContent(imageFile)

        if (fileContent != null) {
            val uploadResponse: String = uploadBitmapImage(
                fileContent.toByteArray(),
                fileType ?: "image/*",
                uploadInfos
            )

            if (Utils.checkIfItsANoelshackImageLink(uploadResponse)) {
                return Utils.noelshackToDirectLink(uploadResponse)
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
                    (_maxPreviewWidth * 2),
                    (_maxPreviewHeight * 2),
                    app
                )
            ) {
                _historyEntryRepo.postUpdateThisUploadInfosPreview(uploadInfos)
            }
        }

    /**
     * Supprime les tag EXIF lié au GPS sur une image.
     */
    private suspend fun removeExifGpsTagFromImageFile(imageFile: File) = withContext(Dispatchers.IO) {
        try {
            val exifInterface = ExifInterface(imageFile)
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
     * Crée une nouvelle image avec le ratio JPEG précisé et dont les dimensions sont divisées par [dimReduceFactor].
     */
    private suspend fun compressImageFileWithParam(
        sourceImage: File,
        destImage: File,
        jpegRatio: Int,
        dimReduceFactor: Int
    ) = withContext(Dispatchers.IO) {
        val bitmapOption = BitmapFactory.Options()
        bitmapOption.inSampleSize = dimReduceFactor
        val bitmap = BitmapFactory.decodeFile(sourceImage.path, bitmapOption)!!
        FileOutputStream(destImage).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, jpegRatio, outputStream)
        }
    }

    /**
     * Créé une copie du fichier représenté par [uploadInfos]. Si cette copie possède des données EXIF les tags de GPS
     * seront supprimés, throw en cas d'erreur de création de la copie.
     * La copie est sauvegardées dans le cache, elle doit être supprimée après utilisation.
     */
    private suspend fun createCachedFileForUpload(uploadInfos: UploadInfos) = withContext(Dispatchers.IO) {
        val cachedFile = _historyEntryRepo.getCachedFileFromUploadInfo(uploadInfos)

        if (!FileUriUtils.saveUriContentToFile(Uri.parse(uploadInfos.imageUri), cachedFile, app)) {
            /* Le fichier ne sera pas créé donc l'upload ratera. */
            return@withContext
        }

        val rotationAngle = FileUriUtils.getRotationAngleFromExif(cachedFile)

        var imageIsCompressed = false
        val maxImageSize = 4_000_000
        val jpegRatio = 80
        if (cachedFile.length() > maxImageSize) {
            val compressedCachedFile = File(cachedFile.path + ".resized.tmp")
            var dimReduceFactor = 1
            compressImageFileWithParam(cachedFile, compressedCachedFile, jpegRatio, dimReduceFactor)
            imageIsCompressed = true

            while (compressedCachedFile.length() > maxImageSize) {
                dimReduceFactor += 1
                compressImageFileWithParam(cachedFile, compressedCachedFile, jpegRatio, dimReduceFactor)
            }

            cachedFile.delete()
            compressedCachedFile.renameTo(cachedFile)
        }

        if (!imageIsCompressed && rotationAngle == 0f) {
            removeExifGpsTagFromImageFile(cachedFile)
        } else if (rotationAngle != 0f) {
            val bitmap = BitmapFactory.decodeFile(cachedFile.path)
            val rotatedBitmap = FileUriUtils.rotateBitmap(bitmap, rotationAngle)
            // TODO: Si l'image était un PNG, la compresser en PNG à la place.
            FileOutputStream(cachedFile).use { outputStream ->
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, jpegRatio, outputStream)
            }
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
     * Met en pause la coroutine pour qu'il n'y ai pas plus de [MAX_NUMBER_OF_UPLOADS_IN_SHORT_TIME] uploads
     * en [SHORT_TIME_DURATION_IN_MS] ms. Met aussi à jour [_listOfLastUploadsTimeInMs] avec les nouveaux temps d'upload
     * des [MAX_NUMBER_OF_UPLOADS_IN_SHORT_TIME] derniers uploads.
     */
    private suspend fun waitForUploadIfNeededAndUpdateListOfLastUploadsTime() {
        if (_listOfLastUploadsTimeInMs.size >= MAX_NUMBER_OF_UPLOADS_IN_SHORT_TIME) {
            val waitTime: Long =
                (SHORT_TIME_DURATION_IN_MS - (System.currentTimeMillis() - _listOfLastUploadsTimeInMs.first()))

            if (waitTime > 0) {
                delay(waitTime)
            }
            _listOfLastUploadsTimeInMs.removeAt(0)
        }
        _listOfLastUploadsTimeInMs.add(System.currentTimeMillis())
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
                    val fileToUpload = _historyEntryRepo.getCachedFileFromUploadInfo(uploadInfos)

                    if (fileToUpload.exists()) {
                        val linkOfImage: String

                        waitForUploadIfNeededAndUpdateListOfLastUploadsTime()
                        linkOfImage = uploadThisImage(
                            fileToUpload,
                            app.contentResolver.getType(Uri.parse(uploadInfos.imageUri)),
                            uploadInfos
                        )
                        uploadStatus = UploadStatus.FINISHED
                        uploadStatusMessage = linkOfImage
                        fileToUpload.delete()
                    } else {
                        throw Exception(app.getString(R.string.fileToUploadNotFound))
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
     * Ajoute l'image représentée par [uploadInfosToUse] à la liste des uploads et commence à uploader ces images.
     * Si l'[uploadInfosToUse] n'a pas d'image en cache à utiliser pour l'upload alors l'upload ratera.
     */
    fun addFileFromUploadInfosToListOfFilesToUploadAndStartUpload(uploadInfosToUse: UploadInfos) {
        _nbOfPendingsAddToUploadList.incrementAndGet()

        viewModelScope.launch(Dispatchers.Main) {
            val newUploadInfos = UploadInfos(
                "",
                uploadInfosToUse.imageName,
                uploadInfosToUse.imageUri,
                uploadInfosToUse.uploadTimeInMs
            )

            _historyEntryRepo.blockReAddUploadInfosToCurrentGroup(newUploadInfos)
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
