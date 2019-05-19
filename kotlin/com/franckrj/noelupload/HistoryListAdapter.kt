package com.franckrj.noelupload

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

/**
 * Adapater pour l'historique des informations des uploads.
 */
class HistoryListAdapter : RecyclerView.Adapter<HistoryListAdapter.HistoryViewHolder>() {
    var listOfUploadInfos: List<UploadInfos> = listOf()
    var itemClickedCallback: ((UploadInfos) -> Any?)? = null

    /**
     * Fonction callback appelée quand un item de la liste est cliqué. Elle appelle la fonction [itemClickedCallback]
     * avec l'[UploadInfos] qui a été cliqué.
     */
    private fun itemInListClicked(position: Int) {
        if (position >= 0 && position < listOfUploadInfos.size) {
            itemClickedCallback?.invoke(listOfUploadInfos[position])
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder =
        HistoryViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.row_history, parent, false),
            ::itemInListClicked
        )

    override fun getItemCount(): Int = listOfUploadInfos.size

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        if (position >= 0 && position < listOfUploadInfos.size) {
            holder.bindView(listOfUploadInfos[position], position)
        }
    }

    /**
     * ViewHolder pour un [UploadInfos].
     */
    class HistoryViewHolder(private val mainView: View, private val clickCallback: (Int) -> Any?) :
        RecyclerView.ViewHolder(mainView) {
        private val _imagePreview: ImageView = mainView.findViewById(R.id.imagepreview_image_history_row)
        private val _imageName: TextView = mainView.findViewById(R.id.imagename_text_history_row)

        init {
            mainView.setOnClickListener { view: View? -> clickCallback(view?.tag as? Int ?: -1) }
        }

        /**
         * Bind le ViewHolder à l'item passé en paramètre.
         */
        fun bindView(uploadInfos: UploadInfos, position: Int) {
            Glide.with(mainView.context).load(Utils.noelshackLinkToPreviewLink(uploadInfos.imageBaseLink))
                .placeholder(R.drawable.ic_file_download_white_24dp)
                .error(R.drawable.ic_file_download_failed_white_24dp).centerCrop().into(_imagePreview)
            _imageName.text = uploadInfos.imageName
            mainView.tag = position
        }
    }
}
