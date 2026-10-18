package de.mhh.digimob.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Farben, die Material 3 nicht kennt: Kopfleiste, Knopfarten, Balken. */
@Immutable
data class DigiMobColors(
    val header: Color,
    val onHeader: Color,
    val onHeaderVariant: Color,
    val selected: Color,
    val danger: Color,
    val on: Color,
    val paused: Color,
    val onPaused: Color,
    val barEmpty: Color,
    val barFull: Color,
    val barLine: Color,
    val barPeak: Color,
    val barConfirm: Color,
    val barCheck: Color,
    val barArrow: Color,
    val barBackground: Color,
    val keypoint: Color,
    val skeleton: Color,
    val onVideo: Color,
    val videoScrim: Color
)

private val LightColors = DigiMobColors(
    header = Kopf, onHeader = Color.White, onHeaderVariant = KopfText, selected = Auswahl,
    danger = Gefahr, on = An, paused = Angehalten, onPaused = Text,
    barEmpty = BalkenLeer, barFull = BalkenVoll, barLine = BalkenLinie, barPeak = BalkenSpitze,
    barConfirm = BalkenBestaetigung, barCheck = BalkenHaken, barArrow = BalkenPfeil, barBackground = Color.White,
    keypoint = Color.Red, skeleton = Color.White, onVideo = Color.White, videoScrim = Color(0x99000000)
)

private val DarkColors = LightColors.copy(
    header = KopfDunkel, selected = AuswahlDunkel, onPaused = Text,
    barLine = TextDunkel, barBackground = FlaecheDunkel
)

private val LightScheme = lightColorScheme(
    primary = Primaer, onPrimary = Color.White,
    secondaryContainer = Sekundaer, onSecondaryContainer = Text,
    background = Hintergrund, onBackground = Text,
    surface = Hintergrund, onSurface = Text,
    surfaceVariant = Flaeche, onSurfaceVariant = TextHell,
    surfaceContainer = Flaeche,
    error = Gefahr, onError = Color.White
)

private val DarkScheme = darkColorScheme(
    primary = PrimaerDunkel, onPrimary = Color.White,
    secondaryContainer = SekundaerDunkel, onSecondaryContainer = TextDunkel,
    background = HintergrundDunkel, onBackground = TextDunkel,
    surface = HintergrundDunkel, onSurface = TextDunkel,
    surfaceVariant = FlaecheDunkel, onSurfaceVariant = TextHellDunkel,
    surfaceContainer = FlaecheDunkel,
    error = Gefahr, onError = Color.White
)

// Gross: die App laeuft auf einem Tablet am Bettende, viele Patienten sind aelter
private val DigiMobTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontSize = 28.sp),
        titleLarge = titleLarge.copy(fontSize = 26.sp),
        titleMedium = titleMedium.copy(fontSize = 22.sp),
        bodyLarge = bodyLarge.copy(fontSize = 22.sp),
        bodyMedium = bodyMedium.copy(fontSize = 18.sp),
        labelLarge = labelLarge.copy(fontSize = 24.sp)
    )
}

private val LocalDigiMobColors = staticCompositionLocalOf { LightColors }

@Composable
fun DigiMobTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalDigiMobColors provides if (darkTheme) DarkColors else LightColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = DigiMobTypography,
            content = content
        )
    }
}

object DigiMobTheme {
    val colors: DigiMobColors
        @Composable get() = LocalDigiMobColors.current
}
