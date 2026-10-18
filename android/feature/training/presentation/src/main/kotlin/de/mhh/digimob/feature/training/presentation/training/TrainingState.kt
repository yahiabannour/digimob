package de.mhh.digimob.feature.training.presentation.training

import de.mhh.digimob.core.domain.exercise.BarStyle
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Pose
import de.mhh.digimob.core.domain.exercise.Side
import de.mhh.digimob.core.presentation.UiText
import de.mhh.digimob.feature.training.domain.Phase
import de.mhh.digimob.feature.training.presentation.bar.BarUi

/** [Image]: das Kamerabild (in der App Bitmap). */
data class TrainingState<out Image>(
    val phase: Phase = Phase.INTRO,
    val exercise: ExerciseId? = null,
    val index: Int = 0,
    val count: Int = 0,
    val frame: Image? = null,
    val pose: Pose? = null,
    val barStyle: BarStyle = BarStyle.SPLIT,
    val bars: Map<Side, BarUi> = emptyMap(),
    val message: UiText? = null,
    val isPaused: Boolean = false,
    val isZoomOn: Boolean = false,
    val showKeypoints: Boolean = false,
    val error: UiText? = null
)

sealed interface TrainingAction {
    data class OnCameraPermissionResult(val isGranted: Boolean) : TrainingAction
    data object OnPauseClick : TrainingAction
    data object OnKeypointsClick : TrainingAction
    data object OnZoomClick : TrainingAction
    data object OnDemoPlayed : TrainingAction
    data object OnStopClick : TrainingAction
}

sealed interface TrainingEvent {
    /** Alle Uebungen fertig: zurueck ins Hauptmenue (nur das ViewModel weiss, wann). */
    data object Finished : TrainingEvent
}
