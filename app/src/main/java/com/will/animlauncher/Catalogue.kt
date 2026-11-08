package com.will.animlauncher

// Une entrée du catalogue : la catégorie de durée, et de quoi fabriquer la scène.
// "() -> Scene" = une petite fonction qui crée une scène neuve à chaque ouverture.
class Entree(val categorie: Categorie, val fabrique: () -> Scene)

// LE CATALOGUE : quelle animation pour quelle app (nom technique -> entrée).
// Pour ajouter une app : une ligne de plus dans la liste.
object Catalogue {
    private val E = Categorie.ESSENTIELLE
    private val C = Categorie.COURANTE
    private val S = Categorie.SECONDAIRE

    private fun e(categorie: Categorie, fabrique: () -> Scene) = Entree(categorie, fabrique)

    private val entrees: Map<String, Entree> = mapOf(
        // --- Lot 1 : les IA ---
        "com.anthropic.claude" to e(E) { Horizon() },
        "com.anthropic.aibo" to e(E) { Lucioles() },
        "com.openai.chatgpt" to e(E) { Brume() },
        "com.google.android.apps.bard" to e(E) { Aurore() },
        "ai.x.grok" to e(E) { Singularite() },
        // --- Lot 2 : Google ---
        "com.google.android.googlequicksearchbox" to e(E) { Quatre() },
        "com.android.chrome" to e(E) { Tunnel() },
        "com.google.android.apps.maps" to e(E) { Itineraire() },
        "com.google.android.gm" to e(E) { Pli() },
        "com.google.android.apps.photos" to e(E) { PoseLongue() },
        "app.revanced.android.youtube" to e(E) { Cinema() },
        "com.google.android.apps.googleassistant" to e(C) { Ecoute() },
        "com.google.android.apps.docs" to e(C) { Prisme() },
        "com.google.android.apps.docs.editors.docs" to e(C) { Ecriture() },
        "com.google.android.apps.tachyon" to e(C) { Visio() },
        "com.google.android.apps.translate" to e(C) { Passage() },
        "com.google.earth" to e(C) { PlaneteBleue() },
        "com.google.android.apps.walletnfcrel" to e(C) { Eventail() },
        "com.google.android.play.games" to e(S) { Neon() },
        "com.google.android.videos" to e(S) { Salon() },
        "com.google.android.apps.authenticator2" to e(S) { Coffre() },
        // --- Lot 3 : système Samsung ---
        "com.samsung.android.dialer" to e(E) { Appel() },
        "com.samsung.android.messaging" to e(E) { Bulles() },
        "com.sec.android.app.camera" to e(E) { Diaphragme() },
        "com.sec.android.gallery3d" to e(E) { ChambreNoire() },
        "com.android.vending" to e(E) { Spirale() },
        "com.android.settings" to e(C) { Engrenages() },
        "com.samsung.android.app.contacts" to e(C) { Constellation() },
        "com.sec.android.app.clockpackage" to e(C) { Cadran() },
        "com.samsung.android.calendar" to e(C) { Ephemeride() },
        "com.sec.android.app.popupcalculator" to e(C) { Compteur() },
        "com.samsung.android.app.notes" to e(C) { Plume() },
        "com.sec.android.app.myfiles" to e(C) { Rotatif() },
        "com.sec.android.app.sbrowser" to e(C) { Toile() },
        "com.sec.android.app.voicenote" to e(C) { Bande() },
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
        "com.whatsapp" to e(E) { AvionPapier() },
        "com.spotify.music" to e(E) { Vinyle() },
        "com.instagram.android" to e(C) { Pellicule() },
        "com.twitter.android" to e(C) { Eclipse() },
        "com.discord" to e(C) { EnseigneNeon() },
        "ee.mtakso.client" to e(C) { Route() },
        "com.duolingo" to e(C) { Flamme() },
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
        "eu.epicompany.wero.wallet" to e(S) { Pont() },
        "zapsolutions.strike" to e(S) { Foudre() },
        "com.cakewallet.cake_wallet" to e(S) { Strates() },
        "network.bisq.mobile.node" to e(S) { Echange() },
        "com.btcframe" to e(S) { Facettes() },
        "com.microstrategy.stream" to e(S) { Isometrie() },
    )

    fun pour(app: String): Entree? = entrees[app]
}
