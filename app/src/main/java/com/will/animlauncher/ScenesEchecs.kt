package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ÉCHECS : 5 coups brillants de vraies parties (1 sacrifice de tour + 4 coups célèbres).
// Une partie est tirée au hasard à chaque ouverture.

// Un coup : case de départ, case d'arrivée, notation affichée.
class Coup(val de: String, val vers: String, val notation: String)

// Une partie : titre, position de départ (notation FEN), coups joués, numéro du coup brillant,
// texte de fin, et « noirs » = on regarde l'échiquier du côté des noirs.
class Partie(val titre: String, val fen: String, val coups: List<Coup>, val brillant: Int, val fin: String, val noirs: Boolean)

val PARTIES = listOf(
    Partie("Kasparov – Topalov · 1999", "b2r3r/k4p1p/p2q1np1/NppP4/3p1Q2/P4PPB/1PP4P/1K1RR3",
        listOf(Coup("d1", "d4", "24. Txd4"), Coup("c5", "d4", "24... cxd4")), 0, "Sacrifice de tour", false),
    Partie("Morphy – Brunswick & Isouard · 1858", "4kb1r/p2n1ppp/4q3/4p1B1/4P3/1Q6/PPP2PPP/2KR4",
        listOf(Coup("b3", "b8", "16. Db8+"), Coup("d7", "b8", "16... Cxb8"), Coup("d1", "d8", "17. Td8#")), 0, "Échec et mat", false),
    Partie("Levitsky – Marshall · 1912", "5rk1/pp4pp/4p3/2R3Q1/3n4/2q4r/P1P2PPP/5RK1",
        listOf(Coup("c3", "g3", "23... Dg3")), 0, "0-1 : les blancs abandonnent", true),
    Partie("Topalov – Shirov · 1998", "8/8/4kpp1/3p1b2/p6P/2B5/6P1/6K1",
        listOf(Coup("f5", "h3", "47... Fh3"), Coup("g2", "h3", "48. gxh3"), Coup("e6", "f5", "48... Rf5")), 0, "Le roi noir file vers les pions", true),
    Partie("Réti – Tartakower · 1910", "rnb1kb1r/pp3ppp/2p5/4q3/4n3/3Q4/PPPB1PPP/2KR1BNR",
        listOf(Coup("d3", "d8", "9. Dd8+"), Coup("e8", "d8", "9... Rxd8"), Coup("d2", "g5", "10. Fg5++"), Coup("d8", "c7", "10... Rc7"), Coup("g5", "d8", "11. Fd8#")), 0, "Échec et mat", false),
)

// Lit une position FEN : case -> pièce (majuscule = blanc). Ex. "e1" -> 'K'.
fun lireFen(fen: String): HashMap<String, Char> {
    val cases = HashMap<String, Char>()
    for ((r, ligne) in fen.split("/").withIndex()) {
        var f = 0
        for (ch in ligne) {
            if (ch.isDigit()) f += ch - '0'
            else { cases["abcdefgh"[f] + (8 - r).toString()] = ch; f += 1 }
        }
    }
    return cases
}

// Les silhouettes des pièces (style Staunton), dans une case de taille 1 centrée sur (0, 0).
object Silhouettes {
    private val D = Path.Direction.CW
    val chemins: Map<Char, Path> = "prbnqk".associateWith { creer(it) }

    private fun creer(g: Char): Path {
        val p = Path()
        when (g) {
            'p' -> {
                p.moveTo(-0.2f, 0.24f); p.cubicTo(-0.14f, 0.1f, -0.08f, 0f, -0.09f, -0.04f); p.lineTo(0.09f, -0.04f); p.cubicTo(0.08f, 0f, 0.14f, 0.1f, 0.2f, 0.24f); p.close()
                p.addOval(RectF(-0.15f, -0.09f, 0.15f, -0.02f), D); p.addCircle(0f, -0.2f, 0.13f, D)
            }
            'r' -> {
                p.moveTo(-0.22f, 0.24f); p.lineTo(-0.18f, -0.12f); p.lineTo(0.18f, -0.12f); p.lineTo(0.22f, 0.24f); p.close()
                p.addRect(-0.25f, -0.19f, 0.25f, -0.1f, D)
                val q = Path(); q.moveTo(-0.26f, -0.17f)
                for (x in arrayOf(floatArrayOf(-0.26f, -0.14f), floatArrayOf(-0.06f, 0.06f), floatArrayOf(0.14f, 0.26f))) {   // les créneaux
                    q.lineTo(x[0], -0.39f); q.lineTo(x[1], -0.39f); q.lineTo(x[1], -0.3f)
                }
                q.lineTo(0.26f, -0.17f); q.close()
                p.addPath(q)
            }
            'b' -> {
                p.moveTo(-0.2f, 0.24f); p.cubicTo(-0.12f, 0.12f, -0.08f, 0.02f, -0.08f, -0.02f); p.lineTo(0.08f, -0.02f); p.cubicTo(0.08f, 0.02f, 0.12f, 0.12f, 0.2f, 0.24f); p.close()
                p.addOval(RectF(-0.16f, -0.08f, 0.16f, 0f), D)
                val m = Path(); m.moveTo(0f, -0.4f); m.cubicTo(0.16f, -0.3f, 0.17f, -0.16f, 0.1f, -0.07f); m.lineTo(-0.1f, -0.07f); m.cubicTo(-0.17f, -0.16f, -0.16f, -0.3f, 0f, -0.4f); m.close()
                p.addPath(m); p.addCircle(0f, -0.43f, 0.05f, D)
            }
            'n' -> {   // la tête de cheval
                p.moveTo(-0.2f, 0.24f); p.lineTo(0.22f, 0.24f); p.cubicTo(0.25f, 0.05f, 0.22f, -0.2f, 0.08f, -0.34f)
                p.lineTo(0.05f, -0.46f); p.lineTo(-0.03f, -0.35f)
                p.cubicTo(-0.14f, -0.31f, -0.25f, -0.18f, -0.32f, -0.05f); p.cubicTo(-0.35f, 0.02f, -0.29f, 0.08f, -0.22f, 0.05f)
                p.cubicTo(-0.15f, 0.01f, -0.09f, 0f, -0.05f, 0.02f); p.cubicTo(-0.13f, 0.1f, -0.2f, 0.16f, -0.2f, 0.24f); p.close()
            }
            'q' -> {   // la dame et sa couronne
                p.moveTo(-0.22f, 0.24f); p.cubicTo(-0.14f, 0.06f, -0.1f, -0.08f, -0.21f, -0.27f)
                val pointes = floatArrayOf(-0.12f, -0.18f, -0.07f, -0.33f, 0f, -0.2f, 0.07f, -0.33f, 0.12f, -0.18f, 0.21f, -0.27f)
                var k = 0
                while (k < pointes.size) { p.lineTo(pointes[k], pointes[k + 1]); k += 2 }
                p.cubicTo(0.1f, -0.08f, 0.14f, 0.06f, 0.22f, 0.24f); p.close()
                for (b in arrayOf(floatArrayOf(-0.21f, -0.29f), floatArrayOf(-0.07f, -0.35f), floatArrayOf(0.07f, -0.35f), floatArrayOf(0.21f, -0.29f))) p.addCircle(b[0], b[1], 0.04f, D)
            }
            else -> {  // le roi et sa croix
                p.moveTo(-0.22f, 0.24f); p.cubicTo(-0.14f, 0.08f, -0.12f, -0.06f, -0.19f, -0.19f); p.cubicTo(-0.1f, -0.27f, 0.1f, -0.27f, 0.19f, -0.19f)
                p.cubicTo(0.12f, -0.06f, 0.14f, 0.08f, 0.22f, 0.24f); p.close()
                p.addRect(-0.03f, -0.44f, 0.03f, -0.24f, D); p.addRect(-0.1f, -0.39f, 0.1f, -0.33f, D)
            }
        }
        p.addRoundRect(RectF(-0.34f, 0.29f, 0.34f, 0.4f), 0.05f, 0.05f, D)    // le socle, commun à toutes
        p.addRoundRect(RectF(-0.25f, 0.22f, 0.25f, 0.3f), 0.03f, 0.03f, D)
        return p
    }
}

fun dessinerPiece(c: Canvas, o: Outils, lettre: Char, x: Float, y: Float, s: Float, alpha: Float, dp: Float, echelle: Float = 1f) {
    if (alpha <= 0.01f) return
    val blanc = lettre.isUpperCase()
    val g = lettre.lowercaseChar()
    val chemin = Silhouettes.chemins[g] ?: return
    val k = s * echelle
    c.save(); c.translate(x, y); c.scale(k, k)
    c.drawOval(RectF(-0.36f, 0.33f, 0.38f, 0.46f), o.pinceau(argb(90f * alpha, 0, 0, 0)))   // l'ombre
    val p = o.pinceau()
    // dégradé de gauche à droite : on dirait une pièce tournée (cylindre éclairé par la gauche)
    p.shader = if (blanc) LinearGradient(-0.32f, 0f, 0.32f, 0f,
        intArrayOf(argb(255f * alpha, 252, 248, 238), argb(255f * alpha, 214, 206, 190), argb(255f * alpha, 150, 140, 124)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    else LinearGradient(-0.32f, 0f, 0.32f, 0f,
        intArrayOf(argb(255f * alpha, 92, 92, 102), argb(255f * alpha, 40, 40, 46), argb(255f * alpha, 14, 14, 18)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    c.drawPath(chemin, p)
    val bord = o.pinceau(if (blanc) argb(255f * alpha, 38, 34, 30) else argb(150f * alpha, 170, 170, 185), 1.1f * dp / k)
    bord.strokeJoin = Paint.Join.ROUND
    c.drawPath(chemin, bord)
    val detail = if (blanc) argb(200f * alpha, 40, 36, 30) else argb(160f * alpha, 170, 170, 185)
    if (g == 'n') {        // l'œil et la crinière
        c.drawCircle(-0.12f, -0.2f, 0.025f, o.pinceau(detail))
        c.drawLine(0.12f, -0.3f, 0.2f, -0.05f, o.pinceau(detail, 1.2f * dp / k))
    }
    if (g == 'b') c.drawLine(0.02f, -0.29f, 0.1f, -0.17f, o.pinceau(detail, 1.4f * dp / k))   // la fente du fou
    c.restore()
}

class CoupsBrillants(numero: Int = -1) : Scene() {
    private val partie = PARTIES[if (numero in PARTIES.indices) numero else Random.nextInt(PARTIES.size)]

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val dp = o.dp; val sec = i.secondes; val intro = i.intro; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        degradeVertical(c, o, w, rgb(12, 13, 12), rgb(4, 5, 4), 0f, h)
        val app = ralentit(intro)
        val a = 1f - rev
        val B = min(w * 0.9f, h * 0.66f)
        val s = B / 8f
        val x0 = (w - B) / 2f; val y0 = (h - B) / 2f - s * 0.2f
        fun caseX(nom: String): Float { val f = nom[0] - 'a'; return x0 + ((if (partie.noirs) 7 - f else f) + 0.5f) * s }
        fun caseY(nom: String): Float { val r = nom[1] - '0'; return y0 + ((if (partie.noirs) r - 1 else 8 - r) + 0.5f) * s }

        // le cadre et les 64 cases
        c.drawRoundRect(RectF(x0 - s * 0.12f, y0 - s * 0.12f, x0 + B + s * 0.12f, y0 + B + s * 0.12f), 6f * dp, 6f * dp, o.pinceau(argb(255f * app, 30, 24, 18)))
        val caseP = o.pinceau()
        for (lig in 0 until 8) for (col in 0 until 8) {
            caseP.color = if ((lig + col) % 2 == 0) argb(255f * app, 104, 124, 82) else argb(255f * app, 40, 52, 34)
            c.drawRect(x0 + col * s, y0 + lig * s, x0 + (col + 1) * s, y0 + (lig + 1) * s, caseP)
        }

        // Le déroulé : chaque coup dure d secondes (70 % de glissade, puis un temps d'arrêt).
        val coups = partie.coups
        val n = coups.size
        val d = min(0.3f, 1f / n)
        val t0 = 0.25f
        val pieces = lireFen(partie.fen)
        var enCours = -1; var avance = 0f
        for ((k, cp) in coups.withIndex()) {
            val debut = t0 + k * d
            if (sec < debut) break
            enCours = k
            avance = doux(borne((sec - debut) / (d * 0.7f)))
            if (avance >= 1f) pieces.remove(cp.de)?.let { pieces[cp.vers] = it }
        }
        if (enCours >= 0) {                               // surbrillance jaune du dernier coup
            val surligne = o.pinceau(argb(90f * app * a, 230, 210, 80))
            for (nom in arrayOf(coups[enCours].de, coups[enCours].vers)) {
                c.drawRect(caseX(nom) - s / 2f, caseY(nom) - s / 2f, caseX(nom) + s / 2f, caseY(nom) + s / 2f, surligne)
            }
        }
        val mat = partie.fin.startsWith("Échec et mat") && enCours == n - 1 && avance >= 1f
        val mobile = if (enCours >= 0 && avance < 1f) coups[enCours] else null
        for ((nom, lettre) in pieces) {
            if (mobile != null && nom == mobile.de) continue
            var alpha = app * a
            if (mobile != null && nom == mobile.vers) alpha *= 1f - borne((avance - 0.6f) / 0.4f)   // la pièce prise s'efface
            if (mat && lettre == (if (partie.noirs) 'K' else 'k')) {                              // le roi maté : case rouge
                c.drawRect(caseX(nom) - s / 2f, caseY(nom) - s / 2f, caseX(nom) + s / 2f, caseY(nom) + s / 2f, o.pinceau(argb(150f * a, 200, 50, 40)))
            }
            dessinerPiece(c, o, lettre, caseX(nom), caseY(nom), s, alpha, dp)
        }
        if (mobile != null) {                              // la pièce qui glisse, un peu soulevée
            val lettre = pieces[mobile.de] ?: ' '
            val hausse = sin(PI.toFloat() * avance) * s * 0.12f
            dessinerPiece(c, o, lettre, mix(caseX(mobile.de), caseX(mobile.vers), avance), mix(caseY(mobile.de), caseY(mobile.vers), avance) - hausse,
                s, app * a, dp, 1f + 0.08f * sin(PI.toFloat() * avance))
        }

        // « !! » sur le coup brillant
        val vb = coups[partie.brillant].vers
        val pop = ralentit(borne((sec - (t0 + partie.brillant * d + d * 0.7f)) / 0.1f))
        if (pop > 0f) {
            val bx = caseX(vb) + s * 0.32f; val by = caseY(vb) - s * 0.32f
            val r = s * 0.26f * pop * (1f + 0.05f * sin(sec * 7f))
            o.halo(c, bx, by, r * 2.4f, 0.3f * a, rgb(40, 200, 190))
            c.drawCircle(bx, by, r, o.pinceau(avecAlpha(rgb(28, 168, 156), a)))
            c.drawCircle(bx, by, r, o.pinceau(avecAlpha(rgb(255, 255, 255), 0.85f * a), 1.5f * dp))
            texte(c, "!!", bx, by - r * 0.05f, r * 1.15f, avecAlpha(rgb(255, 255, 255), a))
        }

        // les textes : la partie en haut, le coup joué en bas, puis la conclusion
        val taille = min(s * 0.42f, w * 0.05f)
        texte(c, partie.titre, w / 2f, y0 - s * 0.62f, taille, avecAlpha(rgb(200, 205, 195), 0.9f * app * a))
        if (enCours >= 0) {
            val brille = enCours == partie.brillant
            texte(c, coups[enCours].notation + if (brille) " !!" else "", w / 2f, y0 + B + s * 0.62f, taille * 1.2f,
                avecAlpha(if (brille) rgb(90, 220, 205) else rgb(230, 230, 225), app * a))
        }
        val fini = borne((sec - (t0 + n * d)) / 0.15f)
        if (fini > 0f) texte(c, partie.fin, w / 2f, y0 + B + s * 1.25f, taille * 0.85f,
            avecAlpha(if (partie.fin.contains("mat")) rgb(240, 120, 100) else rgb(200, 205, 195), fini * a))
        o.vignette(c, w, h, 0.5f)
        o.revelationDouce(c, caseX(vb), caseY(vb), rev, rgb(40, 200, 190))
    }
}
