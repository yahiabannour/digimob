package de.mhh.digimob.feature.menu.presentation.selection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mhh.digimob.core.domain.exercise.ExerciseCatalog
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.OpType
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.domain.exercise.PresetRepository
import de.mhh.digimob.core.domain.onFailure
import de.mhh.digimob.core.presentation.titleRes
import de.mhh.digimob.core.presentation.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Auswahlseite einer OP-Art: Uebungen ankreuzen und je Uebung Voll/Einfach waehlen. Das Preset
 * wird sofort gespeichert und gilt auch fuer "Alle automatisch starten". Gestartet wird direkt
 * aus der Oberflaeche (reine Navigation, kein Event).
 */
class ExerciseSelectionViewModel(
    opType: OpType,
    target: Int,
    private val savedStateHandle: SavedStateHandle,
    private val presetRepository: PresetRepository
) : ViewModel() {

    private var checked: Set<ExerciseId> =
        savedStateHandle.get<List<String>>(KEY_CHECKED).orEmpty().mapNotNull(ExerciseId::fromKey).toSet()
    private var presets: Map<ExerciseId, Preset> = emptyMap()

    private val _state = MutableStateFlow(ExerciseSelectionState(opType = opType, target = target))
    val state = _state.asStateFlow()

    init {
        updateRows()
        presetRepository.observePresets()
            .onEach { presets = it; updateRows() }
            .launchIn(viewModelScope)
    }

    fun onAction(action: ExerciseSelectionAction) {
        when (action) {
            is ExerciseSelectionAction.OnExerciseClick -> {
                checked = if (action.id in checked) checked - action.id else checked + action.id
                savedStateHandle[KEY_CHECKED] = checked.map { it.key }
                updateRows()
            }
            is ExerciseSelectionAction.OnPresetSelect -> viewModelScope.launch {
                presetRepository.setPreset(action.id, action.preset)
                    .onFailure { error -> _state.update { it.copy(error = error.toUiText()) } }
            }
            ExerciseSelectionAction.OnErrorDismiss -> _state.update { it.copy(error = null) }
        }
    }

    private fun updateRows() {
        _state.update { state ->
            state.copy(
                rows = state.opType.exercises.map { id ->
                    ExerciseRowUi(
                        id = id,
                        titleRes = id.titleRes,
                        isChecked = id in checked,
                        preset = presets[id] ?: Preset.VOLL,
                        presetsEqual = ExerciseCatalog.presetsEqual(id)
                    )
                }
            )
        }
    }

    private companion object {
        const val KEY_CHECKED = "checked"
    }
}
