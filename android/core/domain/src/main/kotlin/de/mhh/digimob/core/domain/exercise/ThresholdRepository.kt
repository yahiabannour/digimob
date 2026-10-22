package de.mhh.digimob.core.domain.exercise

import de.mhh.digimob.core.domain.DataError
import de.mhh.digimob.core.domain.EmptyResult
import kotlinx.coroutines.flow.Flow

/**
 * Die auf der Auswahlseite eingestellten Schwellen je Uebung, gespeichert ueber Neustarts hinweg
 * (schwellen.py). Ein Preset oder von Hand angepasste Werte.
 */
interface ThresholdRepository {
    /** Alle Uebungen mit allen ihren Schwellen; ohne gespeicherte Wahl die Werte von Voll. */
    fun observeThresholds(): Flow<Map<ExerciseId, Thresholds>>

    /** Speichert gueltige Werte (ExerciseCatalog.validate() vorher pruefen). */
    suspend fun setThresholds(id: ExerciseId, values: Thresholds): EmptyResult<DataError.Local>
}
