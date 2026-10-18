package de.mhh.digimob.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.mhh.digimob.core.designsystem.theme.DigiMobTheme

/**
 * Eine Spalte mittig auf der Seite (Hauptmenue, Operationsart). Passt sie nicht in die Hoehe
 * (kleiner Bildschirm, grosse Schrift), scrollt sie, statt die unteren Knoepfe abzuschneiden.
 */
@Composable
fun CenteredColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Preview(name = "Passt", widthDp = 1280, heightDp = 800)
@Composable
private fun CenteredColumnPreview() {
    DigiMobTheme {
        CenteredColumn {
            DigiMobButton(text = "Übungen wählen", onClick = {}, kind = ButtonKind.PRIMAER)
            DigiMobButton(text = "Beenden", onClick = {}, kind = ButtonKind.GEFAHR)
        }
    }
}

@Preview(name = "Zu klein, scrollt", widthDp = 900, heightDp = 300)
@Composable
private fun CenteredColumnScrollingPreview() {
    DigiMobTheme {
        CenteredColumn {
            repeat(5) { DigiMobButton(text = "Knopf ${it + 1}", onClick = {}) }
        }
    }
}
