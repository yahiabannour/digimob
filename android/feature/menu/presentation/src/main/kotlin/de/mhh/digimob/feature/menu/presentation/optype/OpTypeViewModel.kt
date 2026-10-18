package de.mhh.digimob.feature.menu.presentation.optype

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import de.mhh.digimob.core.domain.exercise.PostOpDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Operationsart und postoperativer Tag. Der Tag legt nur die Wiederholungen fest. */
class OpTypeViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    private val _state = MutableStateFlow(
        OpTypeState(
            day = savedStateHandle.get<String>(KEY_DAY)
                ?.let { name -> PostOpDay.entries.firstOrNull { it.name == name } }
                ?: PostOpDay.TAG_1
        )
    )
    val state = _state.asStateFlow()

    fun onAction(action: OpTypeAction) {
        when (action) {
            OpTypeAction.OnDayMenuClick -> _state.update { it.copy(isDayMenuExpanded = !it.isDayMenuExpanded) }
            OpTypeAction.OnDayMenuDismiss -> _state.update { it.copy(isDayMenuExpanded = false) }
            is OpTypeAction.OnDaySelect -> {
                savedStateHandle[KEY_DAY] = action.day.name
                _state.update { it.copy(day = action.day, isDayMenuExpanded = false) }
            }
        }
    }

    private companion object {
        const val KEY_DAY = "day"
    }
}
