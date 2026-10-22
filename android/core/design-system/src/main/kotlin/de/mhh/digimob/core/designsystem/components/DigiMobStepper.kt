package de.mhh.digimob.core.designsystem.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.mhh.digimob.core.designsystem.R
import de.mhh.digimob.core.designsystem.theme.AppIcons
import de.mhh.digimob.core.designsystem.theme.DigiMobTheme

/**
 * Eine ganze Zahl mit − und + daneben, fuer den Touchscreen statt einer Tastatur (z.B. die
 * Wiederholungen einer Aufnahme). Die Knoepfe sind an den Grenzen von [range] gesperrt.
 */
@Composable
fun DigiMobStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..99
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(min = 72.dp))
        FilledTonalIconButton(onClick = { onValueChange(value - 1) }, enabled = value > range.first) {
            Icon(painter = AppIcons.Remove, contentDescription = stringResource(R.string.cd_decrease))
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 48.dp)
        )
        FilledTonalIconButton(onClick = { onValueChange(value + 1) }, enabled = value < range.last) {
            Icon(painter = AppIcons.Add, contentDescription = stringResource(R.string.cd_increase))
        }
    }
}

@Preview(name = "Mitte")
@Preview(name = "Mitte dunkel", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DigiMobStepperPreview() {
    DigiMobTheme {
        DigiMobStepper(label = "Links", value = 5, onValueChange = {})
    }
}

@Preview(name = "Untere Grenze (minus gesperrt)")
@Composable
private fun DigiMobStepperMinPreview() {
    DigiMobTheme {
        DigiMobStepper(label = "Rechts", value = 0, onValueChange = {})
    }
}

@Preview(name = "Obere Grenze, grosse Schrift", fontScale = 1.5f)
@Composable
private fun DigiMobStepperMaxPreview() {
    DigiMobTheme {
        DigiMobStepper(label = "Beide", value = 99, onValueChange = {})
    }
}
