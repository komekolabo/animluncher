package com.will.animlauncher

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

// LES RÉGLAGES D'UNE APP, refaits le 09/11 dans le style noir, le plus simple possible :
//   1. un interrupteur (animation allumée ou coupée) et un gros bouton « Voir l'animation » ;
//   2. la vitesse, en 3 choix : Rapide, Normale, Lente (fini le curseur de 0 à 4 s) ;
//   3. les variantes, s'il y en a (une est tirée au hasard parmi celles qui sont cochées) ;
//   4. « seulement au démarrage » pour cette app ;
//   5. l'icône animée (le widget « galet ») à poser sur l'accueil.
class ReglageAppActivity : Activity() {

    private lateinit var pkg: String
    private lateinit var entree: Entree
    private lateinit var racine: FrameLayout          // l'écran entier : l'aperçu s'affiche par-dessus
    private lateinit var avertissement: TextView
    private val vitesses = ArrayList<TextView>()      // les 3 boutons Rapide / Normale / Lente
    private val principal = Handler(Looper.getMainLooper())
    private var apercuEnCours: AnimationView? = null
    private fun px(v: Float) = Style.px(this, v)

    // Les 3 vitesses : leur nom, et par combien on multiplie la durée normale de l'animation.
    // (Normale = la durée prévue pour cette app, rangée comme « -1 » dans les réglages.)
    private val NOMS = listOf("Rapide", "Normale", "Lente")
    private val FACTEURS = floatArrayOf(0.6f, 1f, 1.6f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Style.fenetreNoire(this)
        pkg = intent.getStringExtra("pkg") ?: run { finish(); return }
        entree = Catalogue.pour(pkg) ?: run { finish(); return }
        val nom = intent.getStringExtra("nom") ?: pkg

        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16f), px(4f), px(16f), px(32f))
        }

        // ---------- 1. allumer / couper, et voir ----------
        val carteVoir = Style.carte(this)
        val interActif = Style.interrupteur(this).apply {
            text = "Animation"
            textSize = 17f
            setTextColor(Style.TEXTE)
            isChecked = Reglages.appActive(this@ReglageAppActivity, pkg)
            setOnCheckedChangeListener { _, coche ->
                Reglages.definirAppActive(this@ReglageAppActivity, pkg, coche)
                majAvertissement()
            }
        }
        carteVoir.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(this@ReglageAppActivity).apply {
                try { setImageDrawable(packageManager.getApplicationIcon(pkg)) } catch (_: Exception) {}
            }, LinearLayout.LayoutParams(px(44f), px(44f)).apply { rightMargin = px(14f) })
            addView(interActif, LinearLayout.LayoutParams(0, px(48f), 1f))
        })
        carteVoir.addView(Style.boutonBlanc(this, "▶   Voir l'animation") { apercu(-1) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(50f)).apply { topMargin = px(12f) })
        colonne.addView(carteVoir)

        // l'avertissement (en orange) : il n'apparaît que si l'animation ne peut pas se lancer
        avertissement = Style.texte(this, "", 13.5f, Style.AMBRE).apply { setPadding(px(6f), 0, px(6f), px(12f)) }
        colonne.addView(avertissement)

        // ---------- 2. la vitesse ----------
        val carteVitesse = Style.carte(this)
        carteVitesse.addView(Style.titreCarte(this, "Vitesse"))
        val rangee = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in NOMS.indices) {
            val bouton = Style.texte(this, NOMS[i], 14.5f, Style.TEXTE).apply {
                gravity = Gravity.CENTER
                isClickable = true
                setOnClickListener {
                    // Normale -> on efface le choix (-1) ; sinon on range la durée en millisecondes
                    val ms = if (i == 1) -1 else Math.round(entree.dureeDefaut * FACTEURS[i])
                    Reglages.definirDuree(this@ReglageAppActivity, pkg, ms)
                    majVitesse()
                }
            }
            vitesses.add(bouton)
            rangee.addView(bouton, LinearLayout.LayoutParams(0, px(46f), 1f).apply { if (i > 0) leftMargin = px(8f) })
        }
        carteVitesse.addView(rangee)
        colonne.addView(carteVitesse)

        // ---------- 3. les variantes (seulement pour les apps qui en ont plusieurs) ----------
        if (entree.variantes.isNotEmpty()) {
            val carteVariantes = Style.carte(this)
            carteVariantes.addView(Style.titreCarte(this, "Variantes"))
            carteVariantes.addView(Style.texte(this, "Une est tirée au hasard à chaque ouverture, parmi celles cochées.", 13f, Style.MUET)
                .apply { setPadding(0, 0, 0, px(6f)) })
            val eteintes = Reglages.variantesDesactivees(this, pkg)
            // les cases à cocher aux couleurs de la refonte : blanche quand elle est cochée, grise sinon
            val teinte = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Style.TEXTE, Color.rgb(113, 113, 122)))
            for ((numero, nomVariante) in entree.variantes.withIndex()) {
                val ligne = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                ligne.addView(CheckBox(this).apply {
                    text = nomVariante
                    textSize = 15.5f
                    setTextColor(Style.TEXTE)
                    buttonTintList = teinte
                    isChecked = numero !in eteintes
                    setOnCheckedChangeListener { _, coche ->
                        Reglages.definirVariante(this@ReglageAppActivity, pkg, numero, coche)
                        majAvertissement()
                    }
                }, LinearLayout.LayoutParams(0, px(48f), 1f))
                // le petit bouton rond ▶ : voir CETTE variante
                ligne.addView(Style.texte(this, "▶", 13f, Style.TEXTE).apply {
                    gravity = Gravity.CENTER
                    contentDescription = "Voir $nomVariante"
                    background = Style.arrondi(this@ReglageAppActivity, Color.argb(20, 255, 255, 255), 20f, Color.rgb(63, 63, 70))
                    isClickable = true
                    setOnClickListener { apercu(numero) }
                }, LinearLayout.LayoutParams(px(40f), px(40f)))
                carteVariantes.addView(ligne)
            }
            colonne.addView(carteVariantes)
        }

        // ---------- 4. seulement au démarrage, pour cette app ----------
        // (tant qu'on n'y touche pas, l'app suit l'interrupteur général des réglages)
        val carteDemarrage = Style.carte(this)
        val noteDemarrage = Style.texte(this, "", 13f, Style.MUET)
        val suivre = Style.texte(this, "Revenir au réglage général ›", 13f, Style.DOUX).apply {
            setPadding(0, px(10f), 0, 0)
            isClickable = true
        }
        var majAuto = false      // vrai quand c'est le programme (pas le doigt) qui bouge l'interrupteur
        val interDemarrage = Style.interrupteur(this).apply {
            text = "Seulement au démarrage"
            textSize = 16f
            setTextColor(Style.TEXTE)
            isChecked = Reglages.seulementDemarrage(this@ReglageAppActivity, pkg)
            setOnCheckedChangeListener { _, coche ->
                if (majAuto) return@setOnCheckedChangeListener
                Reglages.definirSeulementDemarrage(this@ReglageAppActivity, pkg, coche)
                majNoteDemarrage(noteDemarrage, suivre)
            }
        }
        suivre.setOnClickListener {
            Reglages.oublierSeulementDemarrage(this, pkg)
            majAuto = true
            interDemarrage.isChecked = Reglages.seulementDemarrage(this, pkg)
            majAuto = false
            majNoteDemarrage(noteDemarrage, suivre)
        }
        carteDemarrage.addView(interDemarrage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(48f)))
        carteDemarrage.addView(noteDemarrage)
        carteDemarrage.addView(suivre)
        majNoteDemarrage(noteDemarrage, suivre)
        colonne.addView(carteDemarrage)

        // ---------- 5. l'icône animée de cette app (voir WidgetApp.kt) ----------
        val carteWidget = Style.carte(this)
        carteWidget.addView(Style.titreCarte(this, "Icône animée"))
        carteWidget.addView(Style.texte(this, "Le logo de $nom qui flotte doucement dans son icône, à poser sur " +
            "l'accueil à la place de l'icône normale.", 13.5f, Style.MUET).apply { setLineSpacing(0f, 1.15f); setPadding(0, 0, 0, px(12f)) })
        carteWidget.addView(Style.boutonDiscret(this, "Mettre sur l'accueil") {
            if (!WidgetApp.proposer(this, pkg)) {
                Toast.makeText(this, "Appui long sur l'accueil → Widgets → " + getString(R.string.app_name), Toast.LENGTH_LONG).show()
            }
        })
        colonne.addView(carteWidget)

        racine = FrameLayout(this).apply {
            setBackgroundColor(Style.NOIR)
            addView(LinearLayout(this@ReglageAppActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(Style.entete(this@ReglageAppActivity, nom))
                addView(ScrollView(this@ReglageAppActivity).apply {
                    isVerticalScrollBarEnabled = false
                    addView(colonne)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            })
        }
        setContentView(racine)
        majVitesse()
    }

    // La durée réglée pour cette app, en millisecondes (la durée normale si rien n'est choisi).
    private fun dureeMs(): Int = Reglages.duree(this, pkg).let { if (it < 0) entree.dureeDefaut else it }

    // Quelle vitesse est choisie ? 0 = Rapide, 1 = Normale, 2 = Lente, -1 = aucune (durée à 0).
    // (Une ancienne durée réglée au curseur est rangée dans la vitesse la plus proche.)
    private fun vitesseChoisie(): Int {
        val choisie = Reglages.duree(this, pkg)
        if (choisie < 0) return 1
        if (choisie == 0) return -1
        val rapport = choisie.toFloat() / entree.dureeDefaut
        return if (rapport < 0.8f) 0 else if (rapport > 1.3f) 2 else 1
    }

    // Colore les 3 boutons : celui qui est choisi devient blanc, les autres restent sombres.
    private fun majVitesse() {
        val choix = vitesseChoisie()
        for ((i, bouton) in vitesses.withIndex()) {
            val actif = i == choix
            bouton.background = if (actif) Style.arrondi(this, Style.TEXTE, 14f)
                                else Style.arrondi(this, Color.argb(20, 255, 255, 255), 14f, Color.rgb(63, 63, 70))
            bouton.setTextColor(if (actif) Style.ENCRE else Style.TEXTE)
        }
        majAvertissement()
    }

    // La petite explication sous l'interrupteur « Seulement au démarrage », et le lien pour
    // revenir au réglage général (il n'apparaît que si on a fait un choix pour cette app).
    private fun majNoteDemarrage(note: TextView, suivre: View) {
        note.text = if (Reglages.seulementDemarrage(this, pkg))
            "Pas d'animation si l'app était encore ouverte en arrière-plan."
            else "L'animation se joue à chaque appui sur l'icône."
        suivre.visibility = if (Reglages.seulementDemarrageChoisi(this, pkg)) View.VISIBLE else View.GONE
    }

    // Prévient si, avec ces réglages, l'animation ne se lancera jamais.
    private fun majAvertissement() {
        val texte = when {
            !Reglages.appActive(this, pkg) -> "L'animation de cette app est coupée."
            Reglages.duree(this, pkg) == 0 -> "Choisis une vitesse : avec une durée de 0, l'animation ne se lance pas."
            entree.variantes.isNotEmpty() &&
                Reglages.variantesDesactivees(this, pkg).size >= entree.variantes.size ->
                "Aucune variante cochée : l'animation ne se lance pas."
            else -> ""
        }
        avertissement.text = texte
        avertissement.visibility = if (texte.isEmpty()) View.GONE else View.VISIBLE
    }

    // L'APERÇU : joue l'animation par-dessus cet écran, comme si on ouvrait l'app.
    // numero = la variante à montrer (-1 = une au hasard parmi celles cochées).
    private fun apercu(numero: Int) {
        if (apercuEnCours != null) return
        val ms = dureeMs()
        if (ms == 0) {
            Toast.makeText(this, "Choisis d'abord une vitesse", Toast.LENGTH_SHORT).show()
            return
        }
        var n = numero
        if (n < 0 && entree.variantes.isNotEmpty()) {
            val permises = entree.variantes.indices.filter { it !in Reglages.variantesDesactivees(this, pkg) }
            n = if (permises.isEmpty()) 0 else permises.random()
        }
        val facteur = ms.toFloat() / entree.dureeDefaut
        val vue = AnimationView(this, entree.fabrique(n), entree.categorie, facteur)
        apercuEnCours = vue
        racine.addView(vue, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // On fait comme si l'app était prête après un temps de chargement typique (0,85 s × facteur).
        principal.postDelayed({ vue.signalerPret() }, (850 * facteur).toLong())
        vue.jouer {
            racine.removeView(vue)
            apercuEnCours = null
        }
    }

    override fun onPause() {
        super.onPause()
        apercuEnCours?.let { it.arreter(); racine.removeView(it); apercuEnCours = null }
    }
}
