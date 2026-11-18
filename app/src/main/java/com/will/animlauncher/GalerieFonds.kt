package com.will.animlauncher

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// LA GALERIE DE FONDS D'ÉCRAN (18/11) : des fonds proposés par l'app, téléchargés à la demande.
//
// OÙ SONT LES IMAGES ? Pas dans l'app (elle doit rester légère, ~5 Mo) : sur GitHub, dans la branche
// « galerie » du dépôt. Chaque DOSSIER de cette branche est une CATÉGORIE (Nature, Night, Japan…),
// et chaque image dans un dossier est un fond. Pour ajouter un fond, Will dépose simplement une image
// dans le bon dossier sur github.com : l'app la voit toute seule, sans nouvelle version.
//
// COMMENT L'APP SAIT CE QU'IL Y A ? Elle demande à GitHub la liste de TOUS les fichiers de la branche
// en UNE seule question (« l'arbre » de la branche). GitHub limite ces questions (60 par heure et par
// téléphone sans compte) : on garde donc la réponse 6 heures dans un carnet, et on la réutilise même
// périmée si Internet ne répond pas.
//
// LES FICHIERS SUR LE TÉLÉPHONE :
//  - cache/galerie : les images téléchargées pour AFFICHER la galerie (Android peut les effacer s'il
//    manque de place : on les retéléchargera) ;
//  - files/galerie : les images CHOISIES (appui sur une vignette) : elles restent, car le fond d'écran
//    posé en a besoin tout le temps.
// LES « DOSSIERS PRÊTS » (19/11, demande de Will) : un dossier de la branche dont le nom commence par
// « Dossier_ » (ex. « Dossier_Anime_Landscapes ») n'est PAS une catégorie : c'est une collection toute faite.
// Dans la galerie, il apparaît comme une grande carte ; un appui met TOUTES ses images dans le « dossier de
// fonds » du téléphone (DossierFond.kt), le fond qui change à chaque retour sur l'accueil.
//
// Chaque fichier est nommé d'après son « sha » (l'empreinte que GitHub donne au contenu) : si Will
// remplace une image par une autre du même nom, l'empreinte change et l'app télécharge la nouvelle.
object GalerieFonds {

    private const val DEPOT = "komekolabo/animluncher"
    private const val BRANCHE = "galerie"
    private const val ARBRE = "https://api.github.com/repos/$DEPOT/git/trees/$BRANCHE?recursive=1"
    private const val BRUT = "https://raw.githubusercontent.com/$DEPOT/$BRANCHE/"
    private const val GARDER_MS = 6 * 60 * 60 * 1000L        // la liste est gardée 6 h

    // Les sortes de fichiers acceptées (les mêmes que l'import : image, GIF, vidéo sans son).
    private val EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "mp4")

    // Un fond de la galerie : son chemin sur GitHub (« Nature/lac.jpg »), sa catégorie (le dossier),
    // son empreinte et sa taille en octets.
    class Fond(val chemin: String, val categorie: String, val sha: String, val octets: Long) {
        val extension: String get() = chemin.substringAfterLast('.', "jpg").lowercase()
        val nomFichier: String get() = "$sha.$extension"
        // (19/11) vrai si ce fond fait partie d'un « dossier prêt » (et non d'une catégorie)
        val estDossierPret: Boolean get() = categorie.lowercase().startsWith(GalerieFonds.PREFIXE_DOSSIER)
    }

    const val PREFIXE_DOSSIER = "dossier_"

    // Le résultat d'une lecture de la liste.
    //  - fonds : la liste (vide si la galerie est vide OU si on n'a rien pu lire) ;
    //  - horsLigne : vrai si Internet n'a pas répondu ET qu'on n'avait aucune liste en réserve.
    class Liste(val fonds: List<Fond>, val horsLigne: Boolean)

    private fun carnet(c: Context) = c.getSharedPreferences("galerie", Context.MODE_PRIVATE)

    // ------------------------------ LA LISTE ------------------------------

    // Lit la liste des fonds (À LANCER DANS UN THREAD : ça peut demander à Internet).
    // « forcer » : redemander à GitHub même si la liste gardée est récente (bouton « Réessayer »).
    fun liste(c: Context, forcer: Boolean = false): Liste {
        val p = carnet(c)
        val gardee = p.getString("arbre", null)
        val age = System.currentTimeMillis() - p.getLong("arbre_ms", 0L)
        if (!forcer && gardee != null && age < GARDER_MS) return Liste(lire(gardee), false)
        val fraiche = try { telechargerTexte(ARBRE) } catch (_: Exception) { null }
        if (fraiche != null) {
            p.edit().putString("arbre", fraiche).putLong("arbre_ms", System.currentTimeMillis()).apply()
            return Liste(lire(fraiche), false)
        }
        // Internet ne répond pas : on se contente de l'ancienne liste, s'il y en a une
        return if (gardee != null) Liste(lire(gardee), false) else Liste(emptyList(), true)
    }

    // Transforme la réponse de GitHub (du JSON) en liste de fonds.
    // On ne garde que les fichiers rangés DANS un dossier (« Nature/lac.jpg ») avec une extension connue :
    // les fichiers d'explication (LISEZMOI.md) et ceux posés à la racine sont ignorés.
    private fun lire(json: String): List<Fond> {
        val sortie = mutableListOf<Fond>()
        try {
            val arbre = JSONObject(json).optJSONArray("tree") ?: return sortie
            for (i in 0 until arbre.length()) {
                val o = arbre.getJSONObject(i)
                if (o.optString("type") != "blob") continue          // « blob » = un fichier (pas un dossier)
                val chemin = o.optString("path")
                val parties = chemin.split("/")
                if (parties.size != 2) continue                      // exactement « Dossier/fichier »
                val ext = chemin.substringAfterLast('.', "").lowercase()
                if (ext !in EXTENSIONS) continue
                sortie.add(Fond(chemin, parties[0], o.optString("sha"), o.optLong("size")))
            }
        } catch (_: Exception) {}
        return sortie
    }

    // Les catégories, dans un ordre agréable : d'abord celles qu'on connaît (dans l'ordre de ORDRE),
    // puis les autres par ordre alphabétique.
    private val ORDRE = listOf("nature", "mountains", "lakes", "countryside", "forest", "japan", "winter", "night")
    fun categories(fonds: List<Fond>): List<String> =
        fonds.filter { !it.estDossierPret }.map { it.categorie }.distinct().sortedWith(compareBy({
            val i = ORDRE.indexOf(it.lowercase()); if (i < 0) ORDRE.size else i
        }, { it.lowercase() }))

    // Le nom affiché d'une catégorie, dans la langue du téléphone (le nom du dossier s'il est inconnu).
    fun nomCategorie(c: Context, dossier: String): String = when (dossier.lowercase()) {
        "nature" -> c.getString(R.string.gal_cat_nature)
        "mountains" -> c.getString(R.string.gal_cat_montagnes)
        "lakes" -> c.getString(R.string.gal_cat_lacs)
        "countryside" -> c.getString(R.string.gal_cat_campagne)
        "forest" -> c.getString(R.string.gal_cat_foret)
        "japan" -> c.getString(R.string.gal_cat_japon)
        "winter" -> c.getString(R.string.gal_cat_hiver)
        "night" -> c.getString(R.string.gal_cat_nuit)
        "sea" -> c.getString(R.string.gal_cat_mer)   // (18/11) mer, plages, vagues, côtes
        else -> dossier.replace('_', ' ').replace('-', ' ')
    }

    // (19/11) Les « dossiers prêts » : le nom du dossier GitHub -> ses fonds (dans l'ordre alphabétique des noms).
    fun dossiersPrets(fonds: List<Fond>): List<Pair<String, List<Fond>>> =
        fonds.filter { it.estDossierPret }.groupBy { it.categorie }.toList()
            .sortedBy { it.first.lowercase() }.map { (nom, l) -> nom to l.sortedBy { it.chemin } }

    // Le nom affiché d'un dossier prêt : traduit s'il est connu, sinon le nom du dossier sans « Dossier_ ».
    fun nomDossierPret(c: Context, dossier: String): String {
        val reste = dossier.substring(PREFIXE_DOSSIER.length)
        return when (reste.lowercase()) {
            "anime_landscapes" -> c.getString(R.string.gal_pret_paysages)
            else -> reste.replace('_', ' ').replace('-', ' ')
        }
    }

    // ------------------------------ LES FICHIERS ------------------------------

    private fun dossierCache(c: Context) = File(c.cacheDir, "galerie").apply { mkdirs() }
    private fun dossierChoisis(c: Context) = File(c.filesDir, "galerie").apply { mkdirs() }

    // Le fichier d'un fond déjà sur le téléphone (choisi en priorité, sinon dans le cache), ou null.
    fun local(c: Context, f: Fond): File? =
        File(dossierChoisis(c), f.nomFichier).takeIf { it.length() > 0 }
            ?: File(dossierCache(c), f.nomFichier).takeIf { it.length() > 0 }

    // Pour la VIGNETTE : le fichier du fond, téléchargé dans le cache s'il le faut (DANS UN THREAD).
    fun pourVignette(c: Context, f: Fond): File? =
        local(c, f) ?: File(dossierCache(c), f.nomFichier).takeIf { telechargerFichier(f, it) }

    // Pour POSER le fond : le fichier rangé dans files/galerie, là où il ne sera pas effacé (DANS UN THREAD).
    // S'il est déjà dans le cache, on le déplace (pas besoin de le retélécharger).
    fun pourFond(c: Context, f: Fond): File? {
        val final = File(dossierChoisis(c), f.nomFichier)
        if (final.length() > 0) return final
        val cache = File(dossierCache(c), f.nomFichier)
        if (cache.length() > 0) {
            if (cache.renameTo(final)) return final
            try { cache.copyTo(final, overwrite = true); return final } catch (_: Exception) {}
        }
        return final.takeIf { telechargerFichier(f, it) }
    }

    // ------------------------------ INTERNET ------------------------------

    private fun ouvrir(adresse: String): HttpURLConnection =
        (URL(adresse).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "AnimEngine")       // GitHub refuse les questions sans « User-Agent »
        }

    private fun telechargerTexte(adresse: String): String? {
        val cx = ouvrir(adresse)
        cx.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (cx.responseCode != 200) return null
            return cx.inputStream.bufferedReader().use { it.readText() }
        } finally { cx.disconnect() }
    }

    // Télécharge un fond dans « vers ». On écrit d'abord un fichier « .part » : si c'est coupé en route,
    // on ne garde pas une image à moitié. On vérifie la taille annoncée par GitHub.
    private fun telechargerFichier(f: Fond, vers: File): Boolean {
        val partiel = File(vers.path + ".part")
        try {
            // chaque morceau du chemin est « encodé » (les espaces et accents passent mal dans une adresse)
            val adresse = BRUT + f.chemin.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
            val cx = ouvrir(adresse)
            try {
                if (cx.responseCode != 200) return false
                cx.inputStream.use { entree -> partiel.outputStream().use { entree.copyTo(it, 64 * 1024) } }
            } finally { cx.disconnect() }
            if (f.octets > 0 && partiel.length() != f.octets) { partiel.delete(); return false }
            return partiel.renameTo(vers)
        } catch (_: Exception) {
            partiel.delete()
            return false
        }
    }
}
