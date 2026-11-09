package com.will.animlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.os.Build
import kotlin.math.max

// QUEL TÉLÉPHONE ? (09/11) L'intro du widget Games montre un petit téléphone qui s'allume : pour qu'il ressemble
// à celui de l'utilisateur, on regarde le MODÈLE (chez Samsung : SM-F7… = un Flip à clapet, SM-F9… = un Fold qui
// s'ouvre comme un livre), ou sinon s'il a un capteur de CHARNIÈRE. Aucune permission n'est nécessaire.
object Appareil {
    const val CLASSIQUE = 0
    const val FLIP = 1
    const val FOLD = 2

    fun type(c: Context): Int {
        val modele = (Build.MODEL ?: "").uppercase()
        if (modele.startsWith("SM-F7") || modele.contains("FLIP") || modele.contains("RAZR")) return FLIP
        if (modele.startsWith("SM-F9") || modele.contains("FOLD")) return FOLD
        val charniere = try { c.packageManager.hasSystemFeature("android.hardware.sensor.hinge_angle") } catch (_: Exception) { false }
        return if (charniere) FOLD else CLASSIQUE
    }
}

// LES INTROS DU WIDGET GAMES (09/11, validées par Will sur l'aperçu /home/claude/apercu/widget_intros2.py) :
// un petit téléphone apparaît, s'allume, lance un jeu (le monde arrive en gris puis prend ses couleurs), et
// on « plonge » dans l'écran, qui grandit jusqu'à remplir le widget.
//   FLIP      : plié (carré), son écran extérieur donne l'heure, il s'ouvre de haut en bas, puis pivote à l'horizontale
//   FOLD      : fermé il est haut et étroit ; il s'ouvre comme un livre sur un grand écran presque carré
//   CLASSIQUE : un téléphone tenu à l'horizontale, qui s'allume
// On dessine UNE image à l'instant t (en secondes) dans un carré de côté d ; WidgetTop en fabrique un « folioscope ».
class IntrosJeu(cv: Canvas) : Peintre(cv) {

    companion object {
        // la durée de l'intro, et l'instant où le jeu apparaît (avant, l'image ne dépend pas du monde affiché)
        fun duree(type: Int): Double = when (type) { Appareil.FLIP -> 4.5; Appareil.FOLD -> 4.2; else -> 3.1 }
        fun debutJeu(type: Int): Double = when (type) { Appareil.FLIP -> 2.6; Appareil.FOLD -> 2.2; else -> 1.1 }
    }

    fun dessiner(type: Int, monde: Int, d: Double, t: Double, couleur: Int) {
        rect(0.0, 0.0, d, d, Color.rgb(8, 8, 12))
        when (type) {
            Appareil.FLIP -> flip(monde, d, t, couleur)
            Appareil.FOLD -> fold(monde, d, t, couleur)
            else -> classique(monde, d, t, couleur)
        }
    }

    private fun blanc(a: Double) = Color.argb(a.toInt().coerceIn(0, 255), 255, 255, 255)
    private fun noirA(a: Double) = Color.argb(a.toInt().coerceIn(0, 255), 0, 0, 0)
    // le métal du téléphone : gris clair en haut, gris foncé en bas
    private fun metal(h: Double, a: Double) = lin(0.0, -h / 2, 0.0, h / 2,
        intArrayOf(Color.argb(a.toInt().coerceIn(0, 255), 72, 74, 86), Color.argb(a.toInt().coerceIn(0, 255), 26, 27, 33)))

    // L'ÉCRAN D'UN TÉLÉPHONE (commun aux 3 intros) : noir, puis un anneau de chargement, puis le jeu.
    // allume = 0 (éteint) à 1 ; couleurs = 0 (gris) à 1 (pleines couleurs) ; jeu = faux tant que le téléphone est debout.
    private fun ecran(l: Double, haut: Double, r: Double, b: Double, rayon: Double, t: Double, couleur: Int, monde: Int,
                      allume: Double, couleurs: Double, debut: Double, jeu: Boolean = true) {
        rrect(l, haut, r, b, rayon, Color.rgb(4, 4, 6))
        if (allume <= 0) return
        val forme = rrectForme(l, haut, r, b, rayon)
        val zone = RectF(l.toFloat(), haut.toFloat(), r.toFloat(), b.toFloat())
        cv.save()
        cv.clipPath(forme)
        cv.saveLayerAlpha(zone, (255 * allume).toInt().coerceIn(0, 255))
        if (jeu && couleurs > 0) MondesJeu.dessiner(cv, monde, zone, couleur, 1 - couleurs, t)
        val charge = if (jeu) 1 - seg(t, debut, debut + 0.4) else 1.0
        if (charge > 0) {
            rect(l, haut, r, b, Color.argb((255 * charge).toInt(), 14, 14, 22))
            val cx = (l + r) / 2; val cy = (haut + b) / 2; val a = minOf(r - l, b - haut) * 0.1
            arc(cx - a, cy - a, cx + a, cy + a, (t * 360) % 360.0, 250.0, a * 0.22, c(ton(couleur, 1.2), 230 * charge))
        }
        cv.restore()
        // le reflet du verre
        chemin(poly(l, haut, l + (r - l) * 0.4, haut, l + (r - l) * 0.18, b, l - (r - l) * 0.05, b), blanc(18 * allume))
        cv.restore()
    }

    // ---------------- FLIP ----------------
    // 0-0,5 s : plié ; 0,5-1 : l'écran extérieur s'allume ; 1-1,7 : il s'ouvre ; 1,7-2 : l'écran s'allume ;
    // 2-2,6 : il pivote à l'horizontale ; 2,6-3,5 : le jeu prend ses couleurs ; 3,5-4,5 : on plonge dedans
    private fun flip(monde: Int, d: Double, t: Double, couleur: Int) {
        val apparait = seg(t, 0.0, 0.5); val externe = seg(t, 0.5, 0.9) * (1 - seg(t, 1.0, 1.2)); val ouvre = seg(t, 1.0, 1.7)
        val allume = seg(t, 1.7, 2.0); val pivote = seg(t, 2.0, 2.6); val couleurs = seg(t, 2.6, 3.5); val plonge = seg(t, 3.5, 4.5)
        val lw = d * 0.39; val lh = d * 0.8; val demi = lh / 2; val a = 255 * apparait
        cv.save()
        deplace(d / 2, d / 2 - (demi / 2) * (1 - ouvre) * (1 - pivote) + (1 - apparait) * d * 0.08)
        tourne(-90 * pivote)
        agrandit((0.85 + 0.15 * apparait) * (1 + plonge * 1.9))
        rrect(-lw / 2, -demi * ouvre, lw / 2, demi, lw * 0.16, noirA(150 * apparait), flou = d * 0.04)       // l'ombre
        rrect(-lw / 2, 0.0, lw / 2, demi, lw * 0.16, 0, metal(lh, a))                                       // la moitié du bas
        if (ouvre < 0.5) {
            // la moitié du haut est encore rabattue : on voit son dos, avec l'écran extérieur et les deux objectifs
            val k = 1 - ouvre / 0.5; val hh = demi * k; val inc = lw * 0.07 * (1 - k); val f = 0.6 + 0.4 * k
            chemin(poly(-lw / 2, 0.0, -lw / 2 + inc, hh, lw / 2 - inc, hh, lw / 2, 0.0), Color.argb(a.toInt(), (52 * f).toInt(), (54 * f).toInt(), (64 * f).toInt()))
            if (k > 0.6) {
                rrect(-lw * 0.42, hh * 0.08, lw * 0.42, hh * 0.92, lw * 0.1, Color.argb(a.toInt(), 4, 4, 8))
                if (externe > 0) {
                    rrect(-lw * 0.42, hh * 0.08, lw * 0.42, hh * 0.92, lw * 0.1, 0, lin(0.0, hh * 0.08, 0.0, hh * 0.92,
                        intArrayOf(c(ton(couleur, 0.55), 255 * externe), c(ton(couleur, 0.18), 255 * externe))))
                    texte("21:37", 0.0, hh * 0.5 + lw * 0.1, lw * 0.22, blanc(235 * externe))
                }
                cercle(-lw * 0.28, hh * 0.22, lw * 0.06, Color.argb(a.toInt(), 10, 10, 14))
                cercle(-lw * 0.1, hh * 0.22, lw * 0.06, Color.argb(a.toInt(), 10, 10, 14))
            }
        } else {
            // la moitié du haut se déplie vers le haut, puis l'écran s'allume
            val k = (ouvre - 0.5) / 0.5; val hh = demi * k; val inc = lw * 0.07 * (1 - k); val m = lw * 0.06
            chemin(poly(-lw / 2, 0.0, -lw / 2 + inc, -hh, lw / 2 - inc, -hh, lw / 2, 0.0), 0, metal(lh, a))
            if (pivote < 1 && couleurs <= 0) {
                ecran(-lw / 2 + m, -hh + m, lw / 2 - m, demi - m, lw * 0.1, t, couleur, monde, allume, 0.0, 99.0, jeu = false)
            } else {
                cv.save()
                tourne(90.0)             // le téléphone est couché : on se remet droit pour dessiner le jeu à l'horizontale
                ecran(-demi + m, -lw / 2 + m, demi - m, lw / 2 - m, lw * 0.1, t, couleur, monde, allume, couleurs, 2.6)
                cv.restore()
            }
        }
        rect(-lw / 2, -lw * 0.02, -lw / 2 + lw * 0.05, lw * 0.02, Color.argb((a * 0.8).toInt(), 115, 118, 132))     // la charnière
        rect(lw / 2 - lw * 0.05, -lw * 0.02, lw / 2, lw * 0.02, Color.argb((a * 0.8).toInt(), 115, 118, 132))
        cv.restore()
    }

    // ---------------- FOLD ----------------
    // 0-0,5 s : fermé (haut et étroit) ; 0,5-1 : l'écran de façade s'allume ; 1-1,8 : il s'ouvre comme un livre ;
    // 1,8-2,2 : le grand écran s'allume ; 2,2-3,2 : le jeu ; 3,2-4,2 : on plonge dedans
    private fun fold(monde: Int, d: Double, t: Double, couleur: Int) {
        val apparait = seg(t, 0.0, 0.5); val externe = seg(t, 0.5, 0.9) * (1 - seg(t, 1.0, 1.25)); val ouvre = seg(t, 1.0, 1.8)
        val allume = seg(t, 1.8, 2.2); val couleurs = seg(t, 2.2, 3.2); val plonge = seg(t, 3.2, 4.2)
        val pw = d * 0.76; val ph = d * 0.66; val demi = pw / 2; val a = 255 * apparait; val ray = ph * 0.06; val m = ph * 0.035
        cv.save()
        deplace(d / 2 + demi / 2 * (1 - ouvre), d / 2 + (1 - apparait) * d * 0.08)
        agrandit((0.85 + 0.15 * apparait) * (1 + plonge * 1.5))
        rrect(-demi, -ph / 2, demi * ouvre, ph / 2, ray, noirA(150 * apparait), flou = d * 0.04)
        rrect(-demi, -ph / 2, 0.0, ph / 2, ray, 0, metal(ph, a))                                            // la moitié gauche
        if (ouvre < 0.5) {
            // la moitié droite est rabattue : on voit son dos, avec l'écran de façade sur toute la hauteur
            val k = 1 - ouvre / 0.5; val lw = demi * k; val inc = ph * 0.05 * (1 - k); val f = 0.6 + 0.4 * k
            chemin(poly(0.0, -ph / 2, -lw, -ph / 2 + inc, -lw, ph / 2 - inc, 0.0, ph / 2), Color.argb(a.toInt(), (52 * f).toInt(), (54 * f).toInt(), (64 * f).toInt()))
            if (k > 0.55) {
                val el = -lw + m; val et = -ph / 2 + m + inc; val er = -m; val eb = ph / 2 - m - inc
                rrect(el, et, er, eb, ray * 0.8, Color.argb(a.toInt(), 4, 4, 8))
                if (externe > 0) {
                    rrect(el, et, er, eb, ray * 0.8, 0, lin(0.0, et, 0.0, eb, intArrayOf(c(ton(couleur, 0.6), 255 * externe), c(ton(couleur, 0.15), 255 * externe))))
                    texte("21", (el + er) / 2, et + ph * 0.26, lw * 0.26, blanc(235 * externe))
                    texte("37", (el + er) / 2, et + ph * 0.4, lw * 0.26, blanc(235 * externe))
                }
            }
        } else {
            val k = (ouvre - 0.5) / 0.5; val lw = demi * k; val inc = ph * 0.05 * (1 - k)
            chemin(poly(0.0, -ph / 2, lw, -ph / 2 + inc, lw, ph / 2 - inc, 0.0, ph / 2), 0, metal(ph, a))
            if (k < 1) rrect(-demi + m, -ph / 2 + m, max(-demi + m * 2, lw - m), ph / 2 - m, ray * 0.8, Color.argb(a.toInt(), 4, 4, 6))
            else ecran(-demi + m, -ph / 2 + m, demi - m, ph / 2 - m, ray * 0.8, t, couleur, monde, allume, couleurs, 2.2)
        }
        rect(-ph * 0.012, -ph / 2, ph * 0.012, -ph / 2 + m, Color.argb((a * 0.8).toInt(), 115, 118, 132))            // la charnière
        rect(-ph * 0.012, ph / 2 - m, ph * 0.012, ph / 2, Color.argb((a * 0.8).toInt(), 115, 118, 132))
        cv.restore()
    }

    // ---------------- TÉLÉPHONE CLASSIQUE ----------------
    // 0-0,5 s : il apparaît, couché ; 0,5-1 : il s'allume ; 1,1-2,1 : le jeu ; 2,1-3,1 : on plonge dedans
    private fun classique(monde: Int, d: Double, t: Double, couleur: Int) {
        val apparait = seg(t, 0.0, 0.5); val allume = seg(t, 0.5, 0.9); val couleurs = seg(t, 1.1, 2.1); val plonge = seg(t, 2.1, 3.1)
        val pw = d * 0.8; val ph = d * 0.39; val a = 255 * apparait; val m = ph * 0.06
        cv.save()
        deplace(d / 2, d / 2 + (1 - apparait) * d * 0.08)
        tourne(-8 * (1 - apparait))
        agrandit((0.85 + 0.15 * apparait) * (1 + plonge * 1.9))
        rrect(-pw / 2, -ph / 2, pw / 2, ph / 2, ph * 0.16, noirA(150 * apparait), flou = d * 0.04)
        rrect(-pw / 2, -ph / 2, pw / 2, ph / 2, ph * 0.16, 0, metal(ph, a))
        ecran(-pw / 2 + m, -ph / 2 + m, pw / 2 - m, ph / 2 - m, ph * 0.1, t, couleur, monde, allume, couleurs, 1.1)
        cercle(-pw / 2 + m * 2.2, 0.0, ph * 0.025, noirA(a))                                                // la caméra « poinçon »
        cv.restore()
    }
}
