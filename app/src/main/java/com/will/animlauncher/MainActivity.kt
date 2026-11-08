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

// L'écran principal : interrupteurs, état de la détection, accès au journal.
class MainActivity : Activity() {

    private lateinit var statut: TextView
    private lateinit var interrupteur: Switch
    private lateinit var interrupteurDemarrage: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(56, 140, 56, 56)
        }

        // 1. L'interrupteur général (kill switch).
        interrupteur = Switch(this).apply {
            text = "Animations activées"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 32)
            setOnCheckedChangeListener { _, coche -> Reglages.definir(this@MainActivity, coche) }
        }

        // 2. Le mode : à chaque ouverture, ou seulement quand l'app démarre.
        interrupteurDemarrage = Switch(this).apply {
            text = "Seulement au démarrage de l'app"
            textSize = 18f
            setTextColor(Color.parseColor("#DFE4F7"))
            setPadding(0, 0, 0, 40)
            setOnCheckedChangeListener { _, coche ->
                Reglages.definirSeulementDemarrage(this@MainActivity, coche)
            }
        }

        // Le grand bouton vers la liste des apps : couper, accélérer ou ralentir chaque animation.
        val boutonApps = Button(this).apply {
            text = "🎬  Animations par app"
            textSize = 18f
            setOnClickListener { startActivity(Intent(this@MainActivity, AppsActivity::class.java)) }
        }

        val boutonVolet = Button(this).apply {
            text = "Ajouter le bouton au volet rapide"
            setOnClickListener { proposerTuile() }
        }

        statut = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#DFE4F7"))
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 24)
        }

        val boutonAccessibilite = Button(this).apply {
            text = if (BuildConfig.STORE) "Activer la détection" else "Ouvrir les réglages d'accessibilité"
            // Version Store : Google exige un écran d'explication AVANT d'envoyer vers les réglages.
            setOnClickListener {
                if (BuildConfig.STORE) expliquerAccessibilite()
                else startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val boutonJournal = Button(this).apply {
            text = "Ouvrir le journal (logs)"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, JournalActivity::class.java))
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
            text = "Version " + packageManager.getPackageInfo(packageName, 0).versionName
            textSize = 14f
            setTextColor(Color.parseColor("#94A0C4"))
            gravity = Gravity.CENTER
            setPadding(0, 64, 0, 0)
        }

        colonne.addView(interrupteur)
        colonne.addView(interrupteurDemarrage)
        colonne.addView(boutonApps)
        colonne.addView(boutonVolet)
        colonne.addView(statut)
        colonne.addView(boutonAccessibilite)
        // Les outils de développement (journal, liste des apps, mode standard) : version Perso seulement.
        if (!BuildConfig.STORE) {
            colonne.addView(boutonJournal)
            colonne.addView(boutonListe)
            colonne.addView(statutStandard)
            colonne.addView(boutonStandard)
        }
        colonne.addView(version)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1B2340"))
            addView(colonne)
        })
    }

    override fun onResume() {
        super.onResume()
        interrupteur.isChecked = Reglages.actif(this)
        interrupteurDemarrage.isChecked = Reglages.seulementDemarrage(this)
        statut.text = if (serviceActif()) {
            "✅ Détection activée"
        } else {
            "⚠️ Détection désactivée : active AnimLauncher dans l'accessibilité."
        }
        if (!BuildConfig.STORE) afficherEtatStandard()
    }

    // ---------------- L'ÉCRAN D'EXPLICATION (version Store) ----------------
    // Règle de Google Play pour les apps qui utilisent l'accessibilité sans être des outils pour
    // personnes handicapées : avant d'envoyer vers les réglages, l'app doit expliquer clairement
    // CE QU'ELLE FAIT avec, POURQUOI, et laisser ACCEPTER ou REFUSER. Ce texte doit rester séparé
    // de toute autre information sur les données. Refuser ne bloque rien : on peut revenir plus tard.
    private fun expliquerAccessibilite() {
        val texte = """
            AnimLauncher utilise le service d'accessibilité d'Android (API AccessibilityService).

            Pourquoi ?
            Pour savoir sur quelle icône de ton écran d'accueil tu appuies, et quelle application s'ouvre. C'est ce qui permet de lancer l'animation AVANT que l'application apparaisse. Aucune autre fonction d'Android ne prévient assez tôt.

            Ce que l'app reçoit :
            • le nom écrit sous l'icône que tu touches sur l'écran d'accueil ;
            • le nom de l'application qui passe au premier plan.

            Ce que l'app ne fait PAS :
            • elle ne lit pas le contenu de tes écrans, ni ce que tu tapes (messages, mots de passe…) ;
            • elle n'agit jamais à ta place ;
            • rien n'est collecté, enregistré sur un serveur ou partagé : tout reste sur ton téléphone.

            Tu peux désactiver ce service à tout moment dans Paramètres > Accessibilité.
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Pourquoi AnimLauncher a besoin de l'accessibilité")
            .setMessage(texte)
            .setCancelable(false)                       // il faut choisir : accepter ou refuser
            .setPositiveButton("Accepter et activer") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Refuser") { _, _ ->
                statut.text = "Sans l'accessibilité, AnimLauncher ne peut pas jouer d'animation. " +
                    "Tu peux changer d'avis quand tu veux avec le bouton ci-dessous."
            }
            .show()
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
            statut.text = "Ouvre le volet rapide, appuie sur le crayon ✏️ et glisse le bouton AnimLauncher."
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
