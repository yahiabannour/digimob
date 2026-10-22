package de.mhh.digimob.feature.menu.presentation.selection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import de.mhh.digimob.core.domain.exercise.ExerciseCatalog
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.OpType
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.domain.exercise.ThresholdRepository
import de.mhh.digimob.core.domain.exercise.Thresholds
import de.mhh.digimob.core.domain.onFailure
import de.mhh.digimob.core.domain.onSuccess
import de.mhh.digimob.core.presentation.format
import de.mhh.digimob.core.presentation.thresholdLabelRes
import de.mhh.digimob.core.presentation.titleRes
import de.mhh.digimob.core.presentation.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Auswahlseite einer OP-Art: Uebungen ankreuzen und je Uebung die Schwellen einstellen, mit Voll/
 * Einfach oder den Schiebereglern (Hauptprogramm.schwellen_regler). Jede gueltige Aenderung wird
 * gespeichert und gilt auch fuer "Alle automatisch starten" und die andere OP-Art. Gestartet wird
 * direkt aus der Oberflaeche (reine Navigation, kein Event).
 */
class ExerciseSelectionViewModel(
    opType: OpType,
    target: Int,
    private val savedStateHandle: SavedStateHandle,
    private val thresholdRepository: ThresholdRepository
) : ViewModel() {

    private val log = Logger.withTag("Schwellen")

    private var checked: Set<ExerciseId> =
        savedStateHandle.get<List<String>>(KEY_CHECKED).orEmpty().mapNotNull(ExerciseId::fromKey).toSet()
    private var saved: Map<ExerciseId, Thresholds> = emptyMap()

    // Reglerstellungen, die (noch) nicht gespeichert sind: waehrend gezogen wird, oder ungueltig
    private var drafts: Map<ExerciseId, Thresholds> = emptyMap()
    private var invalid: Set<ExerciseId> = emptySet()

    private val _state = MutableStateFlow(ExerciseSelectionState(opType = opType, target = target))
    val state = _state.asStateFlow()

    init {
        // Zeilen erst mit den gespeicherten Schwellen: sonst zeigte die Seite beim Einblenden kurz Voll
        thresholdRepository.observeThresholds()
            .onEach { saved = it; updateRows() }
            .launchIn(viewModelScope)
    }

    fun onAction(action: ExerciseSelectionAction) {
        when (action) {
            is ExerciseSelectionAction.OnExerciseClick -> {
                checked = if (action.id in checked) checked - action.id else checked + action.id
                savedStateHandle[KEY_CHECKED] = checked.map { it.key }
                updateRows()
            }
            is ExerciseSelectionAction.OnPresetSelect -> save(action.id, ExerciseCatalog.values(action.id, action.preset))
            is ExerciseSelectionAction.OnThresholdChange -> {
                val threshold = ExerciseCatalog.thresholds(action.id).firstOrNull { it.key == action.key } ?: return
                drafts = drafts + (action.id to current(action.id) + (action.key to threshold.round(action.value)))
                updateRows()
            }
            is ExerciseSelectionAction.OnThresholdChangeFinished -> {
                val values = drafts[action.id] ?: return
                val error = ExerciseCatalog.validate(action.id, values)
                if (error != null) {
                    // Nicht uebernehmen, die Zeile zeigt "ungueltig" (read_schwellen)
                    log.i { "Schwellen ${action.id.key} ungueltig: $error" }
                    invalid = invalid + action.id
                    updateRows()
                } else {
                    save(action.id, values)
                }
            }
            ExerciseSelectionAction.OnErrorDismiss -> _state.update { it.copy(error = null) }
        }
    }

    private fun current(id: ExerciseId): Thresholds =
        drafts[id] ?: saved[id] ?: ExerciseCatalog.values(id, Preset.VOLL)

    private fun save(id: ExerciseId, values: Thresholds) {
        invalid = invalid - id
        if (values == saved[id]) {
            drafts = drafts - id
            updateRows()
            return
        }
        drafts = drafts + (id to values)    // bis die Speicherung bestaetigt ist
        updateRows()
        viewModelScope.launch {
            thresholdRepository.setThresholds(id, values)
                .onSuccess { saved = saved + (id to values) }
                .onFailure { error -> _state.update { it.copy(error = error.toUiText()) } }
            // Auch nach einem Fehler: die Zeile zeigt, was gespeichert ist
            drafts = drafts - id
            updateRows()
        }
    }

    private fun updateRows() {
        _state.update { state ->
            state.copy(
                rows = state.opType.exercises.map { id ->
                    val values = current(id)
                    val isInvalid = id in invalid
                    ExerciseRowUi(
                        id = id,
                        titleRes = id.titleRes,
                        isChecked = id in checked,
                        presets = if (isInvalid) emptySet() else ExerciseCatalog.presetsOf(id, values),
                        isInvalid = isInvalid,
                        thresholds = ExerciseCatalog.thresholds(id).map { threshold ->
                            val value = values.getValue(threshold.key)
                            ThresholdUi(
                                key = threshold.key,
                                labelRes = id.thresholdLabelRes(threshold.key),
                                value = value,
                                valueText = threshold.format(value),
                                range = threshold.range.start.toFloat()..threshold.range.endInclusive.toFloat(),
                                // Rastpunkte zwischen den Enden, im Abstand der Einheit (1 Grad, 0,05x)
                                steps = ((threshold.range.endInclusive - threshold.range.start) / threshold.unit.step)
                                    .roundToInt() - 1
                            )
                        }
                    )
                }
            )
        }
    }

    private companion object {
        const val KEY_CHECKED = "checked"
    }
}
