package de.mhh.digimob.core.designsystem.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import de.mhh.digimob.core.designsystem.theme.DigiMobTheme

/**
 * Umschalter zwischen wenigen Optionen (Voll | Einfach). [selected] darf mehrere enthalten: sind
 * zwei Optionen gleich (Seitheben: Voll = Einfach), leuchten beide.
 */
@Composable
fun DigiMobSegmentedToggle(
    options: List<String>,
    selected: Set<Int>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index in selected,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                label = { Text(text = label, style = MaterialTheme.typography.titleMedium) }
            )
        }
    }
}

@Preview(name = "Erste gewaehlt")
@Preview(name = "Erste gewaehlt dunkel", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DigiMobSegmentedToggleFirstPreview() {
    DigiMobTheme {
        DigiMobSegmentedToggle(options = listOf("Voll", "Einfach"), selected = setOf(0), onSelect = {})
    }
}

@Preview(name = "Zweite gewaehlt")
@Composable
private fun DigiMobSegmentedToggleSecondPreview() {
    DigiMobTheme {
        DigiMobSegmentedToggle(options = listOf("Voll", "Einfach"), selected = setOf(1), onSelect = {})
    }
}

@Preview(name = "Beide gleich")
@Composable
private fun DigiMobSegmentedToggleBothPreview() {
    DigiMobTheme {
        DigiMobSegmentedToggle(options = listOf("Voll", "Einfach"), selected = setOf(0, 1), onSelect = {})
    }
}
