package de.mhh.digimob.feature.menu.presentation

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import de.mhh.digimob.core.domain.DataError
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.OpType
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.presentation.toUiText
import de.mhh.digimob.feature.menu.presentation.selection.ExerciseSelectionAction
import de.mhh.digimob.feature.menu.presentation.selection.ExerciseSelectionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
        repository: FakePresetRepository = FakePresetRepository(),
        handle: SavedStateHandle = SavedStateHandle(),
        opType: OpType = OpType.BRUST_OP
    ) = ExerciseSelectionViewModel(opType, target = 7, savedStateHandle = handle, presetRepository = repository)

    @Test
    fun `Zeilen in der Reihenfolge der OP-Art mit gespeichertem Preset`() {
        val state = viewModel(FakePresetRepository(mapOf(ExerciseId.BUTTERFLY to Preset.EINFACH))).state.value
        assertThat(state.rows.map { it.id })
            .containsExactly(ExerciseId.HALSWIRBELSAEULE, ExerciseId.BUTTERFLY, ExerciseId.ROTATION)
        assertThat(state.rows.map { it.preset }).containsExactly(Preset.VOLL, Preset.EINFACH, Preset.VOLL)
        assertThat(state.target).isEqualTo(7)
    }

    @Test
    fun `Voll = Einfach nur bei Seitheben und Mobilisation markiert`() {
        val rows = viewModel(opType = OpType.BAUCH_OP).state.value.rows
        assertThat(rows.filter { it.presetsEqual }.map { it.id })
            .containsExactly(ExerciseId.SEITENHEBEN, ExerciseId.MOBILISATION_SCHULTER)
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
    fun `Preset waehlen speichert und zeigt es`() = runTest {
        val repository = FakePresetRepository()
        val viewModel = viewModel(repository)
        viewModel.state.test {
            awaitItem()
            viewModel.onAction(ExerciseSelectionAction.OnPresetSelect(ExerciseId.ROTATION, Preset.EINFACH))
            assertThat(awaitItem().rows.first { it.id == ExerciseId.ROTATION }.preset).isEqualTo(Preset.EINFACH)
        }
        assertThat(repository.presets.value.getValue(ExerciseId.ROTATION)).isEqualTo(Preset.EINFACH)
    }

    @Test
    fun `Speicherfehler zeigt eine Meldung, das Preset bleibt, OK schliesst sie`() {
        val repository = FakePresetRepository().apply { error = DataError.Local.DISK_FULL }
        val viewModel = viewModel(repository)
        viewModel.onAction(ExerciseSelectionAction.OnPresetSelect(ExerciseId.ROTATION, Preset.EINFACH))
        assertThat(viewModel.state.value.error).isEqualTo(DataError.Local.DISK_FULL.toUiText())
        assertThat(viewModel.state.value.rows.first { it.id == ExerciseId.ROTATION }.preset).isEqualTo(Preset.VOLL)
        viewModel.onAction(ExerciseSelectionAction.OnErrorDismiss)
        assertThat(viewModel.state.value.error).isNull()
    }

    @Test
    fun `Preset einer anderen Seite (gleiche Uebung, andere OP-Art) erscheint hier`() {
        val repository = FakePresetRepository()
        val viewModel = viewModel(repository)
        repository.presets.value = repository.presets.value + (ExerciseId.HALSWIRBELSAEULE to Preset.EINFACH)
        assertThat(viewModel.state.value.rows.first().preset).isEqualTo(Preset.EINFACH)
    }
}
