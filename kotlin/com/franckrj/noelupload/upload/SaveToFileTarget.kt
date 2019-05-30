package com.franckrj.noelupload.upload

import android.graphics.drawable.Drawable
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

//todo pas besoin de clear quoi que ce soit (après onResourceReady) ?
/**
 * Une [CustomTarget] pour sauvegarder le résultat dans un fichier, en redimensionnant si besoin.
 */
class SaveToFileTarget(
    private val filePath: String,
    private val maxWidth: Int,
    private val maxHeight: Int,
    private val linkedUploadInfos: UploadInfos,
    var onFinishCallBack: ((SaveToFileTarget, UploadInfos) -> Any?)?
) :
    CustomTarget<Bitmap>() {
    /**
     * Retourne une nouvelle [Bitmap] redimensionnée (ou la même si pas besoin).
     */
    private fun scaleBitmapIfNeeded(bitmapToScale: Bitmap): Bitmap {
        var newWidth: Double = bitmapToScale.width.toDouble()
        var newHeight: Double = bitmapToScale.height.toDouble()

        if (newWidth > maxWidth) {
            newHeight /= (newWidth / maxWidth)
            newWidth = maxWidth.toDouble()
        }
        if (newHeight > maxHeight) {
            newWidth /= (newHeight / maxHeight)
            newHeight = maxHeight.toDouble()
        }

        return Bitmap.createScaledBitmap(bitmapToScale, newWidth.roundToInt(), newHeight.roundToInt(), true)
    }

    override fun onLoadCleared(placeholder: Drawable?) {
        /* Rien. */
    }

    override fun onResourceReady(bitmapResource: Bitmap, transition: Transition<in Bitmap>?) {
        try {
            val bitmapScaled = scaleBitmapIfNeeded(bitmapResource)
            val imageFile = File(filePath, "")
            FileOutputStream(imageFile).use { imageFileOutStream ->
                bitmapScaled.compress(Bitmap.CompressFormat.JPEG, 100, imageFileOutStream)
            }
        } catch (e: Exception) {
            /* Rien, tant pis si ça save pas on utilisera le fallback pour la preview. */
        }
        onFinishCallBack?.invoke(this, linkedUploadInfos)
    }
}
