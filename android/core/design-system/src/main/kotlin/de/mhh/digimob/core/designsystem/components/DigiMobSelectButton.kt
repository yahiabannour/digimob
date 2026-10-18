package de.mhh.digimob.core.designsystem.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.mhh.digimob.core.designsystem.R
import de.mhh.digimob.core.designsystem.theme.AppIcons
import de.mhh.digimob.core.designsystem.theme.DigiMobTheme

/** Grosse Auswahlflaeche statt eines kleinen Kaestchens: angekreuzt = hellblau mit Haken. */
@Composable
fun DigiMobSelectButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.widthIn(min = 360.dp).heightIn(min = 64.dp),
        label = {
            Text(text = text, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingIcon = {
            if (selected) Icon(painter = AppIcons.Check, contentDescription = stringResource(R.string.cd_selected))
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = DigiMobTheme.colors.selected,
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface
        ),
        border = null
    )
}

@Preview(name = "Nicht gewaehlt / gewaehlt")
@Preview(name = "Nicht gewaehlt / gewaehlt dunkel", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DigiMobSelectButtonPreview() {
    DigiMobTheme {
        Column {
            DigiMobSelectButton(text = "Abduktion Schulter", selected = false, onClick = {})
            DigiMobSelectButton(text = "Abduktion Schulter", selected = true, onClick = {})
        }
    }
}

@Preview(name = "Grosse Schrift", fontScale = 2f)
@Composable
private fun DigiMobSelectButtonLargeFontPreview() {
    DigiMobTheme {
        DigiMobSelectButton(text = "Mobilisation Schulter", selected = true, onClick = {})
    }
}
