package com.franckrj.noelupload.upload

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object FileUriUtils {
    /**
     * Calcul la valeur de inSampleSize pour que la preview rentre dans un carré de taille [reqWidth] * [reqHeight].
     * Le résultat donnera une Bitmap qui peut perdre en qualité car il peut donner une taille jusqu'à 2 fois plus
     * petite que les valeurs demandées.
     */
    private fun computeLossySampleSize(currWidth: Int, currHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var width = currWidth
        var height = currHeight
        var inSampleSize = 1

        while (width > reqWidth || height > reqHeight) {
            inSampleSize *= 2
            width /= 2
            height /= 2
        }

        return inSampleSize
    }

    /**
     * Retourne le nom du fichier pointé par [uri] via le [ContentResolver]. S'il n'est pas trouvé retourne
     * simplement la dernière partie de [uri].
     */
    fun getFileName(uri: Uri, context: Context): String {
        var result: String? = null

        if (uri.scheme == "content") {
            val queryCursor: Cursor? = context.contentResolver.query(
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
     * Retourne un [ByteArrayOutputStream] avec le contenu du [fileToRead], ou null en cas d'erreur.
     */
    suspend fun readFileContent(fileToRead: File): ByteArrayOutputStream? = withContext(Dispatchers.IO) {
        try {
            FileInputStream(fileToRead).use { inputStream ->
                val outputStreamResult = ByteArrayOutputStream()
                val buffer = ByteArray(8192)

                var length = inputStream.read(buffer)
                while (length != -1) {
                    outputStreamResult.write(buffer, 0, length)
                    length = inputStream.read(buffer)
                }
                outputStreamResult
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Sauvegarde le contenu de [uriToSave] dans le fichier [destinationFile]. Retourne true en cas de succes et false
     * si une erreur a eu lieu.
     */
    suspend fun saveUriContentToFile(uriToSave: Uri, destinationFile: File, context: Context): Boolean =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uriToSave)!!.use { inputStream ->
                    FileOutputStream(destinationFile, false).use { outputFile ->
                        val buffer = ByteArray(8192)

                        var length = inputStream.read(buffer)
                        while (length != -1) {
                            outputFile.write(buffer, 0, length)
                            length = inputStream.read(buffer)
                        }
                    }
                }
                true
            } catch (e: Exception) {
                false
            }
        }

    /**
     * Sauvegarde une preview de l'image représentée par [uriToUse] dans le fichier [previewFile], retourne true en cas
     * de succès et false en cas d'erreur.
     * La preview aura une taille maximum de [maxWidth] * [maxHeight], mais peut être jusqu'à deux fois plus petite
     * de chaque côtés.
     */
    suspend fun savePreviewOfUriToFile(
        uriToUse: Uri,
        previewFile: File,
        maxWidth: Int,
        maxHeight: Int,
        context: Context
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val previewBitmap: Bitmap
                val bitmapOption = BitmapFactory.Options()

                context.contentResolver.openInputStream(uriToUse)!!.use { inputStream ->
                    bitmapOption.inJustDecodeBounds = true
                    BitmapFactory.decodeStream(inputStream, null, bitmapOption)!!
                }
                previewBitmap = context.contentResolver.openInputStream(uriToUse)!!.use { inputStream ->
                    bitmapOption.inJustDecodeBounds = false
                    bitmapOption.inSampleSize =
                        computeLossySampleSize(bitmapOption.outWidth, bitmapOption.outHeight, maxWidth, maxHeight)
                    BitmapFactory.decodeStream(inputStream, null, bitmapOption)!!
                }
                FileOutputStream(previewFile, false).use { outputStream ->
                    previewBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }

                true
            } catch (e: Exception) {
                false
            }
        }
}
