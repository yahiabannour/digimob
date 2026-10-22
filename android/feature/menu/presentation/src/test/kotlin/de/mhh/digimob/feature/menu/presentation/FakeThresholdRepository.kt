package de.mhh.digimob.feature.menu.presentation

import de.mhh.digimob.core.domain.DataError
import de.mhh.digimob.core.domain.EmptyResult
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.core.domain.exercise.ExerciseCatalog
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.domain.exercise.ThresholdRepository
import de.mhh.digimob.core.domain.exercise.Thresholds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update

class FakeThresholdRepository(initial: Map<ExerciseId, Thresholds> = emptyMap()) : ThresholdRepository {
    val thresholds = MutableStateFlow(
        ExerciseId.entries.associateWith { initial[it] ?: ExerciseCatalog.values(it, Preset.VOLL) }
    )
    /** false: DataStore hat die Datei noch nicht gelesen, es kommt noch kein Wert. */
    val isLoaded = MutableStateFlow(true)
    var error: DataError.Local? = null
    var saves = 0

    override fun observeThresholds(): Flow<Map<ExerciseId, Thresholds>> =
        combine(isLoaded, thresholds) { loaded, values -> values.takeIf { loaded } }.filterNotNull()

    override suspend fun setThresholds(id: ExerciseId, values: Thresholds): EmptyResult<DataError.Local> {
        error?.let { return Result.Error(it) }
        saves++
        thresholds.update { it + (id to values) }
        return Result.Success(Unit)
    }
}
