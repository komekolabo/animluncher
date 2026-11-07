package com.will.animlauncher

import android.content.Context

// Un "object" = une boîte à outils unique, utilisable partout dans l'app.
// Elle range les réglages dans le petit carnet du téléphone (SharedPreferences).
object Reglages {
    private const val FICHIER = "reglages"
    private const val CLE_ACTIF = "animations_actives"

    // Les animations sont-elles activées ? (oui par défaut)
    fun actif(contexte: Context): Boolean =
        contexte.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
            .getBoolean(CLE_ACTIF, true)

    fun definir(contexte: Context, valeur: Boolean) {
        contexte.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
            .edit().putBoolean(CLE_ACTIF, valeur).apply()
    }

    // Inverse l'état : activé -> désactivé, désactivé -> activé.
    fun basculer(contexte: Context) = definir(contexte, !actif(contexte))
}
