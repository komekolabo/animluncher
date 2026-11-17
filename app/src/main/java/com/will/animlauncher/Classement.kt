package com.will.animlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

// LE PROTOCOLE DE CLASSEMENT (17/11, demande de Will) : dans quelle catégorie ranger une app ?
// Avant, on avait 3 tests (notre liste, la catégorie déclarée à Android, des mots du nom) et beaucoup d'apps
// finissaient en « Non répertorié ». Maintenant, on pose les questions UNE PAR UNE, de la plus sûre à la
// moins sûre, et on s'arrête à la première qui répond :
//   1. LISTE     : l'app est dans notre liste d'apps connues (Groupes.kt) ;
//   2. ÉDITEUR   : son nom technique commence comme celui d'un éditeur connu (« com.supercell. » → Jeux,
//                  « com.boursorama. » → Finance…). Le début le plus long gagne (« com.ubercab.eats » bat « com.ubercab ») ;
//   3. ANDROID   : l'app a déclaré sa catégorie à Android (jeu, audio, vidéo, cartes…) ;
//   4. MOTEUR    : son écran de départ vient d'un moteur de jeu (Unity, Unreal, Godot…) : c'est un jeu ;
//   5. RÔLE      : l'app se propose à Android pour un rôle (lecteur de musique, galerie, appareil photo,
//                  navigateur, messagerie, agenda, calculatrice, météo…) ;
//   6. NOM       : un mot de son nom (« Photo Editor » → Photos). Si plusieurs catégories répondent, le mot
//                  le plus long gagne (« Workout » → Santé, pas « work » → Travail) ;
//   7. PAQUET    : un mot de son nom technique (« com.gameloft… » commence par « game ») ;
//   8. SERVICE   : l'app fait tourner un service typique (lecteur de médias → Musique, VPN ou clavier → Réglages).
// Sinon : pas de catégorie (« Non répertorié »). Et AVANT tout ça, le choix de l'utilisateur (une app ajoutée à
// la main à un dossier) l'emporte toujours (voir categorieFinale).
// Le résultat est gardé en mémoire (calculer demande plusieurs choses à Android) et oublié quand une app est
// installée, retirée ou mise à jour (voir Groupes.oublier).
object Classement {

    // Comment l'app a été classée (montré dans l'écran « Apps détectées »)
    const val CHOIX = "choix"
    const val LISTE = "liste"
    const val EDITEUR = "editeur"
    const val ANDROID = "android"
    const val MOTEUR = "moteur"
    const val ROLE = "role"
    const val NOM = "nom"
    const val PAQUET = "paquet"
    const val SERVICE = "service"

    // La mémoire : nom technique -> « catégorie|comment », ou "" si aucune catégorie.
    private val memoire = ConcurrentHashMap<String, String>()
    private val lanceables = ConcurrentHashMap<String, Boolean>()

    // Ce qu'Android nous dit de TOUTES les apps d'un coup (rôles, services, écrans d'accueil) : demandé une fois,
    // puis gardé 10 minutes. (« Volatile » : partagé sans risque entre les fils d'exécution.)
    private class Tables(val roles: Map<String, String>, val services: Map<String, String>, val accueils: Set<String>)
    @Volatile private var gardees: Tables? = null
    @Volatile private var tablesA = 0L
    private val verrou = Any()

    fun oublier() {
        memoire.clear()
        lanceables.clear()
        gardees = null
    }

    // Prépare tout en avance (appelée en arrière-plan au démarrage, voir Catalogue) : les tables, puis la
    // catégorie de chaque app qui a une icône. Ensuite, les réponses sont instantanées.
    fun preparer(c: Context) {
        try {
            tables(c)
            val intention = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            for (info in c.packageManager.queryIntentActivities(intention, 0)) {
                val pkg = info.activityInfo.packageName
                categorieFinale(c, pkg)
                lanceable(c, pkg)
            }
        } catch (_: Throwable) {}
    }

    // Pour Groupes.trouver (les dossiers, les rayons, les sons…) : la catégorie trouvée par le protocole.
    fun trouver(pkg: String, code: Int, nom: String): Groupes.Categorie? {
        val r = resultat(pkg, code, nom) ?: return null
        return Groupes.CATEGORIES.firstOrNull { it.id == r.first }
    }

    // LA CATÉGORIE FINALE d'une app : (catégorie, comment) ou null. Le choix de l'utilisateur d'abord.
    fun categorieFinale(c: Context, pkg: String): Pair<String, String>? {
        Groupes.CATEGORIES.firstOrNull { pkg in Groupes.ajoutees(c, it.id) }?.let { return it.id to CHOIX }
        memoire[pkg]?.let { return lire(it) }
        val code = try { c.packageManager.getApplicationInfo(pkg, 0).category } catch (_: Exception) { ApplicationInfo.CATEGORY_UNDEFINED }
        return resultat(pkg, code, Groupes.nomApp(c, pkg))
    }

    // Une app qu'on peut ouvrir depuis une icône (et qui n'est pas un écran d'accueil) : seules celles-là
    // reçoivent une animation de catégorie (pas le clavier, pas le lanceur, pas les morceaux du système).
    fun lanceable(c: Context, pkg: String): Boolean = lanceables.getOrPut(pkg) {
        try {
            c.packageManager.getLaunchIntentForPackage(pkg) != null && pkg !in tables(c).accueils
        } catch (_: Throwable) { false }
    }

    private fun lire(texte: String): Pair<String, String>? =
        if (texte.isEmpty()) null else texte.substringBefore('|') to texte.substringAfter('|')

    private fun resultat(pkg: String, code: Int, nom: String): Pair<String, String>? {
        memoire[pkg]?.let { return lire(it) }
        val r = try { calculer(pkg, code, nom) } catch (_: Throwable) { null }
        memoire[pkg] = if (r == null) "" else "${r.first}|${r.second}"
        return r
    }

    // LES 8 QUESTIONS, dans l'ordre.
    private fun calculer(pkg: String, code: Int, nom: String): Pair<String, String>? {
        val p = pkg.lowercase()
        val c = AnimApp.contexte
        // 1. notre liste
        Groupes.CATEGORIES.firstOrNull { pkg in it.connues }?.let { return it.id to LISTE }
        // 2. l'éditeur (le début le plus long gagne)
        EDITEURS.entries.filter { p.startsWith(it.key) }.maxByOrNull { it.key.length }?.let { return it.value to EDITEUR }
        // 3. ce que l'app a déclaré à Android
        Groupes.CATEGORIES.firstOrNull { code in it.android }?.let { return it.id to ANDROID }
        val info = try { c?.packageManager?.getApplicationInfo(pkg, 0) } catch (_: Exception) { null }
        @Suppress("DEPRECATION")
        val jeuDeclare = info != null && (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        if (jeuDeclare) return "jeux" to ANDROID
        // 4. un moteur de jeu
        if (c != null && moteurDeJeu(c, pkg)) return "jeux" to MOTEUR
        // 5. un rôle proposé à Android
        val t = if (c != null) tables(c) else null
        t?.roles?.get(pkg)?.let { return it to ROLE }
        // 6. les mots du nom
        parMots(mots(nom))?.let { return it to NOM }
        // 7. les mots du nom technique (sans les morceaux qui ne veulent rien dire : « com », « android »…)
        parMots(p.split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in VIDES })?.let { return it to PAQUET }
        // 8. un service typique
        t?.services?.get(pkg)?.let { return it to SERVICE }
        return null
    }

    // Le nom découpé en mots, en minuscules et sans accents : « Éditeur Photo » → [« editeur », « photo »].
    private fun mots(nom: String): List<String> =
        AppsActivity.sansAccents(nom).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    // La catégorie dont un mot-clé commence un des mots. S'il y en a plusieurs, le mot-clé le plus long gagne
    // (à égalité : la première catégorie dans l'ordre de Groupes.kt).
    private fun parMots(liste: List<String>): String? {
        if (liste.isEmpty()) return null
        var meilleure: String? = null
        var score = 0
        for (cat in Groupes.CATEGORIES) {
            val cles = cat.mots + (MOTS[cat.id] ?: emptySet())
            for (k in cles) {
                if (k.length > score && liste.any { it.startsWith(k) }) {
                    score = k.length
                    meilleure = cat.id
                }
            }
        }
        return meilleure
    }

    // L'écran de départ de l'app (celui que l'icône ouvre) vient-il d'un moteur de jeu ?
    private fun moteurDeJeu(c: Context, pkg: String): Boolean {
        if (pkg.startsWith("air.")) return true          // les jeux faits avec Adobe AIR
        val classe = try { c.packageManager.getLaunchIntentForPackage(pkg)?.component?.className } catch (_: Exception) { null }
            ?: return false
        val k = classe.lowercase()
        return MOTEURS.any { it in k }
    }

    // Les tables (rôles, services, écrans d'accueil). Une seule construction à la fois (« synchronized »).
    private fun tables(c: Context): Tables {
        val t = gardees
        if (t != null && SystemClock.elapsedRealtime() - tablesA < 600_000L) return t
        return synchronized(verrou) {
            val t2 = gardees
            if (t2 != null && SystemClock.elapsedRealtime() - tablesA < 600_000L) return t2
            val pm = c.packageManager
            val roles = HashMap<String, String>()
            for ((intention, id) in ROLES) {
                val liste = try { pm.queryIntentActivities(intention, 0) } catch (_: Exception) { emptyList() }
                for (info in liste) roles.putIfAbsent(info.activityInfo.packageName, id)
            }
            val services = HashMap<String, String>()
            for ((action, id) in SERVICES) {
                val liste = try { pm.queryIntentServices(Intent(action), 0) } catch (_: Exception) { emptyList() }
                for (info in liste) services.putIfAbsent(info.serviceInfo.packageName, id)
            }
            val accueils = try {
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                    .map { it.activityInfo.packageName }.toSet()
            } catch (_: Exception) { emptySet() }
            val nouvelles = Tables(roles, services, accueils)
            gardees = nouvelles
            tablesA = SystemClock.elapsedRealtime()
            nouvelles
        }
    }

    // ------------------------------------------------------------------------------------------------
    // LES LISTES. Tout est en minuscules et sans accents. Pour améliorer le classement : ajouter un mot ici.

    // 4. Les moteurs de jeu : un morceau du nom de l'écran de départ.
    private val MOTEURS = listOf(
        "com.unity3d.", "unityplayer", "com.epicgames.", "org.godotengine", "com.godot.game", "org.cocos2dx",
        "org.cocos2d", "com.yoyogames.", "com.dynamo.android", "gameactivity", "androidlauncher", "org.libsdl.app",
        "org.love2d", "com.badlogic.")

    // 5. Les rôles : « app principale de musique, de galerie… », l'appareil photo, le téléphone.
    // (Les catégories sont écrites en toutes lettres : certaines n'existent que depuis Android 10 ou 12.)
    private fun principale(categorie: String) = Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.$categorie")
    private val ROLES: List<Pair<Intent, String>> by lazy {
        listOf(
            principale("APP_MUSIC") to "musique",
            principale("APP_GALLERY") to "photo",
            Intent("android.media.action.STILL_IMAGE_CAMERA") to "photo",
            principale("APP_MAPS") to "trajets",
            principale("APP_MESSAGING") to "social",
            principale("APP_CONTACTS") to "social",
            Intent(Intent.ACTION_DIAL) to "social",
            principale("APP_EMAIL") to "travail",
            principale("APP_CALENDAR") to "travail",
            principale("APP_FILES") to "travail",
            principale("APP_CALCULATOR") to "reglages",
            principale("APP_MARKET") to "shopping",
            principale("APP_FITNESS") to "sante",
            principale("APP_WEATHER") to "infos",
            principale("APP_BROWSER") to "infos",
        )
    }

    // 8. Les services typiques.
    private val SERVICES = listOf(
        "android.media.browse.MediaBrowserService" to "musique",
        "androidx.media3.session.MediaSessionService" to "musique",
        "android.net.VpnService" to "reglages",
        "android.view.InputMethod" to "reglages",
        "android.service.wallpaper.WallpaperService" to "photo",
    )

    // 7. Les morceaux de nom technique qui ne disent rien de l'app.
    private val VIDES = setOf(
        "com", "org", "net", "android", "app", "apps", "mobile", "client", "lite", "pro", "free", "plus", "prod",
        "release", "main", "global", "official", "www", "sec", "samsung", "google", "huawei", "xiaomi", "miui",
        "oneplus", "sony", "lge", "htc", "motorola", "microsoft", "amazon", "apple")

    // 2. Les éditeurs : début du nom technique -> catégorie.
    private fun editeurs(id: String, vararg debuts: String) = debuts.map { it to id }
    private val EDITEURS: Map<String, String> = (
        editeurs("jeux", "com.supercell.", "com.king.", "com.ea.", "com.gameloft.", "com.miniclip.", "com.rovio.",
            "com.mojang.", "com.innersloth.", "com.roblox.", "com.kiloo.", "com.sybo.", "com.playrix.", "com.zynga.",
            "com.tencent.ig", "com.tencent.tmgp", "com.activision.", "com.nianticlabs.", "com.mihoyo.", "com.hoyoverse.",
            "com.netease.", "com.ubisoft.", "com.halfbrick.", "com.outfit7.", "com.imangi.", "com.ketchapp.",
            "com.voodoo.", "com.nintendo.", "com.square_enix.", "com.squareenix.", "com.bandainamcoent.", "com.sega.",
            "com.konami.", "com.scopely.", "com.moonactive.", "com.dts.freefire", "com.garena.", "com.pubg.",
            "com.epicgames.", "com.mobile.legends", "com.playtika.", "com.peoplefun.", "com.fingersoft.",
            "com.bigfishgames.", "com.glu.", "com.naturalmotion.", "com.nekki.", "com.rockstargames.",
            "com.feralinteractive.", "com.blizzard.", "com.riotgames.", "com.robtopx.", "com.lilithgame.", "com.yostar",
            "com.smilegate.", "com.com2us.", "com.netmarble.", "com.ncsoft.", "com.nexon.", "com.kabam.", "com.funplus.",
            "com.igg.", "com.tap4fun.", "com.habby.", "com.saygames.", "com.crazylabs.", "com.azurgames.",
            "com.kwalee.", "com.rollic.", "com.lionstudios.", "com.tinybuild.", "com.chess") +
        editeurs("finance", "com.boursorama.", "fr.creditagricole.", "net.bnpparibas.", "fr.lcl.", "fr.banquepopulaire.",
            "fr.caisseepargne", "mobi.societegenerale.", "com.societegenerale.", "fr.creditmutuel.", "com.cic.", "fr.cic.",
            "com.n26.", "de.number26.", "com.qonto.", "com.binance.", "com.coinbase.", "com.kraken.", "com.bitpanda.",
            "com.etoro.", "com.paypal.", "com.revolut.", "com.lydia", "fr.axa.", "com.axa.", "com.wise.",
            "com.transferwise.", "fr.hellobank.", "com.fortuneo.", "com.ingdirect.", "fr.lbp.", "com.trading212.") +
        editeurs("musique", "com.spotify.", "deezer.", "com.soundcloud.", "com.shazam.", "com.netflix.", "com.disney.",
            "com.amazon.avod", "com.amazon.mp3", "com.canal.", "tv.molotov.", "com.crunchyroll.", "org.videolan.",
            "com.google.android.youtube", "com.google.android.apps.youtube", "com.apple.android.music", "com.audible.",
            "fr.radiofrance.", "com.plexapp.", "tv.twitch.", "com.hbo.", "com.wbd.", "com.aspiro.tidal", "com.qobuz.",
            "com.pandora.") +
        editeurs("social", "com.whatsapp", "com.facebook.", "com.instagram.", "com.snapchat.", "org.telegram.",
            "com.discord", "com.tinder", "com.bumble.", "co.hinge.", "com.viber.", "com.skype.", "com.pinterest",
            "com.tumblr", "jp.naver.line.", "com.kakao.", "com.zhiliaoapp.", "com.ss.android.ugc.", "org.thoughtcrime.",
            "org.joinmastodon.", "com.reddit.") +
        editeurs("shopping", "com.amazon.mshop", "com.ubercab.eats", "com.deliveroo.", "com.justeat.", "fr.vinted",
            "com.zzkko", "com.shein", "com.einnovation.", "com.alibaba.aliexpress", "com.ebay.", "com.cdiscount.",
            "com.fnac.", "com.darty.", "com.ikea.", "com.decathlon.", "com.carrefour.", "com.lidl.", "fr.leclerc.",
            "com.mcdonalds.", "com.contextlogic.", "com.zalando.", "com.asos.") +
        editeurs("trajets", "com.ubercab", "com.waze", "com.bolt.", "ee.mtakso.", "com.citymapper.", "com.sncf.",
            "com.vsct.", "com.blablacar.", "com.booking", "com.airbnb.", "com.tripadvisor.", "com.expedia.",
            "com.limebike", "com.flixbus.", "com.ryanair.", "com.airfrance.", "com.trainline.", "net.mappy.") +
        editeurs("ia", "com.openai.", "com.anthropic.", "ai.perplexity.", "com.microsoft.copilot", "ai.mistral.",
            "com.deepseek.", "ai.x.", "ai.character.", "com.character.") +
        editeurs("travail", "com.microsoft.office.", "com.microsoft.teams", "com.microsoft.todos", "com.slack",
            "us.zoom.", "notion.id", "com.trello", "com.dropbox.", "com.adobe.reader", "com.adobe.scan", "com.evernote",
            "com.todoist", "com.google.android.keep", "com.google.android.calendar", "com.ticktick.", "com.deepl.",
            "ch.protonmail.", "me.proton.android.mail") +
        editeurs("sante", "com.fitbit.", "com.strava", "com.myfitnesspal.", "com.calm.", "com.getsomeheadspace.",
            "com.doctolib.", "com.runtastic.", "com.garmin.", "com.nike.", "com.polar.", "com.withings.") +
        editeurs("photo", "com.adobe.lrmobile", "com.adobe.psmobile", "com.picsart.", "com.canva.", "com.niksoftware.",
            "com.vsco.", "com.google.android.googlecamera", "com.lemon.lvoverseas") +
        editeurs("reglages", "com.x8bit.bitwarden", "com.lastpass.", "com.nordvpn.", "com.expressvpn.", "ch.protonvpn.",
            "com.cloudflare.", "com.avast.", "com.avg.", "com.kms.", "com.authy.", "com.microsoft.launcher",
            "com.teslacoilsw.launcher") +
        editeurs("infos", "com.google.android.apps.magazines", "fr.lemonde.", "fr.playsoft.lefigaro", "com.cnn.",
            "bbc.", "uk.co.bbc.", "flipboard.", "com.android.chrome", "org.mozilla.", "com.opera.", "com.brave.",
            "com.microsoft.emmx", "com.sec.android.app.sbrowser", "com.duckduckgo.", "com.accuweather.",
            "com.weather.", "fr.meteo", "com.meteofrance", "fr.francetv.")
    ).toMap()

    // 6 et 7. Les mots-clés, EN PLUS de ceux de Groupes.kt. Un mot-clé compte s'il COMMENCE un mot du nom
    // (« photo » trouve « Photos » et « Photoshop », pas « Telephoto »).
    private val MOTS: Map<String, Set<String>> = mapOf(
        "ia" to setOf("gpt", "chatgpt", "chatbot", "copilot", "gemini", "claude", "mistral", "perplexity", "deepseek",
            "grok", "llm", "openai", "assistant", "replika", "midjourney", "character"),
        "social" to setOf("messag", "chat", "social", "sms", "contact", "telephone", "phone", "dialer", "appel", "call",
            "tinder", "bumble", "hinge", "dating", "rencontre", "forum", "communaut", "community", "friend", "amis",
            "whatsapp", "telegram", "signal", "snap", "tiktok", "twitter", "threads", "mastodon", "bluesky", "reddit",
            "pinterest", "tumblr", "facebook", "messenger", "viber", "skype", "instagram"),
        "musique" to setOf("music", "musique", "musik", "song", "radio", "podcast", "video", "tv", "film", "movie",
            "cinema", "stream", "player", "lecteur", "spotify", "deezer", "soundcloud", "shazam", "youtube", "netflix",
            "disney", "canal", "crunchyroll", "twitch", "vlc", "mp3", "audio", "sound", "karaoke", "guitar", "piano",
            "playlist", "concert", "replay"),
        "photo" to setOf("photo", "foto", "camera", "gallery", "galerie", "image", "picture", "selfie", "snapseed",
            "lightroom", "picsart", "canva", "collage", "draw", "paint", "dessin", "sketch", "wallpaper", "retouche",
            "filtre", "filter"),
        "jeux" to setOf("game", "jeu", "chess", "echec", "puzzle", "quest", "craft", "clash", "battle", "royale",
            "legend", "hero", "dungeon", "racing", "football", "soccer", "fifa", "minecraft", "roblox", "sudoku",
            "solitaire", "tetris", "mario", "pokemon", "casino", "arcade", "simulat", "tycoon", "idle", "rpg", "kart",
            "ninja", "zombie", "dragon", "survival", "shooter", "sniper", "quiz", "trivia", "crossword", "mahjong",
            "bingo", "poker", "wordle", "saga", "rush", "runner", "surfer", "warfare", "empire", "kingdom", "clans"),
        "finance" to setOf("pay", "money", "argent", "finance", "invest", "trade", "trading", "coin", "bitcoin", "btc",
            "exchange", "budget", "compte", "credit", "assur", "impot", "tax", "lydia", "revolut", "qonto", "sumeria",
            "epargne", "caisse", "paypal", "boursorama", "fortuneo"),
        "trajets" to setOf("map", "gps", "navigation", "transport", "train", "sncf", "tram", "taxi", "uber", "bolt",
            "vtc", "flight", "airline", "airport", "aeroport", "booking", "hotel", "travel", "voyage", "trip", "trajet",
            "itineraire", "parking", "velo", "bike", "scooter", "trottinette", "blablacar", "waze", "citymapper", "ratp",
            "billet", "ticket"),
        "travail" to setOf("office", "word", "excel", "powerpoint", "docs", "document", "sheet", "drive", "cloud",
            "note", "todo", "task", "tache", "agenda", "calendar", "calendrier", "mail", "outlook", "teams", "slack",
            "zoom", "notion", "trello", "scan", "pdf", "file", "fichier", "dropbox", "onedrive", "keep", "memo",
            "translat", "tradu", "diction", "learn", "school", "ecole", "study", "edu", "job", "emploi", "indeed",
            "work", "travail", "project", "projet", "terminal", "github", "gitlab"),
        "sante" to setOf("doctor", "docteur", "medecin", "hopital", "hospital", "soin", "yoga", "workout", "gym",
            "training", "running", "step", "meditat", "calm", "headspace", "period", "cycle", "pill", "diet", "nutri",
            "calorie", "mental", "therap", "coach", "muscle", "mutuelle", "cardio", "heart", "coeur"),
        "shopping" to setOf("boutique", "deal", "promo", "coupon", "cashback", "fnac", "darty", "ikea", "zara",
            "decathlon", "lidl", "carrefour", "auchan", "leclerc", "intermarche", "deliveroo", "eats", "food", "resto",
            "restaurant", "pizza", "burger", "mcdo", "mcdonald", "kfc", "courses", "panier", "achat", "buy", "solde"),
        "reglages" to setOf("tool", "outil", "util", "clean", "nettoy", "battery", "batterie", "security", "securit",
            "antivirus", "password", "passw", "bitwarden", "vault", "backup", "sauvegarde", "widget", "theme", "icon",
            "calculat", "clock", "horloge", "alarm", "reveil", "flashlight", "lampe", "torch", "qr", "wifi",
            "bluetooth", "remote", "telecommande", "home", "smart", "boussole", "compass", "config", "system",
            "systeme", "update"),
        "infos" to setOf("journal", "presse", "press", "monde", "figaro", "liberation", "franceinfo", "bfm", "cnn",
            "bbc", "reuters", "magazine", "flipboard", "feed", "rss", "meteo", "weather", "wiki", "browser",
            "navigateur", "chrome", "firefox", "opera", "brave", "internet", "duckduckgo", "actualit", "headline"),
    )
}
