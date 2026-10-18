package de.mhh.digimob.feature.menu.presentation.selection

import androidx.annotation.StringRes
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.OpType
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.presentation.UiText

data class ExerciseSelectionState(
    val opType: OpType,
    val target: Int,
    val rows: List<ExerciseRowUi> = emptyList(),
    val error: UiText? = null
) {
    /** Angekreuzt, in der Reihenfolge der OP-Art. */
    val selectedIds: List<ExerciseId> get() = rows.filter { it.isChecked }.map { it.id }
    val allIds: List<ExerciseId> get() = rows.map { it.id }
}

data class ExerciseRowUi(
    val id: ExerciseId,
    @StringRes val titleRes: Int,
    val isChecked: Boolean,
    val preset: Preset,
    val presetsEqual: Boolean     // Voll = Einfach (Seitheben, Mobilisation): beide leuchten
)

sealed interface ExerciseSelectionAction {
    data class OnExerciseClick(val id: ExerciseId) : ExerciseSelectionAction
    data class OnPresetSelect(val id: ExerciseId, val preset: Preset) : ExerciseSelectionAction
    data object OnErrorDismiss : ExerciseSelectionAction
}
