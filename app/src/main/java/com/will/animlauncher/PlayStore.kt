package com.will.animlauncher

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// LA CATÉGORIE DU PLAY STORE (18/11, demande de Will).
// Quand le protocole de classement (Classement.kt) ne trouve AUCUN indice sur le téléphone pour une app (nom de
// marque inventé, rien de déclaré à Android…), on demande au Play Store : chaque app y a une catégorie officielle
// (Sports, Travel & Local, Education…). On lit la page de l'app sur play.google.com et on y cherche cette catégorie.
//  - On ne demande QUE pour les apps restées sans catégorie (pas besoin d'Internet pour les autres) ;
//  - la réponse est gardée dans le carnet « playstore » : on ne redemande jamais une app trouvée ;
//  - une app absente du Play Store (installée à la main, comme microG) est notée « ? » et redemandée 7 jours après ;
//  - sans réseau, rien n'est noté : on réessaiera plus tard.
// Ce que le Play Store envoie : seulement la page publique de l'app (on n'envoie que son nom technique).
object PlayStore {
    private const val CARNET = "playstore"
    private const val INCONNU = "?"
    private const val ATTENTE = 7L * 24 * 3600 * 1000          // 7 jours, en millisecondes

    // Le groupe AnimEngine d'une app d'après le Play Store, si on le connaît déjà (sinon null).
    fun groupe(c: Context, pkg: String): String? {
        val brut = c.getSharedPreferences(CARNET, Context.MODE_PRIVATE).getString(pkg, null) ?: return null
        val categorie = brut.substringBefore('|')
        return if (categorie == INCONNU) null else versGroupe(categorie)
    }

    // Demande au Play Store la catégorie de ces apps (À APPELER EN ARRIÈRE-PLAN : ça passe par Internet).
    // Renvoie true si au moins une app a reçu une catégorie.
    fun demander(c: Context, paquets: List<String>): Boolean {
        val carnet = c.getSharedPreferences(CARNET, Context.MODE_PRIVATE)
        val maintenant = System.currentTimeMillis()
        var trouve = false
        for (pkg in paquets) {
            val brut = carnet.getString(pkg, null)
            if (brut != null) {
                if (!brut.startsWith(INCONNU)) continue                       // déjà connue
                val quand = brut.substringAfter('|', "0").toLongOrNull() ?: 0L
                if (maintenant - quand < ATTENTE) continue                   // introuvable il y a moins de 7 jours
            }
            val categorie = lire(pkg) ?: continue                             // pas de réseau : on réessaiera
            if (categorie.isEmpty() || versGroupe(categorie) == null) {
                carnet.edit().putString(pkg, "$INCONNU|$maintenant").apply()
            } else {
                carnet.edit().putString(pkg, categorie).apply()
                trouve = true
            }
        }
        return trouve
    }

    // Lit la page de l'app sur le Play Store. Renvoie sa catégorie (« SPORTS », « GAME_PUZZLE »…), "" si l'app n'y
    // est pas, ou null si on n'a pas pu se connecter.
    private fun lire(pkg: String): String? {
        var connexion: HttpURLConnection? = null
        return try {
            val adresse = URL("https://play.google.com/store/apps/details?id=" + URLEncoder.encode(pkg, "UTF-8") + "&hl=en")
            val cx = (adresse.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 10000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
                setRequestProperty("Accept-Language", "en")
            }
            connexion = cx
            val code = cx.responseCode
            if (code == 404) return ""
            if (code != 200) return null
            // la page peut être longue : on en lit 3 millions de caractères au plus
            val texte = StringBuilder()
            cx.inputStream.bufferedReader().use { lecteur ->
                val tampon = CharArray(16384)
                while (texte.length < 3_000_000) {
                    val n = lecteur.read(tampon)
                    if (n < 0) break
                    texte.append(tampon, 0, n)
                }
            }
            chercher(texte) ?: ""
        } catch (_: Exception) {
            null
        } finally {
            connexion?.disconnect()
        }
    }

    // La catégorie dans la page : d'abord la fiche lisible par les machines (« applicationCategory »), sinon le
    // premier lien vers une catégorie (« /store/apps/category/SPORTS »), sauf « FAMILY » (le menu « Enfants »).
    private fun chercher(t: CharSequence): String? {
        Regex("\"applicationCategory\"\\s*:\\s*\"([A-Z_]+)\"").find(t)?.let { return it.groupValues[1] }
        return Regex("/store/apps/category/([A-Z_]+)").findAll(t).map { it.groupValues[1] }
            .firstOrNull { !it.startsWith("FAMILY") }
    }

    // LA CORRESPONDANCE entre les catégories du Play Store et les groupes d'AnimEngine (voir Groupes.kt).
    fun versGroupe(categorie: String): String? {
        if (categorie.startsWith("GAME")) return "jeux"                      // tous les jeux (GAME_PUZZLE, GAME_ACTION…)
        return when (categorie) {
            "SOCIAL", "COMMUNICATION", "DATING" -> "social"
            "MUSIC_AND_AUDIO", "VIDEO_PLAYERS", "ENTERTAINMENT" -> "musique"
            "PHOTOGRAPHY", "ART_AND_DESIGN" -> "photo"
            "FINANCE" -> "finance"
            "MAPS_AND_NAVIGATION", "TRAVEL_AND_LOCAL", "AUTO_AND_VEHICLES" -> "trajets"
            "PRODUCTIVITY", "BUSINESS" -> "travail"
            "HEALTH_AND_FITNESS", "MEDICAL" -> "sante"
            "SHOPPING" -> "shopping"
            "FOOD_AND_DRINK" -> "cuisine"
            "SPORTS" -> "sport"
            "EDUCATION", "BOOKS_AND_REFERENCE", "COMICS" -> "ecole"
            "HOUSE_AND_HOME", "LIFESTYLE", "PARENTING", "BEAUTY", "EVENTS" -> "maison"
            "TOOLS", "PERSONALIZATION", "LIBRARIES_AND_DEMO" -> "reglages"
            "NEWS_AND_MAGAZINES", "WEATHER" -> "infos"
            else -> null
        }
    }
}
