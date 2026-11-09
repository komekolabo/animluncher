package com.will.animlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

// ======================================================================================
//  L'ICÔNE VIVANTE (essai) : un petit logo animé posé EXACTEMENT sur l'icône d'AnimLauncher,
//  sur l'écran d'accueil. Android ne permet pas d'animer une icône : on triche en posant
//  par-dessus une petite fenêtre transparente qui dessine le logo en mouvement.
//
//  1) OÙ EST L'ICÔNE ? Quand on ouvre AnimLauncher depuis une page de l'écran d'accueil, Samsung
//     transmet le rectangle de l'icône touchée (voir MainActivity.noterPositionIcone). On le garde
//     dans un petit carnet (IconeAccueil). Depuis le dock, Samsung ne le transmet pas (testé).
//  2) QUAND L'AFFICHER ? Seulement quand l'écran d'accueil est au premier plan. Dès qu'une autre
//     fenêtre arrive (une app, les apps récentes…) ou qu'on touche quelque chose, on la cache.
//  3) QUELLE PAGE ? On ne lit pas l'écran (version Store). Mais l'écran d'accueil envoie des
//     signaux de « défilement » quand on change de page. ESSAI : on les note dans le journal pour
//     voir ce qu'ils contiennent, et on s'en sert pour n'afficher le logo que sur la bonne page.
//  La fenêtre laisse passer les appuis : toucher l'icône ouvre bien AnimLauncher.
// ======================================================================================

// Le petit carnet : la position de l'icône, et l'interrupteur de l'essai.
object IconeAccueil {
    private const val FICHIER = "icone_accueil"
    private fun carnet(c: Context) = c.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    // Le dernier « défilement » connu de l'écran d'accueil (rempli par le service, lu par
    // MainActivity au moment où elle reçoit la position : c'est la page où se trouve l'icône).
    // Int.MIN_VALUE = « inconnu ».
    @Volatile var dernierScrollX = Int.MIN_VALUE
    @Volatile var dernierIndex = Int.MIN_VALUE
    // Est-on sur la page de l'icône ? true juste après avoir ouvert l'app depuis son icône (on était
    // forcément sur sa page), puis mis à jour à chaque changement de page. null = on ne sait pas.
    @Volatile var surPageIcone: Boolean? = null
    // true = la page de l'icône reste à confirmer au prochain changement de page (voir finDefilement).
    @Volatile var pageAConfirmer = false

    fun actif(c: Context) = carnet(c).getBoolean("actif", true)
    fun definirActif(c: Context, v: Boolean) { carnet(c).edit().putBoolean("actif", v).apply() }

    // On retient le rectangle de l'icône, la largeur de l'écran à ce moment-là (sur le Flip,
    // le petit écran extérieur n'a pas la même taille), et la page affichée.
    fun retenir(c: Context, r: Rect, largeurEcran: Int) {
        carnet(c).edit()
            .putString("rect", r.flattenToString())
            .putInt("largeur", largeurEcran)
            // On NE note PAS la page tout de suite : la dernière page connue peut être périmée (le
            // bouton Accueil saute sur la page principale sans prévenir). Vu le 09/11 : le logo de
            // l'icône de la page d'accueil s'affichait sur la page 3. La page sera confirmée au
            // prochain changement de page (début du geste = la page de l'icône).
            .putInt("page_scroll", Int.MIN_VALUE)
            .putInt("page_index", dernierIndex)
            .apply()
        surPageIcone = true
        pageAConfirmer = true
    }
    // La page de l'icône, découverte plus tard (au 1er changement de page après l'avoir touchée).
    fun definirPageScroll(c: Context, x: Int) { carnet(c).edit().putInt("page_scroll", x).apply() }
    fun rect(c: Context): Rect? = carnet(c).getString("rect", null)?.let { Rect.unflattenFromString(it) }
    fun largeur(c: Context) = carnet(c).getInt("largeur", 0)
    fun pageScroll(c: Context) = carnet(c).getInt("page_scroll", Int.MIN_VALUE)
    fun pageIndex(c: Context) = carnet(c).getInt("page_index", Int.MIN_VALUE)
}

// Le gestionnaire de la petite fenêtre (utilisé par le service AppWatcherService).
class IconeVivante(private val service: AccessibilityService) {

    private val minuteur = Handler(Looper.getMainLooper())
    private val gestionnaire = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var vue: VueIconeVivante? = null
    private var reglages: WindowManager.LayoutParams? = null
    private var surAccueil = false          // l'écran d'accueil est-il au premier plan ?
    private var defileA = 0L                // dernier signal de défilement (pour attendre la fin)
    private var dernierDefilement: AccessibilityEvent? = null
    private var notesDefilement = 0         // pour ne pas inonder le journal
    private var debutGeste = Int.MIN_VALUE  // scrollX au début du geste (= la page qu'on quitte)
    private var signauxGeste = 0            // nombre de signaux reçus pendant le geste (diagnostic)
    // DIAGNOSTIC : la dernière raison pour laquelle le logo n'est PAS affiché (on ne l'écrit dans
    // le journal que quand elle change), et les noms d'écrans de l'accueil déjà vus.
    private var derniereRaison: String? = null
    private val ecransVus = HashSet<String>()

    // Une fenêtre passe au premier plan.
    fun surFenetre(app: String?, classe: String, accueil: String?) {
        // NOS PROPRES PETITES FENÊTRES (cette icône vivante, le rideau des animations) envoient elles
        // aussi un signal « fenêtre » en apparaissant (classe « View » ou « FrameLayout »). Avant, on le
        // prenait pour une autre app : le logo se retirait 20 ms après être apparu (vu dans le journal).
        if (app == service.packageName && (classe == "View" || classe == "FrameLayout")) return
        // on note une fois chaque nom d'écran de l'accueil (pour comprendre ce que Samsung envoie)
        if (app == accueil && ecransVus.size < 20 && ecransVus.add(classe))
            noter("  (icône vivante : écran de l'accueil « $classe »)")
        val avant = surAccueil
        surAccueil = app == accueil && classe == "Launcher"
        if (!surAccueil) { cacher("écran « $classe » de $app"); return }
        if (!avant) minuteur.postDelayed({ evaluer("retour sur l'accueil") }, 300)
    }

    // On a touché quelque chose : on cache tout de suite (l'animation d'ouverture d'une app,
    // qui démarre au même moment, ne doit pas passer sous notre petite fenêtre).
    // Si on est toujours sur l'accueil 1,2 s plus tard (ex. un dossier ouvert puis refermé),
    // on la remet.
    fun surClic() {
        cacher("appui")
        minuteur.postDelayed({ evaluer("après un appui sur l'accueil") }, 1200)
    }

    // L'écran d'accueil défile (changement de page, tiroir d'apps…).
    fun surDefilement(event: AccessibilityEvent, accueil: String?) {
        if (event.packageName?.toString() != accueil) return
        cacher("défilement")
        // Le 1er signal d'un geste (rien reçu depuis 0,45 s) : sa position est presque celle de la
        // page qu'on quitte. On la garde pour retrouver la page de l'icône (voir finDefilement).
        val classeDuSignal = event.className?.toString()?.substringAfterLast('.') ?: "?"
        if (SystemClock.uptimeMillis() - defileA > 440) {
            signauxGeste = 0
            if (classeDuSignal != "RecyclerView" && surAccueil) debutGeste = event.scrollX
        }
        signauxGeste++
        // On garde une copie des chiffres (Android réutilise l'objet « event » après nous).
        dernierDefilement = AccessibilityEvent.obtain(event)
        defileA = SystemClock.uptimeMillis()
        // On attend que le geste soit fini (plus de signal pendant 0,45 s) avant de décider.
        minuteur.postDelayed({
            if (SystemClock.uptimeMillis() - defileA >= 440) finDefilement()
        }, 450)
    }

    private fun finDefilement() {
        val e = dernierDefilement ?: return
        val classe = e.className?.toString()?.substringAfterLast('.') ?: "?"
        // LES PAGES (vu dans le journal le 09/11) : quand on change de page, l'écran d'accueil Samsung
        // envoie un défilement « ViewGroup » dont scrollX vaut 0, 1039, 2078… (une page = 1039 pixels).
        // Les listes (apps récentes, tiroir d'apps) envoient des « RecyclerView » avec scrollX 0 : on les
        // ignore, sinon on croirait être revenu sur la 1re page. On ne retient que si l'accueil est affiché.
        if (surAccueil && classe != "RecyclerView") {
            if (e.scrollX >= 0) IconeAccueil.dernierScrollX = e.scrollX
            if (e.toIndex >= 0) IconeAccueil.dernierIndex = e.toIndex
            // 1er changement de page depuis qu'on a touché l'icône : on vient de QUITTER sa page (on y
            // était depuis l'ouverture de l'app par son icône) : sa position est celle du début du geste.
            // (Si le geste n'a envoyé qu'un seul signal, le début vaut la fin : on ne peut rien en tirer.)
            if (IconeAccueil.pageAConfirmer) {
                IconeAccueil.pageAConfirmer = false
                if (IconeAccueil.surPageIcone == true && debutGeste >= 0 && debutGeste != e.scrollX) {
                    IconeAccueil.definirPageScroll(service, debutGeste)
                    noter("  (icône vivante : page de l'icône trouvée, scrollX $debutGeste)")
                } else {
                    noter("  (icône vivante : page de l'icône pas trouvée, début $debutGeste, fin ${e.scrollX})")
                }
            }
            // On sait maintenant si la nouvelle page est celle de l'icône (ou on ne sait plus).
            val refApres = IconeAccueil.pageScroll(service)
            IconeAccueil.surPageIcone = if (refApres != Int.MIN_VALUE && e.scrollX >= 0)
                abs(e.scrollX - refApres) < service.resources.displayMetrics.widthPixels / 2 else null
        }
        if (notesDefilement < 40) {
            notesDefilement++
            noter("  [défilement] $classe : scrollX $debutGeste -> ${e.scrollX} ($signauxGeste signaux), " +
                "scrollY ${e.scrollY}, index ${e.fromIndex}->${e.toIndex} sur ${e.itemCount}")
        }
        evaluer("fin du défilement")
    }

    // Doit-on afficher le logo vivant maintenant ?
    private fun evaluer(raison: String) {
        if (!surAccueil) return
        if (!Reglages.actif(service)) return pas("animations coupées (interrupteur général)")
        if (!IconeAccueil.actif(service)) return pas("interrupteur de l'essai coupé")
        val r = IconeAccueil.rect(service)
            ?: return pas("position de l'icône inconnue : ferme AnimLauncher, puis ouvre-la depuis son icône sur une page de l'accueil")
        val ecran = service.resources.displayMetrics
        if (ecran.widthPixels != IconeAccueil.largeur(service)) {                          // autre écran
            cacher("autre écran"); return pas("écran de ${ecran.widthPixels} de large au lieu de ${IconeAccueil.largeur(service)}")
        }
        when (bonnePage()) {
            false -> { cacher("autre page"); return pas("pas sur la page de l'icône") }
            null -> { cacher("page inconnue"); return pas("page affichée inconnue : touche l'icône d'AnimLauncher sur sa page") }
            true -> {}
        }
        derniereRaison = null
        montrer(r, raison)
    }

    // Écrit dans le journal pourquoi le logo n'est pas affiché (seulement si la raison change).
    private fun pas(pourquoi: String) {
        if (pourquoi == derniereRaison) return
        derniereRaison = pourquoi
        noter("  (icône vivante pas affichée : $pourquoi)")
    }

    // Est-on sur la page de l'icône ? On compare la page actuelle à celle notée à l'ouverture.
    // true = oui, false = non, null = on ne sait pas. Dans le doute, on n'affiche RIEN (sinon le logo
    // apparaissait au milieu d'une autre page, là où l'icône se trouve sur SA page).
    //  - si on connaît la position de la page de l'icône ET celle de la page affichée : on compare ;
    //  - sinon, on suit « surPageIcone » : vrai juste après avoir touché l'icône (au 2e essai, on
    //    exigeait d'abord un changement de page, et rien ne s'affichait).
    private fun bonnePage(): Boolean? {
        val refX = IconeAccueil.pageScroll(service)
        val x = IconeAccueil.dernierScrollX
        if (refX != Int.MIN_VALUE && x != Int.MIN_VALUE)
            return abs(x - refX) < service.resources.displayMetrics.widthPixels / 2
        return IconeAccueil.surPageIcone
    }

    private fun montrer(r: Rect, raison: String) {
        // Le rectangle reçu contient l'icône ET son nom écrit dessous. On vise le carré de l'icône :
        // centré en largeur, et son centre à 40 % de la hauteur. On le fait un tout petit peu plus grand
        // que la vraie icône (72 % de la largeur de la case) pour bien la recouvrir. (78 % : trop grand,
        // vu sur la capture du 09/11 : environ 194 pixels pour une icône d'environ 172.)
        val cote = (r.width() * 0.72f).toInt()
        val cx = r.centerX()
        val cy = r.top + (r.height() * 0.40f).toInt()
        val x = cx - cote / 2
        val y = cy - cote / 2
        val dejaLa = vue != null
        if (vue == null) {
            val v = VueIconeVivante(service)
            val p = WindowManager.LayoutParams(
                cote, cote,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                // laisse passer les appuis, ne prend pas le clavier, coordonnées de tout l'écran
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = x; this.y = y
                if (Build.VERSION.SDK_INT >= 30) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    setFitInsetsTypes(0)
                }
            }
            try {
                gestionnaire.addView(v, p)
                vue = v; reglages = p
            } catch (e: Exception) {
                noter("ERREUR icône vivante : ${e.message}")
                return
            }
        } else {
            val p = reglages ?: return
            if (p.x != x || p.y != y || p.width != cote) {
                p.x = x; p.y = y; p.width = cote; p.height = cote
                try { gestionnaire.updateViewLayout(vue, p) } catch (_: Exception) {}
            }
            vue?.visibility = View.VISIBLE
        }
        if (!dejaLa) noter("  (icône vivante affichée en $x, $y, côté $cote : $raison)")
    }

    // On retire la fenêtre (plutôt que la cacher) : rien ne tourne quand elle n'est pas là.
    // « pourquoi » : noté dans le journal (les 15 premières fois), pour comprendre ce qui la retire.
    private var notesRetrait = 0
    fun cacher(pourquoi: String = "rotation ou changement d'écran") {
        val v = vue ?: return
        try { gestionnaire.removeView(v) } catch (_: Exception) {}
        vue = null; reglages = null
        if (notesRetrait < 15) { notesRetrait++; noter("  (icône vivante retirée : $pourquoi)") }
    }

    private fun noter(texte: String) {
        Journal.noter(service, texte)
        Journal.programmerEnvoi(service)
    }
}

// LE DESSIN : le logo « Étincelle halo » qui lévite doucement, et dont le triangle fait
// de temps en temps un tiers de tour (120 degrés : il retombe pile sur la même forme).
class VueIconeVivante(context: Context) : View(context) {

    private val debut = SystemClock.uptimeMillis()
    private val fond = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.logo_fond) }
    private val ombre = Paint(Paint.ANTI_ALIAS_FLAG)
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        color = Color.rgb(236, 239, 247)
    }
    private val etoile = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(-10.5f, -10.5f, 10.5f, 10.5f,
            intArrayOf(Color.rgb(76, 141, 246), Color.rgb(52, 199, 123), Color.rgb(247, 201, 72), Color.rgb(240, 100, 90)),
            floatArrayOf(0f, 0.38f, 0.66f, 1f), Shader.TileMode.CLAMP)
    }
    // les trois coins (même dessin que l'icône, grille 108 x 108)
    private val coins = Path().apply {
        moveTo(44.5f, 45.2f); lineTo(54f, 29f); lineTo(63.5f, 45.2f)
        moveTo(35.5f, 60.8f); lineTo(26f, 77f); lineTo(45f, 77f)
        moveTo(63f, 77f); lineTo(82f, 77f); lineTo(72.5f, 60.8f)
    }
    private val formeEtoile = Path().apply {
        moveTo(0f, -10.5f); lineTo(2.4f, -2.4f); lineTo(10.5f, 0f); lineTo(2.4f, 2.4f)
        lineTo(0f, 10.5f); lineTo(-2.4f, 2.4f); lineTo(-10.5f, 0f); lineTo(-2.4f, -2.4f); close()
    }
    private val carre = RectF()
    private val ellipse = RectF()

    override fun onDraw(c: Canvas) {
        val t = (SystemClock.uptimeMillis() - debut) / 1000f
        val cote = min(width, height).toFloat()

        // 1) le fond : un carré aux coins très arrondis, de la couleur de l'icône, qui recouvre
        //    la vraie icône (immobile) en dessous.
        carre.set(0f, 0f, cote, cote)
        c.drawRoundRect(carre, cote * 0.32f, cote * 0.32f, fond)

        // 2) on passe dans la grille 108 x 108 de l'icône. La partie visible d'une icône Android
        //    fait 72 de large ; notre fenêtre est ~5 % plus grande que l'icône, d'où 72 x 1,05.
        c.save()
        val k = cote / (72f * 1.05f)
        c.translate(cote / 2f, cote / 2f)
        c.scale(k, k)
        c.translate(-54f, -54f)
        c.scale(0.8f, 0.8f, 54f, 54f)          // même réduction que l'icône

        // 3) LA LÉVITATION : le logo monte et descend de 4,5 (sur 108), en 3 secondes.
        //    (Au 1er essai, 2 était trop discret : à la taille d'une icône, ça ne faisait que 3 pixels.)
        val vol = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() / 3f)     // 0 = en bas, 1 = en haut
        val levee = -4.5f * vol

        // son ombre au sol : plus petite et plus pâle quand le logo est haut
        val largeurOmbre = 20f - 3f * vol
        ellipse.set(54f - largeurOmbre, 86f, 54f + largeurOmbre, 90f)
        ombre.shader = RadialGradient(54f, 88f, largeurOmbre,
            Color.argb((110 - 40 * vol).toInt(), 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawOval(ellipse, ombre)

        c.translate(0f, levee)

        // 4) le halo, qui respire au même rythme
        halo.shader = RadialGradient(54f, 61f, 16f,
            Color.argb((50 + 35 * vol).toInt(), 247, 201, 72), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(54f, 61f, 16f, halo)

        // 5) LE TOUR : toutes les 4,5 secondes, le triangle fait un tiers de tour en 1,2 s, en
        //    démarrant et en freinant en douceur. Entre deux tours, il reste immobile.
        //    Le 1er tour attend 1,5 s : sinon il se jouait pendant l'animation de retour sur l'accueil
        //    de Samsung, et on ne le voyait pas.
        val cycle = 4.5f
        val tt = t + cycle - 1.5f
        val numero = floor(tt / cycle)
        val dansCycle = tt - numero * cycle
        val progres = min(1f, dansCycle / 1.2f)
        val doux = progres * progres * (3f - 2f * progres)          // départ et arrêt en douceur
        val angle = (numero * 120f + 120f * doux) % 360f
        c.save()
        c.rotate(angle, 54f, 61f)                                  // autour du centre du triangle
        c.drawPath(coins, trait)
        c.restore()

        // 6) l'étoile reste droite et scintille un peu
        val scintille = 0.92f + 0.12f * (0.5f + 0.5f * sin(t * 2f * PI.toFloat() / 2.4f))
        c.save()
        c.translate(54f, 61f)
        c.scale(scintille, scintille)
        c.drawPath(formeEtoile, etoile)
        c.restore()

        c.restore()
        postInvalidateOnAnimation()     // image suivante (s'arrête tout seul quand la fenêtre est retirée)
    }
}
