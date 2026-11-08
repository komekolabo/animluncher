package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.PriorityQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ===================== MAPS : « A* dans une capitale » =====================
// 5 vraies capitales, dessinées d'après leur géographie réelle (fleuve, grandes avenues, anneaux,
// canaux, parcs) : Paris, Londres, Washington, Amsterdam, Moscou. À chaque ouverture : une ville
// et deux monuments au hasard. Style Google Maps en mode sombre : la destination se tape dans la
// barre de recherche, A* (l'algorithme des GPS) cherche discrètement dans les vraies rues,
// l'itinéraire bleu se trace, l'épingle tombe et le panneau « Démarrer » monte.
//
// Coordonnées des villes : 1 = le petit côté de l'écran, (0, 0) = le centre, y vers le bas (le sud).
// Une liste de points s'écrit floatArrayOf(x1, y1, x2, y2, ...).

class Ville(val nom: String) {
    val fleuves = ArrayList<Pair<FloatArray, Float>>()   // (points, largeur)
    val eaux = ArrayList<FloatArray>()                   // plans d'eau (polygones)
    val iles = ArrayList<FloatArray>()                   // terre posée sur l'eau
    val parcs = ArrayList<FloatArray>()                  // pas de petites rues dedans (dessinés en vert)
    val vides = ArrayList<FloatArray>()                  // pas de petites rues dedans (non dessinés)
    val avenues = ArrayList<Pair<FloatArray, Int>>()     // (points, rang) : 1 avenue, 2 grand axe ; elles passent sur l'eau (ponts)
    val tissu = ArrayList<FloatArray>()                  // les petites rues
    var monuments = listOf<Triple<String, Float, Float>>()
}

private fun rad(deg: Float) = deg * PI.toFloat() / 180f

// Points d'un arc de cercle (angles en degrés : 0 = est, 90 = sud).
fun arcPts(cx: Float, cy: Float, r: Float, a0: Float, a1: Float, n: Int = 40): FloatArray {
    val p = FloatArray((n + 1) * 2)
    for (k in 0..n) {
        val a = rad(a0 + (a1 - a0) * k / n)
        p[2 * k] = cx + r * cos(a); p[2 * k + 1] = cy + r * sin(a)
    }
    return p
}

fun rayonPts(cx: Float, cy: Float, ang: Float, r0: Float, r1: Float): FloatArray {
    val a = rad(ang)
    return floatArrayOf(cx + r0 * cos(a), cy + r0 * sin(a), cx + r1 * cos(a), cy + r1 * sin(a))
}

// Un quadrillage de petites rues, tourné de « angle » degrés, dont on garde une fraction « garde ».
fun grille(v: Ville, x0: Float, y0: Float, x1: Float, y1: Float, pas: Float, angle: Float, garde: Float, graine: Int) {
    val r = Random(graine)
    val ca = cos(rad(angle)); val sa = sin(rad(angle))
    fun tx(x: Float, y: Float) = x * ca - y * sa
    fun ty(x: Float, y: Float) = x * sa + y * ca
    val nx = ((x1 - x0) / pas).toInt(); val ny = ((y1 - y0) / pas).toInt()
    for (j in 0..ny) for (i in 0 until nx) if (r.nextFloat() < garde) {
        val ax = x0 + i * pas; val bx = ax + pas; val y = y0 + j * pas
        v.tissu.add(floatArrayOf(tx(ax, y), ty(ax, y), tx(bx, y), ty(bx, y)))
    }
    for (i in 0..nx) for (j in 0 until ny) if (r.nextFloat() < garde) {
        val x = x0 + i * pas; val ay = y0 + j * pas; val by = ay + pas
        v.tissu.add(floatArrayOf(tx(x, ay), ty(x, ay), tx(x, by), ty(x, by)))
    }
}

// Un tissu en toile d'araignée (anneaux + rayons), pour les villes concentriques comme Moscou.
fun toile(v: Ville, r0: Float, r1: Float, pas: Float, nRayons: Int, garde: Float, graine: Int) {
    val r = Random(graine)
    var rr = r0
    while (rr <= r1) {
        val n = max(8, (2 * PI.toFloat() * rr / pas).toInt())
        for (i in 0 until n) if (r.nextFloat() < garde) v.tissu.add(arcPts(0f, 0f, rr, 360f * i / n, 360f * (i + 1) / n, 4))
        rr += pas
    }
    for (i in 0 until nRayons) {
        val ang = 360f * i / nRayons + (r.nextFloat() * 6f - 3f)
        rr = r0
        while (rr < r1) {
            if (r.nextFloat() < garde) v.tissu.add(rayonPts(0f, 0f, ang, rr, rr + pas))
            rr += pas
        }
    }
}

fun paris(): Ville {
    val v = Ville("PARIS")
    grille(v, -1.5f, -1.3f, 1.5f, 1.3f, 0.06f, 18f, 0.72f, 1)
    grille(v, -1.5f, -1.3f, 1.5f, 1.3f, 0.085f, -30f, 0.25f, 2)
    v.fleuves.add(floatArrayOf(1.6f, 0.48f, 1.0f, 0.36f, 0.6f, 0.2f, 0.38f, 0.1f, 0.2f, 0.07f, 0.0f, 0.05f, -0.15f, 0.06f,
        -0.28f, 0.1f, -0.4f, 0.17f, -0.5f, 0.27f, -0.58f, 0.42f, -0.66f, 0.6f, -0.8f, 0.72f, -1.6f, 0.8f) to 0.045f)   // la Seine
    v.iles.add(floatArrayOf(0.12f, 0.06f, 0.2f, 0.045f, 0.3f, 0.06f, 0.24f, 0.08f, 0.14f, 0.078f))                      // île de la Cité
    v.iles.add(floatArrayOf(0.33f, 0.075f, 0.4f, 0.085f, 0.44f, 0.1f, 0.37f, 0.1f))                                       // île Saint-Louis
    val ex = -0.42f; val ey = -0.22f                                                                                      // l'Étoile
    for (k in 0 until 12) v.avenues.add(rayonPts(ex, ey, 15f + k * 30f, 0.04f, 0.55f) to 1)
    v.avenues.add(arcPts(ex, ey, 0.04f, 0f, 360f, 16) to 1)
    v.avenues.add(floatArrayOf(ex, ey, -0.12f, -0.08f, 0.12f, -0.02f, 0.6f, 0.0f) to 2)        // Champs-Élysées, Louvre, Rivoli
    v.avenues.add(arcPts(0.05f, 0.12f, 0.5f, 190f, 350f) to 1)                                  // Grands Boulevards
    v.avenues.add(arcPts(0.05f, 0.05f, 1.05f, 0f, 360f, 90) to 2)                               // le périphérique
    for (x in floatArrayOf(-0.3f, -0.12f, 0.05f, 0.2f, 0.36f, 0.55f))                          // ponts et axes nord-sud
        v.avenues.add(floatArrayOf(x, -0.7f, x + 0.02f, 0.0f, x - 0.03f, 0.75f) to 1)
    v.avenues.add(floatArrayOf(-0.62f, 0.22f, -0.33f, 0.24f, -0.1f, 0.4f) to 1)                 // Champ de Mars, Invalides
    v.parcs.add(floatArrayOf(-0.55f, 0.2f, -0.46f, 0.18f, -0.34f, 0.38f, -0.44f, 0.4f))         // Champ de Mars
    v.parcs.add(floatArrayOf(-0.04f, -0.06f, 0.1f, -0.04f, 0.1f, 0.0f, -0.04f, -0.02f))         // Tuileries
    v.monuments = listOf(Triple("Arc de Triomphe", -0.42f, -0.22f), Triple("Tour Eiffel", -0.48f, 0.24f), Triple("Notre-Dame", 0.2f, 0.065f),
        Triple("Louvre", 0.13f, -0.02f), Triple("Sacré-Cœur", 0.12f, -0.42f), Triple("Panthéon", 0.24f, 0.3f))
    return v
}

fun londres(): Ville {
    val v = Ville("LONDRES")
    grille(v, -1.6f, -1.3f, 1.6f, 1.3f, 0.065f, -7f, 0.62f, 3)
    grille(v, -1.6f, -1.3f, 1.6f, 1.3f, 0.075f, 28f, 0.32f, 4)
    v.fleuves.add(floatArrayOf(-1.6f, 0.42f, -0.9f, 0.4f, -0.6f, 0.3f, -0.42f, 0.18f, -0.36f, 0.0f, -0.28f, -0.08f, -0.1f, -0.06f,
        0.1f, -0.02f, 0.3f, 0.02f, 0.5f, 0.0f, 0.7f, 0.08f, 0.82f, 0.28f, 0.95f, 0.4f, 1.1f, 0.3f, 1.15f, 0.1f, 1.6f, 0.05f) to 0.055f)   // la Tamise
    v.parcs.add(floatArrayOf(-1.0f, -0.42f, -0.6f, -0.42f, -0.62f, -0.18f, -1.0f, -0.2f))       // Hyde Park
    v.parcs.add(floatArrayOf(-0.56f, -0.16f, -0.4f, -0.12f, -0.4f, -0.06f, -0.56f, -0.08f))     // St James's Park
    v.parcs.add(floatArrayOf(-0.75f, -1.2f, -0.35f, -1.2f, -0.35f, -0.75f, -0.75f, -0.75f))     // Regent's Park
    v.avenues.add(floatArrayOf(-1.6f, -0.45f, -0.6f, -0.44f, -0.2f, -0.38f, 0.3f, -0.3f, 1.6f, -0.25f) to 2)   // Oxford St, High Holborn
    v.avenues.add(floatArrayOf(-0.42f, -0.42f, -0.4f, -0.25f, -0.36f, -0.12f) to 1)            // Regent St
    v.avenues.add(floatArrayOf(-0.6f, -0.12f, -0.38f, -0.09f, -0.28f, -0.12f, 0.1f, -0.1f, 0.3f, -0.08f) to 1)  // The Mall, Strand, Fleet St
    v.avenues.add(floatArrayOf(-0.62f, -0.2f, -0.6f, 0.4f) to 1)
    for (p in listOf(floatArrayOf(-0.42f, -0.06f, -0.34f, 0.08f, -0.3f, 0.4f), floatArrayOf(-0.18f, -0.2f, -0.16f, 0.0f, -0.14f, 0.4f),
            floatArrayOf(0.08f, -0.2f, 0.08f, 0.05f, 0.02f, 0.45f), floatArrayOf(0.36f, -0.2f, 0.34f, 0.1f, 0.3f, 0.45f),
            floatArrayOf(0.75f, -0.3f, 0.85f, 0.5f))) v.avenues.add(p to 1)                    // les ponts
    v.avenues.add(floatArrayOf(-1.6f, 0.0f, -0.6f, 0.02f, -0.4f, 0.0f) to 1)
    v.avenues.add(floatArrayOf(-0.3f, 0.45f, 0.3f, 0.42f, 1.0f, 0.6f) to 1)
    v.monuments = listOf(Triple("Big Ben", -0.33f, -0.02f), Triple("Buckingham", -0.56f, -0.12f), Triple("Tower Bridge", 0.38f, -0.06f),
        Triple("British Museum", -0.12f, -0.42f), Triple("Saint-Paul", 0.12f, -0.13f), Triple("London Eye", -0.27f, 0.06f))
    return v
}

fun washington(): Ville {
    val v = Ville("WASHINGTON")
    grille(v, -1.6f, -1.3f, 1.6f, 1.3f, 0.058f, 0f, 0.8f, 5)
    val capX = 0.4f; val capY = 0.12f; val mbX = -0.22f; val mbY = -0.12f                     // Capitole, Maison-Blanche
    v.fleuves.add(floatArrayOf(-1.0f, -1.3f, -0.82f, -0.6f, -0.72f, -0.1f, -0.62f, 0.3f, -0.4f, 0.6f, -0.1f, 0.85f, 0.1f, 1.3f) to 0.13f)   // Potomac
    v.fleuves.add(floatArrayOf(1.6f, -0.1f, 1.0f, 0.3f, 0.7f, 0.55f, 0.35f, 0.75f, 0.0f, 0.88f) to 0.06f)                                    // Anacostia
    v.parcs.add(floatArrayOf(-0.6f, 0.06f, 0.36f, 0.06f, 0.36f, 0.18f, -0.6f, 0.18f))           // le National Mall
    v.parcs.add(floatArrayOf(-0.28f, -0.18f, -0.16f, -0.18f, -0.16f, -0.04f, -0.28f, -0.04f))   // jardins de la Maison-Blanche
    for (k in 0 until 8) v.avenues.add(rayonPts(capX, capY, 22.5f + 45f * k, 0.03f, 1.2f) to 1)   // avenues en étoile
    for (k in 0 until 6) v.avenues.add(rayonPts(mbX, mbY, 30f + 60f * k, 0.07f, 1.0f) to 1)
    v.avenues.add(floatArrayOf(capX, capY, 0.1f, -0.05f, mbX, mbY, -0.8f, -0.5f) to 2)          // Pennsylvania Avenue
    v.avenues.add(floatArrayOf(-1.6f, -0.6f, 1.6f, 0.6f) to 1)                                  // Massachusetts Avenue
    v.avenues.add(floatArrayOf(-1.6f, -0.3f, 1.6f, -0.3f) to 1)
    v.avenues.add(floatArrayOf(-0.04f, -1.3f, -0.04f, 1.3f) to 1)
    v.monuments = listOf(Triple("Capitole", 0.4f, 0.12f), Triple("Maison-Blanche", -0.22f, -0.11f), Triple("Washington Monument", -0.12f, 0.12f),
        Triple("Lincoln Memorial", -0.58f, 0.12f), Triple("Union Station", 0.42f, -0.12f), Triple("Jefferson Memorial", -0.18f, 0.4f))
    return v
}

fun amsterdam(): Ville {
    val v = Ville("AMSTERDAM")
    val gx = 0f; val gy = -0.3f                                                                 // la gare centrale
    grille(v, -1.6f, -1.3f, 1.6f, 1.3f, 0.06f, 4f, 0.7f, 6)
    v.eaux.add(floatArrayOf(-1.6f, -1.4f, 1.6f, -1.4f, 1.6f, -0.38f, 0.4f, -0.34f, 0.1f, -0.38f, -0.4f, -0.36f, -1.6f, -0.4f))   // l'IJ
    v.vides.add(arcPts(gx, gy, 0.7f, 0f, 180f, 40))                    // dans le croissant : seulement quais et ponts
    for (r in floatArrayOf(0.24f, 0.32f, 0.4f, 0.48f, 0.66f))           // le croissant des canaux, au sud de la gare
        v.fleuves.add(arcPts(gx, gy, r, 12f, 168f, 50) to (if (r < 0.6f) 0.022f else 0.03f))
    v.fleuves.add(floatArrayOf(0.12f, -0.3f, 0.35f, 0.1f, 0.65f, 0.35f, 1.6f, 0.6f) to 0.05f)   // l'Amstel
    for (r in floatArrayOf(0.2f, 0.28f, 0.36f, 0.44f, 0.57f))           // les quais entre les canaux
        v.avenues.add(arcPts(gx, gy, r, 8f, 172f, 50) to 1)
    for (k in 0 until 9) v.avenues.add(rayonPts(gx, gy, 18f + 18f * k, 0.04f, 0.9f) to 1)      // les rues qui traversent les canaux
    v.avenues.add(floatArrayOf(gx, gy, 0.0f, -0.1f) to 2)                                       // Damrak
    v.parcs.add(floatArrayOf(-0.52f, 0.44f, -0.2f, 0.48f, -0.22f, 0.6f, -0.55f, 0.57f))         // Vondelpark
    v.monuments = listOf(Triple("Gare centrale", 0.0f, -0.32f), Triple("Place du Dam", 0.0f, -0.1f), Triple("Maison d'Anne Frank", -0.32f, -0.18f),
        Triple("Rijksmuseum", 0.02f, 0.38f), Triple("Rembrandtplein", 0.26f, 0.02f), Triple("Vondelpark", -0.36f, 0.42f))
    return v
}

fun moscou(): Ville {
    val v = Ville("MOSCOU")
    toile(v, 0.08f, 1.6f, 0.065f, 48, 0.7f, 7)
    v.fleuves.add(floatArrayOf(-1.6f, -0.25f, -0.95f, -0.2f, -0.7f, 0.05f, -0.55f, 0.4f, -0.3f, 0.45f, -0.15f, 0.25f, -0.05f, 0.14f,
        0.15f, 0.13f, 0.3f, 0.24f, 0.45f, 0.48f, 0.8f, 0.45f, 1.1f, 0.7f, 1.6f, 0.75f) to 0.05f)   // la Moskova
    v.parcs.add(floatArrayOf(-0.1f, -0.12f, 0.12f, -0.1f, 0.04f, 0.09f, -0.1f, 0.06f))           // le Kremlin
    v.parcs.add(floatArrayOf(-0.52f, 0.48f, -0.3f, 0.5f, -0.38f, 0.72f, -0.6f, 0.66f))           // parc Gorki
    v.avenues.add(arcPts(0f, 0f, 0.3f, -20f, 200f, 50) to 1)                                     // anneau des boulevards (fer à cheval)
    v.avenues.add(arcPts(0f, 0f, 0.55f, 0f, 360f, 70) to 2)                                      // anneau des Jardins
    v.avenues.add(arcPts(0f, 0f, 0.95f, 0f, 360f, 90) to 2)                                      // troisième anneau
    for (k in 0 until 10) v.avenues.add(rayonPts(0f, 0f, 18f + 36f * k + (if (k % 2 == 1) 8f else 0f), 0.14f, 1.6f) to 1)   // radiales
    v.monuments = listOf(Triple("Kremlin", 0.0f, -0.01f), Triple("Théâtre Bolchoï", 0.12f, -0.2f), Triple("Parc Gorki", -0.45f, 0.45f),
        Triple("Gare de Kazan", 0.42f, -0.36f), Triple("Christ-Sauveur", -0.18f, 0.12f), Triple("Loubianka", 0.26f, -0.12f))
    return v
}

// Le point (x, y) est-il dans le polygone ? (on compte les bords traversés par une demi-droite)
fun dansPoly(x: Float, y: Float, p: FloatArray): Boolean {
    var dedans = false
    val n = p.size / 2
    for (i in 0 until n) {
        val x1 = p[2 * i]; val y1 = p[2 * i + 1]; val x2 = p[2 * ((i + 1) % n)]; val y2 = p[2 * ((i + 1) % n) + 1]
        if ((y1 > y) != (y2 > y) && x < x1 + (y - y1) * (x2 - x1) / (y2 - y1)) dedans = !dedans
    }
    return dedans
}

fun distSeg(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax; val dy = by - ay
    val l2 = dx * dx + dy * dy
    val t = if (l2 == 0f) 0f else borne(((px - ax) * dx + (py - ay) * dy) / l2)
    return hypot(px - ax - t * dx, py - ay - t * dy)
}

class Capitales(numero: Int = -1) : Scene() {
    private val fabriques = listOf(::paris, ::londres, ::washington, ::amsterdam, ::moscou)
    private val v = fabriques[if (numero in fabriques.indices) numero else Random.nextInt(fabriques.size)]()
    private val graine = Random.nextInt()       // le tirage des monuments, fixé à l'ouverture (il ne change plus ensuite)

    // Le résultat du calcul, gardé pour une taille d'écran donnée.
    private var largeur = 0; private var hauteur = 0
    private var xs = FloatArray(0); private var ys = FloatArray(0)
    private var aretes = IntArray(0)            // par groupes de 3 : nœud a, nœud b, rang
    private var ordre = IntArray(0)             // les nœuds dans l'ordre où A* les visite
    private var parent = IntArray(0)
    private var chemin = IntArray(0)
    private var depart = 0; private var but = 0
    private var nomDepart = ""; private var nomBut = ""
    private val rues = Path()

    private fun calculer(w: Float, h: Float, dp: Float) {
        largeur = w.toInt(); hauteur = h.toInt()
        val u = min(w, h)
        val marge = 0.05f * u
        // 1) Tous les tronçons de rue, en pixels : [x0, y0, x1, y1] et leur rang.
        val sx0 = ArrayList<Float>(); val sy0 = ArrayList<Float>(); val sx1 = ArrayList<Float>(); val sy1 = ArrayList<Float>(); val srang = ArrayList<Int>()
        fun ajoute(p: FloatArray, rang: Int) {
            var k = 0
            while (k + 3 < p.size) {
                val x0 = w / 2 + p[k] * u; val y0 = h / 2 + p[k + 1] * u; val x1 = w / 2 + p[k + 2] * u; val y1 = h / 2 + p[k + 3] * u
                k += 2
                if (max(x0, x1) < -marge || min(x0, x1) > w + marge || max(y0, y1) < -marge || min(y0, y1) > h + marge) continue
                sx0.add(x0); sy0.add(y0); sx1.add(x1); sy1.add(y1); srang.add(rang)
            }
        }
        for (p in v.tissu) ajoute(p, 0)
        for ((p, rang) in v.avenues) ajoute(p, rang)
        val ns = srang.size
        // 2) Les croisements. On range les tronçons dans des cases, pour ne comparer que des voisins.
        val case = 0.08f * u
        val cases = HashMap<Long, ArrayList<Int>>()
        for (s in 0 until ns) {
            for (i in floor(min(sx0[s], sx1[s]) / case).toInt()..floor(max(sx0[s], sx1[s]) / case).toInt())
                for (j in floor(min(sy0[s], sy1[s]) / case).toInt()..floor(max(sy0[s], sy1[s]) / case).toInt())
                    cases.getOrPut(i.toLong() * 100000L + j) { ArrayList() }.add(s)
        }
        val coupes = Array(ns) { arrayListOf(0f, 1f) }
        val testes = HashSet<Long>()
        for (liste in cases.values) {
            for (p in liste.indices) for (q in p + 1 until liste.size) {
                val s1 = liste[p]; val s2 = liste[q]
                if (!testes.add(s1.toLong() * ns + s2)) continue
                val rx = sx1[s1] - sx0[s1]; val ry = sy1[s1] - sy0[s1]
                val qx = sx1[s2] - sx0[s2]; val qy = sy1[s2] - sy0[s2]
                val den = rx * qy - ry * qx
                if (abs(den) < 1e-6f) continue
                val t1 = ((sx0[s2] - sx0[s1]) * qy - (sy0[s2] - sy0[s1]) * qx) / den
                val t2 = ((sx0[s2] - sx0[s1]) * ry - (sy0[s2] - sy0[s1]) * rx) / den
                if (t1 > 0f && t1 < 1f && t2 > 0f && t2 < 1f) { coupes[s1].add(t1); coupes[s2].add(t2) }
            }
        }
        // 3) Les nœuds (points fusionnés au demi-pixel près) et les arêtes.
        val index = HashMap<Long, Int>()
        val lx = ArrayList<Float>(); val ly = ArrayList<Float>()
        fun noeud(x: Float, y: Float): Int {
            val cle = Math.round(x * 2).toLong() * 1000000L + Math.round(y * 2)
            return index.getOrPut(cle) { lx.add(x); ly.add(y); lx.size - 1 }
        }
        fun dansEau(x: Float, y: Float): Boolean {
            val X = (x - w / 2) / u; val Y = (y - h / 2) / u
            if (v.iles.any { dansPoly(X, Y, it) }) return false
            if (v.eaux.any { dansPoly(X, Y, it) }) return true
            for ((p, lg) in v.fleuves) {
                var k = 0
                while (k + 3 < p.size) {
                    if (distSeg(X, Y, p[k], p[k + 1], p[k + 2], p[k + 3]) < lg / 2 + 0.006f) return true
                    k += 2
                }
            }
            return false
        }
        fun dansParc(x: Float, y: Float): Boolean {
            val X = (x - w / 2) / u; val Y = (y - h / 2) / u
            return v.parcs.any { dansPoly(X, Y, it) } || v.vides.any { dansPoly(X, Y, it) }
        }
        val la = ArrayList<Int>()
        for (s in 0 until ns) {
            val ts = coupes[s].distinct().sorted()
            var prec = -1
            for (t in ts) {
                val n = noeud(mix(sx0[s], sx1[s], t), mix(sy0[s], sy1[s], t))
                if (prec >= 0 && n != prec) {
                    val mx = (lx[n] + lx[prec]) / 2; val my = (ly[n] + ly[prec]) / 2
                    if (srang[s] > 0 || !(dansEau(mx, my) || dansParc(mx, my))) { la.add(prec); la.add(n); la.add(srang[s]) }
                }
                prec = n
            }
        }
        xs = lx.toFloatArray(); ys = ly.toFloatArray(); aretes = la.toIntArray()
        val nn = xs.size
        // les voisins de chaque nœud, avec la longueur (les avenues comptent moins : le GPS les préfère)
        val voisins = Array(nn) { ArrayList<Int>() }
        val longueurs = Array(nn) { ArrayList<Float>() }
        var k = 0
        while (k < aretes.size) {
            val a = aretes[k]; val b = aretes[k + 1]
            val l = hypot(xs[a] - xs[b], ys[a] - ys[b]) * (if (aretes[k + 2] > 0) 0.7f else 1f)
            voisins[a].add(b); longueurs[a].add(l); voisins[b].add(a); longueurs[b].add(l)
            k += 3
        }
        rues.reset()
        k = 0
        while (k < aretes.size) {
            if (aretes[k + 2] == 0) { rues.moveTo(xs[aretes[k]], ys[aretes[k]]); rues.lineTo(xs[aretes[k + 1]], ys[aretes[k + 1]]) }
            k += 3
        }
        // 4) Deux monuments au hasard, bien visibles et assez éloignés (les longs trajets d'abord).
        // visibles, et pas cachés par la barre de recherche (en haut) ni par le panneau (en bas)
        val vis = v.monuments.filter { abs(it.second) * u < w / 2 - 0.13f * u && h / 2 + it.third * u > 80f * dp && h / 2 + it.third * u < h - 130f * dp }
        fun proche(x: Float, y: Float): Int {
            val px = w / 2 + x * u; val py = h / 2 + y * u
            var meilleur = -1; var dMin = Float.MAX_VALUE
            for (n in 0 until nn) if (voisins[n].isNotEmpty()) {
                val d = (xs[n] - px) * (xs[n] - px) + (ys[n] - py) * (ys[n] - py)
                if (d < dMin) { dMin = d; meilleur = n }
            }
            return meilleur
        }
        val paires = ArrayList<Pair<Triple<String, Float, Float>, Triple<String, Float, Float>>>()
        for (a in vis) for (b in vis) if (a != b && hypot(a.second - b.second, a.third - b.third) > 0.35f) paires.add(a to b)
        paires.shuffle(Random(graine))
        paires.sortBy { if (hypot(it.first.second - it.second.second, it.first.third - it.second.third) < 0.5f) 1 else 0 }
        for ((a, b) in paires) {
            val dep = proche(a.second, a.third); val arr = proche(b.second, b.third)
            if (dep < 0 || arr < 0) continue
            if (astar(dep, arr, voisins, longueurs)) {
                depart = dep; but = arr; nomDepart = a.first; nomBut = b.first
                return
            }
        }
    }

    // A* : on sort toujours le nœud le plus prometteuse (distance parcourue + 0,35 × distance restante).
    // 0,35 au lieu de 1 : A* explore plus large, et on voit mieux l'arbre de recherche pousser.
    private fun astar(dep: Int, arr: Int, voisins: Array<ArrayList<Int>>, longueurs: Array<ArrayList<Float>>): Boolean {
        val nn = xs.size
        val bx = xs[arr]; val by = ys[arr]
        fun h(n: Int) = hypot(xs[n] - bx, ys[n] - by)
        val cout = FloatArray(nn) { Float.MAX_VALUE }
        val par = IntArray(nn) { -1 }
        val vus = BooleanArray(nn)
        val visites = ArrayList<Int>()
        val file = PriorityQueue<FloatArray>(compareBy<FloatArray> { it[0] })
        cout[dep] = 0f
        file.add(floatArrayOf(0.35f * h(dep), dep.toFloat()))
        while (file.isNotEmpty()) {
            val n = file.poll()!![1].toInt()
            if (vus[n]) continue
            vus[n] = true; visites.add(n)
            if (n == arr) {
                ordre = visites.toIntArray(); parent = par
                val trajet = ArrayList<Int>()
                var m = arr
                while (m != -1) { trajet.add(m); m = par[m] }
                chemin = trajet.reversed().toIntArray()
                return true
            }
            for (j in voisins[n].indices) {
                val m = voisins[n][j]
                val g = cout[n] + longueurs[n][j]
                if (g < cout[m]) { cout[m] = g; par[m] = n; file.add(floatArrayOf(g + 0.35f * h(m), m.toFloat())) }
            }
        }
        return false
    }

    // Écrit un texte aligné à gauche ; renvoie sa largeur.
    private fun ecrire(c: Canvas, s: String, x: Float, y: Float, taille: Float, couleur: Int): Float {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.textSize = taille; p.color = couleur
        c.drawText(s, x, y, p)
        return p.measureText(s)
    }

    // DEUX HABILLAGES (voir build.gradle.kts : les versions « perso » et « store ») :
    // - version Perso : le style de Google Maps en mode sombre (barre de recherche, panneau « Démarrer »).
    // - version Store : la MÊME carte et le même A*, mais un habillage original, pour ne pas imiter
    //   l'interface d'une autre app : le nom de la destination écrit sur la carte, un petit compteur
    //   néon en bas, un itinéraire violet et un repère ambre.
    private val store = BuildConfig.STORE
    private val bleu = if (store) rgb(150, 110, 245) else rgb(66, 133, 244)        // itinéraire, point de départ
    private val bleuFonce = if (store) rgb(55, 35, 120) else rgb(24, 70, 160)      // le liseré de l'itinéraire
    private val rougeEpingle = if (store) rgb(255, 170, 70) else rgb(234, 67, 53)
    private val coeurEpingle = if (store) rgb(110, 60, 10) else rgb(120, 20, 15)
    private val neon = rgb(110, 225, 235)                                          // le compteur de la version Store

    // Le dessin, sans rien qui clignote.
    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val rev = i.revelation
        val t = sec * 1.25f                          // l'horloge de l'histoire : tout est joué en 0,9 s environ
        val w = c.width.toFloat(); val h = c.height.toFloat()
        if (w.toInt() != largeur || h.toInt() != hauteur) calculer(w, h, dp)
        val u = min(w, h)
        fun ligne(p: FloatArray, ferme: Boolean = false): Path {
            val chemin = Path()
            var k = 0
            while (k + 1 < p.size) {
                val x = w / 2 + p[k] * u; val y = h / 2 + p[k + 1] * u
                if (k == 0) chemin.moveTo(x, y) else chemin.lineTo(x, y)
                k += 2
            }
            if (ferme) chemin.close()
            return chemin
        }
        val app = ralentit(borne(t / 0.3f))        // la carte apparaît en douceur
        val a = 1f - rev
        val ex = xs[but]; val ey = ys[but]
        val dx = xs[depart]; val dy = ys[depart]
        c.drawColor(rgb(24, 27, 33))
        val zoom = (1f + 0.04f * borne(t / 1.5f)) * (1f + 3f * accelere(rev))   // léger rapprochement, puis plongée
        c.save(); c.translate(ex, ey); c.scale(zoom, zoom); c.translate(-ex, -ey)

        // --- le fond de carte : parcs, eau, rues ---
        for (p in v.parcs) c.drawPath(ligne(p, true), o.pinceau(avecAlpha(rgb(30, 52, 42), app)))
        val eau = rgb(18, 34, 58)
        for (p in v.eaux) c.drawPath(ligne(p, true), o.pinceau(avecAlpha(eau, app)))
        for ((p, lg) in v.fleuves) {
            val pe = o.pinceau(avecAlpha(eau, app), lg * u)
            pe.strokeJoin = Paint.Join.ROUND
            c.drawPath(ligne(p), pe)
        }
        for (p in v.iles) c.drawPath(ligne(p, true), o.pinceau(avecAlpha(rgb(24, 27, 33), app)))
        c.drawPath(rues, o.pinceau(avecAlpha(rgb(48, 54, 66), app), 1.6f * dp))
        for ((p, rang) in v.avenues) {               // les avenues en gris, les grands axes en ambre
            val pa = o.pinceau(avecAlpha(if (rang == 1) rgb(70, 77, 92) else rgb(120, 98, 68), app), (if (rang == 1) 2.6f else 3.6f) * dp)
            pa.strokeJoin = Paint.Join.ROUND
            c.drawPath(ligne(p), pa)
        }
        // les monuments : un petit rond et leur nom, comme sur la carte
        val taille = 0.026f * u
        for ((nom, mx, my) in v.monuments) {
            val x = w / 2 + mx * u; val y = h / 2 + my * u
            c.drawCircle(x, y, 3.2f * dp, o.pinceau(avecAlpha(rgb(120, 130, 150), 0.8f * app)))
            texte(c, nom, x, y + 11f * dp, taille, avecAlpha(rgb(140, 148, 165), 0.75f * app))
        }

        // --- 1) A* cherche : un arbre fin et discret, qui s'efface quand le trajet est trouvé ---
        val explore = doux(borne((t - 0.15f) / 0.5f)) * ordre.size
        val arbre = Path()
        for (j in ordre.indices) {
            if (j > explore) break
            val n = ordre[j]; val p = parent[n]
            if (p >= 0) {
                val f = borne(explore - j)
                arbre.moveTo(xs[p], ys[p]); arbre.lineTo(mix(xs[p], xs[n], f), mix(ys[p], ys[n], f))
            }
        }
        val efface = 1f - borne((t - 0.75f) / 0.3f)
        c.drawPath(arbre, o.pinceau(avecAlpha(rgb(110, 160, 230), 0.5f * efface * a), 1.4f * dp))

        // --- 2) l'itinéraire, avec son liseré (bleu en Perso, violet en Store) ---
        val trace = doux(borne((t - 0.55f) / 0.35f))
        if (trace > 0f && chemin.size > 1) {
            val ch = Path()
            val nb = (chemin.size - 1) * trace
            val kk = nb.toInt()
            ch.moveTo(xs[chemin[0]], ys[chemin[0]])
            for (q in 1..kk) ch.lineTo(xs[chemin[q]], ys[chemin[q]])
            if (kk < chemin.size - 1) ch.lineTo(mix(xs[chemin[kk]], xs[chemin[kk + 1]], nb - kk), mix(ys[chemin[kk]], ys[chemin[kk + 1]], nb - kk))
            for ((ep, col) in listOf(8.5f to bleuFonce, 6f to bleu)) {
                val l = o.pinceau(avecAlpha(col, a), ep * dp)
                l.strokeJoin = Paint.Join.ROUND
                c.drawPath(ch, l)
            }
        }
        // --- le point bleu « vous êtes ici », qui respire doucement ---
        val pouls = 0.5f + 0.5f * sin(sec * 3f)
        c.drawCircle(dx, dy, (14f + 4f * pouls) * dp, o.pinceau(avecAlpha(bleu, 0.18f * app * a)))
        c.drawCircle(dx, dy, 7.5f * dp, o.pinceau(avecAlpha(rgb(255, 255, 255), app * a)))
        c.drawCircle(dx, dy, 5.5f * dp, o.pinceau(avecAlpha(bleu, app * a)))
        // --- 3) l'épingle rouge tombe sur la destination ---
        val chute = ralentit(borne((t - 0.85f) / 0.2f))
        if (chute > 0f) {
            c.drawOval(RectF(ex - 6f * dp, ey - 2f * dp, ex + 6f * dp, ey + 2f * dp), o.pinceau(argb(110f * chute * a, 0, 0, 0)))
            val yb = ey - (1f - chute) * 60f * dp
            val corps = o.pinceau(avecAlpha(rougeEpingle, chute * a))
            c.drawCircle(ex, yb - 22f * dp, 11f * dp, corps)
            val pied = Path(); pied.moveTo(ex - 9.5f * dp, yb - 17f * dp); pied.lineTo(ex, yb); pied.lineTo(ex + 9.5f * dp, yb - 17f * dp); pied.close()
            c.drawPath(pied, corps)
            c.drawCircle(ex, yb - 22f * dp, 4f * dp, o.pinceau(avecAlpha(coeurEpingle, chute * a)))
        }
        c.restore()

        if (store) {
            habillageStore(c, o, t, a, w, h, u, dp)
            o.vignette(c, w, h, 0.35f)
            o.revelationDouce(c, ex, ey, rev, bleu)
            return
        }

        // --- l'interface : la barre de recherche en haut ---
        val barre = ralentit(borne((t - 0.05f) / 0.3f))
        if (barre > 0f) {
            val bw = min(w - 32f * dp, 420f * dp); val bh = 46f * dp
            val bx = (w - bw) / 2; val by = 14f * dp - (1f - barre) * 70f * dp
            c.drawRoundRect(RectF(bx, by + 2f * dp, bx + bw, by + bh + 2f * dp), bh / 2, bh / 2, o.pinceau(argb(90f * a, 0, 0, 0)))   // l'ombre
            c.drawRoundRect(RectF(bx, by, bx + bw, by + bh), bh / 2, bh / 2, o.pinceau(avecAlpha(rgb(48, 52, 60), a)))
            val gris = avecAlpha(rgb(200, 205, 215), a)
            c.drawCircle(bx + 24f * dp, by + bh / 2 - 1f * dp, 6f * dp, o.pinceau(gris, 2f * dp))                         // la loupe
            c.drawLine(bx + 28.5f * dp, by + bh / 2 + 3.5f * dp, bx + 33f * dp, by + bh / 2 + 8f * dp, o.pinceau(gris, 2f * dp))
            val tape = (nomBut.length * borne((t - 0.15f) / 0.4f)).toInt()                     // le nom se tape lettre par lettre
            val lg = ecrire(c, nomBut.substring(0, tape), bx + 46f * dp, by + bh / 2 + 5f * dp, 15f * dp, avecAlpha(rgb(235, 238, 245), a))
            if (tape < nomBut.length && (sec * 6f).toInt() % 2 == 0) {                         // le curseur
                val xc = bx + 46f * dp + lg + 2f * dp
                c.drawLine(xc, by + bh / 2 - 8f * dp, xc, by + bh / 2 + 8f * dp, o.pinceau(avecAlpha(rgb(66, 133, 244), a), 1.5f * dp))
            }
            c.drawCircle(bx + bw - 23f * dp, by + bh / 2, 14f * dp, o.pinceau(avecAlpha(rgb(80, 90, 110), a)))           // la pastille du profil
        }
        // --- le panneau du bas : durée, distance, bouton « Démarrer » ---
        val panneau = ralentit(borne((t - 0.85f) / 0.3f))
        if (panneau > 0f && chemin.size > 1) {
            var lg = 0f
            for (j in 0 until chemin.size - 1) lg += hypot(xs[chemin[j + 1]] - xs[chemin[j]], ys[chemin[j + 1]] - ys[chemin[j]])
            val km = lg / u * 4.2f                                                               // 1 écran ≈ 4 km
            val minutes = max(3, Math.round(km * 3.2f))
            val ph = 104f * dp
            val py0 = h - ph * panneau
            c.drawRoundRect(RectF(0f, py0, w, h + 30f * dp), 22f * dp, 22f * dp, o.pinceau(avecAlpha(rgb(40, 44, 52), a)))
            c.drawRoundRect(RectF(w / 2 - 18f * dp, py0 + 8f * dp, w / 2 + 18f * dp, py0 + 12f * dp), 2f * dp, 2f * dp, o.pinceau(avecAlpha(rgb(90, 96, 108), a)))
            val gris = avecAlpha(rgb(170, 175, 185), a)
            val lm = ecrire(c, "$minutes min", 22f * dp, py0 + 44f * dp, 20f * dp, avecAlpha(rgb(110, 200, 140), a))
            ecrire(c, "(" + String.format("%.1f", km).replace('.', ',') + " km)", 22f * dp + lm + 8f * dp, py0 + 44f * dp, 13f * dp, gris)
            ecrire(c, "Itinéraire le plus rapide", 22f * dp, py0 + 66f * dp, 13f * dp, gris)
            val bw2 = 110f * dp
            c.drawRoundRect(RectF(w - bw2 - 20f * dp, py0 + 26f * dp, w - 20f * dp, py0 + 62f * dp), 18f * dp, 18f * dp, o.pinceau(avecAlpha(rgb(66, 133, 244), a)))
            val p = Paint(Paint.ANTI_ALIAS_FLAG); p.textSize = 14f * dp
            ecrire(c, "Démarrer", w - bw2 / 2 - 20f * dp - p.measureText("Démarrer") / 2, py0 + 49f * dp, 14f * dp, avecAlpha(rgb(20, 24, 32), a))
        }
        o.vignette(c, w, h, 0.35f)
        o.revelationDouce(c, ex, ey, rev, rgb(66, 133, 244))
    }

    // L'HABILLAGE DE LA VERSION STORE : rien qui ressemble à l'interface d'une app existante.
    private fun habillageStore(c: Canvas, o: Outils, t: Float, a: Float, w: Float, h: Float, u: Float, dp: Float) {
        // 1) En haut : le nom de la destination s'écrit en grandes lettres fines et espacées,
        //    sous un petit mot « DESTINATION », avec un trait qui se dessine en dessous.
        val entree = ralentit(borne((t - 0.05f) / 0.3f))
        if (entree > 0f) {
            val haut = 56f * dp - (1f - entree) * 20f * dp
            val petit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 10f * dp; letterSpacing = 0.35f; textAlign = Paint.Align.CENTER
                color = avecAlpha(rgb(150, 158, 175), entree * a)
            }
            c.drawText("DESTINATION", w / 2, haut, petit)
            val titre = nomBut.uppercase()
            val tape = (titre.length * borne((t - 0.15f) / 0.4f)).toInt()          // lettre par lettre
            val grand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 22f * dp; letterSpacing = 0.12f; textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
                color = avecAlpha(rgb(235, 238, 245), a)
            }
            // on mesure le titre COMPLET pour qu'il ne bouge pas pendant qu'il s'écrit
            val largeurTitre = grand.measureText(titre)
            grand.textAlign = Paint.Align.LEFT
            c.drawText(titre.substring(0, tape), w / 2 - largeurTitre / 2, haut + 30f * dp, grand)
            // le trait sous le titre : il part du centre et s'étire vers les deux côtés
            val trait = doux(borne((t - 0.3f) / 0.5f)) * (largeurTitre / 2 + 12f * dp)
            if (trait > 0f) c.drawLine(w / 2 - trait, haut + 42f * dp, w / 2 + trait, haut + 42f * dp,
                o.pinceau(avecAlpha(bleu, 0.8f * a), 1.2f * dp))
        }
        // 2) En bas à gauche : un petit compteur néon (durée · distance), sans panneau ni bouton.
        val compteur = ralentit(borne((t - 0.85f) / 0.3f))
        if (compteur > 0f && chemin.size > 1) {
            var lg = 0f
            for (j in 0 until chemin.size - 1) lg += hypot(xs[chemin[j + 1]] - xs[chemin[j]], ys[chemin[j + 1]] - ys[chemin[j]])
            val km = lg / u * 4.2f                                                    // 1 écran ≈ 4 km
            val minutes = max(3, Math.round(km * 3.2f))
            val y = h - 40f * dp + (1f - compteur) * 16f * dp
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 15f * dp; letterSpacing = 0.18f
                typeface = android.graphics.Typeface.MONOSPACE
                color = avecAlpha(neon, compteur * a)
            }
            val texteCompteur = "$minutes MIN · " + String.format("%.1f", km).replace('.', ',') + " KM"
            // un halo doux derrière le texte (pas de flash : faible opacité, apparition progressive)
            val halo = Paint(p).apply {
                color = avecAlpha(neon, 0.25f * compteur * a)
                maskFilter = android.graphics.BlurMaskFilter(6f * dp, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            // (le flou n'est géré par la puce graphique qu'à partir d'Android 9 : avant, pas de halo)
            if (android.os.Build.VERSION.SDK_INT >= 28) c.drawText(texteCompteur, 24f * dp, y, halo)
            c.drawText(texteCompteur, 24f * dp, y, p)
        }
    }
}
