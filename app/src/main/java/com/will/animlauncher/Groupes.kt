package com.will.animlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

// LES GROUPES D'APPS (09/11) : AnimLauncher range tout seul les apps du téléphone par catégorie
// (IA, réseaux sociaux, jeux…), et propose pour chaque groupe un widget « dossier animé »
// (voir WidgetGroupe.kt et DossierActivity.kt).
//
// COMMENT ON TROUVE LA CATÉGORIE D'UNE APP :
//  1) d'abord NOTRE liste d'apps connues (plus précise : Android ne connaît pas la catégorie « IA »,
//     ni « Finance ») ;
//  2) sinon, la catégorie que l'app déclare elle-même à Android (jeu, musique, photo, plans…).
//     Beaucoup d'apps n'en déclarent aucune : elles restent alors hors des groupes.
// L'utilisateur peut ensuite décocher une app d'un groupe (elle est retenue dans le carnet « groupes »).
// Les noms des groupes sont en anglais, comme tous les textes affichés dans les animations.
object Groupes {

    // Une catégorie : son identifiant (pour les réglages), son nom affiché, nos apps connues,
    // et les catégories Android qui vont avec.
    class Categorie(val id: String, val nom: String, val connues: Set<String>, val android: Set<Int> = emptySet())

    val CATEGORIES = listOf(
        Categorie("ia", "AI", setOf(
            "com.anthropic.claude", "com.anthropic.aibo", "com.openai.chatgpt", "com.google.android.apps.bard",
            "ai.x.grok", "ai.perplexity.app.android", "com.microsoft.copilot", "ai.mistral.chat",
            "com.deepseek.chat", "com.google.android.apps.googleassistant", "com.suno.android")),
        Categorie("social", "Social", setOf(
            "com.whatsapp", "com.instagram.android", "com.twitter.android", "com.discord",
            "com.samsung.android.messaging", "com.google.android.apps.messaging", "com.facebook.katana",
            "com.facebook.orca", "com.snapchat.android", "org.telegram.messenger", "com.zhiliaoapp.musically",
            "com.reddit.frontpage", "com.linkedin.android", "org.thoughtcrime.securesms",
            "com.instagram.barcelona", "xyz.blueskyweb.app", "com.google.android.apps.tachyon"),
            setOf(ApplicationInfo.CATEGORY_SOCIAL)),
        Categorie("musique", "Music & Video", setOf(
            "com.spotify.music", "app.revanced.android.youtube", "com.google.android.youtube",
            "com.netflix.mediaclient", "deezer.android.app", "tv.twitch.android.app",
            "com.jonathanpuckey.radiogarden", "com.samsung.android.tvplus", "com.google.android.videos",
            "com.sony.songpal.mdr", "com.sec.android.app.voicenote"),
            setOf(ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO)),
        Categorie("photo", "Photos", setOf(
            "com.sec.android.gallery3d", "com.google.android.apps.photos", "com.sec.android.app.camera",
            "io.github.libresprite"),
            setOf(ApplicationInfo.CATEGORY_IMAGE)),
        Categorie("jeux", "Games", setOf(
            "com.playstack.balatro.android", "com.supercell.clashofclans", "com.chess",
            "com.robtopx.geometryjumplite", "com.robtopx.geometrydashmeltdown", "com.BlackSaltGames.DREDGE",
            "com.dsemu.drastic", "com.chessbar", "com.strayfawnstudio.gambonanza", "com.venezianoapp.frusta",
            "com.google.android.play.games", "tv.parsec.client"),
            setOf(ApplicationInfo.CATEGORY_GAME)),
        Categorie("finance", "Finance", setOf(
            "com.revolut.revolut", "com.fullsix.android.labanquepostale.accountaccess", "nl.degiro.trader",
            "de.traderepublic.app", "com.kraken.invest.app", "com.ledger.live", "com.kraken.pay.app",
            "com.bankeen", "eu.epicompany.wero.wallet", "zapsolutions.strike", "com.cakewallet.cake_wallet",
            "network.bisq.mobile.node", "com.btcframe", "com.microstrategy.stream",
            "com.google.android.apps.walletnfcrel", "com.paypal.android.p2pmobile", "com.lydia",
            "com.boursorama.android.clients", "fr.creditagricole.androidapp", "net.bnpparibas.mescomptes",
            "com.binance.dev", "com.coinbase.android")),
        Categorie("trajets", "Maps & Travel", setOf(
            "com.google.android.apps.maps", "ee.mtakso.client", "com.fabernovel.ratp", "com.airbnb.android",
            "com.ubercab", "com.waze", "com.google.earth", "com.vsct.vsc.mobile.horaireetresa.android",
            "fr.astillia.SigtaoMobile", "com.samsung.android.app.find"),
            setOf(ApplicationInfo.CATEGORY_MAPS)),
        Categorie("travail", "Productivity", setOf(
            "com.google.android.gm", "com.google.android.apps.docs", "com.google.android.apps.docs.editors.docs",
            "com.samsung.android.app.notes", "com.samsung.android.calendar", "com.samsung.android.app.reminder",
            "com.microsoft.skydrive", "com.github.android", "com.google.android.apps.translate",
            "com.sec.android.app.myfiles"),
            setOf(ApplicationInfo.CATEGORY_PRODUCTIVITY)),
        Categorie("infos", "News", emptySet(), setOf(ApplicationInfo.CATEGORY_NEWS)),
    )

    fun categorie(id: String): Categorie? = CATEGORIES.firstOrNull { it.id == id }

    // Le nom affiché d'une app (« Claude »), ou son nom technique si on ne le trouve pas.
    fun nomApp(c: Context, pkg: String): String = try {
        val pm = c.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    // TOUTES les apps du téléphone qui ont une icône, rangées par catégorie (avant les choix de
    // l'utilisateur). Les apps sans catégorie ne sont pas dans le résultat.
    fun detecter(c: Context): Map<String, List<String>> {
        val pm = c.packageManager
        val intention = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val paquets = pm.queryIntentActivities(intention, 0).map { it.activityInfo.packageName }
            .distinct().filter { it != c.packageName }
        val rangees = LinkedHashMap<String, MutableList<String>>()
        for (pkg in paquets) {
            val code = try { pm.getApplicationInfo(pkg, 0).category } catch (_: Exception) { ApplicationInfo.CATEGORY_UNDEFINED }
            val cat = CATEGORIES.firstOrNull { pkg in it.connues }
                ?: CATEGORIES.firstOrNull { code in it.android }
                ?: continue
            rangees.getOrPut(cat.id) { mutableListOf() }.add(pkg)
        }
        // Les apps de chaque groupe, par ordre alphabétique de leur nom affiché.
        return CATEGORIES.mapNotNull { cat ->
            rangees[cat.id]?.let { liste -> cat.id to liste.map { it to nomApp(c, it).lowercase() }.sortedBy { it.second }.map { it.first } }
        }.toMap()
    }

    // TOUTES les apps d'un groupe : celles trouvées automatiquement + celles que l'utilisateur a ajoutées
    // avec le « + » (seulement si elles sont encore installées), par ordre alphabétique.
    fun tous(c: Context, id: String): List<String> {
        val pm = c.packageManager
        val ajout = ajoutees(c, id).filter { pm.getLaunchIntentForPackage(it) != null }
        return ((detecter(c)[id] ?: emptyList()) + ajout).distinct()
            .map { it to nomApp(c, it).lowercase() }.sortedBy { it.second }.map { it.first }
    }

    // Un dossier contient au plus 60 apps (5 pages de 12), choix de Will le 09/11.
    const val MAX = 60

    // Les apps du dossier : toutes, sauf celles que l'utilisateur a décochées (60 au plus), dans l'ORDRE
    // choisi par l'utilisateur (mode édition du dossier) ; les apps jamais rangées vont à la fin.
    fun apps(c: Context, id: String): List<String> {
        val sans = retirees(c, id)
        val ordre = ordre(c, id)
        return tous(c, id).filter { it !in sans }
            .sortedBy { val i = ordre.indexOf(it); if (i < 0) Int.MAX_VALUE else i }      // tri « stable » : l'alphabet départage
            .take(MAX)
    }

    // L'ordre des apps dans le dossier (retenu quand on les déplace en mode édition).
    fun ordre(c: Context, id: String): List<String> =
        c.getSharedPreferences(CARNET, Context.MODE_PRIVATE).getString("ordre_$id", null)
            ?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

    fun definirOrdre(c: Context, id: String, apps: List<String>) {
        c.getSharedPreferences(CARNET, Context.MODE_PRIVATE).edit().putString("ordre_$id", apps.joinToString(",")).apply()
    }

    // LE CARNET « groupes » : pour chaque groupe, les apps décochées (« sans_… ») et les apps
    // ajoutées à la main (« avec_… »).
    private const val CARNET = "groupes"
    fun retirees(c: Context, id: String): Set<String> =
        c.getSharedPreferences(CARNET, Context.MODE_PRIVATE).getStringSet("sans_$id", emptySet()) ?: emptySet()

    fun ajoutees(c: Context, id: String): Set<String> =
        c.getSharedPreferences(CARNET, Context.MODE_PRIVATE).getStringSet("avec_$id", emptySet()) ?: emptySet()

    // Ajoute une app au groupe (le « + » du dossier) : elle y entre, cochée.
    fun ajouter(c: Context, id: String, pkg: String) {
        val carnet = c.getSharedPreferences(CARNET, Context.MODE_PRIVATE)
        val avec = HashSet(ajoutees(c, id)).apply { add(pkg) }
        val sans = HashSet(retirees(c, id)).apply { remove(pkg) }
        carnet.edit().putStringSet("avec_$id", avec).putStringSet("sans_$id", sans).apply()
    }

    fun definir(c: Context, id: String, pkg: String, dedans: Boolean) {
        val carnet = c.getSharedPreferences(CARNET, Context.MODE_PRIVATE)
        val sans = HashSet(retirees(c, id))      // une copie : Android interdit de modifier l'original
        if (dedans) sans.remove(pkg) else sans.add(pkg)
        carnet.edit().putStringSet("sans_$id", sans).apply()
    }
}
