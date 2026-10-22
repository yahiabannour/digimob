package de.mhh.digimob.core.designsystem.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mhh.digimob.core.designsystem.theme.DigiMobTheme

/** Art eines Knopfs wie KNOPF in der Desktop-App. */
enum class ButtonKind {
    PRIMAER,        // Hauptaktionen
    SEKUNDAER,      // alles andere
    GEFAHR,         // nur Beenden/Stop
    AN,             // eingeschalteter Umschalter (Zoom an, Keypoints an)
    ANGEHALTEN      // Pause aktiv
}

/**
 * Groesse eines Knopfs: NORMAL wie auf dem Desktop, KOMPAKT fuer die Kopfleiste der Uebungsseite
 * (vier Knoepfe neben dem Titel).
 */
enum class ButtonSize(val minWidth: Dp, val minHeight: Dp, val padding: PaddingValues, val fontSize: TextUnit) {
    NORMAL(320.dp, 72.dp, PaddingValues(horizontal = 24.dp, vertical = 12.dp), 24.sp),
    KOMPAKT(140.dp, 52.dp, PaddingValues(horizontal = 16.dp, vertical = 6.dp), 20.sp)
}

/** Grosser, flacher Knopf der App (Desktop: Arial 24, zwei Zeilen hoch, 20 Zeichen breit). */
@Composable
fun DigiMobButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.SEKUNDAER,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.NORMAL
) {
    val (container, content) = when (kind) {
        ButtonKind.PRIMAER -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        ButtonKind.SEKUNDAER -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        ButtonKind.GEFAHR -> DigiMobTheme.colors.danger to Color.White
        ButtonKind.AN -> DigiMobTheme.colors.on to Color.White
        ButtonKind.ANGEHALTEN -> DigiMobTheme.colors.paused to DigiMobTheme.colors.onPaused
    }
    Button(
        onClick = onClick,
        modifier = modifier.widthIn(min = size.minWidth).heightIn(min = size.minHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = size.padding
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = size.fontSize),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Preview(name = "Arten")
@Preview(name = "Arten dunkel", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DigiMobButtonKindsPreview() {
    DigiMobTheme {
        Column {
            ButtonKind.entries.forEach { kind ->
                DigiMobButton(text = kind.name.lowercase(), onClick = {}, kind = kind, modifier = Modifier.padding(4.dp))
            }
        }
    }
}

@Preview(name = "Deaktiviert")
@Composable
private fun DigiMobButtonDisabledPreview() {
    DigiMobTheme {
        DigiMobButton(text = "Speichern und starten", onClick = {}, kind = ButtonKind.PRIMAER, enabled = false)
    }
}

@Preview(name = "Langer Text", fontScale = 2f)
@Composable
private fun DigiMobButtonLongTextPreview() {
    DigiMobTheme {
        DigiMobButton(text = "Alle automatisch starten", onClick = {}, kind = ButtonKind.PRIMAER)
    }
}

@Preview(name = "Kompakt (Kopfleiste)")
@Composable
private fun DigiMobButtonCompactPreview() {
    DigiMobTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DigiMobButton(text = "Pause", onClick = {}, size = ButtonSize.KOMPAKT)
            DigiMobButton(text = "Keypoints an", onClick = {}, kind = ButtonKind.AN, size = ButtonSize.KOMPAKT)
            DigiMobButton(text = "Stop", onClick = {}, kind = ButtonKind.GEFAHR, size = ButtonSize.KOMPAKT)
        }
    }
}
