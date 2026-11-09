package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ===================== MAIL : deux animations, tirées au hasard =====================
// Le logo : une enveloppe en TRAIT FIN, rouge à gauche -> violet -> bleu à droite,
// l'ouverture (le V) aux angles cassés, et des lumières qui parcourent la silhouette.
//  0. « Tourbillon » : des dizaines de petites enveloppes envahissent l'écran et tournent autour du logo
//     (clin d'œil minimaliste aux lettres qui envahissent la maison) ; elles sont soufflées, l'app apparaît.
//  1. « La lettre »  : le rabat se relève, une lettre opaque sort de l'enveloppe… et devient l'app.
// Mesures pensées pour un écran de 1080 de large, multipliées par u (= largeur / 1080).
val MAIL_VARIANTES = listOf("Tourbillon", "La lettre")

fun mail(numero: Int): Scene = when (numero) {
    0 -> MailTourbillon()
    1 -> MailLettre()
    else -> listOf<() -> Scene>({ MailTourbillon() }, { MailLettre() }).random()()
}

private val M_ROUGE = Color.rgb(235, 69, 77)
private val M_VIOLET = Color.rgb(158, 92, 199)
private val M_BLEU = Color.rgb(77, 133, 250)
private val M_FOND = Color.rgb(11, 11, 14)

// Mélange de deux couleurs (t = 0 : la première, t = 1 : la seconde).
private fun mMelange(a: Int, b: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    return Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
    )
}

// La couleur du trait selon la position horizontale : rouge à gauche, bleu à droite.
private fun couleurX(x: Float, w: Float): Int {
    val t = (x / w).coerceIn(0f, 1f) * 2f
    return if (t < 1f) mMelange(M_ROUGE, M_VIOLET, t) else mMelange(M_VIOLET, M_BLEU, t - 1f)
}

// Un dégradé horizontal rouge -> violet -> bleu, avec une opacité a.
private fun degradeMail(x0: Float, x1: Float, a: Float): LinearGradient =
    LinearGradient(x0, 0f, x1, 0f, intArrayOf(avecAlpha(M_ROUGE, a), avecAlpha(M_VIOLET, a), avecAlpha(M_BLEU, a)),
        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)

// Le V de l'ouverture, aux angles CASSÉS : la pointe est arrondie (rayon r).
// « continuer » = on prolonge le tracé en cours au lieu d'en commencer un nouveau.
private fun vArrondi(p: Path, ax: Float, ay: Float, bx: Float, by: Float, cx: Float, vy: Float, rayon: Float, continuer: Boolean = false) {
    val r = min(rayon, abs(vy - ay) * 0.9f + 0.01f)
    fun vers(px: Float, py: Float, qx: Float, qy: Float, d: Float): FloatArray {
        val l = max(1e-4f, hypot(qx - px, qy - py)); return floatArrayOf(px + (qx - px) / l * d, py + (qy - py) / l * d)
    }
    val p1 = vers(cx, vy, ax, ay, r); val p2 = vers(cx, vy, bx, by, r)
    if (continuer) p.lineTo(ax, ay) else p.moveTo(ax, ay)
    p.lineTo(p1[0], p1[1])
    p.cubicTo(cx - (cx - p1[0]) * 0.1f, vy, cx + (p2[0] - cx) * 0.1f, vy, p2[0], p2[1])
    p.lineTo(bx, by)
}

// Le contour de l'enveloppe, échantillonné en points réguliers (pour faire courir les lumières dessus).
private class Contour(rect: RectF, rayon: Float, pas: Float) {
    val xs: FloatArray; val ys: FloatArray; val n: Int
    init {
        val p = Path(); p.addRoundRect(rect, rayon, rayon, Path.Direction.CW)
        val m = PathMeasure(p, true)
        n = max(8, (m.length / pas).toInt())
        xs = FloatArray(n); ys = FloatArray(n)
        val pos = FloatArray(2)
        for (i in 0 until n) { m.getPosTan(m.length * i / n, pos, null); xs[i] = pos[0]; ys[i] = pos[1] }
    }
}

// Une lumière qui court sur le contour : tête blanche + traînée qui s'efface, collée au trait.
private fun lumiereMail(c: Canvas, o: Outils, ct: Contour, pos: Float, longueur: Float, force: Float, trait: Float, w: Float) {
    if (force <= 0.01f) return
    val n = ct.n
    val tete = (pos * n).toInt()
    val nb = (longueur * n).toInt()
    val lueur = o.lumiere(); lueur.style = Paint.Style.STROKE; lueur.strokeCap = Paint.Cap.ROUND
    val coeur = o.lumiere(); coeur.style = Paint.Style.STROKE; coeur.strokeCap = Paint.Cap.ROUND
    for (j in nb downTo 1) {
        val i0 = Math.floorMod(tete - j, n); val i1 = Math.floorMod(tete - j + 1, n)
        val f = 1f - j.toFloat() / nb
        val col = couleurX(ct.xs[i1], w)
        lueur.color = avecAlpha(col, 0.18f * f * f * force); lueur.strokeWidth = trait * 1.8f * f
        c.drawLine(ct.xs[i0], ct.ys[i0], ct.xs[i1], ct.ys[i1], lueur)
        coeur.color = avecAlpha(mMelange(col, Color.WHITE, 0.5f * f), 0.9f * f * f * force); coeur.strokeWidth = trait * (0.7f + 0.3f * f)
        c.drawLine(ct.xs[i0], ct.ys[i0], ct.xs[i1], ct.ys[i1], coeur)
    }
}

// =====================================================================================
// 0. MAIL « TOURBILLON »
// =====================================================================================
class MailTourbillon : Scene() {
    // Les 90 petites enveloppes : moment d'arrivée, angle, rayon d'orbite, vitesse, taille, retournement…
    private class Lettre(val t0: Float, val a0: Float, val rfin: Float, val vit: Float, val taille: Float,
                         val flip: Float, val vflip: Float, val ph: Float, val dy: Float)
    private val lettres: List<Lettre> = run {
        val r = Random(4)
        List(90) { i ->
            Lettre(0.10f + 0.95f * (i / 90f).toDouble().let { Math.pow(it, 0.8).toFloat() } + (r.nextFloat() - 0.5f) * 0.08f,
                r.nextFloat() * 6.283f, 330f + r.nextFloat() * 370f, 1.6f + r.nextFloat(), 46f + r.nextFloat() * 50f,
                r.nextFloat() * 6f, 3f + r.nextFloat() * 4f, r.nextFloat() * 6f, r.nextFloat() * 2f - 1f)
        }
    }
    private var contour: Contour? = null
    private var contourLargeur = 0f
    private val trace = Path()
    private val boite = RectF()
    private val pl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val sec = i.secondes; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val u = w / 1080f
        val app = ralentit(i.intro)
        val cx = w / 2f; val cy = h * 0.47f
        c.drawColor(M_FOND)
        val fond = o.pinceau()
        fond.shader = RadialGradient(cx, cy, 700f * u, avecAlpha(M_VIOLET, 0.07f * app), avecAlpha(M_VIOLET, 0f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, 700f * u, fond)

        // où est chaque petite enveloppe à cet instant ? (x, y, angle, profondeur)
        val pos = ArrayList<FloatArray>()
        for ((k, l) in lettres.withIndex()) {
            val age = sec - l.t0
            if (age < 0f) continue
            val arrivee = ralentit(age / 0.55f)
            var rr = (l.rfin + (1500f - l.rfin) * (1f - arrivee)) * u
            rr *= 1f + 2.5f * accelere(rev)                                   // à l'ouverture : soufflées vers l'extérieur
            val a = l.a0 + l.vit * age * sqrt(900f * u / rr)                  // plus près = plus vite (tourbillon)
            val x = cx + cos(a) * rr
            val y = cy + sin(a) * rr * 0.62f + l.dy * 120f * u + sin(sec * 5f + l.ph) * 14f * u
            pos.add(floatArrayOf(x, y, a, 0.5f + 0.5f * sin(a), k.toFloat()))
        }
        pos.sortBy { it[3] }
        // d'abord celles de derrière (plus petites, plus pâles)
        for (p in pos) if (p[3] < 0.5f) petite(c, p, sec, 0.7f + 0.3f * p[3], (0.35f + 0.4f * p[3]) * app * (1f - rev), 3f * u, w)
        // le logo au centre (il grandit à l'ouverture)
        val k = 1f + 5f * accelere(rev)
        logo(c, o, sec, app * (1f - rev * 0.6f), k, cx, cy, w, u)
        for (p in pos) if (p[3] >= 0.5f) petite(c, p, sec, 0.85f + 0.45f * p[3], (0.55f + 0.4f * p[3]) * app * (1f - rev), 3.5f * u, w)

        // l'ouverture : le cadre du logo s'agrandit et l'app apparaît dedans
        if (rev > 0f) {
            val e = ralentit(rev)
            val dl = 200f * u + (w / 2f + 10f * u - 200f * u) * e
            val dh = 137f * u + (h / 2f + 10f * u - 137f * u) * e
            val cyy = cy + (h / 2f - cy) * e
            boite.set(cx - dl, cyy - dh, cx + dl, cyy + dh)
            val rad = (46f + 30f * e) * u
            c.drawRoundRect(boite, rad, rad, o.gomme())
        }
        if (i.intro < 1f) c.drawColor(avecAlpha(Color.BLACK, 1f - app))
    }

    // Une petite enveloppe en trait fin, qui tourne et se retourne comme du papier.
    private fun petite(c: Canvas, p: FloatArray, sec: Float, s: Float, alpha: Float, epaisseur: Float, w: Float) {
        if (alpha <= 0.01f) return
        val l = lettres[p[4].toInt()]
        val L = l.taille * s * (w / 1080f); val hh = L * 0.69f
        c.save()
        c.translate(p[0], p[1])
        c.rotate((sin(sec * 3f + l.ph) * 0.5f + p[2] * 0.2f) * 57.2958f)
        c.scale(max(0.12f, abs(cos(l.flip + sec * l.vflip))), 1f)
        trace.reset()
        boite.set(-L / 2f, -hh / 2f, L / 2f, hh / 2f)
        trace.addRoundRect(boite, hh * 0.18f, hh * 0.18f, Path.Direction.CW)
        vArrondi(trace, -L / 2f + L * 0.04f, -hh / 2f + hh * 0.1f, L / 2f - L * 0.04f, -hh / 2f + hh * 0.1f, 0f, hh * 0.1f, hh * 0.18f)
        pl.shader = null
        pl.color = avecAlpha(couleurX(p[0], w), alpha); pl.strokeWidth = epaisseur
        c.drawPath(trace, pl)
        c.restore()
    }

    // Le grand logo : contour + V aux angles cassés en dégradé, et 3 lumières qui le parcourent.
    private fun logo(c: Canvas, o: Outils, sec: Float, a: Float, k: Float, cx: Float, cy: Float, w: Float, u: Float) {
        val ll = 400f * u; val lh = 274f * u; val lr = 46f * u
        val x0 = cx - ll / 2f; val y0 = cy - lh / 2f
        if (contour == null || contourLargeur != w) {
            contour = Contour(RectF(x0, y0, x0 + ll, y0 + lh), lr, 3f * u); contourLargeur = w
        }
        c.save()
        c.scale(k, k, cx, cy)
        pl.color = Color.WHITE; pl.shader = degradeMail(x0, x0 + ll, 0.6f * a); pl.strokeWidth = 5f * u
        boite.set(x0, y0, x0 + ll, y0 + lh)
        c.drawRoundRect(boite, lr, lr, pl)
        trace.reset(); vArrondi(trace, x0 + 16f * u, y0 + 28f * u, x0 + ll - 16f * u, y0 + 28f * u, cx, cy + 24f * u, 40f * u)
        c.drawPath(trace, pl)
        pl.shader = null
        val ph = modulo(sec, 2f) / 2f                                          // un tour toutes les 2 s
        val ct = contour!!
        lumiereMail(c, o, ct, ph, 0.22f, a, 5f * u, w)
        lumiereMail(c, o, ct, modulo(ph + 0.5f, 1f), 0.22f, a, 5f * u, w)
        c.restore()
    }
}

// =====================================================================================
// 1. MAIL « LA LETTRE »
// =====================================================================================
class MailLettre : Scene() {
    private var contour: Contour? = null
    private var contourLargeur = 0f
    private val trace = Path()
    private val poche = Path()
    private val boite = RectF()
    private val pl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val plein = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val sec = i.secondes; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val u = w / 1080f
        val app = ralentit(i.intro)
        val cx = w / 2f; val cy = h * 0.54f
        val ll = 470f * u; val lh = 322f * u; val lr = 54f * u; val trait = 5f * u
        val lx0 = cx - ll / 2f; val ly0 = cy - lh / 2f; val lx1 = lx0 + ll; val ly1 = ly0 + lh
        if (contour == null || contourLargeur != w) { contour = Contour(RectF(lx0, ly0, lx1, ly1), lr, 3f * u); contourLargeur = w }

        c.drawColor(M_FOND)
        plein.shader = RadialGradient(cx, cy, 650f * u, avecAlpha(M_VIOLET, 0.08f * app), avecAlpha(M_VIOLET, 0f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, 650f * u, plein); plein.shader = null

        val ouv = doux(borne((sec - 0.30f) / 0.45f))            // le rabat se relève (0 fermé, 1 ouvert)
        val sort = doux(borne((sec - 0.70f) / 0.55f))           // la lettre sort
        val aEnv = app * (1f - ralentit(rev * 1.6f))            // l'enveloppe s'efface à l'ouverture finale…
        val descente = 60f * u * ralentit(rev)                  // …en glissant un peu vers le bas

        val vyFerme = ly0 + lh * 0.50f; val vyOuvert = ly0 - lh * 0.42f
        val vy = mix(vyFerme, vyOuvert, ouv)
        val vax = lx0 + 18f * u; val vbx = lx1 - 18f * u; val vay = ly0 + 30f * u
        // la poche avant : TOUT ce qui est sous le pli (même tracé que le rabat fermé)
        poche.reset()
        poche.moveTo(lx0 - 4f * u, ly1 + 4f * u); poche.lineTo(lx0 - 4f * u, vay)
        vArrondi(poche, vax, vay, vbx, vay, cx, vyFerme, 46f * u, continuer = true)
        poche.lineTo(lx1 + 4f * u, vay); poche.lineTo(lx1 + 4f * u, ly1 + 4f * u); poche.close()

        c.save(); c.translate(0f, descente)
        pl.strokeWidth = trait
        // 1) le rabat RELEVÉ passe derrière la lettre
        if (ouv > 0.5f) {
            trace.reset(); vArrondi(trace, lx0 + lr * 0.7f, ly0, lx1 - lr * 0.7f, ly0, cx, vy, 46f * u)
            pl.color = Color.WHITE; pl.shader = degradeMail(lx0, lx1, 0.6f * aEnv); c.drawPath(trace, pl)
        }
        // 2) la poche avant, remplie de la couleur du fond, et son pli (caché sous le rabat au début)
        c.save(); boite.set(lx0, ly0, lx1, ly1)
        trace.reset(); trace.addRoundRect(boite, lr, lr, Path.Direction.CW); c.clipPath(trace)
        plein.color = M_FOND; c.drawPath(poche, plein); c.restore()
        trace.reset(); vArrondi(trace, vax, vay, vbx, vay, cx, vyFerme, 46f * u)
        pl.strokeWidth = 3f * u; pl.color = Color.WHITE; pl.shader = degradeMail(lx0, lx1, 0.30f * aEnv * borne(ouv * 2.5f)); c.drawPath(trace, pl)
        // 3) le contour + ses lumières
        pl.strokeWidth = trait; pl.color = Color.WHITE; pl.shader = degradeMail(lx0, lx1, 0.6f * aEnv)
        c.drawRoundRect(boite, lr, lr, pl)
        val ph = modulo(sec, 2f) / 2f
        lumiereMail(c, o, contour!!, ph, 0.22f, aEnv, trait, w)
        lumiereMail(c, o, contour!!, modulo(ph + 0.5f, 1f), 0.22f, aEnv, trait, w)
        // 4) le rabat FERMÉ (ou qui commence à se relever) passe devant
        if (ouv <= 0.5f) {
            trace.reset(); vArrondi(trace, vax, vay, vbx, vay, cx, vy, 46f * u)
            pl.color = Color.WHITE; pl.shader = degradeMail(lx0, lx1, 0.6f * aEnv); c.drawPath(trace, pl)
        }
        pl.shader = null
        c.restore()

        // 5) la lettre, OPAQUE, devant l'enveloppe ; seule la poche cache son bas
        val lw = ll * 0.84f; val lhh = lh * 0.92f
        val ltop = ly0 + lh * 0.10f - sort * lh * 0.78f
        val e = ralentit(rev)
        val fx0 = mix(cx - lw / 2f, -6f * u, e); val fy0 = mix(ltop, -6f * u, e)
        val fw = mix(lw, w + 12f * u, e); val fh = mix(lhh, h + 12f * u, e)
        val rad = mix(26f, 60f, e) * u
        if (sort > 0f || rev > 0f) {
            c.save()
            if (rev == 0f) c.clipOutPath(poche)
            lettre(c, fx0, fy0, fw, fh, rad, sort * (1f - e), u)
            c.restore()
        }
        // 6) l'app apparaît DANS la lettre agrandie (fondu rapide)
        if (rev > 0f) {
            val trou = Paint(Paint.ANTI_ALIAS_FLAG)
            trou.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            trou.color = avecAlpha(Color.BLACK, borne(rev * 1.4f))
            boite.set(fx0, fy0, fx0 + fw, fy0 + fh)
            c.drawRoundRect(boite, rad, rad, trou)
        }
        if (i.intro < 1f) c.drawColor(avecAlpha(Color.BLACK, 1f - app))
    }

    // La lettre : carte sombre opaque, liseré en dégradé, lignes d'écriture, reflet qui glisse.
    private fun lettre(c: Canvas, x0: Float, y0: Float, w: Float, h: Float, rad: Float, reflet: Float, u: Float) {
        boite.set(x0, y0, x0 + w, y0 + h)
        plein.shader = LinearGradient(0f, y0, 0f, y0 + h, Color.rgb(33, 31, 41), Color.rgb(22, 22, 26), Shader.TileMode.CLAMP)
        c.drawRoundRect(boite, rad, rad, plein); plein.shader = null
        pl.strokeWidth = 3f * u; pl.color = Color.WHITE; pl.shader = degradeMail(x0, x0 + w, 0.55f); c.drawRoundRect(boite, rad, rad, pl); pl.shader = null
        pl.strokeWidth = 7f * u; pl.color = avecAlpha(Color.WHITE, 0.16f)
        for (k in 0 until 4) {
            val yy = y0 + h * (0.22f + 0.15f * k)
            if (yy > y0 + h - 14f * u) break
            c.drawLine(x0 + w * 0.12f, yy, x0 + w * (0.88f - 0.18f * (k % 2)), yy, pl)
        }
        if (reflet > 0.01f) {
            c.save(); trace.reset(); trace.addRoundRect(boite, rad, rad, Path.Direction.CW); c.clipPath(trace)
            plein.shader = LinearGradient(x0, y0, x0 + w, y0 + h,
                intArrayOf(Color.TRANSPARENT, avecAlpha(Color.WHITE, 0.07f), Color.TRANSPARENT),
                floatArrayOf(max(0f, reflet - 0.15f), reflet, min(1f, reflet + 0.15f) + 0.0001f), Shader.TileMode.CLAMP)
            c.drawRect(boite, plein); plein.shader = null
            c.restore()
        }
    }
}
