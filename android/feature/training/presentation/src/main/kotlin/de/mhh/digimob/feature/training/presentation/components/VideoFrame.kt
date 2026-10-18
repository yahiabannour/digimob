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
 * Das Videobild (4:3) gespiegelt, darueber auf Wunsch das Skelett, oben Name und Meldung der
 * Uebung (bildoperation_opencv.overlay). [content] statt des Bildes: die Demo der Einfuehrung.
 */
@Composable
internal fun VideoFrame(
    title: String,
    message: String?,
    image: ImageBitmap?,
    landmarks: List<Landmark>?,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null
) {
    Box(modifier = modifier.aspectRatio(4f / 3f).background(Color.Black)) {
        if (content != null) {
            content()
        } else {
            // Bild und Skelett zusammen spiegeln: die linke Seite des Patienten ist links im Bild
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = -1f }) {
                image?.let {
                    Image(bitmap = it, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                }
                landmarks?.let { KeypointOverlay(landmarks = it, modifier = Modifier.fillMaxSize()) }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(DigiMobTheme.colors.videoScrim)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = DigiMobTheme.colors.onVideo
            )
            message?.let {
                Text(text = it, style = MaterialTheme.typography.bodyLarge, color = DigiMobTheme.colors.onVideo)
            }
        }
    }
}

@Preview(name = "Ohne Bild")
@Preview(name = "Ohne Bild dunkel", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun VideoFramePreview() {
    DigiMobTheme {
        VideoFrame(
            title = "Abduktion Schulter",
            message = "Super, versuchen Sie 5 mal zu schaffen!",
            image = null,
            landmarks = null,
            modifier = Modifier.width(600.dp)
        )
    }
}

@Preview(name = "Mit Skelett, ohne Meldung")
@Composable
private fun VideoFrameKeypointsPreview() {
    DigiMobTheme {
        VideoFrame(
            title = "Halswirbelsäule",
            message = null,
            image = null,
            landmarks = List(33) { Landmark(0.3 + 0.01 * it, 0.2 + 0.02 * it, 0.0) },
            modifier = Modifier.width(600.dp)
        )
    }
}
