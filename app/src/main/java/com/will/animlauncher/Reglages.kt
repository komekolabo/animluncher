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

    // ---------------- RÉGLAGES APP PAR APP ----------------
    // Chaque réglage est rangé sous une clé qui contient le nom technique de l'app,
    // par exemple "off_com.anthropic.claude".

    // L'animation de cette app est-elle activée ? (oui par défaut)
    fun appActive(c: Context, app: String): Boolean = !carnet(c).getBoolean("off_$app", false)
    fun definirAppActive(c: Context, app: String, active: Boolean) {
        carnet(c).edit().putBoolean("off_$app", !active).apply()
    }

    // La durée choisie pour cette app, en millisecondes. -1 = la durée par défaut.
    // 0 = pas d'animation du tout.
    fun duree(c: Context, app: String): Int = carnet(c).getInt("duree_$app", -1)
    fun definirDuree(c: Context, app: String, ms: Int) {
        carnet(c).edit().putInt("duree_$app", ms).apply()
    }

    // Les variantes désactivées (leurs numéros : 0, 1, 2…) pour les apps qui en ont plusieurs.
    fun variantesDesactivees(c: Context, app: String): Set<Int> =
        (carnet(c).getStringSet("var_$app", emptySet()) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()
    fun definirVariante(c: Context, app: String, numero: Int, active: Boolean) {
        val liste = variantesDesactivees(c, app).toMutableSet()
        if (active) liste.remove(numero) else liste.add(numero)
        // on range une COPIE neuve : Android n'enregistre pas les modifications d'un ensemble existant
        carnet(c).edit().putStringSet("var_$app", liste.map { it.toString() }.toSet()).apply()
    }
}
