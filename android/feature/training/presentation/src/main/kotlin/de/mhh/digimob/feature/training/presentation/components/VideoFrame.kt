package de.mhh.digimob.feature.training.presentation.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.mhh.digimob.core.designsystem.theme.DigiMobTheme
import de.mhh.digimob.core.domain.exercise.Landmark

/**
 * Das Videobild (4:3) gespiegelt, darueber auf Wunsch das Skelett (nur [shownLandmarks]), oben
 * Titel und Meldung (bildoperation_opencv.overlay). [title] null: die Uebungsseite zeigt den Namen
 * in der Kopfleiste. [content] statt des Bildes: die Demo der Einfuehrung.
 */
@Composable
internal fun VideoFrame(
    title: String?,
    message: String?,
    image: ImageBitmap?,
    landmarks: List<Landmark>?,
    shownLandmarks: Set<Int>,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null
) {
    // clipToBounds: Landmarks ausserhalb des Bildes (z.B. die Huefte unter dem Bildrand) nicht ueber die Seite zeichnen
    Box(modifier = modifier.aspectRatio(4f / 3f).clipToBounds().background(Color.Black)) {
        if (content != null) {
            content()
        } else {
            // Bild und Skelett zusammen spiegeln: die linke Seite des Patienten ist links im Bild
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = -1f }) {
                image?.let {
                    Image(bitmap = it, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                }
                landmarks?.let { KeypointOverlay(landmarks = it, shown = shownLandmarks, modifier = Modifier.fillMaxSize()) }
            }
        }
        if (title != null || message != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(DigiMobTheme.colors.videoScrim)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DigiMobTheme.colors.onVideo
                    )
                }
                message?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyLarge, color = DigiMobTheme.colors.onVideo)
                }
            }
        }
    }
}

@Preview(name = "Ohne Bild, nur Meldung")
@Preview(name = "Ohne Bild dunkel", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun VideoFramePreview() {
    DigiMobTheme {
        VideoFrame(
            title = null,
            message = "Okay, versuchen Sie 5 mal zu schaffen!",
            image = null,
            landmarks = null,
            shownLandmarks = emptySet(),
            modifier = Modifier.width(600.dp)
        )
    }
}

@Preview(name = "Mit Titel und Skelett (Gesicht), ohne Meldung")
@Composable
private fun VideoFrameKeypointsPreview() {
    DigiMobTheme {
        VideoFrame(
            title = "Halswirbelsäule",
            message = null,
            image = null,
            landmarks = List(33) { Landmark(0.3 + 0.01 * it, 0.2 + 0.02 * it, 0.0) },
            shownLandmarks = (0..6).toSet(),
            modifier = Modifier.width(600.dp)
        )
    }
}
