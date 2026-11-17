package com.will.animlauncher

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

// L'ÉCRAN « DOSSIER DE FONDS » (17/11, demande de Will) : on y range des photos et des vidéos qui seront
// le fond d'écran chacune à son tour (une nouvelle à chaque retour sur l'accueil). On l'ouvre depuis l'écran
// « Fonds d'écran », case « Dossier ».
//
// De haut en bas :
//  - une phrase d'explication ;
//  - le bouton « Ajouter des photos et des vidéos » (le sélecteur d'Android, plusieurs à la fois) ;
//  - l'interrupteur « Ordre au hasard » (éteint : dans l'ordre d'ajout) ;
//  - le son : le réglage général (sans son / écouteurs / toujours), puis « Tout activer » / « Tout couper » ;
//  - les miniatures, par 3 (appui long = retirer du dossier) ; celle affichée en ce moment a un badge ;
//    (17/11) sur chaque vidéo, un petit haut-parleur : un appui coupe ou remet le son de CETTE vidéo ;
//  - le bouton « Appliquer », qui met ce dossier en fond d'écran.
// La collection elle-même est gérée par DossierFond.kt, et le fond d'écran par FondDossierService.kt.
class DossierFondActivity : Activity() {

    private val CODE_AJOUT = 51
    private lateinit var liste: LinearLayout
    private val px = { v: Float -> Style.px(this, v) }
    // les miniatures sont préparées une par une, en arrière-plan (lire une vidéo prend un instant)
    private val atelier = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Style.fenetreNoire(this)
        liste = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16f), 0, px(16f), px(28f))
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Style.NOIR)
            addView(Style.entete(this@DossierFondActivity, getString(R.string.dfd_titre)))
            addView(ScrollView(this@DossierFondActivity).apply { addView(liste) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    }

    override fun onResume() {
        super.onResume()
        construire()
    }

    override fun onDestroy() {
        atelier.shutdownNow()
        super.onDestroy()
    }

    private fun construire() {
        liste.removeAllViews()
        val fichiers = DossierFond.fichiers(this)
        // 1) l'explication
        liste.addView(Style.texte(this, getString(R.string.dfd_expl), 14f, Style.DOUX).apply {
            setLineSpacing(0f, 1.25f)
            setPadding(px(4f), px(4f), px(4f), px(16f))
        })
        // 2) ajouter
        liste.addView(Style.boutonBlanc(this, getString(R.string.dfd_ajouter)) { choisir() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // 3) l'ordre
        liste.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(4f), px(14f), 0, px(6f))
            addView(LinearLayout(this@DossierFondActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(Style.texte(this@DossierFondActivity, getString(R.string.dfd_hasard), 15f, Style.TEXTE, gras = true))
                addView(Style.texte(this@DossierFondActivity, getString(R.string.dfd_hasard_expl), 12f, Style.MUET))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Style.interrupteur(this@DossierFondActivity).apply {
                isChecked = DossierFond.auHasard(this@DossierFondActivity)
                setOnCheckedChangeListener { _, oui -> DossierFond.mettreAuHasard(this@DossierFondActivity, oui) }
            })
        })
        // 3 bis) (17/11) le son des vidéos du dossier (le même réglage que dans « Fonds d'écran », voir SonFond.kt)
        liste.addView(SonFond.vue(this))
        // (17/11) le son vidéo par vidéo : tout activer / tout couper d'un coup, puis le haut-parleur de chaque vidéo
        if (fichiers.any { DossierFond.estVideo(it) }) {
            liste.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, px(6f), 0, 0)
                addView(Style.boutonDiscret(this@DossierFondActivity, getString(R.string.dfd_son_tout)) {
                    DossierFond.toutesMuettes(this@DossierFondActivity, false); construire()
                }, LinearLayout.LayoutParams(0, px(42f), 1f).apply { rightMargin = px(8f) })
                addView(Style.boutonDiscret(this@DossierFondActivity, getString(R.string.dfd_muet_tout)) {
                    DossierFond.toutesMuettes(this@DossierFondActivity, true); construire()
                }, LinearLayout.LayoutParams(0, px(42f), 1f))
            })
            liste.addView(Style.texte(this, getString(R.string.dfd_son_expl), 12f, Style.MUET).apply {
                setLineSpacing(0f, 1.2f)
                setPadding(px(4f), px(8f), px(4f), 0)
            })
        }
        // 4) les miniatures (ou un message si le dossier est vide)
        liste.addView(Style.titreCarte(this, getString(R.string.dfd_nombre, fichiers.size, DossierFond.MAX)).apply {
            setPadding(px(4f), px(16f), 0, px(10f))
        })
        if (fichiers.isEmpty()) {
            liste.addView(Style.texte(this, getString(R.string.dfd_vide), 13f, Style.MUET).apply {
                setPadding(px(4f), 0, px(4f), px(8f))
            })
        } else {
            val courant = DossierFond.courant(this)
            grille(fichiers.map { f -> miniature(f, f == courant) })
        }
        // 5) appliquer (il faut au moins une photo ou une vidéo)
        if (fichiers.isNotEmpty()) {
            liste.addView(Style.boutonBlanc(this, getString(R.string.fd_appliquer)) { appliquer() },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = px(10f) })
        }
    }

    // Range des cases en lignes de 3. Chaque case a la forme d'un téléphone (hauteur = 2 x largeur).
    private fun grille(cases: List<View>) {
        val largeurEcran = resources.displayMetrics.widthPixels
        val ecart = px(10f)
        val cote = (largeurEcran - px(32f) - 2 * ecart) / 3
        for (debut in cases.indices step 3) {
            val ligne = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in 0 until 3) {
                val lp = LinearLayout.LayoutParams(cote, cote * 2).apply { if (i < 2) rightMargin = ecart }
                ligne.addView(cases.getOrNull(debut + i) ?: View(this), lp)
            }
            liste.addView(ligne, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = ecart })
        }
    }

    // Une miniature : l'image (ou la 1re image de la vidéo), un badge « VIDÉO », et « EN CE MOMENT » pour le fond affiché.
    private fun miniature(f: File, enCeMoment: Boolean): View = FrameLayout(this).apply {
        background = Style.arrondi(this@DossierFondActivity, Style.CARTE, 18f,
            if (enCeMoment) Style.TEXTE else Style.BORD)
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
        val image = ImageView(this@DossierFondActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        chargerMiniature(f, image)
        if (DossierFond.estVideo(f)) addView(badge(getString(R.string.fd_badge_video)),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
                .apply { topMargin = px(8f); rightMargin = px(8f) })
        if (enCeMoment) addView(badge(getString(R.string.dfd_maintenant)),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START)
                .apply { bottomMargin = px(8f); leftMargin = px(8f) })
        if (DossierFond.estVideo(f)) addView(boutonSon(f),
            FrameLayout.LayoutParams(px(34f), px(34f), Gravity.TOP or Gravity.START).apply { topMargin = px(6f); leftMargin = px(6f) })
        isLongClickable = true
        setOnLongClickListener { proposerRetrait(f); true }
    }

    // (17/11) LE HAUT-PARLEUR d'une vidéo : 🔊 son activé, 🔇 son coupé. Un appui bascule.
    // Une vidéo sans son enregistré (importée avant le 17/11, ou vraiment muette) : 🔇 pâle, et un appui l'explique.
    private fun boutonSon(f: File): View = Style.texte(this, "", 15f, Style.TEXTE).apply {
        gravity = Gravity.CENTER
        background = Style.arrondi(this@DossierFondActivity, Color.argb(160, 0, 0, 0), 17f)
        val aDuSon = SonFond.avecSon(f).exists()
        fun peindre() {
            val coupee = !aDuSon || DossierFond.estMuette(this@DossierFondActivity, f)
            text = if (coupee) "🔇" else "🔊"
            alpha = if (aDuSon) 1f else 0.45f
        }
        peindre()
        isClickable = true
        setOnClickListener {
            if (!aDuSon) {
                Toast.makeText(this@DossierFondActivity, getString(R.string.son_fond_pas_de_son), Toast.LENGTH_LONG).show()
            } else {
                DossierFond.basculerMuette(this@DossierFondActivity, f)
                peindre()
            }
        }
    }

    private fun badge(t: String): View = Style.texte(this, t, 10f, Style.TEXTE, gras = true).apply {
        background = Style.arrondi(this@DossierFondActivity, Color.argb(160, 0, 0, 0), 8f)
        setPadding(px(7f), px(3f), px(7f), px(3f))
    }

    private fun chargerMiniature(f: File, vue: ImageView) {
        atelier.execute {
            val b: Bitmap? = try {
                if (DossierFond.estVideo(f)) {
                    val r = MediaMetadataRetriever()
                    try {
                        r.setDataSource(f.path)
                        r.getFrameAtTime(0)?.let { reduire(it, 360) }
                    } finally { try { r.release() } catch (_: Exception) {} }
                } else PeintreFond.chargerImage(f.path, 360)
            } catch (_: Exception) { null }
            if (b != null) runOnUiThread { vue.setImageBitmap(b) }
        }
    }

    private fun reduire(b: Bitmap, cote: Int): Bitmap {
        val k = max(b.width, b.height).toFloat() / cote
        return if (k <= 1f) b else Bitmap.createScaledBitmap(b, (b.width / k).toInt(), (b.height / k).toInt(), true)
    }

    private fun proposerRetrait(f: File) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dfd_retirer_q))
            .setPositiveButton(getString(R.string.dfd_retirer)) { _, _ -> DossierFond.retirer(this, f); construire() }
            .setNegativeButton(getString(R.string.fd_annuler), null)
            .show()
    }

    // ---------------------------------- AJOUTER ----------------------------------

    // Le sélecteur de photos d'Android, en choix MULTIPLE (photos et vidéos). Aucune permission à demander :
    // l'app n'a accès qu'aux fichiers que l'utilisateur choisit. Avant Android 13 : le sélecteur de fichiers.
    private fun choisir() {
        val place = DossierFond.MAX - DossierFond.fichiers(this).size
        if (place <= 0) { Toast.makeText(this, getString(R.string.dfd_plein), Toast.LENGTH_LONG).show(); return }
        val intent = if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES).apply {
            // le nombre maximum doit être au moins 2 (sinon le sélecteur ne permet d'en prendre qu'un)
            if (place >= 2) putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, min(place, MediaStore.getPickImagesMaxLimit()))
        } else Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, CODE_AJOUT)
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.fd_import_rate), Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CODE_AJOUT || resultCode != RESULT_OK || data == null) return
        // plusieurs fichiers choisis : ils sont dans « clipData » ; un seul : dans « data »
        val uris = ArrayList<Uri>()
        val clip = data.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) }
        } else {
            data.data?.let { uris.add(it) }
        }
        if (uris.isEmpty()) return
        // une petite fenêtre pendant la copie (quelques secondes par vidéo : le son est retiré)
        val attente = AlertDialog.Builder(this).setMessage(getString(R.string.dfd_en_cours)).setCancelable(false).show()
        Thread {
            val n = try { DossierFond.ajouter(this, uris) } catch (_: Exception) { 0 }
            runOnUiThread {
                if (!isDestroyed) {
                    attente.dismiss()
                    Toast.makeText(this, if (n > 0) resources.getQuantityString(R.plurals.dfd_ajoutes, n, n)
                        else getString(R.string.dfd_rien), Toast.LENGTH_SHORT).show()
                    construire()
                }
            }
        }.start()
    }

    // ---------------------------------- APPLIQUER ----------------------------------

    // Met le dossier en fond d'écran. Si c'est déjà lui : rien à faire, il suit tout seul les changements.
    // Sinon : l'écran de confirmation d'Android (« Écran d'accueil » / « Accueil et verrouillage »).
    private fun appliquer() {
        val voulu = ComponentName(this, FondDossierService::class.java)
        val actuel = try { WallpaperManager.getInstance(this).wallpaperInfo?.component } catch (_: Exception) { null }
        if (actuel == voulu) {
            Toast.makeText(this, getString(R.string.fd_applique), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, voulu))
            Toast.makeText(this, getString(R.string.fd_choisir_ecran), Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.fd_pas_possible), Toast.LENGTH_LONG).show()
            }
        }
    }
}
