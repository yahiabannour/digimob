package de.mhh.digimob.feature.training.presentation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import de.mhh.digimob.core.domain.DataError
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Pose
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.domain.exercise.Side
import de.mhh.digimob.core.presentation.UiText
import de.mhh.digimob.feature.training.domain.GroundTruth
import de.mhh.digimob.feature.training.domain.SourceError
import de.mhh.digimob.feature.training.presentation.recording.RecordingAction
import de.mhh.digimob.feature.training.presentation.recording.RecordingPhase
import de.mhh.digimob.feature.training.presentation.recording.RecordingViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingViewModelTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val camera = FakeRecordingCamera()
    private val repository = FakeRecordingRepository()
    private var time = 100.0    // Kamerazeit, beginnt nicht bei 0

    /** Mit Erlaubnis und einem ersten Bild: bereit zum Aufnehmen. */
    private fun readyViewModel(): RecordingViewModel<String> = RecordingViewModel(camera, repository).apply {
        onAction(RecordingAction.OnCameraPermissionResult(true))
        send(DOWN)
    }

    private fun send(vararg poses: Pose?) {
        poses.forEach {
            time += 0.1
            camera.send(it, time)
        }
    }

    private fun RecordingViewModel<String>.record(vararg poses: Pose?) {
        onAction(RecordingAction.OnStartClick)
        send(*poses)
        onAction(RecordingAction.OnStopClick)
    }

    @Test
    fun `ohne Kamera-Erlaubnis keine Kamera und eine Meldung`() {
        val viewModel = RecordingViewModel(camera, repository)
        viewModel.onAction(RecordingAction.OnCameraPermissionResult(false))
        send(DOWN)
        assertThat(viewModel.state.value.isCameraRunning).isFalse()
        assertThat(viewModel.state.value.error).isEqualTo(UiText.StringResource(R.string.error_permission))
    }

    @Test
    fun `Start erst, wenn die Kamera Bilder liefert`() {
        val viewModel = RecordingViewModel(camera, repository)
        viewModel.onAction(RecordingAction.OnCameraPermissionResult(true))
        viewModel.onAction(RecordingAction.OnStartClick)
        assertThat(viewModel.state.value.phase).isEqualTo(RecordingPhase.READY)
        send(DOWN)
        viewModel.onAction(RecordingAction.OnStartClick)
        assertThat(viewModel.state.value.phase).isEqualTo(RecordingPhase.RECORDING)
    }

    @Test
    fun `Bilder vor dem Start gehoeren nicht zur Aufnahme, die Zeit beginnt beim ersten Bild`() {
        val viewModel = readyViewModel()
        send(UP, UP)
        viewModel.record(DOWN, null, DOWN)
        viewModel.onAction(RecordingAction.OnSaveClick)
        val (frames, _) = repository.saved.getValue("abduktion/rec1")
        assertThat(frames.map { it.timeSeconds }.map { Math.round(it * 10) / 10.0 }).containsExactly(0.0, 0.1, 0.2)
        assertThat(frames[1].pose).isNull()
    }

    @Test
    fun `die App zaehlt waehrend der Aufnahme mit, wie der Systemtest`() {
        val viewModel = readyViewModel()
        viewModel.onAction(RecordingAction.OnStartClick)
        send(*oneRep().toTypedArray())
        assertThat(viewModel.state.value.counted).isEqualTo(mapOf(Side.LEFT to 1, Side.RIGHT to 1))
        assertThat(viewModel.state.value.frameCount).isEqualTo(18)
    }

    @Test
    fun `das Preset der Aufnahme gilt fuers Mitzaehlen`() {
        // 140 Grad erreicht nur das Ziel von Einfach (120), nicht das von Voll (150)
        val lowRep = List(6) { DOWN } + List(6) { arms(140.0) } + List(6) { DOWN }
        for ((preset, expected) in listOf(Preset.VOLL to 0, Preset.EINFACH to 1)) {
            val ownCamera = FakeRecordingCamera()   // jede Fake-Kamera liefert nur einem Sammler
            val viewModel = RecordingViewModel(ownCamera, FakeRecordingRepository())
            viewModel.onAction(RecordingAction.OnCameraPermissionResult(true))
            ownCamera.send(DOWN, 1.0)
            viewModel.onAction(RecordingAction.OnPresetSelect(preset))
            viewModel.onAction(RecordingAction.OnStartClick)
            lowRep.forEachIndexed { i, pose -> ownCamera.send(pose, 2.0 + i * 0.1) }
            assertThat(viewModel.state.value.counted.getValue(Side.LEFT), "$preset").isEqualTo(expected)
        }
    }

    @Test
    fun `Speichern schreibt Ground Truth, Groesse, Frames und Bildrate`() {
        val viewModel = readyViewModel()
        viewModel.onAction(RecordingAction.OnPresetSelect(Preset.EINFACH))
        viewModel.record(DOWN, null, DOWN, DOWN, null)
        viewModel.onAction(RecordingAction.OnRepsChange(Side.LEFT, 5))
        viewModel.onAction(RecordingAction.OnRepsChange(Side.RIGHT, 4))
        viewModel.onAction(RecordingAction.OnSaveClick)
        val (_, summary) = repository.saved.getValue("abduktion/rec1")
        assertThat(summary.exercise).isEqualTo(ExerciseId.ABDUKTION)
        assertThat(summary.preset).isEqualTo(Preset.EINFACH)
        assertThat(summary.groundTruth).isEqualTo(GroundTruth(4, mapOf(Side.LEFT to 5, Side.RIGHT to 4)))
        assertThat(summary.width to summary.height).isEqualTo(640 to 480)
        assertThat(summary.framesRecorded).isEqualTo(5)
        assertThat(summary.framesWithPose).isEqualTo(3)
        assertThat(Math.round(summary.measuredFps!! * 100) / 100.0).isEqualTo(10.0)   // 4 Abstaende in 0,4 s
        assertThat(viewModel.state.value.phase).isEqualTo(RecordingPhase.READY)
        assertThat(viewModel.state.value.saved).isNotNull()
    }

    @Test
    fun `Uebung mit einer Seite - nur eine Zahl, keine Angabe pro Seite`() {
        val viewModel = readyViewModel()
        viewModel.onAction(RecordingAction.OnExerciseSelect(ExerciseId.BUTTERFLY))
        assertThat(viewModel.state.value.isTwoSided).isFalse()
        viewModel.record(DOWN)
        viewModel.onAction(RecordingAction.OnRepsChange(Side.BOTH, 3))
        viewModel.onAction(RecordingAction.OnSaveClick)
        assertThat(repository.saved.getValue("butterfly/rec1").second.groundTruth).isEqualTo(GroundTruth(3, null))
    }

    @Test
    fun `Ground Truth beginnt bei 0, nicht bei der Zahl der App, und bleibt in 0 bis 99`() {
        val viewModel = readyViewModel()
        viewModel.onAction(RecordingAction.OnStartClick)
        send(*oneRep().toTypedArray())
        viewModel.onAction(RecordingAction.OnStopClick)
        assertThat(viewModel.state.value.left to viewModel.state.value.right).isEqualTo(0 to 0)
        viewModel.onAction(RecordingAction.OnRepsChange(Side.LEFT, -1))
        viewModel.onAction(RecordingAction.OnRepsChange(Side.RIGHT, 100))
        assertThat(viewModel.state.value.left to viewModel.state.value.right).isEqualTo(0 to 99)
    }

    @Test
    fun `nach dem Stopp kommen keine Bilder mehr in die Aufnahme`() {
        val viewModel = readyViewModel()
        viewModel.record(DOWN, DOWN)
        send(UP, UP, UP)
        viewModel.onAction(RecordingAction.OnSaveClick)
        assertThat(repository.saved.getValue("abduktion/rec1").first.size).isEqualTo(2)
    }

    @Test
    fun `Video - Pfad der Aufnahme, unvollstaendig oder nicht moeglich steht nicht in der summary`() {
        val viewModel = readyViewModel()
        viewModel.record(DOWN)
        assertThat(camera.videoPaths).containsExactly("/aufnahmen/abduktion/rec1/video.mp4")
        viewModel.onAction(RecordingAction.OnSaveClick)
        assertThat(repository.saved.getValue("abduktion/rec1").second.hasVideo).isTrue()

        camera.videoComplete = false
        viewModel.record(DOWN)
        viewModel.onAction(RecordingAction.OnSaveClick)
        assertThat(repository.saved.getValue("abduktion/rec2").second.hasVideo).isFalse()

        camera.videoPossible = false
        val stopsBefore = camera.stops
        viewModel.onAction(RecordingAction.OnStartClick)
        assertThat(viewModel.state.value.hasVideo).isFalse()            // die Seite sagt es
        viewModel.onAction(RecordingAction.OnStopClick)
        assertThat(camera.stops).isEqualTo(stopsBefore)                // kein Video zu beenden
    }

    @Test
    fun `App weg waehrend der Aufnahme - endet wie Stop, Video fertig, Kamera aus, Eingabe danach`() {
        val viewModel = readyViewModel()
        viewModel.onAction(RecordingAction.OnStartClick)
        send(DOWN, DOWN)
        viewModel.onAction(RecordingAction.OnScreenHidden)
        assertThat(viewModel.state.value.phase).isEqualTo(RecordingPhase.LABELING)
        assertThat(camera.stops).isEqualTo(1)
        assertThat(camera.isRunning).isFalse()

        viewModel.onAction(RecordingAction.OnScreenShown)
        assertThat(camera.isRunning).isTrue()
        send(UP)
        viewModel.onAction(RecordingAction.OnSaveClick)
        val (frames, summary) = repository.saved.getValue("abduktion/rec1")
        assertThat(frames.size).isEqualTo(2)
        assertThat(summary.hasVideo).isTrue()
    }

    @Test
    fun `App weg vor der Aufnahme - Kamera aus, Start erst wieder mit Bildern`() {
        val viewModel = readyViewModel()
        viewModel.onAction(RecordingAction.OnScreenHidden)
        assertThat(camera.isRunning).isFalse()
        assertThat(viewModel.state.value.isCameraRunning).isFalse()
        viewModel.onAction(RecordingAction.OnStartClick)
        assertThat(viewModel.state.value.phase).isEqualTo(RecordingPhase.READY)

        viewModel.onAction(RecordingAction.OnScreenShown)
        send(DOWN)
        viewModel.onAction(RecordingAction.OnStartClick)
        assertThat(viewModel.state.value.phase).isEqualTo(RecordingPhase.RECORDING)
    }

    @Test
    fun `zurueck, bevor das Video fertig ist - die Kamera bleibt an`() {
        val finish = CompletableDeferred<Unit>()
        camera.videoFinish = finish
        val viewModel = readyViewModel()
        viewModel.onAction(RecordingAction.OnStartClick)
        viewModel.onAction(RecordingAction.OnScreenHidden)
        assertThat(camera.isRunning).isTrue()          // erst das Video fertig schreiben
        viewModel.onAction(RecordingAction.OnScreenShown)
        finish.complete(Unit)
        assertThat(viewModel.state.value.phase).isEqualTo(RecordingPhase.LABELING)
        assertThat(camera.isRunning).isTrue()
        assertThat(camera.collections).isEqualTo(1)
    }

    @Test
    fun `Erlaubnis kommt, waehrend die App weg ist - Kamera erst bei der Rueckkehr`() {
        val viewModel = RecordingViewModel(camera, repository)
        viewModel.onAction(RecordingAction.OnScreenHidden)
        viewModel.onAction(RecordingAction.OnCameraPermissionResult(true))
        assertThat(camera.isRunning).isFalse()
        viewModel.onAction(RecordingAction.OnScreenShown)
        assertThat(camera.isRunning).isTrue()
    }

    @Test
    fun `Verwerfen behaelt nichts`() {
        val viewModel = readyViewModel()
        viewModel.record(DOWN)
        viewModel.onAction(RecordingAction.OnDiscardClick)
        assertThat(repository.discarded).containsExactly("abduktion/rec1")
        assertThat(repository.saved).isEqualTo(emptyMap())
        assertThat(viewModel.state.value.phase).isEqualTo(RecordingPhase.READY)
    }

    @Test
    fun `Speicherfehler - Meldung, Eingabe bleibt, nochmal speichern geht`() {
        val viewModel = readyViewModel()
        viewModel.record(DOWN)
        viewModel.onAction(RecordingAction.OnRepsChange(Side.LEFT, 2))
        repository.saveError = DataError.Local.DISK_FULL
        viewModel.onAction(RecordingAction.OnSaveClick)
        assertThat(viewModel.state.value.phase).isEqualTo(RecordingPhase.LABELING)
        assertThat(viewModel.state.value.error).isEqualTo(
            UiText.StringResource(R.string.recording_error_disk_full)
        )
        assertThat(viewModel.state.value.left).isEqualTo(2)
        repository.saveError = null
        viewModel.onAction(RecordingAction.OnSaveClick)
        assertThat(repository.saved.keys.toList()).containsExactly("abduktion/rec1")
    }

    @Test
    fun `Ordner nicht angelegt - Meldung, keine Aufnahme`() {
        val viewModel = readyViewModel()
        repository.newTakeError = DataError.Local.UNKNOWN
        viewModel.onAction(RecordingAction.OnStartClick)
        assertThat(viewModel.state.value.phase).isEqualTo(RecordingPhase.READY)
        assertThat(viewModel.state.value.error).isNotNull()
        assertThat(camera.videoPaths).isEmpty()
    }

    @Test
    fun `waehrend der Aufnahme lassen sich Uebung und Preset nicht aendern`() {
        val viewModel = readyViewModel()
        viewModel.onAction(RecordingAction.OnStartClick)
        viewModel.onAction(RecordingAction.OnExerciseSelect(ExerciseId.ROTATION))
        viewModel.onAction(RecordingAction.OnPresetSelect(Preset.EINFACH))
        viewModel.onAction(RecordingAction.OnExerciseMenuClick)
        assertThat(viewModel.state.value.exercise).isEqualTo(ExerciseId.ABDUKTION)
        assertThat(viewModel.state.value.preset).isEqualTo(Preset.VOLL)
        assertThat(viewModel.state.value.isExerciseMenuExpanded).isFalse()
    }

    @Test
    fun `doppelt Start oder Speichern legt nur eine Aufnahme an`() {
        val viewModel = readyViewModel()
        repeat(2) { viewModel.onAction(RecordingAction.OnStartClick) }
        send(DOWN)
        viewModel.onAction(RecordingAction.OnStopClick)
        repeat(2) { viewModel.onAction(RecordingAction.OnSaveClick) }
        assertThat(repository.saved.keys.toList()).containsExactly("abduktion/rec1")
        viewModel.record(DOWN)
        viewModel.onAction(RecordingAction.OnSaveClick)
        assertThat(repository.saved.keys.toList()).containsExactly("abduktion/rec1", "abduktion/rec2")
    }

    @Test
    fun `Kamera faellt aus - Meldung, kein Start mehr`() {
        val viewModel = readyViewModel()
        camera.fail(SourceError.CAMERA_FAILED)
        assertThat(viewModel.state.value.isCameraRunning).isFalse()
        assertThat(viewModel.state.value.error).isEqualTo(SourceError.CAMERA_FAILED.toUiText())
        viewModel.onAction(RecordingAction.OnStartClick)
        assertThat(viewModel.state.value.phase).isEqualTo(RecordingPhase.READY)
    }
}
