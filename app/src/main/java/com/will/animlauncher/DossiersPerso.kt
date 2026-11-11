package com.will.animlauncher

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

// LES PETITES FENÊTRES DES DOSSIERS PERSO (11/11, demande de Will) :
//  - « Nouveau dossier » : un nom + le choix du logo animé, dans une grille où tous les logos bougent ;
//  - « Changer le logo » : la même grille seule ;
//  - « Renommer » : une case de texte (on y arrive aussi en touchant le titre du dossier ouvert) ;
//  - « Supprimer » : une question de confirmation.
// Toutes ont le même style : une carte sombre arrondie, au centre de l'écran, comme la fenêtre de la réaction sonore.
// Elles ne font qu'une chose : demander, puis appeler « fini » avec la réponse. C'est l'écran qui les ouvre qui
// enregistre (Groupes.creer, Groupes.renommer…).
object DossiersPerso {

    // ---------------- la carte de base ----------------
    private class Fenetre(val dialogue: Dialog, val colonne: LinearLayout)

    private fun fenetre(a: Activity, titre: String): Fenetre {
        val dialogue = Dialog(a)
        dialogue.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val colonne = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = Style.arrondi(a, Color.rgb(16, 16, 19), 28f, Style.BORD)
            setPadding(Style.px(a, 22f), Style.px(a, 20f), Style.px(a, 22f), Style.px(a, 18f))
        }
        colonne.addView(Style.texte(a, titre, 20f, Style.TEXTE, gras = true).apply { setPadding(0, 0, 0, Style.px(a, 14f)) })
        dialogue.setContentView(colonne)
        dialogue.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((a.resources.displayMetrics.widthPixels * 0.9f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.7f)
        }
        return Fenetre(dialogue, colonne)
    }

    // la case pour taper un nom (le même style « pilule » que la recherche de la feuille « Ajouter une app »)
    private fun caseNom(a: Activity, texte: String): EditText = EditText(a).apply {
        setText(texte)
        setSelection(text.length)
        hint = a.getString(R.string.dossier_nom_indice)
        setTextColor(Color.WHITE)
        setHintTextColor(Color.argb(105, 255, 255, 255))
        textSize = 16f
        setSingleLine()
        background = Style.arrondi(a, Color.argb(18, 255, 255, 255), 16f, Color.argb(30, 255, 255, 255))
        setPadding(Style.px(a, 16f), Style.px(a, 12f), Style.px(a, 16f), Style.px(a, 12f))
    }

    // les deux boutons du bas : « Annuler » (discret) et le bouton principal (blanc)
    private fun boutons(a: Activity, f: Fenetre, principal: String, action: () -> Unit) {
        f.colonne.addView(LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, Style.px(a, 16f), 0, 0)
            addView(Style.boutonDiscret(a, a.getString(R.string.dossier_annuler)) { f.dialogue.dismiss() },
                LinearLayout.LayoutParams(0, Style.px(a, 46f), 1f))
            addView(Style.boutonBlanc(a, principal) { action() },
                LinearLayout.LayoutParams(0, Style.px(a, 46f), 1f).apply { leftMargin = Style.px(a, 8f) })
        })
    }

    // ---------------- LA GRILLE DES LOGOS ----------------
    // Tous les logos (LogosDossier.LISTE), 5 par rangée. Chaque case est un LogoVue (voir ChoixAppActivity.kt) qui
    // dessine le logo au repos ; une petite « vague » fait jouer leur geste l'un après l'autre, en boucle, pour qu'on
    // voie qu'ils sont animés. Le logo choisi a un fond clair et joue son geste quand on le touche.
    // Renvoie la grille, et une fonction pour arrêter la vague (à appeler quand la fenêtre se ferme).
    private fun grille(a: Activity, choisi: String, surChoix: (String) -> Unit): Pair<View, () -> Unit> {
        val vues = ArrayList<Pair<String, FrameLayout>>()
        val logos = ArrayList<LogoVue>()
        val colonne = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        var actuel = choisi
        fun peindre() {
            for ((cle, case) in vues) case.background = if (cle == actuel)
                Style.arrondi(a, Color.argb(34, 255, 255, 255), 16f, Color.argb(120, 255, 255, 255)) else null
        }
        val liste = LogosDossier.LISTE
        for (debut in liste.indices step 5) {
            val rangee = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in 0 until 5) {
                val cle = liste.getOrNull(debut + k)
                if (cle == null) { rangee.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f)); continue }
                // « logo:… » : on dessine CE logo-là, avec ses propres couleurs (pas celui d'un dossier)
                val logo = LogoVue(a, "logo:$cle", LogosDossier.couleurs("logo:$cle"))
                logos.add(logo)
                val case = FrameLayout(a).apply {
                    setPadding(Style.px(a, 4f), Style.px(a, 4f), Style.px(a, 4f), Style.px(a, 4f))
                    addView(logo, FrameLayout.LayoutParams(Style.px(a, 44f), Style.px(a, 44f), Gravity.CENTER))
                    setOnClickListener { actuel = cle; peindre(); logo.jouer(); surChoix(cle) }
                }
                vues.add(cle to case)
                rangee.addView(case, LinearLayout.LayoutParams(0, Style.px(a, 58f), 1f))
            }
            colonne.addView(rangee)
        }
        peindre()
        // la vague : toutes les 0,35 s, le logo suivant joue son geste
        val minuteur = Handler(Looper.getMainLooper())
        var n = 0
        val vague = object : Runnable {
            override fun run() { logos[n % logos.size].jouer(); n++; minuteur.postDelayed(this, 350) }
        }
        minuteur.postDelayed(vague, 300)
        val defilant = ScrollView(a).apply { addView(colonne); isVerticalScrollBarEnabled = false }
        // (la grille ne prend pas plus de la moitié de l'écran : sur un petit écran, elle défile)
        val boite = FrameLayout(a).apply {
            addView(defilant, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                minOf((a.resources.displayMetrics.heightPixels * 0.45f).toInt(), Style.px(a, 58f) * 5 + Style.px(a, 4f))))
        }
        return boite to { minuteur.removeCallbacks(vague) }
    }

    // ---------------- « NOUVEAU DOSSIER » ----------------
    // Un nom et un logo ; « Créer » appelle fini(nom, logo).
    fun nouveau(a: Activity, fini: (String, String) -> Unit) {
        val f = fenetre(a, a.getString(R.string.dossier_titre_nouveau))
        val nom = caseNom(a, "")
        f.colonne.addView(nom)
        f.colonne.addView(Style.texte(a, a.getString(R.string.dossier_choisir_logo), 13f, Style.MUET).apply {
            setPadding(Style.px(a, 4f), Style.px(a, 16f), 0, Style.px(a, 8f))
        })
        var logo = "maison"
        val (vue, arreter) = grille(a, logo) { logo = it }
        f.colonne.addView(vue)
        boutons(a, f, a.getString(R.string.dossier_creer)) {
            val texte = nom.text.toString().trim().ifEmpty { a.getString(R.string.dossier_titre_nouveau) }
            f.dialogue.dismiss()
            fini(texte, logo)
        }
        f.dialogue.setOnDismissListener { arreter() }
        f.dialogue.show()
    }

    // ---------------- « CHANGER LE LOGO » ----------------
    fun choisirLogo(a: Activity, actuel: String, fini: (String) -> Unit) {
        val f = fenetre(a, a.getString(R.string.dossier_changer_logo))
        var logo = actuel
        val (vue, arreter) = grille(a, actuel) { logo = it }
        f.colonne.addView(vue)
        boutons(a, f, a.getString(R.string.dossier_ok)) { f.dialogue.dismiss(); fini(logo) }
        f.dialogue.setOnDismissListener { arreter() }
        f.dialogue.show()
    }

    // ---------------- « RENOMMER » ----------------
    // Le clavier s'ouvre tout seul, avec le nom actuel déjà écrit (tout sélectionné : on tape directement le nouveau).
    fun renommer(a: Activity, actuel: String, fini: (String) -> Unit) {
        val f = fenetre(a, a.getString(R.string.dossier_renommer_titre))
        val nom = caseNom(a, actuel).apply { selectAll() }
        f.colonne.addView(nom)
        boutons(a, f, a.getString(R.string.dossier_ok)) { f.dialogue.dismiss(); fini(nom.text.toString()) }
        f.dialogue.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        f.dialogue.show()
        nom.requestFocus()
    }

    // ---------------- « SUPPRIMER » ----------------
    fun confirmerSuppression(a: Activity, fini: () -> Unit) {
        val f = fenetre(a, a.getString(R.string.dossier_supprimer))
        f.colonne.addView(Style.texte(a, a.getString(R.string.dossier_supprimer_question), 14.5f, Style.DOUX).apply {
            setLineSpacing(0f, 1.2f)
        })
        boutons(a, f, a.getString(R.string.dossier_supprimer)) { f.dialogue.dismiss(); fini() }
        f.dialogue.show()
    }

    // un texte gris de note (utilisé par l'écran des dossiers)
    fun note(a: Activity, t: String): TextView = Style.texte(a, t, 13f, Style.MUET).apply { setLineSpacing(0f, 1.15f) }
}
