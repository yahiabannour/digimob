package de.mhh.digimob.core.domain.exercise

import de.mhh.digimob.core.domain.DataError
import de.mhh.digimob.core.domain.EmptyResult
import kotlinx.coroutines.flow.Flow

/** Das auf der Auswahlseite gewaehlte Preset je Uebung, gespeichert ueber Neustarts hinweg. */
interface PresetRepository {
    /** Alle Uebungen, ohne gespeicherte Wahl VOLL. */
    fun observePresets(): Flow<Map<ExerciseId, Preset>>

    suspend fun setPreset(id: ExerciseId, preset: Preset): EmptyResult<DataError.Local>
}
