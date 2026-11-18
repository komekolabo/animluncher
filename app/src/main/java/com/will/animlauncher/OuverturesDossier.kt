package com.will.animlauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// LES DEUX NOUVELLES OUVERTURES « GÉNÉRALES » DES DOSSIERS (18/11, validées par Will sur l'aperçu vidéo).
// Les dossiers sans transition à eux (tous sauf Maps, Health, Photos, Finance) tirent au hasard, à chaque
// ouverture, entre : la poussière d'origine, ORBITE et CARTES (voir TransitionsDossier.pour).
// Elles reprennent les ingrédients de l'ouverture d'origine (traînées de poussière, éclosion en ressort avec une
// petite rotation qui se remet droite, onde fine autour de l'app) pour rester de la même famille.
// L'aperçu (JavaScript, même dessin) : /home/claude/apercu_dossier/propositions.html (pas dans le repo).
// Rappel : t = le temps depuis l'ouverture (en secondes) ; les tailles sont en « k » (largeur de l'écran / 540).

// =====================================================================================================
// A · ORBITE : l'anneau d'icônes du widget se déploie
// =====================================================================================================
// Sur l'accueil, le widget montre déjà les apps du dossier qui tournent en cercle. À l'appui, cet anneau sort du
// widget et monte au centre de la fenêtre en grandissant et en tournant, tracé par une fine poussière plus dense
// juste derrière chaque app (une petite queue de comète). Puis les apps quittent l'orbite une à une, avec la
// traînée de poussière d'origine, et éclosent à leur place. L'anneau s'éteint doucement.
class EffetOrbite : EffetOuverture() {
    override val panneauDuCentre = true
    override val horsCadre = true            // l'anneau part du widget, plus bas que la fenêtre
    private var n = 1

    override fun preparer(c: CadreDossier) {
        super.preparer(c)
        n = max(1, c.appX.size)
    }

    // l'anneau à l'instant t : son centre (x, y), son rayon, et l'angle de la 1re app
    private val an = FloatArray(4)
    private fun anneau(t: Float) {
        val cd = cadre ?: return
        val k = cd.k; val pan = cd.panneau
        val pcx = pan.centerX(); val pcy = pan.centerY() + 20f * k
        val m = freine(seg(t, 0f, 0.5f))
        an[0] = mix(cd.departX, pcx, m)
        an[1] = mix(cd.departY, pcy, m) - 60f * k * sin(PI.toFloat() * m)     // il monte en décrivant une courbe
        an[2] = mix(12f, 108f, freine(seg(t, 0.02f, 0.5f))) * k
        an[3] = -PI.toFloat() / 2 + 2 * PI.toFloat() * 1.1f * freine(seg(t, 0f, 0.9f))   // il tourne, puis ralentit
    }
    private fun depart(i: Int) = 0.46f + 0.055f * i          // l'instant où l'app i quitte l'orbite

    // la position de l'app i à l'avancement « uu » (0 = sur l'anneau, 1 = à sa place) : une ligne un peu courbée
    private val pos = FloatArray(2)
    private fun position(i: Int, uu: Float) {
        val cd = cadre ?: return
        val a = an[3] + 2 * PI.toFloat() * i / n
        val ox = an[0] + an[2] * cos(a); val oy = an[1] + an[2] * sin(a)
        val tx = cd.appX[i]; val ty = cd.appY[i]
        val nx = -(ty - oy); val ny = tx - ox; val l = max(1e-3f, hypot(nx, ny))
        val bombe = 16f * cd.k * sin(PI.toFloat() * uu)
        pos[0] = mix(ox, tx, uu) + nx / l * bombe
        pos[1] = mix(oy, ty, uu) + ny / l * bombe
    }

    override fun avantApps(c: Canvas, t: Float, fer: Float, p: Float) {
        val cd = cadre ?: return
        val k = cd.k; val te = cd.teinte
        anneau(t)
        val apparait = seg(t, 0f, 0.1f)
        val anneauA = apparait * (1 - seg(t, 0.6f, 1.0f)) * (1 - fer)
        pinceau.style = Paint.Style.FILL
        // une lueur très douce au cœur de l'anneau
        if (anneauA > 0.01f) {
            val r = an[2] * 1.4f
            pinceau.shader = RadialGradient(an[0], an[1], max(1f, r), Teinte.avecAlpha(te.lueur, 38 * anneauA), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawCircle(an[0], an[1], r, pinceau)
            pinceau.shader = null
            // l'anneau de poussière : 90 grains, plus lumineux juste DERRIÈRE chaque app encore en orbite
            for (q in 0 until 90) {
                val b = 2 * PI.toFloat() * q / 90
                var f = 0f
                for (i in 0 until n) {
                    val reste = 1 - seg(t, depart(i), depart(i) + 0.2f)       // l'app i est-elle encore en orbite ?
                    if (reste <= 0f) continue
                    val d = (((an[3] + 2 * PI.toFloat() * i / n - b) % (2 * PI.toFloat())) + 2 * PI.toFloat()) % (2 * PI.toFloat())
                    f = max(f, exp(-d / 0.55f) * reste)
                }
                pinceau.color = Teinte.avecAlpha(te.poussiere, anneauA * (30 + 190 * f))
                c.drawCircle(an[0] + an[2] * cos(b), an[1] + an[2] * sin(b), (1.1f + 1.6f * f) * k, pinceau)
            }
        }
        if (fer > 0f) return
        for (i in 0 until n) {
            val u = borne((t - depart(i)) / 0.45f)
            // la traînée de poussière (la même que l'ouverture d'origine)
            if (u > 0f && u < 1f) for (q in 0 until 10) {
                position(i, lisse(borne(u - q * 0.04f)))
                pinceau.color = Teinte.avecAlpha(te.poussiere, (200 - 18 * q) * (1 - u * 0.5f))
                c.drawCircle(pos[0], pos[1], (3.2f - q * 0.25f) * k, pinceau)
            }
            // un petit halo sous l'app tant qu'elle tourne
            if (u < 1f && apparait > 0f) {
                position(i, lisse(u))
                val ech = 0.3f + 0.7f * ressort(u, 2.2f)
                val hr = max(1f, cd.celluleH - 80f * k) * 0.55f * ech
                pinceau.shader = RadialGradient(pos[0], pos[1], hr, Teinte.avecAlpha(te.lueur, 46 * (1 - u) * apparait), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                c.drawCircle(pos[0], pos[1], hr, pinceau)
                pinceau.shader = null
            }
            // l'onde fine quand l'app arrive à sa place
            val w = borne((t - depart(i) - 0.3f) / 0.45f)
            if (w > 0f && w < 1f) {
                pinceau.style = Paint.Style.STROKE
                pinceau.strokeWidth = 1.6f * k
                pinceau.color = Teinte.avecAlpha(te.onde, 70 * (1 - w))
                c.drawCircle(cd.appX[i], cd.appY[i], (cd.celluleH - 80f * k) * (0.45f + 0.5f * freine(w)), pinceau)
                pinceau.style = Paint.Style.FILL
            }
        }
    }

    override fun arrivee(i: Int, t: Float): Arrivee {
        val cd = cadre
        anneau(t)
        val u = borne((t - depart(i)) / 0.45f)
        val uu = lisse(u)
        if (cd != null && i < cd.appX.size) {
            position(i, uu)
            arr.decalX = pos[0] - cd.appX[i]; arr.decalY = pos[1] - cd.appY[i]
        } else { arr.decalX = 0f; arr.decalY = 0f }
        arr.alpha = seg(t, 0f, 0.1f)
        arr.echelle = 0.3f + 0.7f * ressort(u, 2.2f)
        arr.penche = -14f * (1 - freine(u))
        arr.flot = uu
        arr.largeur = 1f; arr.cisaille = 0f; arr.dos = false; arr.gris = 0f
        arr.nv = borne((t - (depart(i) + 0.35f)) / 0.3f)
        return arr
    }
}

// =====================================================================================================
// C · CARTES : distribuées par le logo, retournées par une vague
// =====================================================================================================
// Pendant que le logo s'envole vers le titre, il « distribue » des cartes de verre fumé (une petite étincelle aux
// couleurs du logo au milieu) : chacune file à sa place avec la traînée de poussière d'origine et s'y pose avec un
// ressort. Puis une vague partie du logo les retourne en perspective, côté app : onde fine, reflet doux qui glisse.
class EffetCartes : EffetOuverture() {
    override val horsCadre = true            // les cartes partent du logo, au-dessus de la grille
    private var dist = FloatArray(0)         // la distance du logo (posé à côté du titre) à chaque app
    private var distMax = 1f
    private var dos: Bitmap? = null          // le dos d'une carte, dessiné une seule fois
    private val penches = floatArrayOf(-14f, 10f, -7f, 15f, -11f, 8f, -16f, 12f, -9f)
    private val rect = RectF()
    private val formeIcone = Path()

    override fun preparer(c: CadreDossier) {
        super.preparer(c)
        dist = FloatArray(c.appX.size) { hypot(c.appX[it] - c.titreX, c.appY[it] - c.titreY) }
        distMax = max(1f, dist.maxOrNull() ?: 1f)
        dos?.recycle(); dos = null           // la couleur a pu changer (roue chromatique) : on le redessinera
    }

    private fun distribue(i: Int) = 0.3f + 0.05f * i                              // l'instant où la carte part
    private fun retourne(i: Int) = 0.8f + dist[i] / distMax * 0.3f                // l'instant où la vague l'atteint

    // l'endroit où est le logo à l'instant t (le même vol que dans DossierVue : du widget jusqu'au titre)
    private val lp = FloatArray(2)
    private fun logo(t: Float) {
        val cd = cadre ?: return
        val u = seg(t, 0f, 0.55f)
        lp[0] = mix(cd.departX, cd.titreX, u)
        lp[1] = mix(cd.departY, cd.titreY, u) - 60f * cd.k * sin(PI.toFloat() * u)
    }
    // la position de la carte i à l'avancement « v » (0 = au logo, 1 = à sa place), en arc
    private val pos = FloatArray(2)
    private fun position(i: Int, v: Float) {
        val cd = cadre ?: return
        logo(distribue(i))
        pos[0] = mix(lp[0], cd.appX[i], v)
        pos[1] = mix(lp[1], cd.appY[i], v) - 26f * cd.k * sin(PI.toFloat() * v)
    }

    override fun avantApps(c: Canvas, t: Float, fer: Float, p: Float) {
        val cd = cadre ?: return
        if (fer > 0f) return
        val k = cd.k; val te = cd.teinte
        val taille = cd.celluleH - 80f * k
        pinceau.style = Paint.Style.FILL
        for (i in dist.indices) {
            val u = borne((t - distribue(i)) / 0.42f)
            // la traînée de poussière derrière la carte qui file
            if (u > 0f && u < 1f) for (q in 0 until 10) {
                position(i, lisse(borne(u - q * 0.04f)))
                pinceau.color = Teinte.avecAlpha(te.poussiere, (200 - 18 * q) * (1 - u * 0.5f))
                c.drawCircle(pos[0], pos[1], (3.2f - q * 0.25f) * k, pinceau)
            }
            if (u <= 0f) continue
            // une ombre douce sous la carte quand elle se soulève pour se retourner
            val sn = sin(PI.toFloat() * lisse(borne((t - retourne(i)) / 0.38f)))
            if (sn > 0.02f) {
                position(i, lisse(u))
                val oy = pos[1] + taille * 0.46f
                pinceau.shader = RadialGradient(pos[0], oy, taille * 0.5f, Color.argb((0.22f * sn * 255).toInt(), 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                c.drawCircle(pos[0], oy, taille * 0.5f, pinceau)
                pinceau.shader = null
            }
            // l'onde fine quand l'app apparaît
            val w = borne((t - retourne(i) - 0.22f) / 0.45f)
            if (w > 0f && w < 1f) {
                pinceau.style = Paint.Style.STROKE
                pinceau.strokeWidth = 1.6f * k
                pinceau.color = Teinte.avecAlpha(te.onde, 70 * (1 - w))
                c.drawCircle(cd.appX[i], cd.appY[i], taille * (0.45f + 0.5f * freine(w)), pinceau)
                pinceau.style = Paint.Style.FILL
            }
        }
    }

    override fun arrivee(i: Int, t: Float): Arrivee {
        val cd = cadre
        if (cd == null || i >= dist.size) { arr.alpha = 1f; arr.nv = 1f; arr.dos = false; return arr }
        val u = borne((t - distribue(i)) / 0.42f)
        position(i, lisse(u))
        val ang = PI.toFloat() * lisse(borne((t - retourne(i)) / 0.38f))     // 0 = dos, PI = côté app
        val sn = sin(ang)
        arr.decalX = pos[0] - cd.appX[i]; arr.decalY = pos[1] - cd.appY[i]
        arr.alpha = if (t < distribue(i)) 0f else borne(u * 3)
        arr.echelle = (0.25f + 0.75f * ressort(u, 2f)) * (1 + 0.06f * sn)    // elle se soulève un peu en se retournant
        arr.penche = penches[i % penches.size] * (1 - freine(u))
        arr.largeur = max(0.02f, abs(cos(ang)))                            // de face -> de profil -> de face
        arr.cisaille = 0.09f * sn * (if (ang < PI.toFloat() / 2) 1f else -1f)
        arr.dos = ang < PI.toFloat() / 2
        arr.flot = seg(t, 1.3f, 1.7f)
        arr.gris = 0f
        arr.nv = borne((t - (retourne(i) + 0.32f)) / 0.3f)
        return arr
    }

    // LE DOS : verre fumé aux couleurs du dossier, liseré clair très fin, et au centre l'étincelle avec le dégradé
    // du logo et une petite lueur. Dessiné une fois dans une image (puis juste recopié à chaque carte).
    override fun dessinerDos(c: Canvas, taille: Float, alpha: Float) {
        val cd = cadre ?: return
        val s = taille.roundToInt().coerceAtLeast(2)
        var b = dos
        if (b == null || b.width != s) {
            b?.recycle()
            b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val x = Canvas(b)
            val te = cd.teinte
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val r = s * 0.26f
            rect.set(s * 0.06f, s * 0.06f, s * 0.94f, s * 0.94f)
            p.shader = LinearGradient(0f, 0f, 0f, s.toFloat(), Teinte.avecAlpha(te.bulle, 235f), Teinte.avecAlpha(te.bas, 235f), Shader.TileMode.CLAMP)
            x.drawRoundRect(rect, r, r, p)
            p.shader = LinearGradient(0f, s * 0.06f, 0f, s * 0.5f, Color.argb(26, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            x.drawRoundRect(rect, r, r, p)                                  // un reflet doux en haut
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.2f * cd.k
            p.color = Teinte.avecAlpha(te.clair, 38f)
            rect.set(s * 0.065f, s * 0.065f, s * 0.935f, s * 0.935f)
            x.drawRoundRect(rect, r, r, p)
            p.style = Paint.Style.FILL
            x.translate(s / 2f, s / 2f)
            p.shader = RadialGradient(0f, 0f, s * 0.3f, Teinte.avecAlpha(te.lueur, 70f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            x.drawCircle(0f, 0f, s * 0.3f, p)
            val q = s * 0.15f; val e = q * 0.17f
            p.shader = LinearGradient(-q, -q, q, q, te.logo, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            val etoile = Path().apply {
                moveTo(0f, -q); quadTo(e, -e, q, 0f); quadTo(e, e, 0f, q); quadTo(-e, e, -q, 0f); quadTo(-e, -e, 0f, -q); close()
            }
            x.drawPath(etoile, p)
            dos = b
        }
        pinceau.color = Color.WHITE
        pinceau.alpha = (255 * alpha).toInt().coerceIn(0, 255)
        c.drawBitmap(b, -s / 2f, -s / 2f, pinceau)
        pinceau.alpha = 255
    }

    // LE REFLET : juste après le retournement, une bande de lumière très douce glisse sur l'icône (pas de flash)
    override fun surIcone(c: Canvas, i: Int, t: Float, x: Float, y: Float, taille: Float) {
        if (i >= dist.size) return
        val g = borne((t - retourne(i) - 0.22f) / 0.45f)
        if (g <= 0f || g >= 1f) return
        val s = taille
        c.save()
        rect.set(x - s * 0.44f, y - s * 0.44f, x + s * 0.44f, y + s * 0.44f)
        formeIcone.reset(); formeIcone.addRoundRect(rect, s * 0.26f, s * 0.26f, Path.Direction.CW)
        c.clipPath(formeIcone)
        val bx = x + mix(-s, s, freine(g))
        pinceau.shader = LinearGradient(bx - s * 0.3f, y - s * 0.5f, bx + s * 0.3f, y + s * 0.5f,
            intArrayOf(Color.TRANSPARENT, Color.argb(33, 255, 255, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        c.drawRect(x - s / 2, y - s / 2, x + s / 2, y + s / 2, pinceau)
        pinceau.shader = null
        c.restore()
    }
}
