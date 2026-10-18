package de.mhh.digimob.feature.training.presentation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import de.mhh.digimob.core.domain.DataError
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Pose
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.domain.exercise.Side
import de.mhh.digimob.core.presentation.UiText
import de.mhh.digimob.feature.training.domain.PoseFrame
import de.mhh.digimob.feature.training.domain.Recording
import de.mhh.digimob.feature.training.domain.RecordingList
import de.mhh.digimob.feature.training.domain.SourceError
import de.mhh.digimob.feature.training.presentation.recording.RecordingTestViewModel
import de.mhh.digimob.feature.training.presentation.recording.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingTestViewModelTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun frames(poses: List<Pose?>): List<Result<PoseFrame<String>, SourceError>> =
        poses.mapIndexed { i, pose -> Result.Success(PoseFrame("bild $i", pose, i / 30.0)) }

    private fun recording(name: String, expected: Int?, perSide: Map<Side, Int>? = null, preset: Preset = Preset.VOLL) =
        Recording(name, "/$name.mp4", ExerciseId.ABDUKTION, expected, perSide, preset)

    private fun viewModel(recordings: List<Recording>, videos: Map<String, List<Result<PoseFrame<String>, SourceError>>>, problems: List<String> = emptyList()) =
        RecordingTestViewModel(
            FakeRecordingRepository(Result.Success(RecordingList(recordings, problems))),
            FakeRecordingSources(videos),
            FakeClock()
        )

    @Test
    fun `jede Aufnahme der Reihe nach, OK nur wenn Summe und jede Seite stimmen`() {
        val twoReps = frames(oneRep() + oneRep())
        val viewModel = viewModel(
            listOf(
                recording("a/rec1", 2),                                             // OK
                recording("a/rec2", 3),                                             // zu wenig
                recording("a/rec3", 2, perSide = mapOf(Side.LEFT to 2, Side.RIGHT to 3)),  // eine Seite falsch
                recording("a/rec4", null)                                           // ohne Ground Truth
            ),
            mapOf("/a/rec1.mp4" to twoReps, "/a/rec2.mp4" to twoReps, "/a/rec3.mp4" to twoReps, "/a/rec4.mp4" to twoReps)
        )
        val state = viewModel.state.value
        assertThat(state.isRunning).isFalse()
        assertThat(state.results.map { it.verdict })
            .containsExactly(Verdict.OK, Verdict.DEVIATES, Verdict.DEVIATES, Verdict.NO_TRUTH)
        assertThat(state.results.first().counter).isEqualTo(mapOf(Side.LEFT to 2, Side.RIGHT to 2))
        assertThat(state.okCount).isEqualTo(1)
    }

    @Test
    fun `jede Aufnahme zaehlt mit ihrem eigenen Preset und frisch`() {
        // 140 Grad: nur mit Einfach eine Wiederholung
        val low = frames(List(6) { DOWN } + List(6) { arms(140.0) } + List(6) { DOWN })
        val viewModel = viewModel(
            listOf(recording("a/voll", 0), recording("a/einfach", 1, preset = Preset.EINFACH), recording("a/voll2", 0)),
            mapOf("/a/voll.mp4" to low, "/a/einfach.mp4" to low, "/a/voll2.mp4" to low)
        )
        assertThat(viewModel.state.value.results.map { it.total }).containsExactly(0, 1, 0)
        assertThat(viewModel.state.value.results.map { it.verdict }).containsExactly(Verdict.OK, Verdict.OK, Verdict.OK)
    }

    @Test
    fun `unlesbares Video wird vermerkt, die naechste Aufnahme laeuft`() {
        val viewModel = viewModel(
            listOf(recording("a/kaputt", 2), recording("a/gut", 1)),
            mapOf("/a/kaputt.mp4" to listOf(Result.Error(SourceError.VIDEO_UNREADABLE)), "/a/gut.mp4" to frames(oneRep()))
        )
        assertThat(viewModel.state.value.results.map { it.verdict }).containsExactly(Verdict.UNREADABLE, Verdict.OK)
    }

    @Test
    fun `fehlende videomodus txt sagt, wohin sie gehoert`() {
        val viewModel = RecordingTestViewModel(
            FakeRecordingRepository(Result.Error(DataError.Local.NOT_FOUND)),
            FakeRecordingSources(emptyMap()),
            FakeClock()
        )
        assertThat(viewModel.state.value.error).isEqualTo(UiText.StringResource(R.string.recording_missing))
        assertThat(viewModel.state.value.isRunning).isFalse()
    }

    @Test
    fun `Probleme aus videomodus txt werden angezeigt`() {
        val viewModel = viewModel(emptyList(), emptyMap(), problems = listOf("x: Datei nicht gefunden"))
        assertThat(viewModel.state.value.problems).containsExactly("x: Datei nicht gefunden")
    }
}
