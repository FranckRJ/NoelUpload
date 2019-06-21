package com.franckrj.noelupload.history

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.franckrj.noelupload.R
import com.franckrj.noelupload.upload.UploadInfos
import com.franckrj.noelupload.utils.Utils
import java.io.File

//TODO: Affichage clair en cas d'erreur sur l'image
//TODO: Bouton supprimer / partager
/**
 * Dialog affichant une entrée de l'historique ainsi que des options la concernant. Le lien direct de l'image est copié
 * directement si possible. (en TODO)
 */
class HistoryEntryMenuDialog : DialogFragment() {
    companion object {
        const val ARG_UPLOAD_IMAGE_BASE_LINK: String = "ARG_UPLOAD_IMAGE_BASE_LINK"
        const val ARG_UPLOAD_IMAGE_NAME: String = "ARG_UPLOAD_IMAGE_NAME"
        const val ARG_UPLOAD_IMAGE_URI: String = "ARG_UPLOAD_IMAGE_URI"
        const val ARG_UPLOAD_TIME_IN_MS: String = "ARG_UPLOAD_TIME_IN_MS"
    }

    private val _historyEntryRepo: HistoryEntryRepository = HistoryEntryRepository.instance
    private var _uploadInfos: UploadInfos? = null
    private var _directLinkOfImage: String? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        /* Suppression de warning justifiée car le layout sera attaché plus tard lors de la construction du dialog. */
        @SuppressLint("InflateParams")
        val mainView: View = requireActivity().layoutInflater.inflate(R.layout.dialog_history_entry_menu, null)
        val imagePreviewView: ImageView = mainView.findViewById(R.id.imagepreview_image_history_entry_menu_dialog)
        val builder = AlertDialog.Builder(requireActivity())
        val currArgs: Bundle? = arguments
        var newUploadInfos: UploadInfos? = null

        if (currArgs != null) {
            val baseLinkOfImage: String = currArgs.getString(ARG_UPLOAD_IMAGE_BASE_LINK, "")

            _directLinkOfImage =
                Utils.noelshackToDirectLink(baseLinkOfImage).takeIf { Utils.checkIfItsANoelshackImageLink(it) }

            try {
                newUploadInfos = UploadInfos(
                    baseLinkOfImage,
                    currArgs.getString(ARG_UPLOAD_IMAGE_NAME, ""),
                    currArgs.getString(ARG_UPLOAD_IMAGE_URI, "").ifEmpty { throw Exception() },
                    currArgs.getLong(ARG_UPLOAD_TIME_IN_MS, 0).also { if (it == 0L) throw Exception() })
            } catch (e: Exception) {
                newUploadInfos = null
            }
            _uploadInfos = newUploadInfos
        }

        if (newUploadInfos != null) {
            val fileForPreview: File? =
                _historyEntryRepo.getPreviewFileFromUploadInfos(newUploadInfos).takeIf { it.exists() }

            Glide.with(this)
                .load(fileForPreview ?: Utils.noelshackLinkToPreviewLink(newUploadInfos.imageBaseLink))
                .error(R.drawable.ic_file_download_failed_white_86dp)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imagePreviewView)
        } else {
            Glide.with(this)
                .load(R.drawable.ic_file_downloading_white_86dp)
                .centerCrop()
                .into(imagePreviewView)
        }

        builder.setView(mainView)

        _directLinkOfImage?.let { directLinkOfImage: String ->
            Utils.putStringInClipboard(requireContext(), directLinkOfImage)
            Toast.makeText(requireContext(), R.string.linkCopied, Toast.LENGTH_SHORT).show()
        }

        return builder.create()
    }

    override fun onPause() {
        dismiss()
        super.onPause()
    }
}
