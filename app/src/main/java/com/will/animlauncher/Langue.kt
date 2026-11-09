package com.will.animlauncher

import android.app.Activity
import android.app.AlertDialog
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList

// LE CHOIX DE LA LANGUE (09/11). Par défaut, l'app suit la langue du téléphone. Ici, on peut en
// choisir une autre, juste pour AnimLauncher.
// On passe par le « LocaleManager » d'Android (Android 13 et plus) : c'est LUI qui retient le choix
// et qui redessine tout de suite les écrans dans la nouvelle langue. Rien à ranger nous-mêmes.
// Sur un Android plus ancien, cette possibilité n'existe pas : le bouton n'est pas affiché.
object Langue {

    // Vrai si le téléphone sait changer la langue d'une seule app.
    val possible: Boolean get() = Build.VERSION.SDK_INT >= 33

    // Les langues de l'app : leur code (« fr ») et leur nom, écrit dans la langue elle-même.
    // Le premier choix, sans code, veut dire « comme le téléphone ».
    private val CODES = listOf("", "en", "fr", "es", "de", "pt", "it")
    private val NOMS = listOf("English", "Français", "Español", "Deutsch", "Português", "Italiano")

    // Ouvre la petite fenêtre avec la liste des langues ; celle en cours est cochée.
    fun choisir(a: Activity) {
        if (Build.VERSION.SDK_INT < 33) return
        val gestion = a.getSystemService(LocaleManager::class.java) ?: return
        val choisies = gestion.applicationLocales                 // vide = on suit le téléphone
        val actuelle = if (choisies.isEmpty) "" else choisies.get(0).language
        val noms = (listOf(a.getString(R.string.langue_systeme)) + NOMS).toTypedArray()
        AlertDialog.Builder(a)
            .setTitle(a.getString(R.string.langue))
            .setSingleChoiceItems(noms, CODES.indexOf(actuelle).coerceAtLeast(0)) { fenetre, i ->
                fenetre.dismiss()
                gestion.applicationLocales =
                    if (i == 0) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(CODES[i])
            }
            .show()
    }
}
