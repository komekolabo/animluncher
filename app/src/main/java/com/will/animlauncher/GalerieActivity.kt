package com.will.animlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max

// L'ÉCRAN « GALERIE » (18/11) : les fonds d'écran proposés par l'app, rangés par catégorie.
// On l'ouvre depuis l'écran Fonds d'écran (case « Galerie », à côté de Nuit et Galets).
//
// Ce qu'on voit, de haut en bas :
//  - l'en-tête (flèche de retour + « Galerie ») ;
//  - une rangée de « pastilles » qu'on fait glisser : Tout, Nature, Montagnes, Japon… (une par dossier de la
//    branche « galerie » sur GitHub, voir GalerieFonds.kt) ; la pastille choisie est blanche ;
//  - la grille de vignettes (3 par ligne, au format d'un téléphone, comme l'écran Fonds d'écran).
// Les vignettes arrivent une à une (chaque image est téléchargée à part). Un appui sur une vignette range
// l'image sur le téléphone puis ouvre l'aperçu plein écran habituel (FondApercuActivity), avec « Appliquer ».
//
// Si la galerie est vide (Will n'a pas encore déposé d'images) : un message « bientôt ».
// Si Internet ne répond pas : un message et un bouton « Réessayer ».
class GalerieActivity : Activity() {

    private lateinit var pastilles: LinearLayout     // la rangée des catégories
    private lateinit var liste: LinearLayout         // la grille (ou le message)
    private val px = { v: Float -> Style.px(this, v) }

    private var fonds: List<GalerieFonds.Fond> = emptyList()
    private var categorie: String? = null            // null = « Tout »

    // 3 « ouvriers » pour télécharger les vignettes en même temps (pas plus : on ne veut pas saturer
    // la connexion ni GitHub). Ils s'arrêtent quand on quitte l'écran.
    private val ouvriers = Executors.newFixedThreadPool(3)
    private var ferme = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Style.fenetreNoire(this)
        pastilles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(px(16f), px(4f), px(16f), px(12f))
        }
        liste = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16f), 0, px(16f), px(28f))
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Style.NOIR)
            addView(Style.entete(this@GalerieActivity, getString(R.string.gal_titre)))
            addView(HorizontalScrollView(this@GalerieActivity).apply {
                isHorizontalScrollBarEnabled = false
                addView(pastilles)
            })
            addView(ScrollView(this@GalerieActivity).apply { addView(liste) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        charger(forcer = false)
    }

    override fun onDestroy() {
        ferme = true
        ouvriers.shutdownNow()
        super.onDestroy()
    }

    // Lit la liste des fonds (en arrière-plan), puis construit l'écran.
    private fun charger(forcer: Boolean) {
        pastilles.removeAllViews()
        message(getString(R.string.gal_chargement), avecRoue = true, bouton = false)
        Thread {
            val l = GalerieFonds.liste(this, forcer)
            runOnUiThread {
                if (ferme) return@runOnUiThread
                fonds = l.fonds
                when {
                    l.horsLigne -> message(getString(R.string.gal_hors_ligne), avecRoue = false, bouton = true)
                    fonds.isEmpty() -> message(getString(R.string.gal_vide), avecRoue = false, bouton = true)
                    else -> { construirePastilles(); construireGrille() }
                }
            }
        }.start()
    }

    // Un message au centre (chargement, galerie vide, pas d'Internet), avec une roue ou un bouton « Réessayer ».
    private fun message(t: String, avecRoue: Boolean, bouton: Boolean) {
        liste.removeAllViews()
        liste.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(px(12f), px(80f), px(12f), 0)
            if (avecRoue) addView(ProgressBar(this@GalerieActivity), LinearLayout.LayoutParams(px(36f), px(36f))
                .apply { bottomMargin = px(16f) })
            addView(Style.texte(this@GalerieActivity, t, 15f, Style.DOUX).apply {
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.25f)
            })
            if (bouton) addView(Style.boutonDiscret(this@GalerieActivity, getString(R.string.gal_reessayer)) { charger(forcer = true) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(44f)).apply { topMargin = px(20f) })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    // La rangée des pastilles : « Tout » puis une par catégorie. Un appui choisit la catégorie.
    private fun construirePastilles() {
        pastilles.removeAllViews()
        val choix = listOf<String?>(null) + GalerieFonds.categories(fonds)
        if (choix.size <= 2) return                   // une seule catégorie : pas besoin de pastilles
        for (cat in choix) {
            val choisie = cat == categorie
            val nom = if (cat == null) getString(R.string.gal_tout) else GalerieFonds.nomCategorie(this, cat)
            pastilles.addView(Style.texte(this, nom, 14f, if (choisie) Style.ENCRE else Style.TEXTE, gras = true).apply {
                gravity = Gravity.CENTER
                setPadding(px(16f), 0, px(16f), 0)
                // la pastille choisie est pleine (blanc cassé), les autres sont grises avec un contour
                background = if (choisie) Style.arrondi(this@GalerieActivity, Style.TEXTE, 18f)
                    else Style.arrondi(this@GalerieActivity, Style.CARTE, 18f, Style.BORD)
                isClickable = true
                setOnClickListener {
                    categorie = cat
                    construirePastilles()
                    construireGrille()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(36f)).apply { rightMargin = px(8f) })
        }
    }

    // La grille des fonds de la catégorie choisie (ou de toutes), en lignes de 3.
    private fun construireGrille() {
        liste.removeAllViews()
        val visibles = if (categorie == null) fonds else fonds.filter { it.categorie == categorie }
        val largeurEcran = resources.displayMetrics.widthPixels
        val ecart = px(10f)
        val cote = (largeurEcran - px(32f) - 2 * ecart) / 3
        for (debut in visibles.indices step 3) {
            val ligne = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in 0 until 3) {
                val lp = LinearLayout.LayoutParams(cote, cote * 2).apply { if (i < 2) rightMargin = ecart }
                val f = visibles.getOrNull(debut + i)
                ligne.addView(if (f != null) vignette(f) else View(this), lp)
            }
            liste.addView(ligne, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = ecart })
        }
    }

    // Une vignette : un fond gris avec une petite roue, remplacé par l'image dès qu'elle est arrivée.
    // Les vidéos et GIF ont un petit badge en haut à droite (comme dans « Mes fonds »).
    private fun vignette(f: GalerieFonds.Fond): View = FrameLayout(this).apply {
        background = Style.arrondi(this@GalerieActivity, Style.CARTE, 18f, Style.BORD)
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
        val roue = ProgressBar(this@GalerieActivity)
        addView(roue, FrameLayout.LayoutParams(px(24f), px(24f), Gravity.CENTER))
        val image = ImageView(this@GalerieActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
        addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val badge = when (f.extension) { "mp4" -> getString(R.string.fd_badge_video); "gif" -> "GIF"; else -> null }
        if (badge != null) addView(Style.texte(this@GalerieActivity, badge, 10f, Style.TEXTE, gras = true).apply {
            background = Style.arrondi(this@GalerieActivity, Color.argb(150, 0, 0, 0), 8f)
            setPadding(px(7f), px(3f), px(7f), px(3f))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
            .apply { topMargin = px(8f); rightMargin = px(8f) })
        isClickable = true
        setOnClickListener { choisir(f) }
        // le téléchargement de la vignette, par un des 3 ouvriers
        try {
            ouvriers.execute {
                val fichier = GalerieFonds.pourVignette(this@GalerieActivity, f)
                val b = fichier?.let { miniature(it, f.extension) }
                runOnUiThread {
                    if (ferme) return@runOnUiThread
                    roue.visibility = View.GONE
                    if (b != null) {
                        image.setImageBitmap(b)
                        image.animate().alpha(1f).setDuration(250).start()    // apparition douce
                    } else addView(TextView(this@GalerieActivity).apply {     // échec : un petit « ! » discret
                        text = "!"; setTextColor(Style.MUET); gravity = Gravity.CENTER
                    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            }
        } catch (_: Exception) {}    // (l'écran est en train de se fermer)
    }

    // Une petite image (au plus 360 pixels de côté) tirée du fichier : l'image elle-même, la 1re image d'un GIF,
    // ou la 1re image d'une vidéo.
    private fun miniature(fichier: File, extension: String): Bitmap? = try {
        if (extension == "mp4") {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(fichier.path)
                r.getFrameAtTime(0)?.let { reduire(it, 360) }
            } finally { r.release() }
        } else PeintreFond.chargerImage(fichier.path, 360)
    } catch (_: Exception) { null }

    private fun reduire(b: Bitmap, cote: Int): Bitmap {
        val k = max(b.width, b.height).toFloat() / cote
        return if (k <= 1f) b else Bitmap.createScaledBitmap(b, (b.width / k).toInt(), (b.height / k).toInt(), true)
    }

    // Appui sur une vignette : on range l'image sur le téléphone (déjà téléchargée en général : c'est immédiat),
    // puis on ouvre l'aperçu plein écran, exactement comme pour un fond importé.
    private fun choisir(f: GalerieFonds.Fond) {
        val attente = AlertDialog.Builder(this).setMessage(getString(R.string.gal_telechargement)).setCancelable(false).show()
        Thread {
            val fichier = GalerieFonds.pourFond(this, f)
            runOnUiThread {
                if (ferme) return@runOnUiThread
                attente.dismiss()
                if (fichier == null) Toast.makeText(this, getString(R.string.gal_rate), Toast.LENGTH_LONG).show()
                else startActivity(Intent(this, FondApercuActivity::class.java)
                    .putExtra(FondApercuActivity.SORTE, CarnetFond.sorteDe(fichier))
                    .putExtra(FondApercuActivity.ID, fichier.path))
            }
        }.start()
    }
}
