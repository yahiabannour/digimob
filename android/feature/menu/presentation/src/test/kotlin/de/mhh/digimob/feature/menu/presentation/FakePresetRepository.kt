package de.mhh.digimob.feature.menu.presentation

import de.mhh.digimob.core.domain.DataError
import de.mhh.digimob.core.domain.EmptyResult
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.domain.exercise.PresetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakePresetRepository(initial: Map<ExerciseId, Preset> = emptyMap()) : PresetRepository {
    val presets = MutableStateFlow(ExerciseId.entries.associateWith { initial[it] ?: Preset.VOLL })
    var error: DataError.Local? = null

    override fun observePresets(): Flow<Map<ExerciseId, Preset>> = presets

    override suspend fun setPreset(id: ExerciseId, preset: Preset): EmptyResult<DataError.Local> {
        error?.let { return Result.Error(it) }
        presets.update { it + (id to preset) }
        return Result.Success(Unit)
    }
}
