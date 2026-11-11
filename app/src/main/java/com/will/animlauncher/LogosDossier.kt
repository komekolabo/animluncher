package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// LES LOGOS DES DOSSIERS ANIMÉS (v2 du 11/11, après l'avis honnête demandé par Will : la v1 faisait trop
// « pictogrammes génériques », trop de roses, et certains rappelaient des marques).
// Chaque catégorie a son logo, une forme pleine et lisible même à la taille d'une icône, et un petit GESTE
// qui raconte quelque chose :
//   AI            l'étoile (inchangée)
//   Social        deux bulles : celle de devant « écrit » (3 points qui sautent), celle du fond arrive en rebondissant
//   Music & Video un vinyle qui tourne, avec son reflet ; le bras de la platine vient se poser dessus, et des notes
//                 de musique s'échappent du disque (« A · Notes », choisi par Will le 11/11)
//   Photos        un boîtier d'appareil photo : clic du diaphragme, le flash scintille doucement, puis une photo
//                 sort par le haut et se développe (« A · Polaroïd », choisi par Will le 11/11)
//   Finance       une courbe de bourse qui se trace en montant, avec l'aire dessous
//   Maps & Travel une carte pliée en 3 ; un itinéraire se trace en pointillés, puis une épingle tombe à l'arrivée
//   Productivity  une liste de tâches dont les 3 cases se cochent l'une après l'autre
//   Health        un cœur traversé par un électrocardiogramme ; il bat quand la ligne passe le pic
//   Shopping      l'étincelle tombe dans le sac, qui l'attrape en s'écrasant puis se balance
//   Settings      3 réglettes dont les curseurs glissent puis reviennent (le curseur du milieu est l'étincelle)
//   News          un journal au coin plié, dont les lignes s'écrivent
//
// L'ÉTINCELLE : une petite étoile à 4 branches, comme celle de l'IA, se retrouve dans CHAQUE logo (en haut à
// droite, ou à un endroit qui a du sens : le reflet de l'objectif, le sommet de la courbe, le curseur…).
// C'est la signature commune d'AnimLauncher : les logos sont de la même famille que l'étoile.
//
// LES COULEURS (v2) sont réparties sur toute la roue (un dossier = une teinte : rouge, ambre, vert, turquoise,
// bleu, indigo, violet…), et le galet du widget prend un sombre assorti (voir hsvGalet).
//
// LE REPÈRE : comme dans WidgetDossier.kt, on dessine dans un carré de 100 x 100 (tout le galet) et le
// logo est centré en (50, 48), d'un « rayon » d'environ 26 (celui de l'étoile).
// LE GESTE : « g » est l'horloge du geste, en secondes. Au repos, g = 0,3 (c'est la pose du logo quand il
// apparaît et quand il renaît) ; WidgetDossier la fait avancer pendant que le logo est affiché.
// LES TROUS (les points de la bulle, la route, les lignes du journal…) sont « découpés » dans le logo : on
// voit le galet au travers. Pour ça, le logo est dessiné sur un calque à part (saveLayer), puis on efface
// ces zones avec un pinceau spécial (mode CLEAR = « rendre transparent »).
// L'aperçu Python de ces logos (logos2.py) dessine la même chose (sans les versions du 11/11 de Music & Video et
// Photos : leur aperçu est la page web « logos animés des dossiers », en JavaScript).
object LogosDossier {
    private const val CX = 50f
    private const val CY = 48f

    // Les 3 couleurs du dégradé de chaque logo (clair -> moyen -> profond).
    private fun c(hex: Int) = Color.rgb((hex shr 16) and 255, (hex shr 8) and 255, hex and 255)
    private val COULEURS = mapOf(
        "ia" to intArrayOf(c(0xFF8C5B), c(0xFF6B9B), c(0xC4D13F)),
        "social" to intArrayOf(c(0x8FD3FF), c(0x4AA8F0), c(0x2F78D6)),       // bleu ciel
        "musique" to intArrayOf(c(0xF2A0F5), c(0xD05CE6), c(0x9E3CC4)),      // violet
        "photo" to intArrayOf(c(0xFFD978), c(0xF5B342), c(0xE08A2E)),        // ambre
        "finance" to intArrayOf(c(0x8EEBB0), c(0x3FCF86), c(0x1E9E6E)),      // émeraude
        "trajets" to intArrayOf(c(0x7FEDE0), c(0x2FCFC0), c(0x1A9AA0)),      // turquoise
        "travail" to intArrayOf(c(0xC0A6FF), c(0x8C6CF0), c(0x6446C8)),      // indigo
        "sante" to intArrayOf(c(0xFF8A8A), c(0xF2545F), c(0xC9364F)),        // rouge
        "shopping" to intArrayOf(c(0xD6E86E), c(0xA6CC44), c(0x6FA836)),     // vert tilleul
        "reglages" to intArrayOf(c(0xC9D2E0), c(0x8E9BB3), c(0x647089)),     // ardoise (assombri)
        "infos" to intArrayOf(c(0xA9BDF2), c(0x6F8EE0), c(0x4A63B8)),        // bleu acier (assombri)
    )

    // LE DÉCALAGE DU DÉPART de chaque dossier (en secondes, 11/11). Sans lui, tous les widgets posés sur le même
    // écran joueraient leur boucle en même temps : tous les logos partiraient et reviendraient ensemble, et tout
    // l'écran « pulserait ». Chaque dossier commence donc sa boucle un peu plus tôt ou plus tard.
    // Les décalages restent entre -0,7 et +2 s : la première image montrée est toujours une image où l'on ne
    // voit QUE le logo (avant les points). C'est important pour les dossiers de plus de 9 apps : quand la page
    // suivante arrive, la boucle repart de cette image, et le changement d'apps ne se voit pas.
    private val DECALAGES = mapOf("ia" to 0.0, "social" to 0.9, "musique" to 1.8, "photo" to -0.5, "finance" to 0.45,
        "trajets" to 1.35, "travail" to -0.25, "sante" to 0.7, "shopping" to 1.6, "reglages" to 0.2, "infos" to 1.1)
    fun decalage(groupe: String): Double = DECALAGES[groupe] ?: 0.0

    // Une catégorie qu'on sait dessiner ? (sinon : l'étoile)
    fun connu(groupe: String) = groupe in COULEURS

    fun couleurs(groupe: String): IntArray = COULEURS[groupe] ?: COULEURS.getValue("ia")

    // LA COULEUR DU GALET (et de la fenêtre du dossier) quand on n'en a pas choisi avec la roue : la teinte
    // moyenne du logo (au format de la roue : teinte, intensité, luminosité). null = l'IA, qui garde son brun.
    fun hsvGalet(groupe: String): FloatArray? {
        if (groupe == "ia" || !connu(groupe)) return null
        val hsv = FloatArray(3)
        Color.colorToHSV(couleurs(groupe)[1], hsv)
        return hsv
    }

    // LA ROTATION quand le logo part (en degrés) et le petit recul avec lequel il revient.
    // null = l'étoile, qui garde exactement ses mouvements d'origine (voir WidgetDossier.Boucle.logo).
    // Tous ne tournent que de 40 degrés, pour qu'on ne les voie jamais tête en bas (11/11 : l'appareil photo
    // n'est plus un diaphragme rond, il ne fait donc plus de demi-tour).
    fun tours(groupe: String): Pair<Double, Double>? = when (groupe) {
        "ia" -> null
        else -> if (connu(groupe)) 40.0 to 25.0 else null
    }

    // LE DÉBUT DU TRACÉ (11/11) : les logos qui se DESSINENT (la courbe de Finance, l'itinéraire de Maps, le tracé
    // du cœur de Health, les lignes du journal de News) commencent leur geste en effaçant leur dessin, puis le
    // refont. Dans le widget, quand le logo revient après les apps, on démarre donc directement à ce moment
    // « vide » : le logo se dessine pendant qu'il revient, au lieu d'apparaître plein, s'effacer, et se redessiner.
    // null = les autres logos (leur geste n'efface rien).
    fun debutTrace(groupe: String): Double? = when (groupe) {
        "finance" -> 0.58
        "trajets" -> 0.55
        "sante" -> 0.6
        "infos" -> 0.58
        else -> null
    }

    // ---------------- les pinceaux ----------------
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val efface = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val calque = RectF(4f, 2f, 96f, 94f)            // la zone du calque (un peu plus grande que les logos)
    private val chemin = Path()
    private val chemin2 = Path()
    private val rect = RectF()
    private val pointilles = DashPathEffect(floatArrayOf(3.2f, 3.0f), 0f)

    // Le dégradé du logo, dans le repère 100 x 100 (on le garde pour ne pas le refaire à chaque image).
    private var degradeCle: IntArray? = null
    private var degrade: Shader? = null
    private fun degradePour(cols: IntArray): Shader {
        if (degradeCle?.contentEquals(cols) != true) {
            degrade = LinearGradient(24f, 22f, 76f, 74f, cols, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            degradeCle = cols.copyOf()
        }
        return degrade!!
    }

    // La couleur de l'étincelle (et de l'épingle, du coin du journal) : la plus claire du logo, adoucie vers le
    // blanc, sans aller jusqu'au blanc pur (pas d'éblouissement).
    private fun clair(cols: IntArray): Int {
        val c = cols[0]
        fun m(v: Int) = (v + (255 - v) * 0.45f).toInt()
        return Color.rgb(m(Color.red(c)), m(Color.green(c)), m(Color.blue(c)))
    }

    // LE LOGO, dans le repère 100 x 100. « cols » = les 3 couleurs (celles de la catégorie, ou de la roue).
    // « alpha » : pour le faire disparaître en fondu (0 à 255).
    // (synchronisé : le widget le dessine en fond pendant que le dossier peut le dessiner à l'écran)
    @Synchronized
    fun dessiner(cv: Canvas, groupe: String, g: Double, cols: IntArray, alpha: Int = 255) {
        val src = degradePour(cols)
        val lum = clair(cols)
        cv.saveLayerAlpha(calque, alpha.coerceIn(0, 255))
        when (groupe) {
            "social" -> social(cv, g, src, lum)
            "musique" -> musique(cv, g, src, lum)
            "photo" -> photo(cv, g, src, lum)
            "finance" -> finance(cv, g, src, lum)
            "trajets" -> trajets(cv, g, src, lum)
            "travail" -> travail(cv, g, src, lum)
            "sante" -> sante(cv, g, src, lum)
            "shopping" -> shopping(cv, g, src, lum)
            "reglages" -> reglages(cv, g, src, lum)
            "infos" -> infos(cv, g, src, lum)
            else -> etoile(cv, src)
        }
        cv.restore()
    }

    // Le même logo, centré en (0, 0) et de rayon « r » (en pixels) : pour l'écran du dossier, où le logo
    // s'envole du widget jusqu'au titre (DossierActivity) puis y retourne (RetourEtoile). Toujours au repos.
    fun dessinerAuCentre(cv: Canvas, groupe: String, r: Float, cols: IntArray, alpha: Int = 255) {
        cv.save()
        cv.scale(r / 26f, r / 26f)
        cv.translate(-CX, -CY)
        dessiner(cv, groupe, 0.3, cols, alpha)
        cv.restore()
    }

    // ---------------- outils ----------------

    // Les façons d'arriver à une valeur et les « clés » (les mêmes que dans WidgetDossier.kt) :
    // une clé = (seconde, valeur, façon d'y arriver), écrite « 1.2 to 8.0 by DOUX ».
    private val LIN: (Double) -> Double = { it }
    private val ACC: (Double) -> Double = { it * it }                                  // démarre doucement puis accélère
    private val DEC: (Double) -> Double = { 1 - (1 - it) * (1 - it) }                  // arrive vite puis ralentit
    private val DOUX: (Double) -> Double = { cos((it + 1) * PI) / 2 + 0.5 }           // départ et arrivée en douceur
    private val RESSORT: (Double) -> Double = { val u = it - 1; u * u * (3 * u + 2) + 1 }   // dépasse un peu puis revient
    private class CleLogo(val t: Double, val v: Double, val f: (Double) -> Double)
    private infix fun Pair<Double, Double>.by(f: (Double) -> Double) = CleLogo(first, second, f)
    private fun cle(t: Double, vararg cles: CleLogo): Double {
        if (t <= cles[0].t) return cles[0].v
        for (k in 1 until cles.size) {
            val a = cles[k - 1]; val b = cles[k]
            if (t <= b.t) return a.v + (b.v - a.v) * b.f((t - a.t) / (b.t - a.t))
        }
        return cles.last().v
    }

    // une petite bosse douce (0 -> 1 -> 0) autour de « centre »
    private fun bosse(t: Double, centre: Double, demi: Double): Double {
        val u = (t - centre) / demi
        return if (abs(u) >= 1) 0.0 else 0.5 + 0.5 * cos(u * PI)
    }

    // remplir avec le dégradé
    private fun remplir(cv: Canvas, path: Path, src: Shader) {
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.shader = src
        cv.drawPath(path, p)
    }

    // remplir en arrondissant les coins (un trait épais de la même couleur tout autour)
    private fun remplirArrondi(cv: Canvas, path: Path, src: Shader, epaisseur: Float) {
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL_AND_STROKE; p.shader = src
        p.strokeJoin = Paint.Join.ROUND; p.strokeWidth = epaisseur
        cv.drawPath(path, p)
    }

    // remplir d'une couleur unie
    private fun uni(couleur: Int): Paint {
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = couleur
        return p
    }

    // un trait avec le dégradé
    private fun trait(epaisseur: Float, src: Shader): Paint {
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.shader = src
        p.strokeWidth = epaisseur; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        return p
    }

    // le pinceau qui « découpe » (en trait ou en remplissage)
    private fun gomme(epaisseur: Float = 0f): Paint {
        efface.style = if (epaisseur > 0) Paint.Style.STROKE else Paint.Style.FILL
        efface.strokeWidth = epaisseur; efface.strokeCap = Paint.Cap.ROUND; efface.strokeJoin = Paint.Join.ROUND
        efface.pathEffect = null
        return efface
    }

    // Une ligne brisée tracée seulement en partie (« part » de 0 à 1) : pour la coche, l'électrocardiogramme,
    // la courbe de bourse et l'itinéraire. On retient aussi où elle s'arrête (finX, finY).
    private var finX = 0f
    private var finY = 0f
    private fun lignePartielle(points: Array<FloatArray>, part: Double): Path? {
        val longueurs = (0 until points.size - 1).map { hypot(points[it + 1][0] - points[it][0], points[it + 1][1] - points[it][1]) }
        var reste = part * longueurs.sum()
        if (reste <= 0.3) return null
        chemin.reset(); chemin.moveTo(points[0][0], points[0][1])
        for (k in longueurs.indices) {
            val f = min(1.0, reste / longueurs[k]).toFloat()
            finX = points[k][0] + (points[k + 1][0] - points[k][0]) * f
            finY = points[k][1] + (points[k + 1][1] - points[k][1]) * f
            chemin.lineTo(finX, finY)
            reste -= longueurs[k]
            if (reste <= 0) break
        }
        return chemin
    }

    // L'ÉTINCELLE : une petite étoile à 4 branches (la signature commune). « eclat » (0 à 1) la fait grossir
    // et tourner d'un huitième de tour, le temps d'un scintillement.
    private fun etincelle(cv: Canvas, x: Float, y: Float, r: Float, couleur: Int, eclat: Double = 0.0, alpha: Float = 1f) {
        if (r <= 0.05f || alpha <= 0.01f) return
        val s = (r * (1 + 0.45 * eclat)).toFloat()
        val q = s * 0.2f
        cv.save()
        cv.translate(x, y)
        cv.rotate((eclat * 45).toFloat())
        chemin2.reset()
        chemin2.moveTo(0f, -s)
        chemin2.quadTo(q, -q, s, 0f); chemin2.quadTo(q, q, 0f, s)
        chemin2.quadTo(-q, q, -s, 0f); chemin2.quadTo(-q, -q, 0f, -s); chemin2.close()
        val pinceau = uni(couleur)
        pinceau.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        cv.drawPath(chemin2, pinceau)
        cv.restore()
    }

    // ---------------- LES LOGOS ----------------

    private fun etoile(cv: Canvas, src: Shader) {
        val r = 26f; val q = r * 0.085f
        chemin.reset()
        chemin.moveTo(CX, CY - r)
        chemin.quadTo(CX + q, CY - q, CX + r, CY); chemin.quadTo(CX + q, CY + q, CX, CY + r)
        chemin.quadTo(CX - q, CY + q, CX - r, CY); chemin.quadTo(CX - q, CY - q, CX, CY - r); chemin.close()
        remplir(cv, chemin, src)
    }

    // SOCIAL : la bulle du fond (plus pâle) fait un petit rebond quand le message « arrive » ; celle de devant
    // est séparée d'elle par un fin liseré découpé, et ses 3 points sautent comme quand quelqu'un écrit.
    private fun social(cv: Canvas, g: Double, src: Shader, lum: Int) {
        val recu = bosse(g, 1.75, 0.2)
        val s = (1 + 0.12 * recu).toFloat()
        cv.save()
        cv.scale(s, s, 59f, 34f)
        cv.saveLayerAlpha(calque, 153)                     // la bulle du fond, à 60 %
        chemin.reset(); chemin.addRoundRect(42f, 22f, 76f, 47f, 11f, 11f, Path.Direction.CW)
        remplir(cv, chemin, src)
        cv.restore()
        cv.restore()
        // la bulle de devant : d'abord on découpe son contour un peu plus large (le liseré), puis on la remplit.
        // La bulle et sa queue sont remplies séparément (dans un seul chemin, l'endroit où elles se chevauchent
        // pourrait devenir un trou).
        chemin.reset()
        chemin.addRoundRect(24f, 34f, 64f, 64f, 12f, 12f, Path.Direction.CW)
        chemin2.reset(); chemin2.moveTo(29f, 58f); chemin2.lineTo(25.5f, 72f); chemin2.lineTo(41f, 63f); chemin2.close()
        cv.drawPath(chemin, gomme(5f)); cv.drawPath(chemin2, gomme(5f))
        remplirArrondi(cv, chemin, src, 2f); remplirArrondi(cv, chemin2, src, 2f)
        for (i in 0..2) {
            val saut = bosse(g, 0.55 + 0.15 * i, 0.16) + bosse(g, 1.15 + 0.15 * i, 0.16)
            cv.drawCircle(34f + 10f * i, (49 - 3 * saut).toFloat(), 3.2f, gomme())
        }
        etincelle(cv, 74f, 22f, 5.5f, lum, recu)
    }

    // MUSIC & VIDEO : un vinyle (2 sillons et le trou du milieu découpés) qui tourne ; son reflet tourne avec
    // lui. Le bras de la platine, d'abord à côté, vient se poser sur le disque.
    private fun musique(cv: Canvas, g: Double, src: Shader, lum: Int) {
        val cx = 46f; val cy = 50f; val r = 22f
        val rot = cle(g, 0.3 to 0.0 by LIN, 2.6 to 300.0 by DOUX).toFloat()
        chemin.reset(); chemin.addCircle(cx, cy, r, Path.Direction.CW)
        remplir(cv, chemin, src)
        // le reflet : une part de tarte blanche très transparente, qui tourne
        cv.save()
        chemin.reset(); chemin.addCircle(cx, cy, r - 1, Path.Direction.CW)
        cv.clipPath(chemin)
        rect.set(cx - r, cy - r, cx + r, cy + r)
        cv.drawArc(rect, rot - 20f, 40f, true, uni(Color.argb(56, 255, 255, 255)))
        cv.restore()
        // les sillons et le trou
        cv.drawCircle(cx, cy, 17f, gomme(1.3f))
        cv.drawCircle(cx, cy, 12.5f, gomme(1.3f))
        cv.drawCircle(cx, cy, 2.2f, gomme())
        // les notes qui s'échappent (une fois le bras posé), sous le bras
        notesQuiSechappent(cv, g, src)
        // le bras : un liseré découpé autour, puis le bras lui-même
        val pose = cle(g, 0.4 to 0.0 by LIN, 0.85 to 1.0 by DOUX)
        val th = Math.toRadians(95 + 23 * pose)
        val px = 73f; val py = 25f; val l = 22f
        val hx = (px + l * cos(th)).toFloat(); val hy = (py + l * sin(th)).toFloat()
        cv.drawLine(px, py, hx, hy, gomme(6.2f))
        cv.drawCircle(hx, hy, 5f, gomme()); cv.drawCircle(px, py, 5.8f, gomme())
        cv.drawLine(px, py, hx, hy, trait(2.6f, src))
        remplirCercle(cv, hx, hy, 3.2f, src); remplirCercle(cv, px, py, 4f, src)
        etincelle(cv, 28f, 30f, 5f, lum, bosse(g, 0.9, 0.2))
    }

    // UNE NOTE DE MUSIQUE, la tête centrée en (0, 0) : une croche (une tête, une tige, un drapeau) ou une double
    // croche (deux têtes, deux tiges, une barre). Toutes les pièces sont tracées dans le même sens (CW), pour que
    // l'endroit où elles se chevauchent reste plein (sinon il pourrait devenir un trou).
    private val mat = Matrix()
    private val ovale = Path()
    private val note = Path()
    private fun tete(x: Float, y: Float) {
        ovale.reset(); ovale.addOval(-3.4f, -2.6f, 3.4f, 2.6f, Path.Direction.CW)
        mat.setRotate(-25f); mat.postTranslate(x, y)
        ovale.transform(mat)
        note.addPath(ovale)
    }
    private fun noteChemin(double: Boolean): Path {
        note.reset()
        tete(0f, 0f)
        note.addRect(2.2f, -12f, 3.8f, -0.4f, Path.Direction.CW)
        if (!double) {
            note.moveTo(3.8f, -12f)
            note.cubicTo(5f, -9f, 9f, -8.5f, 7.6f, -3.6f)
            note.cubicTo(7.2f, -6.6f, 5.6f, -7.6f, 3.8f, -8.4f)
            note.close()
        } else {
            tete(8.4f, -2.2f)
            note.addRect(10.6f, -14.2f, 12.2f, -2.6f, Path.Direction.CW)
            note.moveTo(2.2f, -12f); note.lineTo(12.2f, -14.6f); note.lineTo(12.2f, -11.4f); note.lineTo(2.2f, -8.8f); note.close()
        }
        return note
    }

    // LES NOTES QUI S'ÉCHAPPENT du vinyle : chacune naît sur le haut du disque (un liseré découpé autour la détache
    // du disque), grossit avec un petit élan, monte en se balançant, puis rapetisse et disparaît.
    // Une note = (naissance dans le geste, départ x, départ y, dérive en x, double croche ?)
    private class Note(val nait: Double, val x: Float, val y: Float, val derive: Float, val double: Boolean)
    private val NOTES = arrayOf(Note(0.95, 44f, 33f, -3f, false), Note(1.3, 55f, 34f, 5f, true), Note(1.65, 38f, 37f, -1f, false))
    private fun notesQuiSechappent(cv: Canvas, g: Double, src: Shader) {
        for ((i, n) in NOTES.withIndex()) {
            val u = (g - n.nait) / 1.15                     // l'âge de la note, de 0 (naissance) à 1 (disparue)
            if (u <= 0 || u >= 1) continue
            val apparait = DEC(min(1.0, u / 0.22))
            val part = if (u > 0.68) 1 - DOUX((u - 0.68) / 0.32) else 1.0
            val taille = (apparait * part * (if (n.double) 0.78 else 0.9)).toFloat()
            if (taille <= 0.02f) continue
            val x = (n.x + n.derive * u + 2.4 * sin(u * PI * 2.2 + i * 1.7)).toFloat()
            val y = (n.y - 19 * DEC(u)).toFloat()
            val penche = ((if (i % 2 == 1) 14 else -14) * sin(u * PI * 1.6)).toFloat()
            cv.save()
            cv.translate(x, y); cv.rotate(penche); cv.scale(taille, taille)
            val forme = noteChemin(n.double)
            cv.drawPath(forme, gomme(3f))
            remplir(cv, forme, src)
            cv.restore()
        }
    }

    private fun remplirCercle(cv: Canvas, x: Float, y: Float, r: Float, src: Shader) {
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.shader = src
        cv.drawCircle(x, y, r, p)
    }

    // PHOTOS (« A · Polaroïd », 11/11) : un boîtier d'appareil photo, avec un viseur et une fente découpés, et son
    // objectif (une bague découpée, et dedans le diaphragme : l'ouverture en hexagone et les 6 lamelles).
    // Le geste : le diaphragme fait « clic », l'étincelle du flash scintille DOUCEMENT (pas de flash blanc), puis une
    // photo sort par le haut (elle était cachée derrière le boîtier) et se développe : l'image, d'abord sombre,
    // prend les couleurs du logo, avec des montagnes et un petit soleil.
    private fun photo(cv: Canvas, g: Double, src: Shader, lum: Int) {
        val sortie = cle(g, 1.3 to 0.0 by LIN, 1.85 to 1.0 by DEC, 2.6 to 1.0 by LIN, 2.9 to 0.0 by ACC).toFloat()
        val devel = cle(g, 1.55 to 0.0 by LIN, 2.3 to 1.0 by DOUX)
        // 1) la photo, dessinée AVANT le boîtier (il la cache tant qu'elle n'est pas sortie)
        if (sortie > 0.01f) {
            cv.save()
            cv.translate(0f, -22f * sortie)
            rect.set(35f, 36f, 65f, 62f)
            cv.drawRoundRect(rect, 2.5f, 2.5f, uni(lum))                       // le cadre clair
            rect.set(38.5f, 39f, 61.5f, 55f)
            cv.drawRoundRect(rect, 1.2f, 1.2f, uni(Color.argb(217, 20, 14, 8)))  // l'image pas encore développée
            cv.saveLayerAlpha(calque, (255 * devel).toInt())                     // l'image qui se développe
            chemin.reset(); chemin.addRoundRect(38.5f, 39f, 61.5f, 55f, 1.2f, 1.2f, Path.Direction.CW)
            remplir(cv, chemin, src)
            chemin.reset()
            chemin.moveTo(38.5f, 55f); chemin.lineTo(45f, 46f); chemin.lineTo(49f, 50f); chemin.lineTo(54f, 43.5f)
            chemin.lineTo(61.5f, 52f); chemin.lineTo(61.5f, 55f); chemin.close()
            cv.drawPath(chemin, uni(Color.argb(140, 40, 22, 10)))                // les montagnes
            cv.drawCircle(44f, 43.5f, 2.6f, uni(lum))                            // le soleil
            cv.restore()
            cv.restore()
        }
        // 2) le boîtier, détaché de la photo par un liseré découpé
        chemin.reset(); chemin.addRoundRect(24f, 36f, 76f, 70f, 9f, 9f, Path.Direction.CW)
        cv.drawPath(chemin, gomme(3.2f))
        remplir(cv, chemin, src)
        rect.set(30f, 40.5f, 39f, 45.5f)
        cv.drawRoundRect(rect, 1.6f, 1.6f, gomme())                              // le viseur
        cv.drawLine(33f, 37.2f, 67f, 37.2f, gomme(1.3f))                         // la fente d'où sort la photo
        // 3) l'objectif : la bague, puis le diaphragme qui se ferme au clic et se rouvre avec un ressort
        cv.drawCircle(50f, 55f, 11.5f, gomme(2.2f))
        val ouv = cle(g, 0.9 to 5.2 by LIN, 1.05 to 1.4 by ACC, 1.4 to 5.2 by RESSORT)
        val ang = Math.toRadians(cle(g, 0.9 to 0.0 by LIN, 1.05 to 28.0 by ACC, 1.4 to 0.0 by DOUX))
        val pts = Array(6) { k -> floatArrayOf((50 + ouv * cos(ang + k * PI / 3)).toFloat(), (55 + ouv * sin(ang + k * PI / 3)).toFloat()) }
        chemin.reset(); chemin.moveTo(pts[0][0], pts[0][1])
        for (k in 1..5) chemin.lineTo(pts[k][0], pts[k][1])
        chemin.close()
        cv.drawPath(chemin, gomme())
        // les lamelles : chaque côté de l'hexagone, prolongé vers l'extérieur (sans sortir de l'objectif)
        cv.save()
        chemin.reset(); chemin.addCircle(50f, 55f, 9.2f, Path.Direction.CW)
        cv.clipPath(chemin)
        val gm = gomme(1.1f)
        for (k in 0..5) {
            val a = pts[(k + 5) % 6]; val b = pts[k]
            val dx = b[0] - a[0]; val dy = b[1] - a[1]; val l = hypot(dx, dy).coerceAtLeast(0.01f)
            cv.drawLine(b[0], b[1], b[0] + dx / l * 20f, b[1] + dy / l * 20f, gm)
        }
        cv.restore()
        // 4) le flash : l'étincelle, qui scintille au moment du clic
        etincelle(cv, 68.5f, 43f, 4.6f, lum, bosse(g, 1.05, 0.2))
    }

    // FINANCE : une courbe de bourse qui s'efface puis se retrace en montant, avec l'aire dessous (à 35 %) et un
    // point au bout ; arrivée en haut, l'étincelle apparaît au sommet et brille
    private val BOURSE = arrayOf(floatArrayOf(24f, 63f), floatArrayOf(33f, 55f), floatArrayOf(42f, 59f),
        floatArrayOf(52f, 45f), floatArrayOf(61f, 49f), floatArrayOf(73f, 32f))
    private fun finance(cv: Canvas, g: Double, src: Shader, lum: Int) {
        val prog = cle(g, 0.45 to 1.0 by LIN, 0.58 to 0.0 by ACC, 1.6 to 1.0 by DOUX)
        val ligne = lignePartielle(BOURSE, prog)
        if (ligne != null) {
            // l'aire sous la courbe : la même ligne, refermée jusqu'en bas
            chemin2.set(ligne)
            chemin2.lineTo(finX, 70f); chemin2.lineTo(BOURSE[0][0], 70f); chemin2.close()
            cv.saveLayerAlpha(calque, 89)
            remplir(cv, chemin2, src)
            cv.restore()
            cv.drawPath(ligne, trait(4.4f, src))
            remplirCercle(cv, finX, finY, 3.6f, src)
        }
        val vu = cle(g, 0.45 to 1.0 by LIN, 0.55 to 0.0 by LIN, 1.5 to 0.0 by LIN, 1.7 to 1.0 by DOUX)
        etincelle(cv, 76f, 23f, (6 * vu).toFloat(), lum, bosse(g, 1.8, 0.25))
    }

    // MAPS & TRAVEL : une carte pliée en 3 (le panneau du milieu plus sombre, les plis découpés). Un itinéraire
    // s'efface puis se trace en pointillés depuis le point de départ ; quand il arrive, une épingle claire tombe
    // (avec un rebond) et l'étincelle brille. Ensuite le logo part et laisse place aux apps du dossier.
    private val ROUTE: Array<FloatArray> = Array(31) { i ->
        // une courbe de Bézier (départ, 2 points qui la « tirent », arrivée), découpée en 30 petits segments
        val u = i / 30f; val v = 1 - u
        val p0 = floatArrayOf(29f, 61f); val p1 = floatArrayOf(36f, 36f); val p2 = floatArrayOf(55f, 70f); val p3 = floatArrayOf(66f, 40f)
        floatArrayOf(v * v * v * p0[0] + 3 * v * v * u * p1[0] + 3 * v * u * u * p2[0] + u * u * u * p3[0],
                     v * v * v * p0[1] + 3 * v * v * u * p1[1] + 3 * v * u * u * p2[1] + u * u * u * p3[1])
    }
    private val PANNEAUX = arrayOf(
        floatArrayOf(24f, 28f, 41f, 25f, 41f, 67f, 24f, 70f),
        floatArrayOf(41f, 25f, 59f, 28f, 59f, 70f, 41f, 67f),
        floatArrayOf(59f, 28f, 76f, 25f, 76f, 67f, 59f, 70f))
    private fun panneau(k: Int): Path {
        val c = PANNEAUX[k]
        chemin.reset(); chemin.moveTo(c[0], c[1]); chemin.lineTo(c[2], c[3]); chemin.lineTo(c[4], c[5]); chemin.lineTo(c[6], c[7]); chemin.close()
        return chemin
    }
    private fun trajets(cv: Canvas, g: Double, src: Shader, lum: Int) {
        for (k in 0..2) remplirArrondi(cv, panneau(k), src, 2.4f)
        cv.drawPath(panneau(1), uni(Color.argb(56, 0, 0, 0)))          // le pli du milieu, plus sombre
        val pli = gomme(1.1f)
        cv.drawLine(41f, 25f, 41f, 67f, pli); cv.drawLine(59f, 28f, 59f, 70f, pli)
        // l'itinéraire, découpé en pointillés
        val prog = cle(g, 0.4 to 1.0 by LIN, 0.55 to 0.0 by ACC, 1.7 to 1.0 by DOUX)
        cv.drawCircle(ROUTE[0][0], ROUTE[0][1], 3.2f, gomme())
        lignePartielle(ROUTE, prog)?.let {
            val route = gomme(2.6f)
            route.pathEffect = pointilles
            cv.drawPath(it, route)
            route.pathEffect = null
        }
        // l'épingle d'arrivée
        val tombe = cle(g, 0.45 to 1.0 by LIN, 0.55 to 0.0 by ACC, 1.65 to 0.0 by LIN, 2.0 to 1.0 by RESSORT).toFloat()
        if (tombe > 0.02f) {
            val ex = 66f; val ey = 40f
            cv.save()
            cv.translate(ex, ey); cv.scale(tombe, tombe); cv.translate(-ex, -ey + 6 * (1 - min(1f, tombe)))
            chemin.reset()
            chemin.moveTo(ex, ey)
            chemin.lineTo(ex + 5.5f * cos(Math.toRadians(30.0)).toFloat(), ey - 10 + 5.5f * sin(Math.toRadians(30.0)).toFloat())
            rect.set(ex - 5.5f, ey - 15.5f, ex + 5.5f, ey - 4.5f)
            chemin.arcTo(rect, 30f, -240f)
            chemin.close()
            cv.drawPath(chemin, gomme(3.2f))                    // un liseré découpé autour de l'épingle
            cv.drawPath(chemin, uni(lum))
            cv.drawCircle(ex, ey - 10, 2f, gomme())
            cv.restore()
        }
        etincelle(cv, 75f, 21f, 4.8f, lum, bosse(g, 2.05, 0.25))
    }

    // PRODUCTIVITY : une carte de tâches (3 lignes : une case ronde et une barre, découpées). Les cases se
    // remplissent l'une après l'autre, avec une petite coche dedans ; l'étincelle brille à la dernière.
    private fun travail(cv: Canvas, g: Double, src: Shader, lum: Int) {
        chemin.reset(); chemin.addRoundRect(25f, 23f, 75f, 73f, 11f, 11f, Path.Direction.CW)
        remplir(cv, chemin, src)
        for ((k, y) in listOf(36f, 48f, 60f).withIndex()) {
            val f = cle(g, (0.55 + 0.32 * k) to 0.0 by LIN, (0.78 + 0.32 * k) to 1.0 by RESSORT).toFloat()
            val longueur = if (k < 2) 67f else 60f
            cv.drawCircle(35f, y, 4.6f, gomme(2f))
            if (f > 0.02f) cv.drawCircle(35f, y, 4.6f * min(f, 1.15f), gomme())
            cv.drawLine(44f, y, longueur, y, gomme(3.6f))
            if (f > 0.3f) {
                cv.save()
                cv.translate(35f, y); cv.scale(min(f, 1f), min(f, 1f))
                chemin.reset(); chemin.moveTo(-2.4f, 0.1f); chemin.lineTo(-0.6f, 2f); chemin.lineTo(2.6f, -1.9f)
                cv.drawPath(chemin, trait(1.9f, src))
                cv.restore()
            }
        }
        etincelle(cv, 74f, 24f, 5f, lum, bosse(g, 1.55, 0.22))
    }

    // HEALTH : un cœur, traversé par la ligne d'un électrocardiogramme (découpée) ; la ligne s'efface puis
    // se retrace de gauche à droite, et le cœur bat (« poum-poum ») au moment où elle passe le pic
    private val ECG = arrayOf(floatArrayOf(20f, 46f), floatArrayOf(35f, 46f), floatArrayOf(39.5f, 40f), floatArrayOf(45f, 57f),
        floatArrayOf(51f, 31f), floatArrayOf(56.5f, 51f), floatArrayOf(60f, 46f), floatArrayOf(80f, 46f))
    private fun sante(cv: Canvas, g: Double, src: Shader, lum: Int) {
        val bat = 0.12 * bosse(g, 1.02, 0.13) + 0.07 * bosse(g, 1.26, 0.13) + 0.12 * bosse(g, 2.05, 0.13) + 0.07 * bosse(g, 2.29, 0.13)
        val s = (1 + bat).toFloat()
        val trace = cle(g, 0.45 to 1.0 by LIN, 0.6 to 0.0 by ACC, 1.45 to 1.0 by DOUX)
        cv.save()
        cv.scale(s, s, CX, CY)
        chemin.reset()
        chemin.moveTo(50f, 71f)
        chemin.cubicTo(40f, 63f, 24f, 53f, 24f, 39f)
        chemin.cubicTo(24f, 30f, 31f, 24f, 38.5f, 24f)
        chemin.cubicTo(44f, 24f, 48f, 27.5f, 50f, 31f)
        chemin.cubicTo(52f, 27.5f, 56f, 24f, 61.5f, 24f)
        chemin.cubicTo(69f, 24f, 76f, 30f, 76f, 39f)
        chemin.cubicTo(76f, 53f, 60f, 63f, 50f, 71f)
        chemin.close()
        remplir(cv, chemin, src)
        lignePartielle(ECG, trace)?.let { cv.drawPath(it, gomme(3.6f)) }
        cv.restore()
        etincelle(cv, 73f, 21f, 5f, lum, bosse(g, 1.1, 0.22))
    }

    // SHOPPING : l'étincelle, posée au-dessus du sac, tombe dedans ; le sac l'attrape en s'écrasant un peu,
    // se balance autour de sa poignée, puis une nouvelle étincelle réapparaît au-dessus
    private fun shopping(cv: Canvas, g: Double, src: Shader, lum: Int) {
        val chute = cle(g, 0.5 to 0.0 by LIN, 0.88 to 1.0 by ACC).toFloat()
        val ecr = cle(g, 0.88 to 1.0 by LIN, 0.98 to 0.9 by DEC, 1.3 to 1.0 by RESSORT).toFloat()
        val rot = cle(g, 0.95 to 0.0 by LIN, 1.15 to 6.0 by DEC, 1.5 to -4.0 by DOUX, 1.85 to 2.0 by DOUX, 2.15 to 0.0 by DOUX)
        cv.save()
        cv.scale(2 - ecr, ecr, 50f, 71f)
        cv.rotate(rot.toFloat(), 50f, 26f)
        // la poignée (un demi-cercle et ses deux petits bouts)
        chemin.reset()
        rect.set(40.5f, 28.5f, 59.5f, 47.5f)
        chemin.arcTo(rect, 180f, 180f, true)
        chemin.lineTo(59.5f, 41f)
        chemin.moveTo(40.5f, 41f); chemin.lineTo(40.5f, 38f)
        cv.drawPath(chemin, trait(3.8f, src))
        // le sac, les deux trous de la poignée et la bande du rabat
        chemin.reset(); chemin.moveTo(30.5f, 37f); chemin.lineTo(69.5f, 37f); chemin.lineTo(72f, 71f); chemin.lineTo(28f, 71f); chemin.close()
        remplirArrondi(cv, chemin, src, 5f)
        cv.drawCircle(40.5f, 43.5f, 2.3f, gomme())
        cv.drawCircle(59.5f, 43.5f, 2.3f, gomme())
        cv.drawLine(31.5f, 50f, 68.5f, 50f, gomme(1.4f))
        cv.restore()
        // l'étincelle
        var x = 67 + (50 - 67) * chute; var y = 21 + (40 - 21) * chute
        var r = 5.5f * (1 - 0.6f * chute)
        var a = if (chute < 0.95f) 1f else 0f
        if (g > 1.85) { x = 67f; y = 21f; r = 5.5f; a = cle(g, 1.85 to 0.0 by LIN, 2.2 to 1.0 by DOUX).toFloat() }
        etincelle(cv, x, y, r, lum, 0.0, a)
    }

    // SETTINGS : 3 réglettes. La partie à gauche du curseur est pleine, le reste pâle ; chaque curseur a un liseré
    // découpé autour. Les curseurs glissent (l'un après l'autre) puis reviennent ; celui du milieu est l'étincelle.
    private val REPOS = floatArrayOf(0.28f, 0.66f, 0.42f)
    private val CIBLE = floatArrayOf(0.72f, 0.30f, 0.80f)
    private fun reglages(cv: Canvas, g: Double, src: Shader, lum: Int) {
        for ((k, y) in listOf(33f, 48f, 63f).withIndex()) {
            val m = cle(g, (0.45 + 0.12 * k) to 0.0 by LIN, (0.95 + 0.12 * k) to 1.0 by DOUX,
                        (1.45 + 0.12 * k) to 1.0 by LIN, (2.0 + 0.12 * k) to 0.0 by DOUX)
            val f = REPOS[k] + (CIBLE[k] - REPOS[k]) * m.toFloat()
            val x = 26f + 48f * f
            cv.saveLayerAlpha(calque, 102)                 // le rail, à 40 %
            cv.drawLine(26f, y, 74f, y, trait(3.6f, src))
            cv.restore()
            cv.drawLine(26f, y, x, y, trait(3.6f, src))
            cv.drawCircle(x, y, 7.8f, gomme())
            if (k == 1) etincelle(cv, x, y, 7.6f, lum, m)
            else remplirCercle(cv, x, y, 5.6f, src)
        }
    }

    // NEWS : un journal au coin plié (le coin est clair, séparé par un liseré découpé). Le gros titre et les
    // lignes sont découpés ; les lignes s'effacent puis s'écrivent l'une après l'autre, et l'étincelle brille
    // au coin (« dernière minute »).
    private fun infos(cv: Canvas, g: Double, src: Shader, lum: Int) {
        chemin.reset()
        chemin.moveTo(25f, 24f); chemin.lineTo(63f, 24f); chemin.lineTo(75f, 36f); chemin.lineTo(75f, 72f); chemin.lineTo(25f, 72f); chemin.close()
        remplirArrondi(cv, chemin, src, 5f)
        chemin.reset(); chemin.moveTo(62.5f, 22f); chemin.lineTo(62.5f, 36.5f); chemin.lineTo(77f, 36.5f)
        cv.drawPath(chemin, gomme(2.2f))
        chemin.reset(); chemin.moveTo(64.5f, 26f); chemin.lineTo(64.5f, 34.5f); chemin.lineTo(73f, 34.5f); chemin.close()
        val coin = uni(lum)
        coin.style = Paint.Style.FILL_AND_STROKE; coin.strokeWidth = 1.5f; coin.strokeJoin = Paint.Join.ROUND
        cv.drawPath(chemin, coin)
        val pr = cle(g, 0.45 to 1.0 by LIN, 0.58 to 0.0 by ACC, 1.8 to 1.0 by LIN)
        rect.set(31f, 30.5f, 56f, 37.5f)
        cv.drawRoundRect(rect, 2.5f, 2.5f, gomme())
        val lignes = arrayOf(floatArrayOf(46f, 69f), floatArrayOf(54f, 69f), floatArrayOf(62f, 58f))
        for ((k, l) in lignes.withIndex()) {
            val part = max(0.0, min(1.0, pr * 3 - k)).toFloat()
            if (part > 0.02f) cv.drawLine(31f, l[0], 31f + (l[1] - 31f) * part, l[0], gomme(3.2f))
        }
        etincelle(cv, 76f, 22f, 5f, lum, bosse(g, 1.9, 0.25))
    }
}
