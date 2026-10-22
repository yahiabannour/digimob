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

/**
 * Eine Uebung mit ihren Schwellen. [presets]: die Presets mit genau diesen Werten (leuchten); beide
 * bei Voll = Einfach, keins bei von Hand angepasst. [isInvalid]: die Regler stehen auf Werten, die
 * nicht zusammenpassen, gespeichert ist noch der vorige Stand.
 */
data class ExerciseRowUi(
    val id: ExerciseId,
    @StringRes val titleRes: Int,
    val isChecked: Boolean,
    val presets: Set<Preset>,
    val isInvalid: Boolean,
    val thresholds: List<ThresholdUi>
)

/** Ein Schieberegler: [value] gerundet wie gespeichert, [valueText] mit Einheit ("150°", "1,80x"). */
data class ThresholdUi(
    val key: String,
    @StringRes val labelRes: Int,
    val value: Double,
    val valueText: String,
    val range: ClosedFloatingPointRange<Float>,
    val steps: Int
)

sealed interface ExerciseSelectionAction {
    data class OnExerciseClick(val id: ExerciseId) : ExerciseSelectionAction
    data class OnPresetSelect(val id: ExerciseId, val preset: Preset) : ExerciseSelectionAction
    /** Waehrend der Regler gezogen wird: nur anzeigen. */
    data class OnThresholdChange(val id: ExerciseId, val key: String, val value: Double) : ExerciseSelectionAction
    /** Regler losgelassen: pruefen und speichern. */
    data class OnThresholdChangeFinished(val id: ExerciseId) : ExerciseSelectionAction
    data object OnErrorDismiss : ExerciseSelectionAction
}
