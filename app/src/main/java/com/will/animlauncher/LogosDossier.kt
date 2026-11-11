package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
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
import kotlin.math.min
import kotlin.math.sin

// LES LOGOS DES DOSSIERS ANIMÉS (11/11, validés par Will sur l'aperçu dossiers_reel.mp4).
// Chaque catégorie a son logo, dans le même esprit que l'étoile de l'IA : une forme pleine et simple
// (lisible même à la taille d'une icône), un dégradé de 3 couleurs à elle, et un petit GESTE :
//   AI            l'étoile (inchangée)
//   Social        une bulle de discussion ; 3 points « en train d'écrire » sautent l'un après l'autre
//   Music & Video le triangle « lecture », qui bat au rythme
//   Photos        un diaphragme d'appareil photo, qui se ferme et se rouvre (« clic »)
//   Finance       une pièce avec une flèche qui monte ; elle fait un tour sur elle-même
//   Maps & Travel une épingle de carte qui fait un petit bond, avec son ombre
//   Productivity  une case cochée : la coche s'efface puis se redessine
//   Health        un cœur traversé par la ligne d'un électrocardiogramme ; il bat quand la ligne passe le pic
//   Shopping      un sac qui se balance, accroché à sa poignée
//   Settings      deux engrenages qui s'entraînent (le petit tourne dans l'autre sens, plus vite)
//   News          un point « en direct » et des ondes qui s'allument vers l'extérieur
//
// LE REPÈRE : comme dans WidgetDossier.kt, on dessine dans un carré de 100 x 100 (tout le galet) et le
// logo est centré en (50, 48), d'un « rayon » d'environ 26 (celui de l'étoile).
// LE GESTE : « g » est l'horloge du geste, en secondes. Au repos, g = 0,3 (c'est la pose du logo quand il
// apparaît et quand il renaît) ; WidgetDossier la fait avancer pendant que le logo est affiché.
// LES TROUS (les points de la bulle, le centre de l'épingle, la ligne du cœur…) sont « découpés » dans le
// logo : on voit le galet au travers. Pour ça, le logo est dessiné sur un calque à part (saveLayer), puis
// on efface ces zones avec un pinceau spécial (mode CLEAR = « rendre transparent »).
object LogosDossier {
    private const val CX = 50f
    private const val CY = 48f

    // Les 3 couleurs du dégradé de chaque logo (du haut-gauche vers le bas-droite).
    private fun c(hex: Int) = Color.rgb((hex shr 16) and 255, (hex shr 8) and 255, hex and 255)
    private val COULEURS = mapOf(
        "ia" to intArrayOf(c(0xFF8C5B), c(0xFF6B9B), c(0xC4D13F)),
        "social" to intArrayOf(c(0x6CF0C2), c(0x3FD3E0), c(0x3F9BFF)),
        "musique" to intArrayOf(c(0xFF7AC8), c(0xFF4F7A), c(0xFF9F4F)),
        "photo" to intArrayOf(c(0xFFD86B), c(0xFF8A5C), c(0xFF5C93)),
        "finance" to intArrayOf(c(0xFFE58A), c(0xF5B43C), c(0xD9862B)),
        "trajets" to intArrayOf(c(0xC2F06A), c(0x5ED68A), c(0x2FBFA0)),
        "travail" to intArrayOf(c(0xC29BFF), c(0x7C6CFF), c(0x5E8BFF)),
        "sante" to intArrayOf(c(0xFF9E7A), c(0xFF4D5E), c(0xE8457F)),
        "shopping" to intArrayOf(c(0xFFB27A), c(0xFF7FA8), c(0xC98BFF)),
        "reglages" to intArrayOf(c(0xEAF1FB), c(0x93B4E4), c(0x5E80D2)),
        "infos" to intArrayOf(c(0xEAF5FF), c(0xA9D4FF), c(0x7FA8FF)),
    )

    // Une catégorie qu'on sait dessiner ? (sinon : l'étoile)
    fun connu(groupe: String) = groupe in COULEURS

    fun couleurs(groupe: String): IntArray = COULEURS[groupe] ?: COULEURS.getValue("ia")

    // LA ROTATION quand le logo part (en degrés) et le petit recul avec lequel il revient.
    // null = l'étoile, qui garde exactement ses mouvements d'origine (voir WidgetDossier.Boucle.logo).
    // Les logos ronds (diaphragme, pièce, « en direct ») peuvent faire un demi-tour sans paraître à l'envers ;
    // les autres ne tournent que de 40 degrés, pour qu'on ne les voie jamais tête en bas.
    fun tours(groupe: String): Pair<Double, Double>? = when (groupe) {
        "ia" -> null
        "photo", "finance", "infos" -> 180.0 to 90.0
        else -> if (connu(groupe)) 40.0 to 25.0 else null
    }

    // ---------------- les pinceaux ----------------
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val efface = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val calque = RectF(4f, 2f, 96f, 94f)            // la zone du calque (un peu plus grande que les logos)
    private val chemin = Path()
    private val rect = RectF()

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

    // LE LOGO, dans le repère 100 x 100. « cols » = les 3 couleurs (celles de la catégorie, ou de la roue).
    // « alpha » : pour le faire disparaître en fondu (0 à 255).
    // (synchronisé : le widget le dessine en fond pendant que le dossier peut le dessiner à l'écran)
    @Synchronized
    fun dessiner(cv: Canvas, groupe: String, g: Double, cols: IntArray, alpha: Int = 255) {
        val src = degradePour(cols)
        cv.saveLayerAlpha(calque, alpha.coerceIn(0, 255))
        when (groupe) {
            "social" -> social(cv, g, src)
            "musique" -> musique(cv, g, src)
            "photo" -> photo(cv, g, src)
            "finance" -> finance(cv, g, src)
            "trajets" -> trajets(cv, g, src)
            "travail" -> travail(cv, g, src)
            "sante" -> sante(cv, g, src)
            "shopping" -> shopping(cv, g, src)
            "reglages" -> reglages(cv, g, src)
            "infos" -> infos(cv, g, src)
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
        return efface
    }

    // Une ligne brisée tracée seulement en partie (« part » de 0 à 1) : pour la coche et l'électrocardiogramme.
    private fun lignePartielle(points: Array<FloatArray>, part: Double): Path? {
        val longueurs = (0 until points.size - 1).map { hypot(points[it + 1][0] - points[it][0], points[it + 1][1] - points[it][1]) }
        var reste = part * longueurs.sum()
        if (reste <= 0.3) return null
        chemin.reset(); chemin.moveTo(points[0][0], points[0][1])
        for (k in longueurs.indices) {
            val f = min(1.0, reste / longueurs[k]).toFloat()
            chemin.lineTo(points[k][0] + (points[k + 1][0] - points[k][0]) * f, points[k][1] + (points[k + 1][1] - points[k][1]) * f)
            reste -= longueurs[k]
            if (reste <= 0) break
        }
        return chemin
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

    // SOCIAL : la bulle, puis sa queue en bas à gauche (dessinées séparément : si on les mettait dans le même
    // chemin, la partie où elles se chevauchent pourrait devenir un trou), puis les 3 points découpés.
    private fun social(cv: Canvas, g: Double, src: Shader) {
        chemin.reset(); chemin.addRoundRect(25f, 26f, 75f, 63f, 15f, 15f, Path.Direction.CW)
        remplirArrondi(cv, chemin, src, 2.5f)
        chemin.reset(); chemin.moveTo(31f, 56f); chemin.lineTo(27.5f, 71.5f); chemin.lineTo(44f, 61f); chemin.close()
        remplirArrondi(cv, chemin, src, 2.5f)
        for (i in 0..2) {
            val saut = bosse(g, 0.55 + 0.16 * i, 0.17) + bosse(g, 1.75 + 0.16 * i, 0.17)
            cv.drawCircle(38f + 12f * i, (44.5 - 3.2 * saut).toFloat(), 3.7f, gomme())
        }
    }

    // MUSIQUE : le triangle « lecture » grossit un peu sur chaque temps (plus fort sur les temps forts)
    private fun musique(cv: Canvas, g: Double, src: Shader) {
        val temps = doubleArrayOf(0.45, 0.9, 1.35, 1.8, 2.25)
        var bat = 0.0
        for ((k, c) in temps.withIndex()) bat += bosse(g, c, 0.13) * (if (k % 2 == 0) 1.0 else 0.55)
        val s = (1 + 0.07 * bat).toFloat()
        cv.save()
        cv.scale(s, s, 46f, CY)
        chemin.reset(); chemin.moveTo(35f, 27f); chemin.lineTo(35f, 69f); chemin.lineTo(69f, 48f); chemin.close()
        remplirArrondi(cv, chemin, src, 7f)
        cv.restore()
    }

    // PHOTOS : un disque, avec au milieu l'ouverture en hexagone et les 6 lamelles (des fentes fines)
    private fun photo(cv: Canvas, g: Double, src: Shader) {
        chemin.reset(); chemin.addCircle(CX, CY, 25f, Path.Direction.CW)
        remplir(cv, chemin, src)
        val ouv = cle(g, 1.15 to 9.5 by LIN, 1.32 to 2.6 by ACC, 1.7 to 9.5 by RESSORT)
        val ang = Math.toRadians(cle(g, 1.15 to 0.0 by LIN, 1.32 to 28.0 by ACC, 1.7 to 0.0 by DOUX))
        val pts = Array(6) { k -> floatArrayOf((CX + ouv * cos(ang + k * PI / 3)).toFloat(), (CY + ouv * sin(ang + k * PI / 3)).toFloat()) }
        chemin.reset(); chemin.moveTo(pts[0][0], pts[0][1])
        for (k in 1..5) chemin.lineTo(pts[k][0], pts[k][1])
        chemin.close()
        cv.drawPath(chemin, gomme())
        // les lamelles : chaque côté de l'hexagone, prolongé vers l'extérieur (sans dépasser du disque)
        cv.save()
        rect.set(CX - 20.5f, CY - 20.5f, CX + 20.5f, CY + 20.5f)
        val disque = Path().apply { addOval(rect, Path.Direction.CW) }
        cv.clipPath(disque)
        val gm = gomme(1.7f)
        for (k in 0..5) {
            val a = pts[(k + 5) % 6]; val b = pts[k]
            val dx = b[0] - a[0]; val dy = b[1] - a[1]; val l = hypot(dx, dy).coerceAtLeast(0.01f)
            cv.drawLine(b[0], b[1], b[0] + dx / l * 40f, b[1] + dy / l * 40f, gm)
        }
        cv.restore()
    }

    // FINANCE : la pièce se tourne (on l'écrase à l'horizontale), avec son anneau et sa flèche découpés
    private fun finance(cv: Canvas, g: Double, src: Shader) {
        val tour = cle(g, 1.0 to 0.0 by LIN, 1.9 to 1.0 by DOUX)
        var sx = cos(2 * PI * tour).toFloat()
        if (abs(sx) < 0.08f) sx = if (sx >= 0) 0.08f else -0.08f      // jamais tout à fait plate
        cv.save()
        cv.scale(sx, 1f, CX, CY)
        chemin.reset(); chemin.addCircle(CX, CY, 24.5f, Path.Direction.CW)
        remplir(cv, chemin, src)
        cv.drawCircle(CX, CY, 19f, gomme(1.8f))
        chemin.reset(); chemin.moveTo(38f, 58f); chemin.lineTo(46f, 50f); chemin.lineTo(51f, 54f); chemin.lineTo(59f, 45.5f)
        cv.drawPath(chemin, gomme(4f))
        chemin.reset(); chemin.moveTo(63.5f, 41f); chemin.lineTo(53.8f, 42.2f); chemin.lineTo(62.3f, 50.7f); chemin.close()
        val pointe = gomme(1.6f); pointe.style = Paint.Style.FILL_AND_STROKE
        cv.drawPath(chemin, pointe)
        cv.restore()
    }

    // MAPS & TRAVEL : l'épingle saute, s'écrase un peu en retombant ; son ombre rétrécit quand elle est en l'air
    private fun trajets(cv: Canvas, g: Double, src: Shader) {
        val haut = cle(g, 0.8 to 0.0 by LIN, 1.12 to -7.5 by DEC, 1.42 to 0.0 by ACC).toFloat()
        val ecr = cle(g, 1.42 to 1.0 by LIN, 1.52 to 0.88 by DEC, 1.85 to 1.0 by RESSORT).toFloat()
        val ombre = 1 - 0.35f * (-haut / 7.5f)
        p.reset(); p.isAntiAlias = true; p.color = Color.argb((0.32f * ombre * 255).toInt(), 0, 0, 0)
        rect.set(CX - 9.5f * ombre, 75f - 2.4f * ombre, CX + 9.5f * ombre, 75f + 2.4f * ombre)
        cv.drawOval(rect, p)
        cv.save()
        cv.translate(CX, 72f); cv.scale(2 - ecr, ecr); cv.translate(-CX, -72f + haut)
        val cx = CX; val cy = 40f; val r = 16.5f
        chemin.reset()
        chemin.moveTo(CX, 72f)
        chemin.lineTo(cx + r * cos(Math.toRadians(30.0)).toFloat(), cy + r * sin(Math.toRadians(30.0)).toFloat())
        rect.set(cx - r, cy - r, cx + r, cy + r)
        chemin.arcTo(rect, 30f, -240f)              // le rond du haut, en passant par le dessus
        chemin.close()
        remplirArrondi(cv, chemin, src, 3f)
        cv.drawCircle(cx, cy, 6.6f, gomme())
        cv.restore()
    }

    // PRODUCTIVITY : le cadre arrondi, et la coche qui s'efface vite puis se redessine
    private fun travail(cv: Canvas, g: Double, src: Shader) {
        val prog = cle(g, 1.0 to 1.0 by LIN, 1.16 to 0.0 by ACC, 1.7 to 1.0 by DOUX)
        rect.set(27.5f, 25.5f, 72.5f, 70.5f)
        cv.drawRoundRect(rect, 12f, 12f, trait(4.4f, src))
        val coche = lignePartielle(arrayOf(floatArrayOf(38f, 48.5f), floatArrayOf(46.5f, 57f), floatArrayOf(63f, 39f)), prog)
        if (coche != null) cv.drawPath(coche, trait(5.2f, src))
    }

    // HEALTH : un cœur, traversé par la ligne d'un électrocardiogramme (découpée) ; la ligne s'efface puis
    // se retrace de gauche à droite, et le cœur bat (« poum-poum ») au moment où elle passe le pic
    private val ECG = arrayOf(floatArrayOf(20f, 46f), floatArrayOf(35f, 46f), floatArrayOf(39.5f, 40f), floatArrayOf(45f, 57f),
        floatArrayOf(51f, 31f), floatArrayOf(56.5f, 51f), floatArrayOf(60f, 46f), floatArrayOf(80f, 46f))
    private fun sante(cv: Canvas, g: Double, src: Shader) {
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
    }

    // SHOPPING : le sac se balance autour du haut de sa poignée, puis s'arrête en douceur
    private fun shopping(cv: Canvas, g: Double, src: Shader) {
        val rot = cle(g, 0.6 to 0.0 by LIN, 0.85 to 8.0 by DEC, 1.25 to -5.5 by DOUX, 1.62 to 3.0 by DOUX, 1.95 to 0.0 by DOUX)
        cv.save()
        cv.rotate(rot.toFloat(), 50f, 26f)
        // la poignée (un demi-cercle et ses deux petits bouts)
        chemin.reset()
        rect.set(40.5f, 28.5f, 59.5f, 47.5f)
        chemin.arcTo(rect, 180f, 180f, true)
        chemin.lineTo(59.5f, 41f)
        chemin.moveTo(40.5f, 41f); chemin.lineTo(40.5f, 38f)
        cv.drawPath(chemin, trait(3.8f, src))
        // le sac, et les deux trous de la poignée
        chemin.reset(); chemin.moveTo(30.5f, 37f); chemin.lineTo(69.5f, 37f); chemin.lineTo(72f, 71f); chemin.lineTo(28f, 71f); chemin.close()
        remplirArrondi(cv, chemin, src, 5f)
        cv.drawCircle(40.5f, 43.5f, 2.3f, gomme())
        cv.drawCircle(59.5f, 43.5f, 2.3f, gomme())
        cv.restore()
    }

    // SETTINGS : deux engrenages. Le grand (8 dents) tourne de 45 degrés ; le petit (6 dents) est entraîné
    // dans l'autre sens, 8/6 fois plus vite (comme de vrais engrenages).
    private fun reglages(cv: Canvas, g: Double, src: Shader) {
        val a = Math.toRadians(cle(g, 0.3 to 0.0 by LIN, 2.4 to 45.0 by DOUX))
        engrenage(cv, 45f, 54f, 19.5f, 15f, 8, a, src, 6.5f)
        engrenage(cv, 65f, 34f, 11f, 8f, 6, -a * 8 / 6 + Math.toRadians(30.0), src, 3.4f)
    }

    // Un engrenage : chaque dent = 4 points (base, haut, haut, base). Les points sont calculés directement à
    // leur place (sans tourner la toile) : le dégradé reste ainsi le même sur les deux roues.
    private fun engrenage(cv: Canvas, cx: Float, cy: Float, R: Float, r: Float, dents: Int, rot: Double, src: Shader, trou: Float) {
        val pas = 2 * PI / dents
        chemin.reset()
        for (k in 0 until dents) {
            val a = k * pas
            val l = pas * 0.25                // demi-largeur d'une dent en haut
            val b = pas * 0.36                // demi-largeur d'une dent à la base
            for ((rayon, ang) in listOf(r to a - b, R to a - l, R to a + l, r to a + b)) {
                val x = (cx + rayon * cos(ang + rot)).toFloat(); val y = (cy + rayon * sin(ang + rot)).toFloat()
                if (chemin.isEmpty) chemin.moveTo(x, y) else chemin.lineTo(x, y)
            }
        }
        chemin.close()
        remplirArrondi(cv, chemin, src, 2.2f)
        cv.drawCircle(cx, cy, trou, gomme())
    }

    // NEWS : le point grossit un peu, puis les deux ondes de chaque côté s'allument, de l'intérieur vers l'extérieur
    private fun infos(cv: Canvas, g: Double, src: Shader) {
        val pouls = bosse(g, 0.55, 0.22) + bosse(g, 1.75, 0.22)
        chemin.reset(); chemin.addCircle(CX, CY, (5.8 * (1 + 0.18 * pouls)).toFloat(), Path.Direction.CW)
        remplir(cv, chemin, src)
        for ((k, rayon) in listOf(13f, 22f).withIndex()) {
            val lum = bosse(g, 0.72 + 0.17 * k, 0.24) + bosse(g, 1.92 + 0.17 * k, 0.24)
            val pinceau = trait(4.2f, src)
            pinceau.alpha = ((0.62 + 0.38 * min(1.0, lum)) * 255).toInt()
            val rr = (rayon + 1.2 * lum).toFloat()
            rect.set(CX - rr, CY - rr, CX + rr, CY + rr)
            val demi = Math.toDegrees(0.72).toFloat()
            cv.drawArc(rect, -demi, 2 * demi, false, pinceau)              // l'onde de droite
            cv.drawArc(rect, 180f - demi, 2 * demi, false, pinceau)        // l'onde de gauche
        }
    }
}
