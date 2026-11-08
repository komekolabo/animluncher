package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.exp
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// GEMINI, LOT 2 : deux vraies puces. L'une pour l'IA (TPU), l'autre quantique.
// Pour les distinguer d'un coup d'œil : la puce d'IA est sur un circuit vert, avec des mémoires
// et une grille carrée ; la puce quantique est dorée, glacée, avec des qubits en croix en losange.

// Le dégradé Gemini (bleu → violet → rose), t entre 0 et 1.
private val GEM = arrayOf(intArrayOf(66, 133, 244), intArrayOf(120, 110, 230), intArrayOf(170, 100, 210), intArrayOf(215, 100, 140))
fun couleurGemini(t: Float): Int {
    val u = borne(t) * (GEM.size - 1)
    val k = minOf(u.toInt(), GEM.size - 2); val f = u - k
    val a = GEM[k]; val b = GEM[k + 1]
    return rgb(mix(a[0].toFloat(), b[0].toFloat(), f).toInt(), mix(a[1].toFloat(), b[1].toFloat(), f).toInt(),
        mix(a[2].toFloat(), b[2].toFloat(), f).toInt())
}

// ===================== GEMINI : « TPU » =====================
// Un vrai boîtier de puce d'IA, vu légèrement en perspective :
// - un SUBSTRAT vert très sombre, avec de petits condensateurs et des pistes fines ;
// - au centre, la PUCE en silicium (reflets irisés qui glissent doucement) ;
// - de chaque côté, 4 piles de MÉMOIRE (HBM) qui envoient des données à la puce ;
// - dans la puce, la grille de calcul « systolique » : une vague de calcul la traverse en diagonale.
// Révélation : un balayage diagonal, dans le sens de la vague.
class Tpu : Scene() {
    private val n = 8
    private val poussiere = nuage(41, 60)
    private val r = Random(12)
    // condensateurs : côté (0 à 3), position (0 à 1), rangée (0 ou 1)
    private val condos = ArrayList<FloatArray>().also { l ->
        for (cote in 0..3) for (i in 0 until 11) for (rang in 0..1)
            if (r.nextFloat() < 0.8f) l.add(floatArrayOf(cote.toFloat(), (i + 0.5f) / 11f + (r.nextFloat() - 0.5f) * 0.03f, rang.toFloat()))
    }
    // pistes fines du substrat (en unités locales : 1 = la moitié de la plaque)
    private val pistes = List(34) {
        val y = r.nextFloat() * 1.8f - 0.9f; val x0 = if (r.nextBoolean()) -0.95f else 0.95f
        floatArrayOf(x0, y, x0 * (0.62f + 0.18f * r.nextFloat()), y + (r.nextFloat() - 0.5f) * 0.16f)
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val sec = i.secondes; val w = c.width.toFloat(); val h = c.height.toFloat()
        val k = w / 540f
        val app = ralentit(i.intro); val a = 1f - i.revelation
        val cx = w / 2f; val cy = h * 0.47f
        val fond = o.pinceau()
        fond.shader = LinearGradient(0f, 0f, 0f, h, rgb(4, 6, 12), rgb(10, 10, 24), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fond)
        o.poussieres(c, poussiere, sec, w, h, rgb(160, 160, 230), 0.30f * app * a, 0f, -0.015f)
        o.halo(c, cx, cy, w * 0.75f, 0.10f * app * a, rgb(90, 90, 210))

        val zoom = (0.92f + 0.08f * app) * (1f + 0.14f * doux(i.revelation))
        val u = 0.48f * w * zoom                     // 1 unité locale = la moitié de la plaque
        c.save()
        // La perspective : on transforme le carré de la plaque en trapèze (le haut s'éloigne),
        // avec un léger balancement.
        val bal = sin(sec * 0.5f) * 0.035f
        val haut = 0.84f + bal; val bas = 1f - bal * 0.5f
        val hy = 0.80f * (0.96f + 0.04f * app)
        val m = Matrix()
        m.setPolyToPoly(floatArrayOf(-u, -u, u, -u, u, u, -u, u), 0,
            floatArrayOf(cx - u * haut, cy - u * hy, cx + u * haut, cy - u * hy, cx + u * bas, cy + u * hy, cx - u * bas, cy + u * hy), 0, 4)
        c.concat(m)

        // 1) l'épaisseur de la plaque (bord avant plus sombre), puis la plaque verte
        val plaque = RectF(-u, -u, u, u)
        c.save(); c.translate(0f, 0.045f * u)
        c.drawRoundRect(plaque, 0.06f * u, 0.06f * u, o.pinceau(avecAlpha(rgb(6, 16, 11), app * a)))
        c.restore()
        val vert = o.pinceau()
        vert.shader = LinearGradient(-u, -u, u, u, rgb(18, 44, 32), rgb(10, 28, 22), Shader.TileMode.CLAMP)
        vert.alpha = (255 * app * a).toInt()
        c.drawRoundRect(plaque, 0.06f * u, 0.06f * u, vert)
        c.drawRoundRect(plaque, 0.06f * u, 0.06f * u, o.pinceau(avecAlpha(rgb(60, 110, 80), 0.35f * app * a), 0.006f * u))
        val pPiste = o.pinceau(avecAlpha(rgb(55, 105, 75), 0.30f * app * a), 0.004f * u)
        for (p in pistes) {
            val ch = Path(); ch.moveTo(p[0] * u, p[1] * u); ch.lineTo((p[0] + p[2]) / 2f * u, p[1] * u); ch.lineTo(p[2] * u, p[3] * u)
            c.drawPath(ch, pPiste)
        }
        // condensateurs (petits pavés beiges aux bouts argentés)
        val corpsP = o.pinceau(avecAlpha(rgb(120, 98, 70), 0.85f * app * a))
        val boutP = o.pinceau(avecAlpha(rgb(185, 185, 175), 0.85f * app * a))
        for (cd in condos) {
            val cote = cd[0].toInt()
            val t = (cd[1] * 2f - 1f) * 0.86f * u; val d = (0.90f - cd[2] * 0.065f) * u
            var lx = 0.035f * u; var ly = 0.018f * u
            if (cote == 1 || cote == 3) { val tmp = lx; lx = ly; ly = tmp }
            val x = when (cote) { 0 -> t; 1 -> d; 2 -> t; else -> -d }
            val y = when (cote) { 0 -> -d; 1 -> t; 2 -> d; else -> t }
            c.drawRect(x - lx, y - ly, x + lx, y + ly, corpsP)
            if (cote == 0 || cote == 2) {
                c.drawRect(x - lx, y - ly, x - lx * 0.55f, y + ly, boutP); c.drawRect(x + lx * 0.55f, y - ly, x + lx, y + ly, boutP)
            } else {
                c.drawRect(x - lx, y - ly, x + lx, y - ly * 0.55f, boutP); c.drawRect(x - lx, y + ly * 0.55f, x + lx, y + ly, boutP)
            }
        }

        // 2) l'interposeur (la plaque grise sous la puce et les mémoires)
        val inter = RectF(-0.70f * u, -0.46f * u, 0.70f * u, 0.46f * u)
        c.drawRoundRect(inter, 0.02f * u, 0.02f * u, o.pinceau(avecAlpha(rgb(34, 36, 44), app * a)))
        c.drawRoundRect(inter, 0.02f * u, 0.02f * u, o.pinceau(avecAlpha(rgb(80, 84, 100), 0.4f * app * a), 0.004f * u))

        // 3) les 4 piles de mémoire HBM (2 à gauche, 2 à droite), avec leurs couches visibles
        val dPuce = 0.62f * u
        val piles = ArrayList<FloatArray>()
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
            val x = sx * 0.545f * u; val y = sy * 0.205f * u
            piles.add(floatArrayOf(x, y, sx.toFloat()))
            val pr = RectF(x - 0.11f * u, y - 0.17f * u, x + 0.11f * u, y + 0.17f * u)
            for (couche in 4 downTo 1) {           // les tranches empilées (vues de biais, en bas)
                c.save(); c.translate(0f, couche * 0.008f * u)
                c.drawRect(pr, o.pinceau(avecAlpha(rgb(22 + couche * 4, 23 + couche * 4, 28 + couche * 4), app * a)))
                c.restore()
            }
            val mem = o.pinceau()
            mem.shader = LinearGradient(pr.left, pr.top, pr.right, pr.bottom, rgb(52, 54, 62), rgb(32, 33, 40), Shader.TileMode.CLAMP)
            mem.alpha = (255 * app * a).toInt()
            c.drawRect(pr, mem)
            val pl = o.pinceau(avecAlpha(rgb(80, 82, 95), 0.25f * app * a), 0.003f * u)
            for (ligne in 1..5) {
                val yy = pr.top + pr.height() * ligne / 6f
                c.drawLine(pr.left + 0.015f * u, yy, pr.right - 0.015f * u, yy, pl)
            }
        }

        // 4) le bus de données : de chaque mémoire vers la puce, des impulsions qui ENTRENT
        val pBus = o.pinceau(avecAlpha(rgb(90, 95, 140), 0.30f * app * a), 0.004f * u)
        for ((idx, pi) in piles.withIndex()) {
            val x = pi[0]; val y = pi[1]; val sx = pi[2]
            for (b in 0 until 6) {
                val yy = y + (b - 2.5f) * 0.045f * u
                val xa = x - sx * 0.11f * u; val xb = sx * dPuce / 2f
                c.drawLine(xa, yy, xb, yy, pBus)
                val f = modulo(sec * 0.9f + b * 0.17f + idx * 0.29f, 1f)
                val px = mix(xa, xb, f)
                val vis = sin(Math.PI.toFloat() * f)
                o.halo(c, px, yy, 0.025f * u, 0.40f * app * a * vis, couleurGemini((idx * 6 + b) / 23f))
                c.drawCircle(px, yy, 0.006f * u, o.pinceau(avecAlpha(rgb(230, 232, 255), 0.8f * app * a * vis)))
            }
        }

        // 5) la puce en silicium : fond irisé (reflets qui tournent lentement)
        val puce = RectF(-dPuce / 2f, -dPuce / 2f, dPuce / 2f, dPuce / 2f)
        c.save(); c.translate(0f, 0.012f * u)
        c.drawRect(puce, o.pinceau(avecAlpha(rgb(8, 8, 14), app * a))); c.restore()
        val iris = o.pinceau()
        val sweep = SweepGradient(0f, 0f, intArrayOf(rgb(30, 26, 56), rgb(20, 40, 60), rgb(40, 30, 62), rgb(22, 46, 52), rgb(30, 26, 56)), null)
        sweep.setLocalMatrix(Matrix().apply { setRotate(sec * 12f) })
        iris.shader = sweep
        iris.alpha = (255 * app * a).toInt()
        c.drawRect(puce, iris)
        // bandes de mémoire interne (SRAM) le long des bords de la puce
        val pSram = o.pinceau(avecAlpha(rgb(70, 72, 110), 0.35f * app * a))
        for (q in 0 until 16) {
            val t = -dPuce / 2f + dPuce * (q + 0.5f) / 16f
            c.drawRect(t - 0.012f * u, -dPuce / 2f + 0.012f * u, t + 0.012f * u, -dPuce / 2f + 0.05f * u, pSram)
            c.drawRect(t - 0.012f * u, dPuce / 2f - 0.05f * u, t + 0.012f * u, dPuce / 2f - 0.012f * u, pSram)
        }
        // la grille systolique : une vague de calcul la traverse en diagonale
        val g = dPuce * 0.74f
        val g0 = -g / 2f; val case = g / n; val marge = case * 0.12f
        val vague = modulo(sec * 6f, 2f * n + 6f) - 3f
        val pFond = o.pinceau(); val pBloc = o.pinceau(); val pLum = o.lumiere()
        for (ii in 0 until n) for (jj in 0 until n) {
            val s = (ii + jj).toFloat()
            val apparait = borne(app * (2 * n) * 1.1f - s * 0.9f)
            if (apparait <= 0f) continue
            val impulsion = maxOf(exp(-((s - vague) * (s - vague)) / 1.6f), exp(-((s - vague + n + 3) * (s - vague + n + 3)) / 1.6f))
            val x = g0 + jj * case; val y = g0 + ii * case
            val rc = RectF(x + marge, y + marge, x + case - marge, y + case - marge)
            val coul = couleurGemini(s / (2 * n - 2))
            pFond.color = avecAlpha(rgb(24, 26, 46), 0.9f * apparait * a)
            c.drawRect(rc, pFond)
            // détail : chaque unité a 4 petits blocs internes (les multiplieurs)
            val q = rc.width() / 2f
            pBloc.color = avecAlpha(rgb(44, 46, 76), 0.6f * apparait * a)
            for (ox in floatArrayOf(0f, q)) for (oy in floatArrayOf(0f, q))
                c.drawRect(rc.left + ox + q * 0.15f, rc.top + oy + q * 0.15f, rc.left + ox + q * 0.85f, rc.top + oy + q * 0.85f, pBloc)
            pLum.color = avecAlpha(coul, (0.08f + 0.55f * impulsion) * apparait * a)
            c.drawRect(rc, pLum)
            if (impulsion > 0.05f) o.halo(c, rc.centerX(), rc.centerY(), case * 0.75f, 0.26f * impulsion * apparait * a, coul)
        }
        // un reflet doux qui glisse sur le silicium (jamais éblouissant)
        val pos = modulo(sec * 0.35f, 1.6f) - 0.3f
        val xr = mix(-dPuce, dPuce, pos)
        val reflet = o.lumiere()
        reflet.shader = LinearGradient(xr - 0.2f * u, -dPuce / 2f, xr + 0.2f * u, dPuce / 2f,
            intArrayOf(avecAlpha(rgb(200, 210, 255), 0f), avecAlpha(rgb(200, 210, 255), 0.07f * app * a), avecAlpha(rgb(200, 210, 255), 0f)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(puce, reflet)
        c.drawRect(puce, o.pinceau(avecAlpha(rgb(110, 115, 170), 0.45f * app * a), 0.004f * u))
        c.restore()

        o.vignette(c, w, h, 0.6f)
        o.revelationBalayageDouce(c, i.revelation, 45f, couleurGemini(0.4f))
    }
}

// ===================== GEMINI : « Quantique » =====================
// Inspirée des puces quantiques récentes (sans aucun nom ni logo).
// En haut : le bas du « lustre » doré du cryostat (refroidi presque au zéro absolu) et les câbles
// coaxiaux qui descendent jusqu'à la puce. En bas : la puce sur son support doré, avec un réseau de
// QUBITS en losange (chaque qubit a la forme d'une petite croix, comme les vrais « transmons »).
// Les qubits scintillent (superposition) et des liens d'INTRICATION se propagent depuis le centre.
// Révélation : des bandes horizontales qui s'ouvrent en vague (comme une figure d'interférence).
class Quantique : Scene() {
    private val glace = rgb(200, 228, 255)
    private val bleu = rgb(90, 150, 255)
    private val blanc = rgb(235, 245, 255)
    private val poussiere = nuage(57, 55)
    private val nq = 7                             // réseau de 7 x 7 qubits, tourné à 45°
    private val r = Random(5)
    private val phases = Array(nq * nq) { floatArrayOf(r.nextFloat() * 6.28f, 1.6f + r.nextFloat() * 1.4f) }
    // câbles coaxiaux : position x (0 à 1), courbure, cuivre (1) ou argent (0)
    private val cables = List(14) { m ->
        floatArrayOf(0.18f + 0.64f * (m + 0.5f) / 14f + (r.nextFloat() - 0.5f) * 0.02f, r.nextFloat() * 2f - 1f, if (r.nextFloat() < 0.6f) 1f else 0f)
    }
    private val brume = List(7) { floatArrayOf(r.nextFloat(), 0.25f + 0.5f * r.nextFloat(), 0.6f + 0.8f * r.nextFloat(), r.nextFloat() * 6.28f) }

    // Une plaque dorée (dégradé : ombre, reflet, ombre).
    private fun plaqueOr(c: Canvas, o: Outils, rect: RectF, al: Float, rayon: Float) {
        val p = o.pinceau()
        p.shader = LinearGradient(rect.left, 0f, rect.right, 0f,
            intArrayOf(rgb(92, 64, 26), rgb(196, 152, 80), rgb(150, 110, 48), rgb(205, 165, 92), rgb(86, 60, 24)),
            floatArrayOf(0f, 0.3f, 0.5f, 0.72f, 1f), Shader.TileMode.CLAMP)
        p.alpha = (255 * al).toInt()
        c.drawRoundRect(rect, rayon, rayon, p)
        c.drawRoundRect(rect, rayon, rayon, o.pinceau(avecAlpha(rgb(240, 205, 140), 0.25f * al), 1f))
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val sec = i.secondes; val w = c.width.toFloat(); val h = c.height.toFloat()
        val k = w / 540f
        val app = ralentit(i.intro); val a = 1f - i.revelation
        val al = app * a
        val fond = o.pinceau()
        fond.shader = LinearGradient(0f, 0f, 0f, h, rgb(3, 5, 12), rgb(6, 11, 24), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fond)
        o.poussieres(c, poussiere, sec, w, h, glace, 0.25f * al, 0f, 0.012f)    // du givre qui tombe doucement

        val cx = w / 2f; val cy = h * 0.64f              // le centre de la puce
        val zoom = (0.94f + 0.06f * app) * (1f + 0.10f * doux(i.revelation))
        c.save(); c.scale(zoom, zoom, cx, cy)

        // 1) le bas du lustre : deux étages dorés, reliés par des tiges
        val e1 = RectF(w * 0.08f, h * 0.10f, w * 0.92f, h * 0.10f + 22f * k)
        val e2 = RectF(w * 0.16f, h * 0.30f, w * 0.84f, h * 0.30f + 18f * k)
        for (x in floatArrayOf(0.22f, 0.40f, 0.60f, 0.78f)) {
            val tige = o.pinceau()
            tige.shader = LinearGradient(w * x - 5f * k, 0f, w * x + 5f * k, 0f,
                intArrayOf(rgb(90, 64, 28), rgb(200, 160, 90), rgb(90, 64, 28)), null, Shader.TileMode.CLAMP)
            tige.alpha = (230 * al).toInt()
            c.drawRect(w * x - 5f * k, -10f, w * x + 5f * k, e2.top, tige)
        }

        // 2) les câbles coaxiaux qui descendent jusqu'au support de la puce
        val support = RectF(cx - 0.36f * w, cy - 0.36f * w, cx + 0.36f * w, cy + 0.36f * w)
        val manchon = o.pinceau(avecAlpha(rgb(200, 160, 90), 0.8f * al))
        for (cb in cables) {
            val x0 = w * cb[0]; val courbe = cb[1]
            val xb = mix(support.left + 30f * k, support.right - 30f * k, (cb[0] - 0.18f) / 0.64f)
            val ch = Path(); ch.moveTo(x0, -10f)
            ch.lineTo(x0, e2.bottom + 20f * k)
            ch.cubicTo(x0, e2.bottom + 120f * k + courbe * 20f * k, xb + courbe * 12f * k, support.top - 110f * k, xb, support.top - 4f * k)
            val coul = if (cb[2] > 0.5f) rgb(184, 118, 72) else rgb(170, 178, 190)
            c.drawPath(ch, o.pinceau(avecAlpha(rgb(10, 10, 14), 0.8f * al), 4.6f * k))
            c.drawPath(ch, o.pinceau(avecAlpha(coul, 0.55f * al), 2.4f * k))
            c.drawPath(ch, o.pinceau(avecAlpha(rgb(255, 240, 220), 0.12f * al), 0.8f * k))
            // les connecteurs (petits manchons dorés) sous l'étage et au-dessus du support
            val yc = e2.bottom + 8f * k
            c.drawRect(x0 - 3.6f * k, yc - 5f * k, x0 + 3.6f * k, yc + 7f * k, manchon)
            c.drawRect(xb - 3.6f * k, support.top - 14f * k, xb + 3.6f * k, support.top, manchon)
        }
        plaqueOr(c, o, e1, al, 4f * k)
        plaqueOr(c, o, e2, al, 4f * k)

        // 3) la brume froide (lente, très légère)
        for (b in brume) {
            val x = w * modulo(b[0] + 0.02f * sec + 0.03f * sin(sec * 0.4f + b[3]), 1f)
            val y = h * b[1] + 20f * k * sin(sec * 0.3f + b[3])
            o.tache(c, x, y, 160f * k * b[2], 70f * k * b[2], 0.05f * al, glace)
        }

        // 4) le support doré de la puce, puis la puce
        plaqueOr(c, o, support, al, 10f * k)
        val trouS = RectF(support); trouS.inset(18f * k, 18f * k)
        c.drawRoundRect(trouS, 6f * k, 6f * k, o.pinceau(avecAlpha(rgb(40, 30, 16), al)))
        for ((vx, vy) in listOf(support.left + 9f * k to support.top + 9f * k, support.right - 9f * k to support.top + 9f * k,
                support.left + 9f * k to support.bottom - 9f * k, support.right - 9f * k to support.bottom - 9f * k)) {
            c.drawCircle(vx, vy, 4.5f * k, o.pinceau(avecAlpha(rgb(60, 44, 20), al)))           // les vis de fixation
            c.drawCircle(vx, vy, 4.5f * k, o.pinceau(avecAlpha(rgb(230, 195, 130), 0.4f * al), 0.8f * k))
        }
        val s = 0.27f * w
        val puce = RectF(cx - s, cy - s, cx + s, cy + s)
        val fondP = o.pinceau()
        fondP.shader = RadialGradient(cx, cy - s * 0.3f, s * 1.6f, rgb(18, 26, 48), rgb(8, 12, 26), Shader.TileMode.CLAMP)
        fondP.alpha = (255 * al).toInt()
        c.drawRect(puce, fondP)
        // plots de connexion sur le bord + fils de liaison vers le support
        val plot = o.pinceau(avecAlpha(rgb(190, 175, 140), 0.7f * al))
        val fil = o.pinceau(avecAlpha(rgb(210, 200, 180), 0.35f * al), 0.7f * k)
        for (m in 0 until 12) {
            val t = puce.left + (m + 0.5f) / 12f * 2f * s
            for (pp in arrayOf(floatArrayOf(t, puce.top + 6f * k, 0f, -1f), floatArrayOf(t, puce.bottom - 6f * k, 0f, 1f),
                    floatArrayOf(puce.left + 6f * k, t, -1f, 0f), floatArrayOf(puce.right - 6f * k, t, 1f, 0f))) {
                c.drawRect(pp[0] - 3f * k, pp[1] - 3f * k, pp[0] + 3f * k, pp[1] + 3f * k, plot)
                c.drawLine(pp[0], pp[1], pp[0] + pp[2] * 18f * k, pp[1] + pp[3] * 18f * k, fil)
            }
        }
        // 5) les résonateurs de lecture : des lignes en zigzag (serpentins) près des bords
        val pRes = o.pinceau(avecAlpha(rgb(120, 150, 200), 0.35f * al), 0.9f * k)
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
            val ch = Path(); val x = cx + sx * s * 0.86f; val y = cy + sy * s * 0.30f
            ch.moveTo(x, y)
            for (m in 0 until 7) ch.lineTo(x - sx * 10f * k * ((m % 2) * 2 - 1), y + sy * (m + 1) * 7f * k)
            c.drawPath(ch, pRes)
        }

        // 6) le réseau de qubits (en losange) : coupleurs, onde d'intrication, qubits en croix
        val pas = s * 0.20f
        val centre = (nq - 1) / 2f
        fun px(ii: Int, jj: Int) = cx + (ii - jj) * pas / sqrt(2f)
        fun py(ii: Int, jj: Int) = cy + (ii + jj - (nq - 1)) * pas / sqrt(2f)
        fun dist(ii: Int, jj: Int) = abs(ii - centre) + abs(jj - centre)
        val front = modulo(sec * 4.2f, 12f) - 2f          // l'onde d'intrication qui part du centre
        val pCoupl = o.pinceau(null, 1.6f * k)
        for (ii in 0 until nq) for (jj in 0 until nq) {
            for (dd in arrayOf(intArrayOf(1, 0), intArrayOf(0, 1))) {
                val i2 = ii + dd[0]; val j2 = jj + dd[1]
                if (i2 >= nq || j2 >= nq) continue
                val d = (dist(ii, jj) + dist(i2, j2)) / 2f
                val apparait = borne(app * 9f - d * 1.2f)
                val onde = exp(-((d - front) * (d - front)) / 1.2f)
                val x1 = px(ii, jj); val y1 = py(ii, jj); val x2 = px(i2, j2); val y2 = py(i2, j2)
                pCoupl.color = avecAlpha(rgb(70, 90, 130), 0.5f * apparait * a)
                c.drawLine(x1, y1, x2, y2, pCoupl)
                if (onde > 0.03f) {
                    c.drawLine(x1, y1, x2, y2, o.lumiere(avecAlpha(bleu, 0.55f * onde * apparait * a), 2.2f * k))
                    o.halo(c, (x1 + x2) / 2f, (y1 + y2) / 2f, 10f * k, 0.12f * onde * apparait * a, bleu)
                }
            }
        }
        val croix = o.pinceau(null, 2.6f * k); val croixL = o.lumiere(null, 1.4f * k)
        for (ii in 0 until nq) for (jj in 0 until nq) {
            val d = dist(ii, jj)
            val apparait = borne(app * 9f - d * 1.2f)
            if (apparait <= 0f) continue
            val ph = phases[ii * nq + jj]
            // superposition : chaque qubit « respire » à son propre rythme, + l'interférence de l'onde
            val etat = 0.5f + 0.5f * sin(sec * ph[1] + ph[0])
            val interf = 0.5f + 0.5f * kotlin.math.cos(d * 1.4f - sec * 3f)
            val onde = exp(-((d - front) * (d - front)) / 1.2f)
            val x = px(ii, jj); val y = py(ii, jj)
            val lum = (0.25f + 0.35f * etat * interf + 0.4f * onde) * apparait * a
            o.halo(c, x, y, 12f * k, 0.14f * lum, glace)
            val bras = 7.5f * k
            croix.color = avecAlpha(rgb(150, 180, 220), 0.75f * apparait * a)
            c.drawLine(x - bras, y, x + bras, y, croix); c.drawLine(x, y - bras, x, y + bras, croix)
            croixL.color = avecAlpha(blanc, 0.6f * lum)
            c.drawLine(x - bras, y, x + bras, y, croixL); c.drawLine(x, y - bras, x, y + bras, croixL)
        }
        c.drawRect(puce, o.pinceau(avecAlpha(rgb(130, 160, 210), 0.35f * al), 1f * k))
        o.halo(c, cx, cy, s * 1.5f, 0.06f * al, bleu)
        c.restore()

        o.vignette(c, w, h, 0.6f)
        o.revelationBandes(c, i.revelation, glace)
    }
}
