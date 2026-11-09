package com.will.animlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

// LES « RAYONS » : toutes les apps du téléphone rangées par catégorie, pour les écrans qui montrent
// des barres de catégories (ici : « Logos animés » ; l'écran « Animations d'apps » suit la même règle).
// La différence avec Groupes.kt : ici, AUCUNE app n'est laissée de côté. Celles dont on ne connaît
// pas la catégorie vont dans le rayon « autres » (affiché « Non répertorié »).
object Rayons {

    const val AUTRES = "autres"

    // Le nom d'une catégorie, dans la langue de l'app (fichiers res/values*/categories.xml).
    fun nom(c: Context, id: String): String = c.getString(when (id) {
        "ia" -> R.string.cat_ia
        "social" -> R.string.cat_social
        "musique" -> R.string.cat_musique
        "photo" -> R.string.cat_photo
        "jeux" -> R.string.cat_jeux
        "finance" -> R.string.cat_finance
        "trajets" -> R.string.cat_trajets
        "travail" -> R.string.cat_travail
        "sante" -> R.string.cat_sante
        "shopping" -> R.string.cat_shopping
        "reglages" -> R.string.cat_reglages
        "infos" -> R.string.cat_infos
        else -> R.string.cat_autres
    })

    // La catégorie d'une app : 1) le choix de l'utilisateur (une app ajoutée à la main à un dossier),
    // 2) les tests de Groupes.trouver (apps connues, catégorie Android, mots du nom), 3) sinon « autres ».
    // (Partagée avec l'écran « Animations d'apps » : les deux écrans rangent les apps pareil.)
    fun categorieDe(c: Context, pkg: String, nom: String, ajouts: Map<String, Set<String>>): String {
        val code = try { c.packageManager.getApplicationInfo(pkg, 0).category }
                   catch (_: Exception) { ApplicationInfo.CATEGORY_UNDEFINED }
        val trouvee = Groupes.CATEGORIES.firstOrNull { pkg in (ajouts[it.id] ?: emptySet()) }
            ?: Groupes.trouver(pkg, code, nom)
        return trouvee?.id ?: AUTRES
    }

    // Les apps du rangement de l'utilisateur (ajoutées à la main à un dossier), pour categorieDe.
    fun ajouts(c: Context): Map<String, Set<String>> = Groupes.CATEGORIES.associate { it.id to Groupes.ajoutees(c, it.id) }

    // LA RECHERCHE (corrigée le 09/11) : est-ce que le nom d'une app correspond à ce qu'on a tapé,
    // et à quel point ? On cherche dans le NOM seulement (avant, on cherchait aussi dans le nom
    // technique : « gp » trouvait « Sound Connect » à cause de « com.sony.songpal »).
    //   0 = le nom COMMENCE par le texte (« in » → Instagram) : le meilleur, en premier ;
    //   1 = un AUTRE mot du nom commence par le texte (« gp » → « Chat GPT ») ;
    //   2 = le texte est quelque part au milieu (« gp » → « ChatGPT ») ;
    //  -1 = pas du tout.
    // « nom » et « cherche » sont déjà en minuscules et sans accents.
    fun rang(nom: String, cherche: String): Int = when {
        nom.startsWith(cherche) -> 0
        nom.split(Regex("[^\\p{L}\\p{N}]+")).any { it.startsWith(cherche) } -> 1
        nom.contains(cherche) -> 2
        else -> -1
    }

    // Toutes les apps qui ont une icône, rangées : pour chaque catégorie (dans l'ordre de Groupes.kt,
    // puis « autres »), ses apps par ordre alphabétique. Une catégorie vide n'est pas dans le résultat.
    // (« LinkedHashMap » = un dictionnaire qui garde l'ordre dans lequel on l'a rempli.)
    fun ranger(c: Context): LinkedHashMap<String, List<String>> {
        val pm = c.packageManager
        val ajouts = ajouts(c)
        val intention = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val parCategorie = pm.queryIntentActivities(intention, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString().trim() }
            .filter { it.first != c.packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
            .groupBy({ categorieDe(c, it.first, it.second, ajouts) }, { it.first })
        val resultat = LinkedHashMap<String, List<String>>()
        for (id in Groupes.CATEGORIES.map { it.id } + AUTRES) {
            val apps = parCategorie[id] ?: continue
            resultat[id] = apps
        }
        return resultat
    }
}
