package de.mhh.digimob.feature.training.domain

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import de.mhh.digimob.core.domain.exercise.Abduktion
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.ExerciseMessage
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.domain.exercise.Rotation
import de.mhh.digimob.core.domain.exercise.Side
import org.junit.jupiter.api.Test

class TrainingSessionTest {

    private val clock = FakeClock()
    private val speaker = FakeSpeaker()

    private fun session(target: Int? = 2, intro: Boolean = true, vararg ids: ExerciseId = arrayOf(ExerciseId.ABDUKTION)) =
        TrainingSession(
            exercises = ids.map {
                if (it == ExerciseId.ROTATION) Rotation(target, Preset.VOLL) else Abduktion(target, Preset.VOLL)
            },
            speaker = speaker,
            clock = clock,
            intro = intro
        ).apply { start() }

    /** Eine Wiederholung beider Arme, Bilder alle 1/30 s ab der aktuellen Uhrzeit. */
    private fun TrainingSession.rep() {
        (List(6) { DOWN } + List(6) { UP } + List(6) { DOWN }).forEach {
            clock.now += 1 / 30.0
            onFrame(it, clock.now)
        }
    }

    /** Wie rep(), aber nur bis zu dem Bild, mit dem die Uebung fertig ist. */
    private fun TrainingSession.repUntilDone() {
        for (pose in List(6) { DOWN } + List(6) { UP } + List(6) { DOWN }) {
            clock.now += 1 / 30.0
            onFrame(pose, clock.now)
            if (state.value.phase == Phase.DONE) return
        }
    }

    private fun TrainingSession.at(seconds: Double) {
        clock.now = seconds
        tick()
    }

    @Test
    fun `Einfuehrung braucht die ganze Demo und 10 s`() {
        val session = session()
        assertThat(session.state.value.phase).isEqualTo(Phase.INTRO)
        session.at(12.0)
        assertThat(session.state.value.phase).isEqualTo(Phase.INTRO)        // Demo noch nicht durch
        clock.now = 3.0
        session.onDemoPlayed()
        session.at(9.99)
        assertThat(session.state.value.phase).isEqualTo(Phase.INTRO)        // Demo durch, aber < 10 s
        session.at(10.0)
        assertThat(session.state.value.phase).isEqualTo(Phase.COUNTING)
    }

    @Test
    fun `waehrend der Einfuehrung wird nicht gezaehlt`() {
        val session = session()
        session.rep()
        clock.now = 0.0
        session.onDemoPlayed()
        session.at(10.0)
        assertThat(session.current!!.total).isEqualTo(0)
        assertThat(session.state.value.bars?.getValue(Side.LEFT)?.fill).isNull()
    }

    @Test
    fun `Einfuehrung sagt die Uebung an, Zaehlen ohne Einfuehrung nicht`() {
        session()
        assertThat(speaker.said).containsExactly(Announcement.Intro(ExerciseId.ABDUKTION))
        speaker.said.clear()
        session(intro = false)
        assertThat(speaker.said).doesNotContain(Announcement.Intro(ExerciseId.ABDUKTION))
    }

    @Test
    fun `Ansagen nur mit gueltigem Messwert`() {
        val session = session(intro = false)
        session.onFrame(null, 0.1)
        assertThat(speaker.said).doesNotContain(Announcement.Start(2))
        session.onFrame(DOWN, 0.2)
        assertThat(speaker.said).contains(Announcement.Start(2))
    }

    @Test
    fun `ohne Ziel (Aufnahmen testen) keine Ansagen und nie fertig`() {
        val session = session(target = null, intro = false)
        repeat(3) { session.rep() }
        assertThat(speaker.said).containsExactly()
        assertThat(session.state.value.phase).isEqualTo(Phase.COUNTING)
        assertThat(session.state.value.counter).isEqualTo(mapOf(Side.LEFT to 3, Side.RIGHT to 3))
    }

    @Test
    fun `fertig zeigt Prima mit leeren Balken, nach 5 s die naechste Uebung`() {
        val session = session(intro = false, ids = arrayOf(ExerciseId.ABDUKTION, ExerciseId.ROTATION))
        session.rep()
        session.repUntilDone()
        val done = session.state.value
        assertThat(done.phase).isEqualTo(Phase.DONE)
        assertThat(done.bars).isNull()
        assertThat(done.message).isEqualTo(ExerciseMessage.Done)
        val doneAt = clock.now
        session.at(doneAt + 4.9)
        assertThat(session.state.value.exercise).isEqualTo(ExerciseId.ABDUKTION)
        session.at(doneAt + 5.0)
        assertThat(session.state.value.exercise).isEqualTo(ExerciseId.ROTATION)
        assertThat(session.state.value.index).isEqualTo(1)
    }

    @Test
    fun `naechste Uebung beginnt still und mit neuen Ansagen`() {
        val session = session(intro = false, ids = arrayOf(ExerciseId.ABDUKTION, ExerciseId.ABDUKTION))
        session.rep()
        session.rep()
        val stopsBefore = speaker.stops
        session.at(clock.now + 5.0)
        assertThat(speaker.stops).isEqualTo(stopsBefore + 1)     // was noch spricht, verstummt
        speaker.said.clear()
        session.onFrame(DOWN, clock.now)
        assertThat(speaker.said).containsExactly(Announcement.Start(2))
    }

    @Test
    fun `nach der letzten Uebung fertig`() {
        val session = session(intro = false)
        session.rep()
        session.rep()
        session.at(clock.now + 5.0)
        assertThat(session.state.value.phase).isEqualTo(Phase.FINISHED)
        assertThat(session.current).isNull()
    }

    @Test
    fun `Pause haelt Bilder an und ihre Zeit zaehlt nicht zur Einfuehrung`() {
        val session = session()
        session.onDemoPlayed()
        session.at(5.0)
        session.setPaused(true)
        session.at(20.0)
        assertThat(session.state.value.phase).isEqualTo(Phase.INTRO)
        session.setPaused(false)
        session.at(24.9)                                                    // 5 + 4.9 s ohne Pause
        assertThat(session.state.value.phase).isEqualTo(Phase.INTRO)
        session.at(25.0)
        assertThat(session.state.value.phase).isEqualTo(Phase.COUNTING)
    }

    @Test
    fun `waehrend der Pause wird nicht gezaehlt`() {
        val session = session(intro = false)
        session.setPaused(true)
        session.rep()
        session.setPaused(false)
        assertThat(session.current!!.total).isEqualTo(0)
    }

    @Test
    fun `zweimal Pause ist eine Pause`() {
        val session = session()
        session.onDemoPlayed()
        session.setPaused(true)
        clock.now = 50.0
        session.setPaused(true)
        session.setPaused(false)
        session.at(59.9)
        assertThat(session.state.value.phase).isEqualTo(Phase.INTRO)
        session.at(60.0)
        assertThat(session.state.value.phase).isEqualTo(Phase.COUNTING)
    }

    @Test
    fun `Stop bricht die Ansagen ab und ignoriert weitere Bilder`() {
        val session = session(intro = false)
        val stopsBefore = speaker.stops
        session.stop()
        assertThat(speaker.stops).isEqualTo(stopsBefore + 1)
        session.rep()
        assertThat(session.state.value.phase).isEqualTo(Phase.FINISHED)
        assertThat(session.current).isNull()
    }

    @Test
    fun `beim Zaehlen hat jede Seite ihren Balken`() {
        val session = session(intro = false)
        session.onFrame(DOWN, 0.1)
        assertThat(session.state.value.bars).isNotNull()
        assertThat(session.state.value.bars!!.keys.toList()).containsExactly(Side.LEFT, Side.RIGHT)
    }
}
