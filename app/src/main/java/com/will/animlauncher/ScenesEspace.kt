package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.Base64
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

// CHATGPT, LOT 2 : l'espace et le lancement.

// ===================== CHATGPT : « Collision » =====================
// La Voie lactée et Andromède se rencontrent (dans ~4,5 milliards d'années).
// Une vraie petite simulation : chaque galaxie = un noyau massif + des étoiles en orbite, rangées
// le long de 2 bras spiraux. Les deux noyaux s'attirent ; chaque étoile est attirée par les deux.
// Au passage, la gravité arrache de longues QUEUES DE MARÉE.
// Le calcul est lourd (1 800 étoiles x 800 étapes) : on le fait UNE FOIS, en arrière-plan, dès le
// démarrage du service, puis la scène « rejoue le film ».
object FilmCollision {
    private const val M1 = 1.0; private const val M2 = 1.35
    private const val EPS = 0.18                 // « adoucissement » : évite des forces infinies près d'un noyau
    private const val DT = 0.012; private const val ETAPES = 800; private const val TOUS_LES = 4
    const val N1 = 800; const val N2 = 1000; const val NB = N1 + N2
    const val IMAGES = ETAPES / TOUS_LES
    val masse1 = M1.toFloat(); val masse2 = M2.toFloat()

    // Le film, déjà « projeté » en 2D (vue de biais, en diagonale), centré sur le centre de masse :
    // pour chaque image, x et y de chaque étoile ; et les positions des deux noyaux.
    val xy = FloatArray(IMAGES * NB * 2)
    val noyaux = FloatArray(IMAGES * 4)
    // 3 niveaux d'éclat : beaucoup d'étoiles discrètes, quelques-unes brillantes (évite l'effet « farine »)
    val niveau = ByteArray(NB).also { n ->
        val r = Random(3)
        for (q in 0 until NB) { val x = r.nextFloat(); n[q] = (if (x < 0.62f) 0 else if (x < 0.9f) 1 else 2).toByte() }
    }
    @Volatile var pret = false
        private set
    private var lance = false

    fun preparer() {
        synchronized(this) { if (lance) return; lance = true }
        thread(name = "film-collision", priority = Thread.MIN_PRIORITY) { calculer() }
    }

    // Un tirage « en cloche » (loi normale) à partir de deux tirages au hasard (méthode de Box-Muller).
    private fun gauss(r: Random): Double = sqrt(-2.0 * ln(1.0 - r.nextDouble())) * cos(2 * PI * r.nextDouble())

    // Des étoiles en orbite circulaire autour d'un noyau, rangées le long de 2 bras spiraux,
    // dans un disque incliné. On remplit pos et vit à partir de l'indice "debut".
    private fun disque(pos: DoubleArray, vit: DoubleArray, debut: Int, n: Int, masse: Double, rmin: Double, rmax: Double,
                       incl: Double, noeud: Double, graine: Int, sens: Int) {
        val r = Random(graine)
        val ci = cos(Math.toRadians(incl)); val si = sin(Math.toRadians(incl))
        val cn = cos(Math.toRadians(noeud)); val sn = sin(Math.toRadians(noeud))
        for (q in 0 until n) {
            val rr = rmin + (rmax - rmin) * r.nextDouble().pow(0.8)
            val th = (q % 2) * PI + ln(rr / rmin) / tan(Math.toRadians(22.0)) + gauss(r) * 0.25
            val x = rr * cos(th); val y = rr * sin(th)
            val v = sqrt(masse * rr * rr / (rr * rr + EPS * EPS).pow(1.5)) * sens
            val vx = -v * sin(th); val vy = v * cos(th)
            val k = (debut + q) * 3
            // on incline le disque (autour de l'axe x), puis on le tourne (autour de z)
            pos[k] = x * cn - y * ci * sn; pos[k + 1] = x * sn + y * ci * cn; pos[k + 2] = y * si
            vit[k] = vx * cn - vy * ci * sn; vit[k + 1] = vx * sn + vy * ci * cn; vit[k + 2] = vy * si
        }
    }

    private fun calculer() {
        val P = DoubleArray(NB * 3); val V = DoubleArray(NB * 3)
        disque(P, V, 0, N1, M1, 0.2, 1.5, 30.0, 20.0, 1, +1)
        disque(P, V, N1, N2, M2, 0.2, 1.8, 48.0, -40.0, 2, -1)
        // les deux noyaux : séparés, en approche (trop lents pour s'échapper : elles vont fusionner)
        val c1 = doubleArrayOf(-2.0, -0.6, 0.0); val c2 = doubleArrayOf(2.0, 0.6, 0.0)
        val v1 = doubleArrayOf(0.18, 0.0, 0.03); val v2 = DoubleArray(3) { -v1[it] * M1 / M2 }
        for (q in 0 until NB) for (d in 0..2) {
            P[q * 3 + d] += if (q < N1) c1[d] else c2[d]
            V[q * 3 + d] += if (q < N1) v1[d] else v2[d]
        }
        // l'accélération d'une étoile, attirée par les deux noyaux
        fun accelerer(dt: Double) {
            for (q in 0 until NB) {
                val k = q * 3
                var ax = 0.0; var ay = 0.0; var az = 0.0
                for (g in 0..1) {
                    val c = if (g == 0) c1 else c2; val m = if (g == 0) M1 else M2
                    val dx = c[0] - P[k]; val dy = c[1] - P[k + 1]; val dz = c[2] - P[k + 2]
                    val r2 = dx * dx + dy * dy + dz * dz + EPS * EPS
                    val f = m / (r2 * sqrt(r2))
                    ax += dx * f; ay += dy * f; az += dz * f
                }
                V[k] += ax * dt; V[k + 1] += ay * dt; V[k + 2] += az * dt
            }
        }
        // la projection : vue légèrement inclinée, puis l'axe de la collision mis en diagonale
        val ang = Math.toRadians(62.0); val ca = cos(ang); val sa = sin(ang)
        val c05 = cos(0.5); val s05 = sin(0.5)
        fun proj(x: Double, y: Double, z: Double, out: FloatArray, at: Int) {
            val y2 = y * c05 - z * s05
            out[at] = (x * ca - y2 * sa).toFloat(); out[at + 1] = (x * sa + y2 * ca).toFloat()
        }
        var image = 0
        for (e in 0 until ETAPES) {
            // « saute-mouton » (leapfrog) : une méthode simple et stable pour les orbites
            accelerer(DT * 0.5)
            for (k in 0 until NB * 3) P[k] += V[k] * DT
            val dx = c2[0] - c1[0]; val dy = c2[1] - c1[1]; val dz = c2[2] - c1[2]
            val r2 = dx * dx + dy * dy + dz * dz + EPS * EPS; val r3 = r2 * sqrt(r2)
            v1[0] += M2 * dx / r3 * DT; v1[1] += M2 * dy / r3 * DT; v1[2] += M2 * dz / r3 * DT
            v2[0] -= M1 * dx / r3 * DT; v2[1] -= M1 * dy / r3 * DT; v2[2] -= M1 * dz / r3 * DT
            for (d in 0..2) { c1[d] += v1[d] * DT; c2[d] += v2[d] * DT }
            accelerer(DT * 0.5)
            if (e % TOUS_LES == 0 && image < IMAGES) {
                // la caméra suit le centre de masse des deux noyaux
                val g = DoubleArray(3) { (M1 * c1[it] + M2 * c2[it]) / (M1 + M2) }
                for (q in 0 until NB) {
                    val k = q * 3
                    proj(P[k] - g[0], P[k + 1] - g[1], P[k + 2] - g[2], xy, (image * NB + q) * 2)
                }
                proj(c1[0] - g[0], c1[1] - g[1], c1[2] - g[2], noyaux, image * 4)
                proj(c2[0] - g[0], c2[1] - g[1], c2[2] - g[2], noyaux, image * 4 + 2)
                image++
            }
        }
        pret = true
    }
}

class Collision : Scene() {
    private val teinte = intArrayOf(rgb(200, 220, 255), rgb(255, 228, 200))   // Voie lactée bleutée, Andromède plus chaude
    private val etoiles = nuage(51, 160)
    private val eclats = floatArrayOf(0.30f, 0.55f, 0.90f)      // opacité de chaque niveau
    private val tailles = floatArrayOf(1.2f, 1.7f, 2.3f)        // taille de chaque niveau (en « k »)
    // des tableaux de points prêts à l'emploi : un par galaxie et par niveau d'éclat
    private val tampons = Array(6) { FloatArray(FilmCollision.NB * 2) }
    private val remplis = IntArray(6)

    init { FilmCollision.preparer() }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val sec = i.secondes; val w = c.width.toFloat(); val h = c.height.toFloat()
        val k = w / 540f
        val app = ralentit(i.intro); val a = 1f - i.revelation
        val fond = o.pinceau()
        fond.shader = LinearGradient(0f, 0f, 0f, h, rgb(3, 4, 9), rgb(8, 8, 16), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fond)
        o.poussieres(c, etoiles, sec, w, h, rgb(220, 225, 240), 0.45f * app * a, 0f, 0f)

        // où en est le film : il avance vite au début, puis ralentit quand les galaxies fusionnent
        val avance = 1f - (1f - borne(sec / 3.2f)).pow(2.4f)
        val zoom = (0.92f + 0.08f * app) * (1f + 1.6f * accelere(i.revelation))
        val e = w * 0.19f * zoom
        val ox = w / 2f; val oy = h * 0.47f
        var x1 = ox; var y1 = oy; var x2 = ox; var y2 = oy
        if (FilmCollision.pret) {
            val f = avance * (FilmCollision.IMAGES - 1)
            val i0 = f.toInt(); val i1 = min(i0 + 1, FilmCollision.IMAGES - 1); val t = f - i0
            val nb = FilmCollision.NB
            val n = FilmCollision.noyaux
            x1 = ox + mix(n[i0 * 4], n[i1 * 4], t) * e; y1 = oy + mix(n[i0 * 4 + 1], n[i1 * 4 + 1], t) * e
            x2 = ox + mix(n[i0 * 4 + 2], n[i1 * 4 + 2], t) * e; y2 = oy + mix(n[i0 * 4 + 3], n[i1 * 4 + 3], t) * e
            // les lueurs diffuses des deux galaxies (douces) ; au début, un « corps » plus présent
            val corps = 1f - 0.6f * borne(avance / 0.6f)
            o.halo(c, x1, y1, 75f * k * zoom, (0.06f + 0.10f * corps) * app * a, teinte[0])
            o.halo(c, x1, y1, 32f * k * zoom, (0.18f + 0.14f * corps) * app * a, rgb(255, 240, 220))
            o.halo(c, x2, y2, 75f * k * 1.15f * zoom, (0.06f + 0.10f * corps) * app * a, teinte[1])
            o.halo(c, x2, y2, 32f * k * 1.15f * zoom, (0.18f + 0.14f * corps) * app * a, rgb(255, 240, 220))
            // on range chaque étoile dans le bon tableau (galaxie x niveau d'éclat)
            remplis.fill(0)
            val xy = FilmCollision.xy
            for (q in 0 until nb) {
                val b = (if (q < FilmCollision.N1) 0 else 3) + FilmCollision.niveau[q]
                val a0 = (i0 * nb + q) * 2; val a1 = (i1 * nb + q) * 2
                val tab = tampons[b]; val m = remplis[b]
                tab[m] = ox + mix(xy[a0], xy[a1], t) * e
                tab[m + 1] = oy + mix(xy[a0 + 1], xy[a1 + 1], t) * e
                remplis[b] = m + 2
            }
            for (b in 0 until 6) {
                val g = b / 3; val niv = b % 3
                if (niv == 0) {      // une lueur très faible sous les étoiles discrètes : les queues ressortent
                    val lueur = o.lumiere(avecAlpha(teinte[g], 0.03f * app * a), 5f * k)
                    c.drawPoints(tampons[b], 0, remplis[b], lueur)
                }
                val p = o.lumiere(avecAlpha(teinte[g], eclats[niv] * app * a), tailles[niv] * k)
                c.drawPoints(tampons[b], 0, remplis[b], p)
            }
            // les deux noyaux, compacts et chauds
            val pn = o.pinceau(avecAlpha(rgb(255, 245, 230), 0.9f * app * a))
            c.drawCircle(x1, y1, 2.6f * k * zoom, pn); c.drawCircle(x2, y2, 2.6f * k * zoom, pn)
        }
        o.vignette(c, w, h, 0.6f)
        o.revelationDouce(c, (x1 + x2) / 2f, (y1 + y2) / 2f, i.revelation, rgb(220, 225, 255))
    }
}

// ===================== CHATGPT : « Lancement » (classe GrandPublic) =====================
// Le jour où un grand modèle de langage est devenu accessible à tout le monde.
// 1) Dans le noir, une réponse s'écrit « morceau par morceau » (des pilules = les tokens) + un curseur.
// 2) Elle se condense en un point de lumière qui se pose sur une Terre en pointillés, sur la côte
//    ouest des États-Unis, là où tout a commencé.
// 3) Des arcs partent vers les grandes villes et la lumière gagne tous les continents.
// Révélation : une trame de points qui s'ouvre depuis ce point de départ.
object Terre {
    // Les points de la Terre : 5 200 points répartis régulièrement sur une sphère (« spirale de
    // Fibonacci ») ; ce texte dit, pour chacun, s'il tombe sur la terre ferme (1 bit par point).
    // Il a été calculé une fois avec une carte du monde.
    private const val CARTE = "AABASAgJIQEhYAAMBIADM3NgZk3EtrzWtvf+zt/fe3t/f+XtvL2Vufv3Vv7OysqZOSkzZ+Tk3JyQnTNzcm5OyO25OT0/p+bm3Jyek1Nzc05Oz8mpOT0np+bk3Iyam1Mzck5Gz8mZGTUnp2bUjIyY0xNyak5OzKkZMTknJ+TUjJiYExNyckZMzcgJMTUjJmTUhJiS0RMyak5MyagJMSWnpuTUlJiSU1JyakpJySkoMSWlpOQUlJKSU1IiKkpJwSkoNQWnpMCUlJICUlIqakpJkSkpJRWkpsCQkJICUlIoCEhNASEpFQSkpLAQkBoCQlFICEhNASEoBQSEopAQkAICQlFICAgBJSEgJRSEgLIQEBIKQkFJKAgJRSGgJBSEgKJQkJIKAkBJKioBBSGgIBQUgIJAVIAKAkBBKCoABYGoABUEgIJQVIAKAlEBKCgABYGoCBUVgAJQUIQKCkEBKSgCBYSggBwUgwJiUAAKiEEBKSgCBcSgABQUgQJSUAAKCMEBKCgABYSgABQUgCJQUAAKCEHRKCgCRaSgABQVgKJQUACKCEARKCICRSGgCBQRgCJQRASKCkARKCICR6GoCBQRoSJQRASKglARKAJCRaCICBQBoSJQBASKgBERKAJCRaAICBQBISJQBASKgBEQKAJCRKAICBQAIyBQAAQIAAEQIAACAIAACBAAAiBAAAQAAIEQAAAEAIAAAAAAAgAAAAAAAAEAAAAEAgAAAAAAAgAAAAgAAAAAAAAEAAAAEAAAAAAAAAgAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgIASEloKSElJTS0lpaSk/v+T0/7/f3/fz+//8="
    const val NPTS = 5200
    // Pour chaque point de terre : latitude, longitude (en radians).
    val points: FloatArray by lazy {
        val bits = Base64.decode(CARTE, Base64.DEFAULT)
        val l = ArrayList<Float>()
        for (n in 0 until NPTS) {
            if ((bits[n / 8].toInt() shr (n % 8)) and 1 == 1) {
                val lat = asin(1.0 - 2.0 * (n + 0.5) / NPTS)
                val lon = (n * 2.39996323) % (2 * PI) - PI
                l.add(lat.toFloat()); l.add(lon.toFloat())
            }
        }
        l.toFloatArray()
    }
}

class GrandPublic : Scene() {
    private val menthe = rgb(120, 215, 180)
    private val pale = rgb(225, 240, 235)
    private val poussiere = nuage(77, 45)
    private val r = Random(3)
    private val brillance = FloatArray(Terre.points.size / 2) { r.nextFloat() }
    private val depart = floatArrayOf(Math.toRadians(37.7).toFloat(), Math.toRadians(-122.4).toFloat())
    private val villes = listOf(40.7 to -74.0, 19.4 to -99.1, -23.5 to -46.6, 51.5 to -0.1, 48.9 to 2.3, 52.5 to 13.4,
        6.5 to 3.4, 30.0 to 31.2, 28.6 to 77.2, 35.7 to 139.7, 37.6 to 127.0, -33.9 to 151.2,
        55.8 to 37.6, -34.6 to -58.4, 1.35 to 103.8).map { floatArrayOf(Math.toRadians(it.first).toFloat(), Math.toRadians(it.second).toFloat()) }
    private val t0 = 0.70f                        // le moment où la lumière touche la Terre
    private val largeurs = floatArrayOf(34f, 52f, 26f, 60f, 40f, 30f)   // les « tokens » de la réponse

    // Distance sur la sphère (en radians) entre deux points (lat, lon).
    private fun distance(la1: Float, lo1: Float, la2: Float, lo2: Float): Float =
        acos(borne(sin(la1) * sin(la2) + cos(la1) * cos(la2) * cos(lo1 - lo2), -1f, 1f))

    // Quand un point s'allume : plus il est loin du départ, plus il s'allume tard.
    private fun activation(d: Float, bruit: Float) = t0 + 0.85f * (d / PI.toFloat()).pow(0.8f) + 0.10f * bruit

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val sec = i.secondes; val w = c.width.toFloat(); val h = c.height.toFloat()
        val k = w / 540f
        val app = ralentit(i.intro); val a = 1f - i.revelation
        val fond = o.pinceau()
        fond.shader = LinearGradient(0f, 0f, 0f, h, rgb(6, 8, 10), rgb(10, 14, 16), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fond)
        o.poussieres(c, poussiere, sec, w, h, pale, 0.20f * app * a, 0f, -0.01f)

        val cx = w / 2f; val cy = h * 0.48f
        val rr = 0.40f * w * (1f + 0.12f * doux(i.revelation))
        // la Terre tourne lentement, et on la voit un peu de dessus
        val l0 = Math.toRadians(-92.0 + sec * 9.0).toFloat()
        val cl = cos(-l0); val sl = sin(-l0)
        val ct = cos(Math.toRadians(22.0).toFloat()); val st = sin(Math.toRadians(22.0).toFloat())
        val sortie = FloatArray(3)
        // 3D (x, y, z sur la sphère) -> écran : rotation autour des pôles, puis inclinaison
        fun projeter(x0: Float, y0: Float, z: Float) {
            val x = x0 * cl - y0 * sl; val y = x0 * sl + y0 * cl
            val prof = x * ct + z * st              // > 0 : face visible
            val haut = -x * st + z * ct
            sortie[0] = cx + y * rr; sortie[1] = cy - haut * rr; sortie[2] = prof
        }
        fun projeterLL(lat: Float, lon: Float) = projeter(cos(lat) * cos(lon), cos(lat) * sin(lon), sin(lat))
        val globeA = ralentit(borne((sec - 0.30f) / 0.45f)) * a

        // 1) la Terre : un océan à peine plus clair que le fond, puis les continents en pointillés
        o.halo(c, cx, cy, rr * 1.25f, 0.07f * globeA, menthe)
        val ocean = o.pinceau()
        ocean.shader = RadialGradient(cx - rr * 0.3f, cy - rr * 0.35f, rr * 1.5f, rgb(20, 32, 34), rgb(9, 14, 16), Shader.TileMode.CLAMP)
        ocean.alpha = (255 * globeA).toInt()
        c.drawCircle(cx, cy, rr, ocean)
        val sol = o.pinceau(); val clair = o.lumiere()
        val pts = Terre.points
        for (n in 0 until pts.size / 2) {
            val lat = pts[n * 2]; val lon = pts[n * 2 + 1]
            projeterLL(lat, lon)
            if (sortie[2] <= 0f) continue
            val bord = borne(sortie[2] * 2.2f)           // les points du bord s'estompent
            val br = brillance[n]
            val allume = ralentit((sec - activation(distance(lat, lon, depart[0], depart[1]), br)) / 0.25f)
            sol.color = avecAlpha(rgb(80, 92, 100), (0.60f - 0.25f * allume) * bord * globeA)
            c.drawCircle(sortie[0], sortie[1], 1.7f * k, sol)
            if (allume > 0f) {
                clair.color = avecAlpha(menthe, (0.45f + 0.40f * br) * allume * bord * globeA)
                c.drawCircle(sortie[0], sortie[1], 1.7f * k, clair)
            }
        }

        // 2) les arcs vers les villes : ils montent au-dessus de la Terre puis redescendent
        val vd = floatArrayOf(cos(depart[0]) * cos(depart[1]), cos(depart[0]) * sin(depart[1]), sin(depart[0]))
        val arc = FloatArray(25 * 3)
        for ((n, v) in villes.withIndex()) {
            val d = distance(depart[0], depart[1], v[0], v[1])
            val debut = t0 + 0.05f + 0.32f * (d / PI.toFloat()) + 0.02f * n
            val f = borne((sec - debut) / 0.38f)
            if (f <= 0f) continue
            val vv = floatArrayOf(cos(v[0]) * cos(v[1]), cos(v[0]) * sin(v[1]), sin(v[0]))
            for (s in 0..24) {
                val u = s / 24f * ralentit(f)
                // « slerp » : on glisse sur la sphère du départ à l'arrivée, en prenant de l'altitude
                val sa = sin((1 - u) * d) / sin(d); val sb = sin(u * d) / sin(d)
                val alt = 1f + 0.18f * (d / PI.toFloat()) * sin(PI.toFloat() * u)
                projeter((sa * vd[0] + sb * vv[0]) * alt, (sa * vd[1] + sb * vv[1]) * alt, (sa * vd[2] + sb * vv[2]) * alt)
                arc[s * 3] = sortie[0]; arc[s * 3 + 1] = sortie[1]; arc[s * 3 + 2] = sortie[2]
            }
            val estompe = 1f - borne((sec - debut - 0.55f) / 0.5f)     // l'arc s'efface une fois arrivé
            fun cache(s: Int) = arc[s * 3 + 2] < 0f && hypot(arc[s * 3] - cx, arc[s * 3 + 1] - cy) < rr   // derrière la Terre
            for (s in 0 until 24) {
                if (cache(s) || cache(s + 1)) continue
                c.drawLine(arc[s * 3], arc[s * 3 + 1], arc[s * 3 + 3], arc[s * 3 + 4],
                    o.lumiere(avecAlpha(menthe, 0.55f * (0.3f + 0.7f * s / 24f) * estompe * a), 1.6f * k))
            }
            if (!cache(24)) o.halo(c, arc[72], arc[73], 8f * k, 0.35f * a * (if (f >= 1f) estompe else 1f), pale)
            if (f >= 1f) {
                projeter(vv[0], vv[1], vv[2])
                if (sortie[2] > 0f) o.halo(c, sortie[0], sortie[1], 14f * k, 0.22f * a * borne(sortie[2] * 2f), menthe)
            }
        }

        // 3) le point de départ : il reste allumé
        projeter(vd[0], vd[1], vd[2])
        val sx = sortie[0]; val sy = sortie[1]
        if (sec > t0 && sortie[2] > 0f) {
            o.halo(c, sx, sy, 22f * k, 0.30f * a, menthe)
            c.drawCircle(sx, sy, 2.4f * k, o.pinceau(avecAlpha(pale, 0.9f * a)))
        }

        // 4) l'écriture : des « tokens » qui apparaissent un par un, puis se condensent en un point
        val total = largeurs.sum() + 8f * (largeurs.size - 1)
        val condense = doux(borne((sec - 0.40f) / 0.16f))
        if (condense < 1f) {
            var x = cx - total / 2f * k
            for ((n, lg) in largeurs.withIndex()) {
                val ap = ralentit(borne((sec - 0.04f - n * 0.06f) / 0.10f))
                if (ap <= 0f) break
                val gx = mix(x + lg * k / 2f, cx, condense); val lw = mix(lg * k, 4f * k, condense)
                c.drawRoundRect(gx - lw / 2f, cy - 7f * k, gx + lw / 2f, cy + 7f * k, 7f * k, 7f * k,
                    o.pinceau(avecAlpha(pale, 0.75f * ap * (1f - condense * 0.5f) * a)))
                x += (lg + 8f) * k
            }
            // le curseur qui clignote doucement au bout de la phrase
            val fin = min(largeurs.size, (max(0f, (sec - 0.04f) / 0.06f)).toInt() + 1)
            var xc = cx - total / 2f * k
            for (n in 0 until fin) xc += (largeurs[n] + 8f) * k
            val cl2 = (0.5f + 0.5f * cos(sec * 9f)) * (1f - condense)
            val xx = mix(xc, cx, condense)
            c.drawRoundRect(xx, cy - 10f * k, xx + 3f * k, cy + 10f * k, 1.5f * k, 1.5f * k, o.pinceau(avecAlpha(menthe, 0.8f * cl2 * a)))
        }
        // le point de lumière voyage du centre jusqu'au départ, sur la Terre
        if (sec > 0.54f && sec < t0 + 0.05f) {
            val v = doux((sec - 0.55f) / (t0 - 0.55f))
            val px = mix(cx, sx, v); val py = mix(cy, sy, v)
            o.halo(c, px, py, 18f * k, 0.40f * a, menthe)
            c.drawCircle(px, py, 2.6f * k, o.pinceau(avecAlpha(pale, 0.95f * a)))
        }

        o.vignette(c, w, h, 0.6f)
        o.revelationPoints(c, sx, sy, i.revelation)
    }
}
