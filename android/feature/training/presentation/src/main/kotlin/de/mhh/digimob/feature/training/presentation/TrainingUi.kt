package de.mhh.digimob.feature.training.presentation

import de.mhh.digimob.core.domain.exercise.Bar
import de.mhh.digimob.core.domain.exercise.Side
import de.mhh.digimob.core.presentation.UiText
import de.mhh.digimob.feature.training.domain.Clock
import de.mhh.digimob.feature.training.domain.SourceError
import de.mhh.digimob.feature.training.presentation.bar.BarFeedback
import de.mhh.digimob.feature.training.presentation.bar.BarUi

fun SourceError.toUiText(): UiText = UiText.StringResource(
    when (this) {
        SourceError.NO_CAMERA -> R.string.error_no_camera
        SourceError.CAMERA_FAILED -> R.string.error_camera_failed
        SourceError.POSE_FAILED -> R.string.error_pose_failed
        SourceError.VIDEO_UNREADABLE -> R.string.error_video_unreadable
    }
)

/** Ein BarFeedback je Balken; ohne Balken (Einfuehrung, "Prima") vergessen sie ihren Stand. */
internal class BarFeedbacks(private val clock: Clock) {
    private val feedback = mutableMapOf<Side, BarFeedback>()

    fun update(bars: Map<Side, Bar>?): Map<Side, BarUi> {
        if (bars == null) {
            feedback.clear()    // naechste Uebung: keine Bestaetigung fuer den neuen Zielwert
            return emptyMap()
        }
        return bars.mapValues { (side, bar) -> feedback.getOrPut(side) { BarFeedback(clock) }.update(bar) }
    }
}
