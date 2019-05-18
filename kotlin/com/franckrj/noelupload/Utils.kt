package com.franckrj.noelupload

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object Utils {
    /**
     * Ajoute le texte [textToCopy] dans le presse-papier.
     */
    fun putStringInClipboard(context: Context, textToCopy: String) {
        val clipboard: ClipboardManager? = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

        if (clipboard != null) {
            val clip = ClipData.newPlainText(textToCopy, textToCopy)
            clipboard.primaryClip = clip
        }
    }
}
