package com.will.animlauncher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.Surface
import android.view.SurfaceHolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

// LE FOND D'ÉCRAN « DOSSIER » (17/11, demande de Will) : des photos ET des vidéos qui se relaient.
// Le fond change à chaque retour sur l'écran d'accueil (en quittant une app, en rallumant l'écran).
// La collection et l'ordre sont gérés par DossierFond.kt.
//
// POURQUOI UN TROISIÈME SERVICE, ET POURQUOI OPENGL ?
// Les deux autres services séparent les vidéos et les images, car Android ne laisse pas une même « surface »
// recevoir d'abord des dessins de notre code, puis une vidéo du lecteur (voir CarnetFond, « POURQUOI DEUX
// SERVICES ? »). Ici, il faut passer sans arrêt d'une photo à une vidéo. La solution : on dessine TOUT avec la
// carte graphique (OpenGL ES 2, la façon de dessiner des jeux) :
//  - une photo devient une « texture » (une image rangée dans la carte graphique) ;
//  - une vidéo est envoyée par le lecteur dans une autre texture spéciale (SurfaceTexture), image par image,
//    toujours décodée par la puce vidéo du téléphone (aussi économe que le service vidéo) ;
//  - à chaque fois, la carte graphique peint la texture sur tout l'écran, « en couvrant » (sans déformer,
//    ce qui dépasse est coupé, centré).
//
// LE MOMENT DU CHANGEMENT : quand le fond est CACHÉ depuis 0,4 s (on est dans une app, ou l'écran est éteint),
// on passe au suivant. Il est donc déjà prêt, et affiché, quand on revient : on ne voit jamais le changement se
// faire. (Les 0,4 s évitent de changer pour un fond caché une fraction de seconde, par exemple un menu.)
//
// LE SON (17/11) : comme le fond « Vidéo », une vidéo est muette, sauf si l'utilisateur permet le son des fonds
// (sans son / avec écouteurs / toujours, voir SonFond.kt) : on joue alors sa version avec le son.
// En plus, chaque vidéo du dossier peut avoir son son coupé à part (DossierFond.estMuette) : elle reste muette.
//
// LES ÉCONOMIES : rien ne tourne quand le fond est caché (la vidéo est en pause ; une photo n'est dessinée
// qu'une fois) ; un seul fichier est chargé à la fois.
class FondDossierService : WallpaperService() {

    override fun onCreateEngine(): Engine = Moteur()

    inner class Moteur : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val ctx: Context = this@FondDossierService
        // Tout le dessin se fait sur un fil à part (« fond-dossier ») : OpenGL veut toujours le même fil,
        // et l'écran d'accueil n'est jamais ralenti.
        private val fil = HandlerThread("fond-dossier").apply { start() }
        private val gl = Handler(fil.looper)
        private val principal = Handler(Looper.getMainLooper())
        private var rendu: RenduDossier? = null       // lu et écrit seulement sur « fil »
        private var visible = false
        private val gardien = GardienBatterie(ctx) { quandLeGardienChange() }
        // (17/11) le son permis ou non (écouteurs branchés, choix de l'utilisateur) : on prévient le dessin
        private val son = SonFond.Veille(ctx) { permis -> gl.post { rendu?.son(permis) } }
        private val changer = Runnable { if (!isPreview) DossierFond.avancer(ctx) }

        // SECOURS (comme les autres services) : si Android ne dit jamais si le fond est visible, on suit l'écran.
        private var signalRecu = false
        private val secours = Runnable { if (!signalRecu) { visible = gardien.ecranAllume; envoyerVisible() } }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            DossierFond.carnet(ctx).registerOnSharedPreferenceChangeListener(this)
            gardien.demarrer()
            son.demarrer()
            // le service démarre (téléphone rallumé après un arrêt, fond tout juste choisi…) : un nouveau fond
            if (!isPreview) DossierFond.avancer(ctx)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            val surface = holder.surface
            val vis = visible && !gardien.surchauffe
            val permis = son.permis
            gl.post {
                rendu?.liberer()
                rendu = try { RenduDossier(surface) { DossierFond.estMuette(ctx, it) } } catch (e: Exception) {
                    DiagFond.noter(ctx, "f_erreur" to ((e.message ?: e.javaClass.simpleName)).take(80))
                    null
                }
                rendu?.charger(DossierFond.courant(ctx), vis, permis)
            }
            principal.postDelayed(secours, 1500L)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            gl.post { rendu?.taille(w, h) }
        }

        // Android demande une image tout de suite (le fond réapparaît) : on la dessine et on attend qu'elle soit faite.
        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            attendre { rendu?.dessiner() }
        }

        // La surface va disparaître : il faut TOUT lâcher avant de rendre la main (sinon Android se fâche).
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            attendre { rendu?.liberer(); rendu = null }
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            principal.removeCallbacks(secours)
            principal.removeCallbacks(changer)
            DossierFond.carnet(ctx).unregisterOnSharedPreferenceChangeListener(this)
            gardien.arreter()
            son.arreter()
            attendre { rendu?.liberer(); rendu = null }
            fil.quitSafely()
            super.onDestroy()
        }

        // Fait un travail sur le fil du dessin, et attend qu'il soit fini (2 s au plus).
        private fun attendre(travail: () -> Unit) {
            val fini = CountDownLatch(1)
            val envoye = gl.post {
                try { travail() } catch (_: Exception) {} finally { fini.countDown() }
            }
            if (!envoye) return
            try { fini.await(2, TimeUnit.SECONDS) } catch (_: InterruptedException) {}
        }

        override fun onVisibilityChanged(v: Boolean) {
            signalRecu = true
            visible = v
            envoyerVisible()
        }

        private fun quandLeGardienChange() {
            if (!signalRecu) visible = gardien.ecranAllume
            envoyerVisible()
        }

        // Le fond devient visible ou caché. Caché : dans 0,4 s, on passe au suivant (annulé s'il réapparaît avant).
        private fun envoyerVisible() {
            val vis = visible && !gardien.surchauffe          // téléphone brûlant : la vidéo s'arrête
            DiagFond.noter(ctx, "f_vis" to visible, "f_vis_ms" to System.currentTimeMillis(), "f_signal" to signalRecu)
            principal.removeCallbacks(changer)
            if (!visible) principal.postDelayed(changer, 400L)
            gl.post { rendu?.montrer(vis) }
        }

        // Le carnet a changé : un autre fond est « en cours » (ou la collection a changé) -> on l'affiche.
        override fun onSharedPreferenceChanged(p: SharedPreferences?, cle: String?) {
            // (« muettes » : le son d'une vidéo vient d'être coupé ou remis)
            if (cle != null && cle != DossierFond.COURANT && cle != "change" && cle != DossierFond.MUETTES) return
            val f = DossierFond.courant(ctx)
            val vis = visible && !gardien.surchauffe
            val permis = son.permis
            gl.post { rendu?.charger(f, vis, permis) }
        }
    }
}

// =============================================================================================
// LE DESSIN AVEC LA CARTE GRAPHIQUE. Tout ici se passe sur le fil « fond-dossier ».
// =============================================================================================
// « muette » : dit si le son d'une vidéo a été coupé à part dans le dossier (alors on joue toujours sa version muette).
class RenduDossier(surface: Surface, private val muette: (File) -> Boolean) {
    // EGL = le « branchement » entre OpenGL et l'écran : un affichage, un contexte (la mémoire de dessin
    // d'OpenGL) et une fenêtre (notre surface de fond d'écran).
    private val affichage: EGLDisplay
    private val contexte: EGLContext
    private val fenetre: EGLSurface
    private var largeur = 1
    private var hauteur = 1

    // Les 2 « programmes » de la carte graphique (de tout petits codes qu'elle exécute pour chaque point) :
    // un pour les photos, un pour les vidéos.
    private val progPhoto: Int
    private val progVideo: Int
    // Les 4 coins du rectangle qui couvre l'écran : pour chaque coin, sa place (x, y) et le point de l'image
    // qu'on y met (u, v).
    private val coins: FloatBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    // Le fond affiché
    private var fichier: File? = null
    private var estVideo = false
    private var largeurMedia = 1
    private var hauteurMedia = 1
    private var texPhoto = 0                          // la texture de la photo (0 = aucune)
    private var texVideo = 0                          // la texture de la vidéo
    private var surfaceTex: SurfaceTexture? = null    // où le lecteur dépose les images de la vidéo
    private var surfaceVideo: Surface? = null
    private var lecteur: MediaPlayer? = null
    private var pret = false                          // la vidéo est-elle prête à jouer ?
    private var imageVideoRecue = false               // a-t-on déjà reçu une image de la vidéo ?
    private var visible = false
    private var sonPermis = false                     // (17/11) le son des fonds est-il permis en ce moment ?
    private var avecSon = false                       // joue-t-on la version AVEC le son de la vidéo ?
    private var joue: String? = null                  // le fichier vraiment joué (version muette ou avec le son)
    private var reprise = 0                           // où reprendre la vidéo après un changement de version (ms)
    private val fil = Handler(Looper.myLooper() ?: Looper.getMainLooper())
    private val matrice = FloatArray(16)
    private val identite = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    init {
        affichage = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (affichage == EGL14.EGL_NO_DISPLAY) throw IllegalStateException("EGL : pas d'affichage")
        val version = IntArray(2)
        if (!EGL14.eglInitialize(affichage, version, 0, version, 1)) throw IllegalStateException("EGL : refusé")
        // on veut : rouge, vert, bleu sur 8 bits chacun, OpenGL ES 2, pour dessiner dans une fenêtre
        val voulu = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE)
        val configs = arrayOfNulls<EGLConfig>(1)
        val nombre = IntArray(1)
        if (!EGL14.eglChooseConfig(affichage, voulu, 0, configs, 0, 1, nombre, 0) || nombre[0] == 0)
            throw IllegalStateException("EGL : pas de configuration")
        val config = configs[0] ?: throw IllegalStateException("EGL : pas de configuration")
        contexte = EGL14.eglCreateContext(affichage, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        if (contexte == EGL14.EGL_NO_CONTEXT) throw IllegalStateException("EGL : pas de contexte")
        fenetre = EGL14.eglCreateWindowSurface(affichage, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        if (fenetre == EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroyContext(affichage, contexte)
            throw IllegalStateException("EGL : surface refusée")
        }
        EGL14.eglMakeCurrent(affichage, fenetre, fenetre, contexte)
        progPhoto = programme(SOMMETS, PEINTURE_PHOTO)
        progVideo = programme(SOMMETS, PEINTURE_VIDEO)
    }

    fun taille(w: Int, h: Int) {
        largeur = max(1, w); hauteur = max(1, h)
        dessiner()
    }

    // AFFICHER UN FOND (une photo ou une vidéo). « vis » : le fond est-il visible en ce moment ?
    // « permis » : le son des fonds est-il permis ?
    fun charger(f: File?, vis: Boolean, permis: Boolean) {
        visible = vis
        sonPermis = permis
        // c'est déjà lui : rien à recharger
        if (f != null && f == fichier && (texPhoto != 0 || lecteur != null)) {
            if (estVideo && aJouer(f, permis).path != joue) son(permis) else montrer(vis)
            return
        }
        vider()
        fichier = f
        if (f != null && f.exists()) {
            estVideo = DossierFond.estVideo(f)
            if (estVideo) ouvrirVideo(f) else ouvrirPhoto(f)
        }
        dessiner()
    }

    // Le fond devient visible : la vidéo joue. Caché : elle se met en pause.
    fun montrer(v: Boolean) {
        visible = v
        val mp = lecteur ?: return
        if (!pret) return
        try {
            if (v && avecSon) mp.setVolume(1f, 1f)              // le son ne sort que quand le fond est visible
            if (v && !mp.isPlaying) mp.start() else if (!v && mp.isPlaying) mp.pause()
        } catch (_: Exception) {}
    }

    // (17/11) Le son vient d'être permis ou interdit. Si c'est une vidéo et que ça change la version à jouer,
    // on la recharge, à l'endroit où on en était.
    fun son(permis: Boolean) {
        sonPermis = permis
        val f = fichier ?: return
        if (!estVideo || aJouer(f, permis).path == joue) return
        reprise = try { lecteur?.currentPosition ?: 0 } catch (_: Exception) { 0 }
        vider()
        fichier = f
        estVideo = true
        ouvrirVideo(f)
        dessiner()
    }

    // La version à jouer : avec le son seulement si le son est permis ET que cette vidéo n'est pas coupée.
    private fun aJouer(f: File, permis: Boolean): File = SonFond.fichierAJouer(f, permis && !muette(f))

    // UNE PHOTO : on la charge (réduite si besoin), on la range dans une texture, puis on oublie l'image.
    private fun ouvrirPhoto(f: File) {
        val maxi = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxi, 0)
        val limite = if (maxi[0] > 0) maxi[0] else 2048         // la plus grande texture que la carte accepte
        val cote = min(max(largeur, hauteur).coerceAtLeast(1080), limite)
        var b: Bitmap = PeintreFond.chargerImage(f.path, cote) ?: return
        if (max(b.width, b.height) > limite) {
            val k = limite.toFloat() / max(b.width, b.height)
            val petite = Bitmap.createScaledBitmap(b, max(1, (b.width * k).toInt()), max(1, (b.height * k).toInt()), true)
            if (petite != b) b.recycle()
            b = petite
        }
        texPhoto = nouvelleTexture(GLES20.GL_TEXTURE_2D)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
        largeurMedia = b.width; hauteurMedia = b.height
        b.recycle()                                              // l'image est dans la carte graphique : on libère
    }

    // UNE VIDÉO : le lecteur d'Android la décode et dépose chaque image dans notre texture spéciale.
    private fun ouvrirVideo(f: File) {
        // sa taille, et son sens : une vidéo filmée en portrait est souvent rangée « couchée » avec une rotation
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(f.path)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (w > 0 && h > 0) {
                if (rot == 90 || rot == 270) { largeurMedia = h; hauteurMedia = w } else { largeurMedia = w; hauteurMedia = h }
            }
        } catch (_: Exception) {
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
        texVideo = nouvelleTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        val st = SurfaceTexture(texVideo)
        // à chaque nouvelle image de la vidéo, on est prévenu ici (sur notre fil)
        st.setOnFrameAvailableListener(SurfaceTexture.OnFrameAvailableListener { imageVideo(it) }, fil)
        surfaceTex = st
        val sv = Surface(st)
        surfaceVideo = sv
        // la version muette, ou celle avec le son si le son est permis (et qu'elle existe)
        val source = aJouer(f, sonPermis)
        avecSon = source.path != f.path
        joue = source.path
        val mp = MediaPlayer()
        lecteur = mp
        try {
            mp.setDataSource(source.path)
            mp.setSurface(sv)
            mp.isLooping = true                                  // en boucle
            // muette au départ ; le son (s'il est permis) n'est mis que quand le fond est visible (voir montrer)
            mp.setVolume(0f, 0f)
            // Prête : on la lance, même si le fond est caché. Dès la 1re image reçue, on la remet en pause s'il
            // est caché (voir imageVideo) : comme ça, la 1re image est déjà à l'écran quand on revient.
            mp.setOnPreparedListener { lu ->
                if (lu === lecteur) {
                    pret = true
                    try {
                        if (reprise > 0) lu.seekTo(reprise)           // après un changement de son : on reprend là
                        reprise = 0
                        if (visible && avecSon) lu.setVolume(1f, 1f)
                        lu.start()
                    } catch (_: Exception) {}
                }
            }
            mp.setOnErrorListener { _, _, _ -> true }
            mp.prepareAsync()
        } catch (_: Exception) {
            vider()                                              // vidéo illisible : écran noir
        }
    }

    // Une nouvelle image de la vidéo est arrivée : on la prend et on la dessine.
    private fun imageVideo(st: SurfaceTexture) {
        if (st !== surfaceTex) return                            // une image d'une ancienne vidéo : on l'ignore
        try { st.updateTexImage() } catch (_: Exception) { return }
        imageVideoRecue = true
        dessiner()
        if (!visible) lecteur?.let { try { if (it.isPlaying) it.pause() } catch (_: Exception) {} }
    }

    private fun nouvelleTexture(sorte: Int): Int {
        val id = IntArray(1)
        GLES20.glGenTextures(1, id, 0)
        GLES20.glBindTexture(sorte, id[0])
        // lissage quand l'image est agrandie ou réduite, et pas de répétition sur les bords
        GLES20.glTexParameteri(sorte, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(sorte, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(sorte, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(sorte, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return id[0]
    }

    // DESSINE UNE IMAGE : du noir, puis la photo ou l'image de la vidéo par-dessus, et on l'envoie à l'écran.
    fun dessiner() {
        GLES20.glViewport(0, 0, largeur, hauteur)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val st = surfaceTex
        if (estVideo && imageVideoRecue && st != null && texVideo != 0) {
            st.getTransformMatrix(matrice)      // le lecteur dit comment tourner / recadrer son image
            peindre(progVideo, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texVideo, matrice, true)
        } else if (!estVideo && texPhoto != 0) {
            peindre(progPhoto, GLES20.GL_TEXTURE_2D, texPhoto, identite, false)
        }
        EGL14.eglSwapBuffers(affichage, fenetre)
    }

    // Peint la texture sur tout l'écran en « couvrant » : on ne prend que la partie centrale de l'image qui a la
    // même forme que l'écran (une photo plus large que l'écran perd un peu de ses côtés, par exemple).
    private fun peindre(prog: Int, sorte: Int, tex: Int, m: FloatArray, video: Boolean) {
        val formeEcran = largeur.toFloat() / hauteur
        val formeMedia = largeurMedia.toFloat() / hauteurMedia
        var u0 = 0f; var u1 = 1f; var v0 = 0f; var v1 = 1f
        if (formeMedia > formeEcran) {
            val k = formeEcran / formeMedia; u0 = (1 - k) / 2; u1 = 1 - u0
        } else {
            val k = formeMedia / formeEcran; v0 = (1 - k) / 2; v1 = 1 - v0
        }
        // Une photo est rangée « de haut en bas » (v = 0 en haut) ; la vidéo « de bas en haut » (v = 0 en bas).
        val bas = if (video) v0 else v1
        val haut = if (video) v1 else v0
        coins.clear()
        coins.put(floatArrayOf(
            -1f, -1f, u0, bas,       // en bas à gauche
            1f, -1f, u1, bas,        // en bas à droite
            -1f, 1f, u0, haut,       // en haut à gauche
            1f, 1f, u1, haut))       // en haut à droite
        GLES20.glUseProgram(prog)
        val aPos = GLES20.glGetAttribLocation(prog, "aPos")
        val aTex = GLES20.glGetAttribLocation(prog, "aTex")
        coins.position(0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, coins)
        coins.position(2)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 16, coins)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(prog, "uTexM"), 1, false, m, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(sorte, tex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "uImg"), 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    // Lâche le fond affiché (lecteur, textures).
    private fun vider() {
        lecteur?.let { try { it.release() } catch (_: Exception) {} }
        lecteur = null
        pret = false
        surfaceVideo?.release(); surfaceVideo = null
        surfaceTex?.release(); surfaceTex = null
        if (texVideo != 0) { GLES20.glDeleteTextures(1, intArrayOf(texVideo), 0); texVideo = 0 }
        if (texPhoto != 0) { GLES20.glDeleteTextures(1, intArrayOf(texPhoto), 0); texPhoto = 0 }
        imageVideoRecue = false
        estVideo = false
        avecSon = false
        joue = null
        fichier = null
    }

    // Lâche TOUT (la surface va disparaître). On ne « termine » pas l'affichage EGL : il est partagé avec les
    // autres moteurs (accueil et verrouillage), le fermer les casserait.
    fun liberer() {
        vider()
        GLES20.glDeleteProgram(progPhoto)
        GLES20.glDeleteProgram(progVideo)
        EGL14.eglMakeCurrent(affichage, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(affichage, fenetre)
        EGL14.eglDestroyContext(affichage, contexte)
    }

    // Assemble un programme pour la carte graphique à partir de ses 2 parties (les coins, puis chaque point).
    private fun programme(sommets: String, peinture: String): Int {
        fun partie(sorte: Int, code: String): Int {
            val id = GLES20.glCreateShader(sorte)
            GLES20.glShaderSource(id, code)
            GLES20.glCompileShader(id)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) throw IllegalStateException("GL : " + GLES20.glGetShaderInfoLog(id).take(60))
            return id
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, partie(GLES20.GL_VERTEX_SHADER, sommets))
        GLES20.glAttachShader(p, partie(GLES20.GL_FRAGMENT_SHADER, peinture))
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) throw IllegalStateException("GL : " + GLES20.glGetProgramInfoLog(p).take(60))
        return p
    }

    private companion object {
        // Pour chaque coin : sa place, et le point de l'image à y mettre (transformé par la matrice du lecteur
        // pour une vidéo ; pour une photo, la matrice ne change rien).
        val SOMMETS = """
            attribute vec2 aPos;
            attribute vec2 aTex;
            uniform mat4 uTexM;
            varying vec2 vTex;
            void main() {
                gl_Position = vec4(aPos, 0.0, 1.0);
                vTex = (uTexM * vec4(aTex, 0.0, 1.0)).xy;
            }
        """.trimIndent()

        // Pour chaque point de l'écran : la couleur de la photo à cet endroit.
        val PEINTURE_PHOTO = """
            precision mediump float;
            varying vec2 vTex;
            uniform sampler2D uImg;
            void main() {
                gl_FragColor = vec4(texture2D(uImg, vTex).rgb, 1.0);
            }
        """.trimIndent()

        // Pareil pour la vidéo, qui vient d'une texture « externe » (celle du lecteur).
        val PEINTURE_VIDEO = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTex;
            uniform samplerExternalOES uImg;
            void main() {
                gl_FragColor = vec4(texture2D(uImg, vTex).rgb, 1.0);
            }
        """.trimIndent()
    }
}
