package com.franckrj.noelupload.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.franckrj.noelupload.R
import com.franckrj.noelupload.upload.UploadStatus

/**
 * Adapater pour l'historique des informations des uploads.
 */
class HistoryListAdapter : RecyclerView.Adapter<HistoryListAdapter.HistoryViewHolder>() {
    var listOfHistoryEntries: List<HistoryEntryInfos> = listOf()
    var itemClickedCallback: ((HistoryEntryInfos) -> Any?)? = null

    /**
     * Fonction callback appelée quand un item de la liste est cliqué. Elle appelle la fonction [itemClickedCallback]
     * avec l'[HistoryEntryInfos] qui a été cliqué.
     */
    private fun itemInListClicked(position: Int) {
        if (position >= 0 && position < listOfHistoryEntries.size) {
            itemClickedCallback?.invoke(listOfHistoryEntries[position])
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder =
        HistoryViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false),
            ::itemInListClicked
        )

    override fun getItemCount(): Int = listOfHistoryEntries.size

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        if (position >= 0 && position < listOfHistoryEntries.size) {
            holder.bindView(listOfHistoryEntries[position], position)
        }
    }

    /**
     * ViewHolder pour un [HistoryEntryInfos].
     */
    class HistoryViewHolder(private val mainView: View, clickCallback: (Int) -> Any?) :
        RecyclerView.ViewHolder(mainView) {
        private val _imagePreview: ImageView = mainView.findViewById(R.id.imagepreview_image_history_item)
        private val _infoBackground: View = mainView.findViewById(R.id.background_info_view_history_item)
        private val _errorText: TextView = mainView.findViewById(R.id.error_text_history_item)
        private val _uploadProgress: ProgressBar = mainView.findViewById(R.id.upload_progress_history_item)
        private val _currentGroupIndicator: View = mainView.findViewById(R.id.current_group_indicator_history_item)

        init {
            mainView.setOnClickListener { view: View? -> clickCallback(view?.tag as? Int ?: -1) }
        }

        /**
         * Bind le ViewHolder à l'item passé en paramètre.
         * L'image de preview sera chargée si le fichier est différent de null, sinon la miniature noelshack sera utilisée.
         * L'image de preview vaut null si le fichier n'existe pas.
         */
        fun bindView(historyEntry: HistoryEntryInfos, position: Int) {
            val currUploadProgression: Int = if (historyEntry.uploadStatus == UploadStatus.UPLOADING) {
                historyEntry.uploadStatusMessage.toIntOrNull() ?: -1
            } else {
                -1
            }

            if (historyEntry.fileForPreview != null || historyEntry.fallbackPreviewUrl.isNotEmpty()) {
                Glide.with(mainView.context)
                    .load(historyEntry.fileForPreview ?: historyEntry.fallbackPreviewUrl)
                    .placeholder(R.drawable.ic_file_downloading_white_86dp)
                    .error(R.drawable.ic_file_download_failed_white_86dp)
                    .centerCrop()
                    .into(_imagePreview)
            } else {
                Glide.with(mainView.context)
                    .load(R.drawable.ic_file_downloading_white_86dp)
                    .centerCrop()
                    .into(_imagePreview)
            }

            when {
                currUploadProgression in 0..100 -> {
                    _infoBackground.setBackgroundResource(R.color.colorTransparentBackgroundNormal)
                    _infoBackground.visibility = View.VISIBLE
                    _errorText.visibility = View.INVISIBLE
                    _uploadProgress.visibility = View.VISIBLE
                    if (currUploadProgression == 0) {
                        _uploadProgress.isIndeterminate = true
                    } else {
                        _uploadProgress.isIndeterminate = false
                        _uploadProgress.progress = currUploadProgression
                    }
                }
                historyEntry.imageBaseLink.isNotEmpty() -> {
                    _infoBackground.visibility = View.GONE
                    _errorText.visibility = View.GONE
                    _uploadProgress.visibility = View.GONE
                }
                else -> {
                    _infoBackground.setBackgroundResource(R.color.colorTransparentBackgroundError)
                    _infoBackground.visibility = View.VISIBLE
                    _errorText.visibility = View.VISIBLE
                    _uploadProgress.visibility = View.GONE
                }
            }

            _currentGroupIndicator.visibility = (if (historyEntry.isInCurrentUploadGroup) View.VISIBLE else View.GONE)

            mainView.tag = position
        }
    }
}
