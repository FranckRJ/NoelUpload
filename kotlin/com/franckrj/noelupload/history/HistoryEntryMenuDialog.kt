package com.franckrj.noelupload.history

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.franckrj.noelupload.R
import com.franckrj.noelupload.databinding.DialogHistoryEntryMenuBinding
import com.franckrj.noelupload.upload.UploadInfos
import com.franckrj.noelupload.utils.Utils
import java.io.File

/**
 * Dialog affichant une entrée de l'historique ainsi que des options la concernant. Le lien direct de l'image est copié
 * directement si possible.
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
    private lateinit var _binding: DialogHistoryEntryMenuBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        val currArgs: Bundle? = arguments
        var newUploadInfos: UploadInfos? = null

        _binding = DialogHistoryEntryMenuBinding.inflate(requireActivity().layoutInflater)
        _binding.dialog = this

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
                .into(_binding.imagepreviewImageHistoryEntryMenuDialog)
        } else {
            Glide.with(this)
                .load(R.drawable.ic_file_downloading_white_86dp)
                .centerCrop()
                .into(_binding.imagepreviewImageHistoryEntryMenuDialog)
        }

        builder.setView(_binding.root)

        _directLinkOfImage.let { directLinkOfImage: String? ->
            if (directLinkOfImage != null) {
                Utils.putStringInClipboard(requireContext(), directLinkOfImage)
                Toast.makeText(requireContext(), R.string.linkCopied, Toast.LENGTH_SHORT).show()
            } else {
                _binding.errorTextHistoryEntryMenuDialog.visibility = View.VISIBLE
                _binding.backgroundErrorViewHistoryEntryMenuDialog.visibility = View.VISIBLE
            }
        }

        return builder.create()
    }

    override fun onResume() {
        super.onResume()
        //TODO: à améliorer.
        /* Scroll tout en bas de la popup dans le cas où l'image est trop grande (pour voir les boutons). Arrive
           normalement uniquement en mode paysage. */
        _binding.contentScrollviewHistoryEntryMenuDialog.post {
            _binding.contentScrollviewHistoryEntryMenuDialog.apply {
                val lastChild = getChildAt(childCount - 1)
                val bottom = lastChild.bottom + paddingBottom
                val delta = bottom - (scrollY + height)
                scrollBy(0, delta)
            }
        }
    }

    override fun onPause() {
        dismiss()
        super.onPause()
    }

    /**
     * Partage le lien direct de l'image de l'[_uploadInfos] ou affiche une erreur si le lien est invalide.
     */
    fun shareDirectLinkOfHistoryEntry() {
        _directLinkOfImage.let { directLinkOfImage: String? ->
            if (directLinkOfImage != null) {
                val sharingIntent = Intent(Intent.ACTION_SEND)
                sharingIntent.type = "text/plain"
                sharingIntent.putExtra(Intent.EXTRA_TEXT, directLinkOfImage)
                startActivity(Intent.createChooser(sharingIntent, resources.getString(R.string.share)))
                dismiss()
            } else {
                Toast.makeText(requireContext(), R.string.errorInvalidLink, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Supprime l'[HistoryEntryInfos] représenté par [_uploadInfos] de l'historique et ferme le dialog.
     */
    fun deleteHistoryEntry() {
        _historyEntryRepo.postDeleteThisUploadInfos(_uploadInfos)
        dismiss()
    }
}
