package de.mhh.digimob.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Die Farben der Desktop-App (Hauptprogramm.py): ruhig, hoher Kontrast (weisse Schrift auf
// Primaer, Gefahr und An je ueber 5:1), Rot nur fuer Beenden/Stop.
// Nur fuer die Theme-Definition, Composables lesen MaterialTheme.colorScheme / DigiMobTheme.colors.
internal val Hintergrund = Color(0xFFFFFFFF)
internal val Flaeche = Color(0xFFF1F4F8)
internal val Kopf = Color(0xFF1F4E79)
internal val KopfText = Color(0xFFC9D9EA)
internal val Text = Color(0xFF1D2125)
internal val TextHell = Color(0xFF5D6670)
internal val Auswahl = Color(0xFFD6E6F7)
internal val Primaer = Color(0xFF1F6FB2)
internal val Sekundaer = Color(0xFFE3E9F0)
internal val Gefahr = Color(0xFFC62828)
internal val An = Color(0xFF2E7D32)
internal val Angehalten = Color(0xFFF2A900)

// Dunkel: dieselben Rollen, fuer Geraete im Nachtmodus
internal val HintergrundDunkel = Color(0xFF111418)
internal val FlaecheDunkel = Color(0xFF1D2329)
internal val KopfDunkel = Color(0xFF16395A)
internal val TextDunkel = Color(0xFFE3E7EC)
internal val TextHellDunkel = Color(0xFFA5AFBA)
internal val AuswahlDunkel = Color(0xFF24476B)
internal val PrimaerDunkel = Color(0xFF4F9BDA)
internal val SekundaerDunkel = Color(0xFF2C343D)

// Balken: Gelb (leer) -> Gruen (voll, ab dem Ziel), Ziellinie schwarz, hoechster Punkt dunkelblau,
// Bestaetigung hellgruen mit Haekchen
internal val BalkenLeer = Color(0xFFFFFF00)
internal val BalkenVoll = Color(0xFF008000)
internal val BalkenLinie = Color(0xFF000000)
internal val BalkenSpitze = Color(0xFF1F3C88)
internal val BalkenBestaetigung = Color(0xFFC8F0C8)
internal val BalkenHaken = Color(0xFF006400)
internal val BalkenPfeil = Color(0xFF202020)
