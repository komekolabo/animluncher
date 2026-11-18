package com.will.animlauncher

import android.content.Context
import android.graphics.Color
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

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

        // LES 8 COULEURS PROPOSÉES (18/11) : les petits ronds au-dessus de la roue. Un appui = la roue saute
        // sur cette couleur. Elles sont rangées dans l'ordre de la roue (du rouge au rose), et notées en HSV
        // comme tout ce que retient la roue. Calibrées sur un aperçu (voir apercu_couleurs.png de la session).
        val PROPOSEES = arrayOf(
            floatArrayOf(14f, 0.64f, 1f),     // Corail (la couleur de départ)
            floatArrayOf(38f, 0.70f, 1f),     // Ambre
            floatArrayOf(95f, 0.40f, 0.85f),  // Sauge
            floatArrayOf(150f, 0.60f, 0.9f),  // Émeraude
            floatArrayOf(185f, 0.65f, 1f),    // Lagon
            floatArrayOf(215f, 0.60f, 1f),    // Azur
            floatArrayOf(265f, 0.45f, 1f),    // Lavande
            floatArrayOf(320f, 0.55f, 1f))    // Framboise

        // Une couleur choisie -> toutes les couleurs du dossier.
        // (18/11) AVANT, on calculait tout en HSV. Problème : en HSV, une même « luminosité » ne donne pas la
        // même clarté à l'œil (un jaune paraît bien plus clair qu'un bleu), et une intensité à fond donne des
        // couleurs criardes. MAINTENANT, on ne garde de la roue que la TEINTE (l'angle), puis on construit
        // chaque couleur dans l'espace OKLCH (voir plus bas), fait pour coller à ce que voit l'œil :
        //   L = la clarté (0 noir -> 1 blanc), C = l'intensité de la couleur, h = la teinte.
        // À clarté égale, toutes les teintes paraissent aussi claires : le dossier bleu est aussi lisible que le
        // jaune, et les fenêtres ont toutes la même « profondeur » de sombre.
        // L'intensité de la roue (s) règle C, en restant raisonnable ; la luminosité (v) règle la clarté du logo.
        fun depuis(hsv: FloatArray): Teinte {
            val s = hsv[1].coerceIn(0f, 1f)
            // la teinte vue par l'œil : celle de la couleur PURE de la roue (stable même si s est presque à 0)
            val h = versOklch(Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f)))[2]
            val u = (hsv[2].coerceIn(0.2f, 1f) - 0.2f) / 0.8f      // la luminosité, ramenée de 0 à 1
            val l = 0.56f + 0.22f * u                               // clarté du logo : jamais trop sombre
            val ch = 0.05f + 0.17f * s                              // intensité du logo : vive, jamais criarde (18/11 : relevée, Will voulait que ça ressorte plus)
            val cs = 0.6f + 0.4f * s                                // les fenêtres suivent un peu l'intensité
            return Teinte(
                intArrayOf(oklch(l, ch, h - 28), oklch(l - 0.02f, ch * 0.97f, h), oklch(l + 0.05f, ch * 0.92f, h + 42)),
                oklch(0.32f, 0.065f * cs, h),          // haut de la fenêtre
                oklch(0.22f, 0.048f * cs, h),          // bas de la fenêtre
                oklch(0.38f, 0.08f * cs, h),           // bulle « + »
                oklch(0.95f, 0.035f, h),               // « + », croix, points
                oklch(l, ch * 1.05f, h),               // lueur (cœur)
                oklch(l - 0.05f, ch * 1.15f, h + 12),  // lueur (bord)
                oklch(0.87f, 0.07f, h),                // poussières
                oklch(0.86f, 0.075f, h),               // onde
                oklch(0.76f, 0.045f, h),               // « 5m ago »
                oklch(0.89f, 0.065f, h),               // mode d'emploi
                hsv.copyOf())
        }

        // ---------------- OKLCH : les conversions (formules publiques de Björn Ottosson, 2020) ----------------
        // Un écran mélange du rouge, du vert et du bleu (« sRGB »), mais pas en ligne droite : on passe d'abord
        // en lumière « linéaire », puis dans l'espace Oklab (L, a, b), et enfin en OKLCH (a et b vus comme un
        // angle h et une distance C). Pas besoin de comprendre les nombres : ce sont des constantes fixes.
        private fun lineaire(x: Float): Float = if (x <= 0.04045f) x / 12.92f else ((x + 0.055f) / 1.055f).pow(2.4f)
        private fun ecran(x: Float): Float = if (x <= 0.0031308f) 12.92f * x else 1.055f * x.pow(1f / 2.4f) - 0.055f

        // Une couleur de l'écran -> (L, C, h en degrés).
        fun versOklch(couleur: Int): FloatArray {
            val r = lineaire(Color.red(couleur) / 255f); val g = lineaire(Color.green(couleur) / 255f); val b = lineaire(Color.blue(couleur) / 255f)
            val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
            val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
            val q = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
            val L = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * q
            val A = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * q
            val B = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * q
            return floatArrayOf(L, hypot(A, B), ((Math.toDegrees(atan2(B, A).toDouble()).toFloat()) + 360f) % 360f)
        }

        // (L, a, b) -> rouge, vert, bleu « linéaires » (peuvent sortir de 0..1 si l'écran ne sait pas l'afficher).
        private fun versRvb(L: Float, a: Float, b: Float): FloatArray {
            val l = L + 0.3963377774f * a + 0.2158037573f * b
            val m = L - 0.1055345942f * a - 0.0638541728f * b
            val q = L - 0.0894841775f * a - 1.2914855480f * b
            val l3 = l * l * l; val m3 = m * m * m; val q3 = q * q * q
            return floatArrayOf(4.0767416621f * l3 - 3.3077115913f * m3 + 0.2309699292f * q3,
                               -1.2684380046f * l3 + 2.6097574011f * m3 - 0.3413193965f * q3,
                               -0.0041960863f * l3 - 0.7034186147f * m3 + 1.7076147010f * q3)
        }

        // (L, C, h) -> une couleur de l'écran. Si la couleur demandée est trop intense pour l'écran, on baisse
        // son intensité C (sans toucher la clarté ni la teinte) jusqu'à ce qu'elle rentre : par dichotomie,
        // c'est-à-dire en coupant l'écart en deux à chaque essai (14 essais suffisent largement).
        fun oklch(L0: Float, C0: Float, h: Float): Int {
            val L = L0.coerceIn(0f, 1f)
            val rad = Math.toRadians(h.toDouble())
            val ca = cos(rad).toFloat(); val sa = sin(rad).toFloat()
            fun rentre(C: Float) = versRvb(L, C * ca, C * sa).all { it >= -0.0001f && it <= 1.0001f }
            var C = C0
            if (!rentre(C)) {
                var bas = 0f; var haut = C0
                repeat(14) { val milieu = (bas + haut) / 2; if (rentre(milieu)) bas = milieu else haut = milieu }
                C = bas
            }
            val rvb = versRvb(L, C * ca, C * sa)
            fun canal(x: Float) = (ecran(x.coerceIn(0f, 1f)) * 255f).roundToInt().coerceIn(0, 255)
            return Color.rgb(canal(rvb[0]), canal(rvb[1]), canal(rvb[2]))
        }

        // La couleur « vitrine » d'une couleur de la roue (le milieu du dégradé du logo) : pour les petits ronds,
        // le code affiché, le bouton « Done » et les widgets.
        fun vitrine(hsv: FloatArray): Int = depuis(hsv).logo[1]

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
