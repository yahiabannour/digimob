package de.mhh.digimob.feature.training.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.mhh.digimob.core.designsystem.theme.DigiMobTheme
import de.mhh.digimob.core.domain.exercise.Landmark

/**
 * Das Skelett ueber dem Videobild (draw_pose): rote Punkte, weisse Verbindungen, nur die Punkte in
 * [shown] (die der Uebung, ExerciseCatalog.landmarks) und die Verbindungen zwischen ihnen.
 * [landmarks] relativ zum Bild, das darunter in derselben Groesse liegt (beide werden zusammen
 * gespiegelt).
 */
@Composable
internal fun KeypointOverlay(landmarks: List<Landmark>, shown: Set<Int>, modifier: Modifier = Modifier) {
    val colors = DigiMobTheme.colors
    Canvas(modifier = modifier) {
        fun point(i: Int) = landmarks[i].let { Offset((it.x * size.width).toFloat(), (it.y * size.height).toFloat()) }
        fun drawn(i: Int) = i < landmarks.size && i in shown
        for ((a, b) in POSE_CONNECTIONS) {
            if (drawn(a) && drawn(b)) drawLine(colors.skeleton, point(a), point(b), strokeWidth = 3.dp.toPx())
        }
        landmarks.indices.filter(::drawn).forEach { drawCircle(colors.keypoint, radius = 5.dp.toPx(), center = point(it)) }
    }
}

/** Die Verbindungen der 33 Pose-Landmarks (MediaPipe POSE_CONNECTIONS). */
private val POSE_CONNECTIONS = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 7, 0 to 4, 4 to 5, 5 to 6, 6 to 8, 9 to 10,
    11 to 12, 11 to 13, 13 to 15, 15 to 17, 15 to 19, 15 to 21, 17 to 19,
    12 to 14, 14 to 16, 16 to 18, 16 to 20, 16 to 22, 18 to 20,
    11 to 23, 12 to 24, 23 to 24, 23 to 25, 24 to 26, 25 to 27, 26 to 28,
    27 to 29, 28 to 30, 29 to 31, 30 to 32, 27 to 31, 28 to 32
)

@Preview(name = "Skelett, Schultern und Ellbogen")
@Composable
private fun KeypointOverlayPreview() {
    DigiMobTheme {
        Box(modifier = Modifier.width(400.dp).aspectRatio(4f / 3f).background(Color.DarkGray)) {
            val points = List(33) { i ->
                val row = i / 6
                Landmark(0.2 + 0.12 * (i % 6), 0.1 + 0.14 * row, 0.0)
            }
            KeypointOverlay(landmarks = points, shown = setOf(11, 12, 13, 14), modifier = Modifier.width(400.dp).aspectRatio(4f / 3f))
        }
    }
}
