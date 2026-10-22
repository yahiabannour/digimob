package de.mhh.digimob.feature.training.presentation.recording

import de.mhh.digimob.core.domain.exercise.ExerciseCatalog
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Pose
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.domain.exercise.Side
import de.mhh.digimob.core.presentation.UiText

/** READY -> (STARTING) -> RECORDING -> (STOPPING) -> LABELING -> (SAVING) -> READY */
enum class RecordingPhase { READY, STARTING, RECORDING, STOPPING, LABELING, SAVING }

/**
 * [counted]: was die App mit diesen Schwellen zaehlt (Vorschau auf den Systemtest), [left]/[right]:
 * die eingegebene Ground Truth, [right] nur bei zwei Seiten. [saved]: Meldung zur letzten Aufnahme.
 */
data class RecordingState<out Image>(
    val exercise: ExerciseId = ExerciseId.ABDUKTION,
    val preset: Preset = Preset.VOLL,
    val isTwoSided: Boolean = true,
    val isExerciseMenuExpanded: Boolean = false,
    val phase: RecordingPhase = RecordingPhase.READY,
    val isCameraRunning: Boolean = false,
    val frame: Image? = null,
    val pose: Pose? = null,
    val seconds: Double = 0.0,
    val frameCount: Int = 0,
    val hasPose: Boolean = false,
    val counted: Map<Side, Int> = emptyMap(),
    val left: Int = 0,
    val right: Int = 0,
    val hasVideo: Boolean = false,
    val saved: UiText? = null,
    val error: UiText? = null
) {
    /** Was das Skelett zeigt: die Landmarks der Uebung, wie auf der Uebungsseite. */
    val shownLandmarks: Set<Int> get() = ExerciseCatalog.landmarks(exercise)
}

sealed interface RecordingAction {
    data class OnCameraPermissionResult(val isGranted: Boolean) : RecordingAction
    data object OnExerciseMenuClick : RecordingAction
    data object OnExerciseMenuDismiss : RecordingAction
    data class OnExerciseSelect(val id: ExerciseId) : RecordingAction
    data class OnPresetSelect(val preset: Preset) : RecordingAction
    data object OnStartClick : RecordingAction
    data object OnStopClick : RecordingAction
    data class OnRepsChange(val side: Side, val value: Int) : RecordingAction
    data object OnSaveClick : RecordingAction
    data object OnDiscardClick : RecordingAction
    data object OnErrorDismiss : RecordingAction

    /** Die App ist nicht mehr zu sehen (Home, Bildschirm aus) bzw. wieder da. */
    data object OnScreenHidden : RecordingAction
    data object OnScreenShown : RecordingAction
}
