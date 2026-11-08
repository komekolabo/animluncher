package com.will.animlauncher

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

// L'écran JOURNAL : le journal complet, avec Copier / Effacer / Envoyer,
// et la case où l'on colle (une seule fois) la clé GitHub pour l'envoi automatique.
class JournalActivity : Activity() {

    private lateinit var contenu: TextView
    private lateinit var etat: TextView
    private lateinit var caseJeton: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fun bouton(t: String, action: () -> Unit) = Button(this).apply {
            text = t
            setOnClickListener { action() }
        }
        // Une rangée horizontale de boutons, chacun prend la même largeur (poids 1).
        fun rangee(vararg vues: android.view.View) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            for (v in vues) addView(v, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        // --- l'envoi automatique sur GitHub ---
        caseJeton = EditText(this).apply {
            hint = "Clé GitHub (github_pat_…)"
            setHintTextColor(Color.parseColor("#7A84A8"))
            setTextColor(Color.WHITE)
            textSize = 14f
            // les caractères sont masqués, comme un mot de passe
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
        }
        val enregistrer = bouton("Enregistrer la clé") {
            Journal.definirJeton(this, caseJeton.text.toString())
            Toast.makeText(this, if (caseJeton.text.isBlank()) "Clé retirée" else "Clé enregistrée", Toast.LENGTH_SHORT).show()
            majEtat()
        }
        etat = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#A9B3D6"))
            setPadding(0, 8, 0, 16)
        }

        // --- les actions sur le journal ---
        val barre = rangee(
            bouton("Copier") { copier() },
            bouton("Effacer") { Journal.effacer(this); contenu.text = Journal.texte(this) },
            bouton("Envoyer") {
                etat.text = "Envoi…"
                Journal.envoyer(this) { message -> majEtat(); Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
            }
        )

        contenu = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#DFE4F7"))
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, 24, 0, 24)
        }

        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1B2340"))
            setPadding(32, 120, 32, 32)
            addView(rangee(caseJeton, enregistrer))
            addView(etat)
            addView(barre)
            // Le texte défile et prend toute la place restante (hauteur 0 + poids 1).
            addView(
                ScrollView(this@JournalActivity).apply { addView(contenu) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            )
        }
        setContentView(colonne)
    }

    override fun onResume() {
        super.onResume()
        contenu.text = Journal.texte(this)
        caseJeton.setText(Journal.jeton(this))
        majEtat()
    }

    // La petite ligne d'état : envoi automatique actif ou non, dernier envoi, lien du ticket.
    private fun majEtat() {
        val actif = Journal.jeton(this).isNotEmpty()
        val dernier = Journal.etat(this).ifBlank { "aucun envoi pour l'instant" }
        val lien = Journal.lienTicket(this)?.let { "\n$it" } ?: ""
        etat.text = if (actif) "Envoi automatique sur GitHub : activé\nDernier envoi : $dernier$lien"
                    else "Envoi automatique sur GitHub : désactivé (pas de clé)"
    }

    // Met le texte dans le presse-papiers, prêt à coller dans Claude.
    private fun copier() {
        val pressePapiers = getSystemService(ClipboardManager::class.java)
        pressePapiers.setPrimaryClip(ClipData.newPlainText("Journal AnimLauncher", Journal.texte(this)))
        Toast.makeText(this, "Journal copié", Toast.LENGTH_SHORT).show()
    }
}
