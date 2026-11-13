package com.will.animlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.io.File
import kotlin.math.abs
import kotlin.math.max

// LE FOND D'ÉCRAN 3D (13/11, idée de Will) : une photo coupée en DEUX CALQUES, le sujet (détouré) et le fond
// (avec le trou du sujet rebouché). Quand on penche le téléphone, le fond glisse dans un sens et le sujet dans
// l'autre : l'œil croit voir de la profondeur. Rendu choisi par Will sur l'aperçu « première version » :
//  - le fond couvre l'écran, un peu agrandi (x 1,16) et assombri ;
//  - le sujet occupe 84 % de la hauteur de l'écran, posé en bas (il dépasse un peu sous l'écran), centré ;
//  - une lueur douce derrière le sujet, un vignettage sur les bords.
// La fabrication des deux calques (détourage + rebouchage) est dans FabriqueFond3D.kt.
//
// Les fichiers d'un fond 3D sont rangés dans un dossier à lui : filesDir/fonds3d/p_<date>/
//   fond.jpg (le fond rebouché), sujet.png (le sujet, transparent autour), apercu.jpg (la vignette).

// ---------------------------------------------------------------------------------------------
// L'INCLINAISON : lit le capteur de « gravité » du téléphone (dans quel sens tire la pesanteur).
// x = penché à gauche / à droite, y = penché vers soi / vers l'avant, chacun entre -1 et 1.
// Le « zéro » suit lentement la façon dont on tient le téléphone (en ~2 s) : que tu sois assis ou couché,
// le fond revient au centre quand tu ne bouges plus. Seuls les MOUVEMENTS font bouger les calques.
// Le capteur n'est allumé que pendant que le fond est visible (demarrer / arreter).
// ---------------------------------------------------------------------------------------------
class Inclinaison(c: Context, private val quandCaBouge: () -> Unit) : SensorEventListener {
    private val sm = c.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    // capteur de gravité (déjà filtré par Android) ; sinon l'accéléromètre brut
    private val capteur: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var zeroX = Float.NaN
    private var zeroY = 0f
    var x = 0f
        private set
    var y = 0f
        private set
    private var allume = false

    fun demarrer() {
        if (allume || capteur == null) return
        allume = true
        zeroX = Float.NaN                       // on reprend le zéro à la position actuelle
        sm.registerListener(this, capteur, SensorManager.SENSOR_DELAY_GAME)
    }

    fun arreter() {
        if (!allume) return
        allume = false
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(e: SensorEvent) {
        // la gravité vaut 9,81 quand le téléphone est à plat dans cet axe : on ramène entre -1 et 1
        val gx = -e.values[0] / SensorManager.GRAVITY_EARTH
        val gy = e.values[1] / SensorManager.GRAVITY_EARTH
        if (zeroX.isNaN()) { zeroX = gx; zeroY = gy }
        // le zéro rattrape doucement la position (environ 2 % à chaque mesure, ~50 mesures par seconde)
        zeroX += (gx - zeroX) * 0.02f
        zeroY += (gy - zeroY) * 0.02f
        // 0,25 = environ 15° d'inclinaison pour aller au bout du mouvement
        val nx = ((gx - zeroX) / 0.25f).coerceIn(-1f, 1f)
        val ny = ((gy - zeroY) / 0.25f).coerceIn(-1f, 1f)
        val bouge = abs(nx - x) > 0.002f || abs(ny - y) > 0.002f
        x = nx; y = ny
        if (bouge) quandCaBouge()
    }

    override fun onAccuracyChanged(s: Sensor?, precision: Int) {}
}

// ---------------------------------------------------------------------------------------------
// LE DESSIN DU FOND 3D. Il hérite de DessinFond (comme Nuit et Galets) : le service de fond d'écran,
// l'aperçu et la galerie savent donc déjà l'afficher.
// ---------------------------------------------------------------------------------------------
class FondProfondeur(c: Context, dossier: String) : DessinFond() {
    private val fond: Bitmap?
    private val sujet: Bitmap?
    init {
        val m = c.resources.displayMetrics
        val cote = max(m.widthPixels, m.heightPixels)
        fond = PeintreFond.chargerImage(File(dossier, "fond.jpg").path, cote)
        sujet = try { BitmapFactory.decodeFile(File(dossier, "sujet.png").path) } catch (_: Exception) { null }
    }
    val valide = fond != null && sujet != null

    // la position actuelle des calques ; elle rejoint en douceur ce que donne le capteur
    private var px = 0f
    private var py = 0f
    private val inclinaison = Inclinaison(c) { reveil?.invoke() }

    private val filtre = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val voile = Paint()
    private val pLueur = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pVignette = Paint()
    private val cadre = RectF()
    private var wCache = -1f
    private var hCache = -1f

    override fun demarrer() = inclinaison.demarrer()
    override fun arreter() = inclinaison.arreter()

    // au repos = les calques sont arrivés là où le capteur les veut : plus besoin de redessiner
    override fun auRepos() = abs(inclinaison.x - px) < 0.002f && abs(inclinaison.y - py) < 0.002f

    override fun dessiner(c: Canvas, w: Float, h: Float, t: Float, dp: Float) {
        val f = fond ?: return
        val s = sujet ?: return
        // rattraper le capteur : 12 % du chemin à chaque image (mouvement souple, sans à-coups)
        px += (inclinaison.x - px) * 0.12f
        py += (inclinaison.y - py) * 0.12f
        if (w != wCache || h != hCache) {
            pLueur.shader = RadialGradient(w / 2f, h * 0.5f, w * 0.6f,
                intArrayOf(Color.argb(38, 255, 236, 220), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            pVignette.shader = RadialGradient(w / 2f, h * 0.45f, h * 0.62f,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(140, 0, 0, 0)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            voile.color = Color.argb(46, 0, 0, 0)          // le fond est assombri de 18 % (le sujet ressort)
            wCache = w; hCache = h
        }
        // 1) LE FOND : il « couvre » l'écran, agrandi de 16 % (on ne voit jamais son bord quand il glisse),
        //    et part à l'OPPOSÉ de l'inclinaison (il est loin)
        val sf = max(w / f.width, h / f.height) * 1.16f * 1.02f
        val fw = f.width * sf; val fh = f.height * sf
        val dxf = -px * w * 0.06f; val dyf = -py * w * 0.05f
        cadre.set((w - fw) / 2f + dxf, (h - fh) / 2f + dyf, (w + fw) / 2f + dxf, (h + fh) / 2f + dyf)
        c.drawBitmap(f, null, cadre, filtre)
        c.drawRect(0f, 0f, w, h, voile)
        // 2) LA LUEUR douce derrière le sujet (elle bouge à moitié, entre les deux calques)
        c.save()
        c.translate(-px * w * 0.03f, -py * w * 0.025f)
        c.drawRect(-w * 0.1f, -h * 0.1f, w * 1.1f, h * 1.1f, pLueur)
        c.restore()
        // 3) LE SUJET : 84 % de la hauteur de l'écran, posé en bas (dépasse de 4 %), centré ; il suit
        //    l'inclinaison (il est proche)
        val ss = h * 0.84f / s.height
        val sw = s.width * ss; val sh = s.height * ss
        val dxs = px * w * 0.035f; val dys = py * w * 0.025f
        val bas = h * 1.04f
        cadre.set((w - sw) / 2f + dxs, bas - sh + dys, (w + sw) / 2f + dxs, bas + dys)
        c.drawBitmap(s, null, cadre, filtre)
        // 4) le vignettage (les bords un peu plus sombres)
        c.drawRect(0f, 0f, w, h, pVignette)
    }
}
