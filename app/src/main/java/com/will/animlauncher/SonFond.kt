package com.will.animlauncher

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

// LE SON DES FONDS D'ÉCRAN VIDÉO (17/11, demande de Will). Trois choix :
//  - « Sans son » (de base) ;
//  - « Avec écouteurs » : le son sort seulement si des écouteurs ou un casque sont branchés (fil, USB, Bluetooth) ;
//  - « Toujours » : le son sort aussi par le haut-parleur du téléphone et par les enceintes Bluetooth.
// Le choix vaut pour les deux fonds qui jouent des vidéos : « Vidéo » (FondVideoService) et « Dossier »
// (FondDossierService). Il est rangé dans le carnet « fond », case « son ».
//
// COMMENT LE SON EST GARDÉ : à l'import, une vidéo est rangée DEUX fois si elle a du son (voir ImportFond) :
//  - la version muette (sans la piste son), jouée quand le son est coupé. Pourquoi ne pas juste baisser le
//    volume ? Une vidéo qui a du son, même à zéro, fait croire au téléphone qu'une musique joue (la réaction
//    sonore s'allumerait, par exemple) ;
//  - la version d'origine, avec le son, dans le sous-dossier « son/ », jouée quand le son est permis.
// Les vidéos importées AVANT le 17/11 n'ont que la version muette : il faut les réimporter pour avoir le son.
//
// ÉCOUTEURS OU ENCEINTE ? Un fil, une prise USB-C ou un casque Bluetooth « basse consommation » se reconnaissent
// tout seuls. Mais en Bluetooth classique, écouteurs et enceintes se ressemblent : pour faire la différence, on
// regarde la « classe » de l'appareil (casque, haut-parleur, chaîne hi-fi…). Depuis Android 12, ça demande la
// permission « Appareils à proximité » : elle est demandée seulement quand on choisit « Avec écouteurs ».
// Sans elle, tout appareil Bluetooth audio compte comme des écouteurs.
object SonFond {
    const val JAMAIS = "jamais"
    const val ECOUTEURS = "ecouteurs"
    const val TOUJOURS = "toujours"
    const val CLE = "son"
    const val CODE_PERMISSION = 61

    fun mode(c: Context): String = CarnetFond.carnet(c).getString(CLE, JAMAIS) ?: JAMAIS
    fun choisir(c: Context, m: String) { CarnetFond.carnet(c).edit().putString(CLE, m).apply() }

    // Le son peut-il sortir EN CE MOMENT ?
    fun permis(c: Context): Boolean = when (mode(c)) {
        TOUJOURS -> true
        ECOUTEURS -> ecouteursBranches(c)
        else -> false
    }

    // La version « avec le son » d'une vidéo importée (elle n'existe que si la vidéo avait du son).
    fun avecSon(f: java.io.File): java.io.File = java.io.File(java.io.File(f.parentFile, "son"), f.name)

    // La vidéo à jouer : celle avec le son si le son est permis (et qu'elle existe), sinon la muette.
    fun fichierAJouer(f: java.io.File, permis: Boolean): java.io.File {
        val son = avecSon(f)
        return if (permis && son.exists()) son else f
    }

    // Des écouteurs ou un casque sont-ils branchés ? (une enceinte Bluetooth ne compte pas, si on peut le savoir)
    fun ecouteursBranches(c: Context): Boolean {
        val audio = c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        for (d in audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            when (d.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_HEARING_AID,
                26 /* TYPE_BLE_HEADSET (Android 12) */ -> return true
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> if (!estEnceinte(c, d)) return true
            }
        }
        return false
    }

    // Cet appareil Bluetooth est-il une enceinte (et pas des écouteurs) ? On lit sa « classe ».
    @Suppress("MissingPermission")
    private fun estEnceinte(c: Context, d: AudioDeviceInfo): Boolean {
        if (Build.VERSION.SDK_INT < 28 || !peutLireBluetooth(c)) return false     // (l'adresse d'un appareil : Android 9 et plus)
        return try {
            val adaptateur = (c.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: return false
            val classe = adaptateur.getRemoteDevice(d.address).bluetoothClass ?: return false
            when (classe.deviceClass) {
                BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
                BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO,
                BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
                BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO,
                BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER -> true
                else -> false
            }
        } catch (_: Exception) { false }
    }

    private fun peutLireBluetooth(c: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------------------------------------
    // POUR LES SERVICES : prévient quand le son devient permis ou interdit (écouteurs branchés ou retirés,
    // choix changé dans l'app). « quandChange » reçoit la nouvelle réponse.
    // ------------------------------------------------------------------------------------------
    class Veille(private val c: Context, private val quandChange: (Boolean) -> Unit) :
        SharedPreferences.OnSharedPreferenceChangeListener {
        private val audio = c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        private val principal = Handler(Looper.getMainLooper())
        var permis = SonFond.permis(c)
            private set
        private val appareils = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(ajoutes: Array<out AudioDeviceInfo>?) = verifier()
            override fun onAudioDevicesRemoved(retires: Array<out AudioDeviceInfo>?) = verifier()
        }

        fun demarrer() {
            CarnetFond.carnet(c).registerOnSharedPreferenceChangeListener(this)
            audio?.registerAudioDeviceCallback(appareils, principal)
            verifier()
        }

        fun arreter() {
            CarnetFond.carnet(c).unregisterOnSharedPreferenceChangeListener(this)
            audio?.unregisterAudioDeviceCallback(appareils)
        }

        override fun onSharedPreferenceChanged(p: SharedPreferences?, cle: String?) {
            if (cle == null || cle == SonFond.CLE) verifier()
        }

        private fun verifier() {
            val nouveau = SonFond.permis(c)
            if (nouveau != permis) { permis = nouveau; quandChange(nouveau) }
        }
    }

    // ------------------------------------------------------------------------------------------
    // LE RÉGLAGE À L'ÉCRAN : un titre, les 3 choix côte à côte (celui choisi en blanc), et une phrase.
    // Utilisé par l'écran « Fonds d'écran » et par l'écran du dossier.
    // ------------------------------------------------------------------------------------------
    // « explication » : la phrase sous les 3 choix (on la retire dans la petite feuille de l'aperçu).
    fun vue(a: Activity, explication: Boolean = true): View {
        val px = { v: Float -> Style.px(a, v) }
        val choix = listOf(JAMAIS to R.string.son_fond_jamais, ECOUTEURS to R.string.son_fond_ecouteurs, TOUJOURS to R.string.son_fond_toujours)
        val boutons = ArrayList<TextView>()
        fun peindre() {
            val actuel = mode(a)
            for ((i, b) in boutons.withIndex()) {
                val oui = choix[i].first == actuel
                b.setTextColor(if (oui) Style.ENCRE else Style.DOUX)
                b.background = Style.arrondi(a, if (oui) Style.TEXTE else Color.argb(14, 255, 255, 255), 12f,
                    if (oui) null else Color.rgb(52, 52, 58))
            }
        }
        return LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(4f), px(18f), px(4f), 0)
            addView(Style.texte(a, a.getString(R.string.son_fond_titre), 15f, Style.TEXTE, gras = true))
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, px(10f), 0, px(8f))
                for ((i, c) in choix.withIndex()) {
                    val b = Style.texte(a, a.getString(c.second), 13f, Style.DOUX, gras = true).apply {
                        gravity = Gravity.CENTER
                        setPadding(px(6f), 0, px(6f), 0)
                        isClickable = true
                        setOnClickListener {
                            choisir(a, c.first)
                            peindre()
                            // « Avec écouteurs » : pour reconnaître une enceinte Bluetooth, il faut la permission
                            // « Appareils à proximité » (Android 12 et plus). On la demande une fois, ici.
                            if (c.first == ECOUTEURS && !peutLireBluetooth(a) && Build.VERSION.SDK_INT >= 31) {
                                a.requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), CODE_PERMISSION)
                            }
                        }
                    }
                    boutons.add(b)
                    addView(b, LinearLayout.LayoutParams(0, px(42f), 1f).apply { if (i < 2) rightMargin = px(8f) })
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            if (explication) addView(Style.texte(a, a.getString(R.string.son_fond_expl), 12f, Style.MUET).apply { setLineSpacing(0f, 1.2f) })
            peindre()
        }
    }
}
