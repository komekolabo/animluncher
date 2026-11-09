package com.will.animlauncher

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout

// L'ÉCRAN « FONDS D'ÉCRAN » (carte 3 de l'accueil). Pour l'instant, c'est un écran d'attente :
// il montre un aperçu animé (le même fond que sur la carte) et explique ce qui arrive.
// Plus tard : de vrais fonds d'écran animés (un « WallpaperService » Android qui réutilisera les
// outils de dessin des animations), puis la partie communauté (qui demandera un serveur).
class FondsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Style.fenetreNoire(this)
        val px = { v: Float -> Style.px(this, v) }

        // l'en-tête : la flèche de retour et le titre
        val entete = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(4f), 0, px(16f), 0)
            addView(Icone(this@FondsActivity, Icone.RETOUR).apply {
                contentDescription = getString(R.string.retour)
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(px(48f), px(48f)))
            addView(Style.texte(this@FondsActivity, getString(R.string.carte_fonds), 20f, Style.TEXTE, gras = true).apply {
                setPadding(px(6f), 0, 0, 0)
            })
        }

        // l'aperçu : un grand cadre arrondi avec le fond animé dedans
        val apercu = FrameLayout(this).apply {
            background = Style.arrondi(this@FondsActivity, Style.CARTE, 30f, Style.BORD)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true          // le dessin est coupé selon l'arrondi
            addView(DessinFonds(this@FondsActivity))
        }

        val textes = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24f), px(20f), px(24f), px(28f))
            addView(Style.texte(this@FondsActivity, getString(R.string.bientot), 22f, Style.TEXTE, gras = true))
            addView(Style.texte(this@FondsActivity,
                getString(R.string.fonds_note),
                14f, Style.MUET).apply { setLineSpacing(0f, 1.2f); setPadding(0, px(8f), 0, 0) })
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Style.NOIR)
            addView(entete, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(64f)))
            addView(apercu, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                leftMargin = px(20f); rightMargin = px(20f)
            })
            addView(textes)
        })
    }
}
