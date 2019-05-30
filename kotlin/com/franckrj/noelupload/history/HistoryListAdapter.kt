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
import com.franckrj.noelupload.upload.UploadInfos

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

    /**
     * Retourne l'index de [HistoryEntryInfos] ayant un [UploadInfos] d'un id [id].
     * Retourne -1 si rien n'a été trouvé.
     */
    fun findItemIndexByUploadId(id: Long?): Int {
        if (id == null) {
            return -1
        } else {
            for (i in 0 until listOfHistoryEntries.size) {
                if (listOfHistoryEntries[i].uploadInfos.id == id) {
                    return i
                }
            }
            return -1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder =
        HistoryViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.row_history, parent, false),
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
    class HistoryViewHolder(private val mainView: View, private val clickCallback: (Int) -> Any?) :
        RecyclerView.ViewHolder(mainView) {
        private val _imagePreview: ImageView = mainView.findViewById(R.id.imagepreview_image_history_row)
        private val _imageName: TextView = mainView.findViewById(R.id.imagename_text_history_row)
        private val _uploadProgress: ProgressBar = mainView.findViewById(R.id.upload_progress_history_row)

        init {
            mainView.setOnClickListener { view: View? -> clickCallback(view?.tag as? Int ?: -1) }
        }

        /**
         * Bind le ViewHolder à l'item passé en paramètre.
         * L'image de preview sera chargée si le fichier est différent de null, sinon la miniature noelshack sera utilisée.
         * L'image de preview vaut null si le fichier n'existe pas.
         */
        fun bindView(historyEntryInfos: HistoryEntryInfos, position: Int) {
            val currUploadProgression: Int = historyEntryInfos.uploadProgression

            Glide.with(mainView.context)
                .load(historyEntryInfos.fileForPreview ?: historyEntryInfos.fallbackPreviewUrl)
                .placeholder(R.drawable.ic_file_download_white_24dp)
                .error(R.drawable.ic_file_download_failed_white_24dp)
                .centerCrop()
                .into(_imagePreview)

            if (currUploadProgression in 0..100) {
                _imageName.visibility = View.GONE
                _uploadProgress.visibility = View.VISIBLE
                _uploadProgress.progress = currUploadProgression
            } else {
                _imageName.visibility = View.VISIBLE
                _uploadProgress.visibility = View.GONE
                _imageName.text = historyEntryInfos.uploadInfos.imageName
            }

            mainView.tag = position
        }
    }
}
