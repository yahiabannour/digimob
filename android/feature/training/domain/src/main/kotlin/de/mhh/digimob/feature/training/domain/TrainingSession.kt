package de.mhh.digimob.feature.training.domain

import de.mhh.digimob.core.domain.exercise.Bar
import de.mhh.digimob.core.domain.exercise.BarStyle
import de.mhh.digimob.core.domain.exercise.Exercise
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.ExerciseMessage
import de.mhh.digimob.core.domain.exercise.Pose
import de.mhh.digimob.core.domain.exercise.Side
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Uhr in Sekunden (monoton), austauschbar fuer Tests. */
fun interface Clock {
    fun nowSeconds(): Double
}

enum class Phase { INTRO, COUNTING, DONE, FINISHED }

/**
 * Was die Oberflaeche zeigt. [bars] null = alle Balken leer (Einfuehrung, "Prima").
 * [exercise] null nur nach dem Ende ([Phase.FINISHED]).
 */
data class SessionState(
    val phase: Phase,
    val exercise: ExerciseId?,
    val index: Int,                 // die wievielte Uebung (0-basiert)
    val count: Int,                 // so viele Uebungen insgesamt
    val barStyle: BarStyle,
    val bars: Map<Side, Bar>?,
    val message: ExerciseMessage,
    val counter: Map<Side, Int>     // gezaehlt je Seite (Aufnahmen testen)
)

/**
 * Fuehrt eine Warteschlange frischer Uebungen durch Einfuehrung, Zaehlen und Fertig
 * (uebungslauf.py). Kennt weder Kamera noch Oberflaeche:
 *  - [onFrame] mit jeder erkannten Pose (live und im Aufnahmen-Test)
 *  - [tick] regelmaessig, auch ohne Bilder: Ende von Einfuehrung und "Prima"
 *  - [onDemoPlayed], wenn die 3D-Demo einmal ganz durchgelaufen ist
 * Pausenzeit zaehlt nicht zur Einfuehrung und zu "Prima".
 *
 * intro = false (Aufnahmen testen): ohne Demo und Ansage, gezaehlt wird ab dem ersten Bild.
 */
class TrainingSession(
    exercises: List<Exercise>,
    private val speaker: Speaker,
    private val clock: Clock,
    private val intro: Boolean = true
) {
    private val queue = ArrayDeque(exercises)
    private val total = exercises.size
    private val milestones = Milestones(speaker)

    private lateinit var exercise: Exercise
    private var index = -1
    private var phase = Phase.FINISHED
    private var phaseSince = 0.0
    private var demoPlays = 0
    private var pausedSince: Double? = null

    private val _state = MutableStateFlow(snapshot(null))
    val state: StateFlow<SessionState> = _state.asStateFlow()

    val current: Exercise? get() = if (phase == Phase.FINISHED) null else exercise

    val isPaused: Boolean get() = pausedSince != null

    /** Erste Uebung beginnen. */
    fun start() {
        next()
    }

    fun onFrame(pose: Pose?, t: Double) {
        if (isPaused || phase == Phase.INTRO || phase == Phase.FINISHED) return
        exercise.update(pose, t)
        // Ansagen nur waehrend gezaehlt wird, nur mit gueltigem Messwert, ohne Ziel nie
        val target = exercise.target
        if (phase == Phase.COUNTING && exercise.valid && target != null) {
            milestones.update(exercise.total, target)
        }
        if (phase == Phase.COUNTING && exercise.done) setPhase(Phase.DONE)
        publish()
    }

    fun onDemoPlayed() {
        if (phase == Phase.INTRO) demoPlays++
        tick()
    }

    fun tick() {
        if (isPaused) return
        val elapsed = clock.nowSeconds() - phaseSince
        when (phase) {
            Phase.INTRO -> if (demoPlays >= DEMO_PLAYS && elapsed >= INTRO_SECONDS) {
                setPhase(Phase.COUNTING)
                publish()
            }
            Phase.DONE -> if (elapsed >= DONE_SECONDS) next()
            Phase.COUNTING, Phase.FINISHED -> Unit
        }
    }

    fun setPaused(paused: Boolean) {
        val since = pausedSince
        if (paused && since == null) {
            pausedSince = clock.nowSeconds()
        } else if (!paused && since != null) {
            phaseSince += clock.nowSeconds() - since    // die Pause zaehlt nicht mit
            pausedSince = null
        }
    }

    /** Stop: keine Ansage mehr, auch keine wartende. */
    fun stop() {
        phase = Phase.FINISHED
        queue.clear()
        speaker.stop()
        _state.value = snapshot(null)
    }

    private fun next() {
        val nextExercise = queue.removeFirstOrNull()
        if (nextExercise == null) {
            phase = Phase.FINISHED
            _state.value = snapshot(null)
            return
        }
        exercise = nextExercise
        index++
        demoPlays = 0
        speaker.stop()          // was von der vorigen Uebung noch laeuft oder wartet, verstummt
        milestones.reset()
        setPhase(if (intro) Phase.INTRO else Phase.COUNTING)
        if (intro) speaker.say(Announcement.Intro(exercise.id))
        publish()
    }

    private fun setPhase(newPhase: Phase) {
        phase = newPhase
        phaseSince = clock.nowSeconds()
    }

    private fun publish() {
        _state.value = snapshot(exercise)
    }

    private fun snapshot(exercise: Exercise?): SessionState = SessionState(
        phase = phase,
        exercise = exercise?.id,
        index = index.coerceAtLeast(0),
        count = total,
        barStyle = exercise?.barStyle ?: BarStyle.SPLIT,
        bars = if (exercise == null || phase != Phase.COUNTING) null else exercise.progress(),
        message = exercise?.message ?: ExerciseMessage.Start,
        counter = exercise?.counter ?: emptyMap()
    )

    companion object {
        const val INTRO_SECONDS = 10.0  // Einfuehrung mindestens so lange
        const val DEMO_PLAYS = 1        // so oft laeuft die 3D-Demo ganz durch, bevor gezaehlt wird
        const val DONE_SECONDS = 5.0    // "Prima, gute Leistung!" stehen lassen, dann die naechste Uebung
    }
}
