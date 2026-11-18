package com.will.animlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import kotlin.math.max

// L'ÉCRAN « FONDS D'ÉCRAN » (carte 3 de l'accueil) — 11/11, étape 1.
//
// Deux parties, en grilles de 3 vignettes (au format d'un téléphone) :
//  - « Proposés par l'app » : nos fonds dessinés (Nuit, Galets), qui bougent déjà dans leur vignette ;
//  - « Mes fonds » : la case « + Importer », la case « Fond 3D » (13/11 : une photo dont le sujet et le fond
//    bougent séparément quand on penche le téléphone, voir FondProfondeur.kt), puis les fonds de l'utilisateur
//    (vidéo, GIF ou image de son téléphone). Appui long sur un fond importé = le supprimer.
// Un appui sur une vignette ouvre l'aperçu plein écran (FondApercuActivity), avec le bouton « Appliquer ».
//
// L'IMPORT : le fichier choisi est COPIÉ dans le dossier privé de l'app (il reste sur le téléphone).
// (17/11) Le travail de copie est dans ImportFond.kt, partagé avec le « dossier de fonds » (DossierFondActivity) :
//  - vidéo : on garde seulement la piste vidéo (le son est retiré, sans réencoder : c'est rapide) ;
//  - image : réduite à la taille de l'écran (une photo énorme gaspillerait de la mémoire) ;
//  - GIF : copié tel quel (étape 2 : le convertir en vidéo, encore plus économe).
class FondsActivity : Activity() {

    private val CODE_IMPORT = 41
    private val CODE_3D = 42                 // (13/11) la photo choisie pour un fond 3D
    private lateinit var liste: LinearLayout
    private val px = { v: Float -> Style.px(this, v) }

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
            addView(Style.entete(this@FondsActivity, getString(R.string.carte_fonds)))
            addView(ScrollView(this@FondsActivity).apply { addView(liste) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    }

    // On reconstruit la liste à chaque retour sur l'écran (après un import ou une suppression).
    override fun onResume() {
        super.onResume()
        construire()
    }

    private fun construire() {
        liste.removeAllViews()
        // 1) les fonds de l'app
        liste.addView(Style.titreCarte(this, getString(R.string.fd_de_lapp)).apply { setPadding(px(4f), px(8f), 0, px(10f)) })
        grille(CarnetFond.catalogue.map { f ->
            vignette(VueDessinFond(this, f.fabriquer(CarnetFond.palette(this))), getString(f.nom), null) {
                ouvrirApercu(CarnetFond.APP, f.id)
            }
        })
        // 2) mes fonds : « + Importer » puis les fichiers importés
        liste.addView(Style.titreCarte(this, getString(R.string.fd_mes_fonds)).apply { setPadding(px(4f), px(22f), 0, px(10f)) })
        val cases = mutableListOf<View>(caseImporter(), caseFond3D(), caseDossier())
        // (13/11) les fonds 3D : leur vignette (apercu.jpg), un badge « 3D »
        for (d in FabriqueFond3D.liste(this)) {
            val miniature = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            chargerMiniature(File(d, "apercu.jpg"), CarnetFond.IMAGE, miniature)
            cases.add(vignette(miniature, getString(R.string.fd_nom_3d), "3D") { ouvrirApercu(CarnetFond.PROFONDEUR, d.path) }.apply {
                setOnLongClickListener { proposerSuppression(d); true }
            })
        }
        for (f in CarnetFond.importes(this)) {
            val sorte = CarnetFond.sorteDe(f)
            val miniature = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            chargerMiniature(f, sorte, miniature)
            val badge = when (sorte) { CarnetFond.VIDEO -> getString(R.string.fd_badge_video); CarnetFond.GIF -> "GIF"; else -> null }
            cases.add(vignette(miniature, nomDe(sorte), badge) { ouvrirApercu(sorte, f.path) }.apply {
                setOnLongClickListener { proposerSuppression(f); true }
            })
        }
        grille(cases)
        // 2 bis) (17/11) le son des fonds vidéo : sans son / avec écouteurs / toujours (voir SonFond.kt)
        liste.addView(SonFond.vue(this))
        // 3) l'astuce batterie
        liste.addView(Style.texte(this, getString(R.string.fd_astuce), 13f, Style.MUET).apply {
            setLineSpacing(0f, 1.2f)
            setPadding(px(4f), px(18f), px(4f), 0)
        })
        // 3 bis) (13/11) l'outil de détourage des dessins, s'il a été téléchargé : sa place, et un bouton pour le supprimer
        if (DetourageDessins.estInstalle(this)) {
            liste.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(px(4f), px(16f), 0, 0)
                addView(Style.texte(this@FondsActivity, getString(R.string.fd_3d_outil_place, DetourageDessins.placeMo(this@FondsActivity)),
                    13f, Style.DOUX), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(Style.boutonDiscret(this@FondsActivity, getString(R.string.fd_supprimer)) {
                    DetourageDessins.supprimer(this@FondsActivity)
                    Toast.makeText(this@FondsActivity, getString(R.string.fd_3d_outil_supprime), Toast.LENGTH_SHORT).show()
                    construire()
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(42f)))
            })
        }
        // 4) le diagnostic (12/11) : ce que les fonds d'écran ont vécu la dernière fois qu'ils étaient affichés
        liste.addView(Style.texte(this, diagnostic(), 11f, Color.rgb(110, 110, 118)).apply {
            setLineSpacing(0f, 1.25f)
            setPadding(px(4f), px(14f), px(4f), 0)
            setTextIsSelectable(true)                // on peut le copier pour l'envoyer
        })
    }

    // Le texte du diagnostic, lu dans le carnet « fond_diag » rempli par les services (FondService.kt).
    //  visible : ✓ / ✗ + depuis combien de temps ; « ? » = Android n'a jamais envoyé le signal (mode secours)
    //  dessin : nombre d'images dessinées par le dernier moteur ; vidéo : ▶ lecture, ⏸ pause, … chargement
    private fun diagnostic(): String {
        val d = DiagFond.carnet(this)
        val maintenant = System.currentTimeMillis()
        fun depuis(cle: String): String {
            val ms = d.getLong(cle, 0L)
            if (ms == 0L) return "—"
            val s = (maintenant - ms) / 1000
            return if (s < 120) "$s s" else "${s / 60} min"
        }
        fun visible(p: String): String {
            if (!d.contains(p + "_vis")) return "—"
            val v = if (d.getBoolean(p + "_vis", false)) "✓" else "✗"
            val signal = if (d.getBoolean(p + "_signal", false)) "" else "?"
            return "$v$signal (${depuis(p + "_vis_ms")})"
        }
        fun oui(cle: String) = if (d.getBoolean(cle, false)) "✓" else "✗"
        val etat = when (val e = d.getString("v_etat", null)) {
            null -> "—"
            "lecture" -> "▶"
            "pause" -> "⏸"
            "chargement" -> "…"
            else -> "⚠ $e"
        }
        val erreur = d.getString("d_erreur", null)?.let { "\n⚠ $it" } ?: ""
        return getString(R.string.fd_diag_titre) + "\n" +
            getString(R.string.fd_diag_dessin, visible("d"), d.getInt("d_images", 0), depuis("d_img_ms"), oui("d_eco"), oui("d_chaud")) + erreur + "\n" +
            getString(R.string.fd_diag_video, visible("v"), etat, depuis("v_etat_ms"), oui("v_eco"), oui("v_chaud"))
    }

    private fun nomDe(sorte: String) = getString(when (sorte) {
        CarnetFond.VIDEO -> R.string.fd_nom_video
        CarnetFond.GIF -> R.string.fd_nom_gif
        else -> R.string.fd_nom_image
    })

    // Range des cases en lignes de 3. Chaque case a la forme d'un téléphone (hauteur = 2 x largeur).
    private fun grille(cases: List<View>) {
        val largeurEcran = resources.displayMetrics.widthPixels
        val ecart = px(10f)
        val cote = (largeurEcran - px(32f) - 2 * ecart) / 3
        for (debut in cases.indices step 3) {
            val ligne = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in 0 until 3) {
                val lp = LinearLayout.LayoutParams(cote, cote * 2).apply { if (i < 2) rightMargin = ecart }
                val v = cases.getOrNull(debut + i) ?: View(this)      // case vide pour garder l'alignement
                ligne.addView(v, lp)
            }
            liste.addView(ligne, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = ecart })
        }
    }

    // Une vignette : le contenu, coupé en rectangle arrondi, avec le nom en bas (et un badge éventuel).
    private fun vignette(contenu: View, nom: String, badge: String?, action: () -> Unit): FrameLayout =
        FrameLayout(this).apply {
            background = Style.arrondi(this@FondsActivity, Style.CARTE, 18f, Style.BORD)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            addView(contenu, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            // un dégradé noir en bas, pour que le nom reste lisible sur n'importe quelle image
            addView(View(this@FondsActivity).apply {
                background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                    intArrayOf(Color.argb(200, 0, 0, 0), Color.TRANSPARENT))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(56f), Gravity.BOTTOM))
            addView(Style.texte(this@FondsActivity, nom, 13f, Style.TEXTE, gras = true).apply {
                setPadding(px(10f), 0, px(10f), px(9f))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
            if (badge != null) addView(Style.texte(this@FondsActivity, badge, 10f, Style.TEXTE, gras = true).apply {
                background = Style.arrondi(this@FondsActivity, Color.argb(150, 0, 0, 0), 8f)
                setPadding(px(7f), px(3f), px(7f), px(3f))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
                .apply { topMargin = px(8f); rightMargin = px(8f) })
            isClickable = true
            setOnClickListener { action() }
        }

    // La case « + Importer » : un contour en pointillés, un grand « + » et une ligne d'explication.
    private fun caseImporter(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(Color.argb(10, 255, 255, 255))
            cornerRadius = px(18f).toFloat()
            setStroke(px(1.5f), Color.rgb(70, 70, 78), px(6f).toFloat(), px(5f).toFloat())   // pointillés
        }
        setPadding(px(8f), 0, px(8f), 0)
        addView(Style.texte(this@FondsActivity, "+", 34f, Style.TEXTE).apply { gravity = Gravity.CENTER })
        addView(Style.texte(this@FondsActivity, getString(R.string.fd_importer), 14f, Style.TEXTE, gras = true).apply { gravity = Gravity.CENTER })
        addView(Style.texte(this@FondsActivity, getString(R.string.fd_importer_expl), 11f, Style.MUET).apply {
            gravity = Gravity.CENTER
            setPadding(0, px(6f), 0, 0)
        })
        isClickable = true
        setOnClickListener { choisirFichier() }
    }

    // (13/11) La case « Fond 3D » : même forme que « Importer », avec un petit symbole de calques.
    private fun caseFond3D(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(Color.argb(10, 255, 255, 255))
            cornerRadius = px(18f).toFloat()
            setStroke(px(1.5f), Color.rgb(70, 70, 78), px(6f).toFloat(), px(5f).toFloat())
        }
        setPadding(px(8f), 0, px(8f), 0)
        addView(Style.texte(this@FondsActivity, "◩", 30f, Style.TEXTE).apply { gravity = Gravity.CENTER })
        addView(Style.texte(this@FondsActivity, getString(R.string.fd_creer_3d), 14f, Style.TEXTE, gras = true).apply { gravity = Gravity.CENTER })
        addView(Style.texte(this@FondsActivity, getString(R.string.fd_creer_3d_expl), 11f, Style.MUET).apply {
            gravity = Gravity.CENTER
            setPadding(0, px(6f), 0, 0)
        })
        isClickable = true
        setOnClickListener { choisirPhoto3D() }
    }

    // (17/11) La case « Dossier » : des photos et des vidéos qui se relaient en fond d'écran (une nouvelle à chaque
    // retour sur l'accueil). Elle ouvre l'écran du dossier (DossierFondActivity). Sous le titre : combien il y en a.
    private fun caseDossier(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(Color.argb(10, 255, 255, 255))
            cornerRadius = px(18f).toFloat()
            setStroke(px(1.5f), Color.rgb(70, 70, 78), px(6f).toFloat(), px(5f).toFloat())
        }
        setPadding(px(8f), 0, px(8f), 0)
        addView(Style.texte(this@FondsActivity, "⧉", 30f, Style.TEXTE).apply { gravity = Gravity.CENTER })
        addView(Style.texte(this@FondsActivity, getString(R.string.dfd_case), 14f, Style.TEXTE, gras = true).apply { gravity = Gravity.CENTER })
        val n = DossierFond.fichiers(this@FondsActivity).size
        addView(Style.texte(this@FondsActivity,
            if (n == 0) getString(R.string.dfd_case_expl) else getString(R.string.dfd_nombre, n, DossierFond.MAX),
            11f, Style.MUET).apply {
            gravity = Gravity.CENTER
            setPadding(0, px(6f), 0, 0)
        })
        isClickable = true
        setOnClickListener { startActivity(Intent(this@FondsActivity, DossierFondActivity::class.java)) }
    }

    // Le sélecteur de photos, pour un fond 3D (photos seulement).
    private fun choisirPhoto3D() {
        val intent = if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES).apply { type = "image/*" }
        else Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, CODE_3D)
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.fd_import_rate), Toast.LENGTH_LONG).show()
        }
    }

    // La photo est choisie : on détoure le sujet (1 à 5 s), puis on ouvre la GOMME pour corriger (GommeActivity).
    // Si l'image demande l'outil des dessins et qu'il n'est pas encore là : la fenêtre des 44 Mo, puis on recommence.
    private fun fabriquer3D(uri: Uri) {
        val attente = AlertDialog.Builder(this).setMessage(getString(R.string.fd_3d_en_cours)).setCancelable(false).show()
        Thread {
            var d: FabriqueFond3D.Detourage? = null
            var raison: FabriqueFond3D.Raison? = null
            try { d = FabriqueFond3D.detourer(this, uri) } catch (e: FabriqueFond3D.Echec) { raison = e.raison } catch (_: Throwable) {}
            runOnUiThread {
                attente.dismiss()
                val r = d
                when {
                    r != null -> {
                        GommeActivity.enCours = r
                        GommeActivity.photo = uri
                        GommeActivity.dossier = null          // un NOUVEAU fond (pas une retouche)
                        startActivity(Intent(this, GommeActivity::class.java))
                    }
                    raison == FabriqueFond3D.Raison.OUTIL_DESSINS_ABSENT -> OutilDessinsUi.proposer(this) { fabriquer3D(uri) }
                    else -> Toast.makeText(this, getString(OutilDessinsUi.message(raison)), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // La miniature d'un fond importé, préparée en arrière-plan (lire une vidéo prend un instant).
    private fun chargerMiniature(f: File, sorte: String, vue: ImageView) {
        Thread {
            val b: Bitmap? = try {
                if (sorte == CarnetFond.VIDEO) {
                    val r = MediaMetadataRetriever()
                    try {
                        r.setDataSource(f.path)
                        r.getFrameAtTime(0)?.let { reduire(it, 360) }
                    } finally { r.release() }
                } else PeintreFond.chargerImage(f.path, 360)       // pour un GIF : sa 1re image
            } catch (_: Exception) { null }
            if (b != null) runOnUiThread { vue.setImageBitmap(b) }
        }.start()
    }

    private fun reduire(b: Bitmap, cote: Int): Bitmap {
        val k = max(b.width, b.height).toFloat() / cote
        return if (k <= 1f) b else Bitmap.createScaledBitmap(b, (b.width / k).toInt(), (b.height / k).toInt(), true)
    }

    private fun proposerSuppression(f: File) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.fd_supprimer_q))
            .setPositiveButton(getString(R.string.fd_supprimer)) { _, _ -> ImportFond.supprimer(f); construire() }
            .setNegativeButton(getString(R.string.fd_annuler), null)
            .show()
    }

    private fun ouvrirApercu(sorte: String, id: String) {
        startActivity(Intent(this, FondApercuActivity::class.java)
            .putExtra(FondApercuActivity.SORTE, sorte)
            .putExtra(FondApercuActivity.ID, id))
    }

    // ---------------------------------- L'IMPORT ----------------------------------

    // Ouvre le sélecteur de photos d'Android (sans demander de permission : l'utilisateur choisit
    // lui-même UN fichier, et l'app n'a accès qu'à celui-là). Avant Android 13 : le sélecteur de fichiers.
    private fun choisirFichier() {
        val intent = if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES)   // photos ET vidéos
        else Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, CODE_IMPORT)
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.fd_import_rate), Toast.LENGTH_LONG).show()
        }
    }

    // (startActivityForResult est ancien mais suffit ici : l'app n'utilise pas la bibliothèque AndroidX)
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == CODE_3D && resultCode == RESULT_OK && uri != null) { fabriquer3D(uri); return }
        if (requestCode != CODE_IMPORT || resultCode != RESULT_OK || uri == null) return
        // une petite fenêtre « Préparation du fond… » pendant la copie (quelques secondes pour une vidéo)
        val attente = AlertDialog.Builder(this).setMessage(getString(R.string.fd_import_en_cours)).setCancelable(false).show()
        Thread {
            val f = try { ImportFond.importer(this, uri, CarnetFond.dossier(this)) } catch (_: Exception) { null }
            runOnUiThread {
                attente.dismiss()
                if (f == null) Toast.makeText(this, getString(R.string.fd_import_rate), Toast.LENGTH_LONG).show()
                else ouvrirApercu(CarnetFond.sorteDe(f), f.path)
            }
        }.start()
    }
}
