package com.will.animlauncher

import android.content.Context

// Une boîte à outils unique qui range les réglages dans le carnet du téléphone.
object Reglages {
    private const val FICHIER = "reglages"
    private const val CLE_ACTIF = "animations_actives"
    private const val CLE_DEMARRAGE = "seulement_demarrage"

    private fun carnet(c: Context) = c.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    // KILL SWITCH : les animations sont-elles activées ? (oui par défaut)
    fun actif(c: Context): Boolean = carnet(c).getBoolean(CLE_ACTIF, true)
    fun definir(c: Context, valeur: Boolean) {
        carnet(c).edit().putBoolean(CLE_ACTIF, valeur).apply()
    }
    fun basculer(c: Context) = definir(c, !actif(c))

    // MODE : animation seulement quand l'app démarre ? (non par défaut = à chaque fois)
    fun seulementDemarrage(c: Context): Boolean = carnet(c).getBoolean(CLE_DEMARRAGE, false)
    fun definirSeulementDemarrage(c: Context, valeur: Boolean) {
        carnet(c).edit().putBoolean(CLE_DEMARRAGE, valeur).apply()
    }
}
