package de.mhh.digimob.feature.training.presentation.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.mhh.digimob.core.designsystem.theme.DigiMobTheme
import de.mhh.digimob.feature.training.presentation.R

/**
 * Liegt ueber der Demo der Einfuehrung: Doppeltippen ueberspringt sie, der Hinweis steht unten
 * rechts (nicht gespiegelt). Eigene Flaeche ueber dem Player, der selbst ein Android-View ist.
 */
@Composable
internal fun DemoSkipOverlay(
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hint = stringResource(R.string.demo_skip_hint)
    val currentOnSkip by rememberUpdatedState(onSkip)
    Box(
        modifier = modifier
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { currentOnSkip() }) }
            .semantics { onClick(label = hint) { currentOnSkip(); true } }
    ) {
        Text(
            text = hint,
            style = MaterialTheme.typography.labelLarge,
            color = DigiMobTheme.colors.onVideo,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .background(DigiMobTheme.colors.videoScrim, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Preview(name = "Hinweis")
@Preview(name = "Hinweis dunkel", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DemoSkipOverlayPreview() {
    DigiMobTheme {
        DemoSkipOverlay(
            onSkip = {},
            modifier = Modifier.width(600.dp).aspectRatio(4f / 3f).background(Color.DarkGray)
        )
    }
}

@Preview(name = "Hinweis, schmal", widthDp = 320)
@Composable
private fun DemoSkipOverlayNarrowPreview() {
    DigiMobTheme {
        DemoSkipOverlay(onSkip = {}, modifier = Modifier.fillMaxSize().background(Color.DarkGray))
    }
}
