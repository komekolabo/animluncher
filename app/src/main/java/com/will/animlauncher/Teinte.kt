package com.will.animlauncher

import android.content.Context
import android.graphics.Color

// LA COULEUR D'UN DOSSIER (09/11) : on la choisit avec la roue chromatique du dossier (le point
// multicolore en haut à gauche). On ne retient qu'UNE couleur (teinte, intensité, luminosité = « HSV ») ;
// tout le reste en est calculé ici : le dégradé du logo (3 teintes voisines), la fenêtre (la même teinte
// en très sombre, pour garder le thème sombre), la bulle « + », les poussières, la lueur…
// Sans choix, c'est la palette CHAUDE d'origine, validée par Will (orange -> rose -> vert-jaune, fenêtre brune).
// Chaque valeur est une couleur SANS transparence : au dessin, on lui donne son alpha avec avecAlpha().
class Teinte(
    val logo: IntArray,       // le dégradé du logo (3 couleurs)
    val haut: Int,            // la fenêtre du dossier, en haut…
    val bas: Int,             // … et en bas
    val bulle: Int,           // la bulle « + » et le rond de la croix « × »
    val clair: Int,           // le « + », la croix, les points des pages
    val lueur: Int,           // la lueur autour du logo (cœur)
    val lueur2: Int,          // … et son bord
    val poussiere: Int,       // les traînées de poussière vers les apps
    val onde: Int,            // l'onde fine quand une app éclot
    val info: Int,            // « 5m ago »
    val consigne: Int,        // le mode d'emploi du mode édition
    val hsv: FloatArray?      // la couleur choisie (null = palette d'origine)
) {
    companion object {
        // La palette CHAUDE d'origine (exactement les couleurs d'avant la roue).
        val ORIGINE = Teinte(
            intArrayOf(Color.rgb(255, 140, 91), Color.rgb(255, 107, 155), Color.rgb(196, 209, 63)),
            Color.rgb(69, 42, 35), Color.rgb(41, 24, 20), Color.rgb(86, 52, 43), Color.rgb(255, 226, 220),
            Color.rgb(255, 107, 122), Color.rgb(255, 75, 58), Color.rgb(255, 196, 185), Color.rgb(255, 192, 175),
            Color.rgb(201, 167, 155), Color.rgb(255, 205, 180), null)

        // La couleur que montre la roue quand on l'ouvre pour la première fois (l'orange-rose d'origine).
        val DEPART = floatArrayOf(14f, 0.64f, 1f)

        private fun c(h: Float, s: Float, v: Float) =
            Color.HSVToColor(floatArrayOf(((h % 360) + 360) % 360, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))

        // Une couleur choisie -> toutes les couleurs du dossier.
        fun depuis(hsv: FloatArray): Teinte {
            val h = hsv[0]; val s = hsv[1]; val v = hsv[2]
            return Teinte(
                intArrayOf(c(h - 28, s, v), c(h, s * 0.85f, v), c(h + 42, s * 0.9f, v * 1.05f)),
                c(h, 0.35f + 0.25f * s, 0.27f), c(h, 0.45f + 0.25f * s, 0.15f), c(h, 0.45f + 0.2f * s, 0.34f),
                c(h, 0.12f, 1f), c(h, s, v), c(h + 10, s, v * 0.9f), c(h, 0.25f, 1f), c(h, 0.3f, 1f),
                c(h, 0.2f, 0.79f), c(h, 0.28f, 1f), hsv.copyOf())
        }

        // La teinte d'un dossier (retenue dans le carnet « groupes », voir Groupes.couleur).
        // Sans couleur choisie (11/11) : l'IA garde la palette chaude d'origine ; les autres dossiers prennent la
        // teinte de leur logo (la même que leur galet, voir LogosDossier.hsvGalet), et le logo garde ses propres
        // couleurs. hsv reste null : pour la roue, c'est toujours « pas de couleur choisie ».
        fun de(c: Context, groupe: String): Teinte {
            Groupes.couleur(c, groupe)?.let { return depuis(it) }
            val h = LogosDossier.hsvGalet(groupe) ?: return ORIGINE
            val d = depuis(h)
            val l = LogosDossier.couleurs(groupe)
            return Teinte(l, d.haut, d.bas, d.bulle, d.clair, l[1], l[2], d.poussiere, d.onde, d.info, d.consigne, null)
        }

        // Une couleur avec sa transparence (alpha de 0 à 255).
        fun avecAlpha(couleur: Int, alpha: Float): Int =
            Color.argb(alpha.toInt().coerceIn(0, 255), Color.red(couleur), Color.green(couleur), Color.blue(couleur))
    }
}
