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
            setPadding(56, 100, 56, 56)
        }

        // 0. Le logo animé (voir LogoAnime.kt), centré en haut.
        val tailleLogo = (110 * resources.displayMetrics.density).toInt()
        colonne.addView(LogoAnime(this), LinearLayout.LayoutParams(tailleLogo, tailleLogo).apply {
            bottomMargin = (16 * resources.displayMetrics.density).toInt()
        })

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
                else ouvrirAccessibilite()
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
        } else if (peutEtreRestreinte()) {
            "⚠️ Détection désactivée : active AnimLauncher dans l'accessibilité.\n" +
                "(Grisé ? Le bouton ci-dessous t'explique comment le débloquer.)"
        } else {
            "⚠️ Détection désactivée : active AnimLauncher dans l'accessibilité."
        }
        if (!BuildConfig.STORE) afficherEtatStandard()
        // Le guide « grisé » était ouvert et l'accessibilité est maintenant active : c'est gagné.
        if (guide?.isShowing == true && serviceActif()) {
            guide?.dismiss()
            Toast.makeText(this, "✅ C'est activé ! Les animations sont prêtes.", Toast.LENGTH_LONG).show()
        }
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
                ouvrirAccessibilite()
            }
            .setNegativeButton("Refuser") { _, _ ->
                statut.text = "Sans l'accessibilité, AnimLauncher ne peut pas jouer d'animation. " +
                    "Tu peux changer d'avis quand tu veux avec le bouton ci-dessous."
            }
            .show()
    }

    // ---------------- L'ACCESSIBILITÉ GRISÉE (« paramètres restreints ») ----------------
    // Depuis Android 13, une app installée HORS du Play Store (un APK téléchargé, comme depuis
    // GitHub) ne peut pas recevoir l'accessibilité tout de suite : elle apparaît grisée, avec
    // « Contrôlé par les paramètres restreints ». C'est une protection contre les apps piégées.
    // Pour la débloquer, l'utilisateur doit faire 3 gestes, qu'on lui explique ici.
    // (Une app installée depuis le Play Store n'est pas concernée : le guide ne s'affiche pas.)
    private fun peutEtreRestreinte(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false        // la protection n'existe qu'à partir d'Android 13
        val installeur = try {
            packageManager.getInstallSourceInfo(packageName).installingPackageName
        } catch (_: Exception) { null }
        return installeur != "com.android.vending"           // « com.android.vending » = le Play Store
    }

    // Le chemin à suivre dans l'accessibilité pour trouver AnimLauncher. Il dépend de la marque :
    // chez Samsung, les apps comme AnimLauncher sont rangées dans « Applications installées ».
    private val estSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    // (« get() » : calculé au moment où on s'en sert, car le nom de l'app n'est pas encore
    //  disponible quand l'écran est construit. Le nom : « AnimLauncher » ou « AnimLauncher Perso ».)
    private val cheminAccessibilite: String get() {
        val nom = getString(R.string.app_name)
        return if (estSamsung) "touche « Applications installées » (en bas), puis « $nom »"
               else "cherche « $nom » (souvent dans « Applications téléchargées » ou « installées »)"
    }

    // Ouvre l'accessibilité. (Ouvrir DIRECTEMENT la page d'AnimLauncher est réservé aux apps
    // système d'Android : on ouvre donc l'accessibilité, et on affiche le chemin à suivre.)
    // Les deux « extras » demandent aux Paramètres de mettre AnimLauncher EN SURBRILLANCE dans la
    // liste : ça marche sur les Pixel et beaucoup d'autres téléphones ; les autres les ignorent.
    private fun ouvrirPageAccessibilite() {
        val service = ComponentName(this, AppWatcherService::class.java).flattenToString()
        val intention = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(":settings:fragment_args_key", service)
            .putExtra(":settings:show_fragment_args", Bundle().apply {
                putString(":settings:fragment_args_key", service)
            })
        startActivity(intention)
        // le rappel s'affiche par-dessus les Paramètres
        Toast.makeText(this, "Dans l'accessibilité : " + cheminAccessibilite, Toast.LENGTH_LONG).show()
    }

    private fun ouvrirAccessibilite() {
        if (serviceActif() || !peutEtreRestreinte()) {
            ouvrirPageAccessibilite()
            return
        }
        montrerGuide()
    }

    // LE GUIDE EN 3 ÉTAPES, avec un bouton par étape. Il reste ouvert pendant les allers-retours
    // dans les Paramètres, et se ferme tout seul quand l'accessibilité est activée (voir onResume).
    // (Une app ne peut pas appuyer à la place de l'utilisateur dans les Paramètres : on l'emmène
    // donc au bon endroit, et on lui dit exactement où toucher.)
    private var guide: AlertDialog? = null

    private fun montrerGuide() {
        val d = resources.displayMetrics.density
        val contenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * d).toInt(), (8 * d).toInt(), (24 * d).toInt(), 0)
        }
        fun texte(t: String, taille: Float = 15f, haut: Int = 0) = TextView(this).apply {
            text = t; textSize = taille; setPadding(0, (haut * d).toInt(), 0, (6 * d).toInt())
        }
        fun bouton(t: String, action: () -> Unit) = Button(this).apply {
            text = t; isAllCaps = false; setOnClickListener { action() }
        }
        contenu.addView(texte("Android protège les apps installées en dehors du Play Store : " +
            "AnimLauncher apparaît grisé tant qu'on ne l'a pas débloqué. Fais les 3 étapes dans l'ordre :"))

        contenu.addView(texte("① Déclencher le blocage", 17f, 14))
        contenu.addView(texte("Le bouton ouvre l'accessibilité : $cheminAccessibilite (grisé). " +
            "Un message « paramètre restreint » s'affiche : touche OK, puis reviens ici. " +
            "(Sans cette étape, l'option ② n'apparaît pas.)"))
        contenu.addView(bouton("Ouvrir l'accessibilité") { ouvrirPageAccessibilite() })

        contenu.addView(texte("② Autoriser", 17f, 14))
        contenu.addView(texte("Le bouton ouvre les infos de l'app. Touche les 3 points ⋮ EN HAUT À DROITE, " +
            "puis « Autoriser les paramètres restreints », et confirme."))
        contenu.addView(bouton("Ouvrir les infos de l'app") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:$packageName")))
            // un rappel qui s'affiche par-dessus les Paramètres, le temps de trouver le menu
            Toast.makeText(this, "Touche ⋮ en haut à droite, puis « Autoriser les paramètres restreints »",
                Toast.LENGTH_LONG).show()
        })

        contenu.addView(texte("③ Activer", 17f, 14))
        contenu.addView(texte("Retourne dans l'accessibilité : $cheminAccessibilite. Il n'est plus grisé : " +
            "active l'interrupteur. Ce guide se fermera tout seul."))
        contenu.addView(bouton("Ouvrir l'accessibilité") { ouvrirPageAccessibilite() })

        guide?.dismiss()
        guide = AlertDialog.Builder(this)
            .setTitle("Débloquer AnimLauncher")
            .setView(ScrollView(this).apply { addView(contenu) })
            .setNegativeButton("Fermer", null)
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
