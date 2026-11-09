package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// ===================== DISCORD : « Setup gaming » =====================
// Une vraie petite scène en 3D (perspective cohérente) : on est assis devant un bureau de gamer, la nuit.
// Trois écrans : un jeu à gauche, un tableau de bord de live à droite, et au centre (vertical, au format
// du téléphone) une messagerie de jeu. Mur en lames de bois éclairé par l'arrière des écrans, néon « GG »,
// étagères avec LED, clavier RGB en 3D, tasse fumante, casque gaming.
// La caméra AVANCE vers l'écran central : les objets proches défilent plus vite (parallaxe), la mise au
// point glisse vers l'écran (premier plan et fond deviennent flous), et l'écran devient l'app.
// Unités de la scène : le centimètre. Une « caméra » transforme chaque point (x, y, z) en point de l'écran.
private fun dHexa(s: String) = Color.parseColor(s)
private fun dTeinte(h: Float, s: Float = 0.75f, v: Float = 1f): Int = Color.HSVToColor(floatArrayOf(modulo(h, 1f) * 360f, s, v))
private fun dMelange(a: Int, b: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    return Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
    )
}
private val D_ACCENT = Color.rgb(106, 111, 242)

class SetupGaming : Scene() {
    // --- dimensions de la scène (en cm)
    private val bureauY = 75f
    private val murZ = 125f
    private val cenW = 34f; private val cenY0 = 95f; private val cenZ = 95f
    private val theta = Math.toRadians(36.0).toFloat()
    private val latW = 60f; private val latH = 34f; private val latY0 = 104f

    // --- la caméra (mise à jour à chaque image)
    private var camX = 0f; private var camY = 115f; private var camZ = 0f
    private var focale = 1050f; private var lw = 1080f; private var lh = 2340f
    private var u = 1f

    private fun ledH(t: Float) = 0.74f + 0.08f * sin(t * 0.9f)      // l'ambiance hésite entre violet et bleu

    // Projection : un point 3D -> un point de l'écran (plus c'est loin, plus c'est petit et proche du centre).
    private fun sx(x: Float, z: Float) = lw / 2f + focale * (x - camX) / max(z - camZ, 0.5f)
    private fun sy(y: Float, z: Float) = lh / 2f - focale * (y - camY) / max(z - camZ, 0.5f)
    private fun echelle(z: Float) = focale / max(z - camZ, 0.5f)    // pixels pour 1 cm à cette distance

    // --- pinceaux et objets réutilisés
    private val chemin = Path()
    private val ov = RectF()
    private val pl = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tr = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    // texte sans « accrochage » aux pixels : sinon il tremble pendant le zoom
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { isSubpixelText = true; isLinearText = true }
    private val matrice = Matrix()
    private val noeuds = arrayOfNulls<Any>(2)                     // « nœuds » de dessin pour le flou (Android 12+)

    // éléments tirés au hasard une fois pour toutes
    private val livres: List<FloatArray> = run {
        val r = Random(3)
        List(5) { floatArrayOf(2.2f + r.nextFloat() * 1.2f, 15f + r.nextFloat() * 7f, r.nextInt(5).toFloat()) }
    }
    private val couleursLivres = intArrayOf(dHexa("#7a3a5a"), dHexa("#3a4a8a"), dHexa("#2c6a5a"), dHexa("#8a6a2a"), dHexa("#5a3a8a"))
    private val poussiere: List<FloatArray> = run {
        val r = Random(5)
        List(110) { floatArrayOf(r.nextFloat() * 120f - 60f, 80f + r.nextFloat() * 140f, 40f + r.nextFloat() * 80f, r.nextFloat() * 6f) }
    }

    // Un polygone 3D -> un chemin à l'écran, en coupant proprement ce qui passe derrière la caméra.
    private fun poly3(vararg p: Float): Boolean {
        chemin.reset()
        val n = p.size / 3; val zn = camZ + 1f
        var cnt = 0
        fun ajoute(x: Float, y: Float, z: Float) {
            val a = sx(x, z); val b = sy(y, z)
            if (cnt == 0) chemin.moveTo(a, b) else chemin.lineTo(a, b); cnt++
        }
        for (i in 0 until n) {
            val j = (i + 1) % n
            val ax = p[i * 3]; val ay = p[i * 3 + 1]; val az = p[i * 3 + 2]
            val bx = p[j * 3]; val by = p[j * 3 + 1]; val bz = p[j * 3 + 2]
            val ina = az > zn; val inb = bz > zn
            if (ina) ajoute(ax, ay, az)
            if (ina != inb) { val t = (zn - az) / (bz - az); ajoute(ax + (bx - ax) * t, ay + (by - ay) * t, zn) }
        }
        if (cnt < 3) return false
        chemin.close(); return true
    }
    private fun remplir(c: Canvas, couleur: Int) { pl.shader = null; pl.color = couleur; c.drawPath(chemin, pl) }
    private fun halo(c: Canvas, o: Outils, x: Float, y: Float, rayon: Float, a: Float, col: Int) = o.halo(c, x, y, rayon / 2f, a, col)

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val sec = i.secondes; val rev = i.revelation
        lw = c.width.toFloat(); lh = c.height.toFloat()
        u = lw / 1080f
        focale = 1050f * u
        val app = ralentit(i.intro)
        // la caméra : elle avance jusqu'à ce que l'écran central remplisse EXACTEMENT le téléphone
        val cenH = cenW * lh / lw
        val z = doux(borne((sec - 0.15f) / 1.05f))
        val zFin = cenZ - focale * cenH / lh
        camZ = mix(0f, zFin, z.pow(1.15f))
        camY = mix(115f, cenY0 + cenH / 2f, z.pow(0.9f))
        camX = mix(-6f, 0f, doux(borne(sec / 1.0f)))               // petit glissement : la parallaxe se voit dès le départ

        c.drawColor(dHexa("#0b0a1a"))
        couche(c, (1.2f + 10f * z) * u, 0) { cc -> mur(cc, o, sec); neon(cc, o, sec); etageres(cc, o, sec) }   // le fond devient flou
        c.drawColor(avecAlpha(dHexa("#1a1030"), 0.20f + 0.15f * z))                                          // brume légère
        bureau(c, o, sec); casque(c, o, sec); ecrans(c, o, sec, cenH)
        couche(c, (1.5f + 22f * z) * u, 1) { cc -> tasse(cc, sec); clavier(cc, sec); souris(cc, sec) }      // premier plan flou
        // poussière en suspension à différentes profondeurs (les proches défilent vite)
        for (q in poussiere) {
            val d = q[2]
            if (d - camZ < 3f) continue
            val x = sx(q[0] + sin(sec * 0.7f + q[3]) * 2f, d); val y = sy(q[1] - sec * 2f, d)
            if (x < 0f || x > lw || y < 0f || y > lh) continue
            pl.shader = null
            pl.color = avecAlpha(Color.rgb(255, 235, 255), 0.35f * (0.6f + 0.4f * sin(sec * 2f + q[3])) * borne((d - camZ) / 20f) * (1f - z))
            c.drawCircle(x, y, max(0.8f * u, 0.12f * echelle(d)), pl)
        }
        // vignette de cinéma (s'efface à l'arrivée sur l'écran)
        pl.shader = RadialGradient(lw / 2f, lh / 2f, lh * 0.72f, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, avecAlpha(Color.BLACK, 0.6f * (1f - z))),
            floatArrayOf(0f, 0.39f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, lw, lh, pl); pl.shader = null
        // l'app remplace l'écran (fondu doux)
        if (rev > 0f) c.drawColor(avecAlpha(Color.BLACK, ralentit(rev)), PorterDuff.Mode.DST_OUT)
        if (i.intro < 1f) c.drawColor(avecAlpha(Color.BLACK, 1f - app))
    }

    // Dessine une couche, floutée si possible (Android 12+ : « nœud de rendu » + effet de flou).
    private fun couche(c: Canvas, flou: Float, idx: Int, dessin: (Canvas) -> Unit) {
        if (flou > 0.5f && Build.VERSION.SDK_INT >= 31 && c.isHardwareAccelerated) {
            val nd = (noeuds[idx] as? RenderNode) ?: RenderNode("setup$idx").also { noeuds[idx] = it }
            nd.setPosition(0, 0, lw.toInt(), lh.toInt())
            nd.setRenderEffect(RenderEffect.createBlurEffect(flou, flou, Shader.TileMode.DECAL))
            val rc = nd.beginRecording()
            try { dessin(rc) } finally { nd.endRecording() }
            c.drawRenderNode(nd)
        } else dessin(c)
    }

    // ------------------------------------------------------------------ le mur, le néon, les étagères
    private fun mur(c: Canvas, o: Outils, t: Float) {
        if (poly3(-200f, 0f, murZ, 200f, 0f, murZ, 200f, 320f, murZ, -200f, 320f, murZ)) remplir(c, dHexa("#0d0b18"))
        val led = dTeinte(ledH(t), 0.65f)
        val clair = dHexa("#1a1426"); val sombre = dHexa("#120e1c")
        for (k in -34 until 34) {                                     // lames de bois verticales
            val x0 = k * 6f
            if (poly3(x0 + 0.6f, bureauY, murZ, x0 + 5.4f, bureauY, murZ, x0 + 5.4f, 320f, murZ, x0 + 0.6f, 320f, murZ))
                remplir(c, dMelange(clair, sombre, borne(kotlin.math.abs(x0 + 3f) / 120f) * 0.8f))
        }
        val e = echelle(murZ)
        val x = sx(0f, murZ); val y = sy(128f, murZ)
        halo(c, o, x, y, 95f * e, 0.40f, led)                         // la grande lueur derrière les écrans
        halo(c, o, x, y - 20f * e, 55f * e, 0.25f, dTeinte(ledH(t) - 0.08f, 0.6f))
        for (cote in intArrayOf(-1, 1)) halo(c, o, sx(cote * 62f, murZ), sy(120f, murZ), 60f * e, 0.28f, dTeinte(ledH(t) + cote * 0.06f, 0.7f))
    }

    private fun neon(c: Canvas, o: Outils, t: Float) {
        val pul = 0.85f + 0.15f * sin(t * 2.3f)
        val e = echelle(murZ - 2f)
        val ox = sx(-14f, murZ - 2f); val oy = sy(228f, murZ - 2f)
        val rose = dHexa("#ff4fd8"); val cyan = dHexa("#38e1ff")
        val gg = Path()
        for (gx in floatArrayOf(0f, 120f)) {
            gg.addArc(RectF(gx + 4f, 4f, gx + 96f, 96f), -31.5f, -291.25f)
            gg.moveTo(gx + 92f, 72f); gg.lineTo(gx + 92f, 52f); gg.lineTo(gx + 62f, 52f)
        }
        val eclair = Path(); eclair.moveTo(262f, -6f); eclair.lineTo(236f, 54f); eclair.lineTo(258f, 54f); eclair.lineTo(240f, 108f)
        c.save(); c.translate(ox, oy); c.scale(e / 10f, e / 10f)
        for ((p, col) in listOf(gg to rose, eclair to cyan)) {
            for ((lwd, a) in listOf(24f to 0.10f, 12f to 0.25f, 5f to 1f)) {
                tr.shader = null; tr.strokeWidth = lwd
                tr.color = avecAlpha(if (lwd == 5f) dMelange(col, Color.WHITE, 0.55f) else col, a * pul)
                c.drawPath(p, tr)
            }
        }
        c.restore()
        halo(c, o, ox + 13f * e, oy + 5f * e, 30f * e, 0.22f * pul, rose)
    }

    private fun etageres(c: Canvas, o: Outils, t: Float) {
        for (cote in intArrayOf(-1, 1)) {
            val y = 186f
            val a = min(cote * 40f, cote * 110f); val b = max(cote * 40f, cote * 110f)
            if (poly3(a, y, murZ - 12f, b, y, murZ - 12f, b, y, murZ, a, y, murZ)) remplir(c, dHexa("#241a1e"))
            if (poly3(a, y - 2.5f, murZ - 12f, b, y - 2.5f, murZ - 12f, b, y, murZ - 12f, a, y, murZ - 12f)) remplir(c, dHexa("#1c1414"))
            val x0 = sx(a + 2f, murZ - 11f); val y0 = sy(y - 2.6f, murZ - 11f); val x1 = sx(b - 2f, murZ - 11f)
            val col = dTeinte(ledH(t) + cote * 0.1f, 0.7f)
            pl.shader = null; pl.color = avecAlpha(col, 0.9f); c.drawRect(x0, y0, x1, y0 + 2.5f * u, pl)        // LED sous l'étagère
            halo(c, o, (x0 + x1) / 2f, y0 + 40f * u, (x1 - x0) * 0.6f, 0.20f, col)
            val z = murZ - 6f
            if (cote == -1) {
                var x = -48f
                for (l in livres) {
                    if (poly3(x - l[0], y, z, x, y, z, x, y + l[1], z, x - l[0], y + l[1], z)) remplir(c, couleursLivres[l[2].toInt()])
                    x -= l[0] + 0.4f
                }
                val px = -92f
                if (poly3(px - 5f, y, z, px + 5f, y, z, px + 6f, y + 9f, z, px - 6f, y + 9f, z)) remplir(c, dHexa("#c97a52"))
                val e = echelle(z); val bx = sx(px, z); val by = sy(y + 11f, z)
                for (k in 0 until 9) {                                 // feuilles de la plante
                    val ang = -2.6f + k * 0.33f; val l = (6f + (k % 3) * 2.5f) * e
                    c.save(); c.translate(bx + cos(ang) * l, by + sin(ang) * l * 0.8f)
                    c.rotate((ang + sin(t * 1.5f + k) * 0.05f) * 57.2958f); c.scale(1f, 0.45f)
                    pl.color = dMelange(dHexa("#2f8a5a"), dHexa("#5fd08a"), (k % 3) / 2f); c.drawCircle(0f, 0f, 3.2f * e, pl)
                    c.restore()
                }
            } else {
                val rx = 56f; val e = echelle(z)
                val corps = dHexa("#c8c4e8")
                if (poly3(rx - 4f, y, z, rx + 4f, y, z, rx + 4f, y + 8f, z, rx - 4f, y + 8f, z)) remplir(c, corps)
                if (poly3(rx - 5.5f, y + 8.5f, z, rx + 5.5f, y + 8.5f, z, rx + 5.5f, y + 16f, z, rx - 5.5f, y + 16f, z)) remplir(c, corps)
                for (ex in floatArrayOf(-2.4f, 2.4f)) {                // yeux lumineux du petit robot
                    val ax = sx(rx + ex, z - 0.1f); val ay = sy(y + 12.3f, z - 0.1f)
                    pl.color = dHexa("#38e1ff"); c.drawCircle(ax, ay, 1f * e, pl); halo(c, o, ax, ay, 4f * e, 0.35f, dHexa("#38e1ff"))
                }
                val sp = 82f                                           // petite enceinte
                if (poly3(sp - 6f, y, z, sp + 6f, y, z, sp + 6f, y + 18f, z, sp - 6f, y + 18f, z)) remplir(c, dHexa("#1a1a22"))
                for ((cy, rr) in listOf(y + 5f to 3.6f, y + 13f to 2.2f)) {
                    val ax = sx(sp, z - 0.1f); val ay = sy(cy, z - 0.1f)
                    pl.color = dHexa("#2a2a36"); c.drawCircle(ax, ay, rr * e, pl)
                    tr.shader = null; tr.color = avecAlpha(dTeinte(ledH(t), 0.7f), 0.8f); tr.strokeWidth = 1.5f * u; c.drawCircle(ax, ay, rr * e, tr)
                }
            }
        }
    }

    // ------------------------------------------------------------------ le bureau
    private fun ombre(c: Canvas, x: Float, z: Float, rx: Float, rz: Float, a: Float) {
        val ax = sx(x, z); val ay = sy(bureauY + 0.2f, z); val e = echelle(z)
        c.save(); c.translate(ax, ay); c.scale(1f, max(0.15f, rz / rx * 0.5f))
        pl.shader = RadialGradient(0f, 0f, rx * e, avecAlpha(Color.BLACK, a), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, rx * e, pl); pl.shader = null
        c.restore()
    }

    private fun bureau(c: Canvas, o: Outils, t: Float) {
        if (poly3(-130f, bureauY, 38f, 130f, bureauY, 38f, 130f, bureauY, murZ, -130f, bureauY, murZ)) {
            val yb = sy(bureauY, murZ); val yf = sy(bureauY, 38f)
            pl.shader = LinearGradient(0f, yb, 0f, max(yf, yb + 1f), dHexa("#1c1420"), dHexa("#34262e"), Shader.TileMode.CLAMP)
            c.drawPath(chemin, pl); pl.shader = null
        }
        // la lumière des écrans posée sur le bureau
        var e = echelle(92f)
        c.save(); c.translate(sx(0f, 92f), sy(bureauY, 92f)); c.scale(1f, 0.32f)
        halo(c, o, 0f, 0f, 70f * e, 0.28f, dMelange(D_ACCENT, Color.WHITE, 0.2f)); c.restore()
        for (cote in intArrayOf(-1, 1)) {
            e = echelle(80f)
            c.save(); c.translate(sx(cote * 52f, 80f), sy(bureauY, 80f)); c.scale(1f, 0.3f)
            halo(c, o, 0f, 0f, 50f * e, 0.16f, if (cote < 0) dHexa("#8a7cff") else dHexa("#ff6aa8")); c.restore()
        }
        // tapis de souris avec son contour RGB
        val yv = bureauY + 0.15f
        if (poly3(-42f, yv, 42f, 42f, yv, 42f, 42f, yv, 82f, -42f, yv, 82f)) remplir(c, dHexa("#14121e"))
        val n = 48
        for (k in 0 until n) {
            val x0 = -42f + 84f * k / n; val x1 = -42f + 84f * (k + 1) / n
            if (poly3(x0, yv + 0.05f, 42f, x1, yv + 0.05f, 42f, x1, yv + 0.05f, 43.2f, x0, yv + 0.05f, 43.2f))
                remplir(c, avecAlpha(dTeinte(k.toFloat() / n - t * 0.3f, 0.8f), 0.95f))
        }
        for ((xx, h0) in listOf(-42f to 0f, 41f to 0.5f)) {
            for (k in 0 until 20) {
                val z0 = 42f + 40f * k / 20; val z1 = 42f + 40f * (k + 1) / 20
                if (poly3(xx, yv + 0.05f, z0, xx + 1f, yv + 0.05f, z0, xx + 1f, yv + 0.05f, z1, xx, yv + 0.05f, z1))
                    remplir(c, avecAlpha(dTeinte(h0 + k / 40f - t * 0.3f, 0.8f), 0.9f))
            }
        }
    }

    private fun pied(c: Canvas, x: Float, z: Float) {
        ombre(c, x, z, 18f, 10f, 0.6f)
        if (poly3(x - 13f, bureauY + 0.6f, z - 6f, x + 13f, bureauY + 0.6f, z - 6f, x + 13f, bureauY + 0.6f, z + 6f, x - 13f, bureauY + 0.6f, z + 6f)) remplir(c, dHexa("#1a1a22"))
        if (poly3(x - 2f, bureauY, z + 3f, x + 2f, bureauY, z + 3f, x + 2f, cenY0 + 10f, z + 3f, x - 2f, cenY0 + 10f, z + 3f)) remplir(c, dHexa("#15151c"))
    }

    // Les 4 coins d'un écran latéral (bas-int, bas-ext, haut-ext, haut-int), chacun (x, y, z). cote = -1 gauche, +1 droite.
    private fun lateral(cote: Int): FloatArray {
        val xi = cote * (cenW / 2f + 3.5f); val zi = cenZ
        val xe = xi + cote * latW * cos(theta); val ze = zi - latW * sin(theta)
        return floatArrayOf(xi, latY0, zi, xe, latY0, ze, xe, latY0 + latH, ze, xi, latY0 + latH, zi)
    }

    private fun ecrans(c: Canvas, o: Outils, t: Float, cenH: Float) {
        pied(c, 0f, cenZ + 4f)
        for (cote in intArrayOf(-1, 1)) {
            val q = lateral(cote)
            val mx = (q[0] + q[3]) / 2f; val mz = (q[2] + q[5]) / 2f
            if (poly3(mx - 2f, bureauY, mz + 3f, mx + 2f, bureauY, mz + 3f, mx + 2f, latY0 + 5f, mz + 3f, mx - 2f, latY0 + 5f, mz + 3f)) remplir(c, dHexa("#15151c"))
            ombre(c, mx, mz + 3f, 16f, 9f, 0.55f)
        }
        // écrans latéraux : épaisseur + cadre + contenu plaqué en VRAIE perspective
        for (cote in intArrayOf(-1, 1)) {
            val q = lateral(cote)
            val cadre = 1.3f
            // q : bi(0..2) be(3..5) he(6..8) hi(9..11)
            if (poly3(q[3], q[4] - cadre, q[5], q[3] + cote * 0.4f, q[4] - cadre, q[5] + 2.5f, q[6] + cote * 0.4f, q[7] + cadre, q[8] + 2.5f, q[6], q[7] + cadre, q[8]))
                remplir(c, dHexa("#0c0c12"))
            val ux = (q[3] - q[0]) / latW; val uz = (q[5] - q[2]) / latW
            if (poly3(q[0] - ux * cadre, q[1] - cadre, q[2] - uz * cadre, q[3] + ux * cadre, q[4] - cadre, q[5] + uz * cadre,
                      q[6] + ux * cadre, q[7] + cadre, q[8] + uz * cadre, q[9] - ux * cadre, q[10] + cadre, q[11] - uz * cadre))
                remplir(c, dHexa("#1c1c26"))
            // on n'affiche l'image que si les 4 coins sont bien devant la caméra
            if (minOf(q[2], q[5], q[8], q[11]) - camZ > 2f) {
                // coins de l'image dans l'ordre haut-gauche, haut-droite, bas-droite, bas-gauche (à l'écran)
                val coins = if (cote < 0) intArrayOf(6, 9, 0, 3) else intArrayOf(9, 6, 3, 0)
                val dst = FloatArray(8)
                for (k in 0 until 4) { val b = coins[k]; dst[k * 2] = sx(q[b], q[b + 2]); dst[k * 2 + 1] = sy(q[b + 1], q[b + 2]) }
                val sw = 600f; val sh = 340f
                matrice.setPolyToPoly(floatArrayOf(0f, 0f, sw, 0f, sw, sh, 0f, sh), 0, dst, 0, 4)
                c.save(); c.concat(matrice); c.clipRect(0f, 0f, sw, sh)
                if (cote < 0) jeu(c, o, t, sw, sh) else stream(c, t, sw, sh)
                c.restore()
                // reflet sur la vitre
                if (poly3(q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7], q[8], q[9], q[10], q[11])) {
                    pl.shader = LinearGradient(sx(q[9], q[11]), sy(q[10], q[11]), sx(q[3], q[5]), sy(q[4], q[5]),
                        intArrayOf(avecAlpha(Color.WHITE, 0.07f), Color.TRANSPARENT, avecAlpha(Color.WHITE, 0.03f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
                    c.drawPath(chemin, pl); pl.shader = null
                }
            }
        }
        // écran central, face à nous : la messagerie, dessinée en vectoriel (nette même de très près)
        val cadre = 1.2f
        if (poly3(-cenW / 2f - cadre, cenY0 - cadre, cenZ, cenW / 2f + cadre, cenY0 - cadre, cenZ,
                  cenW / 2f + cadre, cenY0 + cenH + cadre, cenZ, -cenW / 2f - cadre, cenY0 + cenH + cadre, cenZ)) remplir(c, dHexa("#1c1c26"))
        val x0 = sx(-cenW / 2f, cenZ); val y0 = sy(cenY0 + cenH, cenZ); val x1 = sx(cenW / 2f, cenZ); val y1 = sy(cenY0, cenZ)
        c.save(); c.clipRect(x0, y0, x1, y1); c.translate(x0, y0)
        messagerie(c, t, x1 - x0, y1 - y0)
        c.restore()
        pl.shader = LinearGradient(x0, y0, x1, y1, intArrayOf(avecAlpha(Color.WHITE, 0.06f), Color.TRANSPARENT, avecAlpha(Color.WHITE, 0.02f)),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(x0, y0, x1, y1, pl); pl.shader = null
    }

    // ------------------------------------------------------------------ contenus des écrans
    private fun ecrire(c: Canvas, s: String, x: Float, y: Float, taille: Float, col: Int, gras: Boolean = false, a: Float = 1f) {
        txt.typeface = if (gras) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        txt.textSize = taille; txt.color = avecAlpha(col, a * Color.alpha(col) / 255f)
        c.drawText(s, x, y, txt)
    }

    private val messages = listOf(
        arrayOf("Nova", "#ff7ab8", "anyone up for a raid tonight?", "0"),
        arrayOf("Kai", "#5fd0ff", "yes! give me 5 min", "0"),
        arrayOf("Rin", "#9fe870", "I'll bring snacks lol", "0"),
        arrayOf("Nova", "#ff7ab8", "gg on that last match btw", "0"),
        arrayOf("Kai", "#5fd0ff", "that clutch was insane", "0.45"),
        arrayOf("Rin", "#9fe870", "joining voice now", "0.9")
    )

    // La messagerie de jeu (au format téléphone) : serveurs, salon, messages qui arrivent, « … is typing ».
    private fun messagerie(c: Canvas, t: Float, w: Float, h: Float) {
        val k = w / 400f
        pl.shader = null
        pl.color = dHexa("#2b2d33"); c.drawRect(0f, 0f, w, h, pl)
        pl.color = dHexa("#1e1f23"); c.drawRect(0f, 0f, 72f * k, h, pl)
        val serveurs = arrayOf("#6a6ff2", "#ff7ab8", "#5fd0ff", "#9fe870", "#ffb05a", "#b98cff", "#ff6a6a")
        for ((j, col) in serveurs.withIndex()) {
            val cy = (60f + j * 70f) * k
            pl.color = dHexa(col); c.drawCircle(36f * k, cy, 24f * k, pl)
            if (j == 0) { pl.color = Color.WHITE; ov.set(0f, cy - 16f * k, 5f * k, cy + 16f * k); c.drawRoundRect(ov, 2f * k, 2f * k, pl) }
        }
        pl.color = dHexa("#313339"); c.drawRect(72f * k, 0f, w, 64f * k, pl)
        ecrire(c, "# squad-night", 92f * k, 42f * k, 22f * k, Color.WHITE, true, 0.92f)
        pl.color = dHexa("#3dd68c"); c.drawCircle(w - 30f * k, 32f * k, 8f * k, pl)
        var yy = 100f * k
        for (m in messages) {
            val a = borne((t - m[3].toFloat()) / 0.3f)
            if (a <= 0f) continue
            val dy = (1f - ralentit(a)) * 24f * k
            val col = dHexa(m[1])
            pl.color = avecAlpha(col, a); c.drawCircle(104f * k, yy + 20f * k + dy, 19f * k, pl)
            ecrire(c, m[0], 134f * k, yy + 14f * k + dy, 17f * k, col, true, a)
            ecrire(c, m[2], 134f * k, yy + 40f * k + dy, 15f * k, Color.rgb(222, 224, 232), false, a)
            yy += 78f * k
        }
        for (j in 0 until 3) {
            val b = 0.5f + 0.5f * sin(t * 8f - j * 0.8f)
            pl.color = avecAlpha(Color.WHITE, 0.35f + 0.4f * b); c.drawCircle((96f + j * 13f) * k, h - (96f + 4f * b) * k, 4f * k, pl)
        }
        ecrire(c, "Kai is typing...", 140f * k, h - 90f * k, 14f * k, Color.WHITE, false, 0.5f)
        pl.color = dHexa("#383a41"); ov.set(88f * k, h - 70f * k, w - 16f * k, h - 20f * k); c.drawRoundRect(ov, 14f * k, 14f * k, pl)
        ecrire(c, "Message #squad-night", 108f * k, h - 38f * k, 15f * k, Color.WHITE, false, 0.35f)
        pl.color = D_ACCENT; c.drawCircle(w - 44f * k, h - 45f * k, 15f * k, pl)
    }

    // Le jeu (écran gauche) : montagnes au clair de lune, héros à l'épée lumineuse, interface.
    private fun jeu(c: Canvas, o: Outils, t: Float, w: Float, h: Float) {
        pl.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(dHexa("#0d1640"), dHexa("#3a2a6a"), dHexa("#1a1430")), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, pl); pl.shader = null
        pl.color = avecAlpha(dHexa("#f0e8ff"), 0.95f); c.drawCircle(w * 0.30f, h * 0.28f, h * 0.09f, pl)
        o.halo(c, w * 0.30f, h * 0.28f, h * 0.2f, 0.15f, dHexa("#b8a8ff"))
        val chaines = arrayOf(floatArrayOf(0.62f, 0.20f, t * 0.05f, 0.35f), floatArrayOf(0.75f, 0.14f, 1.3f + t * 0.12f, 0.5f), floatArrayOf(0.88f, 0.08f, 2.1f + t * 0.25f, 0.7f))
        val cols = intArrayOf(dHexa("#2a2460"), dHexa("#1c1848"), dHexa("#120f30"))
        val p = Path()
        for ((n, ch) in chaines.withIndex()) {
            p.reset(); p.moveTo(0f, h)
            for (k in 0..40) {
                p.lineTo(k * w / 40f, h * ch[0] - h * ch[1] * kotlin.math.abs(sin(k * ch[3] + ch[2])) - h * ch[1] * 0.4f * sin(k * 0.9f + ch[2]))
            }
            p.lineTo(w, h); p.close(); pl.color = cols[n]; c.drawPath(p, pl)
        }
        val hx = w * 0.62f; val hy = h * 0.8f
        pl.color = dHexa("#0a0820"); ov.set(hx - 10f, hy - 44f, hx + 10f, hy); c.drawRoundRect(ov, 7f, 7f, pl); c.drawCircle(hx, hy - 53f, 10f, pl)
        tr.shader = null; tr.color = dHexa("#38e1ff"); tr.strokeWidth = 4f; c.drawLine(hx + 8f, hy - 34f, hx + 38f, hy - 66f, tr)
        o.halo(c, hx + 26f, hy - 52f, 23f, 0.35f, dHexa("#38e1ff"))
        pl.color = avecAlpha(dHexa("#ffe08a"), 0.8f)
        for (k in 0 until 14) c.drawCircle(modulo(k * 97f + t * 30f, w), h * 0.5f + sin(t * 2f + k) * h * 0.1f + k * 6f, 2.5f, pl)
        pl.color = avecAlpha(Color.BLACK, 0.5f); ov.set(18f, 16f, 168f, 30f); c.drawRoundRect(ov, 7f, 7f, pl)
        pl.shader = LinearGradient(0f, 0f, 170f, 0f, dHexa("#ff4f6a"), dHexa("#ff9f5a"), Shader.TileMode.CLAMP)
        ov.set(20f, 18f, 20f + 146f * 0.78f, 28f); c.drawRoundRect(ov, 5f, 5f, pl); pl.shader = null
        pl.color = avecAlpha(Color.BLACK, 0.45f); c.drawCircle(w - 46f, 46f, 32f, pl)
        tr.color = avecAlpha(dHexa("#8af0c0"), 0.7f); tr.strokeWidth = 2f; c.drawCircle(w - 46f, 46f, 32f, tr)
        c.drawLine(w - 46f, 46f, w - 46f + 28f * cos(t * 3f), 46f + 28f * sin(t * 3f), tr)
        ecrire(c, "LVL 42", 18f, h - 16f, 16f, Color.WHITE, true, 0.85f)
    }

    // Le tableau de bord de live (écran droit) : LIVE, spectateurs, courbe, visualiseur audio.
    private fun stream(c: Canvas, t: Float, w: Float, h: Float) {
        pl.shader = null
        pl.color = dHexa("#14121e"); c.drawRect(0f, 0f, w, h, pl)
        pl.color = dHexa("#ff3b5c"); ov.set(16f, 14f, 86f, 40f); c.drawRoundRect(ov, 6f, 6f, pl)
        ecrire(c, "LIVE", 28f, 33f, 16f, Color.WHITE, true)
        ecrire(c, "1,284 viewers", 98f, 33f, 15f, Color.WHITE, false, 0.75f)
        val p = Path(); p.moveTo(16f, h * 0.62f)
        for (k in 0 until 40) p.lineTo(16f + k * (w - 32f) / 39f, h * 0.62f - (k / 39f) * h * 0.22f - sin(k * 0.7f + t) * 8f)
        tr.shader = null; tr.color = avecAlpha(dHexa("#b98cff"), 0.9f); tr.strokeWidth = 3f; c.drawPath(p, tr)
        val n = 28
        for (k in 0 until n) {
            val v = 0.25f + 0.75f * kotlin.math.abs(sin(t * 4f + k * 0.6f) * sin(t * 1.7f + k * 0.23f))
            val bh = v * h * 0.22f
            pl.color = dTeinte(0.75f - k.toFloat() / n * 0.25f, 0.7f)
            val x = 16f + k * (w - 32f) / n
            ov.set(x, h - 14f - bh, x + (w - 32f) / n - 3f, h - 14f); c.drawRoundRect(ov, 2f, 2f, pl)
        }
    }

    // ------------------------------------------------------------------ les objets sur le bureau
    private fun clavier(c: Canvas, t: Float) {
        val z0 = 50f; val z1 = 64f; val y = bureauY + 2.6f
        ombre(c, 0f, (z0 + z1) / 2f, 26f, 9f, 0.7f)
        if (poly3(-19f, bureauY + 0.4f, z0, 19f, bureauY + 0.4f, z0, 19f, y, z0 + 0.6f, -19f, y, z0 + 0.6f)) remplir(c, dHexa("#0e0e14"))
        if (poly3(-19f, y, z0 + 0.6f, 19f, y, z0 + 0.6f, 19f, y + 0.6f, z1, -19f, y + 0.6f, z1)) remplir(c, dHexa("#1a1a22"))
        val rows = 5; val cols = 16
        val base = dHexa("#262632")
        for (j in 0 until rows) {
            for (i in 0 until cols) {
                val xa = -18f + 36f * i / cols + 0.25f; val xb = -18f + 36f * (i + 1) / cols - 0.25f
                val za = z0 + 1f + (z1 - z0 - 1.6f) * (rows - 1 - j) / rows + 0.2f
                val zb = z0 + 1f + (z1 - z0 - 1.6f) * (rows - j) / rows - 0.25f
                val yk = y + 0.9f + (za - z0) * 0.04f
                val col = dTeinte(i.toFloat() / cols * 0.8f - t * 0.45f + j * 0.04f, 0.75f)
                if (poly3(xa, yk - 0.9f, za, xb, yk - 0.9f, za, xb, yk, za, xa, yk, za)) remplir(c, avecAlpha(col, 0.85f))   // lumière qui fuit dessous
                if (poly3(xa, yk, za, xb, yk, za, xb, yk, zb, xa, yk, zb)) remplir(c, dMelange(base, col, 0.28f))            // dessus de la touche
            }
        }
    }

    private fun souris(c: Canvas, t: Float) {
        val x = 27f; val z = 58f
        ombre(c, x + 0.5f, z + 1f, 5f, 6f, 0.75f)
        val e = echelle(z)
        c.save(); c.translate(sx(x, z), sy(bureauY + 1.6f, z)); c.scale(1f, 0.62f)
        pl.shader = LinearGradient(-3f * e, 0f, 3f * e, 0f, dHexa("#2c2c38"), dHexa("#121218"), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, 3.4f * e, pl); pl.shader = null
        tr.shader = null; tr.color = avecAlpha(dTeinte(0.55f - t * 0.3f, 0.8f), 0.95f); tr.strokeWidth = 0.35f * e
        ov.set(-3.2f * e, -3.2f * e, 3.2f * e, 3.2f * e); c.drawArc(ov, 194.8f, 143.2f, false, tr)
        c.restore()
    }

    private fun tasse(c: Canvas, t: Float) {
        val x = -27f; val z = 74f; val rr = 4.3f; val hh = 10f
        ombre(c, x + 1f, z + 1f, 7f, 6f, 0.7f)
        val ax = sx(x, z); val sb = sy(bureauY, z); val st = sy(bureauY + hh, z); val e = echelle(z)
        val rx = rr * e; val ry = rx * 0.28f
        val p = Path()
        ov.set(ax - rx, sb - ry, ax + rx, sb + ry); p.arcTo(ov, 0f, 180f, true)
        p.lineTo(ax - rx, st); p.lineTo(ax + rx, st); p.close()
        pl.shader = LinearGradient(ax - rx, 0f, ax + rx, 0f, intArrayOf(dHexa("#e8e0f0"), dHexa("#bab0d0"), dHexa("#6a6080")), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(p, pl); pl.shader = null
        pl.color = D_ACCENT; c.drawRect(ax - rx, st + (sb - st) * 0.35f, ax + rx, st + (sb - st) * 0.47f, pl)
        pl.color = dHexa("#d8d0e8"); ov.set(ax - rx, st - ry, ax + rx, st + ry); c.drawOval(ov, pl)
        pl.color = dHexa("#2a1a12"); ov.set(ax - rx * 0.86f, st - ry * 0.86f, ax + rx * 0.86f, st + ry * 0.86f); c.drawOval(ov, pl)
        tr.shader = null; tr.color = dHexa("#a89ec0"); tr.strokeWidth = 1.2f * e
        val ra = (sb - st) * 0.22f; val acx = ax + rx * 1.05f; val acy = (sb + st) / 2f
        ov.set(acx - ra, acy - ra, acx + ra, acy + ra); c.drawArc(ov, -74.5f, 149f, false, tr)
        for (k in 0 until 3) {                                       // la vapeur
            val ph = modulo(t * 0.5f + k / 3f, 1f)
            tr.color = avecAlpha(Color.WHITE, 0.16f * (1f - ph)); tr.strokeWidth = 0.8f * e
            val bx = ax - rx * 0.4f + k * rx * 0.4f
            p.reset(); p.moveTo(bx, st - 4f * u)
            p.cubicTo(bx + rx * 0.4f * sin(t * 2f + k), st - (3f + ph * 4f) * e, bx - rx * 0.4f * sin(t * 2f + k), st - (6f + ph * 6f) * e, bx + rx * 0.1f, st - (9f + ph * 8f) * e)
            c.drawPath(p, tr)
        }
    }

    // Casque gaming posé debout sur ses oreillettes : arceau rembourré, glissières en métal, coussins,
    // coques avec anneau RGB et petit logo lumineux, micro rétractable, câble qui serpente.
    private fun casque(c: Canvas, o: Outils, t: Float) {
        val x = 24f; val z = 88f
        val ox = sx(x, z); val oy = sy(bureauY, z); val e = echelle(z)
        fun qx(v: Float) = ox + v * e
        fun qy(v: Float) = oy - v * e
        ombre(c, x, z + 1f, 14f, 7f, 0.75f)
        val lum = dMelange(D_ACCENT, Color.WHITE, 0.3f)
        val p = Path()
        // câble
        tr.shader = null; tr.color = dHexa("#14141a"); tr.strokeWidth = 0.45f * e
        p.moveTo(qx(-9.5f), qy(1.2f)); p.cubicTo(qx(-14f), qy(0.3f), qx(-12f), qy(-0.6f), qx(-17f), qy(-0.4f))
        p.cubicTo(qx(-22f), qy(-0.2f), qx(-20f), qy(-1.6f), qx(-26f), qy(-1.2f)); c.drawPath(p, tr)
        // arceau : bande extérieure en dégradé, puis coussin dessous
        fun arceau(dv: Float) { p.reset(); p.moveTo(qx(-8.4f), qy(9.6f + dv)); p.cubicTo(qx(-8.6f), qy(17.5f + dv), qx(8.6f), qy(17.5f + dv), qx(8.4f), qy(9.6f + dv)) }
        arceau(0f); tr.strokeWidth = 1.9f * e; tr.color = Color.WHITE
        tr.shader = LinearGradient(qx(-9f), qy(18f), qx(9f), qy(8f), intArrayOf(dHexa("#3a3a4a"), dHexa("#1c1c26"), dHexa("#101016")), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(p, tr); tr.shader = null
        arceau(-1f); tr.strokeWidth = 1.2f * e; tr.color = dHexa("#26222e"); c.drawPath(p, tr)
        pl.shader = null; pl.color = avecAlpha(Color.WHITE, 0.10f)
        for (k in 0 until 9) {                                       // coutures du coussin
            val tt = 0.12f + k * 0.095f; val m = 1f - tt
            val uu = m * m * m * -8.4f + 3f * m * m * tt * -8.6f + 3f * m * tt * tt * 8.6f + tt * tt * tt * 8.4f
            val vv = m * m * m * 8.6f + 3f * m * m * tt * 16.5f + 3f * m * tt * tt * 16.5f + tt * tt * tt * 8.6f
            c.drawCircle(qx(uu), qy(vv), 0.12f * e, pl)
        }
        tr.strokeWidth = 0.25f * e; tr.color = avecAlpha(lum, 0.45f)  // reflet sur l'arceau
        p.reset(); p.moveTo(qx(-7.9f), qy(11.5f)); p.cubicTo(qx(-7.8f), qy(17.6f), qx(1f), qy(18.3f), qx(4f), qy(17.6f)); c.drawPath(p, tr)
        for (side in intArrayOf(-1, 1)) {                            // glissières en métal
            val gx = qx(side * 8.4f - 0.35f); val gy = qy(12f)
            pl.shader = LinearGradient(gx, 0f, gx + 0.7f * e, 0f, intArrayOf(dHexa("#9a98b0"), dHexa("#e0def0"), dHexa("#6a6880")), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(gx, gy, gx + 0.7f * e, gy + 3.4f * e, pl); pl.shader = null
        }
        for (side in intArrayOf(-1, 1)) {
            val cu = side * 8f
            tr.color = dHexa("#1a1a22"); tr.strokeWidth = 0.6f * e     // fourche
            c.drawLine(qx(cu + side * 0.4f), qy(8.6f), qx(cu + side * 0.4f), qy(10f), tr)
            // coussin en similicuir (côté intérieur)
            c.save(); c.translate(qx(cu - side * 1.3f), qy(4.6f)); c.scale(2.1f * e, 4.4f * e)
            pl.shader = RadialGradient(-0.3f * side, -0.3f, 1.3f, dHexa("#3a3644"), dHexa("#15131a"), Shader.TileMode.CLAMP)
            c.drawCircle(0f, 0f, 1f, pl); pl.shader = null; c.restore()
            // coque, anneau RGB, logo triangle, reflet
            val cxs = qx(cu + side * 0.6f); val cys = qy(4.7f)
            val col = dTeinte(0.80f + side * 0.06f - t * 0.25f, 0.85f)
            c.save(); c.translate(cxs, cys); c.scale(2.7f * e, 4.8f * e)
            pl.shader = LinearGradient(-1f, -1f, 1f, 1f, dMelange(dHexa("#2e2e3c"), lum, if (side < 0) 0.25f else 0.1f), dHexa("#0e0e14"), Shader.TileMode.CLAMP)
            c.drawCircle(0f, 0f, 1f, pl); pl.shader = null
            tr.color = avecAlpha(col, 0.95f); tr.strokeWidth = 0.09f; c.drawCircle(0f, 0f, 0.8f, tr)
            pl.color = dHexa("#14141c"); c.drawCircle(0f, 0f, 0.62f, pl)
            p.reset(); p.moveTo(0f, -0.26f); p.lineTo(0.24f, 0.16f); p.lineTo(-0.24f, 0.16f); p.close()
            pl.color = avecAlpha(col, 0.9f); c.drawPath(p, pl)
            tr.color = avecAlpha(Color.WHITE, 0.10f); tr.strokeWidth = 0.06f; ov.set(-0.93f, -0.93f, 0.93f, 0.93f); c.drawArc(ov, 198f, 90f, false, tr)
            c.restore()
            halo(c, o, cxs, cys, 4.5f * e, 0.22f, col)
            if (side < 0) {                                          // micro rétractable
                tr.color = dHexa("#202028"); tr.strokeWidth = 0.35f * e
                p.reset(); p.moveTo(qx(cu - 1.8f), qy(2.5f)); p.cubicTo(qx(cu - 3.5f), qy(1.2f), qx(cu - 4.5f), qy(0.8f), qx(cu - 6f), qy(1.6f)); c.drawPath(p, tr)
                pl.color = dHexa("#2a2a34"); c.drawCircle(qx(cu - 6.2f), qy(1.7f), 0.55f * e, pl)
                pl.color = dHexa("#ff4f6a"); c.drawCircle(qx(cu - 6.2f), qy(1.7f), 0.18f * e, pl)
            }
        }
    }
}
