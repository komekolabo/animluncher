package com.will.animlauncher

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout

// LES OPTIONS DE LA RÉACTION SONORE (11/11, demandées par Will) :
//   - « Confettis » : cochée de base. Décochée, le palier 5 reste rouge mais ne lance plus de confettis.
//   - « Écran éteint, tour allumé » : décochée de base. Cochée, quand on éteint le téléphone pendant une
//     musique, l'écran reste noir avec le tour lumineux (voir VeilleSon.kt).
// Les deux choix sont rangés dans le même carnet que le reste de la réaction sonore (« son »).
// C'est un petit fichier à part pour ne pas alourdir ReactionSonore.kt et ReactionSonoreUi.kt.
object OptionsSon {
    private const val CARNET = "son"

    // Les deux choix, gardés en mémoire pour que le dessin puisse les lire 60 fois par seconde sans rien coûter.
    @Volatile var confettis = true
        private set
    @Volatile var veille = false
        private set
    private var pret = false

    private fun carnet(c: Context) = c.applicationContext.getSharedPreferences(CARNET, Context.MODE_PRIVATE)

    // Relit les deux choix dans le carnet.
    fun lire(c: Context) {
        confettis = carnet(c).getBoolean("confettis", true)
        veille = carnet(c).getBoolean("veille", false)
    }

    // À appeler n'importe quand : la PREMIÈRE fois, lit les choix et branche l'écoute « l'écran s'éteint » ;
    // ensuite ne fait plus rien. (Appelé par le dessin du tour, voir Confettis.dessiner, et par la fenêtre du rond.)
    fun preparer() {
        if (pret) return
        val c = AnimApp.contexte ?: return
        pret = true
        lire(c)
        VeilleSon.brancher(c)
    }

    fun definirConfettis(c: Context, valeur: Boolean) {
        confettis = valeur
        carnet(c).edit().putBoolean("confettis", valeur).apply()
    }

    fun definirVeille(c: Context, valeur: Boolean) {
        veille = valeur
        carnet(c).edit().putBoolean("veille", valeur).apply()
    }

    // Ajoute les deux lignes « option + interrupteur » dans la fenêtre du rond (appelé par ReactionSonoreUi.kt).
    fun ajouter(a: Activity, colonne: LinearLayout) {
        preparer()
        lire(a)
        fun px(v: Float) = Style.px(a, v)
        colonne.addView(Style.titreCarte(a, a.getString(R.string.son_options)).apply { setPadding(0, px(16f), 0, px(6f)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // Une ligne : à gauche le nom et sa petite explication, à droite l'interrupteur.
        fun rangee(titre: Int, description: Int, coche: Boolean, change: (Boolean) -> Unit) {
            val bouton = Style.interrupteur(a).apply {
                isChecked = coche
                setOnCheckedChangeListener { _, valeur -> change(valeur) }
            }
            val ligne = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = Style.arrondi(a, Color.argb(14, 255, 255, 255), 14f, Style.BORD)
                setPadding(px(14f), px(9f), px(10f), px(9f))
                addView(LinearLayout(a).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Style.texte(a, a.getString(titre), 15f, Style.TEXTE, gras = true))
                    addView(Style.texte(a, a.getString(description), 12.5f, Style.MUET))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(bouton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = px(10f) })
                setOnClickListener { bouton.toggle() }         // un appui n'importe où sur la ligne bascule l'interrupteur
            }
            colonne.addView(ligne, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(6f) })
        }
        rangee(R.string.son_confettis, R.string.son_confettis_desc, confettis) { definirConfettis(a, it) }
        rangee(R.string.son_veille, R.string.son_veille_desc, veille) { definirVeille(a, it) }
        // LES APPLICATIONS (11/11) : une ligne qui ouvre la fenêtre où l'on choisit pour quelles apps le tour
        // s'allume (voir AppsSon.kt). Pas d'interrupteur ici : une petite flèche « › » à droite.
        val versApps = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Style.arrondi(a, Color.argb(14, 255, 255, 255), 14f, Style.BORD)
            setPadding(px(14f), px(9f), px(14f), px(9f))
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                addView(Style.texte(a, a.getString(R.string.son_apps), 15f, Style.TEXTE, gras = true))
                addView(Style.texte(a, a.getString(R.string.son_apps_desc), 12.5f, Style.MUET))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Style.texte(a, "›", 22f, Style.MUET))
            setOnClickListener { AppsSon.ouvrir(a) }
        }
        colonne.addView(versApps, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(6f) })
    }
}
