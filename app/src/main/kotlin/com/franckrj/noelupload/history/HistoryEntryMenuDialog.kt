package com.franckrj.noelupload.history

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.franckrj.noelupload.R
import com.franckrj.noelupload.databinding.DialogHistoryEntryMenuBinding
import com.franckrj.noelupload.upload.UploadInfos
import com.franckrj.noelupload.upload.UploadViewModel
import com.franckrj.noelupload.utils.Utils
import com.franckrj.noelupload.utils.getDrawableCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private var _deleteConfirmationIsVisible: Boolean = false
    private lateinit var _binding: DialogHistoryEntryMenuBinding

    /**
     * Ajuste le ratio de la taille de la prévisualisation de l'image en fonction de la taille du dialog pour que
     * la root view ne soit pas plus grande que la fenêtre dans laquelle elle se trouve.
     */
    private fun adjustImagePreviewSizeForDialogWindow() {
        _binding.root.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val totalContentHeight: Int = _binding.imagepreviewImageHistoryEntryMenuDialog.height +
                        _binding.deleteButtonHistoryEntryMenuDialog.height +
                        _binding.deleteButtonHistoryEntryMenuDialog.marginTop +
                        _binding.deleteButtonHistoryEntryMenuDialog.marginBottom
                _binding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                if (totalContentHeight > _binding.root.height) {
                    val imagepreviewLayoutParams =
                        _binding.imagepreviewImageHistoryEntryMenuDialog.layoutParams as ConstraintLayout.LayoutParams
                    imagepreviewLayoutParams.dimensionRatio = "1:0.5"
                    _binding.imagepreviewImageHistoryEntryMenuDialog.layoutParams = imagepreviewLayoutParams
                }
            }
        })
    }

    /**
     * Affiche le chip d'information de l'image en tant que toast (fade in, pause de 1750ms, fade out).
     */
    private fun showInfosChipAsAToast() {
        _binding.infosChipHistoryEntryMenuDialog.postDelayed({
            TransitionManager.beginDelayedTransition(_binding.contentLayoutHistoryEntryMenuDialog)
            _binding.infosChipHistoryEntryMenuDialog.visibility = View.VISIBLE

            lifecycleScope.launch(Dispatchers.Main) {
                delay(1750)
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    /* Comme le fragment se dismiss durant le onPause ça veut dire que dès qu'il n'est plus RESUMED
                       il restera invalide pour toujours, donc pas de soucis de GONE qui ne s'execute pas. */
                    TransitionManager.beginDelayedTransition(_binding.contentLayoutHistoryEntryMenuDialog)
                    _binding.infosChipHistoryEntryMenuDialog.visibility = View.GONE
                }
            }
        }, 100)
    }

    /**
     * Partage le lien direct de l'image [directLinkToShare].
     */
    private fun shareThisDirectLinkOfHistoryEntry(directLinkToShare: String) {
        val sharingIntent = Intent(Intent.ACTION_SEND)
        sharingIntent.type = "text/plain"
        sharingIntent.putExtra(Intent.EXTRA_TEXT, directLinkToShare)
        startActivity(Intent.createChooser(sharingIntent, resources.getString(R.string.share)))
    }

    /**
     * Reupload l'[_uploadInfos] actuel, retourne vrai si l'upload a pu commencer ou faux s'il y a une erreur.
     */
    private fun reuploadCurrentHistoryEntry(): Boolean {
        val uploadInfos = _uploadInfos

        if (uploadInfos != null && uploadInfos.imageUri.isNotEmpty()) {
            val uploadViewModel: UploadViewModel =
                ViewModelProviders.of(requireActivity()).get(UploadViewModel::class.java)
            uploadViewModel.addFileFromUploadInfosToListOfFilesToUploadAndStartUpload(uploadInfos)
            return true
        }

        return false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireActivity())
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
                .error(requireContext().getDrawableCompat(R.drawable.ic_file_download_failed_white_86dp))
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(_binding.imagepreviewImageHistoryEntryMenuDialog)
        } else {
            Glide.with(this)
                .load(requireContext().getDrawableCompat(R.drawable.ic_file_downloading_white_86dp))
                .centerCrop()
                .into(_binding.imagepreviewImageHistoryEntryMenuDialog)
        }

        builder.setView(_binding.root)
        adjustImagePreviewSizeForDialogWindow()

        _directLinkOfImage.let { directLinkOfImage: String? ->
            if (directLinkOfImage != null) {
                Utils.putStringInClipboard(requireContext(), directLinkOfImage)
                showInfosChipAsAToast()
            } else {
                _binding.infosChipHistoryEntryMenuDialog.visibility = View.VISIBLE
                _binding.infosChipHistoryEntryMenuDialog.setChipBackgroundColorResource(R.color.colorTransparentBackgroundError)
                _binding.infosChipHistoryEntryMenuDialog.setText(R.string.errorImageHasNotBeenUploaded)
                _binding.shareOrReuploadImageHistoryEntryMenuDialog.contentDescription = getString(R.string.reupload)
                _binding.shareOrReuploadImageHistoryEntryMenuDialog.setImageDrawable(
                    requireContext().getDrawableCompat(
                        R.drawable.ic_file_upload_white_36dp
                    )
                )
            }
        }

        return builder.create()
    }

    override fun onPause() {
        dismiss()
        super.onPause()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        val parentActivity: FragmentActivity? = activity

        if (parentActivity is DialogInterface.OnDismissListener) {
            parentActivity.onDismiss(dialog)
        }
    }

    /**
     * Ouvre le lien direct de l'image de l'[_uploadInfos] dans le navigateur ou affiche une erreur si le lien est invalide.
     */
    fun openDirectLinkInBrowser() {
        _directLinkOfImage.let { directLinkOfImage: String? ->
            if (directLinkOfImage != null) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(directLinkOfImage)))
                dismiss()
            } else {
                Toast.makeText(requireContext(), R.string.errorInvalidLink, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Partage ou reupload l'image de l'[_uploadInfos] selon ce qui est possible, ferme le dialog si nécessaire
     * ou affiche une erreur.
     */
    fun shareOrReuploadHistoryEntry() {
        _directLinkOfImage.let { directLinkOfImage: String? ->
            if (directLinkOfImage != null) {
                shareThisDirectLinkOfHistoryEntry(directLinkOfImage)
                dismiss()
            } else if (reuploadCurrentHistoryEntry()) {
                dismiss()
            } else {
                Toast.makeText(requireContext(), R.string.errorInvalidLink, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Affiche la confirmation de suppression, si elle est déjà affichée supprime l'[HistoryEntryInfos] représenté
     * par [_uploadInfos] de l'historique et ferme le dialog.
     */
    fun deleteHistoryEntry() {
        if (_deleteConfirmationIsVisible) {
            _historyEntryRepo.postDeleteThisUploadInfos(_uploadInfos)
            dismiss()
        } else {
            TransitionManager.beginDelayedTransition(_binding.contentLayoutHistoryEntryMenuDialog)
            _binding.deleteConfirmationTextHistoryEntryMenuDialog.visibility = View.VISIBLE
            _deleteConfirmationIsVisible = true
        }
    }
}
