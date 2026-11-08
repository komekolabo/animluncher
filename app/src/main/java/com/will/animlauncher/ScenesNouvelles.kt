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
            c.drawCircle(ex, yb - 22f * dp, 4f * dp * app, o.pinceau(avecAlpha(rgb(110, 20, 15), chute * a)))
        } else {
            c.drawCircle(ex, ey, 4f * dp * app, o.lumiere(avecAlpha(ROUGE_G, 0.7f * app * a)))
        }
        c.restore()
        o.vignette(c, w, h, 0.6f)
        o.revelationDouce(c, ex, ey, rev, ROUGE_G)
    }
}
