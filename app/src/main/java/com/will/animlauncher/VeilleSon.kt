package com.will.animlauncher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

// LE MODE « ÉCRAN ÉTEINT, TOUR ALLUMÉ » de la réaction sonore (11/11, idée de Will).
//
// Quand l'option est cochée (voir OptionsSon.kt) et qu'on éteint le téléphone PENDANT une musique :
//   1) Android nous prévient que l'écran vient de s'éteindre ;
//   2) on ouvre aussitôt un écran tout NOIR (VeilleSonActivity) qui rallume l'écran et se pose par-dessus
//      l'écran de verrouillage. Sur un écran OLED, le noir = des points éteints : on dirait que le téléphone
//      est éteint ;
//   3) le tour lumineux, lui, est une fenêtre posée au-dessus de TOUT : il se dessine donc sur ce noir.
// POUR EN SORTIR : rester appuyé un peu moins d'une seconde n'importe où (ou le bouton retour). On retombe sur
// l'écran de verrouillage. Le mode ne se relance alors plus tant qu'on n'a pas déverrouillé le téléphone
// (sinon il reviendrait dès que l'écran se rééteint tout seul). Appuyer sur le bouton d'allumage pendant
// l'écran noir éteint vraiment l'écran, de la même façon.
// Il s'arrête aussi tout seul après 10 secondes sans musique.
// ATTENTION : l'écran reste allumé (même noir, le téléphone ne dort pas) : ça consomme plus de batterie.
object VeilleSon {
    @Volatile var enCours = false            // l'écran noir est affiché en ce moment
    private var suspendue = false            // on en est sorti exprès : attendre le prochain déverrouillage
    private var branche = false

    // « BroadcastReceiver » = une oreille qu'on tend à Android : il nous appelle quand l'écran s'éteint
    // (SCREEN_OFF) et quand le téléphone est déverrouillé (USER_PRESENT).
    private val ecoute = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intention: Intent) {
            try {
                if (intention.action == Intent.ACTION_USER_PRESENT) suspendue = false
                else if (intention.action == Intent.ACTION_SCREEN_OFF) ecranEteint(c)
            } catch (e: Throwable) {
                AnimApp.noter("veille : erreur (${e.javaClass.simpleName})")
            }
        }
    }

    // Tend l'oreille (une seule fois pour toute la vie de l'app).
    fun brancher(c: Context) {
        if (branche) return
        try {
            val filtre = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }
            val app = c.applicationContext
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(ecoute, filtre, Context.RECEIVER_NOT_EXPORTED)
            else app.registerReceiver(ecoute, filtre)
            branche = true
        } catch (_: Throwable) {}
    }

    // L'écran vient de s'éteindre : faut-il poser l'écran noir ?
    private fun ecranEteint(c: Context) {
        if (!OptionsSon.veille || suspendue || enCours) return
        // c'est le service d'accessibilité qui ouvre l'écran : Android le laisse ouvrir un écran depuis l'arrière-plan
        val service = AppWatcherService.actif ?: return
        if (!ReactionSonore.actif(c) || !ReactionSonore.permission(c)) return
        val audio = c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (!audio.isMusicActive) return                 // pas de musique : on laisse le téléphone s'éteindre normalement
        service.startActivity(Intent(service, VeilleSonActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        AnimApp.noter("veille : écran noir posé")
    }

    // On est sorti exprès de l'écran noir : ne pas le relancer avant le prochain déverrouillage.
    fun sortieVoulue() { suspendue = true }
}

// L'ÉCRAN NOIR. « Activity » = un écran de l'app.
class VeilleSonActivity : Activity() {
    private val minuteur = Handler(Looper.getMainLooper())
    private var naissance = 0L
    private var silences = 0
    private var aide: TextView? = null

    // l'appui long a duré assez longtemps : on sort
    private val quitter = Runnable { VeilleSon.sortieVoulue(); finish() }
    // la petite phrase d'aide s'efface doucement
    private val cacherAide = Runnable { aide?.animate()?.alpha(0f)?.setDuration(600)?.start() }
    // une fois par seconde : la musique joue-t-elle encore ? Après 10 secondes de silence, on s'en va.
    private val surveiller = object : Runnable {
        override fun run() {
            val musique = try { (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.isMusicActive == true } catch (_: Throwable) { false }
            silences = if (musique) 0 else silences + 1
            if (silences >= 10) { finish(); return }
            minuteur.postDelayed(this, 1000)
        }
    }

    override fun onCreate(etat: Bundle?) {
        super.onCreate(etat)
        naissance = SystemClock.uptimeMillis()
        VeilleSon.enCours = true
        // « montre-toi par-dessus l'écran de verrouillage » et « rallume l'écran »
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or          // l'écran ne se rééteint pas tout seul
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        // la phrase d'aide, en gris discret au milieu : « Reste appuyé pour quitter »
        val texte = TextView(this).apply {
            text = getString(R.string.son_veille_aide)
            setTextColor(Color.rgb(120, 120, 128))
            textSize = 13f
            gravity = Gravity.CENTER
        }
        aide = texte
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(texte, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        })
        barresNoires()
        minuteur.postDelayed(cacherAide, 4000)
        minuteur.postDelayed(surveiller, 1000)
    }

    // LES BARRES DU HAUT ET DU BAS. On ne les CACHE pas : le tour lumineux s'efface exprès quand la barre
    // d'état est cachée (c'est comme ça qu'il reconnaît une vidéo ou un jeu en plein écran). À la place, on met
    // leur fond en noir et on demande des icônes SOMBRES (celles prévues pour un fond clair) : du sombre sur du
    // noir, on ne les voit presque plus.
    @Suppress("DEPRECATION")
    private fun barresNoires() {
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        if (Build.VERSION.SDK_INT >= 30) {
            val sombres = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(sombres, sombres)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    // UN DOIGT SUR L'ÉCRAN : on remontre la phrase d'aide, et s'il reste posé 0,8 seconde, on sort.
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                aide?.animate()?.cancel()
                aide?.alpha = 1f
                minuteur.removeCallbacks(cacherAide)
                minuteur.postDelayed(cacherAide, 3000)
                minuteur.postDelayed(quitter, 800)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> minuteur.removeCallbacks(quitter)
        }
        return true
    }

    // le bouton « retour » fait sortir aussi
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        VeilleSon.sortieVoulue()
        finish()
    }

    // L'écran noir n'est plus visible sans qu'on l'ait fermé nous-mêmes : on a appuyé sur le bouton d'allumage,
    // ou un appel est arrivé par-dessus. On considère qu'on en est sorti exprès.
    // (Pendant les 2 premières secondes on ne fait rien : l'écran est encore en train de se rallumer.)
    override fun onStop() {
        super.onStop()
        if (!isFinishing && SystemClock.uptimeMillis() - naissance > 2000) {
            VeilleSon.sortieVoulue()
            finish()
        }
    }

    override fun onDestroy() {
        minuteur.removeCallbacksAndMessages(null)
        VeilleSon.enCours = false
        super.onDestroy()
    }
}
