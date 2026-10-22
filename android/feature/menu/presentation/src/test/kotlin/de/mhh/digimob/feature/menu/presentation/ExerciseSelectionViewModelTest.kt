package de.mhh.digimob.feature.menu.presentation

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import de.mhh.digimob.core.domain.DataError
import de.mhh.digimob.core.domain.exercise.ExerciseCatalog
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.OpType
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.presentation.toUiText
import de.mhh.digimob.feature.menu.presentation.selection.ExerciseRowUi
import de.mhh.digimob.feature.menu.presentation.selection.ExerciseSelectionAction
import de.mhh.digimob.feature.menu.presentation.selection.ExerciseSelectionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseSelectionViewModelTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repository: FakeThresholdRepository = FakeThresholdRepository(),
        handle: SavedStateHandle = SavedStateHandle(),
        opType: OpType = OpType.BRUST_OP
    ) = ExerciseSelectionViewModel(opType, target = 7, savedStateHandle = handle, thresholdRepository = repository)

    private fun ExerciseSelectionViewModel.row(id: ExerciseId): ExerciseRowUi = state.value.rows.first { it.id == id }

    private fun ExerciseSelectionViewModel.drag(id: ExerciseId, key: String, vararg values: Double) {
        values.forEach { onAction(ExerciseSelectionAction.OnThresholdChange(id, key, it)) }
        onAction(ExerciseSelectionAction.OnThresholdChangeFinished(id))
    }

    @Test
    fun `Zeilen in der Reihenfolge der OP-Art mit gespeicherten Schwellen`() {
        val repository = FakeThresholdRepository(
            mapOf(ExerciseId.BUTTERFLY to ExerciseCatalog.values(ExerciseId.BUTTERFLY, Preset.EINFACH))
        )
        val state = viewModel(repository).state.value
        assertThat(state.rows.map { it.id })
            .containsExactly(ExerciseId.HALSWIRBELSAEULE, ExerciseId.BUTTERFLY, ExerciseId.ROTATION)
        assertThat(state.rows.map { it.presets })
            .containsExactly(setOf(Preset.VOLL), setOf(Preset.EINFACH), setOf(Preset.VOLL))
        assertThat(state.rows[1].thresholds.single().value).isEqualTo(1.5)
        assertThat(state.target).isEqualTo(7)
    }

    @Test
    fun `vor den gespeicherten Schwellen keine Zeilen, sonst stuende dort kurz Voll`() {
        val repository = FakeThresholdRepository(
            mapOf(ExerciseId.BUTTERFLY to ExerciseCatalog.values(ExerciseId.BUTTERFLY, Preset.EINFACH))
        ).apply { isLoaded.value = false }
        val viewModel = viewModel(repository)
        assertThat(viewModel.state.value.rows).isEmpty()

        repository.isLoaded.value = true
        assertThat(viewModel.row(ExerciseId.BUTTERFLY).presets).containsOnly(Preset.EINFACH)
    }

    @Test
    fun `Voll = Einfach leuchtet bei Seitheben und Mobilisation doppelt`() {
        val rows = viewModel(opType = OpType.BAUCH_OP).state.value.rows
        assertThat(rows.filter { it.presets.size == 2 }.map { it.id })
            .containsExactly(ExerciseId.SEITENHEBEN, ExerciseId.MOBILISATION_SCHULTER)
    }

    @Test
    fun `je Schwelle ein Regler mit Bereich, Rastpunkten und Einheit`() {
        val curls = viewModel(opType = OpType.BAUCH_OP).row(ExerciseId.BIZEPSCURLS).thresholds
        assertThat(curls.map { it.key }).containsExactly("min_angle", "max_shoulder")
        assertThat(curls[0].range).isEqualTo(30f..155f)
        assertThat(curls[0].steps).isEqualTo(124)                // 30, 31, ... 155: ganze Grad
        assertThat(curls[0].valueText).isEqualTo("110°")
        val butterfly = viewModel().row(ExerciseId.BUTTERFLY).thresholds.single()
        assertThat(butterfly.steps).isEqualTo(35)                // 1,20 ... 3,00 in 0,05
        assertThat(butterfly.valueText).isEqualTo("1,80x")
    }

    @Test
    fun `angekreuzt startet in der Reihenfolge der OP-Art, nicht der Klicks`() {
        val viewModel = viewModel()
        viewModel.onAction(ExerciseSelectionAction.OnExerciseClick(ExerciseId.ROTATION))
        viewModel.onAction(ExerciseSelectionAction.OnExerciseClick(ExerciseId.HALSWIRBELSAEULE))
        assertThat(viewModel.state.value.selectedIds).containsExactly(ExerciseId.HALSWIRBELSAEULE, ExerciseId.ROTATION)
    }

    @Test
    fun `zweimal tippen nimmt das Kreuz wieder weg`() {
        val viewModel = viewModel()
        repeat(2) { viewModel.onAction(ExerciseSelectionAction.OnExerciseClick(ExerciseId.BUTTERFLY)) }
        assertThat(viewModel.state.value.selectedIds).isEmpty()
    }

    @Test
    fun `alle starten nimmt jede Uebung, auch nicht angekreuzte`() {
        val viewModel = viewModel()
        viewModel.onAction(ExerciseSelectionAction.OnExerciseClick(ExerciseId.BUTTERFLY))
        assertThat(viewModel.state.value.allIds)
            .containsExactly(ExerciseId.HALSWIRBELSAEULE, ExerciseId.BUTTERFLY, ExerciseId.ROTATION)
    }

    @Test
    fun `Kreuze ueberstehen das Ende des Prozesses, Unbekanntes faellt heraus`() {
        val handle = SavedStateHandle()
        viewModel(handle = handle).onAction(ExerciseSelectionAction.OnExerciseClick(ExerciseId.ROTATION))
        handle["checked"] = handle.get<List<String>>("checked")!! + "gibt_es_nicht"
        assertThat(viewModel(handle = handle).state.value.selectedIds).containsExactly(ExerciseId.ROTATION)
    }

    @Test
    fun `Preset waehlen speichert seine Werte und zeigt es`() {
        val repository = FakeThresholdRepository()
        val viewModel = viewModel(repository)
        viewModel.onAction(ExerciseSelectionAction.OnPresetSelect(ExerciseId.ROTATION, Preset.EINFACH))
        assertThat(viewModel.row(ExerciseId.ROTATION).presets).containsOnly(Preset.EINFACH)
        assertThat(repository.thresholds.value.getValue(ExerciseId.ROTATION)).isEqualTo(mapOf("max_distance" to 1.3))
    }

    @Test
    fun `Ziehen zeigt den Wert sofort, gespeichert wird erst beim Loslassen, einmal`() {
        val repository = FakeThresholdRepository()
        val viewModel = viewModel(repository)
        viewModel.onAction(ExerciseSelectionAction.OnThresholdChange(ExerciseId.HALSWIRBELSAEULE, "max_angle", 40.2))
        assertThat(viewModel.row(ExerciseId.HALSWIRBELSAEULE).thresholds.single().valueText).isEqualTo("40°")
        assertThat(repository.saves).isEqualTo(0)
        viewModel.drag(ExerciseId.HALSWIRBELSAEULE, "max_angle", 41.0, 42.7)
        assertThat(repository.saves).isEqualTo(1)
        assertThat(repository.thresholds.value.getValue(ExerciseId.HALSWIRBELSAEULE)).isEqualTo(mapOf("max_angle" to 43.0))
    }

    @Test
    fun `von Hand angepasst leuchtet kein Preset, zurueck auf den Presetwert wieder`() {
        val viewModel = viewModel()
        viewModel.drag(ExerciseId.ROTATION, "max_distance", 1.6)
        assertThat(viewModel.row(ExerciseId.ROTATION).presets).isEmpty()
        assertThat(viewModel.row(ExerciseId.ROTATION).isInvalid).isFalse()
        // 1.3000000000000003 vom Regler: gerundet ist es genau Einfach
        viewModel.drag(ExerciseId.ROTATION, "max_distance", 1.3000000000000003)
        assertThat(viewModel.row(ExerciseId.ROTATION).presets).containsOnly(Preset.EINFACH)
    }

    @Test
    fun `losgelassen auf dem gespeicherten Wert speichert nicht`() {
        val repository = FakeThresholdRepository()
        val viewModel = viewModel(repository)
        viewModel.drag(ExerciseId.ROTATION, "max_distance", 2.0, 1.4)
        assertThat(repository.saves).isEqualTo(0)
    }

    @Test
    fun `ungueltige Werte werden nicht gespeichert, die Zeile zeigt es, ein Preset heilt es`() {
        val repository = FakeThresholdRepository()
        val viewModel = viewModel(repository, opType = OpType.BAUCH_OP)
        // Ein Wert ausserhalb des Bereichs kommt vom Regler nicht, hier steht er fuer jede Art Fehler aus pruefe()
        viewModel.drag(ExerciseId.ABDUKTION, "max_angle", 180.0)
        val row = viewModel.row(ExerciseId.ABDUKTION)
        assertThat(row.isInvalid).isTrue()
        assertThat(row.presets).isEmpty()
        assertThat(repository.saves).isEqualTo(0)
        viewModel.onAction(ExerciseSelectionAction.OnPresetSelect(ExerciseId.ABDUKTION, Preset.EINFACH))
        assertThat(viewModel.row(ExerciseId.ABDUKTION).isInvalid).isFalse()
        assertThat(viewModel.row(ExerciseId.ABDUKTION).thresholds.single().value).isEqualTo(120.0)
    }

    @Test
    fun `unbekannte Schwelle wird ignoriert`() {
        val viewModel = viewModel()
        viewModel.onAction(ExerciseSelectionAction.OnThresholdChange(ExerciseId.ROTATION, "min_angle", 50.0))
        assertThat(viewModel.row(ExerciseId.ROTATION).thresholds.map { it.key }).containsExactly("max_distance")
    }

    @Test
    fun `Speicherfehler zeigt eine Meldung, die Zeile zeigt den gespeicherten Stand, OK schliesst sie`() {
        val repository = FakeThresholdRepository().apply { error = DataError.Local.DISK_FULL }
        val viewModel = viewModel(repository)
        viewModel.drag(ExerciseId.ROTATION, "max_distance", 2.5)
        assertThat(viewModel.state.value.error).isEqualTo(DataError.Local.DISK_FULL.toUiText())
        assertThat(viewModel.row(ExerciseId.ROTATION).thresholds.single().value).isEqualTo(1.4)
        assertThat(viewModel.row(ExerciseId.ROTATION).presets).containsOnly(Preset.VOLL)
        viewModel.onAction(ExerciseSelectionAction.OnErrorDismiss)
        assertThat(viewModel.state.value.error).isNull()
    }

    @Test
    fun `Schwellen einer anderen Seite (gleiche Uebung, andere OP-Art) erscheinen hier`() {
        val repository = FakeThresholdRepository()
        val viewModel = viewModel(repository)
        repository.thresholds.value = repository.thresholds.value + (ExerciseId.HALSWIRBELSAEULE to mapOf("max_angle" to 50.0))
        assertThat(viewModel.state.value.rows.first().thresholds.single().value).isEqualTo(50.0)
    }
}
