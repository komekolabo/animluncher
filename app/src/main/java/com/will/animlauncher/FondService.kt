package com.will.animlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import java.io.File
import kotlin.math.max
import kotlin.math.min

// LES VRAIS FONDS D'ÉCRAN ANIMÉS (11/11, étape 1).
//
// Un « WallpaperService » est un service qu'Android appelle pour dessiner le fond de l'écran d'accueil
// (et de l'écran de verrouillage si l'utilisateur le choisit). Android lui donne une « surface » (la
// zone de l'écran derrière les icônes) ; le service y dessine ce qu'il veut. Notre app n'a PAS besoin
// d'être ouverte : Android lance le service tout seul, même après un redémarrage.
//
// Il y en a DEUX (voir l'explication dans FondsAnimes.kt, « POURQUOI DEUX SERVICES ? ») :
//  - FondDessinService : les fonds dessinés par notre code (Nuit, Galets), les GIF et les images fixes ;
//  - FondVideoService : les vidéos, envoyées directement à la puce vidéo du téléphone.
//
// LES ÉCONOMIES DE BATTERIE (le cœur de l'idée) :
//  1) on ne dessine RIEN quand le fond n'est pas visible (une app par-dessus, écran éteint) ;
//  2) 30 images par seconde par défaut (« Éco »), 60 seulement si on choisit « Fluide » ;
//  3) option « figer » : après 10 s sans toucher l'accueil, le mouvement ralentit puis s'arrête ;
//     il repart dès qu'on touche l'écran ou qu'on change de page ;
//  4) en mode économie d'énergie, le fond RALENTIT (15 images/s) ; il ne se fige que si le téléphone
//     est en surchauffe (avant le 12/11, l'économie d'énergie le figeait : Will voyait une image fixe) ;
//  5) une image fixe n'est dessinée qu'UNE fois (et redessinée seulement si Android le demande) ;
//  6) le dessin se fait avec la carte graphique (lockHardwareCanvas), plus économe que le processeur.

// ---------------------------------------------------------------------------------------------
// LE GARDIEN : surveille le mode économie d'énergie, la chaleur du téléphone et l'écran (allumé / éteint).
//  - economie : mode économie d'énergie, ou téléphone chaud -> on RALENTIT (moins d'images par seconde) ;
//  - surchauffe : téléphone brûlant (niveau « critique » d'Android) -> on FIGE le fond ;
//  - ecranAllume : sert de secours si Android ne nous dit jamais si le fond est visible (voir plus bas).
// Il prévient le moteur dès que quelque chose change.
// ---------------------------------------------------------------------------------------------
class GardienBatterie(private val c: Context, private val quandChange: () -> Unit) {
    private val pm = c.getSystemService(PowerManager::class.java)
    var economie = false
        private set
    var surchauffe = false
        private set
    var ecranAllume = true
        private set
    private var ecouteChaleur: Any? = null          // l'écouteur de chaleur (Android 10 et plus)
    private val recepteur = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) = calculer()
    }

    fun demarrer() {
        val filtre = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED).apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        // Android 13+ veut qu'on précise si d'autres apps peuvent nous envoyer ces messages : non.
        // (Les messages du système, comme ceux-ci, arrivent quand même.)
        if (Build.VERSION.SDK_INT >= 33) c.registerReceiver(recepteur, filtre, Context.RECEIVER_NOT_EXPORTED)
        else c.registerReceiver(recepteur, filtre)
        if (Build.VERSION.SDK_INT >= 29) {
            val l = PowerManager.OnThermalStatusChangedListener { calculer() }
            pm.addThermalStatusListener(l)
            ecouteChaleur = l
        }
        calculer()
    }

    fun arreter() {
        try { c.unregisterReceiver(recepteur) } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT >= 29) {
            (ecouteChaleur as? PowerManager.OnThermalStatusChangedListener)?.let { pm.removeThermalStatusListener(it) }
        }
        ecouteChaleur = null
    }

    private fun calculer() {
        val avant = Triple(economie, surchauffe, ecranAllume)
        val chaleur = if (Build.VERSION.SDK_INT >= 29) pm.currentThermalStatus else 0
        economie = pm.isPowerSaveMode || chaleur >= PowerManager.THERMAL_STATUS_SEVERE
        surchauffe = chaleur >= PowerManager.THERMAL_STATUS_CRITICAL
        ecranAllume = pm.isInteractive
        if (avant != Triple(economie, surchauffe, ecranAllume)) quandChange()
    }
}

// ---------------------------------------------------------------------------------------------
// LE DIAGNOSTIC (12/11) : chaque moteur note ce qu'il vit dans un petit carnet « fond_diag »
// (visible ou non, images dessinées, état de la vidéo…). L'écran « Fonds d'écran » l'affiche en bas :
// Will peut ainsi dire à Claude ce qui se passe sur son téléphone.
// ---------------------------------------------------------------------------------------------
object DiagFond {
    fun carnet(c: Context): SharedPreferences = c.getSharedPreferences("fond_diag", Context.MODE_PRIVATE)

    fun noter(c: Context, vararg paires: Pair<String, Any>) {
        val e = carnet(c).edit()
        for ((cle, v) in paires) when (v) {
            is Boolean -> e.putBoolean(cle, v)
            is Int -> e.putInt(cle, v)
            is Long -> e.putLong(cle, v)
            else -> e.putString(cle, v.toString())
        }
        e.apply()
    }
}

// ---------------------------------------------------------------------------------------------
// LE PEINTRE : pose une image sur tout l'écran SANS la déformer (« couvrir ») : elle est agrandie
// jusqu'à remplir l'écran, et ce qui dépasse sur les côtés est coupé (centré).
// ---------------------------------------------------------------------------------------------
object PeintreFond {
    private val filtre = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)  // lissage à l'agrandissement
    private val cadre = RectF()

    fun couvrir(c: Canvas, b: Bitmap, w: Float, h: Float) {
        val s = max(w / b.width, h / b.height)
        val bw = b.width * s
        val bh = b.height * s
        cadre.set((w - bw) / 2f, (h - bh) / 2f, (w + bw) / 2f, (h + bh) / 2f)
        c.drawBitmap(b, null, cadre, filtre)
    }

    // Charge une image du téléphone en la réduisant si elle est bien plus grande que l'écran
    // (une photo de 50 mégapixels prendrait 200 Mo de mémoire pour rien).
    fun chargerImage(chemin: String, cotePlusLong: Int): Bitmap? {
        val taille = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(chemin, taille)
        if (taille.outWidth <= 0) return null
        var reduction = 1
        while (max(taille.outWidth, taille.outHeight) / (reduction * 2) >= cotePlusLong) reduction *= 2
        return BitmapFactory.decodeFile(chemin, BitmapFactory.Options().apply { inSampleSize = reduction })
    }
}

// ---------------------------------------------------------------------------------------------
// LE LECTEUR DE GIF : un GIF est une suite d'images. L'outil « Movie » d'Android sait dire quelle image
// montrer à un instant donné. On la peint dans une petite image à nous (la taille du GIF), qu'on agrandit
// ensuite sur l'écran avec le peintre. (Movie est ancien, mais il marche partout et sans bibliothèque.)
// Plus tard (étape 2) : les GIF seront convertis en vidéo à l'import, encore plus économe.
// ---------------------------------------------------------------------------------------------
@Suppress("DEPRECATION")
class LecteurGif(chemin: String) {
    private val film: Movie? = try { Movie.decodeFile(chemin) } catch (_: Exception) { null }
    val valide = film != null && film.width() > 0 && film.height() > 0
    val anime = valide && film!!.duration() > 0
    private val image: Bitmap? = if (valide) Bitmap.createBitmap(film!!.width(), film.height(), Bitmap.Config.ARGB_8888) else null
    private val toile: Canvas? = image?.let { Canvas(it) }

    // L'image du GIF à l'instant t (en secondes).
    fun imageA(t: Float): Bitmap? {
        val f = film ?: return null
        val b = image ?: return null
        val duree = f.duration().takeIf { it > 0 } ?: 1
        f.setTime(((t * 1000f).toLong() % duree).toInt())
        b.eraseColor(Color.TRANSPARENT)
        f.draw(toile!!, 0f, 0f)
        return b
    }
}

// =============================================================================================
// 1) LE SERVICE DES FONDS DESSINÉS (dessins de l'app, GIF, images fixes)
// =============================================================================================
class FondDessinService : WallpaperService() {

    override fun onCreateEngine(): Engine = Moteur()

    // Le « moteur » : un par écran où le fond est affiché (accueil, verrouillage, aperçu d'Android).
    // Choreographer.FrameCallback : Android nous appelle à chaque image de l'écran (doFrame), quand on le demande.
    inner class Moteur : Engine(), Choreographer.FrameCallback, SharedPreferences.OnSharedPreferenceChangeListener {
        private val ctx: Context = this@FondDessinService
        private val dp = ctx.resources.displayMetrics.density
        private var choix = CarnetFond.lire(ctx, CarnetFond.PREFIXE_DESSIN)

        // le contenu : UN des trois est rempli
        private var dessin: DessinFond? = null
        private var image: Bitmap? = null
        private var gif: LecteurGif? = null

        private var visible = false
        private var largeur = 0f
        private var hauteur = 0f
        private var imageDemandee = false        // une image est-elle déjà demandée à Android ?
        private var t = 0f                       // le temps de l'animation (il s'arrête quand c'est figé)
        private var derniereImageNs = 0L         // quand on a dessiné la dernière image
        private var vitesse = 1f                 // 1 = normal, 0 = figé (pour ralentir en douceur)
        private var dernierContact = SystemClock.uptimeMillis()
        private var toileMateriel = true         // dessiner avec la carte graphique (sinon : processeur)
        private val gardien = GardienBatterie(ctx) { quandLeGardienChange() }
        private val voile = Paint()

        // SECOURS : normalement Android dit au moteur quand le fond devient visible (onVisibilityChanged).
        // Si ce signal n'arrive jamais (lanceurs capricieux), au bout de 1,5 s on considère le fond
        // visible tant que l'écran est allumé.
        private var signalRecu = false
        private val minuteur = Handler(Looper.getMainLooper())
        private val secours = Runnable {
            if (!signalRecu) { visible = gardien.ecranAllume; noterVisible(); relancer() }
        }
        private var images = 0                   // images dessinées (pour le diagnostic)
        private var dernierDiag = 0L

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setTouchEventsEnabled(true)          // pour savoir quand on touche l'accueil (option « figer »)
            CarnetFond.carnet(ctx).registerOnSharedPreferenceChangeListener(this)
            gardien.demarrer()
            charger()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            minuteur.postDelayed(secours, 1500L)
        }

        private fun quandLeGardienChange() {
            if (!signalRecu) visible = gardien.ecranAllume      // mode secours : on suit l'écran
            noterVisible()
            relancer()
        }

        private fun noterVisible() {
            DiagFond.noter(ctx, "d_vis" to visible, "d_vis_ms" to System.currentTimeMillis(),
                "d_signal" to signalRecu, "d_eco" to gardien.economie, "d_chaud" to gardien.surchauffe)
        }

        override fun onDestroy() {
            minuteur.removeCallbacks(secours)
            Choreographer.getInstance().removeFrameCallback(this)
            CarnetFond.carnet(ctx).unregisterOnSharedPreferenceChangeListener(this)
            gardien.arreter()
            super.onDestroy()
        }

        // Prépare le contenu d'après le carnet. Si le fichier a disparu : le fond « Nuit ».
        private fun charger() {
            dessin = null; image = null; gif = null
            val f = File(choix.id)
            when (choix.sorte) {
                CarnetFond.IMAGE -> {
                    val m = ctx.resources.displayMetrics
                    image = if (f.exists()) PeintreFond.chargerImage(f.path, max(m.widthPixels, m.heightPixels)) else null
                }
                CarnetFond.GIF -> gif = if (f.exists()) LecteurGif(f.path).takeIf { it.valide } else null
                else -> dessin = CarnetFond.dessin(choix.id)
            }
            if (dessin == null && image == null && gif == null) dessin = FondNuit()
            voile.color = Color.argb(choix.assombrir * 255 / 100, 0, 0, 0)
        }

        // Le contenu bouge-t-il ? (une image fixe ou un GIF d'une seule image : non)
        private fun anime() = dessin != null || gif?.anime == true

        override fun onSharedPreferenceChanged(p: SharedPreferences?, cle: String?) {
            if (cle != null && !cle.startsWith(CarnetFond.PREFIXE_DESSIN)) return
            choix = CarnetFond.lire(ctx, CarnetFond.PREFIXE_DESSIN)
            charger()
            dernierContact = SystemClock.uptimeMillis()
            vitesse = 1f
            dessinerImage()
            relancer()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            largeur = w.toFloat(); hauteur = h.toFloat()
            dessinerImage()
            relancer()
        }

        // Android demande de redessiner (par exemple quand le fond réapparaît) : une image tout de suite.
        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            dessinerImage()
        }

        override fun onVisibilityChanged(v: Boolean) {
            signalRecu = true
            visible = v
            noterVisible()
            if (v) {
                dernierContact = SystemClock.uptimeMillis()
                derniereImageNs = 0L
                relancer()
            } else {
                // caché : on arrête TOUT (c'est l'économie n°1)
                Choreographer.getInstance().removeFrameCallback(this)
                imageDemandee = false
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            Choreographer.getInstance().removeFrameCallback(this)
            imageDemandee = false
            super.onSurfaceDestroyed(holder)
        }

        // On touche l'accueil, ou on change de page : le fond se réveille (option « figer »).
        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            reveiller()
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xStep: Float, yStep: Float, xPx: Int, yPx: Int) {
            super.onOffsetsChanged(xOffset, yOffset, xStep, yStep, xPx, yPx)
            reveiller()
        }

        private fun reveiller() {
            dernierContact = SystemClock.uptimeMillis()
            relancer()
        }

        // Demande une image à Android, si le fond est visible et qu'aucune n'est déjà demandée.
        private fun relancer() {
            if (visible && !imageDemandee) {
                imageDemandee = true
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        // Android nous appelle à chaque image de l'écran (jusqu'à 120 fois par seconde sur le Flip6).
        override fun doFrame(ns: Long) {
            imageDemandee = false
            if (!visible) return
            // Éco = 30 images/s : on saute les images en trop (tolérance de 4 ms pour rester régulier).
            // En économie d'énergie : 15 images/s (ça bouge toujours, mais deux fois moins de travail).
            val periodeNs = when {
                gardien.economie -> 66_600_000L
                choix.fluide -> 16_600_000L
                else -> 33_300_000L
            }
            if (derniereImageNs != 0L && ns - derniereImageNs < periodeNs - 4_000_000L) {
                relancer()
                return
            }
            val dt = if (derniereImageNs == 0L) 0f else min((ns - derniereImageNs) / 1e9f, 0.1f)
            derniereImageNs = ns
            // l'option « figer » : après 10 s sans contact, on ralentit sur 1,5 s jusqu'à l'arrêt ;
            // au réveil, on réaccélère sur 0,6 s (pas de démarrage brusque)
            val doitFiger = gardien.surchauffe || (choix.figer && SystemClock.uptimeMillis() - dernierContact > 10_000L)
            vitesse = if (doitFiger) max(0f, vitesse - dt / 1.5f) else min(1f, vitesse + dt / 0.6f)
            if (gardien.surchauffe) vitesse = 0f   // téléphone brûlant : arrêt immédiat
            t += dt * vitesse
            dessinerImage()
            images++
            // le diagnostic : au plus une note toutes les 2 s (écrire à chaque image coûterait de la batterie)
            val maintenant = SystemClock.uptimeMillis()
            if (maintenant - dernierDiag > 2000L) {
                dernierDiag = maintenant
                DiagFond.noter(ctx, "d_images" to images, "d_img_ms" to System.currentTimeMillis())
            }
            // on redemande une image seulement si ça bouge encore
            if (anime() && (vitesse > 0f || !doitFiger)) relancer()
        }

        // Dessine UNE image sur la surface du fond d'écran.
        private fun dessinerImage() {
            val surface = surfaceHolder.surface
            if (!surface.isValid || largeur <= 0f) return
            val c: Canvas = try {
                if (toileMateriel) surface.lockHardwareCanvas() else surfaceHolder.lockCanvas()
            } catch (e: Exception) {
                // si la carte graphique refuse (rare), on passe au processeur pour toujours
                DiagFond.noter(ctx, "d_erreur" to (e.javaClass.simpleName + " " + (e.message ?: "")).take(80))
                if (toileMateriel) { toileMateriel = false; dessinerImage() }
                return
            } ?: return
            try {
                c.drawColor(Color.BLACK)
                dessin?.dessiner(c, largeur, hauteur, t, dp)
                image?.let { PeintreFond.couvrir(c, it, largeur, hauteur) }
                gif?.imageA(t)?.let { PeintreFond.couvrir(c, it, largeur, hauteur) }
                if (choix.assombrir > 0) c.drawRect(0f, 0f, largeur, hauteur, voile)
            } finally {
                try {
                    if (toileMateriel) surface.unlockCanvasAndPost(c) else surfaceHolder.unlockCanvasAndPost(c)
                } catch (_: Exception) {}
            }
        }
    }
}

// =============================================================================================
// 2) LE SERVICE DES FONDS VIDÉO
// La vidéo est confiée au lecteur d'Android (MediaPlayer), branché DIRECTEMENT sur la surface du fond :
// c'est la puce vidéo du téléphone qui décode, sans copie intermédiaire (le chemin le plus économe).
// Le son a été retiré à l'import (FondsActivity) ; par sécurité, le volume est aussi à 0.
// =============================================================================================
class FondVideoService : WallpaperService() {

    override fun onCreateEngine(): Engine = Moteur()

    inner class Moteur : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val ctx: Context = this@FondVideoService
        private var choix = CarnetFond.lire(ctx, CarnetFond.PREFIXE_VIDEO)
        private var lecteur: MediaPlayer? = null
        private var pret = false
        private var visible = false
        private var fige = false                 // figé par l'option « figer » (10 s sans contact)
        private val minuteur = Handler(Looper.getMainLooper())
        private val figer = Runnable { fige = true; mettreAJour() }
        private val gardien = GardienBatterie(ctx) { quandLeGardienChange() }

        // SECOURS : même principe que pour les dessins (voir FondDessinService)
        private var signalRecu = false
        private val secours = Runnable {
            if (!signalRecu) { visible = gardien.ecranAllume; noterVisible(); mettreAJour() }
        }

        private fun quandLeGardienChange() {
            if (!signalRecu) visible = gardien.ecranAllume
            noterVisible()
            mettreAJour()
        }

        private fun noterVisible() {
            DiagFond.noter(ctx, "v_vis" to visible, "v_vis_ms" to System.currentTimeMillis(),
                "v_signal" to signalRecu, "v_eco" to gardien.economie, "v_chaud" to gardien.surchauffe)
        }

        private fun noterEtat(etat: String) = DiagFond.noter(ctx, "v_etat" to etat, "v_etat_ms" to System.currentTimeMillis())

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setTouchEventsEnabled(true)
            CarnetFond.carnet(ctx).registerOnSharedPreferenceChangeListener(this)
            gardien.demarrer()
        }

        override fun onDestroy() {
            minuteur.removeCallbacks(figer)
            minuteur.removeCallbacks(secours)
            CarnetFond.carnet(ctx).unregisterOnSharedPreferenceChangeListener(this)
            gardien.arreter()
            liberer()
            super.onDestroy()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            preparer(holder)
            minuteur.postDelayed(secours, 1500L)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            liberer()
            super.onSurfaceDestroyed(holder)
        }

        override fun onSharedPreferenceChanged(p: SharedPreferences?, cle: String?) {
            if (cle != null && !cle.startsWith(CarnetFond.PREFIXE_VIDEO)) return
            val nouveau = CarnetFond.lire(ctx, CarnetFond.PREFIXE_VIDEO)
            val autreFichier = nouveau.id != choix.id
            choix = nouveau
            if (autreFichier && surfaceHolder.surface.isValid) preparer(surfaceHolder) else mettreAJour()
        }

        // Ouvre la vidéo et la branche sur la surface du fond.
        private fun preparer(holder: SurfaceHolder) {
            liberer()
            val f = File(choix.id)
            if (!f.exists()) { noterEtat("absent"); return }
            noterEtat("chargement")
            val mp = MediaPlayer()
            try {
                mp.setDataSource(f.path)
                mp.setSurface(holder.surface)
                mp.isLooping = true                    // en boucle
                mp.setVolume(0f, 0f)                   // muet
                mp.setOnPreparedListener {
                    // « couvrir » l'écran sans déformer (ce qui dépasse est coupé)
                    mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    mp.seekTo(0)                       // affiche la 1re image même si on ne joue pas encore
                    pret = true
                    mettreAJour()
                }
                mp.setOnErrorListener { _, quoi, extra -> pret = false; noterEtat("erreur $quoi/$extra"); true }
                mp.prepareAsync()                      // prépare sans bloquer l'écran
                lecteur = mp
            } catch (e: Exception) {
                noterEtat("illisible " + e.javaClass.simpleName)
                mp.release()                           // fichier illisible : on abandonne proprement
            }
        }

        private fun liberer() {
            pret = false
            lecteur?.let { try { it.release() } catch (_: Exception) {} }
            lecteur = null
        }

        // Joue ou met en pause selon la situation (visible, économie d'énergie, figé).
        private fun mettreAJour() {
            val mp = lecteur ?: return
            if (!pret) return
            // (l'économie d'énergie ne met PAS la vidéo en pause : c'est la puce vidéo qui travaille, très peu coûteux)
            val doitJouer = visible && !gardien.surchauffe && !fige
            try {
                if (doitJouer && !mp.isPlaying) { mp.start(); noterEtat("lecture") }
                else if (!doitJouer && mp.isPlaying) { mp.pause(); noterEtat("pause") }
            } catch (e: Exception) { noterEtat("erreur " + e.javaClass.simpleName) }
            minuteur.removeCallbacks(figer)
            if (doitJouer && choix.figer) minuteur.postDelayed(figer, 10_000L)
        }

        override fun onVisibilityChanged(v: Boolean) {
            signalRecu = true
            visible = v
            noterVisible()
            if (v) fige = false
            mettreAJour()
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            reveiller()
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xStep: Float, yStep: Float, xPx: Int, yPx: Int) {
            super.onOffsetsChanged(xOffset, yOffset, xStep, yStep, xPx, yPx)
            reveiller()
        }

        private fun reveiller() {
            fige = false
            mettreAJour()                // relance la vidéo et le compte à rebours des 10 s
        }
    }
}
