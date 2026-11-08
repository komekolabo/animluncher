package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// LA COURSE À L'IA, EN CHIFFRES : une animation différente pour chaque IA.
// Les chiffres montrent une TENDANCE générale (aucun vrai score d'une entreprise précise).
// Textes en anglais. Chaque scène a sa propre façon d'ouvrir l'app.

// ===================== CHATGPT : « Benchmarks » =====================
// Cinq courbes de scores (maths, code, sciences…) montent et viennent buter contre le plafond des
// 100 % : les tests « saturent », chacun plus vite que le précédent. Les scores défilent en dessous.
// Révélation : des colonnes qui montent depuis le bas (comme un graphique en barres).
class Benchmarks : Scene() {
    private val tests = listOf("MATH" to rgb(120, 215, 180), "CODE" to rgb(95, 195, 205), "SCIENCE" to rgb(170, 225, 160),
        "REASONING" to rgb(140, 200, 240), "AGENTS" to rgb(220, 235, 225))
    private val poussiere = nuage(88, 40)

    // Le score du test i à l'instant x du graphique (0 = début, 1 = fin) : une courbe en « S ».
    private fun score(i: Int, x: Float): Float {
        val x0 = 0.22f + 0.15f * i; val pente = 0.075f - 0.011f * i     // les derniers tests montent plus vite
        val base = 0.08f + 0.04f * i
        return base + (1f - base) / (1f + exp(-(x - x0) / pente))
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val sec = i.secondes; val w = c.width.toFloat(); val h = c.height.toFloat()
        val k = w / 540f
        val app = ralentit(i.intro); val a = 1f - i.revelation
        c.drawColor(rgb(7, 9, 10))
        o.poussieres(c, poussiere, sec, w, h, rgb(200, 230, 220), 0.15f * app * a, 0f, -0.01f)
        val L = 62f * k; val R = w - 50f * k; val T = h * 0.30f; val B = h * 0.60f
        val xh = ralentit(sec / 1.2f) * app                 // jusqu'où le graphique est tracé
        val gris = rgb(120, 135, 135)

        o.texte(c, "BENCHMARKS", L, T - 60f * k, 13f * k, avecAlpha(rgb(200, 225, 215), 0.55f * app * a), gras = true)
        val pGrille = o.pinceau(avecAlpha(gris, 0.10f * app * a), 1f * k)
        for (g in 1..3) { val y = mix(B, T, g / 4f); c.drawLine(L, y, R, y, pGrille) }
        c.drawLine(L, B, R, B, o.pinceau(avecAlpha(gris, 0.45f * app * a), 1.2f * k))
        // le plafond des 100 %, en pointillés qui défilent
        val pt = o.pinceau(avecAlpha(rgb(200, 230, 220), 0.45f * app * a), 1.2f * k)
        pt.pathEffect = DashPathEffect(floatArrayOf(6f * k, 6f * k), modulo(sec * 30f * k, 12f * k))
        c.drawLine(L, T, R, T, pt)
        o.texte(c, "100%", L - 8f * k, T + 4f * k, 11f * k, avecAlpha(rgb(200, 230, 220), 0.6f * app * a), ancre = 1f, mono = true)

        for ((n, test) in tests.withIndex()) {
            val (nom, coul) = test
            val ch = Path()
            for (s in 0..70) {
                val x = xh * s / 70f
                val px = mix(L, R, x); val py = mix(B, T, score(n, x))
                if (s == 0) ch.moveTo(px, py) else ch.lineTo(px, py)
            }
            c.drawPath(ch, o.pinceau(avecAlpha(coul, 0.15f * app * a), 6f * k))      // lueur douce
            c.drawPath(ch, o.pinceau(avecAlpha(coul, 0.85f * app * a), 2f * k))
            val v = score(n, xh)
            val hx = mix(L, R, xh); val hy = mix(B, T, v)
            val sature = borne((v - 0.95f) / 0.05f)
            o.halo(c, hx, hy, 12f * k, 0.28f * (1f - 0.8f * sature) * app * a, coul)   // la lueur s'éteint une fois saturé
            c.drawCircle(hx, hy, 3f * k, o.pinceau(avecAlpha(rgb(240, 250, 245), 0.9f * (1f - 0.7f * sature) * app * a)))

            // la ligne de score sous le graphique
            val y = B + 70f * k + n * 34f * k
            val ap = ralentit((sec - 0.05f * n) / 0.3f) * a
            c.drawCircle(L + 5f * k, y - 4f * k, 4f * k, o.pinceau(avecAlpha(coul, 0.9f * ap)))
            o.texte(c, nom, L + 20f * k, y, 13f * k, avecAlpha(rgb(210, 225, 220), 0.75f * ap))
            o.texte(c, String.format(java.util.Locale.US, "%5.1f%%", min(100f, v * 100f)), R, y, 13f * k, avecAlpha(coul, 0.9f * ap), ancre = 1f, mono = true)
            c.drawRect(L + 150f * k, y - 6f * k, R - 150f * k, y - 3f * k, o.pinceau(avecAlpha(gris, 0.18f * ap)))
            c.drawRect(L + 150f * k, y - 6f * k, mix(L + 150f * k, R - 150f * k, v), y - 3f * k, o.pinceau(avecAlpha(coul, 0.7f * ap)))
            if (sature > 0f) o.texte(c, "saturated", R - 72f * k, y, 10f * k, avecAlpha(coul, 0.6f * sature * ap), ancre = 1f)
        }
        o.vignette(c, w, h, 0.55f)
        o.revelationColonnes(c, i.revelation)
    }
}

// ===================== CLAUDE : « Autonomy » =====================
// Combien de temps une IA peut travailler SEULE, génération après génération.
// L'axe vertical est « logarithmique » : chaque ligne vaut beaucoup plus que celle du dessous
// (1 seconde, 1 minute, 15 minutes, 4 heures, 2 jours, 1 mois), avec un exemple de tâche humaine.
// Les points (les générations) montent en ligne droite sur cette échelle = la durée DOUBLE
// régulièrement. Quand la courbe atteint une ligne, sa tâche s'allume. Des pointillés prolongent
// la tendance. Révélation : un balayage qui part dans le sens de la tendance (vers le haut à droite).
class Autonomie : Scene() {
    private val terre = rgb(217, 119, 87)
    private val creme = rgb(240, 228, 212)
    private val poussiere = nuage(19, 40)
    private val lmax = log10(2.6e6).toFloat()
    private val taches = listOf(Triple(0f, "1 sec", "answer a question"), Triple(log10(60f), "1 min", "write a function"),
        Triple(log10(900f), "15 min", "fix a bug"), Triple(log10(14400f), "4 h", "build a feature"),
        Triple(log10(172800f), "2 days", "ship an app"), Triple(lmax, "1 month", "run a project"))
    private val n = 9
    // chaque génération : (position en temps 0 à 1, log10 des secondes de travail autonome)
    private val gens = Random(7).let { r -> List(n) { floatArrayOf(0.13f + 0.078f * it, 0.15f + 0.68f * it + (r.nextFloat() - 0.5f) * 0.36f) } }

    // Transforme un log10(secondes) en texte lisible.
    private fun duree(lg: Float): String {
        val s = 10.0.pow(lg.toDouble())
        for ((u, v) in listOf("days" to 86400.0, "hours" to 3600.0, "min" to 60.0)) {
            if (s >= v) { val q = s / v; return if (q >= 10) "${q.roundToInt()} $u" else String.format(java.util.Locale.US, "%.1f %s", q, u) }
        }
        return "${s.roundToInt()} sec"
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val sec = i.secondes; val w = c.width.toFloat(); val h = c.height.toFloat()
        val k = w / 540f
        val app = ralentit(i.intro); val a = 1f - i.revelation
        val fond = o.pinceau()
        fond.shader = LinearGradient(0f, 0f, 0f, h, rgb(18, 14, 12), rgb(9, 8, 8), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fond)
        o.poussieres(c, poussiere, sec, w, h, creme, 0.12f * app * a, 0f, -0.01f)
        val L = 36f * k; val R = w - 30f * k; val B = h * 0.72f; val T = h * 0.20f
        fun X(x: Float) = mix(L, R, x)
        fun Y(lg: Float) = mix(B, T, lg / lmax)

        o.texte(c, "HOW LONG AI CAN WORK ALONE", L, T - 70f * k, 13f * k, avecAlpha(creme, 0.55f * app * a), gras = true)

        // les générations déjà apparues (la dernière glisse en douceur depuis la précédente)
        val vis = if (sec > 0.08f) min(n - 1f, (sec - 0.08f) / 0.095f) else -1f
        val pts = ArrayList<FloatArray>()
        for (q in 0 until n) {
            if (vis < q - 1) break
            if (q == 0) { pts.add(gens[0]); continue }
            val f = ralentit(vis - (q - 1))
            val g0 = gens[q - 1]; val g1 = gens[q]
            pts.add(floatArrayOf(mix(g0[0], g1[0], f), mix(g0[1], g1[1], f)))
        }
        val sommet = pts.maxOfOrNull { it[1] } ?: 0f

        // les lignes de tâches : elles s'allument quand la courbe les atteint
        for ((lg, temps, tache) in taches) {
            val y = Y(lg)
            val atteint = if (pts.isEmpty()) 0f else borne((sommet - lg) / 0.25f + 1f)
            c.drawLine(L, y, R, y, o.pinceau(avecAlpha(creme, (0.08f + 0.10f * atteint) * app * a), 1f * k))
            o.texte(c, temps, L, y - 7f * k, 11f * k, avecAlpha(creme, (0.45f + 0.3f * atteint) * app * a), mono = true)
            o.texte(c, tache, R, y - 7f * k, 12f * k, avecAlpha(if (atteint >= 1f) terre else creme, (0.35f + 0.55f * atteint) * app * a), ancre = 1f)
        }

        if (pts.size >= 2) {
            // la surface sous la courbe : un dégradé terre cuite qui s'efface vers le bas
            val zone = Path(); zone.moveTo(X(pts[0][0]), B)
            for (p in pts) zone.lineTo(X(p[0]), Y(p[1]))
            zone.lineTo(X(pts.last()[0]), B); zone.close()
            val rempl = o.pinceau()
            rempl.shader = LinearGradient(0f, T, 0f, B, avecAlpha(terre, 0.30f * a), avecAlpha(terre, 0f), Shader.TileMode.CLAMP)
            c.drawPath(zone, rempl)
            val ligne = Path(); ligne.moveTo(X(pts[0][0]), Y(pts[0][1]))
            for (p in pts.drop(1)) ligne.lineTo(X(p[0]), Y(p[1]))
            c.drawPath(ligne, o.pinceau(avecAlpha(terre, 0.18f * a), 8f * k))
            c.drawPath(ligne, o.pinceau(avecAlpha(terre, 0.95f * a), 2.4f * k))
            // la tendance prolongée, en pointillés, vers le haut
            if (vis >= n - 1.5f) {
                val fin = borne((sec - (0.08f + 0.095f * (n - 1))) / 0.25f)
                val p0 = pts.last()
                val tp = o.pinceau(avecAlpha(creme, 0.5f * a), 1.4f * k)
                tp.pathEffect = DashPathEffect(floatArrayOf(5f * k, 6f * k), modulo(-sec * 20f * k, 11f * k))
                c.drawLine(X(p0[0]), Y(p0[1]), X(p0[0] + 0.25f * fin), Y(p0[1] + 0.68f * 2.4f * fin), tp)
            }
        }
        for ((q, p) in pts.withIndex()) {
            val dernier = q == pts.size - 1
            o.halo(c, X(p[0]), Y(p[1]), (if (dernier) 22f else 12f) * k, (if (dernier) 0.40f else 0.18f) * a, terre)
            c.drawCircle(X(p[0]), Y(p[1]), (if (dernier) 4.2f else 3.0f) * k, o.pinceau(avecAlpha(creme, 0.95f * a)))
        }

        // le compteur en bas
        val yb = B + 110f * k
        o.texte(c, if (pts.isEmpty()) "0 sec" else duree(sommet), w / 2f, yb, 42f * k, avecAlpha(terre, 0.95f * app * a), ancre = 0.5f, mono = true, gras = true)
        o.texte(c, "of autonomous work", w / 2f, yb + 34f * k, 13f * k, avecAlpha(creme, 0.5f * app * a), ancre = 0.5f)

        o.vignette(c, w, h, 0.55f)
        o.revelationBalayageDouce(c, i.revelation, -35f, terre)
    }
}

// ===================== GEMINI : « Power » =====================
// La course à l'IA se joue aussi en ÉLECTRICITÉ. Vue du ciel, la nuit : un bâtiment de serveurs
// (ventilateurs qui tournent sur le toit), puis le campus grandit et la vue recule. Des lignes à haute
// tension amènent le courant. Le compteur avance en 3 temps posés : 50 MW (une petite ville),
// 1 GW (un réacteur nucléaire), 5 GW (5 réacteurs). Révélation : des tuiles carrées.
class Puissance : Scene() {
    // les emplacements des bâtiments (en « unités bâtiment »), triés du centre vers l'extérieur
    private val cases = ArrayList<FloatArray>().also { l ->
        for (ii in 0 until 8) for (jj in 0 until 10) l.add(floatArrayOf((ii - 3.5f) * 1.25f, (jj - 4.5f) * 0.85f))
        l.sortBy { it[0] * it[0] + (it[1] * 1.3f) * (it[1] * 1.3f) }
    }
    private val leds = Random(2).let { r -> List(cases.size) { List(5) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat()) } } }
    // 3 temps, chacun tenu un instant pour avoir le temps de lire : (moment, log10 de la puissance en MW)
    private val cles = arrayOf(floatArrayOf(0f, 1.7f), floatArrayOf(0.30f, 1.7f), floatArrayOf(0.55f, 3.0f),
        floatArrayOf(0.80f, 3.0f), floatArrayOf(1.02f, 3.7f))

    private fun puissanceLog(sec: Float): Float {
        for (q in 0 until cles.size - 1) {
            val (t0, v0) = cles[q]; val (t1, v1) = cles[q + 1]
            if (sec <= t1) return mix(v0, v1, doux((sec - t0) / (t1 - t0)))
        }
        return cles.last()[1]
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val sec = i.secondes; val w = c.width.toFloat(); val h = c.height.toFloat()
        val k = w / 540f
        val app = ralentit(i.intro); val a = 1f - i.revelation
        c.drawColor(rgb(6, 7, 12))
        val lp = puissanceLog(sec)
        val mw = 10f.pow(lp)
        val nb = min(max(1f, mw / 75f), cases.size.toFloat())      // un bâtiment ≈ 75 MW
        val visibles = ceil(nb).toInt()
        // on centre la vue sur le milieu des bâtiments visibles (pondéré par leur apparition)
        val poids = FloatArray(visibles) { if (it > 0) borne(nb - it) else 1f }
        val tot = poids.sum()
        var mx = 0f; var my = 0f
        for (q in 0 until visibles) { mx += cases[q][0] * poids[q]; my += cases[q][1] * poids[q] }
        mx /= tot; my /= tot
        var ext = 1f
        for (q in 0 until visibles) {
            val p = poids[q]
            ext = max(ext, max((kotlin.math.abs(cases[q][0] - mx) + 0.7f) * p + (1 - p) * ext,
                ((kotlin.math.abs(cases[q][1] - my) + 0.5f) * 1.3f) * p + (1 - p) * ext))
        }
        val u = 0.40f * w / ext                       // taille d'une « unité bâtiment » à l'écran
        val cx = w / 2f - mx * u; val cy = h * 0.60f - my * u

        // le sol : quelques routes très sombres
        val route = o.pinceau(avecAlpha(rgb(22, 24, 34), 0.5f * app * a), max(1f, 0.08f * u))
        for (q in -6..6) { val x = cx + (q + 0.5f) * 1.25f * u - 0.62f * u; c.drawLine(x, 0f, x, h, route) }

        // le poste électrique, juste au-dessus du campus, et les lignes à haute tension (impulsions de courant)
        var haut = 0f
        for (q in 0 until visibles) haut = min(haut, cases[q][1])
        val sx = w / 2f; val sy = cy + (haut - 0.95f) * u
        val pLigne = o.pinceau(avecAlpha(rgb(110, 115, 150), 0.45f * app * a), max(0.8f, 0.03f * u))
        for (m in 0..2) {
            val x0 = sx + (m - 1) * 0.25f * u
            c.drawLine(x0, -10f, x0, sy, pLigne)
            for (q in 0..2) {
                val f = modulo(sec * 1.6f + q / 3f + m * 0.11f, 1f)
                o.halo(c, x0, mix(-10f, sy, f), min(9f * k, max(4f * k, 0.18f * u)), 0.30f * app * a, couleurGemini(0.2f + 0.25f * m))
            }
        }
        val poste = RectF(sx - 0.55f * u, sy - 0.35f * u, sx + 0.55f * u, sy + 0.35f * u)
        c.drawRect(poste, o.pinceau(avecAlpha(rgb(26, 28, 38), app * a)))
        for (m in 0..3) {
            val tx = poste.left + (m + 0.5f) * poste.width() / 4f
            c.drawRect(tx - 0.08f * u, sy - 0.15f * u, tx + 0.08f * u, sy + 0.15f * u, o.pinceau(avecAlpha(rgb(70, 74, 95), 0.8f * app * a)))
            c.drawCircle(tx, sy, max(1f, 0.03f * u), o.pinceau(avecAlpha(rgb(240, 200, 120), 0.7f * app * a)))
        }

        // les bâtiments
        val pBat = o.pinceau(); val pBord = o.pinceau(null, max(0.8f, 0.02f * u))
        val pVent = o.pinceau(); val pPale = o.pinceau(null, max(0.8f, 0.018f * u)); val pLed = o.pinceau()
        for (idx in 0 until visibles) {
            val ap = ralentit(if (idx > 0) borne(nb - idx) else 1f) * app * a
            if (ap <= 0f) continue
            val x = cx + cases[idx][0] * u; val y = cy + cases[idx][1] * u
            val lw = 0.50f * u; val lh = 0.32f * u
            val bat = RectF(x - lw, y - lh, x + lw, y + lh)
            pBat.color = avecAlpha(rgb(34, 36, 50), ap); c.drawRect(bat, pBat)
            pBord.color = avecAlpha(couleurGemini(idx / 40f), 0.35f * ap); c.drawRect(bat, pBord)
            // les ventilateurs sur le toit : 2 rangées de 5, ils tournent
            val rf = 0.075f * u
            for (rg in 0..1) for (col in 0..4) {
                val fx = x - lw + (col + 0.5f) * (2f * lw / 5f); val fy = y - lh * 0.45f + rg * lh * 0.9f
                if (rf < 1.2f) { pVent.color = avecAlpha(rgb(60, 64, 90), ap); c.drawCircle(fx, fy, max(0.6f, rf), pVent); continue }
                pVent.color = avecAlpha(rgb(16, 17, 26), ap); c.drawCircle(fx, fy, rf, pVent)
                pPale.color = avecAlpha(rgb(95, 100, 135), ap)
                val ang = sec * 9f + idx + col
                for (b in 0..2) {
                    val aa = ang + b * 2.094f
                    c.drawLine(fx, fy, fx + cos(aa) * rf * 0.85f, fy + sin(aa) * rf * 0.85f, pPale)
                }
            }
            // petites LED d'activité
            for (l in leds[idx]) {
                val on = 0.5f + 0.5f * sin(sec * 8f + l[2] * 20f)
                pLed.color = avecAlpha(couleurGemini(l[0]), (0.3f + 0.6f * on) * ap)
                c.drawCircle(bat.left + l[0] * bat.width(), bat.top + l[1] * bat.height(), max(0.7f, 0.02f * u), pLed)
            }
            o.halo(c, x, y, 0.9f * u, 0.06f * ap, couleurGemini(idx / 40f))
        }

        // le compteur de puissance et son équivalent
        val yt = h * 0.15f
        o.texte(c, "AI DATA CENTER POWER", w / 2f, yt - 50f * k, 13f * k, avecAlpha(rgb(190, 190, 240), 0.55f * app * a), ancre = 0.5f, gras = true)
        val valeur = if (mw < 1000f) "${mw.roundToInt()} MW" else String.format(java.util.Locale.US, "%.1f GW", mw / 1000f)
        o.texte(c, valeur, w / 2f, yt, 40f * k, avecAlpha(rgb(230, 230, 255), 0.95f * app * a), ancre = 0.5f, mono = true, gras = true)
        val equiv = when {
            lp >= 2.9f -> { val nr = max(1, (mw / 1000f).roundToInt()); if (nr == 1) "a nuclear reactor" else "$nr nuclear reactors" }
            lp >= 2.0f -> "a big city"
            else -> "a small town"
        }
        o.texte(c, "≈ the power of $equiv", w / 2f, yt + 32f * k, 14f * k, avecAlpha(couleurGemini(0.5f), 0.85f * app * a), ancre = 0.5f)

        o.vignette(c, w, h, 0.55f)
        o.revelationTuiles(c, i.revelation)
    }
}

// ===================== GROK : « Intelligence » =====================
// L'intelligence (en hauteur) au fil du temps. Des paliers en pointillés : insecte, souris, corbeau,
// chimpanzé, humain, génie. La courbe de l'IA rampe longtemps tout en bas… puis accélère
// (exponentielle) et franchit les paliers de plus en plus vite ; l'écart humain → génie, qui nous paraît
// énorme, passe en un instant. Un bonhomme bâton, debout sur la ligne HUMAN, est choqué quand elle
// passe devant lui. Révélation : une fente verticale qui s'ouvre depuis la gauche.
class Intelligence : Scene() {
    private val argent = rgb(225, 228, 236)
    private val rouge = rgb(185, 70, 60)           // le bonhomme : rouge sombre, pour qu'il se fonde dans le fond
    private val paliers = listOf(Triple("INSECT", 0.07f, rgb(110, 100, 170)), Triple("MOUSE", 0.17f, rgb(90, 120, 190)),
        Triple("CROW", 0.31f, rgb(80, 150, 170)), Triple("CHIMP", 0.50f, rgb(110, 160, 120)),
        Triple("HUMAN", 0.71f, rgb(180, 160, 100)), Triple("GENIUS", 0.755f, rgb(200, 130, 100)))
    private val poussiere = nuage(61, 40)
    private val kExp = 9f
    private val eK = exp(kExp) - 1f

    // Hauteur de la courbe (0 = en bas, 1 = en haut du graphique) : une exponentielle.
    private fun courbe(x: Float) = (exp(kExp * x) - 1f) / eK * 1.35f + 0.015f * x
    // L'inverse : à quel moment la courbe atteint la hauteur y.
    private fun xDe(y: Float) = ln(1f + max(0f, y) / 1.35f * eK) / kExp

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val sec = i.secondes; val w = c.width.toFloat(); val h = c.height.toFloat()
        val k = w / 540f
        val app = ralentit(i.intro); val a = 1f - i.revelation
        c.drawColor(rgb(4, 4, 6))
        o.poussieres(c, poussiere, sec, w, h, argent, 0.14f * app * a, 0f, -0.01f)
        val L = 70f * k; val R = w - 40f * k; val T = h * 0.16f; val B = h * 0.80f
        fun px(x: Float) = mix(L, R, x)
        fun py(y: Float) = mix(B, T, y)
        val xh = borne(sec / 1.12f) * (0.4f + 0.6f * app)       // où en est la courbe

        // les axes
        val axe = o.pinceau(avecAlpha(argent, 0.55f * app * a), 1.6f * k)
        c.drawLine(L, B, R, B, axe); c.drawLine(L, B, L, T - 20f * k, axe)
        o.texte(c, "TIME", R, B + 30f * k, 12f * k, avecAlpha(argent, 0.5f * app * a), ancre = 1f, gras = true)
        c.save(); c.translate(L - 46f * k, mix(B, T, 0.5f)); c.rotate(-90f)
        o.texte(c, "INTELLIGENCE", 0f, 0f, 12f * k, avecAlpha(argent, 0.5f * app * a), ancre = 0.5f, gras = true)
        c.restore()

        // les paliers : ils s'allument quand la courbe les franchit
        for ((nom, y, coul) in paliers) {
            val franchi = borne((xh - xDe(y)) / 0.04f)
            val yy = py(y)
            val tir = o.pinceau(avecAlpha(coul, (0.45f + 0.35f * franchi) * app * a), 1.2f * k)
            tir.pathEffect = DashPathEffect(floatArrayOf(5f * k, 5f * k), 0f)
            c.drawLine(L, yy, R, yy, tir)
            o.texte(c, nom, L + 6f * k, yy - 6f * k, 11f * k, avecAlpha(coul, (0.65f + 0.35f * franchi) * app * a), gras = true)
            if (franchi > 0f && franchi < 1f) o.halo(c, px(xDe(y)), yy, 20f * k, 0.25f * (1f - franchi) * a, coul)
        }

        // la courbe
        val ch = Path()
        for (s in 0..90) {
            val x = xh * s / 90f
            if (s == 0) ch.moveTo(px(x), py(courbe(x))) else ch.lineTo(px(x), py(courbe(x)))
        }
        c.drawPath(ch, o.pinceau(avecAlpha(argent, 0.12f * app * a), 9f * k))
        c.drawPath(ch, o.pinceau(avecAlpha(argent, 0.95f * app * a), 2.6f * k))
        val hx = px(xh); val hy = py(courbe(xh))
        if (hy > -20f) {
            o.halo(c, hx, hy, 18f * k, 0.35f * app * a, argent)
            c.drawCircle(hx, hy, 3.5f * k, o.pinceau(avecAlpha(rgb(255, 255, 255), 0.95f * app * a)))
        }

        bonhomme(c, o, sec, xh, px(0.80f), py(0.71f), k, app * a)

        o.vignette(c, w, h, 0.55f)
        o.revelationFente(c, L + 20f * k, i.revelation)     // ça s'ouvre depuis la gauche : le bonhomme reste visible
    }

    // Un bonhomme bâton debout sur le palier HUMAN (x, y = ses pieds). Calme… puis choqué quand la courbe
    // passe devant lui à toute vitesse : bras en l'air, il recule, et « ?! » tremble au-dessus de sa tête.
    private fun bonhomme(c: Canvas, o: Outils, sec: Float, xh: Float, x: Float, y: Float, k: Float, al: Float) {
        val choc = doux((xh - 0.86f) / 0.05f)                 // 0 = calme, 1 = choqué
        val tremble = choc * 1.6f * k
        val jx = tremble * sin(sec * 71f); val jy = tremble * cos(sec * 53f)
        val recul = -0.20f * choc                             // il se penche en arrière
        val trait = o.pinceau(avecAlpha(rouge, 0.80f * al), 2.2f * k)
        val hh = 62f * k
        val hancheX = x; val hancheY = y - hh * 0.45f
        val couX = x + sin(recul) * hh * 0.35f + jx * 0.5f; val couY = hancheY - hh * 0.33f
        val teteX = couX + sin(recul) * 8f * k + jx; val teteY = couY - 9f * k + jy
        // jambes, corps, tête
        c.drawLine(hancheX, hancheY, x - 9f * k, y, trait)
        c.drawLine(hancheX, hancheY, x + 9f * k, y, trait)
        c.drawLine(hancheX, hancheY, couX, couY, trait)
        c.drawCircle(teteX, teteY, 8f * k, trait)
        // les bras : angle mesuré depuis la verticale vers le bas (20° = ballants, 132° = levés)
        val epX = mix(hancheX, couX, 0.72f); val epY = mix(hancheY, couY, 0.72f)
        val th = Math.toRadians(mix(20f, 132f, choc).toDouble()).toFloat()
        val lg = 23f * k
        for (cote in intArrayOf(-1, 1))
            c.drawLine(epX, epY, epX + cote * sin(th) * lg + jx, epY + cos(th) * lg + jy, trait)
        // « ?! » qui vibre, et des petits traits de surprise autour de la tête
        if (choc > 0f) {
            val coul = avecAlpha(rgb(235, 105, 85), 0.95f * choc * al)
            for ((n, s) in listOf("?", "!").withIndex()) {
                val dx = 2.2f * k * sin(sec * 83f + n * 2); val dy = 2.2f * k * cos(sec * 67f + n)
                o.texte(c, s, teteX - 9f * k + n * 13f * k + dx, teteY - 18f * k + dy, 22f * k, coul, gras = true)
            }
            val pt = o.pinceau(avecAlpha(rouge, 0.6f * choc * al), 1.4f * k)
            for (ang in intArrayOf(-150, -120, -60, -30)) {
                val aa = Math.toRadians(ang.toDouble()).toFloat()
                c.drawLine(teteX + cos(aa) * 12f * k + jx, teteY + sin(aa) * 12f * k + jy,
                    teteX + cos(aa) * 17f * k + jx, teteY + sin(aa) * 17f * k + jy, pt)
            }
        }
    }
}
