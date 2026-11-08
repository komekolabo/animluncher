package com.will.animlauncher

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

// LES RÉGLAGES D'UNE APP : activer ou couper son animation, choisir sa durée (0 à 4 s),
// choisir ses variantes (pour Claude, Maps, les échecs…), et voir un aperçu.
class ReglageAppActivity : Activity() {

    private lateinit var pkg: String
    private lateinit var entree: Entree
    private lateinit var racine: FrameLayout          // l'écran entier : l'aperçu s'affiche par-dessus
    private lateinit var texteDuree: TextView
    private lateinit var curseur: SeekBar
    private lateinit var avertissement: TextView
    private val principal = Handler(Looper.getMainLooper())
    private var apercuEnCours: AnimationView? = null
    private var dp = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dp = resources.displayMetrics.density
        pkg = intent.getStringExtra("pkg") ?: run { finish(); return }
        entree = Catalogue.pour(pkg) ?: run { finish(); return }
        val nom = intent.getStringExtra("nom") ?: pkg

        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((22 * dp).toInt(), (40 * dp).toInt(), (22 * dp).toInt(), (40 * dp).toInt())
        }

        // --- l'en-tête : icône et nom de l'app ---
        val entete = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (24 * dp).toInt())
            addView(ImageView(this@ReglageAppActivity).apply {
                try { setImageDrawable(packageManager.getApplicationIcon(pkg)) } catch (_: Exception) {}
            }, LinearLayout.LayoutParams((56 * dp).toInt(), (56 * dp).toInt()))
            addView(TextView(this@ReglageAppActivity).apply {
                text = nom
                textSize = 26f
                setTextColor(Color.WHITE)
                setPadding((16 * dp).toInt(), 0, 0, 0)
            })
        }
        colonne.addView(entete)

        // --- 1. activer / couper l'animation de cette app ---
        val carteActive = carte()
        carteActive.addView(Switch(this).apply {
            text = "Animation activée"
            textSize = 19f
            setTextColor(Color.WHITE)
            isChecked = Reglages.appActive(this@ReglageAppActivity, pkg)
            setOnCheckedChangeListener { _, coche ->
                Reglages.definirAppActive(this@ReglageAppActivity, pkg, coche)
                majAvertissement()
            }
        })
        colonne.addView(carteActive)

        // --- 2. la durée : un curseur de 0 à 4 s, par pas de 0,1 s ---
        val carteDuree = carte()
        carteDuree.addView(titre("Durée de l'animation"))
        texteDuree = TextView(this).apply { textSize = 16f; setTextColor(Color.parseColor("#DFE4F7")) }
        carteDuree.addView(texteDuree)
        curseur = SeekBar(this).apply {
            max = 40                               // 40 crans de 0,1 s = 4 s
            setPadding((8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, valeur: Int, parUtilisateur: Boolean) {
                    if (parUtilisateur) {          // seulement quand c'est le doigt qui bouge le curseur
                        Reglages.definirDuree(this@ReglageAppActivity, pkg, valeur * 100)
                        majDuree()
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        carteDuree.addView(curseur)
        carteDuree.addView(TextView(this).apply {
            text = "0 s = pas d'animation · 4 s = très lent"
            textSize = 13f
            setTextColor(Color.parseColor("#94A0C4"))
        })
        carteDuree.addView(Button(this).apply {
            text = "Remettre la durée par défaut"
            setOnClickListener {
                Reglages.definirDuree(this@ReglageAppActivity, pkg, -1)
                majDuree()
            }
        })
        colonne.addView(carteDuree)

        // --- 3. les variantes (seulement pour les apps qui en ont plusieurs) ---
        if (entree.variantes.isNotEmpty()) {
            val carteVariantes = carte()
            carteVariantes.addView(titre("Variantes (une est tirée au hasard parmi celles cochées)"))
            val eteintes = Reglages.variantesDesactivees(this, pkg)
            for ((numero, nomVariante) in entree.variantes.withIndex()) {
                val ligne = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                ligne.addView(CheckBox(this).apply {
                    text = nomVariante
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    isChecked = numero !in eteintes
                    setOnCheckedChangeListener { _, coche ->
                        Reglages.definirVariante(this@ReglageAppActivity, pkg, numero, coche)
                        majAvertissement()
                    }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                ligne.addView(Button(this).apply {
                    text = "▶"
                    setOnClickListener { apercu(numero) }
                })
                carteVariantes.addView(ligne)
            }
            colonne.addView(carteVariantes)
        }

        // --- 4. l'aperçu et l'avertissement ---
        avertissement = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#FFB4A0"))
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        }
        colonne.addView(avertissement)
        colonne.addView(Button(this).apply {
            text = "▶  Voir l'animation"
            textSize = 17f
            setOnClickListener { apercu(-1) }
        })

        racine = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#1B2340"))
            addView(ScrollView(this@ReglageAppActivity).apply { addView(colonne) })
        }
        setContentView(racine)
        majDuree()
        majAvertissement()
    }

    // Une « carte » : un bloc arrondi un peu plus clair que le fond.
    private fun carte() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { setColor(Color.parseColor("#242E52")); cornerRadius = 18 * dp }
        setPadding((18 * dp).toInt(), (16 * dp).toInt(), (18 * dp).toInt(), (16 * dp).toInt())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = (14 * dp).toInt() }
    }

    private fun titre(t: String) = TextView(this).apply {
        text = t
        textSize = 15f
        setTextColor(Color.parseColor("#94A0C4"))
        setPadding(0, 0, 0, (8 * dp).toInt())
    }

    private fun secondes(ms: Int) = String.format("%.2f", ms / 1000f).trimEnd('0').trimEnd(',', '.') + " s"

    // Met à jour le texte et la position du curseur de durée.
    private fun majDuree() {
        val choisie = Reglages.duree(this, pkg)
        val ms = if (choisie < 0) entree.dureeDefaut else choisie
        curseur.progress = Math.round(ms / 100f)
        texteDuree.text = when {
            ms == 0 -> "0 s : pas d'animation"
            choisie < 0 -> secondes(ms) + " (par défaut)"
            else -> secondes(ms) + " (par défaut : ${secondes(entree.dureeDefaut)})"
        }
        majAvertissement()
    }

    // Prévient si, avec ces réglages, l'animation ne se lancera jamais.
    private fun majAvertissement() {
        avertissement.text = when {
            !Reglages.appActive(this, pkg) -> "⚠️ L'animation de cette app est coupée."
            Reglages.duree(this, pkg) == 0 -> "⚠️ Durée de 0 s : l'animation ne se lance pas."
            entree.variantes.isNotEmpty() &&
                Reglages.variantesDesactivees(this, pkg).size >= entree.variantes.size ->
                "⚠️ Aucune variante cochée : l'animation ne se lance pas."
            else -> ""
        }
    }

    // L'APERÇU : joue l'animation par-dessus cet écran, comme si on ouvrait l'app.
    // numero = la variante à montrer (-1 = une au hasard parmi celles cochées).
    private fun apercu(numero: Int) {
        if (apercuEnCours != null) return
        val choisie = Reglages.duree(this, pkg)
        val ms = if (choisie < 0) entree.dureeDefaut else choisie
        if (ms == 0) {
            Toast.makeText(this, "Durée de 0 s : pas d'animation", Toast.LENGTH_SHORT).show()
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
