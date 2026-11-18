package com.will.animlauncher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// LE STYLE AUTO (18/11, demande de Will) : en un appui, l'app habille tout le téléphone d'un style cohérent,
// fait à partir des apps de l'utilisateur. Écrans : StyleAutoActivity.kt. Ici, le « moteur », sans écran :
//
//  1) ANALYSER : on regarde les icônes des apps de chaque dossier et on trouve leur couleur principale
//     (la « teinte » qui revient le plus), puis la couleur dominante de TOUT le téléphone.
//  2) CALCULER un des 5 styles (Assorti, Dégradé, Monochrome, Nuit néon, Coucher de soleil) : une couleur
//     par dossier, les 3 couleurs de la réaction sonore, un fond d'écran et sa palette de 4 couleurs.
//  3) APPLIQUER ce que l'utilisateur a laissé coché, APRÈS avoir fait une SAUVEGARDE de ses réglages
//     actuels : « Annuler ce style » remet tout comme avant.
//
// Les teintes sont pensées en OKLCH (voir Teinte.kt) : à clarté égale, toutes les couleurs paraissent aussi
// lumineuses à l'œil. C'est ce qui rend les dossiers harmonieux entre eux.
object StyleAuto {

    // Les 5 styles, dans l'ordre de l'écran.
    const val ASSORTI = 0
    const val DEGRADE = 1
    const val MONO = 2
    const val NEON = 3
    const val SOLEIL = 4
    val STYLES = intArrayOf(ASSORTI, DEGRADE, MONO, NEON, SOLEIL)
    val NOMS = intArrayOf(R.string.sa_assorti, R.string.sa_degrade, R.string.sa_mono, R.string.sa_neon, R.string.sa_soleil)
    val DESCRIPTIONS = intArrayOf(R.string.sa_assorti_desc, R.string.sa_degrade_desc, R.string.sa_mono_desc,
        R.string.sa_neon_desc, R.string.sa_soleil_desc)

    // ======================= 1) ANALYSER LES ICÔNES =======================

    // Ce que l'analyse a trouvé :
    //  - dossiers : les dossiers à habiller, dans l'ordre de l'écran des dossiers (catégories, puis dossiers perso)
    //  - teintes : la teinte OKLCH principale des icônes de chaque dossier (NaN = icônes sans vraie couleur)
    //  - dominante : la teinte qui revient le plus sur tout le téléphone (le point de départ de 3 styles)
    //  - nbApps : le nombre d'apps rangées dans ces dossiers
    //  - compatibles : les apps qui ont une animation dans le catalogue
    class Analyse(val dossiers: List<String>, val teintes: FloatArray, val dominante: Float, val nbApps: Int,
                  val compatibles: List<String>)

    // L'analyse prend une ou deux secondes (on dessine chaque icône) : à lancer HORS du fil de l'écran.
    fun analyser(c: Context): Analyse {
        val dossiers = Groupes.CATEGORIES.map { it.id }.filter { Groupes.tous(c, it).size >= 2 } +   // même règle que l'écran des dossiers
            Groupes.persos(c)
        val total = FloatArray(BACS)                // l'histogramme de TOUT le téléphone
        val teintes = FloatArray(dossiers.size)
        var nbApps = 0
        for ((i, id) in dossiers.withIndex()) {
            val apps = Groupes.apps(c, id)
            nbApps += apps.size
            val histo = FloatArray(BACS)
            for (pkg in apps.take(16)) {             // 16 icônes suffisent pour connaître la couleur d'un dossier
                val h = histogrammeIcone(c, pkg) ?: continue
                for (b in 0 until BACS) { histo[b] += h[b]; total[b] += h[b] }
            }
            teintes[i] = sommet(histo)
        }
        val intention = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val compatibles = c.packageManager.queryIntentActivities(intention, 0).map { it.activityInfo.packageName }
            .distinct().filter { it != c.packageName && Catalogue.pour(it) != null }
        val dominante = sommet(total).let { if (it.isNaN()) 255f else it }      // rien de coloré ? un bleu nuit
        return Analyse(dossiers, teintes, dominante, nbApps, compatibles)
    }

    // L'HISTOGRAMME DES TEINTES d'une icône : on la dessine en 32 x 32 pixels, puis chaque pixel bien coloré
    // vote pour sa teinte (24 « bacs » de 15°). Un pixel vote d'autant plus fort que sa couleur est vive
    // (son intensité C) : le fond blanc ou gris d'une icône ne compte pas, la couleur de son logo, si.
    private const val BACS = 24
    private fun histogrammeIcone(c: Context, pkg: String): FloatArray? {
        val icone = try { c.packageManager.getApplicationIcon(pkg) } catch (_: Exception) { return null }
        val b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            icone.setBounds(0, 0, 32, 32)
            icone.draw(Canvas(b))
        } catch (_: Exception) { b.recycle(); return null }
        val pixels = IntArray(32 * 32)
        b.getPixels(pixels, 0, 32, 0, 0, 32, 32)
        b.recycle()
        val histo = FloatArray(BACS)
        for (p in pixels) {
            if (Color.alpha(p) < 200) continue                      // les coins transparents
            val lch = Teinte.versOklch(p)
            if (lch[1] < 0.05f || lch[0] < 0.25f || lch[0] > 0.96f) continue    // gris, presque noir, presque blanc
            histo[((lch[2] / 15f).toInt()) % BACS] += lch[1]
        }
        // chaque icône pèse pareil, qu'elle soit très colorée ou non (sinon une seule icône criarde déciderait tout)
        val somme = histo.sum()
        if (somme < 0.5f) return null
        for (i in histo.indices) histo[i] /= somme
        return histo
    }

    // LA TEINTE QUI REVIENT LE PLUS dans un histogramme : on lisse un peu (chaque bac + la moitié de ses
    // voisins), on prend le bac le plus haut, puis la moyenne « en cercle » de ce bac et de ses 2 voisins
    // pour être plus précis que 15°. NaN = histogramme vide.
    private fun sommet(h: FloatArray): Float {
        if (h.sum() <= 0f) return Float.NaN
        var meilleur = 0; var max = -1f
        for (i in 0 until BACS) {
            val v = h[i] + 0.5f * (h[(i + BACS - 1) % BACS] + h[(i + 1) % BACS])
            if (v > max) { max = v; meilleur = i }
        }
        // moyenne d'angles : on additionne des flèches (cos, sin) au lieu des nombres (sinon 350° et 10° donneraient 180°)
        var x = 0.0; var y = 0.0
        for (d in -1..1) {
            val i = (meilleur + d + BACS) % BACS
            val a = Math.toRadians((i * 15f + 7.5f).toDouble())
            x += cos(a) * h[i]; y += sin(a) * h[i]
        }
        return ((Math.toDegrees(atan2(y, x)).toFloat()) + 360f) % 360f
    }

    // ======================= 2) CALCULER UN STYLE =======================

    // Le résultat d'un style :
    //  - couleurs : pour chaque dossier, la couleur à donner à sa roue (en HSV, comme la roue la retient)
    //  - son : les 3 teintes de la réaction sonore (calme, moyen, ça envoie), en angles de la roue
    //  - fond : le dessin de fond d'écran conseillé (« nuit » ou « galets ») et sa palette de 4 couleurs
    class Resultat(val style: Int, val couleurs: LinkedHashMap<String, FloatArray>, val son: FloatArray,
                   val fond: String, val palette: IntArray)

    fun calculer(style: Int, a: Analyse): Resultat {
        val n = a.dossiers.size
        val d = a.dominante
        // u(i) = la place du dossier dans la liste, de 0 (le 1er) à 1 (le dernier)
        fun u(i: Int) = if (n <= 1) 0.5f else i / (n - 1f)
        // Pour chaque dossier : (teinte OKLCH, intensité, luminosité) -> la couleur de la roue.
        val reglages: List<FloatArray> = when (style) {
            // ASSORTI : la couleur des icônes de chaque dossier, un peu ÉCARTÉES entre elles pour qu'on les distingue
            ASSORTI -> {
                val brutes = FloatArray(n) { i -> a.teintes[i].let { if (it.isNaN()) d + 360f * i / n.coerceAtLeast(1) else it } }
                ecarter(brutes, 24f).map { floatArrayOf(it, 0.62f, 1f) }
            }
            // DÉGRADÉ : de la dominante -55° à la dominante +65°, régulièrement, d'un dossier à l'autre
            DEGRADE -> List(n) { i -> floatArrayOf(d - 55f + 120f * u(i), 0.6f, 1f) }
            // MONOCHROME : une seule teinte, en nuances (plus ou moins intense, plus ou moins clair)
            MONO -> List(n) { i -> floatArrayOf(d, 0.32f + 0.38f * u(i), if (i % 2 == 0) 1f else 0.82f) }
            // NUIT NÉON : la dominante et une couleur à 150° d'elle, en alternance, à fond
            NEON -> List(n) { i -> floatArrayOf(if (i % 2 == 0) d else d + 150f, 1f, 1f) }
            // COUCHER DE SOLEIL : du corail (22°) à l'ambre (77°)
            else -> List(n) { i -> floatArrayOf(22f + 55f * u(i), 0.66f, 1f) }
        }
        val couleurs = LinkedHashMap<String, FloatArray>()
        for ((i, id) in a.dossiers.withIndex()) {
            val r = reglages[i]
            couleurs[id] = roue(r[0], r[1], r[2])
        }
        // Les 3 couleurs de la réaction sonore : calme, moyen, ça envoie (en teintes OKLCH, converties plus bas)
        val sonOk: FloatArray = when (style) {
            ASSORTI -> if (n == 0) floatArrayOf(d, d + 120, d + 240)
                       else floatArrayOf(reglages[0][0], reglages[n / 2][0], reglages[n - 1][0])
            DEGRADE -> floatArrayOf(d - 55f, d + 5f, d + 65f)
            MONO -> floatArrayOf(d - 15f, d, d + 15f)
            NEON -> floatArrayOf(d, d + 75f, d + 150f)
            else -> floatArrayOf(77f, 45f, 22f)
        }
        // La réaction sonore range ses couleurs en angles de roue HSV (voir ReactionSonore.palette) : on convertit.
        val son = FloatArray(3) { i ->
            val hsv = FloatArray(3)
            Color.colorToHSV(Teinte.oklch(0.72f, 0.15f, sonOk[i]), hsv)
            hsv[0]
        }
        val fond = if (style == ASSORTI || style == SOLEIL) "galets" else "nuit"
        // La palette du fond : 4 couleurs de dossiers réparties sur la liste (ou celles de la réaction sonore)
        val vitrines = couleurs.values.map { Teinte.vitrine(it) }
        val palette = if (vitrines.isEmpty()) IntArray(4) { Teinte.oklch(0.72f, 0.15f, sonOk[it % 3]) }
            else IntArray(4) { k -> vitrines[((vitrines.size - 1) * k / 3f).toInt()] }
        return Resultat(style, couleurs, son, fond, palette)
    }

    // Écarte des teintes trop proches (au moins « mini » degrés entre deux voisines, quand il y a la place) :
    // deux dossiers aux icônes bleues ne doivent pas avoir exactement le même bleu. On fait quelques passes
    // où chaque paire trop serrée est poussée de part et d'autre.
    private fun ecarter(teintes: FloatArray, mini: Float): List<Float> {
        val t = teintes.map { ((it % 360f) + 360f) % 360f }.toMutableList()
        if (t.size < 2 || t.size * mini > 300f) return t
        repeat(30) {
            var bouge = false
            for (i in t.indices) for (j in i + 1 until t.size) {
                var e = t[j] - t[i]
                if (e > 180f) e -= 360f
                if (e < -180f) e += 360f
                if (abs(e) < mini) {
                    val pousse = (mini - abs(e)) / 2f * (if (e >= 0f) 1f else -1f)
                    t[i] -= pousse; t[j] += pousse
                    bouge = true
                }
            }
            if (!bouge) return t
        }
        return t
    }

    // UNE TEINTE OKLCH -> LA COULEUR DE LA ROUE (HSV). La roue retient du HSV ; Teinte.depuis en tire ensuite
    // la vraie teinte « vue par l'œil » (celle de la couleur pure HSV(h, 1, 1)). Il faut donc trouver l'angle h
    // de la roue qui DONNE la teinte OKLCH voulue : on a calculé une fois les 720 demi-degrés de la roue,
    // et on prend le plus proche.
    private val tableRoue: FloatArray by lazy {
        FloatArray(720) { Teinte.versOklch(Color.HSVToColor(floatArrayOf(it / 2f, 1f, 1f)))[2] }
    }
    fun roue(teinteOklch: Float, intensite: Float, luminosite: Float): FloatArray {
        val voulu = ((teinteOklch % 360f) + 360f) % 360f
        var meilleur = 0; var ecart = 999f
        for (i in tableRoue.indices) {
            var e = abs(tableRoue[i] - voulu)
            if (e > 180f) e = 360f - e
            if (e < ecart) { ecart = e; meilleur = i }
        }
        return floatArrayOf(meilleur / 2f, intensite.coerceIn(0f, 1f), luminosite.coerceIn(0.2f, 1f))
    }

    // ======================= 3) APPLIQUER, SAUVEGARDER, ANNULER =======================

    // Ce qu'on peut appliquer (les interrupteurs de l'écran « Ce qui va changer »).
    // (Poser les dossiers sur l'accueil est fait par l'écran lui-même : Android doit montrer sa fenêtre.)
    class Choix(val couleurs: Boolean, val fond: Boolean, val son: Boolean, val animations: Boolean)

    // Applique le style. Renvoie vrai si le fond d'écran a besoin de la confirmation d'Android
    // (notre fond animé n'est pas encore le fond du téléphone) : l'écran ouvre alors la page d'Android.
    fun appliquer(c: Context, r: Resultat, a: Analyse, choix: Choix): Boolean {
        sauvegarder(c)
        if (choix.couleurs) for ((id, hsv) in r.couleurs) {
            Groupes.definirCouleur(c, id, hsv)
            try { WidgetGroupe.rafraichir(c, id) } catch (_: Exception) {}
        }
        if (choix.son) {
            for (m in 0..2) ReactionSonore.definirTeinte(c, m, r.son[m], true)
        }
        if (choix.animations) {
            Reglages.definir(c, true)
            for (app in a.compatibles) Reglages.definirAppActive(c, app, true)
        }
        var confirmer = false
        if (choix.fond) {
            // on garde les réglages du fond déjà choisis (fluidité, figer, assombrir) : seul le dessin change
            val avant = CarnetFond.lire(c, CarnetFond.PREFIXE_DESSIN)
            CarnetFond.definirPalette(c, r.palette)
            CarnetFond.ecrire(c, CarnetFond.Choix(CarnetFond.APP, r.fond, avant.fluide, avant.figer, avant.assombrir))
            val voulu = CarnetFond.service(c, CarnetFond.APP)
            val actuel = try { android.app.WallpaperManager.getInstance(c).wallpaperInfo?.component } catch (_: Exception) { null }
            confirmer = actuel != voulu
        }
        carnet(c).edit().putInt("dernier_style", r.style).apply()
        return confirmer
    }

    // LA SAUVEGARDE : une copie des réglages que le style peut changer, rangée dans le carnet « style_auto ».
    // On ne copie que les cases concernées de chaque carnet (voir FILTRES) : les couleurs des dossiers,
    // les 3 couleurs du son, le fond dessiné et sa palette, l'interrupteur général et les apps coupées.
    private fun carnet(c: Context): SharedPreferences = c.getSharedPreferences("style_auto", Context.MODE_PRIVATE)
    private val FILTRES: List<Pair<String, (String) -> Boolean>> = listOf(
        "groupes" to { k: String -> k.startsWith("couleur_") },
        "son" to { k: String -> k.startsWith("teinte_") },
        "fond" to { k: String -> k.startsWith(CarnetFond.PREFIXE_DESSIN) },
        "reglages" to { k: String -> k == "animations_actives" || k.startsWith("off_") }
    )

    private fun sauvegarder(c: Context) {
        val tout = JSONObject()
        for ((nom, garder) in FILTRES) {
            val copie = JSONObject()
            for ((k, v) in c.getSharedPreferences(nom, Context.MODE_PRIVATE).all) {
                if (!garder(k) || v == null) continue
                // on note le TYPE avec la valeur (« b » vrai/faux, « i » nombre entier…) pour la remettre pareil
                copie.put(k, JSONArray().put(when (v) {
                    is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; is Set<*> -> "S"; else -> "s"
                }).put(if (v is Set<*>) JSONArray(v.map { it.toString() }) else v.toString()))
            }
            tout.put(nom, copie)
        }
        carnet(c).edit().putString("sauvegarde", tout.toString()).apply()
    }

    fun aSauvegarde(c: Context): Boolean = carnet(c).contains("sauvegarde")
    fun dernierStyle(c: Context): Int = carnet(c).getInt("dernier_style", -1)

    // ANNULER : on efface les cases concernées, puis on remet celles de la sauvegarde, avec leur type.
    fun annuler(c: Context) {
        val tout = try { JSONObject(carnet(c).getString("sauvegarde", null) ?: return) } catch (_: Exception) { return }
        for ((nom, garder) in FILTRES) {
            val p = c.getSharedPreferences(nom, Context.MODE_PRIVATE)
            val e = p.edit()
            for (k in p.all.keys) if (garder(k)) e.remove(k)
            val copie = tout.optJSONObject(nom) ?: JSONObject()
            for (k in copie.keys()) {
                val paire = copie.optJSONArray(k) ?: continue
                val v = paire.optString(1)
                when (paire.optString(0)) {
                    "b" -> e.putBoolean(k, v.toBoolean())
                    "i" -> e.putInt(k, v.toIntOrNull() ?: 0)
                    "l" -> e.putLong(k, v.toLongOrNull() ?: 0L)
                    "f" -> e.putFloat(k, v.toFloatOrNull() ?: 0f)
                    "S" -> e.putStringSet(k, paire.optJSONArray(1)?.let { l -> (0 until l.length()).map { l.optString(it) }.toSet() } ?: emptySet())
                    else -> e.putString(k, v)
                }
            }
            e.commit()          // tout de suite (pas « plus tard ») : les widgets sont repeints juste après
        }
        carnet(c).edit().remove("sauvegarde").remove("dernier_style").apply()
        // tout ce qui affiche ces réglages se met à jour
        ReactionSonore.lireCouleurs(c)
        for (id in Groupes.CATEGORIES.map { it.id } + Groupes.persos(c)) {
            try { WidgetGroupe.rafraichir(c, id) } catch (_: Exception) {}
        }
    }
}
