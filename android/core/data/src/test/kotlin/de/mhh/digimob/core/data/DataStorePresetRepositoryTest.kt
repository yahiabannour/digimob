package de.mhh.digimob.core.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Preset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Mit echtem DataStore auf einer Datei im Temp-Ordner (Datei-I/O, also ein Integrationstest):
 * dass die Wahl einen Neustart uebersteht und eine kaputte oder fremde Datei Voll ergibt, kann
 * nur die echte Datei zeigen.
 */
class DataStorePresetRepositoryTest {

    @TempDir
    lateinit var dir: File

    private val file get() = dir.resolve("presets.preferences_pb")

    /** Ein "App-Start": eigener DataStore auf derselben Datei. */
    private suspend fun <T> withDataStore(block: suspend (DataStore<Preferences>) -> T): T {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        return try {
            block(createPresetDataStore(scope) { file })
        } finally {
            scope.cancel()
        }
    }

    private suspend fun <T> withRepository(block: suspend (DataStorePresetRepository) -> T): T =
        withDataStore { block(DataStorePresetRepository(it)) }

    @Test
    fun `ohne Datei gilt fuer jede Uebung Voll`() = runTest {
        val presets = withRepository { it.observePresets().first() }
        assertThat(presets).isEqualTo(ExerciseId.entries.associateWith { Preset.VOLL })
    }

    @Test
    fun `Einfach uebersteht einen Neustart, Voll setzt zurueck`() = runTest {
        withRepository {
            assertThat(it.setPreset(ExerciseId.BIZEPSCURLS, Preset.EINFACH)).isInstanceOf<Result.Success<Unit>>()
        }
        val afterRestart = withRepository { it.observePresets().first() }
        assertThat(afterRestart.getValue(ExerciseId.BIZEPSCURLS)).isEqualTo(Preset.EINFACH)
        assertThat(afterRestart.getValue(ExerciseId.ABDUKTION)).isEqualTo(Preset.VOLL)

        withRepository { it.setPreset(ExerciseId.BIZEPSCURLS, Preset.VOLL) }
        assertThat(withRepository { it.observePresets().first() }.getValue(ExerciseId.BIZEPSCURLS))
            .isEqualTo(Preset.VOLL)
    }

    @Test
    fun `kaputte Datei ergibt Voll statt eines Absturzes`() = runTest {
        file.writeBytes(byteArrayOf(0x7f, 0x00, 0x13, 0x37, 0x42))
        val presets = withRepository { it.observePresets().first() }
        assertThat(presets).isEqualTo(ExerciseId.entries.associateWith { Preset.VOLL })
    }

    @Test
    fun `unbekannter Wert in der Datei gilt als Voll, die anderen bleiben`() = runTest {
        withDataStore { dataStore ->
            dataStore.edit {
                it[stringPreferencesKey("abduktion")] = "mittel"
                it[stringPreferencesKey("rotation")] = "einfach"
            }
        }
        val presets = withRepository { it.observePresets().first() }
        assertThat(presets.getValue(ExerciseId.ABDUKTION)).isEqualTo(Preset.VOLL)
        assertThat(presets.getValue(ExerciseId.ROTATION)).isEqualTo(Preset.EINFACH)
    }
}
