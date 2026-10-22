package de.mhh.digimob.core.designsystem.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.mhh.digimob.core.designsystem.theme.DigiMobTheme

/**
 * Schieberegler mit Beschriftung darueber (Schwellen auf der Auswahlseite). [steps]: so viele
 * Rastpunkte zwischen den Enden, wie Slider sie erwartet. [onValueChangeFinished] beim Loslassen:
 * erst dann wird gespeichert. [isError]: die Beschriftung rot (ungueltige Werte).
 */
@Composable
fun DigiMobSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                activeTickColor = MaterialTheme.colorScheme.primary,
                inactiveTickColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Preview(name = "Grad")
@Preview(name = "Grad dunkel", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DigiMobSliderPreview() {
    DigiMobTheme {
        DigiMobSlider(
            label = "Arm oben über: 150°",
            value = 150f,
            valueRange = 40f..175f,
            steps = 134,
            onValueChange = {},
            onValueChangeFinished = {},
            modifier = Modifier.width(240.dp)
        )
    }
}

@Preview(name = "Vielfaches, am Rand")
@Composable
private fun DigiMobSliderEdgePreview() {
    DigiMobTheme {
        DigiMobSlider(
            label = "Arme offen über: 1,20x",
            value = 1.2f,
            valueRange = 1.2f..3f,
            steps = 35,
            onValueChange = {},
            onValueChangeFinished = {},
            modifier = Modifier.width(240.dp)
        )
    }
}

@Preview(name = "Ungueltig")
@Composable
private fun DigiMobSliderErrorPreview() {
    DigiMobTheme {
        DigiMobSlider(
            label = "Arm gebeugt unter: 150°",
            value = 150f,
            valueRange = 30f..155f,
            steps = 124,
            onValueChange = {},
            onValueChangeFinished = {},
            modifier = Modifier.width(240.dp),
            isError = true
        )
    }
}

@Preview(name = "Lange Beschriftung, grosse Schrift", fontScale = 1.5f)
@Composable
private fun DigiMobSliderLongLabelPreview() {
    DigiMobTheme {
        DigiMobSlider(
            label = "Oberarm am Körper bis: 50°",
            value = 50f,
            valueRange = 20f..90f,
            steps = 69,
            onValueChange = {},
            onValueChangeFinished = {},
            modifier = Modifier.width(240.dp)
        )
    }
}
