package de.mhh.digimob.feature.training.presentation.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import de.mhh.digimob.core.domain.DataError
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.core.domain.exercise.Exercise
import de.mhh.digimob.core.domain.exercise.ExerciseCatalog
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Side
import de.mhh.digimob.core.domain.onFailure
import de.mhh.digimob.core.domain.onSuccess
import de.mhh.digimob.core.presentation.UiText
import de.mhh.digimob.feature.training.domain.GroundTruth
import de.mhh.digimob.feature.training.domain.PoseFrame
import de.mhh.digimob.feature.training.domain.RecordedFrame
import de.mhh.digimob.feature.training.domain.RecordingCamera
import de.mhh.digimob.feature.training.domain.RecordingRepository
import de.mhh.digimob.feature.training.domain.RecordingSummary
import de.mhh.digimob.feature.training.domain.Take
import de.mhh.digimob.feature.training.presentation.R
import de.mhh.digimob.feature.training.presentation.toUiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Aufnehmen mit Ground Truth (tools/record_exercise.py), nur im Debug-Build: Uebung und Preset
 * waehlen, aufnehmen, danach eintippen, wie viele Wiederholungen gemacht wurden, speichern oder
 * verwerfen. Jede Aufnahme in der Startposition der Uebung beginnen und beenden: der Systemtest
 * zaehlt ab dem ersten Frame. Ohne Ground Truth bleibt nichts liegen. Verschwindet die App (Home,
 * Bildschirm aus), endet eine laufende Aufnahme wie mit Stop und die Kamera geht aus, bis die App
 * wieder zu sehen ist.
 */
class RecordingViewModel<Image : Any>(
    private val camera: RecordingCamera<Image>,
    private val repository: RecordingRepository
) : ViewModel() {

    private val log = Logger.withTag("Aufnahme")

    private val _state = MutableStateFlow(RecordingState<Image>())
    val state = _state.asStateFlow()

    private var cameraJob: Job? = null
    private var hideJob: Job? = null
    private var isCameraAllowed = false
    private var isHidden = false
    private var take: Take? = null
    private val frames = mutableListOf<RecordedFrame>()
    private var firstTime: Double? = null
    private var size: Pair<Int, Int>? = null
    private var counter: Exercise? = null       // zaehlt mit, wie der Systemtest (ohne Ziel)

    fun onAction(action: RecordingAction) {
        val phase = _state.value.phase
        when (action) {
            is RecordingAction.OnCameraPermissionResult ->
                if (action.isGranted) allowCamera() else _state.update { it.copy(error = UiText.StringResource(R.string.error_permission)) }
            RecordingAction.OnExerciseMenuClick -> if (phase == RecordingPhase.READY) {
                _state.update { it.copy(isExerciseMenuExpanded = !it.isExerciseMenuExpanded) }
            }
            RecordingAction.OnExerciseMenuDismiss -> _state.update { it.copy(isExerciseMenuExpanded = false) }
            is RecordingAction.OnExerciseSelect -> if (phase == RecordingPhase.READY) {
                _state.update {
                    it.copy(
                        exercise = action.id,
                        isTwoSided = ExerciseCatalog.create(action.id, null, it.preset).sides.size > 1,
                        isExerciseMenuExpanded = false
                    )
                }
            }
            is RecordingAction.OnPresetSelect -> if (phase == RecordingPhase.READY) {
                _state.update { it.copy(preset = action.preset) }
            }
            RecordingAction.OnStartClick -> if (phase == RecordingPhase.READY && _state.value.isCameraRunning) start()
            RecordingAction.OnStopClick -> if (phase == RecordingPhase.RECORDING) stop()
            is RecordingAction.OnRepsChange -> if (phase == RecordingPhase.LABELING) {
                val value = action.value.coerceIn(0, MAX_REPS)
                _state.update { if (action.side == Side.RIGHT) it.copy(right = value) else it.copy(left = value) }
            }
            RecordingAction.OnSaveClick -> if (phase == RecordingPhase.LABELING) save()
            RecordingAction.OnDiscardClick -> if (phase == RecordingPhase.LABELING) discard()
            RecordingAction.OnErrorDismiss -> _state.update { it.copy(error = null) }
            RecordingAction.OnScreenHidden -> hide()
            RecordingAction.OnScreenShown -> show()
        }
    }

    private fun allowCamera() {
        isCameraAllowed = true
        if (!isHidden) startCamera()
    }

    private fun hide() {
        isHidden = true
        val stopping = if (_state.value.phase == RecordingPhase.RECORDING) stop() else null
        hideJob = viewModelScope.launch {
            stopping?.join()    // erst das Video fertig schreiben, dann die Kamera loesen
            stopCamera()
        }
    }

    private fun show() {
        isHidden = false
        // Noch nicht geloest (Video wird gerade fertig geschrieben): Kamera einfach weiter benutzen
        hideJob?.takeIf { it.isActive }?.cancel()
        hideJob = null
        if (isCameraAllowed) startCamera()
    }

    private fun stopCamera() {
        cameraJob?.cancel()
        cameraJob = null
        _state.update { it.copy(isCameraRunning = false) }
    }

    private fun startCamera() {
        if (cameraJob != null) return
        cameraJob = viewModelScope.launch {
            camera.frames().collect { result ->
                when (result) {
                    is Result.Success -> onFrame(result.data)
                    is Result.Error -> {
                        log.e { "Kamera: ${result.error}" }
                        _state.update { it.copy(isCameraRunning = false, error = result.error.toUiText()) }
                    }
                }
            }
        }
    }

    private fun onFrame(frame: PoseFrame<Image>) {
        _state.update { it.copy(isCameraRunning = true, frame = frame.image, pose = frame.pose) }
        if (_state.value.phase != RecordingPhase.RECORDING) return
        // Zeit ab dem ersten Frame der Aufnahme, wie Recording.add_frame()
        val start = firstTime ?: frame.timeSeconds.also { firstTime = it }
        val time = frame.timeSeconds - start
        frames += RecordedFrame(time, frame.pose)
        frame.pose?.let { if (size == null) size = it.width to it.height }
        val exercise = counter
        exercise?.update(frame.pose, time)
        _state.update {
            it.copy(
                seconds = time,
                frameCount = frames.size,
                hasPose = frame.pose != null,
                counted = exercise?.counter.orEmpty()
            )
        }
    }

    private fun start() {
        _state.update { it.copy(phase = RecordingPhase.STARTING, saved = null) }
        viewModelScope.launch {
            val current = _state.value
            when (val result = repository.newTake(current.exercise)) {
                is Result.Success -> {
                    take = result.data
                    frames.clear()
                    firstTime = null
                    size = null
                    counter = ExerciseCatalog.create(current.exercise, null, current.preset)
                    val hasVideo = camera.startVideo(result.data.videoPath)
                    log.i { "Aufnahme ${result.data.name} laeuft (${current.preset}, Video: $hasVideo)" }
                    _state.update {
                        it.copy(
                            phase = RecordingPhase.RECORDING,
                            hasVideo = hasVideo,
                            seconds = 0.0,
                            frameCount = 0,
                            counted = counter?.counter.orEmpty()
                        )
                    }
                }
                is Result.Error -> _state.update { it.copy(phase = RecordingPhase.READY, error = result.error.toRecordingUiText()) }
            }
        }
    }

    private fun stop(): Job {
        _state.update { it.copy(phase = RecordingPhase.STOPPING) }
        return viewModelScope.launch {
            val hasVideo = _state.value.hasVideo && camera.stopVideo()
            // Ground Truth eintippen; vorbelegt mit 0, nicht mit dem, was die App zaehlte
            _state.update { it.copy(phase = RecordingPhase.LABELING, hasVideo = hasVideo, left = 0, right = 0) }
        }
    }

    private fun save() {
        val current = _state.value
        val take = take ?: return
        val (width, height) = size ?: DEFAULT_SIZE
        val last = frames.lastOrNull()?.timeSeconds
        val summary = RecordingSummary(
            exercise = current.exercise,
            preset = current.preset,
            groundTruth = GroundTruth.of(current.left, current.right.takeIf { current.isTwoSided }),
            width = width,
            height = height,
            framesRecorded = frames.size,
            framesWithPose = frames.count { it.pose != null },
            measuredFps = if (frames.size >= 2 && last != null && last > 0) (frames.size - 1) / last else null,
            hasVideo = current.hasVideo
        )
        _state.update { it.copy(phase = RecordingPhase.SAVING) }
        viewModelScope.launch {
            repository.save(take, frames.toList(), summary)
                .onSuccess {
                    this@RecordingViewModel.take = null
                    _state.update { it.copy(phase = RecordingPhase.READY, saved = savedText(take, summary)) }
                }
                .onFailure { error -> _state.update { it.copy(phase = RecordingPhase.LABELING, error = error.toRecordingUiText()) } }
        }
    }

    private fun discard() {
        val take = take ?: return
        _state.update { it.copy(phase = RecordingPhase.SAVING) }
        viewModelScope.launch {
            repository.discard(take)
            this@RecordingViewModel.take = null
            log.i { "Aufnahme ${take.name} verworfen" }
            _state.update { it.copy(phase = RecordingPhase.READY) }
        }
    }

    private fun savedText(take: Take, summary: RecordingSummary): UiText {
        val fps = summary.measuredFps?.let { String.format(Locale.GERMANY, "%.1f", it) } ?: "?"
        return UiText.StringResource(
            if (summary.hasVideo) R.string.recording_saved_video else R.string.recording_saved,
            arrayOf(take.name, summary.groundTruth.reps, summary.framesRecorded, fps)
        )
    }

    private fun DataError.toRecordingUiText(): UiText = UiText.StringResource(
        if (this == DataError.Local.DISK_FULL) R.string.recording_error_disk_full else R.string.recording_error_unknown
    )

    private companion object {
        const val MAX_REPS = 99
        val DEFAULT_SIZE = 640 to 480
    }
}
