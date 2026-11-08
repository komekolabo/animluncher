package com.will.animlauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RuntimeShader
import android.os.Build
import android.annotation.TargetApi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ===================== GROK : deux variantes =====================
// 0. « Trou noir » : un trou noir spectaculaire (disque de matière fin vu par la tranche, lumière
//    courbée au-dessus et au-dessous, anneau de photons, étoiles déformées), puis on plonge dedans.
// 1. « Épuré » : le même trou noir en noir et blanc, tout en traits fins (dans le style de l'app Grok).
val GROK_VARIANTES = listOf("Trou noir", "Épuré")
fun grok(numero: Int): Scene = if (numero == 1) GrokEpure() else GrokTrouNoir()

// LES SHADERS : de petits programmes qui calculent la couleur de CHAQUE pixel, exécutés par la
// puce graphique du téléphone (des millions de pixels par image, impossible autrement).
// Langage AGSL, disponible depuis Android 13. Sur un téléphone plus ancien, on garde l'ancienne animation.
object Shaders {
    // Crée le shader ; en cas de problème, on le note dans le journal et on renvoie null.
    fun creer(nom: String, source: String): Any? {
        if (Build.VERSION.SDK_INT < 33) return null
        return try {
            creer33(source)
        } catch (e: Exception) {
            AnimApp.noter("ERREUR shader « $nom » : ${e.message?.take(300)}")
            null
        }
    }

    @TargetApi(33)
    private fun creer33(source: String): Any = RuntimeShader(source)

    // Dessine tout l'écran avec le shader, après lui avoir donné ses réglages (les « uniforms »).
    @TargetApi(33)
    fun dessiner(c: Canvas, shader: Any, peinture: Paint, valeurs: Map<String, FloatArray>) {
        val s = shader as RuntimeShader
        for ((nom, v) in valeurs) {
            when (v.size) {
                1 -> s.setFloatUniform(nom, v[0])
                2 -> s.setFloatUniform(nom, v[0], v[1])
            }
        }
        peinture.shader = s
        c.drawPaint(peinture)
    }

    // Morceaux communs : bruit « lissé » et turbulence (somme de bruits de tailles différentes).
    const val BRUIT = """
        float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
        float vnoise(float2 p) {
            float2 i = floor(p);
            float2 f = fract(p);
            float2 u = f * f * (3.0 - 2.0 * f);
            float a = hash(i);
            float b = hash(i + float2(1.0, 0.0));
            float c = hash(i + float2(0.0, 1.0));
            float d = hash(i + float2(1.0, 1.0));
            return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
        }
        float fbm(float2 p) {
            float v = 0.0;
            float amp = 0.5;
            for (int i = 0; i < 4; i++) {
                v += amp * vnoise(p);
                p = p * 2.03;
                amp *= 0.5;
            }
            return v;
        }
    """
}

// Le trou noir. Toutes les mesures sont celles d'un écran « modèle » de 540 de large (k = largeur / 540).
private const val SHADER_TROU_NOIR = Shaders.BRUIT + """
    uniform float2 res;
    uniform float k;
    uniform float sec;
    uniform float app;
    uniform float rev;

    const float PI = 3.14159265;
    const float INCL = 0.13;
    const float ROLL = -0.15708;

    // la matière du disque : turbulence qui fait le tour sans couture (a de 0 à 1 = un tour)
    float turb(float u, float a) {
        float n1 = fbm(float2(a * 24.0, u * 10.0));
        float n2 = fbm(float2((a - 1.0) * 24.0, u * 10.0));
        return clamp((mix(n1, n2, a) - 0.25) / 0.5, 0.0, 1.0);
    }

    // luminosité du disque à un rayon rr (en rayons du trou) et un angle ang
    float disque(float rr, float ang) {
        if (rr < 1.55 || rr > 5.2) { return 0.0; }
        float u = clamp((rr - 1.55) / 3.65, 0.0, 0.999);
        float vit = 0.9 / pow(max(rr, 1.0), 1.5);
        float a = fract(ang / (2.0 * PI) + sec * vit);
        float t = turb(u, a);
        float radial = exp(-(rr - 1.6) * 0.75);
        float doppler = 1.0 - 0.75 * cos(ang);
        return radial * (0.35 + 0.9 * pow(t, 1.6)) * doppler;
    }

    half4 main(float2 frag) {
        float2 m = frag / k;
        float W = 540.0;
        float Hm = res.y / k;
        float zoom = (0.80 + 0.20 * app + 0.035 * sec) * (1.0 + 6.0 * pow(rev, 2.2));
        float rh = W * 0.12 * zoom;
        float2 c = float2(W * 0.5, Hm * 0.47);
        float a = 1.0 - clamp(rev * 1.4, 0.0, 1.0);
        float2 dv = m - c;
        float dist = length(dv) + 0.001;

        // les étoiles, vues à travers la lentille gravitationnelle
        float re = rh * 2.0;
        float beta = dist - re * re / dist;
        float2 src = c + dv / dist * beta;
        float2 cell = floor(src / 6.0);
        float etoile = 0.0;
        if (hash(cell) > 0.967) {
            float2 pos = (cell + float2(hash(cell + 3.1), hash(cell + 7.7))) * 6.0;
            float dd = length(src - pos);
            float br = 0.25 + 0.75 * pow(hash(cell + 11.3), 2.0);
            etoile = br * exp(-dd * dd / 0.6);
        }
        float bande = exp(-pow((m.y - (Hm * 0.35 + (m.x - W * 0.5) * 0.55)) / 130.0, 2.0)) * 0.05;
        float3 ciel = (etoile * float3(0.88, 0.90, 1.0) + bande * float3(0.47, 0.51, 0.67)) * app * a;

        // repère légèrement penché
        float X = dv.x * cos(ROLL) + dv.y * sin(ROLL);
        float Y = -dv.x * sin(ROLL) + dv.y * cos(ROLL);
        float rho = length(float2(X, Y)) / rh;
        float phi = atan(Y, X);
        // le disque, vu presque par la tranche
        float Yd = Y / INCL;
        float rr = length(float2(X, Yd)) / rh;
        float ang = atan(Yd, X);
        float lum = disque(rr, ang);
        float arriere = Y < 0.0 ? lum : 0.0;
        float avant = Y >= 0.0 ? lum : 0.0;

        // la lumière de l'arrière du disque, courbée au-dessus et au-dessous du trou
        float dessus = pow(clamp(sin(-phi) + 0.25, 0.0, 1.0), 0.7);
        float haut = (exp(-pow((rho - 1.20) / 0.07, 2.0)) * 1.3 + exp(-pow((rho - 1.50) / 0.28, 2.0)) * 0.55) * dessus;
        float bas = exp(-pow((rho - 1.28) / 0.13, 2.0)) * pow(clamp(sin(phi), 0.0, 1.0), 0.6) * 0.55;
        float ta = turb(clamp(rho - 1.0, 0.0, 0.99), fract(phi / (2.0 * PI) + sec * 0.25));
        float lentille = (haut + bas) * (0.55 + 0.6 * ta) * (1.0 + 0.5 * cos(phi + PI));
        float anneau = exp(-pow((rho - 1.035) / 0.022, 2.0)) * (0.8 + 0.4 * cos(phi + PI));

        float total = (arriere + lentille * 0.95 + anneau * 1.1) * app * a;
        float ombre = clamp((rho - 1.0) * rh / 1.2, 0.0, 1.0);
        total = total * ombre + avant * app * a;
        float t = clamp(total, 0.0, 3.0);
        float3 chaud = float3(clamp(0.95 * t, 0.0, 1.0), clamp(0.62 * pow(t, 1.15), 0.0, 1.0), clamp(0.30 * pow(t, 1.6), 0.0, 1.0));
        float3 col = ciel * ombre * (1.0 - clamp(t, 0.0, 1.0)) + chaud;
        // une lueur douce autour des parties brillantes
        float lueur = 0.20 * exp(-pow((rho - 1.3) / 0.6, 2.0)) * (0.7 + 0.3 * cos(phi + PI))
                    + 0.10 * exp(-pow(Y / (rh * 0.45), 2.0)) * exp(-pow(rho / 4.5, 2.0));
        col += float3(1.0, 0.67, 0.35) * lueur * app * a * (0.4 + 0.6 * ombre);
        // la plongée : l'ombre remplit l'écran
        col *= 1.0 - clamp((rh * 1.02 - dist) / 3.0, 0.0, 1.0) * step(0.001, rev);
        col = clamp(col, 0.0, 1.0);
        return half4(col.r, col.g, col.b, 1.0);
    }
"""

class GrokTrouNoir : Scene() {
    private val shader = Shaders.creer("Grok trou noir", SHADER_TROU_NOIR)
    private val secours by lazy { Singularite() }      // si le téléphone ne sait pas faire de shader
    private val peinture = Paint()

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val s = shader
        if (s == null || Build.VERSION.SDK_INT < 33) { secours.dessiner(c, o, i); return }
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val k = w / 540f
        Shaders.dessiner(c, s, peinture, mapOf(
            "res" to floatArrayOf(w, h), "k" to floatArrayOf(k), "sec" to floatArrayOf(i.secondes),
            "app" to floatArrayOf(ralentit(i.intro)), "rev" to floatArrayOf(i.revelation)
        ))
        o.vignette(c, w, h, 0.55f)
        // l'app apparaît depuis le centre du trou, une fois la plongée bien avancée
        o.revelationDouce(c, w / 2f, h * 0.47f, borne((i.revelation - 0.25f) / 0.75f), null, 0.35f)
    }
}

// ===================== GROK ÉPURÉ (noir et blanc, traits fins) =====================
class GrokEpure : Scene() {
    private val incl = 0.13f
    private val roll = (-9.0 * PI / 180).toFloat()
    private val etoiles = Random(3).let { r ->
        List(220) { floatArrayOf(r.nextFloat() * 1.6f - 0.3f, r.nextFloat() * 1.2f - 0.1f, r.nextFloat()) }
    }
    private val chemin = Path()
    private val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)
    }
    private val point = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val noir = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }

    // Un morceau d'ellipse (rx, ry), de l'angle a0 à a1, tourné de « roll » autour de (cx, cy).
    private fun ellipse(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, a0: Float, a1: Float,
                        alpha: Float, largeur: Float) {
        if (alpha <= 0.003f) return
        chemin.reset()
        val n = 120
        val cr = cos(roll); val sr = sin(roll)
        for (j in 0..n) {
            val t = a0 + (a1 - a0) * j / n
            val x = rx * cos(t); val y = ry * sin(t)
            val px = cx + x * cr - y * sr; val py = cy + x * sr + y * cr
            if (j == 0) chemin.moveTo(px, py) else chemin.lineTo(px, py)
        }
        trait.strokeWidth = largeur
        trait.alpha = (borne(alpha) * 255).toInt()
        c.drawPath(chemin, trait)
    }

    override fun dessiner(c: Canvas, o: Outils, i: Instant) {
        val sec = i.secondes; val rev = i.revelation
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val k = w / 540f
        val app = ralentit(i.intro)
        val zoom = (0.85f + 0.15f * app + 0.03f * sec) * (1f + 6f * Math.pow(rev.toDouble(), 2.2).toFloat())
        val rh = w * 0.12f * zoom
        val cx = w / 2f; val cy = h * 0.47f
        val a = 1f - borne(rev * 1.4f)
        val pi = PI.toFloat()
        c.drawColor(Color.BLACK)

        // quelques étoiles, très fines, déviées autour du trou
        val re = rh * 2f
        for (e in etoiles) {
            val dx = e[0] * w - cx; val dy = e[1] * h - cy
            val d = hypot(dx, dy) + 0.001f
            val nd = (d + sqrt(d * d + 4f * re * re)) / 2f
            point.alpha = (0.5f * e[2] * app * a * 255).toInt()
            c.drawCircle(cx + dx / d * nd, cy + dy / d * nd, (0.5f + 0.5f * e[2]) * k, point)
        }
        // le disque : des ellipses fines qui se dessinent les unes après les autres
        for (j in 0 until 9) {
            val r = rh * (1.7f + j * 0.38f)
            val apparition = borne((sec - 0.05f * j) / 0.35f)
            if (apparition <= 0f) continue
            val al = (0.55f - j * 0.05f) * app * a
            ellipse(c, cx, cy, r, r * incl, pi, pi + pi * apparition, al * 0.6f, 1.0f * k)      // l'arrière
            ellipse(c, cx, cy, r, r * incl, 0f, pi * apparition, al, 1.1f * k)                  // l'avant
        }
        // la lumière courbée au-dessus et au-dessous du trou
        for ((rr, al) in listOf(1.22f to 0.85f, 1.42f to 0.45f, 1.62f to 0.22f)) {
            ellipse(c, cx, cy, rh * rr, rh * rr, pi * 1.05f, pi * 1.95f, al * app * a, 1.2f * k)
        }
        ellipse(c, cx, cy, rh * 1.12f, rh * 1.12f, pi * 0.15f, pi * 0.85f, 0.35f * app * a, 1.0f * k)
        // l'ombre, l'anneau de photons, et l'avant du disque qui passe devant
        c.drawCircle(cx, cy, rh, noir)
        trait.strokeWidth = 1.6f * k
        trait.alpha = (0.95f * app * a * 255).toInt()
        c.drawCircle(cx, cy, rh * 1.02f, trait)
        o.halo(c, cx, cy, rh * 1.6f, 0.05f * app * a, Color.WHITE)
        for (j in 0 until 3) {
            val r = rh * (1.7f + j * 0.38f)
            ellipse(c, cx, cy, r, r * incl, 0.05f, pi - 0.05f, (0.6f - 0.15f * j) * app * a, 1.1f * k)
        }
        // des particules qui tournent sur le disque
        val cr = cos(roll); val sr = sin(roll)
        point.alpha = (0.8f * app * a * 255).toInt()
        for (j in 0 until 40) {
            val r = rh * (1.8f + (j * 0.37f) % 3.0f)
            val t = (j * 2.4f + sec * 1.6f * (2.2f / Math.pow((r / rh).toDouble(), 1.5).toFloat())) % (2f * pi)
            val x = r * cos(t); val y = r * incl * sin(t)
            val px = cx + x * cr - y * sr; val py = cy + x * sr + y * cr
            if (sin(t) < 0f && hypot(px - cx, py - cy) < rh) continue        // cachée derrière le trou
            c.drawCircle(px, py, 1.1f * k, point)
        }
        // la plongée : l'ombre remplit l'écran, puis l'app apparaît depuis le centre
        if (rev > 0f) {
            c.drawCircle(cx, cy, rh * 1.02f, noir)
            o.revelationDouce(c, cx, cy, borne((rev - 0.25f) / 0.75f), Color.WHITE, 0.35f)
        }
    }
}
