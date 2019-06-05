package com.franckrj.noelupload

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import com.franckrj.noelupload.databinding.ActivityMainBinding
import com.franckrj.noelupload.history.HistoryEntryInfos
import com.franckrj.noelupload.history.HistoryListAdapter
import com.franckrj.noelupload.history.HistoryViewModel
import com.franckrj.noelupload.upload.UploadViewModel
import androidx.recyclerview.widget.SimpleItemAnimator
import com.franckrj.noelupload.history.HistoryEntryRepository
import com.franckrj.noelupload.upload.UploadStatus
import com.franckrj.noelupload.utils.Utils
import androidx.appcompat.app.AppCompatActivity
import com.franckrj.noelupload.history.FixedGlobalHeightRelativeLayout

/**
 * Activité principale pour consulter l'historique des uploads et upload des nouvelles images.
 */
class MainActivity : AppCompatActivity() {
    companion object {
        private const val CHOOSE_IMAGE_REQUEST_CODE: Int = 38
        private const val ACTION_UPLOAD_IMAGE: String = "com.franckrj.noelupload.UPLOAD_IMAGE"
    }

    //private val uploadViewModel: UploadViewModel by viewModels()
    //todo check ktx pour ça
    private lateinit var _historyViewModel: HistoryViewModel
    private lateinit var _uploadViewModel: UploadViewModel
    private val _adapterForHistory = HistoryListAdapter()

    /**
     * Callback appelé lorsqu'un item est cliqué dans la liste, copie le lien direct associé dans
     * le presse-papier ou affiche une erreur.
     */
    private fun itemInHistoryListClicked(historyEntryInfos: HistoryEntryInfos) {
        val linkOfImage: String = Utils.noelshackToDirectLink(historyEntryInfos.imageBaseLink)

        if (Utils.checkIfItsANoelshackImageLink(linkOfImage)) {
            Utils.putStringInClipboard(this, linkOfImage)
            Toast.makeText(this, R.string.linkCopied, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.errorInvalidLink, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Lance l'upload de [uri] ou affiche un message d'erreur.
     */
    private fun startUploadThisImage(uri: Uri?) {
        if (uri != null) {
            if (!_uploadViewModel.startUploadThisImage(uri)) {
                Toast.makeText(this, R.string.errorUploadAlreadyRunning, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, R.string.errorFileIsInvalid, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Consomme le [currIntent], s'il est du bon type initialise la nouvelle image à upload s'il est valide,
     * sinon s'il est invalide affiche un message d'erreur.
     */
    private fun consumeIntent(currIntent: Intent?) {
        if (currIntent != null) {
            if (currIntent.action == Intent.ACTION_SEND && currIntent.hasExtra(Intent.EXTRA_STREAM)) {
                val newUri: Uri? = currIntent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

                startUploadThisImage(newUri)
            } else if (currIntent.action == ACTION_UPLOAD_IMAGE) {
                pickAnImage()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val numberOfColumnsToShow: Int

        val binding: ActivityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        _historyViewModel = ViewModelProviders.of(this).get(HistoryViewModel::class.java)
        _uploadViewModel = ViewModelProviders.of(this).get(UploadViewModel::class.java)
        binding.lifecycleOwner = this
        binding.activity = this

        numberOfColumnsToShow = _historyViewModel.computeNumberOfColumnsToShow(windowManager.defaultDisplay)
        FixedGlobalHeightRelativeLayout.fixedHeightInPixel =
            _historyViewModel.computeHeightOfItems(numberOfColumnsToShow, windowManager.defaultDisplay)
        _adapterForHistory.listOfHistoryEntries = _historyViewModel.listOfHistoryEntries
        _adapterForHistory.itemClickedCallback = ::itemInHistoryListClicked
        _adapterForHistory.notifyDataSetChanged()

        binding.uploadhistoryListHistory.layoutManager = GridLayoutManager(this, numberOfColumnsToShow)
        binding.uploadhistoryListHistory.adapter = _adapterForHistory
        (binding.uploadhistoryListHistory.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

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
                                    itemInHistoryListClicked(historyEntryChange.newHistoryEntry)
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

        if (savedInstanceState == null) {
            if (_adapterForHistory.itemCount > 0) {
                binding.uploadhistoryListHistory.scrollToPosition(_adapterForHistory.itemCount - 1)
            }
            consumeIntent(intent)
        }
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
        val newUri: Uri? = data?.data

        if (requestCode == CHOOSE_IMAGE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            startUploadThisImage(newUri)
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Lance un intent permettant de sélectionner une image ou affiche une erreur si la sélection est impossible.
     */
    fun pickAnImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        try {
            startActivityForResult(intent, CHOOSE_IMAGE_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.errorFileManagerNotFound, Toast.LENGTH_LONG).show()
        }
    }
}
