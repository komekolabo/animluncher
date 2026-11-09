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

    // L'animation de cette app est-elle activée ?
    // Si l'utilisateur a déjà choisi (interrupteur dans les options), c'est son choix qui compte.
    // Sinon, c'est le réglage de base du catalogue : activée, sauf pour quelques apps qu'on veut
    // ouvrir tout de suite (Téléphone, Appareil photo…), désactivées de base.
    fun appActive(c: Context, app: String): Boolean {
        val reglages = carnet(c)
        return if (reglages.contains("off_$app")) !reglages.getBoolean("off_$app", false)
               else Catalogue.actifParDefaut(app)
    }
    fun definirAppActive(c: Context, app: String, active: Boolean) {
        carnet(c).edit().putBoolean("off_$app", !active).apply()
    }

    // « Seulement au démarrage » pour CETTE app : si on l'a réglé dans la page de l'app, c'est ce
    // choix qui compte ; sinon on suit l'interrupteur général de l'écran principal.
    fun seulementDemarrage(c: Context, app: String): Boolean {
        val reglages = carnet(c)
        return if (reglages.contains("dem_$app")) reglages.getBoolean("dem_$app", false)
               else seulementDemarrage(c)
    }
    fun definirSeulementDemarrage(c: Context, app: String, valeur: Boolean) {
        carnet(c).edit().putBoolean("dem_$app", valeur).apply()
    }
    // Revenir au réglage général pour cette app.
    fun oublierSeulementDemarrage(c: Context, app: String) {
        carnet(c).edit().remove("dem_$app").apply()
    }
    fun seulementDemarrageChoisi(c: Context, app: String): Boolean = carnet(c).contains("dem_$app")

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

    // ---------------- LE TIRAGE « ÉQUILIBRÉ » DES VARIANTES ----------------
    // Chaque variante a un compteur d'attente : le nombre d'ouvertures depuis qu'elle
    // n'est pas passée. Son poids au tirage = 1 + attente (plafonné à ATTENTE_MAX).
    // Quand une variante se joue, son compteur repart à 0 (elle a moins de chances de
    // retomber) et celui des autres monte de 1 (elles ont plus de chances).
    // Exemple avec 5 variantes : juste après « A », A pèse 1 et les 4 autres pèsent 2,
    // donc A n'a plus que 1 chance sur 9 (11 %) et chaque autre 2 sur 9 (22 %).
    private const val ATTENTE_MAX = 6

    // Lit les compteurs, rangés sous la forme "0,2,1,3" (un nombre par variante).
    private fun attentes(c: Context, app: String, nombre: Int): IntArray {
        val brut = carnet(c).getString("attente_$app", null)?.split(",")?.map { it.toIntOrNull() ?: 0 }
        // si le nombre de variantes a changé (une ajoutée ou retirée), on repart de zéro
        return if (brut != null && brut.size == nombre) brut.toIntArray() else IntArray(nombre)
    }

    // Tire une variante parmi « permises » (les cochées) et met les compteurs à jour.
    // nombre = le nombre total de variantes de l'app.
    fun tirerVariante(c: Context, app: String, nombre: Int, permises: List<Int>): Int {
        val att = attentes(c, app, nombre)
        val poids = permises.map { 1 + att[it].coerceAtMost(ATTENTE_MAX) }
        // tirage pondéré : on lance un « dé » entre 0 et la somme des poids,
        // puis on avance dans la liste jusqu'à dépasser le nombre tiré
        var de = kotlin.random.Random.nextInt(poids.sum())
        var choisie = permises.last()
        for (k in permises.indices) {
            if (de < poids[k]) { choisie = permises[k]; break }
            de -= poids[k]
        }
        // mise à jour : la gagnante repart à 0, les autres cochées attendent un tour de plus
        for (v in permises) att[v] = if (v == choisie) 0 else (att[v] + 1).coerceAtMost(ATTENTE_MAX)
        carnet(c).edit().putString("attente_$app", att.joinToString(",")).apply()
        return choisie
    }
}
