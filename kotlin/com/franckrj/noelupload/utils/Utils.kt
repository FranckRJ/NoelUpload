package com.franckrj.noelupload.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.util.*

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

    /**
     * Converti n'importe quel lien vers une image noelshack en lien direct.
     */
    fun noelshackToDirectLink(baseLink: String): String {
        var link: String = baseLink

        if (link.contains("noelshack.com/")) {
            link = link.substring(link.indexOf("noelshack.com/") + 14)
        } else {
            return link
        }

        link = if (link.startsWith("fichiers/") || link.startsWith("fichiers-xs/") || link.startsWith("minis/")) {
            link.substring(link.indexOf("/") + 1)
        } else {
            link.replaceFirst("-", "/").replaceFirst("-", "/")
        }

        /* Moyen dégueulasse pour checker si le lien utilise le nouveau format (deux nombres entre l'année et le timestamp au lieu d'un). */
        if (link.contains("/")) {
            var checkForNewStringType = link.substring(link.lastIndexOf("/") + 1)

            if (checkForNewStringType.contains("-")) {
                checkForNewStringType = checkForNewStringType.substring(0, checkForNewStringType.indexOf("-"))

                if (checkForNewStringType.matches("[0-9]{1,8}".toRegex())) {
                    link = link.replaceFirst("-", "/")
                }
            }
        }

        return "http://image.noelshack.com/fichiers/$link"
    }

    //todo le code est pompé de RespawnIRC est devrait sans doute être amélioré (comme noelshackToDirectLink ?).
    /**
     * Retourne true si [baseLinkToCheck] est un lien vers une image noelshack, false sinon.
     */
    fun checkIfItsANoelshackImageLink(baseLinkToCheck: String): Boolean {
        var linkToCheck = baseLinkToCheck
        var endOfLink = linkToCheck.indexOf("?")
        if (endOfLink != -1) {
            linkToCheck = linkToCheck.substring(0, endOfLink)
        }

        endOfLink = linkToCheck.indexOf("#")
        if (endOfLink != -1) {
            linkToCheck = linkToCheck.substring(0, endOfLink)
        }

        linkToCheck = linkToCheck.toLowerCase(Locale.US)
        return if (!linkToCheck.endsWith(".php") && !linkToCheck.endsWith(".com/") && !linkToCheck.endsWith(".json")) {
            if (linkToCheck.startsWith("http://") || linkToCheck.startsWith("https://")) {
                linkToCheck = linkToCheck.substring(linkToCheck.indexOf("://") + 3)
            }

            linkToCheck.startsWith("image.noelshack.com/") || linkToCheck.startsWith("www.noelshack.com/") ||
                    linkToCheck.startsWith("noelshack.com/")
        } else {
            false
        }
    }

    /**
     * Converti n'importe quel lien vers une image noelshack en lien vers sa preview.
     */
    fun noelshackLinkToPreviewLink(baseLink: String): String {
        var link: String = noelshackToDirectLink(baseLink)

        link = link.replaceFirst("/fichiers/", "/minis/")
        if (link.contains(".")) {
            link = link.substring(0, link.lastIndexOf(".")) + ".png"
        }

        return link
    }
}
