package com.will.animlauncher

import android.app.Activity
import android.app.AlertDialog
import android.app.StatusBarManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

// L'ÉCRAN DES RÉGLAGES (la roue en haut à droite de l'accueil, ou « Réglages » dans le menu).
// C'est l'ancien écran principal, déplacé tel quel lors de la refonte du 09/11 : interrupteurs,
// état de la détection, écran d'explication (Store), journal, outils de développement (Perso).
// Seul le fond change : noir, comme le reste de la refonte.
class ReglagesActivity : Activity() {

    private lateinit var statut: TextView
    private lateinit var interrupteur: Switch
    private lateinit var interrupteurDemarrage: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Style.fenetreNoire(this)

        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(56, 100, 56, 56)
        }

        // 0. Le logo animé (voir LogoAnime.kt), centré en haut.
        val tailleLogo = (110 * resources.displayMetrics.density).toInt()
        colonne.addView(LogoAnime(this), LinearLayout.LayoutParams(tailleLogo, tailleLogo).apply {
            bottomMargin = (16 * resources.displayMetrics.density).toInt()
        })

        // 1. L'interrupteur général (kill switch).
        interrupteur = Switch(this).apply {
            text = getString(R.string.anim_activees)
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 32)
            setOnCheckedChangeListener { _, coche -> Reglages.definir(this@ReglagesActivity, coche) }
        }

        // 2. Le mode : à chaque ouverture, ou seulement quand l'app démarre.
        interrupteurDemarrage = Switch(this).apply {
            text = getString(R.string.seulement_demarrage_app)
            textSize = 18f
            setTextColor(Color.parseColor("#DFE4F7"))
            setPadding(0, 0, 0, 40)
            setOnCheckedChangeListener { _, coche ->
                Reglages.definirSeulementDemarrage(this@ReglagesActivity, coche)
            }
        }

        // Le grand bouton vers la liste des apps : couper, accélérer ou ralentir chaque animation.
        val boutonApps = Button(this).apply {
            text = getString(R.string.carte_apps)
            textSize = 18f
            setOnClickListener { startActivity(Intent(this@ReglagesActivity, AppsActivity::class.java)) }
        }

        // Les dossiers animés (voir GroupesActivity.kt) : les apps rangées par catégorie, en widgets.
        val boutonDossiers = Button(this).apply {
            text = getString(R.string.dossiers_animes)
            textSize = 18f
            setOnClickListener { startActivity(Intent(this@ReglagesActivity, GroupesActivity::class.java)) }
        }

        val boutonVolet = Button(this).apply {
            text = getString(R.string.bouton_volet)
            setOnClickListener { proposerTuile() }
        }

        // Le widget « logo animé » (voir LogoWidget.kt) : Samsung affiche sa fenêtre pour le poser
        // sur l'accueil. Ensuite, on peut retirer l'icône normale et garder le widget à sa place.
        val boutonWidget = Button(this).apply {
            text = getString(R.string.bouton_logo)
            setOnClickListener {
                if (!LogoWidget.proposer(this@ReglagesActivity)) {
                    statut.text = getString(R.string.aide_widget, getString(R.string.app_name))
                }
            }
        }

        statut = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#DFE4F7"))
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 24)
        }

        val boutonAccessibilite = Button(this).apply {
            text = getString(if (BuildConfig.STORE) R.string.activer_detection else R.string.ouvrir_accessibilite)
            // Version Store : Google exige un écran d'explication AVANT d'envoyer vers les réglages.
            setOnClickListener {
                if (BuildConfig.STORE) expliquerAccessibilite()
                else ouvrirAccessibilite()
            }
        }

        val boutonJournal = Button(this).apply {
            text = getString(R.string.menu_journal)
            setOnClickListener {
                startActivity(Intent(this@ReglagesActivity, JournalActivity::class.java))
            }
        }

        // 3. Copie la liste de toutes les apps (nom + nom technique), à coller dans Claude.
        val boutonListe = Button(this).apply {
            text = "Copier la liste de mes apps"
            setOnClickListener { copierListeApps() }
        }

        // 4. LE MODE STANDARD (prototype sans accessibilité, voir ModeStandard.kt).
        // Un seul bouton qui guide : il demande la permission qui manque, puis allume/éteint le mode.
        statutStandard = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#DFE4F7"))
            gravity = Gravity.CENTER
            setPadding(0, 56, 0, 16)
        }
        boutonStandard = Button(this).apply {
            setOnClickListener { etapeSuivanteStandard() }
        }

        val version = TextView(this).apply {
            text = getString(R.string.version, packageManager.getPackageInfo(packageName, 0).versionName)
            textSize = 14f
            setTextColor(Color.parseColor("#94A0C4"))
            gravity = Gravity.CENTER
            setPadding(0, 64, 0, 0)
        }

        colonne.addView(interrupteur)
        colonne.addView(interrupteurDemarrage)
        colonne.addView(boutonApps)
        colonne.addView(boutonDossiers)
        colonne.addView(boutonVolet)
        colonne.addView(boutonWidget)
        colonne.addView(statut)
        colonne.addView(boutonAccessibilite)
        // Le journal : dans les deux versions. En Store, il reste sur le téléphone (pas d'Internet) :
        // on le copie et on le colle dans Claude pour comprendre un souci.
        colonne.addView(boutonJournal)
        // Les autres outils de développement (liste des apps, mode standard) : version Perso seulement.
        if (!BuildConfig.STORE) {
            colonne.addView(boutonListe)
            colonne.addView(statutStandard)
            colonne.addView(boutonStandard)
        }
        colonne.addView(version)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Style.NOIR)
            addView(colonne)
        })
    }

    override fun onResume() {
        super.onResume()
        interrupteur.isChecked = Reglages.actif(this)
        interrupteurDemarrage.isChecked = Reglages.seulementDemarrage(this)
        val nomApp = getString(R.string.app_name)
        statut.text = if (serviceActif()) {
            getString(R.string.detection_ok)
        } else if (peutEtreRestreinte()) {
            getString(R.string.detection_ko, nomApp) + "\n" + getString(R.string.detection_grise)
        } else {
            getString(R.string.detection_ko, nomApp)
        }
        if (!BuildConfig.STORE) afficherEtatStandard()
    }

    // ---------------- L'ÉCRAN D'EXPLICATION (version Store) ----------------
    // Règle de Google Play pour les apps qui utilisent l'accessibilité sans être des outils pour
    // personnes handicapées : avant d'envoyer vers les réglages, l'app doit expliquer clairement
    // CE QU'ELLE FAIT avec, POURQUOI, et laisser ACCEPTER ou REFUSER. Ce texte doit rester séparé
    // de toute autre information sur les données. Refuser ne bloque rien : on peut revenir plus tard.
    private fun expliquerAccessibilite() {
        // Le texte est dans les fichiers de textes (clé « expl_texte »), traduit dans chaque langue :
        // Google demande que l'explication soit claire pour l'utilisateur, donc dans SA langue.
        val nom = getString(R.string.app_name)
        val texte = getString(R.string.expl_texte, nom)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.expl_titre, nom))
            .setMessage(texte)
            .setCancelable(false)                       // il faut choisir : accepter ou refuser
            .setPositiveButton(getString(R.string.expl_accepter)) { _, _ ->
                ouvrirAccessibilite()
            }
            .setNegativeButton(getString(R.string.expl_refuser)) { _, _ ->
                statut.text = getString(R.string.expl_refus, nom)
            }
            .show()
    }

    // ---------------- L'ACCESSIBILITÉ GRISÉE (« paramètres restreints ») ----------------
    // Une app installée HORS du Play Store (un APK) apparaît grisée dans l'accessibilité depuis
    // Android 13. Dans ce cas, on ouvre le guide plein écran (voir GuideDeblocage.kt), qui montre
    // les 3 missions avec des dessins. Sinon, on ouvre directement l'accessibilité.
    private fun peutEtreRestreinte(): Boolean = GuideDeblocage.necessaire(this)

    // Le chemin à suivre dans l'accessibilité. Chez Samsung, les apps comme AnimLauncher sont
    // rangées dans « Applications installées ».
    // (« get() » : calculé au moment où on s'en sert, quand le nom de l'app est disponible.)
    private val cheminAccessibilite: String get() {
        val nom = getString(R.string.app_name)
        return if (Build.MANUFACTURER.equals("samsung", ignoreCase = true))
            getString(R.string.chemin_samsung, nom)
        else getString(R.string.chemin_autre, nom)
    }

    private fun ouvrirAccessibilite() {
        if (serviceActif() || !peutEtreRestreinte()) {
            GuideDeblocage.ouvrirAccessibilite(this)
            // le rappel s'affiche par-dessus les Paramètres
            Toast.makeText(this, getString(R.string.dans_accessibilite, cheminAccessibilite), Toast.LENGTH_LONG).show()
        } else {
            startActivity(Intent(this, GuideDeblocage::class.java))
        }
    }

    // ---------------- MODE STANDARD ----------------
    private lateinit var statutStandard: TextView
    private lateinit var boutonStandard: Button

    // Affiche où on en est, et ce que fera le bouton.
    private fun afficherEtatStandard() {
        val enMarche = ModeStandard.enMarche
        statutStandard.text = "🧪 Mode standard (test sans accessibilité)\n" + when {
            enMarche && serviceActif() -> "En marche, mais en veille : coupe l'accessibilité pour le tester."
            enMarche -> "✅ En marche. Ouvre une app depuis l'accueil, puis regarde le journal (« décalage »)."
            else -> "Arrêté."
        }
        boutonStandard.text = when {
            enMarche -> "Arrêter le mode standard"
            !ModeStandard.accesUtilisation(this) -> "1/2 · Autoriser l'accès aux données d'utilisation"
            !Settings.canDrawOverlays(this) -> "2/2 · Autoriser l'affichage par-dessus les apps"
            else -> "Démarrer le mode standard"
        }
    }

    // Fait l'étape suivante : ouvrir la bonne page des réglages, ou démarrer/arrêter le service.
    private fun etapeSuivanteStandard() {
        when {
            ModeStandard.enMarche -> stopService(Intent(this, ModeStandard::class.java))
            !ModeStandard.accesUtilisation(this) ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            !Settings.canDrawOverlays(this) ->
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")))
            else -> {
                // Android 13+ : on demande le droit d'afficher la notification permanente.
                if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
                startForegroundService(Intent(this, ModeStandard::class.java))
            }
        }
        // le service met un instant à démarrer/s'arrêter : on rafraîchit l'affichage juste après
        statutStandard.postDelayed({ afficherEtatStandard() }, 400)
    }

    // Demande à Android toutes les apps qui ont une icône, et copie
    // "Nom affiché = nom.technique" pour chacune, triées par ordre alphabétique.
    private fun copierListeApps() {
        val intention = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intention, 0)
            .map { "${it.loadLabel(packageManager)} = ${it.activityInfo.packageName}" }
            .distinct()
            .sortedBy { it.lowercase() }
        val texte = "Mes apps (${apps.size})\n\n" + apps.joinToString("\n")
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Mes apps", texte))
        Toast.makeText(this, "${apps.size} apps copiées", Toast.LENGTH_SHORT).show()
    }

    private fun proposerTuile() {
        if (Build.VERSION.SDK_INT >= 33) {
            val barre = getSystemService(StatusBarManager::class.java)
            barre.requestAddTileService(
                ComponentName(this, InterrupteurTile::class.java),
                "AnimLauncher",
                Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                mainExecutor
            ) { }
        } else {
            statut.text = getString(R.string.volet_aide, getString(R.string.app_name))
        }
    }

    private fun serviceActif(): Boolean {
        val actifs = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return actifs.contains("$packageName/")
    }
}
