package com.franckrj.noelupload

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import com.franckrj.noelupload.databinding.ActivityUploadBinding

//todo il y avait pas une histoire avec un paramètre de constructeur pour spécifier le layout ?
/**
 * Activité pour upload des images.
 */
class UploadActivity : AbsToolbarActivity() {
    companion object {
        private const val CHOOSE_IMAGE_REQUEST_CODE: Int = 38
    }

    //private val uploadViewModel: UploadViewModel by viewModels()
    //todo check ktx pour ça
    private lateinit var _uploadViewModel: UploadViewModel

    /**
     * Consomme le [currIntent], s'il est du bon type initialise la nouvelle image à upload s'il est valide,
     * sinon s'il est invalide affiche un message d'erreur.
     */
    private fun consumeIntent(currIntent: Intent?) {
        if (currIntent != null) {
            if (currIntent.action == Intent.ACTION_SEND && currIntent.hasExtra(Intent.EXTRA_STREAM)) {
                val newUri: Uri? = currIntent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

                if (newUri != null) {
                    _uploadViewModel.setCurrentUri(newUri)
                } else {
                    Toast.makeText(this, R.string.invalid_file, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding: ActivityUploadBinding = DataBindingUtil.setContentView(this, R.layout.activity_upload)
        _uploadViewModel = ViewModelProviders.of(this).get(UploadViewModel::class.java)
        binding.lifecycleOwner = this
        binding.activity = this
        binding.viewmodel = _uploadViewModel

        initToolbar(binding.toolbarUpload as Toolbar)

        if (savedInstanceState == null) {
            consumeIntent(intent)
        }

        /* Pour désactiver l'édition de l'EditText en le faisant buger le moins possible. */
        binding.currImageChoosedEditUpload.keyListener = null

        _uploadViewModel.restoreSavedData(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _uploadViewModel.onSaveData(outState)
    }

    /**
     * Intercepte les nouveaux intent reçus et les envoit à [consumeIntent].
     */
    override fun onNewIntent(newIntent: Intent?) {
        super.onNewIntent(newIntent)

        consumeIntent(newIntent)
    }

    /**
     * Intercepte le retour de l'intent pour sélectionner une image et sauvegarde son résultat, ou
     * affiche une erreur si le retour est invalide.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val newUri: Uri? = data?.data

        if (requestCode == CHOOSE_IMAGE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && newUri != null) {
                _uploadViewModel.setCurrentUri(newUri)
            } else {
                Toast.makeText(this, R.string.invalid_file, Toast.LENGTH_SHORT).show()
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Lance un intent permettant de sélectionner une image ou affiche une erreur si la sélection est impossible.
     */
    fun chooseAnImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        try {
            startActivityForResult(intent, CHOOSE_IMAGE_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.file_manager_not_found, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Commence l'upload de l'image sélectionnée, affiche une erreur en cas d'erreur.
     */
    fun startUploadCurrentImage() {
        val errorMessage: String? = _uploadViewModel.startUploadCurrentImage()

        if (errorMessage != null) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Si le lien de la dernière image uploadée est valide l'ajoute dans le presse-papier, sinon affiche une erreur.
     */
    fun copyLastImageUploadedLinkToClipboard() {
        val lastLink: String? = _uploadViewModel.lastImageUploadedInfo.value

        if (!lastLink.isNullOrEmpty() && Utils.checkIfItsANoelshackImageLink(lastLink)) {
            Utils.putStringInClipboard(this, lastLink)
            Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.invalid_link, Toast.LENGTH_SHORT).show()
        }
    }
}
