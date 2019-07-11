package com.franckrj.noelupload

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.franckrj.noelupload.databinding.ActivityMainBinding
import com.franckrj.noelupload.history.FixedGlobalHeightRelativeLayout
import com.franckrj.noelupload.history.HistoryEntryInfos
import com.franckrj.noelupload.history.HistoryEntryMenuDialog
import com.franckrj.noelupload.history.HistoryEntryRepository
import com.franckrj.noelupload.history.HistoryListAdapter
import com.franckrj.noelupload.history.HistoryViewModel
import com.franckrj.noelupload.upload.UploadStatus
import com.franckrj.noelupload.upload.UploadViewModel
import com.franckrj.noelupload.utils.Utils

/**
 * Activité principale pour consulter l'historique des uploads et upload des nouvelles images.
 */
class MainActivity : AppCompatActivity() {
    companion object {
        private const val CHOOSE_IMAGE_REQUEST_CODE: Int = 38
        private const val ACTION_UPLOAD_IMAGE: String = "com.franckrj.noelupload.UPLOAD_IMAGE"
        private const val SAVE_PAUSE_COPY_OF_UPLOAD_LINKS = "SAVE_PAUSE_COPY_OF_UPLOAD_LINKS"
        private const val DEFAULT_PAUSE_COPY_OF_UPLOAD_LINKS: Boolean = false
    }

    private val _historyViewModel: HistoryViewModel by viewModels()
    private val _uploadViewModel: UploadViewModel by viewModels()
    private val _adapterForHistory = HistoryListAdapter()
    private var _pauseCopyOfUploadLinks: Boolean = DEFAULT_PAUSE_COPY_OF_UPLOAD_LINKS

    /**
     * Callback appelé lorsqu'un item est cliqué dans la liste, affiche un dialog contenant le menu de l'entrée si
     * l'image n'est pas entrain d'être upload.
     */
    private fun itemInHistoryListClicked(historyEntryInfos: HistoryEntryInfos) {
        if (historyEntryInfos.uploadStatus != UploadStatus.UPLOADING) {
            val args = Bundle()
            val historyEntryMenuDialog = HistoryEntryMenuDialog()

            args.putString(HistoryEntryMenuDialog.ARG_UPLOAD_IMAGE_BASE_LINK, historyEntryInfos.imageBaseLink)
            args.putString(HistoryEntryMenuDialog.ARG_UPLOAD_IMAGE_NAME, historyEntryInfos.imageName)
            args.putString(HistoryEntryMenuDialog.ARG_UPLOAD_IMAGE_URI, historyEntryInfos.imageUri)
            args.putLong(HistoryEntryMenuDialog.ARG_UPLOAD_TIME_IN_MS, historyEntryInfos.uploadTimeInMs)

            historyEntryMenuDialog.arguments = args
            historyEntryMenuDialog.show(supportFragmentManager, "HistoryEntryMenuDialog")
            //TODO: bloquer la copie d'upload pendant l'ouverture de cette fenêtre
        }
    }

    /**
     * Copie les liens du groupe d'upload si il n'y a plus aucun upload en cours et si la copie n'est pas mise en pause.
     * La copie est mise en pause lors du choix de nouvelles images à upload.
     */
    private fun tryToCopyLinksFromUploadGroup() {
        if (!_pauseCopyOfUploadLinks && _uploadViewModel.uploadListIsEmpty()) {
            val listOfLinks: List<String> = _historyViewModel.getDirectLinksOfUploadGrouptAndClearIt()

            if (listOfLinks.isNotEmpty()) {
                val linksInAString: String = listOfLinks.joinToString("\n")

                Utils.putStringInClipboard(this, linksInAString)
                Toast.makeText(
                    this,
                    if (listOfLinks.size == 1) R.string.linkCopied else R.string.linksCopied,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Lance l'upload de [uri] ou affiche un message d'erreur.
     */
    private fun startUploadThisImage(uri: Uri?) {
        if (uri != null) {
            _uploadViewModel.addFileToListOfFilesToUploadAndStartUpload(uri)
        } else {
            Toast.makeText(this, R.string.errorFileIsInvalid, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Consomme le [currIntent], lance des uploads ou ouvre le sélecteur d'image.
     */
    private fun consumeIntent(currIntent: Intent?) {
        if (currIntent != null) {
            if (currIntent.action == Intent.ACTION_SEND || currIntent.action == Intent.ACTION_SEND_MULTIPLE) {
                val newClipData: ClipData? = currIntent.clipData

                if (newClipData != null) {
                    for (idx: Int in 0 until newClipData.itemCount) {
                        startUploadThisImage(newClipData.getItemAt(idx).uri)
                    }
                } else if (currIntent.hasExtra(Intent.EXTRA_STREAM)) {
                    val newListOfUris: List<Uri?>? = currIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)

                    if (newListOfUris != null) {
                        for (currUri: Uri? in newListOfUris) {
                            startUploadThisImage(currUri)
                        }
                    } else {
                        val newUri: Uri? = currIntent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                        startUploadThisImage(newUri)
                    }
                }
            } else if (currIntent.action == ACTION_UPLOAD_IMAGE) {
                pickAnImage()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val numberOfColumnsToShow: Int

        val binding: ActivityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.lifecycleOwner = this
        binding.activity = this

        if (savedInstanceState != null) {
            _pauseCopyOfUploadLinks =
                savedInstanceState.getBoolean(SAVE_PAUSE_COPY_OF_UPLOAD_LINKS, DEFAULT_PAUSE_COPY_OF_UPLOAD_LINKS)
        }

        numberOfColumnsToShow = _historyViewModel.computeNumberOfColumnsToShow(windowManager.defaultDisplay)
        FixedGlobalHeightRelativeLayout.fixedHeightInPixel =
            _historyViewModel.computeHeightOfItems(numberOfColumnsToShow, windowManager.defaultDisplay)
        _adapterForHistory.listOfHistoryEntries = _historyViewModel.listOfHistoryEntries
        _adapterForHistory.itemClickedCallback = ::itemInHistoryListClicked
        _adapterForHistory.notifyDataSetChanged()

        binding.uploadhistoryListHistory.setOnApplyWindowInsetsListener { _, insets ->
            _historyViewModel.windowInsetTop = insets.systemWindowInsetTop
            insets.consumeSystemWindowInsets()
        }
        binding.uploadhistoryListHistory.layoutManager = GridLayoutManager(this, numberOfColumnsToShow)
        binding.uploadhistoryListHistory.adapter = _adapterForHistory
        (binding.uploadhistoryListHistory.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        binding.pickImageFabHistory.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.pickImageFabHistory.viewTreeObserver.removeOnGlobalLayoutListener(this)
                _historyViewModel.fabHeight = binding.pickImageFabHistory.height
            }
        })

        _historyViewModel.listOfHistoryEntriesChanges.observe(
            this,
            Observer { newListOfHistoryEntriesChanges: List<HistoryEntryRepository.HistoryEntryChangeInfos> ->
                while (newListOfHistoryEntriesChanges.isNotEmpty()) {
                    val historyEntryChange: HistoryEntryRepository.HistoryEntryChangeInfos =
                        newListOfHistoryEntriesChanges.first()

                    if (_historyViewModel.applyHistoryChange(historyEntryChange)) {
                        when (historyEntryChange.changeType) {
                            HistoryEntryRepository.HistoryEntryChangeType.NEW -> {
                                _adapterForHistory.notifyItemInserted(historyEntryChange.changeIndex)
                                binding.uploadhistoryListHistory.smoothScrollToPosition(_adapterForHistory.itemCount - 1)
                            }
                            HistoryEntryRepository.HistoryEntryChangeType.DELETED -> {
                                _adapterForHistory.notifyItemRemoved(historyEntryChange.changeIndex)
                            }
                            HistoryEntryRepository.HistoryEntryChangeType.CHANGED -> {
                                _adapterForHistory.notifyItemChanged(historyEntryChange.changeIndex)
                                if (historyEntryChange.newHistoryEntry.uploadStatus == UploadStatus.FINISHED) {
                                    tryToCopyLinksFromUploadGroup()
                                } else if (historyEntryChange.newHistoryEntry.uploadStatus == UploadStatus.ERROR) {
                                    Toast.makeText(
                                        this,
                                        getString(
                                            R.string.errorMessage,
                                            historyEntryChange.newHistoryEntry.uploadStatusMessage
                                        ),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                    _historyViewModel.removeFirstHistoryEntryChange()
                }
            })

        _historyViewModel.historyListPadding.observe(this, Observer { newRect: Rect ->
            binding.uploadhistoryListHistory.setPaddingRelative(
                newRect.left,
                newRect.top,
                newRect.right,
                newRect.bottom
            )
        })

        if (savedInstanceState == null) {
            binding.uploadhistoryListHistory.post {
                if (_adapterForHistory.itemCount > 0) {
                    binding.uploadhistoryListHistory.scrollToPosition(_adapterForHistory.itemCount - 1)
                }
            }
            consumeIntent(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(SAVE_PAUSE_COPY_OF_UPLOAD_LINKS, _pauseCopyOfUploadLinks)
    }

    /**
     * Intercepte les nouveaux intent reçus et les envoit à [consumeIntent].
     */
    override fun onNewIntent(newIntent: Intent?) {
        super.onNewIntent(newIntent)

        consumeIntent(newIntent)
    }

    /**
     * Intercepte le retour de l'intent pour sélectionner une image et upload son résultat, ou
     * affiche une erreur si le retour est invalide.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == CHOOSE_IMAGE_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            data.action = Intent.ACTION_SEND
            if (!data.hasExtra(Intent.EXTRA_STREAM)) {
                data.putExtra(Intent.EXTRA_STREAM, data.data)
            }
            consumeIntent(data)
        }

        _pauseCopyOfUploadLinks = false
        tryToCopyLinksFromUploadGroup()
    }

    /**
     * Lance un intent permettant de sélectionner une image ou affiche une erreur si la sélection est impossible.
     */
    fun pickAnImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

        try {
            startActivityForResult(intent, CHOOSE_IMAGE_REQUEST_CODE)
            _pauseCopyOfUploadLinks = true
        } catch (e: Exception) {
            Toast.makeText(this, R.string.errorFileManagerNotFound, Toast.LENGTH_LONG).show()
        }
    }
}
