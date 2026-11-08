package com.will.animlauncher

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout

// ============================================================================
// LE MODE STANDARD (prototype) : la même chose que AppWatcherService, mais SANS accessibilité.
//
// Comment il sait qu'une app s'ouvre ? Android tient un JOURNAL D'UTILISATION : chaque fois
// qu'une app passe au premier plan, il y écrit une ligne (« telle app, à telle heure »).
// Ce service relit ce journal très souvent et repère le moment où on passe
// de L'ÉCRAN D'ACCUEIL à UNE APP : c'est un lancement depuis une icône -> on joue l'animation.
//
// Ce qu'il faut à l'utilisateur (aucune n'est « dangereuse » pour Google Play) :
//   1. « Accès aux données d'utilisation »   (pour lire le journal)
//   2. « Afficher par-dessus les autres apps » (pour le rideau)
//   3. Une notification permanente            (sinon Android finit par arrêter le service)
//
// LA LIMITE : Android n'écrit dans le journal qu'APRÈS l'ouverture de l'app. Il y a donc un petit
// décalage. Chaque animation note ce décalage dans le journal (« décalage XX ms ») :
// c'est justement ce qu'on veut mesurer avec ce prototype.
// ============================================================================
class ModeStandard : Service() {

    private val minuteur = Handler(Looper.getMainLooper())
    private lateinit var journalAndroid: UsageStatsManager

    // Ce qu'on a déjà lu dans le journal d'Android (pour ne pas traiter deux fois la même ligne).
    private var derniereHeureLue = 0L
    // L'app au premier plan en ce moment (d'après le journal).
    private var appActuelle: String? = null
    // Quand chaque app a été quittée pour la dernière fois (pour « seulement au démarrage »).
    private val quitteeA = HashMap<String, Long>()
    // L'écran est-il allumé ? Écran éteint = on arrête de surveiller (économie de batterie).
    private var ecranAllume = true

    private val ignorees = setOf(
        "com.samsung.android.spay",
        "com.samsung.android.honeyboard",
        "com.samsung.android.app.spage"
    )

    private val ecranAccueil: String? by lazy {
        val intention = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.resolveActivity(intention, 0)?.activityInfo?.packageName
    }

    override fun onBind(intent: Intent?): IBinder? = null   // personne ne se « connecte » à ce service

    override fun onCreate() {
        super.onCreate()
        enMarche = true
        journalAndroid = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        derniereHeureLue = System.currentTimeMillis()   // on ignore tout ce qui s'est passé avant
        passerAuPremierPlan()
        ecouterEcran()
        installerRideau()
        noter("=== [standard] Mode standard démarré ===")
        minuteur.post(surveillance)
    }

    // Si Android tue le service (manque de mémoire), START_STICKY lui demande de le relancer.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        enMarche = false
        minuteur.removeCallbacksAndMessages(null)
        try { unregisterReceiver(recepteurEcran) } catch (_: Exception) {}
        try { cadreRideau?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } } catch (_: Exception) {}
        noter("=== [standard] Mode standard arrêté ===")
        super.onDestroy()
    }

    // ---------------- LA NOTIFICATION PERMANENTE ----------------
    // Android exige qu'un service qui tourne en continu le montre à l'utilisateur.
    private fun passerAuPremierPlan() {
        val gestionnaire = getSystemService(NotificationManager::class.java)
        // Un « canal » = une catégorie de notifications. IMPORTANCE_MIN : discrète, sans son.
        gestionnaire.createNotificationChannel(
            NotificationChannel(CANAL, "Mode standard", NotificationManager.IMPORTANCE_MIN)
        )
        // Toucher la notification ouvre l'écran principal de l'app.
        val ouvrir = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CANAL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("AnimLauncher actif")
            .setContentText("Mode standard (sans accessibilité)")
            .setContentIntent(ouvrir)
            .setOngoing(true)
            .build()
        // Depuis Android 14, on doit dire POURQUOI le service tourne : « usage spécial ».
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    // ---------------- ÉCRAN ALLUMÉ / ÉTEINT ----------------
    private val recepteurEcran = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            ecranAllume = intent.action == Intent.ACTION_SCREEN_ON
            minuteur.removeCallbacks(surveillance)
            if (ecranAllume) {
                derniereHeureLue = System.currentTimeMillis()   // on ne rejoue pas ce qui s'est passé écran éteint
                minuteur.post(surveillance)
            }
        }
    }

    private fun ecouterEcran() {
        val filtre = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(recepteurEcran, filtre, RECEIVER_NOT_EXPORTED)
        else registerReceiver(recepteurEcran, filtre)
        ecranAllume = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive
    }

    // ---------------- LA SURVEILLANCE ADAPTATIVE ----------------
    // On relit le journal TRÈS souvent (toutes les 50 ms) quand on est sur l'écran d'accueil,
    // car c'est de là qu'on lance les apps : il faut réagir vite. Dans une app, on relit plus
    // rarement (250 ms) : on attend juste de revenir à l'accueil. Écran éteint : on ne relit plus.
    private val surveillance = object : Runnable {
        override fun run() {
            if (!ecranAllume) return
            try {
                lireJournal()
            } catch (e: Exception) {
                noter("[standard] ERREUR (rattrapée) : " + Journal.pile(e))
            }
            val pause = if (appActuelle == ecranAccueil) 50L else 250L
            minuteur.postDelayed(this, pause)
        }
    }

    private fun lireJournal() {
        val maintenant = System.currentTimeMillis()
        // On relit depuis un peu avant la dernière lecture (1 s de marge : Android range parfois
        // une ligne avec un léger retard), puis on saute les lignes déjà vues grâce à l'heure.
        val lignes = journalAndroid.queryEvents(derniereHeureLue - 1000, maintenant)
        val ligne = UsageEvents.Event()
        while (lignes.hasNextEvent()) {
            lignes.getNextEvent(ligne)
            if (ligne.timeStamp <= derniereHeureLue) continue
            derniereHeureLue = ligne.timeStamp
            // On ne garde que « telle app vient de passer au premier plan ».
            // (MOVE_TO_FOREGROUND a le même numéro que ACTIVITY_RESUMED des Android récents.)
            if (ligne.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND) continue
            arrivee(ligne.packageName, ligne.timeStamp)
        }
    }

    // Une app vient de passer au premier plan (« heure » = quand Android l'a noté).
    private fun arrivee(app: String, heure: Long) {
        if (app == appActuelle) return            // la même app qui change d'écran : rien
        val precedente = appActuelle
        appActuelle = app
        val maintenant = System.currentTimeMillis()
        // l'app qu'on quitte : on note l'heure (elle reste ouverte en arrière-plan)
        if (precedente != null && precedente != ecranAccueil) quitteeA[precedente] = maintenant

        if (app == ecranAccueil || app == packageName || app in ignorees) return
        // LA RÈGLE « SEULEMENT DEPUIS UNE ICÔNE » : on n'anime que si on arrive DE l'écran d'accueil.
        // Passer d'une app à une autre (un lien, un partage…) ne déclenche rien.
        if (precedente != ecranAccueil) return

        // Si l'accessibilité est encore allumée, c'est elle qui joue les animations :
        // le mode standard se tait pour ne pas jouer l'animation deux fois.
        if (accessibiliteActive(this)) {
            noter("[standard] $app ouverte, mais l'accessibilité est active : je ne fais rien")
            return
        }
        if (!Reglages.actif(this)) return                       // kill switch
        val lancement = Catalogue.lancement(this, app) ?: return
        // « Seulement au démarrage » : si l'app a été quittée il y a moins de 30 min,
        // elle est sans doute encore ouverte en arrière-plan -> pas d'animation.
        val quittee = quitteeA[app]
        if (Reglages.seulementDemarrage(this, app) && quittee != null && maintenant - quittee < 30 * 60_000L) {
            noter("[standard] $app : déjà ouverte en arrière-plan, pas d'animation")
            return
        }
        afficher(lancement)
        // LA MESURE IMPORTANTE : combien de temps entre l'ouverture de l'app et notre réaction.
        noter("[standard] $app : animation lancée, décalage ${maintenant - heure} ms")
    }

    // ---------------- LE RIDEAU (repris de AppWatcherService) ----------------
    // Seule différence : la fenêtre est de type « APPLICATION_OVERLAY » (permission
    // « Afficher par-dessus les autres apps ») au lieu de « ACCESSIBILITY_OVERLAY ».
    private var rideau: Rideau? = null
    private var cadreRideau: FrameLayout? = null
    private var reglagesRideau: WindowManager.LayoutParams? = null
    private var seanceEnCours: Rideau.Seance? = null

    private fun installerRideau() {
        if (rideau != null || !Settings.canDrawOverlays(this)) return
        val taille = tailleEcran()
        val reglages = WindowManager.LayoutParams(
            taille.x, taille.y,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // alpha 1 au début pour qu'Android crée la surface de dessin, puis 0 (invisible).
            alpha = 1f
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            } else if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val r = Rideau(this)
        val cadre = FrameLayout(this)
        cadre.addView(r, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(cadre, reglages)
            rideau = r
            cadreRideau = cadre
            reglagesRideau = reglages
            minuteur.postDelayed({ if (seanceEnCours == null) montrerRideau(false) }, 2000)
        } catch (e: Exception) {
            noter("[standard] ERREUR installation du rideau : ${e.message}")
        }
    }

    private fun montrerRideau(visible: Boolean) {
        val cadre = cadreRideau ?: return
        val reglages = reglagesRideau ?: return
        val a = if (visible) 1f else 0f
        if (reglages.alpha == a) return
        reglages.alpha = a
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).updateViewLayout(cadre, reglages)
        } catch (e: Exception) {
            noter("[standard] ERREUR rideau : ${e.message}")
        }
    }

    private fun afficher(lancement: Lancement) {
        seanceEnCours?.arreter()
        installerRideau()
        val r = rideau ?: run { noter("[standard] pas de rideau : permission « par-dessus » manquante ?"); return }
        ajusterTaille()
        val seance = Rideau.Seance(lancement.scene, lancement.categorie, lancement.facteur) { fini ->
            if (seanceEnCours === fini) { seanceEnCours = null; montrerRideau(false) }
        }
        seanceEnCours = seance
        // Ici l'app est DÉJÀ au premier plan (le journal nous le dit après coup) :
        // elle est « prête », l'animation joue sa durée minimale puis se révèle.
        seance.signalerPret()
        r.jouer(seance)
        montrerRideau(true)
        // Filet de sécurité : si l'animation ne se termine pas, on cache le rideau quand même.
        minuteur.postDelayed({
            if (seanceEnCours === seance) { seance.arreter(); seanceEnCours = null; montrerRideau(false) }
        }, seance.dureeMax + 1000)
    }

    private fun ajusterTaille() {
        val cadre = cadreRideau ?: return
        val reglages = reglagesRideau ?: return
        val taille = tailleEcran()
        if (reglages.width == taille.x && reglages.height == taille.y) return
        reglages.width = taille.x
        reglages.height = taille.y
        try { (getSystemService(WINDOW_SERVICE) as WindowManager).updateViewLayout(cadre, reglages) } catch (_: Exception) {}
    }

    private fun tailleEcran(): Point {
        val ecran = (getSystemService(DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        val taille = Point()
        @Suppress("DEPRECATION") ecran.getRealSize(taille)
        return taille
    }

    private fun noter(texte: String) {
        Journal.noter(this, texte)
        Journal.programmerEnvoi(this)
    }

    companion object {
        const val CANAL = "mode_standard"
        @Volatile var enMarche = false

        // A-t-on l'« Accès aux données d'utilisation » ? (réglé par l'utilisateur dans les paramètres)
        fun accesUtilisation(c: Context): Boolean {
            val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val etat = if (Build.VERSION.SDK_INT >= 29)
                ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
            else
                ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
            return etat == AppOpsManager.MODE_ALLOWED
        }

        // Le service d'accessibilité d'AnimLauncher est-il allumé ?
        fun accessibiliteActive(c: Context): Boolean {
            val actifs = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            return actifs.contains("${c.packageName}/")
        }
    }
}
