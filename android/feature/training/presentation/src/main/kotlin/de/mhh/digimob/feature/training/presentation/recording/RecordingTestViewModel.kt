package de.mhh.digimob.feature.training.presentation.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import de.mhh.digimob.core.domain.DataError
import de.mhh.digimob.core.domain.Result
import de.mhh.digimob.core.domain.exercise.ExerciseCatalog
import de.mhh.digimob.core.presentation.UiText
import de.mhh.digimob.core.presentation.toUiText
import de.mhh.digimob.feature.training.domain.Announcement
import de.mhh.digimob.feature.training.domain.Clock
import de.mhh.digimob.feature.training.domain.Recording
import de.mhh.digimob.feature.training.domain.RecordingPoseSourceFactory
import de.mhh.digimob.feature.training.domain.RecordingRepository
import de.mhh.digimob.feature.training.domain.Speaker
import de.mhh.digimob.feature.training.domain.TrainingSession
import de.mhh.digimob.feature.training.presentation.BarFeedbacks
import de.mhh.digimob.feature.training.presentation.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Aufnahmen testen (aufnahmen_test.py): jede Aufnahme aus videomodus.txt durch dieselbe Schleife
 * wie live, nur mit dem Video als Bildquelle, ohne Einfuehrung, ohne Ziel, ohne Ansagen und mit
 * dem Preset der Aufnahme. Jedes Bild der Reihe nach mit seiner Zeit im Video: gleiches Ergebnis
 * bei jedem Durchlauf. "OK" nur, wenn die Summe und jede Seite stimmen (wie systest.py).
 */
class RecordingTestViewModel<Image : Any>(
    private val repository: RecordingRepository,
    private val sources: RecordingPoseSourceFactory<Image>,
    private val clock: Clock
) : ViewModel() {

    private val log = Logger.withTag("AufnahmenTest")

    private val _state = MutableStateFlow(RecordingTestState<Image>())
    val state = _state.asStateFlow()

    private val job = viewModelScope.launch {
        when (val list = repository.recordings()) {
            is Result.Error -> _state.update { it.copy(isRunning = false, error = list.error.toRecordingUiText()) }
            is Result.Success -> {
                val recordings = list.data.recordings
                _state.update { it.copy(count = recordings.size, problems = list.data.problems) }
                recordings.forEachIndexed { index, recording ->
                    val result = run(recording, index)
                    log.i { "${result.name}: ${result.total}/${result.expectedReps ?: "?"} ${recording.preset} ${result.counter} ${result.verdict}" }
                    _state.update { it.copy(results = it.results + result) }
                }
                _state.update { it.copy(isRunning = false, current = null, frame = null, pose = null, bars = emptyMap()) }
                log.i { "Aufnahmen testen: ${_state.value.okCount} von ${recordings.size} OK" }
            }
        }
    }

    /** Stop: der laufende Durchgang endet, die Ergebnisse bleiben. */
    fun stop() {
        job.cancel()
        _state.update { it.copy(isRunning = false) }
    }

    private suspend fun run(recording: Recording, index: Int): RecordingResultUi {
        val exercise = ExerciseCatalog.create(recording.exercise, target = null, preset = recording.preset)
        val session = TrainingSession(listOf(exercise), Silent, clock, intro = false).apply { start() }
        val bars = BarFeedbacks(clock)
        _state.update {
            it.copy(current = recording.name, currentExercise = recording.exercise, index = index, barStyle = exercise.barStyle)
        }
        var unreadable = false
        sources.create(recording.videoPath).frames(NO_ZOOM).collect { result ->
            when (result) {
                is Result.Success -> {
                    session.onFrame(result.data.pose, result.data.timeSeconds)
                    _state.update {
                        it.copy(frame = result.data.image, pose = result.data.pose, bars = bars.update(session.state.value.bars))
                    }
                }
                is Result.Error -> unreadable = true
            }
        }
        val expected = recording.expectedFor(exercise.sides)
        val verdict = when {
            unreadable -> Verdict.UNREADABLE
            recording.expectedReps == null || expected == null -> Verdict.NO_TRUTH
            exercise.total == recording.expectedReps && exercise.counter == expected -> Verdict.OK
            else -> Verdict.DEVIATES
        }
        return RecordingResultUi(
            name = recording.name,
            total = exercise.total,
            counter = exercise.counter,
            expectedReps = recording.expectedReps,
            expectedPerSide = expected,
            verdict = verdict
        )
    }

    private fun DataError.Local.toRecordingUiText(): UiText =
        if (this == DataError.Local.NOT_FOUND) UiText.StringResource(R.string.recording_missing) else toUiText()

    /** Ohne Ziel gibt es keine Ansagen; Stop braucht keine Stimme. */
    private object Silent : Speaker {
        override fun say(announcement: Announcement) = Unit
        override fun stop() = Unit
    }

    private companion object {
        val NO_ZOOM = MutableStateFlow(false)
    }
}
