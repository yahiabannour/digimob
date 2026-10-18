package de.mhh.digimob.feature.training.presentation.recording

import de.mhh.digimob.core.domain.exercise.BarStyle
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Pose
import de.mhh.digimob.core.domain.exercise.Side
import de.mhh.digimob.core.presentation.UiText
import de.mhh.digimob.feature.training.presentation.bar.BarUi

enum class Verdict { OK, DEVIATES, NO_TRUTH, UNREADABLE }

/** Ergebnis einer Aufnahme. counter/expected je Seite; expectedReps null ohne Ground Truth. */
data class RecordingResultUi(
    val name: String,
    val total: Int,
    val counter: Map<Side, Int>,
    val expectedReps: Int?,
    val expectedPerSide: Map<Side, Int>?,
    val verdict: Verdict
)

data class RecordingTestState<out Image>(
    val isRunning: Boolean = true,
    val current: String? = null,
    val currentExercise: ExerciseId? = null,
    val index: Int = 0,
    val count: Int = 0,
    val frame: Image? = null,
    val pose: Pose? = null,
    val barStyle: BarStyle = BarStyle.SPLIT,
    val bars: Map<Side, BarUi> = emptyMap(),
    val results: List<RecordingResultUi> = emptyList(),
    val problems: List<String> = emptyList(),
    val error: UiText? = null
) {
    val okCount: Int get() = results.count { it.verdict == Verdict.OK }
}
