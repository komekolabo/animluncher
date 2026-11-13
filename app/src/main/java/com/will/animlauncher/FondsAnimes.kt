package com.will.animlauncher

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// LES FONDS D'ÉCRAN ANIMÉS (11/11, étape 1) : ce fichier range les « briques » communes.
//
//  1) Les DESSINS : les fonds proposés par l'app, dessinés par notre code (comme les animations).
//     Chaque dessin sait se dessiner à n'importe quel instant t (en secondes) : on lui donne une toile
//     (Canvas), la largeur et la hauteur, et il dessine TOUTE l'image. Le même dessin sert à trois endroits :
//     la galerie (petite vignette), l'aperçu plein écran et le vrai fond d'écran (FondService.kt).
//
//  2) Le CARNET « fond » : ce que l'utilisateur a choisi (quel fond, fluidité, figer, assombrir).
//     Les deux services de fond d'écran (dessins / vidéo) lisent ce carnet et se mettent à jour
//     tout seuls dès qu'il change.
//
//  3) Les FICHIERS importés (vidéos, GIF, images) : rangés dans le dossier privé de l'app « fonds/ ».
//     Ils ne quittent jamais le téléphone.

// ========================= 1) LES DESSINS =========================

abstract class DessinFond {
    // dp = la taille d'un « dp » en pixels (pour que les étoiles aient la même taille sur tous les écrans)
    abstract fun dessiner(c: Canvas, w: Float, h: Float, t: Float, dp: Float)

    // (13/11, pour le fond 3D) Un dessin peut avoir besoin d'un capteur : on l'allume quand il devient
    // visible (demarrer) et on l'éteint quand il est caché (arreter). Par défaut : rien à faire.
    open fun demarrer() {}
    open fun arreter() {}
    // « au repos » = l'image ne change plus : inutile de la redessiner tant que rien ne bouge.
    // Le dessin appelle alors « reveil » quand il a de nouveau besoin d'être dessiné (le téléphone a bougé).
    // Par défaut : jamais au repos (Nuit et Galets bougent tout le temps).
    open fun auRepos(): Boolean = false
    var reveil: (() -> Unit)? = null

    // Un cycle de 0 à 1 qui se répète toutes les « periode » secondes.
    protected fun cycle(t: Float, periode: Float): Float {
        val r = t % periode
        return (if (r < 0f) r + periode else r) / periode
    }
}

// « NUIT » : le fond de la carte 3 de l'accueil (déjà vu et validé par Will), en plein écran.
// Trois lueurs sombres (bleu nuit, braise, vert profond) qui dérivent très lentement, et des étoiles
// qui scintillent. Très sombre : sur l'écran OLED du Samsung, le noir ne consomme presque rien.
class FondNuit : DessinFond() {
    private class Lueur(val fx: Float, val fy: Float, val couleur: Int)
    private val lueurs = listOf(
        Lueur(0.33f, 0.30f, Color.rgb(30, 42, 58)),
        Lueur(0.70f, 0.50f, Color.rgb(58, 36, 24)),
        Lueur(0.25f, 0.72f, Color.rgb(16, 48, 42))
    )
    private val etoiles = listOf(
        floatArrayOf(0.18f, 0.22f, 1f), floatArrayOf(0.72f, 0.16f, 1.5f), floatArrayOf(0.58f, 0.38f, 1f),
        floatArrayOf(0.84f, 0.44f, 1f), floatArrayOf(0.30f, 0.48f, 1.5f), floatArrayOf(0.12f, 0.60f, 1f),
        floatArrayOf(0.64f, 0.66f, 1f), floatArrayOf(0.46f, 0.12f, 1f)
    )
    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pEtoile = Paint(Paint.ANTI_ALIAS_FLAG)

    // Les dégradés ne sont refaits que si la taille change (en refaire 3 à chaque image coûte de la batterie).
    // Pour faire bouger les lueurs, on déplace la toile (rotate/translate) au lieu de refaire les dégradés.
    private var wCache = -1f
    private var hCache = -1f
    private var degrades: List<RadialGradient> = emptyList()

    override fun dessiner(c: Canvas, w: Float, h: Float, t: Float, dp: Float) {
        if (w != wCache || h != hCache) {
            val r = 0.55f * w
            degrades = lueurs.map {
                RadialGradient(it.fx * w, it.fy * h, r, intArrayOf(it.couleur, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            }
            wCache = w; hCache = h
        }
        c.drawColor(Color.rgb(3, 3, 4))
        // k va de 0 à 1 puis revient à 0, en douceur, sur 44 s
        val k = 0.5f - 0.5f * cos(2f * PI.toFloat() * cycle(t, 44f))
        c.save()
        c.rotate(14f * k, w / 2f, h / 2f)
        c.translate(0.08f * w * k, -0.06f * h * k)
        for ((i, l) in lueurs.withIndex()) {
            pinceau.shader = degrades[i]
            c.drawCircle(l.fx * w, l.fy * h, 0.55f * w, pinceau)
        }
        c.restore()
        for ((i, e) in etoiles.withIndex()) {
            val a = 0.5f + 0.5f * sin(t * 1.3f + i * 1.7f)
            pEtoile.color = Color.argb((70 + 110 * a).toInt(), 200, 200, 206)
            c.drawCircle(e[0] * w, e[1] * h, e[2] * dp, pEtoile)
        }
    }
}

// « GALETS » : le fond de la carte 2 de l'accueil (déjà vu par Will), en plein écran :
// un halo doré très léger qui respire au centre et 4 galets de couleur qui flottent autour.
class FondGalets : DessinFond() {
    private class Galet(val dx: Float, val dy: Float, val r: Float, val couleur: Int, val retard: Float)
    private val galets = listOf(
        Galet(-92f, -63f, 19f, Color.rgb(217, 119, 87), 0f),
        Galet(94f, -13f, 15f, Color.rgb(60, 196, 106), 0.9f),
        Galet(-74f, 97f, 13f, Color.rgb(91, 147, 240), 1.7f),
        Galet(66f, 103f, 11f, Color.rgb(123, 203, 58), 2.4f)
    )
    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ombre = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 0, 0, 0) }

    override fun dessiner(c: Canvas, w: Float, h: Float, t: Float, dp: Float) {
        c.drawColor(Color.rgb(3, 3, 4))
        val cx = w / 2f
        val cy = h / 2f
        // sur un petit cadre (la vignette de la galerie), on réduit tout pour que ça tienne
        val echelle = min(1.6f, w / (318f * dp))
        val souffle = 0.92f + 0.14f * (0.5f - 0.5f * cos(2f * PI.toFloat() * cycle(t, 6f)))
        val rh = 100f * dp * echelle * souffle
        pinceau.shader = RadialGradient(cx, cy, rh,
            intArrayOf(Color.argb(31, 246, 210, 122), Color.TRANSPARENT, Color.TRANSPARENT),
            floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, rh, pinceau)
        for (g in galets) {
            val flotte = -3.5f * dp * echelle * sin(2f * PI.toFloat() * cycle(t + g.retard, 3f))
            val x = cx + g.dx * dp * echelle
            val y = cy + g.dy * dp * echelle + flotte
            val r = g.r * dp * echelle
            c.drawCircle(x, y + r * 0.45f, r * 0.9f, ombre)
            pinceau.shader = RadialGradient(x - r * 0.3f, y - r * 0.4f, r * 1.9f,
                intArrayOf(g.couleur, Color.rgb(10, 10, 10)), null, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, pinceau)
        }
    }
}

// Une vue qui affiche un DessinFond en boucle (pour la galerie et l'aperçu).
class VueDessinFond(context: Context, private val dessin: DessinFond) : View(context) {
    private val debut = SystemClock.uptimeMillis()
    private val dp = context.resources.displayMetrics.density
    var assombrir = 0             // 0 à 60 : % de noir posé par-dessus
        set(v) { field = v; invalidate() }

    // le capteur du dessin (fond 3D) n'est allumé que pendant que la vue est à l'écran
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        dessin.reveil = { invalidate() }
        dessin.demarrer()
    }

    override fun onDetachedFromWindow() {
        dessin.arreter()
        dessin.reveil = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val t = (SystemClock.uptimeMillis() - debut) / 1000f
        dessin.dessiner(c, width.toFloat(), height.toFloat(), t, dp)
        if (assombrir > 0) c.drawColor(Color.argb(assombrir * 255 / 100, 0, 0, 0))
        if (!dessin.auRepos()) postInvalidateOnAnimation()       // au repos : on attend le réveil
    }
}

// ========================= 2) LE CARNET « fond » =========================

object CarnetFond {
    // Les sortes de fond
    const val APP = "app"         // un dessin de l'app (id = "nuit", "galets"…)
    const val VIDEO = "video"     // une vidéo importée
    const val GIF = "gif"         // un GIF importé
    const val IMAGE = "image"     // une image fixe importée
    const val PROFONDEUR = "profondeur"   // (13/11) un fond 3D : id = son dossier (voir FondProfondeur.kt)

    // Le catalogue des fonds proposés par l'app : identifiant -> (nom affiché, fabrique du dessin).
    // Pour en ajouter un : une ligne ici + son nom dans res/values*/strings_fonds.xml.
    class FondApp(val id: String, val nom: Int, val fabriquer: () -> DessinFond)
    val catalogue = listOf(
        FondApp("nuit", R.string.fd_nom_nuit) { FondNuit() },
        FondApp("galets", R.string.fd_nom_galets) { FondGalets() }
    )
    fun dessin(id: String): DessinFond = (catalogue.firstOrNull { it.id == id } ?: catalogue[0]).fabriquer()

    // Le dessin d'un choix : un fond 3D (s'il existe encore) ou un dessin de l'app.
    fun dessinPour(c: Context, sorte: String, id: String): DessinFond =
        if (sorte == PROFONDEUR) FondProfondeur(c, id).takeIf { it.valide } ?: FondNuit() else dessin(id)

    // Ce que l'utilisateur a choisi, tout en un.
    //  - sorte : APP / VIDEO / GIF / IMAGE / PROFONDEUR
    //  - id : le nom du dessin (APP) ou le chemin du fichier (les autres)
    //  - fluide : 60 images par seconde au lieu de 30
    //  - figer : arrêter le mouvement après 10 s sans toucher l'accueil
    //  - assombrir : 0 à 60 % de noir par-dessus (pas pour les vidéos)
    data class Choix(val sorte: String, val id: String, val fluide: Boolean, val figer: Boolean, val assombrir: Int)

    // POURQUOI DEUX SERVICES ? Un fond vidéo est envoyé directement à la puce vidéo du téléphone
    // (le plus économe). Les autres fonds sont dessinés par notre code. Android ne permet pas de faire
    // les deux sur la même « surface » l'un après l'autre : on a donc un service pour chaque.
    // Chaque service a ses propres cases dans le carnet (préfixe « dessin_ » ou « video_ ») : si
    // l'utilisateur choisit une vidéo puis annule l'écran d'Android, le fond dessiné actuel ne change pas.
    const val PREFIXE_DESSIN = "dessin_"
    const val PREFIXE_VIDEO = "video_"

    fun carnet(c: Context): SharedPreferences = c.getSharedPreferences("fond", Context.MODE_PRIVATE)

    fun lire(c: Context, prefixe: String): Choix {
        val p = carnet(c)
        return Choix(
            sorte = p.getString(prefixe + "sorte", null) ?: (if (prefixe == PREFIXE_VIDEO) VIDEO else APP),
            id = p.getString(prefixe + "id", null) ?: (if (prefixe == PREFIXE_VIDEO) "" else "nuit"),
            fluide = p.getBoolean(prefixe + "fluide", false),
            figer = p.getBoolean(prefixe + "figer", false),
            assombrir = p.getInt(prefixe + "assombrir", 0)
        )
    }

    fun ecrire(c: Context, choix: Choix) {
        val prefixe = prefixePour(choix.sorte)
        carnet(c).edit()
            .putString(prefixe + "sorte", choix.sorte)
            .putString(prefixe + "id", choix.id)
            .putBoolean(prefixe + "fluide", choix.fluide)
            .putBoolean(prefixe + "figer", choix.figer)
            .putInt(prefixe + "assombrir", choix.assombrir)
            .apply()
    }

    fun prefixePour(sorte: String) = if (sorte == VIDEO) PREFIXE_VIDEO else PREFIXE_DESSIN

    // Le service qui affiche cette sorte de fond.
    fun service(c: Context, sorte: String): ComponentName =
        ComponentName(c, if (sorte == VIDEO) FondVideoService::class.java else FondDessinService::class.java)

    // ========================= 3) LES FICHIERS IMPORTÉS =========================

    fun dossier(c: Context): File = File(c.filesDir, "fonds").apply { mkdirs() }

    // Les fonds importés, du plus récent au plus ancien.
    fun importes(c: Context): List<File> =
        (dossier(c).listFiles() ?: emptyArray()).filter { it.isFile && it.length() > 0 }.sortedByDescending { it.lastModified() }

    // La sorte d'un fichier importé, d'après sa fin de nom.
    fun sorteDe(f: File): String = when (f.extension.lowercase()) {
        "mp4", "webm", "3gp", "mkv" -> VIDEO
        "gif" -> GIF
        else -> IMAGE
    }
}
