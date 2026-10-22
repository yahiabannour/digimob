package de.mhh.digimob.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import co.touchlab.kermit.Logger
import de.mhh.digimob.core.domain.DataError
import de.mhh.digimob.core.domain.EmptyResult
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.core.domain.exercise.ExerciseCatalog
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.domain.exercise.ThresholdRepository
import de.mhh.digimob.core.domain.exercise.Thresholds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Die Schwellen je Uebung in einem Preferences-DataStore (schwellen.py, statt schwellen.json).
 * Wie dort je Uebung entweder ein Preset oder die von Hand angepassten Werte:
 *  - "<uebung>" = "einfach"
 *  - "<uebung>.<schwelle>" = Wert, nur die Werte, die von Voll abweichen
 * Voll wird nicht gespeichert. Keine Patientendaten. DataStore schreibt atomar: ein Stromausfall
 * beim Speichern laesst die alte Datei ganz.
 */
class DataStoreThresholdRepository(private val dataStore: DataStore<Preferences>) : ThresholdRepository {

    private val log = Logger.withTag("Schwellen")

    override fun observeThresholds(): Flow<Map<ExerciseId, Thresholds>> = dataStore.data
        .catch { e ->
            if (e !is IOException) throw e
            log.e(e) { "Schwellen nicht lesbar, es gilt Voll" }
            emit(emptyPreferences())
        }
        .map { preferences -> ExerciseId.entries.associateWith { id -> preferences.thresholdsOf(id) } }

    override suspend fun setThresholds(id: ExerciseId, values: Thresholds): EmptyResult<DataError.Local> {
        return try {
            dataStore.edit { preferences -> preferences.store(id, values) }
            log.i { "Schwellen ${id.key}: $values" }
            Result.Success(Unit)
        } catch (e: IOException) {
            log.e(e) { "Schwellen ${id.key} nicht gespeichert" }
            Result.Error(if (e.isDiskFull()) DataError.Local.DISK_FULL else DataError.Local.UNKNOWN)
        }
    }

    /** schwellen.speichern(): ein Preset als Name, angepasste Werte als Abweichung von Voll. */
    private fun MutablePreferences.store(id: ExerciseId, values: Thresholds) {
        remove(presetKey(id))
        ExerciseCatalog.thresholds(id).forEach { remove(valueKey(id, it.key)) }
        val presets = ExerciseCatalog.presetsOf(id, values)
        when {
            Preset.VOLL in presets -> Unit
            Preset.EINFACH in presets -> this[presetKey(id)] = EINFACH
            else -> {
                val voll = ExerciseCatalog.values(id, Preset.VOLL)
                values.filter { (key, value) -> voll[key] != value }.forEach { (key, value) -> this[valueKey(id, key)] = value }
            }
        }
    }

    /** schwellen.laden(): Unbekanntes und Unpassendes wird geloggt, dann gilt fuer diese Uebung Voll. */
    private fun Preferences.thresholdsOf(id: ExerciseId): Thresholds {
        val voll = ExerciseCatalog.values(id, Preset.VOLL)
        val custom = ExerciseCatalog.thresholds(id)
            .mapNotNull { threshold -> this[valueKey(id, threshold.key)]?.let { threshold.key to it } }
            .toMap()
        val values = when (val preset = this[presetKey(id)]) {
            null -> voll + custom
            EINFACH -> ExerciseCatalog.values(id, Preset.EINFACH)
            else -> {
                // Unbekannter Wert (z.B. aus einer spaeteren Version)
                log.w { "Schwellen ${id.key}: \"$preset\" unbekannt, es gilt Voll" }
                return voll
            }
        }
        val error = ExerciseCatalog.validate(id, values)
        if (error != null) {
            log.w { "Schwellen ${id.key}: $values ungueltig ($error), es gilt Voll" }
            return voll
        }
        return values
    }

    private fun presetKey(id: ExerciseId) = stringPreferencesKey(id.key)

    private fun valueKey(id: ExerciseId, key: String) = doublePreferencesKey("${id.key}.$key")

    private fun IOException.isDiskFull(): Boolean =
        generateSequence<Throwable>(this) { it.cause }.any { it.message?.contains("ENOSPC") == true }

    private companion object {
        const val EINFACH = "einfach"
    }
}
