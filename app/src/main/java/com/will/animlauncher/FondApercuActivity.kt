package com.will.animlauncher

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

// L'APERÇU PLEIN ÉCRAN D'UN FOND (11/11, étape 1), avant de l'appliquer.
//
// On voit le fond en grand, avec par-dessus une FAUSSE horloge et de fausses icônes (en gris) pour
// juger du rendu réel. En bas, une feuille de réglages :
//  - la pastille de consommation (très légère / légère / moyenne) ;
//  - Fluidité : Éco (30 images/s) ou Fluide (60) — pas pour les vidéos (elles gardent leur vitesse) ;
//  - « Figer après 10 s sans toucher l'accueil » ;
//  - Assombrir (0 à 60 %) — pas pour les vidéos ;
//  - le bouton « Appliquer ».
// Toucher l'aperçu masque / remontre la feuille, pour voir le fond seul.
//
// APPLIQUER : si notre service est déjà le fond d'écran du téléphone, le changement est INSTANTANÉ
// (le service lit le carnet et se met à jour). Sinon, Android affiche son propre écran de confirmation
// (obligatoire : aucune app ne peut changer le fond d'écran animé sans l'accord de l'utilisateur).
class FondApercuActivity : Activity() {

    companion object {
        const val SORTE = "sorte"
        const val ID = "id"
    }

    private lateinit var sorte: String
    private lateinit var ref: String         // le nom du dessin, ou le chemin du fichier importé
    private var fluide = false
    private var figer = false
    private var assombrir = 0

    private val px = { v: Float -> Style.px(this, v) }
    private var lecteur: MediaPlayer? = null
    private lateinit var voile: View
    private lateinit var feuille: LinearLayout
    private lateinit var pastille: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sorte = intent.getStringExtra(SORTE) ?: CarnetFond.APP
        ref = intent.getStringExtra(ID) ?: "nuit"
        // si ce fond est celui déjà choisi, on reprend ses réglages
        val actuel = CarnetFond.lire(this, CarnetFond.prefixePour(sorte))
        if (actuel.sorte == sorte && actuel.id == ref) {
            fluide = actuel.fluide; figer = actuel.figer; assombrir = actuel.assombrir
        }
        pleinEcran()

        val racine = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        racine.addView(contenu(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        voile = View(this).apply { setBackgroundColor(Color.BLACK); alpha = assombrir / 100f }
        racine.addView(voile, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        racine.addView(FauxAccueil(this), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        feuille = feuilleReglages()
        racine.addView(feuille, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
            .apply { leftMargin = px(12f); rightMargin = px(12f); bottomMargin = px(12f) })
        // toucher l'aperçu : masquer / remontrer la feuille (en fondu)
        racine.setOnClickListener {
            val montrer = feuille.alpha < 0.5f
            feuille.animate().alpha(if (montrer) 1f else 0f).setDuration(220).withEndAction {
                feuille.visibility = if (montrer) View.VISIBLE else View.INVISIBLE
            }.start()
            if (montrer) feuille.visibility = View.VISIBLE
        }
        // la feuille se pose au-dessus de la barre de navigation (le « bord à bord » d'Android 15+)
        racine.setOnApplyWindowInsetsListener { _, insets ->
            val bas = basDesBarres(insets)
            (feuille.layoutParams as FrameLayout.LayoutParams).bottomMargin = px(12f) + bas
            feuille.requestLayout()
            insets
        }
        setContentView(racine)
    }

    // AnimApp écarte automatiquement le contenu de chaque écran des barres du haut et du bas (Android 15+,
    // voir Style.sousLesBarres). Ici on veut le fond sur TOUT l'écran : on retire cet écart (onPostCreate
    // arrive juste après). La feuille de réglages, elle, se pose seule au-dessus de la barre du bas.
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        findViewById<View>(android.R.id.content)?.apply {
            setOnApplyWindowInsetsListener(null)
            setPadding(0, 0, 0, 0)
            requestApplyInsets()
        }
    }

    // La hauteur de la barre de navigation (en bas), pour poser la feuille juste au-dessus.
    @Suppress("DEPRECATION")
    private fun basDesBarres(insets: WindowInsets): Int =
        if (Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.systemBars()).bottom
        else insets.systemWindowInsetBottom

    // Le fond s'affiche sous la barre d'état et la barre de navigation (comme le vrai fond d'écran).
    // (Les anciennes façons de faire sont « dépréciées » mais restent nécessaires avant Android 11.)
    @Suppress("DEPRECATION")
    private fun pleinEcran() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        else window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    // Le fond lui-même, selon sa sorte.
    private fun contenu(): View = when (sorte) {
        CarnetFond.VIDEO -> videoApercu()
        CarnetFond.IMAGE -> ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            val m = resources.displayMetrics
            setImageBitmap(PeintreFond.chargerImage(ref, max(m.widthPixels, m.heightPixels)))
        }
        CarnetFond.GIF -> VueGif(this, ref)
        else -> VueDessinFond(this, CarnetFond.dessinPour(this, sorte, ref))
    }

    // La vidéo, jouée en boucle et muette, qui « couvre » l'écran sans être déformée.
    private fun videoApercu(): View = TextureView(this).apply {
        val vue = this
        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                lecteur = MediaPlayer().apply {
                    try {
                        setDataSource(ref)
                        setSurface(Surface(st))
                        isLooping = true
                        setVolume(0f, 0f)
                        setOnVideoSizeChangedListener { _, vw, vh -> couvrir(vue, vw, vh) }
                        setOnPreparedListener { it.start() }
                        prepareAsync()
                    } catch (_: Exception) {}
                }
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                lecteur?.release(); lecteur = null
                return true
            }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
    }

    // Agrandit la vidéo pour remplir l'écran (ce qui dépasse est coupé), centrée.
    private fun couvrir(vue: TextureView, vw: Int, vh: Int) {
        if (vw <= 0 || vh <= 0 || vue.width <= 0) return
        val w = vue.width.toFloat()
        val h = vue.height.toFloat()
        val s = max(w / vw, h / vh)
        vue.setTransform(Matrix().apply { setScale(vw * s / w, vh * s / h, w / 2f, h / 2f) })
    }

    override fun onPause() {
        super.onPause()
        try { lecteur?.pause() } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        try { lecteur?.start() } catch (_: Exception) {}
    }

    override fun onDestroy() {
        lecteur?.release(); lecteur = null
        super.onDestroy()
    }

    // ------------------------------ LA FEUILLE DE RÉGLAGES ------------------------------

    private fun feuilleReglages(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Style.arrondi(this@FondApercuActivity, Color.argb(235, 14, 14, 16), 26f, Style.BORD)
        setPadding(px(18f), px(16f), px(18f), px(14f))
        isClickable = true                       // un appui sur la feuille ne la masque pas

        // le nom du fond, et la pastille de consommation à droite
        val nom = when (sorte) {
            CarnetFond.VIDEO -> getString(R.string.fd_nom_video)
            CarnetFond.GIF -> getString(R.string.fd_nom_gif)
            CarnetFond.IMAGE -> getString(R.string.fd_nom_image)
            CarnetFond.PROFONDEUR -> getString(R.string.fd_nom_3d)
            else -> getString((CarnetFond.catalogue.firstOrNull { it.id == ref } ?: CarnetFond.catalogue[0]).nom)
        }
        pastille = Style.texte(this@FondApercuActivity, "", 12f, Style.DOUX)
        addView(LinearLayout(this@FondApercuActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Style.texte(this@FondApercuActivity, nom, 18f, Style.TEXTE, gras = true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(pastille)
        })
        majPastille()

        val video = sorte == CarnetFond.VIDEO
        val bouge = sorte == CarnetFond.APP || sorte == CarnetFond.GIF || sorte == CarnetFond.PROFONDEUR

        // Fluidité : deux pilules, une seule allumée
        if (bouge) {
            addView(Style.texte(this@FondApercuActivity, getString(R.string.fd_fluidite), 13f, Style.MUET).apply {
                setPadding(0, px(14f), 0, px(8f))
            })
            lateinit var eco: TextView
            lateinit var rapide: TextView
            fun peindre() {
                for ((v, on) in listOf(eco to !fluide, rapide to fluide)) {
                    v.background = Style.arrondi(this@FondApercuActivity, if (on) Style.TEXTE else Color.argb(20, 255, 255, 255), 14f,
                        if (on) null else Color.rgb(63, 63, 70))
                    v.setTextColor(if (on) Style.ENCRE else Style.TEXTE)
                }
                majPastille()
            }
            eco = pilule(getString(R.string.fd_eco)) { fluide = false; peindre() }
            rapide = pilule(getString(R.string.fd_fluide)) { fluide = true; peindre() }
            addView(LinearLayout(this@FondApercuActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(eco, LinearLayout.LayoutParams(0, px(40f), 1f).apply { rightMargin = px(8f) })
                addView(rapide, LinearLayout.LayoutParams(0, px(40f), 1f))
            })
            peindre()
        }

        // Figer après 10 s (pour tout ce qui bouge, vidéos comprises)
        if (bouge || video) {
            addView(LinearLayout(this@FondApercuActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(12f), 0, 0)
                addView(Style.texte(this@FondApercuActivity, getString(R.string.fd_figer), 14f, Style.TEXTE),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(Style.interrupteur(this@FondApercuActivity).apply {
                    isChecked = figer
                    setOnCheckedChangeListener { _, on -> figer = on; majPastille() }
                })
            })
        }

        // Assombrir (pas pour les vidéos : elles sont affichées directement par la puce vidéo)
        if (!video) {
            addView(Style.texte(this@FondApercuActivity, getString(R.string.fd_assombrir), 13f, Style.MUET).apply {
                setPadding(0, px(12f), 0, 0)
            })
            addView(SeekBar(this@FondApercuActivity).apply {
                max = 60
                progress = assombrir
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar, v: Int, parUtilisateur: Boolean) {
                        assombrir = v
                        voile.alpha = v / 100f
                    }
                    override fun onStartTrackingTouch(s: SeekBar) {}
                    override fun onStopTrackingTouch(s: SeekBar) {}
                })
            })
        }

        addView(Style.boutonBlanc(this@FondApercuActivity, getString(R.string.fd_appliquer)) { appliquer() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(14f) })
        addView(Style.texte(this@FondApercuActivity, getString(R.string.fd_toucher_masquer), 11f, Style.MUET).apply {
            gravity = Gravity.CENTER
            setPadding(0, px(8f), 0, 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun pilule(t: String, action: () -> Unit): TextView = Style.texte(this, t, 13f, Style.TEXTE, gras = true).apply {
        gravity = Gravity.CENTER
        isClickable = true
        setOnClickListener { action() }
    }

    // La pastille : une estimation simple (pas une mesure), pour guider l'utilisateur.
    //  image fixe = très légère ; dessin ou vidéo en Éco = légère ; GIF ou 60 images/s = moyenne.
    // Avec « figer », on descend d'un cran (le fond est immobile la plupart du temps).
    private fun majPastille() {
        var niveau = when (sorte) {
            CarnetFond.IMAGE -> 0
            CarnetFond.GIF -> 2
            CarnetFond.VIDEO -> 1
            else -> if (fluide) 2 else 1
        }
        if (sorte == CarnetFond.GIF && fluide) niveau = 2
        if (figer && niveau > 0) niveau -= 1
        val (texte, couleur) = when (niveau) {
            0 -> getString(R.string.fd_conso_tres_legere) to Color.rgb(110, 200, 140)
            1 -> getString(R.string.fd_conso_legere) to Color.rgb(110, 200, 140)
            else -> getString(R.string.fd_conso_moyenne) to Style.AMBRE
        }
        pastille.text = "● $texte"
        pastille.setTextColor(couleur)
    }

    // ------------------------------ APPLIQUER ------------------------------

    private fun appliquer() {
        CarnetFond.ecrire(this, CarnetFond.Choix(sorte, ref, fluide, figer, assombrir))
        val voulu = CarnetFond.service(this, sorte)
        val actuel = try { WallpaperManager.getInstance(this).wallpaperInfo?.component } catch (_: Exception) { null }
        if (actuel == voulu) {
            // notre service est déjà le fond d'écran : il vient de se mettre à jour tout seul
            Toast.makeText(this, getString(R.string.fd_applique), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        // sinon : l'écran de confirmation d'Android (« Écran d'accueil » / « Accueil et verrouillage »)
        try {
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, voulu))
            Toast.makeText(this, getString(R.string.fd_choisir_ecran), Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            try {
                // repli : la liste des fonds animés d'Android (l'utilisateur y choisit « AnimLauncher »)
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.fd_pas_possible), Toast.LENGTH_LONG).show()
            }
        }
    }
}

// Un GIF en boucle, qui « couvre » sa vue (pour l'aperçu).
class VueGif(context: android.content.Context, chemin: String) : View(context) {
    private val gif = if (File(chemin).exists()) LecteurGif(chemin) else null
    private val debut = SystemClock.uptimeMillis()

    override fun onDraw(c: Canvas) {
        val t = (SystemClock.uptimeMillis() - debut) / 1000f
        val b = gif?.imageA(t) ?: return
        PeintreFond.couvrir(c, b, width.toFloat(), height.toFloat())
        if (gif?.anime == true) postInvalidateOnAnimation()
    }
}

// La fausse page d'accueil posée par-dessus l'aperçu : l'heure et la date en haut, et deux rangées de
// fausses icônes (des carrés arrondis gris translucides) en bas. Elle ne capte pas les appuis.
class FauxAccueil(context: android.content.Context) : View(context) {
    private val dp = context.resources.displayMetrics.density
    private val pHeure = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        setShadowLayer(6f * dp, 0f, 1f * dp, Color.argb(90, 0, 0, 0))
    }
    private val pDate = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f * dp, 0f, 1f * dp, Color.argb(90, 0, 0, 0))
    }
    private val pIcone = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(46, 255, 255, 255) }
    private val carre = RectF()

    init { isClickable = false }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val maintenant = Date()
        pHeure.textSize = 64f * dp
        c.drawText(DateFormat.getTimeInstance(DateFormat.SHORT).format(maintenant), w / 2f, h * 0.16f, pHeure)
        pDate.textSize = 15f * dp
        c.drawText(DateFormat.getDateInstance(DateFormat.FULL).format(maintenant), w / 2f, h * 0.16f + 28f * dp, pDate)
        // deux rangées de 4 icônes, à 66 % et 78 % de la hauteur
        val cote = 54f * dp
        val ecart = (w - 4 * cote) / 5f
        for (y in floatArrayOf(h * 0.66f, h * 0.78f)) {
            for (i in 0 until 4) {
                val x = ecart + i * (cote + ecart)
                carre.set(x, y, x + cote, y + cote)
                c.drawRoundRect(carre, 16f * dp, 16f * dp, pIcone)
            }
        }
    }
}
