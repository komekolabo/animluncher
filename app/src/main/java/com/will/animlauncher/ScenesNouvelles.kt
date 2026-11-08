package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Étire le temps d'une scène : avec un facteur 2,5, tous ses mouvements vont 2,5 fois moins vite.
// Sert aux jeux, passés de « Secondaire » (0,5 s) à « Jeu » (environ 1 s).
class Etire(private val scene: Scene, private val facteur: Float) : Scene() {
    override fun dessiner(c: Canvas, o: Outils, i: Instant) =
        scene.dessiner(c, o, Instant(i.secondes / facteur, i.intro, i.revelation))
}

// ===================== ÉCHECS : « Sacrifice de tour » (le mème) =====================
// La tour fonce prendre le pion devant le roi : « !! ». Le roi la prend… et la dame arrive : mat.
class Sacrifice : Scene() {

    // Une pièce dessinée simplement (silhouette), centrée en (x, y), pour une case de taille s.
    private fun piece(c: Canvas, o: Outils, genre: Int, x: Float, y: Float, s: Float, blanc: Boolean, alpha: Float, dp: Float) {
        val corps = if (blanc) argb(255f * alpha, 228, 222, 206) else argb(255f * alpha, 28, 28, 32)
        val bord = if (blanc) argb(255f * alpha, 40, 36, 30) else argb(160f * alpha, 200, 200, 210)
        val p = o.pinceau(corps)
        val b = o.pinceau(bord, 1.3f * dp)
        val socle = RectF(x - s * 0.3f, y + s * 0.26f, x + s * 0.3f, y + s * 0.36f)
        c.drawRoundRect(socle, 2f * dp, 2f * dp, p); c.drawRoundRect(socle, 2f * dp, 2f * dp, b)
        val t = Path()
        when (genre) {
            PION -> {
                t.moveTo(x - s * 0.2f, y + s * 0.26f); t.lineTo(x - s * 0.08f, y - s * 0.02f); t.lineTo(x + s * 0.08f, y - s * 0.02f); t.lineTo(x + s * 0.2f, y + s * 0.26f); t.close()
                c.drawPath(t, p); c.drawPath(t, b)
                c.drawCircle(x, y - s * 0.1f, s * 0.13f, p); c.drawCircle(x, y - s * 0.1f, s * 0.13f, b)
            }
            TOUR -> {
                t.moveTo(x - s * 0.22f, y + s * 0.26f); t.lineTo(x - s * 0.18f, y - s * 0.16f); t.lineTo(x - s * 0.26f, y - s * 0.16f); t.lineTo(x - s * 0.26f, y - s * 0.34f)
                for (k in 0 until 3) {                                 // les créneaux
                    val xa = x - s * 0.26f + k * s * 0.2f
                    t.lineTo(xa + s * 0.09f, y - s * 0.34f); t.lineTo(xa + s * 0.09f, y - s * 0.26f); t.lineTo(xa + s * 0.15f, y - s * 0.26f); t.lineTo(xa + s * 0.15f, y - s * 0.34f)
                }
                t.lineTo(x + s * 0.26f, y - s * 0.34f); t.lineTo(x + s * 0.26f, y - s * 0.16f); t.lineTo(x + s * 0.18f, y - s * 0.16f); t.lineTo(x + s * 0.22f, y + s * 0.26f); t.close()
                c.drawPath(t, p); c.drawPath(t, b)
            }
            DAME -> {
                t.moveTo(x - s * 0.24f, y + s * 0.26f); t.lineTo(x - s * 0.3f, y - s * 0.22f); t.lineTo(x - s * 0.12f, y - s * 0.02f)
                t.lineTo(x, y - s * 0.3f); t.lineTo(x + s * 0.12f, y - s * 0.02f); t.lineTo(x + s * 0.3f, y - s * 0.22f); t.lineTo(x + s * 0.24f, y + s * 0.26f); t.close()
                c.drawPath(t, p); c.drawPath(t, b)
                for (d in arrayOf(floatArrayOf(-0.3f, -0.25f), floatArrayOf(0f, -0.33f), floatArrayOf(0.3f, -0.25f))) {   // la couronne
                    c.drawCircle(x + d[0] * s, y + d[1] * s, s * 0.05f, p); c.drawCircle(x + d[0] * s, y + d[1] * s, s * 0.05f, b)
                }
            }
            else -> {                                                 // le roi, avec sa croix
                t.moveTo(x - s * 0.24f, y + s * 0.26f); t.lineTo(x - s * 0.28f, y - s * 0.12f); t.quadTo(x, y - s * 0.3f, x + s * 0.28f, y - s * 0.12f); t.lineTo(x + s * 0.24f, y + s * 0.26f); t.close()
                c.drawPath(t, p); c.drawPath(t, b)
                c.drawLine(x, y - s * 0.24f, x, y - s * 0.44f, o.pinceau(corps, 3f * dp))
                c.drawLine(x - s * 0.08f, y - s * 0.36f, x + s * 0.08f, y - s * 0.36f, o.pinceau(corps, 3f * dp))
            }
        }
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(10, 12, 10), rgb(4, 5, 4), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val m = min(w, h)
        val s = m * 0.15f                                   // une case ; on montre les colonnes c à h, rangées 8 à 3
        val x0 = w / 2f - 3f * s; val y0 = h / 2f - 3f * s
        fun cx(col: Int) = x0 + (col + 0.5f) * s
        fun cy(lig: Int) = y0 + (lig + 0.5f) * s
        val case = o.pinceau()
        for (lig in 0 until 6) for (col in 0 until 6) {
            case.color = if ((col + lig) % 2 == 0) argb(255f * app, 88, 112, 64) else argb(255f * app, 34, 44, 28)
            c.drawRect(x0 + col * s, y0 + lig * s, x0 + (col + 1) * s, y0 + (lig + 1) * s, case)
        }
        // Les étapes de l'histoire, en secondes.
        val tourT = doux(borne((sec - 0.03f) / 0.15f))      // la tour fonce de h3 à h7 et prend le pion
        val roiT = doux(borne((sec - 0.22f) / 0.08f))       // le roi prend la tour (g8 -> h7)
        val dameT = doux(borne((sec - 0.32f) / 0.12f))      // la dame arrive en h5 : échec et mat
        val chute = ralentit(borne((sec - 0.46f) / 0.1f))   // le roi tombe
        val hx = cx(5); val hy = cy(1)
        val kx = mix(cx(4), hx, roiT); val ky = mix(cy(0), hy, roiT)
        if (dameT > 0.9f) o.tache(c, hx, hy, s * 0.7f, s * 0.7f, 0.5f * a * (0.8f + 0.2f * sin(sec * 8f)), rgb(230, 60, 50))   // case du roi : mat
        for (col in intArrayOf(3, 4)) piece(c, o, PION, cx(col), cy(1), s, false, app * a, dp)
        if (tourT < 1f) {
            piece(c, o, PION, hx, hy, s, false, app * a, dp)
        } else {                                            // le pion pris s'envole en tournoyant
            val vol = borne((sec - 0.18f) / 0.25f)
            c.save(); c.translate(hx + vol * s * 2.5f, hy - vol * s * 1.5f + vol * vol * s * 3f); c.rotate(vol * 400f)
            piece(c, o, PION, 0f, 0f, s, false, (1f - vol) * app * a, dp); c.restore()
        }
        if (roiT < 0.9f) {                                  // la tour blanche, avec sa traînée de vitesse
            val y = mix(cy(5) + s * 2f, hy, tourT)
            if (tourT > 0f && tourT < 1f) for (g in 1..5) o.halo(c, hx, y + g * s * 0.25f, s * 0.3f, 0.08f * (1f - g / 6f) * app * a, rgb(255, 230, 180))
            piece(c, o, TOUR, hx, y, s, true, app * a * (1f - roiT), dp)
        }
        if (tourT >= 0.95f && sec < 0.33f) {                // l'impact
            val t = borne((sec - 0.18f) / 0.15f)
            c.drawCircle(hx, hy, s * (0.3f + t), o.lumiere(avecAlpha(rgb(255, 220, 160), 0.5f * (1f - t) * a), 2f * dp))
        }
        c.save(); c.translate(kx, ky + s * 0.3f); c.rotate(-90f * chute)   // le roi (couché à la fin)
        piece(c, o, ROI, 0f, -s * 0.3f, s, false, app * a, dp); c.restore()
        piece(c, o, DAME, mix(cx(3), cx(5), dameT), mix(cy(5), cy(3), dameT), s, true, app * a, dp)   // la dame f3 -> h5
        val pop = ralentit(borne((sec - 0.17f) / 0.08f))   // « !! » : le coup brillant
        if (pop > 0f) {
            val bx = hx + s * 0.42f; val by = hy - s * 0.45f
            val r = s * 0.24f * pop * (1f + 0.06f * sin(sec * 7f))
            c.drawCircle(bx, by, r, o.pinceau(avecAlpha(rgb(30, 170, 160), a)))
            c.drawCircle(bx, by, r, o.pinceau(avecAlpha(rgb(255, 255, 255), 0.8f * a), 1.5f * dp))
            texte(c, "!!", bx, by, r * 1.1f, avecAlpha(rgb(255, 255, 255), a))
            o.halo(c, bx, by, r * 2.2f, 0.25f * a, rgb(40, 200, 190))
        }
        if (chute > 0.5f) texte(c, "#", hx - s * 0.45f, hy - s * 0.45f, s * 0.35f, avecAlpha(rgb(240, 90, 80), (chute - 0.5f) * 2f * a))
        o.vignette(c, w, h, 0.6f)
        o.revelationDouce(c, hx, hy, rev, rgb(40, 200, 190))
    }

    companion object { const val PION = 0; const val TOUR = 1; const val DAME = 2; const val ROI = 3 }
}

// ===================== MAPS : « A* dans la ville » =====================
// Une ville vue du ciel. L'algorithme A* (celui des GPS) cherche le chemin : on voit son arbre
// de recherche pousser dans les rues, attiré vers la destination. Puis le trajet s'allume,
// l'épingle tombe, et on plonge dessus.
class AEtoile : Scene() {
    // Le calcul est fait une seule fois pour une taille d'écran donnée, puis gardé.
    private var cols = 0; private var lignes = 0
    private lateinit var horiz: BooleanArray        // rue ouverte entre (x, y) et (x+1, y) ?
    private lateinit var vert: BooleanArray         // rue ouverte entre (x, y) et (x, y+1) ?
    private var ordre = IntArray(0)                 // les intersections, dans l'ordre où A* les visite
    private lateinit var parent: IntArray           // d'où vient-on pour arriver à chaque intersection
    private var chemin = IntArray(0)                // le trajet final, du départ à l'arrivée
    private var depart = 0; private var but = 0
    private lateinit var blocs: FloatArray

    private fun calculer(nc: Int, nl: Int, graine: Int) {
        cols = nc; lignes = nl
        val r = Random(graine)
        horiz = BooleanArray(nc * nl) { r.nextFloat() > 0.28f }   // on ferme 28 % des rues au hasard
        vert = BooleanArray(nc * nl) { r.nextFloat() > 0.28f }
        depart = (nl * 0.82f).toInt() * nc + (nc * 0.15f).toInt()
        but = (nl * 0.18f).toInt() * nc + (nc * 0.82f).toInt()
        val bx = but % nc; val by = but / nc
        fun h(n: Int) = (abs(n % nc - bx) + abs(n / nc - by)).toFloat()
        val cout = IntArray(nc * nl) { Int.MAX_VALUE }
        parent = IntArray(nc * nl) { -1 }
        val vus = BooleanArray(nc * nl)
        val visites = ArrayList<Int>()
        // La file d'attente d'A* : on sort toujours l'intersection la plus prometteuse
        // (distance parcourue + 0,6 × distance restante estimée ; 0,6 = on explore un peu plus large).
        val file = PriorityQueue<FloatArray>(compareBy<FloatArray> { it[0] })
        cout[depart] = 0
        file.add(floatArrayOf(h(depart) * 0.6f, depart.toFloat()))
        while (file.isNotEmpty()) {
            val n = file.poll()!![1].toInt()
            if (vus[n]) continue
            vus[n] = true; visites.add(n)
            if (n == but) break
            val x = n % nc; val y = n / nc
            val voisins = intArrayOf(
                if (x + 1 < nc && horiz[n]) n + 1 else -1,
                if (x > 0 && horiz[n - 1]) n - 1 else -1,
                if (y + 1 < nl && vert[n]) n + nc else -1,
                if (y > 0 && vert[n - nc]) n - nc else -1
            )
            for (v in voisins) {
                if (v < 0) continue
                val g = cout[n] + 1
                if (g < cout[v]) { cout[v] = g; parent[v] = n; file.add(floatArrayOf(g + 0.6f * h(v), v.toFloat())) }
            }
        }
        if (!vus[but]) { calculer(nc, nl, graine + 1); return }   // pas de chemin : on retire une autre ville
        ordre = visites.toIntArray()
        val trajet = ArrayList<Int>()
        var n = but
        while (n != -1) { trajet.add(n); n = parent[n] }
        chemin = trajet.reversed().toIntArray()
        blocs = FloatArray(nc * nl) { r.nextFloat() }
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val m = min(w, h)
        val pas = m * 0.085f
        val nc = (w / pas).toInt() + 2; val nl = (h / pas).toInt() + 2
        if (nc != cols || nl != lignes) calculer(nc, nl, 77)
        val x0 = (w - (cols - 1) * pas) / 2f; val y0 = (h - (lignes - 1) * pas) / 2f
        fun px(n: Int) = x0 + (n % cols) * pas
        fun py(n: Int) = y0 + (n / cols) * pas
        c.drawColor(rgb(9, 11, 16))
        val app = ralentit(intro)
        val a = 1f - rev
        val bleu = rgb(70, 150, 255)
        val ex = px(but); val ey = py(but)
        val zoom = 1f + 4f * accelere(rev)
        c.save(); c.translate(ex, ey); c.scale(zoom, zoom); c.translate(-ex, -ey)
        val bloc = o.pinceau()
        for (y in 0 until lignes - 1) for (x in 0 until cols - 1) {     // pâtés de maisons (et quelques parcs)
            val v = blocs[y * cols + x]
            val bx = x0 + x * pas; val by = y0 + y * pas
            bloc.color = if (v > 0.9f) argb(255f * app, 18, 34, 24) else argb(255f * app, (20 + 14 * v).toInt(), (22 + 14 * v).toInt(), (30 + 14 * v).toInt())
            c.drawRoundRect(RectF(bx + pas * 0.14f, by + pas * 0.14f, bx + pas * 0.86f, by + pas * 0.86f), 3f * dp, 3f * dp, bloc)
        }
        val rue = o.pinceau(argb(40f * app * a, 120, 130, 160), 1.2f * dp)
        for (n in 0 until cols * lignes) {
            if (n % cols + 1 < cols && horiz[n]) c.drawLine(px(n), py(n), px(n + 1), py(n + 1), rue)
            if (n / cols + 1 < lignes && vert[n]) c.drawLine(px(n), py(n), px(n + cols), py(n + cols), rue)
        }
        // 1) La recherche : l'arbre d'A* pousse dans les rues.
        val explore = ralentit(borne(sec / 0.45f)) * ordre.size
        val branche = o.lumiere(avecAlpha(rgb(60, 200, 210), 0.5f * app * a), 1.8f * dp)
        for (k in ordre.indices) {
            if (k > explore) break
            val n = ordre[k]; val p = parent[n]
            val age = explore - k
            if (p >= 0) {
                val f = borne(age)
                c.drawLine(px(p), py(p), mix(px(p), px(n), f), mix(py(p), py(n), f), branche)
            }
            if (age < 6f) o.halo(c, px(n), py(n), pas * 0.35f, 0.3f * (1f - age / 6f) * app * a, rgb(80, 220, 230))   // la frontière brille
        }
        // 2) Le chemin trouvé s'allume.
        val trace = ralentit(borne((sec - 0.42f) / 0.25f))
        if (trace > 0f && chemin.size > 1) {
            val chem = Path()
            val nb = (chemin.size - 1) * trace
            val k = nb.toInt()
            chem.moveTo(px(chemin[0]), py(chemin[0]))
            for (q in 1..k) chem.lineTo(px(chemin[q]), py(chemin[q]))
            if (k < chemin.size - 1) chem.lineTo(mix(px(chemin[k]), px(chemin[k + 1]), nb - k), mix(py(chemin[k]), py(chemin[k + 1]), nb - k))
            val couches = arrayOf(floatArrayOf(14f, 0.1f), floatArrayOf(6f, 0.3f), floatArrayOf(3f, 0.95f))
            for (cc in couches) c.drawPath(chem, o.lumiere(avecAlpha(bleu, cc[1] * app * a), cc[0] * dp))
        }
        o.halo(c, px(depart), py(depart), 14f * dp, 0.6f * app * a, bleu)
        c.drawCircle(px(depart), py(depart), 5f * dp * app, o.pinceau(avecAlpha(rgb(220, 235, 255), app * a)))
        // 3) L'épingle tombe sur la destination.
        val chute = ralentit(borne((sec - 0.62f) / 0.2f))
        if (chute > 0f) {
            val onde = modulo(sec * 1.3f, 1f)
            c.save(); c.translate(ex, ey); c.scale(1f, 0.5f)
            c.drawCircle(0f, 0f, 40f * dp * onde, o.pinceau(avecAlpha(ROUGE_G, 0.6f * (1f - onde) * a), 2.5f * dp))
            c.restore()
            val yb = ey - (1f - chute) * 90f * dp
            val corps = o.pinceau()
            corps.shader = RadialGradient(ex - 3f * dp, yb - 26f * dp, 16f * dp, avecAlpha(rgb(255, 140, 120), chute * a), avecAlpha(ROUGE_G, chute * a), Shader.TileMode.CLAMP)
            c.drawCircle(ex, yb - 22f * dp, 11f * dp, corps)
            val pied = Path(); pied.moveTo(ex - 9.5f * dp, yb - 17f * dp); pied.lineTo(ex, yb); pied.lineTo(ex + 9.5f * dp, yb - 17f * dp); pied.close()
            c.drawPath(pied, corps)
            c.drawCircle(ex, yb - 22f * dp, 4f * dp, o.pinceau(avecAlpha(rgb(110, 20, 15), chute * a)))
        } else {
            c.drawCircle(ex, ey, 4f * dp * app, o.lumiere(avecAlpha(ROUGE_G, 0.7f * app * a)))
        }
        c.restore()
        o.vignette(c, w, h, 0.6f)
        o.revelationDouce(c, ex, ey, rev, ROUGE_G)
    }
}
