package com.will.animlauncher

import android.content.Context

// Une entrée du catalogue : la catégorie de durée, et de quoi fabriquer la scène.
// "(Int) -> Scene" = une petite fonction qui crée une scène neuve à chaque ouverture ;
// le nombre qu'on lui donne est le numéro de la variante (pour les apps qui en ont plusieurs).
// "variantes" = le nom de chaque variante (liste vide si l'app n'a qu'une animation).
// "actifParDefaut" = false : l'animation existe mais est désactivée de base (on peut l'activer dans les options).
class Entree(val categorie: Categorie, val fabrique: (Int) -> Scene, val variantes: List<String> = emptyList(),
             val actifParDefaut: Boolean = true) {
    // La durée par défaut (apparition + révélation), en millisecondes.
    val dureeDefaut: Int get() = (categorie.intro + categorie.revelation).toInt()
}

// Ce qu'il faut pour jouer une animation : la scène, sa catégorie, et le facteur de vitesse
// (2 = deux fois plus long que la durée par défaut, 0,5 = deux fois plus court).
class Lancement(val scene: Scene, val categorie: Categorie, val facteur: Float)

// LE CATALOGUE : quelle animation pour quelle app (nom technique -> entrée).
// Pour ajouter une app : une ligne de plus dans la liste.
object Catalogue {
    private val E = Categorie.ESSENTIELLE
    private val C = Categorie.COURANTE
    private val S = Categorie.SECONDAIRE
    private val J = Categorie.JEU
    private val X = Categorie.ECHECS

    private fun e(categorie: Categorie, fabrique: () -> Scene) = Entree(categorie, { _ -> fabrique() })
    // Une app dont l'animation est DÉSACTIVÉE de base (on veut l'ouvrir tout de suite).
    private fun off(categorie: Categorie, fabrique: () -> Scene) = Entree(categorie, { _ -> fabrique() }, actifParDefaut = false)
    // Une app à plusieurs variantes : on donne leurs noms, et la fonction reçoit le numéro choisi.
    private fun v(categorie: Categorie, variantes: List<String>, fabrique: (Int) -> Scene) = Entree(categorie, fabrique, variantes)

    // off(...) = désactivée de base (on veut ces apps tout de suite) : Téléphone, Appareil photo,
    // Dictaphone, Authenticator, Identité Numérique La Poste, Wizway. Réactivables dans les options.
    // « Transition » = juste une belle transition, la plus courte possible (voir ScenesTransition.kt).
    private val entrees: Map<String, Entree> = mapOf(
        // --- Lot 1 : les IA ---
        "com.anthropic.claude" to v(E, MONDES.map { it.nom }) { Horizons(it) },   // un des 5 horizons, au hasard
        "com.anthropic.aibo" to e(E) { Lucioles() },
        "com.openai.chatgpt" to e(E) { Brume() },
        "com.google.android.apps.bard" to e(E) { Aurore() },
        "ai.x.grok" to e(E) { Singularite() },
        // --- Lot 2 : Google ---
        "com.google.android.googlequicksearchbox" to e(E) { Quatre() },
        "com.android.chrome" to e(E) { Tunnel() },
        "com.google.android.apps.maps" to v(E, listOf("Paris", "Londres", "Washington", "Amsterdam", "Moscou")) { Capitales(it) },   // une des 5 capitales, au hasard
        "com.google.android.gm" to e(E) { Pli() },
        "com.google.android.apps.photos" to e(E) { PoseLongue() },
        "app.revanced.android.youtube" to e(E) { Cinema() },
        "com.google.android.apps.googleassistant" to e(C) { Ecoute() },
        "com.google.android.apps.docs" to e(C) { Prisme() },
        "com.google.android.apps.docs.editors.docs" to e(C) { Ecriture() },
        "com.google.android.apps.tachyon" to e(C) { Visio() },
        "com.google.android.apps.translate" to e(C) { Passage() },
        "com.google.earth" to e(C) { PlaneteBleue() },
        "com.google.android.apps.walletnfcrel" to e(C) { Rapide(Eventail()) },   // raccourcie : pas de minimum de 1,2 s
        "com.google.android.play.games" to e(S) { Neon() },
        "com.google.android.apps.authenticator2" to off(S) { Coffre() },
        "com.google.android.videos" to e(S) { Salon() },
        // --- Lot 3 : système Samsung ---
        "com.samsung.android.dialer" to off(E) { Appel() },
        "com.sec.android.app.camera" to off(E) { Diaphragme() },
        "com.sec.android.app.voicenote" to off(C) { Bande() },
        "com.samsung.android.messaging" to e(S) { Transition(rgb(70, 130, 255)) },      // minimale
        "com.sec.android.gallery3d" to e(E) { ChambreNoire() },
        "com.android.vending" to e(E) { Spirale() },
        "com.android.settings" to e(C) { Engrenages() },
        "com.samsung.android.app.contacts" to e(C) { Constellation() },
        "com.sec.android.app.clockpackage" to e(C) { Cadran() },
        "com.samsung.android.calendar" to e(C) { Ephemeride() },
        "com.sec.android.app.popupcalculator" to e(S) { Transition(rgb(150, 160, 180)) },   // minimale
        "com.samsung.android.app.notes" to e(C) { Plume() },
        "com.sec.android.app.myfiles" to e(C) { Rotatif() },
        "com.sec.android.app.sbrowser" to e(C) { Toile() },
        "com.samsung.android.app.reminder" to e(C) { Sablier() },
        "com.sec.android.app.shealth" to e(C) { Pouls() },
        "com.sec.android.app.samsungapps" to e(S) { Galaxie() },
        "com.samsung.android.app.find" to e(S) { Radar() },
        "com.samsung.android.shealthmonitor" to e(S) { Jauge() },
        "com.samsung.android.oneconnect" to e(S) { Maison() },
        "com.samsung.android.app.watchmanager" to e(S) { Lunette() },
        "com.samsung.android.voc" to e(S) { Cercle() },
        "com.samsung.android.app.tips" to e(S) { Ampoule() },
        "com.sec.android.easyMover" to e(S) { Transfert() },
        "com.samsung.android.tvplus" to e(S) { Mire() },
        "com.samsung.ecomm.global.gbr" to e(S) { Paquet() },
        // --- Lot 4 : réseaux, musique, services ---
        "com.whatsapp" to e(S) { Transition(rgb(37, 211, 102)) },       // minimale
        "com.spotify.music" to e(E) { Vinyle() },
        "com.instagram.android" to e(C) { Pellicule() },
        "com.twitter.android" to e(C) { Eclipse() },
        "com.discord" to e(C) { EnseigneNeon() },
        "ee.mtakso.client" to e(C) { Route() },
        "com.duolingo" to v(C, DUO_VARIANTES) { duolingo(it) },   // une des 5 animations Duolingo, au hasard
        "com.suno.android" to e(C) { Spectre() },
        "com.github.android" to e(C) { Branches() },
        "com.fabernovel.ratp" to e(C) { Quai() },
        "com.jonathanpuckey.radiogarden" to e(S) { Antenne() },
        "com.sony.songpal.mdr" to e(S) { Silence() },
        "com.microsoft.skydrive" to e(S) { Nuees() },
        // --- Lot 5 : finance et crypto ---
        "com.revolut.revolut" to e(C) { CarteMetal() },
        "com.fullsix.android.labanquepostale.accountaccess" to e(C) { Timbre() },
        "nl.degiro.trader" to e(C) { Chandeliers() },
        "de.traderepublic.app" to e(C) { Courbe() },
        "com.kraken.invest.app" to e(C) { Abysses() },
        "com.ledger.live" to e(C) { ChaineBlocs() },
        "com.kraken.pay.app" to e(S) { Piece() },
        "com.bankeen" to e(S) { Piles() },
        "eu.epicompany.wero.wallet" to e(S) { Transition(rgb(230, 220, 70)) },   // minimale
        "zapsolutions.strike" to e(S) { StrikeNoirBlanc() },
        "com.cakewallet.cake_wallet" to e(S) { Strates() },
        "network.bisq.mobile.node" to e(S) { Echange() },
        "com.btcframe" to e(S) { Facettes() },
        "com.microstrategy.stream" to e(S) { Isometrie() },
        // --- Lot 6 : jeux (environ 1 s) et utilitaires ---
        "com.playstack.balatro.android" to e(J) { Cartes() },
        "com.supercell.clashofclans" to e(J) { Torches() },
        "com.chess" to v(X, PARTIES.map { it.titre }) { CoupsBrillants(it) },   // une des 5 parties, au hasard
        "com.robtopx.geometryjumplite" to e(J) { Cube() },
        "com.robtopx.geometrydashmeltdown" to e(J) { Cube(rgb(255, 140, 50), true) },
        "com.BlackSaltGames.DREDGE" to e(J) { Etire(Lanterne(), 2.5f) },
        "com.dsemu.drastic" to e(J) { Etire(DoubleEcran(), 2.5f) },
        "com.chessbar" to e(J) { Etire(Pendule(), 2.5f) },
        "com.strayfawnstudio.gambonanza" to e(J) { Etire(Des(), 2.5f) },
        "com.venezianoapp.frusta" to e(J) { Etire(Fouet(), 2.5f) },
        "app.revanced.android.gms" to e(S) { Puzzle() },
        "com.revanced.net.revancedmanager" to e(S) { Couture() },
        "tv.parsec.client" to e(S) { Hyperespace() },
        "com.freevpnplanet" to e(S) { Bulle() },
        "io.github.libresprite" to e(S) { Pixels() },
        "fr.astillia.SigtaoMobile" to e(S) { Boussole() },
        "com.airbnb.android" to e(S) { Cle() },
        "fr.laposte.idn" to off(S) { Empreinte() },
        "com.wizway.nfcagent" to off(S) { SansContact() },
    )

    fun pour(app: String): Entree? = entrees[app]

    // L'animation de cette app est-elle activée de base ? (oui, sauf les apps marquées off)
    fun actifParDefaut(app: String): Boolean = entrees[app]?.actifParDefaut ?: true

    // Prépare l'animation d'une app en tenant compte des réglages de l'utilisateur.
    // Renvoie null s'il ne faut rien jouer : pas d'animation pour cette app, animation
    // désactivée, durée réglée à 0 s, ou toutes les variantes décochées.
    fun lancement(c: Context, app: String): Lancement? {
        val e = entrees[app] ?: return null
        if (!Reglages.appActive(c, app)) return null
        val ms = Reglages.duree(c, app).let { if (it < 0) e.dureeDefaut else it }
        if (ms <= 0) return null
        var numero = -1
        if (e.variantes.isNotEmpty()) {
            val eteintes = Reglages.variantesDesactivees(c, app)
            val permises = e.variantes.indices.filter { it !in eteintes }
            if (permises.isEmpty()) return null
            numero = permises.random()          // une variante au hasard parmi celles qui sont cochées
        }
        return Lancement(e.fabrique(numero), e.categorie, ms.toFloat() / e.dureeDefaut)
    }
}
