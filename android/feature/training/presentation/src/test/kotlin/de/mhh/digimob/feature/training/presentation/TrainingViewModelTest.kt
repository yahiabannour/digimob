package de.mhh.digimob.feature.training.presentation

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import de.mhh.digimob.core.domain.exercise.BarStyle
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.ExerciseCatalog
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.domain.exercise.Thresholds
import de.mhh.digimob.core.domain.exercise.Side
import de.mhh.digimob.core.presentation.UiText
import de.mhh.digimob.feature.training.domain.Announcement
import de.mhh.digimob.feature.training.domain.Phase
import de.mhh.digimob.feature.training.domain.SourceError
import de.mhh.digimob.feature.training.presentation.training.TrainingAction
import de.mhh.digimob.feature.training.presentation.training.TrainingEvent
import de.mhh.digimob.feature.training.presentation.training.TrainingViewModel
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
class TrainingViewModelTest {

    private val clock = FakeClock()
    private val speaker = FakeSpeaker()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private var camera = FakeLivePoseSource()

    private fun viewModel(
        ids: List<ExerciseId> = listOf(ExerciseId.ABDUKTION),
        target: Int = 1,
        thresholds: Map<ExerciseId, Thresholds> = emptyMap()
    ): TrainingViewModel<String> {
        camera = FakeLivePoseSource()   // je ViewModel eine eigene Kamera
        return TrainingViewModel(ids, target, FakeThresholdRepository(thresholds), camera, speaker, clock)
    }

    /** Einfuehrung zu Ende: Demo durch und 10 s vorbei. */
    private fun TrainingViewModel<String>.skipIntro() {
        onAction(TrainingAction.OnDemoPlayed)
        clock.now += 10.0
        camera.send(DOWN, clock.now)
    }

    private fun feed(vararg poses: List<de.mhh.digimob.core.domain.exercise.Pose>) {
        poses.toList().flatten().forEach {
            clock.now += 1 / 30.0
            camera.send(it, clock.now)
        }
    }

    @Test
    fun `ohne Kamera-Erlaubnis startet nichts und es kommt eine Meldung`() {
        val viewModel = viewModel()
        viewModel.onAction(TrainingAction.OnCameraPermissionResult(isGranted = false))
        assertThat(camera.collections).isEqualTo(0)
        assertThat(viewModel.state.value.error).isEqualTo(UiText.StringResource(R.string.error_permission))
        assertThat(speaker.said).isEqualTo(emptyList<Announcement>())
    }

    @Test
    fun `mit Erlaubnis beginnt die Einfuehrung mit Ansage, die Kamera laeuft einmal`() {
        val viewModel = viewModel()
        viewModel.onAction(TrainingAction.OnCameraPermissionResult(isGranted = true))
        viewModel.onAction(TrainingAction.OnCameraPermissionResult(isGranted = true))   // doppelt: einmal
        assertThat(viewModel.state.value.phase).isEqualTo(Phase.INTRO)
        assertThat(viewModel.state.value.exercise).isEqualTo(ExerciseId.ABDUKTION)
        assertThat(speaker.said).isEqualTo(listOf<Announcement>(Announcement.Intro(ExerciseId.ABDUKTION)))
        assertThat(camera.collections).isEqualTo(1)
    }

    @Test
    fun `Bilder zeigen Bild, Pose und Balken je Arm`() {
        val viewModel = viewModel()
        viewModel.onAction(TrainingAction.OnCameraPermissionResult(true))
        viewModel.skipIntro()
        camera.send(UP, clock.now + 0.1, image = "bild 2")
        val state = viewModel.state.value
        assertThat(state.phase).isEqualTo(Phase.COUNTING)
        assertThat(state.frame).isEqualTo("bild 2")
        assertThat(state.pose).isNotNull()
        assertThat(state.barStyle).isEqualTo(BarStyle.SPLIT)
        assertThat(state.bars.getValue(Side.LEFT).count).isEqualTo(1)
    }

    @Test
    fun `die gespeicherten Schwellen gelten, Preset wie von Hand`() {
        // 140 Grad erreicht das Ziel von Einfach (120) und von Hand 135, nicht das von Voll (150)
        val lowRep = List(6) { DOWN } + List(6) { arms(140.0) } + List(6) { DOWN }
        val cases = listOf(
            ExerciseCatalog.values(ExerciseId.ABDUKTION, Preset.VOLL) to Phase.COUNTING,
            ExerciseCatalog.values(ExerciseId.ABDUKTION, Preset.EINFACH) to Phase.DONE,
            mapOf("max_angle" to 135.0) to Phase.DONE
        )
        for ((values, phase) in cases) {
            val viewModel = viewModel(thresholds = mapOf(ExerciseId.ABDUKTION to values))
            viewModel.onAction(TrainingAction.OnCameraPermissionResult(true))
            viewModel.skipIntro()
            feed(lowRep)
            assertThat(viewModel.state.value.phase).isEqualTo(phase)
            viewModel.onAction(TrainingAction.OnStopClick)
        }
    }

    @Test
    fun `Pause friert ein, Bilder werden nicht gezaehlt`() {
        val viewModel = viewModel()
        viewModel.onAction(TrainingAction.OnCameraPermissionResult(true))
        viewModel.skipIntro()
        viewModel.onAction(TrainingAction.OnPauseClick)
        feed(oneRep())
        camera.send(UP, clock.now, image = "waehrend der Pause")
        assertThat(viewModel.state.value.isPaused).isTrue()
        assertThat(viewModel.state.value.phase).isEqualTo(Phase.COUNTING)
        assertThat(viewModel.state.value.frame).isEqualTo("bild")      // das Bild steht still
        viewModel.onAction(TrainingAction.OnPauseClick)
        feed(oneRep())
        assertThat(viewModel.state.value.phase).isEqualTo(Phase.DONE)
    }

    @Test
    fun `App weg - Uebung haelt an, Kamera aus, zurueck - Kamera an, weiter erst mit Weiter`() {
        val viewModel = viewModel()
        viewModel.onAction(TrainingAction.OnCameraPermissionResult(true))
        viewModel.skipIntro()
        viewModel.onAction(TrainingAction.OnScreenHidden)
        assertThat(viewModel.state.value.isPaused).isTrue()
        assertThat(camera.isRunning).isFalse()

        viewModel.onAction(TrainingAction.OnScreenShown)
        assertThat(camera.isRunning).isTrue()
        assertThat(camera.collections).isEqualTo(2)
        feed(oneRep())
        assertThat(viewModel.state.value.phase).isEqualTo(Phase.COUNTING)  // noch angehalten
        viewModel.onAction(TrainingAction.OnPauseClick)
        feed(oneRep())
        assertThat(viewModel.state.value.phase).isEqualTo(Phase.DONE)
    }

    @Test
    fun `schon angehalten, App weg und zurueck - bleibt angehalten`() {
        val viewModel = viewModel()
        viewModel.onAction(TrainingAction.OnCameraPermissionResult(true))
        viewModel.onAction(TrainingAction.OnPauseClick)
        viewModel.onAction(TrainingAction.OnScreenHidden)
        viewModel.onAction(TrainingAction.OnScreenShown)
        assertThat(viewModel.state.value.isPaused).isTrue()
    }

    @Test
    fun `Seite erscheint vor der Erlaubnis - die Kamera startet erst mit ihr, einmal`() {
        val viewModel = viewModel()
        viewModel.onAction(TrainingAction.OnScreenShown)
        assertThat(camera.collections).isEqualTo(0)
        viewModel.onAction(TrainingAction.OnCameraPermissionResult(true))
        assertThat(camera.collections).isEqualTo(1)
        assertThat(viewModel.state.value.isPaused).isFalse()
    }

    @Test
    fun `Erlaubnis kommt, waehrend die App weg ist - angehalten, Kamera erst bei der Rueckkehr`() {
        val viewModel = viewModel()
        viewModel.onAction(TrainingAction.OnScreenHidden)
        viewModel.onAction(TrainingAction.OnCameraPermissionResult(true))
        assertThat(camera.collections).isEqualTo(0)
        assertThat(viewModel.state.value.isPaused).isTrue()
        viewModel.onAction(TrainingAction.OnScreenShown)
        assertThat(camera.isRunning).isTrue()
    }

    @Test
    fun `nach Stop startet die Rueckkehr die Kamera nicht wieder`() {
        val viewModel = viewModel()
        viewModel.onAction(TrainingAction.OnCameraPermissionResult(true))
        viewModel.onAction(TrainingAction.OnStopClick)
        viewModel.onAction(TrainingAction.OnScreenHidden)
        viewModel.onAction(TrainingAction.OnScreenShown)
        assertThat(camera.isRunning).isFalse()
        assertThat(camera.collections).isEqualTo(1)
    }

    @Test
    fun `Doppeltipp auf die Demo - es wird sofort gezaehlt`() {
        val viewModel = viewModel()
        viewModel.onAction(TrainingAction.OnCameraPermissionResult(true))
        viewModel.onAction(TrainingAction.OnDemoSkip)
        assertThat(viewModel.state.value.phase).isEqualTo(Phase.COUNTING)
        feed(oneRep())
        assertThat(viewModel.state.value.phase).isEqualTo(Phase.DONE)
    }

    @Test
    fun `Doppeltipp vor der Erlaubnis tut nichts`() {
        val viewModel = viewModel()
        viewModel.onAction(TrainingAction.OnDemoSkip)
        viewModel.onAction(TrainingAction.OnCameraPermissionResult(true))
        assertThat(viewModel.state.value.phase).isEqualTo(Phase.INTRO)
    }

    @Test
    fun `Zoom schaltet die Bildquelle um`() {
        val viewModel = viewModel()
        viewModel.onAction(TrainingAction.OnCameraPermissionResult(true))
        viewModel.onAction(TrainingAction.OnZoomClick)
        assertThat(camera.lastZoom!!.value).isTrue()
        assertThat(viewModel.state.value.isZoomOn).isTrue()
        viewModel.onAction(TrainingAction.OnZoomClick)
        assertThat(camera.lastZoom!!.value).isFalse()
    }

    @Test
    fun `Kamerafehler zeigt eine Meldung und beendet die Ansagen, das letzte Bild bleibt`() {
        val viewModel = viewModel()
        viewModel.onAction(TrainingAction.OnCameraPermissionResult(true))
        viewModel.skipIntro()
        val stopsBefore = speaker.stops
        camera.fail(SourceError.CAMERA_FAILED)
        assertThat(viewModel.state.value.error).isEqualTo(UiText.StringResource(R.string.error_camera_failed))
        assertThat(viewModel.state.value.frame).isEqualTo("bild")
        assertThat(speaker.stops).isEqualTo(stopsBefore + 1)
    }

    @Test
    fun `alle Uebungen fertig meldet Finished, genau einmal`() = runTest {
        val viewModel = viewModel()
        viewModel.events.test {
            viewModel.onAction(TrainingAction.OnCameraPermissionResult(true))
            viewModel.skipIntro()
            feed(oneRep())
            assertThat(viewModel.state.value.phase).isEqualTo(Phase.DONE)
            clock.now += 5.0
            camera.send(DOWN, clock.now)
            assertThat(awaitItem()).isEqualTo(TrainingEvent.Finished)
            camera.send(DOWN, clock.now + 1)
            expectNoEvents()
        }
    }

    @Test
    fun `Stop meldet kein Finished, verstummt und nimmt keine Bilder mehr`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(TrainingAction.OnCameraPermissionResult(true))
        viewModel.events.test {
            viewModel.onAction(TrainingAction.OnStopClick)
            viewModel.onAction(TrainingAction.OnDemoPlayed)     // die Demo meldet sich noch nach dem Stop
            expectNoEvents()
        }
        assertThat(speaker.stops).isEqualTo(2)      // vor der ersten Uebung und beim Stop
        camera.send(DOWN, 1.0, image = "nach Stop")
        assertThat(viewModel.state.value.frame).isNull()
    }

    @Test
    fun `Meldung erst nach der Einfuehrung`() {
        val viewModel = viewModel()
        viewModel.onAction(TrainingAction.OnCameraPermissionResult(true))
        assertThat(viewModel.state.value.message).isNull()
        viewModel.skipIntro()
        assertThat(viewModel.state.value.message).isNotNull()
    }

    @Test
    fun `Ansagen kommen aus der Session, Start beim ersten Bild nach der Einfuehrung`() {
        val viewModel = viewModel(target = 5)
        viewModel.onAction(TrainingAction.OnCameraPermissionResult(true))
        viewModel.skipIntro()
        assertThat(speaker.said).contains(Announcement.Start(5))
    }
}
