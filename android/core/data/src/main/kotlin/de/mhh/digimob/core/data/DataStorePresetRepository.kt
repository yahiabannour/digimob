package de.mhh.digimob.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import co.touchlab.kermit.Logger
import de.mhh.digimob.core.domain.DataError
import de.mhh.digimob.core.domain.EmptyResult
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.domain.exercise.PresetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Das Preset je Uebung in einem Preferences-DataStore (ersetzt schwellen.json): ein Schluessel je
 * Uebung mit "einfach"; Voll wird nicht gespeichert. Keine Patientendaten.
 * DataStore schreibt atomar: ein Stromausfall beim Speichern laesst die alte Datei ganz.
 */
class DataStorePresetRepository(private val dataStore: DataStore<Preferences>) : PresetRepository {

    private val log = Logger.withTag("Presets")

    override fun observePresets(): Flow<Map<ExerciseId, Preset>> = dataStore.data
        .catch { e ->
            if (e !is IOException) throw e
            log.e(e) { "Presets nicht lesbar, es gilt Voll" }
            emit(emptyPreferences())
        }
        .map { preferences -> ExerciseId.entries.associateWith { id -> preferences.presetOf(id) } }

    override suspend fun setPreset(id: ExerciseId, preset: Preset): EmptyResult<DataError.Local> {
        return try {
            dataStore.edit { preferences ->
                if (preset == Preset.VOLL) preferences.remove(key(id)) else preferences[key(id)] = EINFACH
            }
            log.i { "Preset ${id.key}: $preset" }
            Result.Success(Unit)
        } catch (e: IOException) {
            log.e(e) { "Preset ${id.key} nicht gespeichert" }
            Result.Error(if (e.isDiskFull()) DataError.Local.DISK_FULL else DataError.Local.UNKNOWN)
        }
    }

    private fun Preferences.presetOf(id: ExerciseId): Preset = when (val value = this[key(id)]) {
        null -> Preset.VOLL
        EINFACH -> Preset.EINFACH
        else -> {
            // Unbekannter Wert (z.B. aus einer spaeteren Version): wie schwellen.laden() Voll
            log.w { "Preset ${id.key}: \"$value\" unbekannt, es gilt Voll" }
            Preset.VOLL
        }
    }

    private fun key(id: ExerciseId) = stringPreferencesKey(id.key)

    private fun IOException.isDiskFull(): Boolean =
        generateSequence<Throwable>(this) { it.cause }.any { it.message?.contains("ENOSPC") == true }

    private companion object {
        const val EINFACH = "einfach"
    }
}
