package com.will.animlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
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
import android.view.MotionEvent
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
    // Le tiroir d'apps est-il affiché ? (rempli par IconeVivante, lu ici dans retenir)
    @Volatile var dansTiroir = false
    // Le numéro de la page de l'accueil affichée (le X de « Page X sur N »), -1 = inconnu.
    @Volatile var pageActuelle = -1
    // Est-on SÛR d'être sur l'accueil (et pas dans le tiroir d'apps) ? Vrai seulement juste après un
    // « Page X sur N » de l'accueil. Faux dès qu'un doigt se pose ailleurs (on a peut-être ouvert le
    // tiroir, ce que Samsung ne signale pas toujours), ou au retour d'une app.
    @Volatile var accueilSur = false

    fun actif(c: Context) = carnet(c).getBoolean("actif", true)
    fun definirActif(c: Context, v: Boolean) { carnet(c).edit().putBoolean("actif", v).apply() }

    // On retient le rectangle de l'icône, la largeur de l'écran à ce moment-là (sur le Flip,
    // le petit écran extérieur n'a pas la même taille), et la page affichée.
    fun retenir(c: Context, r: Rect, largeurEcran: Int) {
        // Ouverte depuis le TIROIR D'APPS : Samsung transmet aussi la position, mais c'est celle de
        // l'icône dans le tiroir, pas sur l'accueil (vu le 09/11 : le logo flottait au milieu de la
        // page). On l'ignore.
        if (dansTiroir) {
            Journal.noter(c, "  (icône vivante : ouverte depuis le tiroir d'apps, position ignorée)")
            return
        }
        // SÉCURITÉ (vu le 09/11) : le tiroir d'apps s'ouvre parfois SANS aucun signal. L'app a alors pris
        // l'icône du tiroir pour celle de l'accueil, et le logo tournait au mauvais endroit. Donc si on
        // connaît déjà une position, on ne la remplace que si on est SÛR d'être sur l'accueil.
        // (Si on n'en connaît pas, ou si on l'a oubliée après un appui long : on prend la nouvelle.)
        val ancienne = rect(c)
        if (ancienne != null && ancienne != r && !accueilSur) {
            Journal.noter(c, "  (icône vivante : pas sûr d'être sur l'accueil, position ignorée)")
            return
        }
        carnet(c).edit()
            .putString("position_v2", r.flattenToString())
            .putInt("largeur", largeurEcran)
            // On NE note PAS la page tout de suite : la dernière page connue peut être périmée (le
            // bouton Accueil saute sur la page principale sans prévenir). Vu le 09/11 : le logo de
            // l'icône de la page d'accueil s'affichait sur la page 3. La page sera confirmée au
            // prochain changement de page (début du geste = la page de l'icône).
            .putInt("page_scroll", Int.MIN_VALUE)
            .putInt("page_index", dernierIndex)
            // LE NUMÉRO DE LA PAGE : on était forcément sur la page de l'icône, et le dernier
            // « Page X sur N » de l'accueil dit laquelle. Si on ne le connaît pas encore (service qui
            // vient de démarrer), on le trouvera au prochain changement de page (voir finDefilement).
            .putInt("page_x", pageActuelle)
            .apply()
        if (pageActuelle > 0) Journal.noter(c, "  (icône vivante : l'icône est sur la page n° $pageActuelle de l'accueil)")
        surPageIcone = true
        pageAConfirmer = pageActuelle <= 0
    }
    // La page de l'icône, découverte plus tard (au 1er changement de page après l'avoir touchée).
    fun definirPageScroll(c: Context, x: Int) { carnet(c).edit().putInt("page_scroll", x).apply() }
    // Le NUMÉRO de la page de l'icône (1, 2, 3…), -1 = inconnu. Plus sûr que la position de défilement.
    fun pageIcone(c: Context) = carnet(c).getInt("page_x", -1)
    fun definirPageIcone(c: Context, x: Int) { carnet(c).edit().putInt("page_x", x).apply() }
    // Le nombre de pages de l'écran d'accueil (appris grâce aux signaux « Page 2 sur 3 ») : le tiroir
    // d'apps a ses propres pages, en général pas le même nombre. -1 = inconnu.
    fun nbPages(c: Context) = carnet(c).getInt("nb_pages", -1)
    fun definirNbPages(c: Context, n: Int) { carnet(c).edit().putInt("nb_pages", n).apply() }
    // On oublie la position (l'icône a peut-être été déplacée) : il faudra retoucher l'icône.
    fun oublier(c: Context) { carnet(c).edit().remove("position_v2").apply(); surPageIcone = null }
    fun rect(c: Context): Rect? = carnet(c).getString("position_v2", null)?.let { Rect.unflattenFromString(it) }
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
    // LE TIROIR D'APPS (l'écran avec toutes les apps, ouvert en glissant vers le haut). Samsung ne
    // prévient PAS quand il s'ouvre (vu le 09/11 : le logo restait par-dessus). Mais quand on change de
    // page, l'écran envoie un signal « Page 2 sur 3 » : l'accueil et le tiroir n'ont pas le même nombre
    // de pages, et la page principale de l'accueil dit « Page par défaut ». On s'en sert pour savoir où
    // on est.
    private var dansTiroir: Boolean
        get() = IconeAccueil.dansTiroir
        set(v) { IconeAccueil.dansTiroir = v }
    private var selectionsRecues = 0        // signaux « Page X sur N » reçus (0 = ce téléphone n'en envoie pas)
    private var pageDuGeste = -1            // le N de « Page X sur N » reçu pendant le geste en cours (-1 = aucun)
    private var numeroDuGeste = -1          // le X de « Page X sur N » reçu pendant le geste en cours
    private var notesSelection = 0
    private var selectionA = 0L             // heure du dernier « Page X sur N »
    // Après un doigt posé AILLEURS que sur le logo, on ne sait pas ce qui se passe (ouverture du tiroir ?
    // simple appui ?) : le logo reste caché jusqu'à une preuve qu'on est sur la bonne page de l'accueil
    // (un vrai changement de page, ou un retour sur l'accueil depuis une app).
    private var enAttenteDePreuve = false

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
        if (!avant) {
            // On revient d'une app. ATTENTION : si l'app avait été ouverte depuis le tiroir d'apps,
            // Samsung revient DANS le tiroir (vu le 09/11 : le logo s'affichait par-dessus ChessBar).
            // On ne remet donc PAS « dansTiroir » à faux ici : c'est le prochain « Page X sur N » de
            // l'accueil (fermeture du tiroir, changement de page) qui le fera.
            enAttenteDePreuve = false
            IconeAccueil.accueilSur = false     // accueil ou tiroir ? on ne sait pas encore
            minuteur.postDelayed({ evaluer("retour sur l'accueil") }, 300)
        }
    }

    // On a touché quelque chose : on cache tout de suite (l'animation d'ouverture d'une app,
    // qui démarre au même moment, ne doit pas passer sous notre petite fenêtre).
    // Si on est toujours sur l'accueil 1,2 s plus tard (ex. un dossier ouvert puis refermé),
    // on la remet.
    fun surClic() {
        cacher("appui")
        minuteur.postDelayed({ evaluer("après un appui sur l'accueil") }, 800)
    }

    // Le signal « Page 2 sur 3 » (envoyé quand une page de l'accueil OU du tiroir s'affiche).
    // Il ne contient que ce petit texte de numéro de page.
    fun surSelection(event: AccessibilityEvent, accueil: String?) {
        if (event.packageName?.toString() != accueil) return
        val texte = event.text.joinToString(" ") + " " + (event.contentDescription ?: "")
        val m = Regex("""(\d+)\D+?(\d+)""").find(texte) ?: return
        val n = m.groupValues[2].toIntOrNull() ?: return
        val x = m.groupValues[1].toIntOrNull() ?: return
        selectionsRecues++
        selectionA = SystemClock.uptimeMillis()
        // « Page par défaut » : c'est forcément l'accueil, on apprend son nombre de pages.
        val defaut = texte.contains("défaut", true) || texte.contains("default", true)
        if (defaut) IconeAccueil.definirNbPages(service, n)
        val nbAccueil = IconeAccueil.nbPages(service)
        dansTiroir = when {
            defaut -> false
            nbAccueil > 0 -> n != nbAccueil
            else -> dansTiroir
        }
        pageDuGeste = if (dansTiroir) -1 else n
        numeroDuGeste = if (dansTiroir) -1 else x
        IconeAccueil.accueilSur = !dansTiroir
        if (!dansTiroir) {
            IconeAccueil.pageActuelle = x
            // On est sûr d'être sur l'accueil (ex. on vient de refermer le tiroir d'apps, qui ne
            // défile pas forcément) : on peut réafficher le logo, si c'est la bonne page.
            enAttenteDePreuve = false
            minuteur.postDelayed({ evaluer("page $x de l'accueil affichée") }, 350)
        }
        if (notesSelection < 25) {
            notesSelection++
            noter("  (icône vivante : « ${texte.trim()} » -> ${if (dansTiroir) "tiroir" else "accueil"}, accueil = $nbAccueil pages)")
        }
        if (dansTiroir) cacher("tiroir d'apps")
    }

    // L'écran d'accueil défile (changement de page, tiroir d'apps…).
    fun surDefilement(event: AccessibilityEvent, accueil: String?) {
        if (event.packageName?.toString() != accueil) return
        cacher("défilement")
        // Le 1er signal d'un geste (rien reçu depuis 0,3 s) : sa position est presque celle de la
        // page qu'on quitte. On la garde pour retrouver la page de l'icône (voir finDefilement).
        val classeDuSignal = event.className?.toString()?.substringAfterLast('.') ?: "?"
        if (SystemClock.uptimeMillis() - defileA > 290) {
            signauxGeste = 0
            // (sauf si le « Page X sur N » vient juste d'arriver : il appartient à ce geste-ci)
            if (SystemClock.uptimeMillis() - selectionA > 300) { pageDuGeste = -1; numeroDuGeste = -1 }
            if (classeDuSignal != "RecyclerView" && surAccueil) debutGeste = event.scrollX
        }
        signauxGeste++
        // On garde une copie des chiffres (Android réutilise l'objet « event » après nous).
        dernierDefilement = AccessibilityEvent.obtain(event)
        defileA = SystemClock.uptimeMillis()
        // On attend que le geste soit fini (plus de signal pendant 0,3 s) avant de décider.
        // (C'était 0,45 s : le logo revenait trop lentement.)
        minuteur.postDelayed({
            if (SystemClock.uptimeMillis() - defileA >= 290) finDefilement()
        }, 300)
    }

    private fun finDefilement() {
        val e = dernierDefilement ?: return
        val classe = e.className?.toString()?.substringAfterLast('.') ?: "?"
        // LES PAGES (vu dans le journal le 09/11) : quand on change de page, l'écran d'accueil Samsung
        // envoie un défilement « ViewGroup » dont scrollX vaut 0, 1039, 2078… (une page = 1039 pixels).
        // Les listes (apps récentes, tiroir d'apps) envoient des « RecyclerView » avec scrollX 0 : on les
        // ignore, sinon on croirait être revenu sur la 1re page. On ne retient que si l'accueil est affiché.
        // Un VRAI changement de page de l'accueil envoie aussi « Page X sur N ». Si ce téléphone envoie
        // ces signaux et qu'aucun n'est venu pendant ce geste, ce n'était pas un changement de page de
        // l'accueil (ouverture du tiroir, page du tiroir…) : on ne touche à rien et le logo reste caché.
        val vraiePage = selectionsRecues == 0 || pageDuGeste > 0
        if (surAccueil && classe != "RecyclerView" && !dansTiroir && vraiePage) {
            enAttenteDePreuve = false
            if (e.scrollX >= 0) IconeAccueil.dernierScrollX = e.scrollX
            if (e.toIndex >= 0) IconeAccueil.dernierIndex = e.toIndex
            // 1er changement de page depuis qu'on a touché l'icône : on vient de QUITTER sa page (on y
            // était depuis l'ouverture de l'app par son icône) : sa position est celle du début du geste.
            // (Si le geste n'a envoyé qu'un seul signal, le début vaut la fin : on ne peut rien en tirer.)
            if (IconeAccueil.pageAConfirmer) {
                IconeAccueil.pageAConfirmer = false
                // LE NUMÉRO DE LA PAGE quittée. Le 1er signal d'un geste arrive souvent au milieu du
                // mouvement (vu le 09/11 : 275 ou 522 au lieu de 0 ou 1039), donc sa position ne dit pas
                // d'où on vient. Mais elle dit dans quel SENS on va : si on arrive sur la page X en
                // allant vers la droite (scrollX qui monte), on vient de la page X - 1 ; sinon de X + 1.
                if (IconeAccueil.surPageIcone == true && numeroDuGeste > 0 && debutGeste >= 0 && debutGeste != e.scrollX) {
                    val quittee = if (e.scrollX > debutGeste) numeroDuGeste - 1 else numeroDuGeste + 1
                    IconeAccueil.definirPageIcone(service, quittee)
                    noter("  (icône vivante : l'icône est sur la page n° $quittee de l'accueil)")
                }
                // et le nombre de pages de l'accueil, si on ne le connaît pas encore
                if (pageDuGeste > 0 && IconeAccueil.nbPages(service) <= 0) IconeAccueil.definirNbPages(service, pageDuGeste)
                if (IconeAccueil.pageIcone(service) > 0) {
                    // trouvée grâce au numéro de page (juste au-dessus)
                } else if (selectionsRecues == 0 && IconeAccueil.surPageIcone == true && debutGeste >= 0 && debutGeste != e.scrollX) {
                    // (ancienne méthode, seulement si ce téléphone n'envoie pas les numéros de page)
                    IconeAccueil.definirPageScroll(service, debutGeste)
                    noter("  (icône vivante : page de l'icône trouvée, scrollX $debutGeste)")
                } else {
                    noter("  (icône vivante : page de l'icône pas trouvée, début $debutGeste, fin ${e.scrollX})")
                }
            }
            // On sait maintenant si la nouvelle page est celle de l'icône (ou on ne sait plus).
            val numIcone = IconeAccueil.pageIcone(service)
            val refApres = IconeAccueil.pageScroll(service)
            IconeAccueil.surPageIcone = when {
                numIcone > 0 && numeroDuGeste > 0 -> numeroDuGeste == numIcone
                refApres != Int.MIN_VALUE && e.scrollX >= 0 ->
                    abs(e.scrollX - refApres) < service.resources.displayMetrics.widthPixels / 2
                else -> null
            }
        } else if (!vraiePage) {
            // geste sans « Page X sur N » : on ne sait pas où on est, le logo reste caché
            enAttenteDePreuve = true
        }
        if (notesDefilement < 40) {
            notesDefilement++
            noter("  [défilement] $classe : scrollX $debutGeste -> ${e.scrollX} ($signauxGeste signaux, " +
                "page ${if (pageDuGeste > 0) "sur $pageDuGeste" else "?"}${if (dansTiroir) ", tiroir" else ""}), " +
                "scrollY ${e.scrollY}, index ${e.fromIndex}->${e.toIndex} sur ${e.itemCount}")
        }
        evaluer("fin du défilement")
    }

    // Doit-on afficher le logo vivant maintenant ?
    private fun evaluer(raison: String) {
        if (!surAccueil) return
        // un changement de page est en cours : c'est la fin du défilement qui décidera
        if (SystemClock.uptimeMillis() - defileA < 290) return
        if (dansTiroir) { cacher("tiroir d'apps"); return pas("tiroir d'apps ouvert") }
        if (enAttenteDePreuve) return pas("doigt posé ailleurs : j'attends un changement de page ou un retour sur l'accueil")
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
    //  - si on connaît le numéro (ou la position) de la page de l'icône ET de la page affichée : on compare ;
    //  - sinon, on suit « surPageIcone » : vrai juste après avoir touché l'icône (au 2e essai, on
    //    exigeait d'abord un changement de page, et rien ne s'affichait).
    private fun bonnePage(): Boolean? {
        // 1) le plus sûr : les numéros de page (« Page 2 sur 2 »)
        val numIcone = IconeAccueil.pageIcone(service)
        if (numIcone > 0 && IconeAccueil.pageActuelle > 0) return IconeAccueil.pageActuelle == numIcone
        // 2) sinon, les positions de défilement (téléphones qui n'envoient pas les numéros)
        val refX = IconeAccueil.pageScroll(service)
        val x = IconeAccueil.dernierScrollX
        if (refX != Int.MIN_VALUE && x != Int.MIN_VALUE)
            return abs(x - refX) < service.resources.displayMetrics.widthPixels / 2
        return IconeAccueil.surPageIcone
    }

    private fun montrer(r: Rect, raison: String) {
        // Le rectangle reçu contient l'icône ET son nom écrit dessous. Mesuré sur la capture du 09/11
        // (case de 249 x 290) : l'icône fait 69 % de la largeur de la case, elle est centrée en
        // largeur, et son centre est à 42,5 % de la hauteur. Notre fenêtre déborde de MARGE pixels de
        // chaque côté pour bien recouvrir la vraie icône (le dessin, lui, garde la taille exacte).
        val icone = (r.width() * 0.69f).toInt()
        val cote = icone + 2 * VueIconeVivante.MARGE
        val cx = r.centerX()
        val cy = r.top + (r.height() * 0.425f).toInt()
        val x = cx - cote / 2
        val y = cy - cote / 2
        val dejaLa = vue != null
        if (vue == null) {
            val v = VueIconeVivante(service)
            v.auToucher = { e -> toucher(e, r) }
            val p = WindowManager.LayoutParams(
                cote, cote,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                // NOT_TOUCH_MODAL : les appuis AILLEURS que sur le logo vont à l'écran d'accueil, comme
                // d'habitude. WATCH_OUTSIDE_TOUCH : mais on est PRÉVENU dès qu'un doigt se pose ailleurs
                // (avant même que la page commence à bouger) : on cache le logo tout de suite.
                // (Avant, on attendait le signal de défilement, qui arrive trop tard : on voyait le logo
                // une demi-seconde sur l'autre page.) Pas de clavier, coordonnées de tout l'écran.
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
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

    // LES APPUIS. Le logo est posé sur l'icône : c'est lui qui reçoit les doigts posés dessus.
    //  - un doigt posé AILLEURS (« outside ») : on cache le logo tout de suite ; il revient après un
    //    changement de page vers la page de l'icône, ou au retour d'une app ;
    //  - un appui court SUR le logo : on ouvre AnimLauncher nous-mêmes (comme l'aurait fait l'icône) ;
    //  - un appui long ou un glissé SUR le logo : on le retire, pour que tu puisses refaire le geste
    //    sur la vraie icône dessous (la déplacer, changer de page…).
    private var appuiA = 0L
    private var appuiX = 0f
    private var appuiY = 0f
    // (On retire la fenêtre avec « post » : juste APRÈS avoir fini de traiter le doigt, car retirer une
    // fenêtre pendant qu'Android lui envoie un appui peut le faire planter.)
    private fun toucher(e: MotionEvent, r: Rect): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> {
                // On ne remet PLUS le logo au bout de 0,9 s : quand le doigt glisse vers le haut pour
                // ouvrir le tiroir d'apps, Samsung ne prévient pas, et le logo revenait par-dessus.
                enAttenteDePreuve = true
                IconeAccueil.accueilSur = false
                if (!BuildConfig.STORE) noter("  [enquête] --- doigt posé ailleurs ---")
                minuteur.post { cacher("doigt posé ailleurs") }
            }
            MotionEvent.ACTION_DOWN -> {
                appuiA = SystemClock.uptimeMillis(); appuiX = e.rawX; appuiY = e.rawY
                val ici = appuiA
                // appui long : tu veux sûrement déplacer l'icône (ou ouvrir son menu). On oublie sa
                // position : touche-la de nouveau après l'avoir déplacée pour que le logo la retrouve.
                minuteur.postDelayed({ if (appuiA == ici) {
                    appuiA = 0L; IconeAccueil.oublier(service); cacher("appui long sur le logo (position oubliée)")
                } }, 450)
            }
            MotionEvent.ACTION_MOVE -> {
                if (appuiA != 0L && (abs(e.rawX - appuiX) > 30 || abs(e.rawY - appuiY) > 30)) {
                    appuiA = 0L; minuteur.post { cacher("glissé depuis le logo") }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (appuiA != 0L) {
                    appuiA = 0L
                    minuteur.post { ouvrirAnimLauncher(r) }
                }
            }
            MotionEvent.ACTION_CANCEL -> appuiA = 0L
        }
        return true
    }

    // Ouvre AnimLauncher comme si on avait touché son icône (on transmet aussi le rectangle de l'icône).
    private fun ouvrirAnimLauncher(r: Rect) {
        cacher("ouverture d'AnimLauncher")
        val intention = service.packageManager.getLaunchIntentForPackage(service.packageName) ?: return
        intention.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intention.sourceBounds = Rect(r)
        try { service.startActivity(intention) } catch (e: Exception) { noter("ERREUR ouverture : ${e.message}") }
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

    // L'ENQUÊTE (version Perso seulement, 09/11) : on note dans le journal TOUS les types de signaux
    // envoyés par l'écran d'accueil, pour découvrir s'il y en a un qui annonce l'ouverture ou la
    // fermeture du tiroir d'apps (glissement vers le haut ou le bas, sans changement de page).
    // Un même signal répété en rafale n'est noté qu'une fois. 300 lignes au plus.
    private var enqueteLignes = 0
    private var enqueteCle = ""
    private var enqueteA = 0L
    fun enquete(event: AccessibilityEvent, accueil: String?) {
        if (BuildConfig.STORE || enqueteLignes >= 300) return
        if (event.packageName?.toString() != accueil) return
        val type = AccessibilityEvent.eventTypeToString(event.eventType).removePrefix("TYPE_")
        val classe = event.className?.toString()?.substringAfterLast('.') ?: "?"
        val cle = "$type $classe ${event.contentChangeTypes}"
        val maintenant = SystemClock.uptimeMillis()
        val rafale = cle == enqueteCle && maintenant - enqueteA < 400
        enqueteA = maintenant
        if (rafale) return
        enqueteCle = cle
        enqueteLignes++
        val texte = (event.text.joinToString(" ") + " " + (event.contentDescription ?: "")).trim().take(40)
        noter("  [enquête] $type $classe changements=${event.contentChangeTypes} " +
            "défilement=${event.scrollX},${event.scrollY} fenêtre=${event.windowId} « $texte »")
    }

    private fun noter(texte: String) {
        Journal.noter(service, texte)
        Journal.programmerEnvoi(service)
    }
}

// LE DESSIN : le logo « Étincelle halo » qui lévite doucement, et dont le triangle fait
// de temps en temps un tiers de tour (120 degrés : il retombe pile sur la même forme).
class VueIconeVivante(context: Context) : View(context) {

    companion object {
        const val MARGE = 3      // pixels de débordement autour de la vraie icône (pour bien la couvrir)
    }

    // Ce qu'on fait quand un doigt touche (ou se pose ailleurs) : réglé par IconeVivante.
    var auToucher: ((MotionEvent) -> Boolean)? = null
    override fun onTouchEvent(event: MotionEvent): Boolean = auToucher?.invoke(event) ?: false

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
    // LA FORME de l'icône Samsung (One UI) : pas un carré aux coins arrondis, mais une « super-ellipse »
    // (|x|^n + |y|^n = 1), plus ronde. n = 2,6 mesuré sur ta capture du 09/11.
    private val forme = Path()
    private var formePour = -1f
    private fun preparerForme(cote: Float) {
        if (cote == formePour) return
        formePour = cote
        forme.reset()
        val n = 2.6
        val r = cote / 2f
        for (i in 0 until 120) {
            val a = 2.0 * PI * i / 120
            val c = Math.cos(a); val s = Math.sin(a)
            val x = r + r * Math.signum(c) * Math.pow(Math.abs(c), 2.0 / n)
            val y = r + r * Math.signum(s) * Math.pow(Math.abs(s), 2.0 / n)
            if (i == 0) forme.moveTo(x.toFloat(), y.toFloat()) else forme.lineTo(x.toFloat(), y.toFloat())
        }
        forme.close()
    }
    private val ellipse = RectF()

    override fun onDraw(c: Canvas) {
        val t = (SystemClock.uptimeMillis() - debut) / 1000f
        val cote = min(width, height).toFloat()

        // 1) le fond : la forme d'icône Samsung, de la couleur de l'icône, qui recouvre la vraie icône
        //    (immobile) en dessous.
        preparerForme(cote)
        c.drawPath(forme, fond)

        // 2) on passe dans la grille 108 x 108 de l'icône. La partie visible d'une icône Android
        //    fait 72 de large, et elle occupe toute la vraie icône (la fenêtre moins ses marges).
        c.save()
        // (69,5 au lieu de 72 : sur ta capture, Samsung dessine le triangle environ 3 % plus grand)
        val k = (cote - 2 * MARGE) / 69.5f
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
