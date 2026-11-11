package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// LES LOGOS « VIE QUOTIDIENNE » (11/11, validés par Will sur la page d'aperçu « logos animés des dossiers »).
// 14 logos de plus, qu'on peut choisir pour n'importe quel dossier (y compris les dossiers perso) :
//   jeux      une manette : le joystick fait un tour, les gâchettes s'enfoncent, les boutons s'allument en combo, elle vibre
//             (c'est aussi le logo du dossier Games, qui n'en avait pas)
//   maison    la fenêtre s'allume (lumière douce), la fumée sort de la cheminée en petites bouffées
//   cuisine   une casserole : le couvercle sautille, la vapeur s'échappe en volutes
//   cafe      une tasse : la vapeur monte et dessine un petit cœur
//   sport     un haltère soulevé avec élan, qui tremble un peu en haut puis retombe
//   voiture   une petite voiture : les roues tournent, la route défile, les phares clignent
//   voyage    un avion trace sa route en pointillés en montant
//   ecole     un livre ouvert dont deux pages se tournent
//   animaux   une patte se pose, les doigts rebondissent, un petit cœur apparaît
//   plante    une pousse : la tige pousse, les feuilles se déplient, un bourgeon (l'étincelle) éclot
//   meteo     un nuage glisse et découvre le soleil, puis revient
//   nuit      un croissant de lune qui se berce, des « z » montent, deux étoiles scintillent
//   fete      deux flûtes qui trinquent : l'étincelle jaillit, des bulles montent
//   tirelire  un cochon : une pièce tombe en tournoyant dans la fente, il sautille, rosit, remue la queue
// Même repère et mêmes règles que LogosDossier.kt (carré 100 x 100, centre (50, 48), « g » = l'horloge du geste,
// 0,3 = la pose au repos ; les trous sont DÉCOUPÉS avec la gomme ; une étincelle dans chaque logo).
// Les outils de dessin (remplir, gomme, trait, clés, étincelle…) sont ceux de LogosDossier.
// L'aperçu JavaScript de la page web dessine exactement la même chose.
internal object LogosQuotidien {
    private val L = LogosDossier
    private val mat = Matrix()
    // un pinceau à nous pour les traits de couleur unie (rayons du soleil, onde de la tirelire)
    private val q = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---------------- petits fabricants de formes ----------------
    private fun rr(l: Float, t: Float, r: Float, b: Float, k: Float) = Path().apply { addRoundRect(l, t, r, b, k, k, Path.Direction.CW) }
    private fun rond(x: Float, y: Float, r: Float) = Path().apply { addCircle(x, y, r, Path.Direction.CW) }
    private fun ovale(cx: Float, cy: Float, rx: Float, ry: Float, angle: Float = 0f): Path {
        val p = Path(); p.addOval(cx - rx, cy - ry, cx + rx, cy + ry, Path.Direction.CW)
        if (angle != 0f) { mat.setRotate(angle, cx, cy); p.transform(mat) }
        return p
    }
    // une ligne brisée : x1, y1, x2, y2… (fermée ou non)
    private fun poly(vararg xy: Float, ferme: Boolean = true): Path {
        val p = Path(); p.moveTo(xy[0], xy[1])
        var i = 2
        while (i < xy.size) { p.lineTo(xy[i], xy[i + 1]); i += 2 }
        if (ferme) p.close()
        return p
    }
    private fun f(d: Double) = d.toFloat()
    // remplir une forme de la couleur claire, avec une transparence (0 à 1)
    private fun lumiere(cv: Canvas, p: Path, lum: Int, a: Double) {
        val pinceau = L.uni(lum); pinceau.alpha = (255 * a).roundToInt().coerceIn(0, 255); cv.drawPath(p, pinceau)
    }
    private fun calque(cv: Canvas, a: Double) { cv.saveLayerAlpha(L.calque, (a).roundToInt().coerceIn(0, 255)) }

    // les clés : on réutilise celles de LogosDossier (« 0.5 to 1.0 by DOUX »)
    private val LIN = L.LIN; private val ACC = L.ACC; private val DEC = L.DEC; private val DOUX = L.DOUX; private val RESSORT = L.RESSORT
    private fun cle(t: Double, vararg c: LogosDossier.CleLogo) = L.cle(t, *c)
    private infix fun Pair<Double, Double>.by(fn: (Double) -> Double) = LogosDossier.CleLogo(first, second, fn)
    private fun bosse(t: Double, c: Double, d: Double) = L.bosse(t, c, d)

    fun dessiner(cv: Canvas, cle: String, g: Double, src: Shader, lum: Int): Boolean {
        when (cle) {
            "jeux" -> jeux(cv, g, src, lum)
            "maison" -> maison(cv, g, src, lum)
            "cuisine" -> cuisine(cv, g, src, lum)
            "cafe" -> cafe(cv, g, src, lum)
            "sport" -> sport(cv, g, src, lum)
            "voiture" -> voiture(cv, g, src, lum)
            "voyage" -> voyage(cv, g, src, lum)
            "ecole" -> ecole(cv, g, src, lum)
            "animaux" -> animaux(cv, g, src, lum)
            "plante" -> plante(cv, g, src, lum)
            "meteo" -> meteo(cv, g, src, lum)
            "nuit" -> nuit(cv, g, src, lum)
            "fete" -> fete(cv, g, src, lum)
            "tirelire" -> tirelire(cv, g, src, lum)
            else -> return false
        }
        return true
    }

    // ---------------- JEUX : la manette ----------------
    private fun manette(): Path = Path().apply {
        moveTo(31f, 39f); cubicTo(38f, 36f, 62f, 36f, 69f, 39f); cubicTo(77f, 42f, 80.5f, 53f, 81f, 62f)
        cubicTo(81.5f, 70f, 75.5f, 73f, 71.5f, 69f); cubicTo(68f, 65.5f, 66f, 61f, 60f, 61f); lineTo(40f, 61f)
        cubicTo(34f, 61f, 32f, 65.5f, 28.5f, 69f); cubicTo(24.5f, 73f, 18.5f, 70f, 19f, 62f); cubicTo(19.5f, 53f, 23f, 42f, 31f, 39f)
        close()
    }
    private fun jeux(cv: Canvas, g: Double, src: Shader, lum: Int) {
        val vib = bosse(g, 1.95, 0.22)
        cv.save(); cv.translate(f(0.9 * sin(g * 70) * vib), 0f)
        // les gâchettes (derrière la manette), qui s'enfoncent l'une après l'autre
        val gach = doubleArrayOf(bosse(g, 0.62, 0.1), bosse(g, 0.8, 0.1))
        val places = arrayOf(floatArrayOf(27f, 33.5f, 40f, 39f), floatArrayOf(60f, 33.5f, 73f, 39f))
        for (i in 0..1) { val (a, b, c, d) = places[i]; val e = f(1.6 * gach[i]); L.remplir(cv, rr(a, b + e, c, d + e, 2.6f), src) }
        // le corps, détaché des gâchettes par un liseré découpé
        val corps = manette(); cv.drawPath(corps, L.gomme(2.4f)); L.remplir(cv, corps, src)
        // la croix (découpée), qui s'enfonce
        cv.save(); cv.translate(35.5f, 48.5f); val ap = f(1 - 0.14 * bosse(g, 1.25, 0.1)); cv.scale(ap, ap)
        cv.drawPath(rr(-6f, -1.9f, 6f, 1.9f, 1f), L.gomme()); cv.drawPath(rr(-1.9f, -6f, 1.9f, 6f, 1f), L.gomme())
        cv.restore()
        // les 4 boutons : ils s'allument en combo
        val boutons = arrayOf(floatArrayOf(64.5f, 44.3f), floatArrayOf(68.7f, 48.5f), floatArrayOf(64.5f, 52.7f), floatArrayOf(60.3f, 48.5f))
        for ((i, b) in boutons.withIndex()) {
            val a = bosse(g, 1.15 + 0.13 * i, 0.13)
            cv.drawCircle(b[0], b[1], f(2.4 * (1 - 0.15 * a)), L.gomme())
            if (a > 0.02) lumiere(cv, rond(b[0], b[1], 1.9f), lum, 0.9 * a)
        }
        // les 2 joysticks : le gauche fait un tour
        val tour = cle(g, 0.45 to 0.0 by LIN, 1.1 to 1.0 by DOUX)
        val r = 1.6 * sin(tour * PI)
        val sticks = arrayOf(floatArrayOf(43.5f, 56f, f(cos(tour * 2 * PI) * r), f(sin(tour * 2 * PI) * r)), floatArrayOf(56.5f, 56f, 0f, 0f))
        for ((x, y, ox, oy) in sticks) {
            cv.drawCircle(x, y, 4.3f, L.gomme()); L.remplir(cv, rond(x + ox, y + oy, 3f), src); cv.drawCircle(x + ox, y + oy, 1.1f, L.gomme())
        }
        cv.restore()
        // les petites ondes de vibration sur les côtés
        if (vib > 0.05) {
            calque(cv, 200 * vib)
            val traits = arrayOf(floatArrayOf(14f, 52f, 14f, 60f), floatArrayOf(12f, 50f, 11f, 62f), floatArrayOf(86f, 52f, 86f, 60f), floatArrayOf(88f, 50f, 89f, 62f))
            for ((i, t) in traits.withIndex()) if (i % 2 == 0 || vib > 0.5) cv.drawLine(t[0], t[1], t[2], t[3], L.trait(1.6f, src))
            cv.restore()
        }
        L.etincelle(cv, 50f, 27f, 4.6f, lum, bosse(g, 1.6, 0.22))
    }

    // ---------------- MAISON ----------------
    private fun maison(cv: Canvas, g: Double, src: Shader, lum: Int) {
        L.remplir(cv, rr(61f, 27f, 68f, 42f, 1.5f), src)                                 // la cheminée
        L.remplirArrondi(cv, poly(23f, 47f, 50f, 25f, 77f, 47f), src, 4f)                 // le toit
        cv.drawLine(26f, 48.5f, 74f, 48.5f, L.gomme(2f))                                 // le liseré toit / murs
        L.remplir(cv, rr(30f, 50f, 70f, 73f, 3f), src)                                   // les murs
        cv.drawPath(rr(45f, 59f, 55f, 73f, 2f), L.gomme())                               // la porte
        val allume = cle(g, 0.55 to 0.0 by LIN, 0.85 to 1.0 by DOUX)
        cv.drawPath(rr(34.5f, 54f, 42.5f, 62f, 1.5f), L.gomme())                         // la fenêtre…
        if (allume > 0.02) lumiere(cv, rr(35.5f, 55f, 41.5f, 61f, 1f), lum, 0.85 * allume)  // … qui s'allume
        cv.drawLine(38.5f, 54f, 38.5f, 62f, L.gomme(1f)); cv.drawLine(34.5f, 58f, 42.5f, 58f, L.gomme(1f))
        for (i in 0..2) {                                                               // les bouffées de fumée
            val u = (g - (0.9 + 0.38 * i)) / 1.1
            if (u <= 0 || u >= 1) continue
            calque(cv, 150 * sin(u * PI))
            L.remplir(cv, rond(f(64.5 + 3 * sin(u * 4 + i)), f(24 - 12 * u), f(2 + 3.2 * u)), src)
            cv.restore()
        }
        L.etincelle(cv, 29f, 29f, 5f, lum, bosse(g, 0.85, 0.22))
    }

    // ---------------- CUISINE : la casserole ----------------
    private fun cuisine(cv: Canvas, g: Double, src: Shader, lum: Int) {
        L.remplir(cv, rr(19f, 49f, 31f, 53f, 2f), src); L.remplir(cv, rr(69f, 49f, 81f, 53f, 2f), src)   // les poignées
        L.remplir(cv, rr(27f, 45f, 73f, 71f, 8f), src)                                                  // la casserole
        cv.drawLine(29f, 55f, 71f, 55f, L.gomme(1.4f))
        // le couvercle sautille trois fois, en penchant
        val saut = f(3.2 * (bosse(g, 0.7, 0.1) + bosse(g, 0.95, 0.1) + bosse(g, 1.2, 0.1)))
        val penche = f(5 * (bosse(g, 0.7, 0.1) - bosse(g, 0.95, 0.1) + bosse(g, 1.2, 0.1)))
        cv.save(); cv.translate(50f, 42f - saut); cv.rotate(penche); cv.translate(-50f, -42f)
        val couvercle = rr(28f, 39.5f, 72f, 44.5f, 2.5f); val bouton = rond(50f, 36.5f, 3.2f)
        cv.drawPath(couvercle, L.gomme(2.6f)); cv.drawPath(bouton, L.gomme(2.6f))
        L.remplir(cv, couvercle, src); L.remplir(cv, bouton, src)
        cv.restore()
        // la vapeur : trois volutes qui se dessinent en montant, puis s'effacent
        for ((x, b) in listOf(38f to 0.95, 50f to 1.2, 62f to 1.45)) {
            val u = (g - b) / 1.1
            if (u <= 0 || u >= 1) continue
            val pts = Array(13) { k -> val v = k / 12.0; floatArrayOf(f(x + 2.6 * sin(v * 7 + b * 3)), f(32 - 14 * v)) }
            val l = L.lignePartielle(pts, min(1.0, u * 1.8)) ?: continue
            calque(cv, 220 * sin(u * PI)); cv.drawPath(l, L.trait(2.4f, src)); cv.restore()
        }
        L.etincelle(cv, 76f, 27f, 4.6f, lum, bosse(g, 1.25, 0.22))
    }

    // ---------------- CAFÉ ----------------
    private fun cafe(cv: Canvas, g: Double, src: Shader, lum: Int) {
        L.remplir(cv, rr(24f, 69f, 72f, 74f, 2.5f), src)                                 // la soucoupe
        cv.drawCircle(66f, 52f, 6.5f, L.trait(3.6f, src))                                // l'anse
        val tasse = Path().apply {
            moveTo(29f, 40f); lineTo(67f, 40f); lineTo(64f, 61f); quadTo(63f, 67f, 57f, 67f)
            lineTo(39f, 67f); quadTo(33f, 67f, 32f, 61f); close()
        }
        cv.drawPath(tasse, L.gomme(2.6f)); L.remplirArrondi(cv, tasse, src, 1.5f)
        cv.drawPath(ovale(48f, 42.5f, 15f, 2.2f), L.gomme(1.3f))                         // le bord du café
        // la vapeur : deux volutes qui montent et se rejoignent en cœur (un trait qui se dessine)
        val u = cle(g, 0.5 to 0.0 by LIN, 1.6 to 1.0 by DOUX)
        val fond = 1 - cle(g, 2.2 to 0.0 by LIN, 2.7 to 1.0 by LIN)
        if (u > 0.01 && fond > 0.01) {
            val coeur = Path().apply {
                moveTo(48f, 34f); cubicTo(48f, 30f, 40f, 28f, 40f, 23f); cubicTo(40f, 18f, 46.5f, 17f, 48f, 21.5f)
                moveTo(48f, 34f); cubicTo(48f, 30f, 56f, 28f, 56f, 23f); cubicTo(56f, 18f, 49.5f, 17f, 48f, 21.5f)
            }
            calque(cv, 230 * fond)
            val pinceau = L.trait(2.6f, src)
            pinceau.pathEffect = DashPathEffect(floatArrayOf(f(u * 40), 60f), 0f)
            cv.drawPath(coeur, pinceau)
            pinceau.pathEffect = null
            cv.restore()
        }
        L.etincelle(cv, 74f, 27f, 4.6f, lum, bosse(g, 1.7, 0.22))
    }

    // ---------------- SPORT : l'haltère ----------------
    private fun sport(cv: Canvas, g: Double, src: Shader, lum: Int) {
        val h = f(cle(g, 0.5 to 0.0 by LIN, 0.85 to -9.0 by RESSORT, 1.55 to -9.0 by LIN, 1.85 to 0.0 by ACC))
        val tremble = f(0.7 * sin(g * 55) * bosse(g, 1.25, 0.25))
        val ecr = f(bosse(g, 1.92, 0.1))
        cv.save(); cv.translate(tremble, h); cv.translate(50f, 60f); cv.scale(1 + 0.05f * ecr, 1 - 0.08f * ecr); cv.translate(-50f, -60f)
        cv.drawLine(30f, 50f, 70f, 50f, L.trait(3.6f, src))
        for ((a, b, c, d) in arrayOf(floatArrayOf(23f, 37f, 30f, 63f), floatArrayOf(31.5f, 40.5f, 37.5f, 59.5f),
                floatArrayOf(70f, 37f, 77f, 63f), floatArrayOf(62.5f, 40.5f, 68.5f, 59.5f))) {
            val p = rr(a, b, c, d, 2.2f); cv.drawPath(p, L.gomme(2f)); L.remplir(cv, p, src)
        }
        cv.restore()
        val choc = bosse(g, 1.95, 0.18)                                                  // les petits traits du choc au sol
        if (choc > 0.02) {
            calque(cv, 200 * choc)
            for ((a, b, c, d) in arrayOf(floatArrayOf(34f, 70f, 28f, 74f), floatArrayOf(50f, 71f, 50f, 76f), floatArrayOf(66f, 70f, 72f, 74f)))
                cv.drawLine(a, b, c, d, L.trait(2f, src))
            cv.restore()
        }
        L.etincelle(cv, 50f, 26f, 5f, lum, bosse(g, 1.2, 0.25),
            f(cle(g, 0.75 to 0.0 by LIN, 0.9 to 1.0 by LIN, 1.6 to 1.0 by LIN, 1.8 to 0.0 by LIN)))
    }

    // ---------------- VOITURE ----------------
    private fun voiture(cv: Canvas, g: Double, src: Shader, lum: Int) {
        val roule = cle(g, 0.4 to 0.0 by LIN, 2.5 to 1.0 by DOUX)
        val cahot = f(0.8 * sin(g * 18) * sin(roule * PI))
        // la route : des pointillés qui défilent sous la voiture
        calque(cv, 120.0)
        cv.save(); cv.clipRect(20f, 70f, 80f, 80f)
        for (k in -1..4) { val x = f(20 + k * 16 - (roule * 64) % 16); cv.drawLine(x, 76f, x + 7f, 76f, L.trait(2.4f, src)) }
        cv.restore(); cv.restore()
        cv.save(); cv.translate(0f, cahot)
        L.remplirArrondi(cv, poly(33f, 49f, 39.5f, 37f, 61f, 37f, 69f, 49f), src, 4f)   // la cabine
        L.remplir(cv, rr(22f, 47f, 78f, 63f, 6.5f), src)                                  // la carrosserie
        cv.drawPath(poly(37.5f, 48f, 42f, 40f, 49f, 40f, 49f, 48f), L.gomme())             // les vitres
        cv.drawPath(poly(52f, 48f, 52f, 40f, 59.5f, 40f, 65f, 48f), L.gomme())
        lumiere(cv, rr(73.5f, 51f, 78f, 55f, 1.5f), lum, min(1.0, 0.4 + 0.6 * (bosse(g, 0.7, 0.12) + bosse(g, 0.95, 0.12))))   // le phare
        cv.restore()
        for (x in floatArrayOf(36f, 64f)) {                                              // les roues, qui tournent
            cv.drawCircle(x, 63f, 9f, L.gomme()); L.remplir(cv, rond(x, 63f, 7f), src); cv.drawCircle(x, 63f, 2.6f, L.gomme())
            cv.save(); cv.translate(x, 63f); cv.rotate(f(roule * 720))
            for (k in 0..2) { cv.rotate(120f); cv.drawLine(3.6f, 0f, 5.4f, 0f, L.gomme(1.3f)) }
            cv.restore()
        }
        L.etincelle(cv, 29f, 31f, 4.6f, lum, bosse(g, 0.95, 0.22))
    }

    // ---------------- VOYAGE : l'avion ----------------
    private val VOL: Array<FloatArray> = Array(41) { i ->
        val u = i / 40f; val v = 1 - u
        val p0 = floatArrayOf(24f, 68f); val p1 = floatArrayOf(30f, 34f); val p2 = floatArrayOf(56f, 74f); val p3 = floatArrayOf(66f, 34f)
        floatArrayOf(v * v * v * p0[0] + 3 * v * v * u * p1[0] + 3 * v * u * u * p2[0] + u * u * u * p3[0],
                     v * v * v * p0[1] + 3 * v * v * u * p1[1] + 3 * v * u * u * p2[1] + u * u * u * p3[1])
    }
    private fun avion(): Path = Path().apply {
        addRoundRect(-14f, -2.6f, 14f, 2.6f, 2.6f, 2.6f, Path.Direction.CW)
        moveTo(-1f, -2f); lineTo(-7f, -15f); lineTo(-3f, -15f); lineTo(7f, -2f); close()
        moveTo(-1f, 2f); lineTo(-7f, 15f); lineTo(-3f, 15f); lineTo(7f, 2f); close()
        moveTo(-11f, -1.5f); lineTo(-15.5f, -7.5f); lineTo(-13f, -7.5f); lineTo(-8f, -1.5f); close()
        moveTo(-11f, 1.5f); lineTo(-15.5f, 7.5f); lineTo(-13f, 7.5f); lineTo(-8f, 1.5f); close()
    }
    private fun voyage(cv: Canvas, g: Double, src: Shader, lum: Int) {
        val prog = cle(g, 0.4 to 1.0 by LIN, 0.5 to 0.0 by ACC, 1.9 to 1.0 by DOUX)
        val l = L.lignePartielle(VOL, prog)
        val fx = L.finX; val fy = L.finY                      // (où la route s'arrête : là où est l'avion)
        if (l != null) {
            calque(cv, 150.0)
            val pinceau = L.trait(2.2f, src)
            pinceau.pathEffect = DashPathEffect(floatArrayOf(3f, 3.4f), 0f)
            cv.drawPath(l, pinceau)
            pinceau.pathEffect = null
            cv.restore()
        }
        val n = (prog * 40).roundToInt().coerceIn(1, 40)
        val x = if (l != null) fx else VOL[0][0]
        val y = if (l != null) fy else VOL[0][1]
        val avant = VOL[max(0, n - 2)]
        val angle = Math.toDegrees(atan2((y - avant[1]).toDouble(), (x - avant[0]).toDouble()))
        cv.save(); cv.translate(x, y); cv.rotate(if (l != null && !angle.isNaN()) f(angle) else -60f); cv.scale(0.78f, 0.78f)
        val a = avion(); cv.drawPath(a, L.gomme(3.4f)); L.remplir(cv, a, src)
        cv.restore()
        L.etincelle(cv, 30f, 28f, 4.6f, lum, bosse(g, 2.0, 0.25))
    }

    // ---------------- ÉTUDES : le livre ----------------
    private fun page(s: Float): Path = Path().apply {
        moveTo(50f, 34f); quadTo(50 + s * 10, 28f, 50 + s * 24, 30f); lineTo(50 + s * 24, 66f); quadTo(50 + s * 10, 64f, 50f, 70f); close()
    }
    private fun ecole(cv: Canvas, g: Double, src: Shader, lum: Int) {
        L.remplir(cv, page(-1f), src); L.remplir(cv, page(1f), src)
        cv.drawLine(50f, 33f, 50f, 71f, L.gomme(1.6f))                                   // la reliure
        for (s in floatArrayOf(-1f, 1f)) for ((k, y) in floatArrayOf(42f, 49f, 56f).withIndex())
            cv.drawLine(50 + s * 6, y + 1, 50 + s * (if (k == 2) 15 else 19), y - 1, L.gomme(1.8f))   // les lignes de texte
        // deux pages qui se tournent (la page « s'aplatit » autour de la reliure, puis passe de l'autre côté)
        for ((a, b) in listOf(0.55 to 1.25, 1.2 to 1.9)) {
            val u = (g - a) / (b - a)
            if (u <= 0 || u >= 1) continue
            val k = f(cos(u * PI))
            cv.save(); cv.translate(50f, 0f); cv.scale(k, 1f); cv.translate(-50f, 0f)
            val p = page(1f)
            cv.drawPath(p, L.gomme(2f))
            cv.saveLayerAlpha(L.calque, 255)
            L.remplir(cv, p, src)
            cv.drawPath(p, L.uni(android.graphics.Color.argb(f(255 * (0.1 + 0.15 * (1 - abs(k)))).roundToInt(), 255, 255, 255)))
            cv.restore()
            cv.restore()
        }
        L.etincelle(cv, 76f, 27f, 4.6f, lum, bosse(g, 1.95, 0.22))
    }

    // ---------------- ANIMAUX : la patte ----------------
    private fun animaux(cv: Canvas, g: Double, src: Shader, lum: Int) {
        val pose = f(bosse(g, 0.6, 0.14))
        cv.save(); cv.translate(50f, 70f); cv.scale(1 + 0.05f * pose, 1 - 0.07f * pose); cv.translate(-50f, -70f)
        L.remplir(cv, ovale(50f, 58f, 13f, 11f), src)
        val doigts = arrayOf(floatArrayOf(34.5f, 44f, -25f), floatArrayOf(44f, 35.5f, -8f), floatArrayOf(56f, 35.5f, 8f), floatArrayOf(65.5f, 44f, 25f))
        for ((i, d) in doigts.withIndex()) {
            val s = f(1 + 0.22 * bosse(g, 0.85 + 0.14 * i, 0.12))
            cv.save(); cv.translate(d[0], d[1]); cv.scale(s, s); L.remplir(cv, ovale(0f, 0f, 5.4f, 6.6f, d[2]), src); cv.restore()
        }
        cv.restore()
        val c = f(cle(g, 1.45 to 0.0 by LIN, 1.75 to 1.0 by RESSORT, 2.5 to 1.0 by LIN, 2.8 to 0.0 by ACC))
        if (c > 0.02f) {                                                               // le petit cœur
            val coeur = Path().apply { moveTo(0f, 4f); cubicTo(-7f, -1f, -4f, -7f, 0f, -3.5f); cubicTo(4f, -7f, 7f, -1f, 0f, 4f) }
            cv.save(); cv.translate(70f, 25f); cv.scale(c, c); cv.drawPath(coeur, L.uni(lum)); cv.restore()
        }
        L.etincelle(cv, 29f, 27f, 4.4f, lum, bosse(g, 1.1, 0.22))
    }

    // ---------------- PLANTE ----------------
    private fun feuille(cv: Canvas, x: Float, y: Float, angle: Float, s: Float, src: Shader) {
        if (s <= 0.02f) return
        cv.save(); cv.translate(x, y); cv.rotate(angle); cv.scale(s, s)
        L.remplir(cv, Path().apply { moveTo(0f, 0f); quadTo(7f, -7f, 15f, -1f); quadTo(7f, 5f, 0f, 0f) }, src)
        cv.drawLine(1.5f, 0f, 11f, -1f, L.gomme(1f))
        cv.restore()
    }
    private fun plante(cv: Canvas, g: Double, src: Shader, lum: Int) {
        L.remplir(cv, poly(36f, 57f, 64f, 57f, 61f, 74f, 39f, 74f), src)                // le pot
        L.remplir(cv, rr(32.5f, 53f, 67.5f, 58.5f, 2f), src)
        cv.drawLine(34f, 58.6f, 66f, 58.6f, L.gomme(1.4f))
        val tige = cle(g, 0.4 to 1.0 by LIN, 0.5 to 0.0 by ACC, 1.2 to 1.0 by DOUX)
        val pts = Array(13) { k -> val v = k / 12.0; floatArrayOf(f(50 + 3 * sin(v * 3)), f(53 - 25 * v)) }
        L.lignePartielle(pts, tige)?.let { cv.drawPath(it, L.trait(3.2f, src)) }
        val vent = f(4 * sin(g * 3))
        feuille(cv, 50.5f, 45f, 200f + vent, f(cle(g, 0.4 to 1.0 by LIN, 0.5 to 0.0 by ACC, 0.75 to 0.0 by LIN, 1.05 to 1.0 by RESSORT)), src)
        feuille(cv, 52f, 38f, -20f - vent, f(cle(g, 0.4 to 1.0 by LIN, 0.5 to 0.0 by ACC, 0.95 to 0.0 by LIN, 1.25 to 1.0 by RESSORT)), src)
        val bourgeon = cle(g, 0.4 to 1.0 by LIN, 0.5 to 0.0 by ACC, 1.2 to 0.0 by LIN, 1.55 to 1.0 by RESSORT)
        L.etincelle(cv, 52.8f, 27f, f(5.2 * bourgeon), lum, bosse(g, 1.75, 0.25))
    }

    // ---------------- MÉTÉO ----------------
    private fun meteo(cv: Canvas, g: Double, src: Shader, lum: Int) {
        val d = f(cle(g, 0.5 to 0.0 by LIN, 1.1 to -10.0 by DOUX, 2.1 to -10.0 by LIN, 2.7 to 0.0 by DOUX))
        val rayons = cle(g, 0.7 to 0.0 by LIN, 1.2 to 1.0 by RESSORT, 2.0 to 1.0 by LIN, 2.5 to 0.0 by DOUX)
        // le soleil (couleur claire), et ses rayons qui s'allongent en tournant
        cv.save(); cv.translate(61f, 37f); cv.rotate(f(g * 25))
        cv.drawCircle(0f, 0f, 9.5f, L.uni(lum))
        if (rayons > 0.02) {
            q.reset(); q.isAntiAlias = true; q.style = Paint.Style.STROKE; q.strokeWidth = 2.4f; q.strokeCap = Paint.Cap.ROUND; q.color = lum
            for (k in 0..7) {
                val a = k * PI / 4
                cv.drawLine(f(cos(a) * 12.5), f(sin(a) * 12.5), f(cos(a) * (12.5 + 4.5 * rayons)), f(sin(a) * (12.5 + 4.5 * rayons)), q)
            }
        }
        cv.restore()
        // le nuage, détaché du soleil par un liseré découpé
        cv.save(); cv.translate(d, 0f)
        val nuage = listOf(rond(38f, 52f, 10f), rond(50f, 45f, 12.5f), rond(62f, 53f, 9f), rr(28f, 52f, 72f, 66f, 7f))
        for (p in nuage) cv.drawPath(p, L.gomme(4f))
        for (p in nuage) L.remplir(cv, p, src)
        cv.restore()
        L.etincelle(cv, 27f, 30f, 4.4f, lum, bosse(g, 1.2, 0.22))
    }

    // ---------------- NUIT ----------------
    private fun nuit(cv: Canvas, g: Double, src: Shader, lum: Int) {
        val berce = f(6 * sin(g * 2.2) * ((g - 0.3) / 0.5).coerceIn(0.0, 1.0))
        cv.save(); cv.translate(44f, 50f); cv.rotate(berce - 12f); cv.translate(-44f, -50f)
        L.remplir(cv, rond(44f, 50f, 21f), src); cv.drawCircle(54f, 42f, 17f, L.gomme())   // le croissant
        cv.restore()
        for ((i, b) in listOf(0 to 0.7, 1 to 1.1, 2 to 1.5)) {                              // les « z » qui montent
            val u = (g - b) / 1.2
            if (u <= 0 || u >= 1) continue
            val s = 3f + 1.4f * i
            val x = f(61 + 4 * i + 2 * sin(u * 5)); val y = f(50 - 7 * i - 10 * u)
            calque(cv, 255 * sin(u * PI))
            cv.drawPath(poly(x - s, y - s, x + s, y - s, x - s, y + s, x + s, y + s, ferme = false), L.trait(2f, src))
            cv.restore()
        }
        L.etincelle(cv, 72f, 28f, 4f, lum, bosse(g, 1.0, 0.2))
        L.etincelle(cv, 30f, 26f, 3f, lum, bosse(g, 1.6, 0.2))
    }

    // ---------------- FÊTE : deux flûtes qui trinquent ----------------
    private fun flute(cv: Canvas, x: Float, angle: Float, src: Shader) {
        cv.save(); cv.translate(x, 50f); cv.rotate(angle)
        val coupe = Path().apply { moveTo(-6.5f, -18f); lineTo(6.5f, -18f); lineTo(4.4f, 0f); quadTo(0f, 4.5f, -4.4f, 0f); close() }
        cv.drawPath(coupe, L.gomme(2.6f)); L.remplir(cv, coupe, src)
        cv.drawLine(-5.5f, -11f, 5.5f, -11f, L.gomme(1.2f))                              // la surface du champagne
        cv.drawLine(0f, 3f, 0f, 16f, L.trait(2.2f, src)); cv.drawLine(-6f, 17f, 6f, 17f, L.trait(2.6f, src))
        cv.restore()
    }
    private fun fete(cv: Canvas, g: Double, src: Shader, lum: Int) {
        val t = f(cle(g, 0.5 to 0.0 by LIN, 0.95 to 1.0 by ACC, 1.05 to 0.82 by DEC, 1.6 to 0.9 by LIN, 2.1 to 0.0 by DOUX))
        flute(cv, 39 + 3 * t, -4 + 16 * t, src); flute(cv, 61 - 3 * t, 4 - 16 * t, src)
        for (k in 0..4) {                                                               // les bulles
            val u = (g - (1.0 + 0.1 * k)) / 1.0
            if (u <= 0 || u >= 1) continue
            lumiere(cv, rond(f(50 + (k - 2) * 4 + 2 * sin(u * 6 + k)), f(30 - 12 * u), 1.3f + 0.4f * (k % 2)), lum, sin(u * PI))
        }
        val r = cle(g, 0.5 to 0.5 by LIN, 0.9 to 0.0 by LIN, 1.0 to 1.0 by RESSORT, 1.7 to 1.0 by LIN, 2.0 to 0.5 by LIN)
        L.etincelle(cv, 50f, 29f, f(5.4 * r), lum, bosse(g, 1.1, 0.2))
    }

    // ---------------- TIRELIRE ----------------
    private fun tirelire(cv: Canvas, g: Double, src: Shader, lum: Int) {
        val saut = f(cle(g, 1.0 to 0.0 by LIN, 1.17 to -4.5 by DEC, 1.38 to 0.0 by ACC))
        val ecr = f(bosse(g, 1.43, 0.1))
        val queue = f(18 * sin(g * 16) * bosse(g, 1.5, 0.35))
        cv.save(); cv.translate(0f, saut); cv.translate(50f, 73f); cv.scale(1 + 0.05f * ecr, 1 - 0.07f * ecr); cv.translate(-50f, -73f)
        calque(cv, 170.0)                                                                // les pattes du fond (plus pâles)
        L.remplir(cv, rr(33f, 63f, 38.5f, 72.5f, 2.2f), src); L.remplir(cv, rr(57f, 63f, 62.5f, 72.5f, 2.2f), src)
        cv.restore()
        L.remplir(cv, rr(28.5f, 64f, 34f, 74f, 2.2f), src); L.remplir(cv, rr(52.5f, 64f, 58f, 74f, 2.2f), src)   // les pattes de devant
        cv.save(); cv.translate(26.5f, 52f); cv.rotate(queue)                            // la queue en tire-bouchon
        cv.drawPath(Path().apply { moveTo(0f, 0f); cubicTo(-5f, -1f, -6f, -7f, -2f, -7f); cubicTo(1f, -7f, 1f, -3f, -2f, -3.5f) }, L.trait(2f, src))
        cv.restore()
        L.remplirArrondi(cv, poly(56.5f, 41f, 61f, 31.5f, 66.5f, 42f), src, 2.6f)        // l'oreille
        val corps = ovale(47f, 55f, 21.5f, 15.5f); cv.drawPath(corps, L.gomme(2.2f)); L.remplir(cv, corps, src)
        L.remplir(cv, rond(61f, 51.5f, 11.5f), src)                                      // la tête
        val groin = ovale(72f, 55f, 4.4f, 5.4f); cv.drawPath(groin, L.gomme(2.2f)); L.remplir(cv, groin, src)
        cv.drawPath(ovale(71.4f, 53.2f, 0.9f, 1.4f), L.gomme()); cv.drawPath(ovale(73f, 57f, 0.9f, 1.4f), L.gomme())
        cv.drawCircle(64f, 48.5f, 1.6f, L.gomme())                                       // l'œil
        val rose = bosse(g, 1.6, 0.4)                                                    // les joues qui rosissent
        if (rose > 0.02) lumiere(cv, ovale(63.5f, 56f, 3f, 2f), lum, 0.45 * rose)
        cv.drawPath(rr(40f, 39.6f, 52f, 42.6f, 1.5f), L.gomme())                         // la fente
        cv.restore()
        val onde = (g - 0.97) / 0.5                                                      // l'onde qui part de la fente
        if (onde > 0 && onde < 1) {
            calque(cv, 200 * (1 - onde))
            q.reset(); q.isAntiAlias = true; q.style = Paint.Style.STROKE; q.strokeWidth = 1.4f; q.color = lum
            cv.drawPath(ovale(46f, 41f, f(6 + 9 * onde), f(1.6 + 3 * onde)), q)
            cv.restore()
        }
        val chute = cle(g, 0.55 to 0.0 by LIN, 0.97 to 1.0 by ACC)
        if (chute < 0.99) {                                                              // la pièce, qui tournoie en tombant
            val tourne = f(abs(cos(chute * 9)) * 0.85 + 0.15)
            val r = f(1 - 0.25 * chute)
            cv.save(); cv.translate(46f, f(19 + 22 * chute)); cv.scale(tourne * r, r)
            cv.drawCircle(0f, 0f, 5.4f, L.uni(lum)); cv.drawCircle(0f, 0f, 3.6f, L.gomme(1f)); cv.drawLine(0f, -1.8f, 0f, 1.8f, L.gomme(1.2f))
            cv.restore()
        }
        L.etincelle(cv, 53.5f, 15.5f, 3.6f, lum, bosse(g, 0.45, 0.15), f(1 - cle(g, 0.55 to 0.0 by LIN, 0.7 to 1.0 by LIN)))
    }
}
