package de.mhh.digimob.feature.training.presentation.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.core.domain.exercise.ExerciseCatalog
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.domain.exercise.PresetRepository
import de.mhh.digimob.core.presentation.UiText
import de.mhh.digimob.core.presentation.toUiText
import de.mhh.digimob.feature.training.domain.Clock
import de.mhh.digimob.feature.training.domain.LivePoseSource
import de.mhh.digimob.feature.training.domain.Phase
import de.mhh.digimob.feature.training.domain.SessionState
import de.mhh.digimob.feature.training.domain.Speaker
import de.mhh.digimob.feature.training.domain.TrainingSession
import de.mhh.digimob.feature.training.presentation.BarFeedbacks
import de.mhh.digimob.feature.training.presentation.R
import de.mhh.digimob.feature.training.presentation.toUiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Die Uebungsseite live (Hauptprogramm.start_exercises + loop): frische Uebungen mit dem Ziel des
 * Tages und dem gespeicherten Preset je Uebung, die Kamera liefert Bilder mit Pose, die
 * TrainingSession zaehlt. Jedes Bild treibt auch die Zeit der Session (Einfuehrung, "Prima"), die
 * Kamera laeuft durchgehend. Gestartet wird erst mit der Kamera-Erlaubnis.
 */
class TrainingViewModel<Image : Any>(
    private val exerciseIds: List<ExerciseId>,
    private val target: Int,
    private val presetRepository: PresetRepository,
    private val poseSource: LivePoseSource<Image>,
    private val speaker: Speaker,
    private val clock: Clock
) : ViewModel() {

    private val log = Logger.withTag("Training")

    private val _state = MutableStateFlow(TrainingState<Image>(count = exerciseIds.size))
    val state = _state.asStateFlow()

    private val _events = Channel<TrainingEvent>()
    val events = _events.receiveAsFlow()

    private val zoom = MutableStateFlow(false)
    private val barFeedbacks = BarFeedbacks(clock)
    private var session: TrainingSession? = null
    private var job: Job? = null
    private var ended = false

    fun onAction(action: TrainingAction) {
        when (action) {
            is TrainingAction.OnCameraPermissionResult ->
                if (action.isGranted) start() else fail(UiText.StringResource(R.string.error_permission))
            TrainingAction.OnPauseClick -> {
                val paused = !_state.value.isPaused
                session?.setPaused(paused)
                _state.update { it.copy(isPaused = paused) }
                log.i { if (paused) "Die Uebung wird angehalten" else "Die Uebung wird weiter durchgefuehrt" }
            }
            TrainingAction.OnKeypointsClick -> _state.update { it.copy(showKeypoints = !it.showKeypoints) }
            TrainingAction.OnZoomClick -> {
                zoom.value = !zoom.value
                _state.update { it.copy(isZoomOn = zoom.value) }
            }
            TrainingAction.OnDemoPlayed -> session?.let {
                it.onDemoPlayed()
                publish(it.state.value)
            }
            TrainingAction.OnStopClick -> end()
        }
    }

    private fun start() {
        if (job != null || ended) return
        job = viewModelScope.launch {
            val presets = presetRepository.observePresets().first()
            exerciseIds.forEach { log.i { "${it.key}: ${presets[it] ?: Preset.VOLL}, Ziel $target" } }
            val session = TrainingSession(
                exercises = exerciseIds.map { ExerciseCatalog.create(it, target, presets[it] ?: Preset.VOLL) },
                speaker = speaker,
                clock = clock
            )
            this@TrainingViewModel.session = session
            session.start()
            publish(session.state.value)

            poseSource.frames(zoom).collect { result ->
                when (result) {
                    is Result.Success -> if (!session.isPaused) {
                        val frame = result.data
                        session.tick()
                        session.onFrame(frame.pose, frame.timeSeconds)
                        _state.update { it.copy(frame = frame.image, pose = frame.pose) }
                        publish(session.state.value)
                    }
                    is Result.Error -> {
                        log.e { "Bildquelle: ${result.error}" }
                        fail(result.error.toUiText())
                    }
                }
            }
        }
    }

    private fun publish(session: SessionState) {
        _state.update {
            it.copy(
                phase = session.phase,
                exercise = session.exercise ?: it.exercise,
                index = session.index,
                barStyle = session.barStyle,
                bars = barFeedbacks.update(session.bars),
                message = if (session.phase == Phase.INTRO) null else session.message.toUiText()
            )
        }
        if (session.phase == Phase.FINISHED && !ended) {
            end()
            _events.trySend(TrainingEvent.Finished)
        }
    }

    /** Das letzte Bild bleibt stehen, Stop fuehrt zurueck. */
    private fun fail(error: UiText) {
        end()
        _state.update { it.copy(error = error) }
    }

    private fun end() {
        if (ended) return
        ended = true
        session?.stop()
        job?.cancel()
    }

    override fun onCleared() {
        end()
    }
}
