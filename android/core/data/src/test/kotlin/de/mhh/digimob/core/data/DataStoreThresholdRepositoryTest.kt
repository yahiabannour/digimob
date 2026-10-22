package de.mhh.digimob.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.core.domain.exercise.ExerciseCatalog
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

/**
 * Mit echtem DataStore auf einer Datei im Temp-Ordner (Datei-I/O, also ein Integrationstest):
 * dass die Schwellen einen Neustart ueberstehen und eine kaputte oder fremde Datei Voll ergibt, kann
 * nur die echte Datei zeigen.
 */
class DataStoreThresholdRepositoryTest {

    @TempDir
    lateinit var dir: File

    private val file get() = dir.resolve("presets.preferences_pb")

    private val voll = ExerciseId.entries.associateWith { ExerciseCatalog.values(it, Preset.VOLL) }

    /** Ein "App-Start": eigener DataStore auf derselben Datei. */
    private suspend fun <T> withDataStore(block: suspend (DataStore<Preferences>) -> T): T {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        return try {
            block(createThresholdDataStore(scope) { file })
        } finally {
            scope.cancel()
        }
    }

    private suspend fun <T> withRepository(block: suspend (DataStoreThresholdRepository) -> T): T =
        withDataStore { block(DataStoreThresholdRepository(it)) }

    private suspend fun stored(): Map<String, Any> = withDataStore { it.data.first().asMap().mapKeys { (key, _) -> key.name } }

    @Test
    fun `ohne Datei gilt fuer jede Uebung Voll`() = runTest {
        assertThat(withRepository { it.observeThresholds().first() }).isEqualTo(voll)
    }

    @Test
    fun `Einfach uebersteht einen Neustart und steht als Name in der Datei`() = runTest {
        val einfach = ExerciseCatalog.values(ExerciseId.BIZEPSCURLS, Preset.EINFACH)
        withRepository {
            assertThat(it.setThresholds(ExerciseId.BIZEPSCURLS, einfach)).isInstanceOf<Result.Success<Unit>>()
        }
        val afterRestart = withRepository { it.observeThresholds().first() }
        assertThat(afterRestart).isEqualTo(voll + (ExerciseId.BIZEPSCURLS to einfach))
        assertThat(stored()).isEqualTo(mapOf("bizepscurls" to "einfach"))
    }

    @Test
    fun `angepasste Werte, nur die Abweichung von Voll wird gespeichert`() = runTest {
        val custom = mapOf("min_angle" to 95.0, "max_shoulder" to 50.0)
        withRepository { it.setThresholds(ExerciseId.BIZEPSCURLS, custom) }
        assertThat(withRepository { it.observeThresholds().first() }.getValue(ExerciseId.BIZEPSCURLS)).isEqualTo(custom)
        assertThat(stored()).isEqualTo(mapOf("bizepscurls.min_angle" to 95.0))
    }

    @Test
    fun `zurueck auf Voll loescht alles, auch angepasste Werte`() = runTest {
        withRepository { it.setThresholds(ExerciseId.ABDUKTION, mapOf("max_angle" to 99.0)) }
        withRepository { it.setThresholds(ExerciseId.ABDUKTION, ExerciseCatalog.values(ExerciseId.ABDUKTION, Preset.VOLL)) }
        assertThat(stored()).isEqualTo(emptyMap())
    }

    @Test
    fun `von angepasst auf Einfach bleibt kein alter Wert stehen`() = runTest {
        withRepository { it.setThresholds(ExerciseId.ROTATION, mapOf("max_distance" to 2.0)) }
        withRepository { it.setThresholds(ExerciseId.ROTATION, ExerciseCatalog.values(ExerciseId.ROTATION, Preset.EINFACH)) }
        assertThat(stored()).isEqualTo(mapOf("rotation" to "einfach"))
    }

    @Test
    fun `Voll = Einfach (Seitheben) wird nicht gespeichert`() = runTest {
        withRepository { it.setThresholds(ExerciseId.SEITENHEBEN, ExerciseCatalog.values(ExerciseId.SEITENHEBEN, Preset.EINFACH)) }
        assertThat(stored()).isEqualTo(emptyMap())
    }

    @Test
    fun `kaputte Datei ergibt Voll statt eines Absturzes`() = runTest {
        file.writeBytes(byteArrayOf(0x7f, 0x00, 0x13, 0x37, 0x42))
        assertThat(withRepository { it.observeThresholds().first() }).isEqualTo(voll)
    }

    @Test
    fun `unbekanntes Preset gilt als Voll, die anderen Uebungen bleiben`() = runTest {
        withDataStore { dataStore ->
            dataStore.edit {
                it[stringPreferencesKey("abduktion")] = "mittel"
                it[stringPreferencesKey("rotation")] = "einfach"
            }
        }
        val thresholds = withRepository { it.observeThresholds().first() }
        assertThat(thresholds.getValue(ExerciseId.ABDUKTION)).isEqualTo(voll.getValue(ExerciseId.ABDUKTION))
        assertThat(thresholds.getValue(ExerciseId.ROTATION)).isEqualTo(ExerciseCatalog.values(ExerciseId.ROTATION, Preset.EINFACH))
    }

    @Test
    fun `Wert ausserhalb des Bereichs in der Datei gilt als Voll`() = runTest {
        withDataStore { dataStore ->
            dataStore.edit {
                it[doublePreferencesKey("abduktion.max_angle")] = 500.0
                it[doublePreferencesKey("butterfly.max_distance")] = 2.0
            }
        }
        val thresholds = withRepository { it.observeThresholds().first() }
        assertThat(thresholds.getValue(ExerciseId.ABDUKTION)).isEqualTo(voll.getValue(ExerciseId.ABDUKTION))
        assertThat(thresholds.getValue(ExerciseId.BUTTERFLY)).isEqualTo(mapOf("max_distance" to 2.0))
    }
}
