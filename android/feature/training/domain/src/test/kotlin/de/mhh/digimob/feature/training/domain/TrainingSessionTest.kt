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

    /** skipIntro: Demo und 10 s schon vorbei, die Einfuehrungs-Ansage vergessen; es wird gezaehlt. */
    private fun session(skipIntro: Boolean = false, vararg ids: ExerciseId = arrayOf(ExerciseId.ABDUKTION)) =
        TrainingSession(
            exercises = ids.map {
                if (it == ExerciseId.ROTATION) Rotation(2, Preset.VOLL) else Abduktion(2, Preset.VOLL)
            },
            speaker = speaker,
            clock = clock
        ).apply {
            start()
            if (skipIntro) {
                onDemoPlayed()
                at(clock.now + TrainingSession.INTRO_SECONDS)
                speaker.said.clear()
            }
        }

    /** Eine Wiederholung beider Arme, Bilder alle 1/32 s (binaer exakt: "nach 5 s" ohne Rundung) ab der aktuellen Uhrzeit. */
    private fun TrainingSession.rep() {
        (List(6) { DOWN } + List(6) { UP } + List(6) { DOWN }).forEach {
            clock.now += 1 / 32.0
            onFrame(it, clock.now)
        }
    }

    /** Wie rep(), aber nur bis zu dem Bild, mit dem die Uebung fertig ist. */
    private fun TrainingSession.repUntilDone() {
        for (pose in List(6) { DOWN } + List(6) { UP } + List(6) { DOWN }) {
            clock.now += 1 / 32.0
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
    fun `Einfuehrung sagt die Uebung an`() {
        session()
        assertThat(speaker.said).containsExactly(Announcement.Intro(ExerciseId.ABDUKTION))
    }

    @Test
    fun `Ansagen nur mit gueltigem Messwert`() {
        val session = session(skipIntro = true)
        session.onFrame(null, 0.1)
        assertThat(speaker.said).doesNotContain(Announcement.Start(2))
        session.onFrame(DOWN, 0.2)
        assertThat(speaker.said).contains(Announcement.Start(2))
    }

    @Test
    fun `fertig zeigt Prima mit leeren Balken, nach 5 s die naechste Uebung`() {
        val session = session(skipIntro = true, ids = arrayOf(ExerciseId.ABDUKTION, ExerciseId.ROTATION))
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
        val session = session(skipIntro = true, ids = arrayOf(ExerciseId.ABDUKTION, ExerciseId.ABDUKTION))
        session.rep()
        session.rep()
        val stopsBefore = speaker.stops
        session.at(clock.now + 5.0)
        assertThat(speaker.stops).isEqualTo(stopsBefore + 1)     // was noch spricht, verstummt
        assertThat(speaker.said.last()).isEqualTo(Announcement.Intro(ExerciseId.ABDUKTION))
        speaker.said.clear()
        session.onDemoPlayed()
        session.at(clock.now + TrainingSession.INTRO_SECONDS)
        session.onFrame(DOWN, clock.now)
        assertThat(speaker.said).containsExactly(Announcement.Start(2))
    }

    @Test
    fun `nach der letzten Uebung fertig`() {
        val session = session(skipIntro = true)
        session.rep()
        session.rep()
        session.at(clock.now + 5.0)
        assertThat(session.state.value.phase).isEqualTo(Phase.FINISHED)
        assertThat(session.current).isNull()
    }

    @Test
    fun `Doppeltipp - Einfuehrung sofort vorbei, Ansage verstummt, es wird gezaehlt`() {
        val session = session()
        val stopsBefore = speaker.stops
        session.skipIntro()                                     // weder Demo noch 10 s
        assertThat(session.state.value.phase).isEqualTo(Phase.COUNTING)
        assertThat(speaker.stops).isEqualTo(stopsBefore + 1)
        session.rep()
        assertThat(session.current!!.total).isEqualTo(1)
    }

    @Test
    fun `Doppeltipp ausserhalb der Einfuehrung aendert nichts, auch nicht bei Prima`() {
        val session = session(true, ExerciseId.ABDUKTION, ExerciseId.ROTATION)
        val stopsBefore = speaker.stops
        session.skipIntro()                                     // beim Zaehlen
        assertThat(session.state.value.phase).isEqualTo(Phase.COUNTING)
        session.rep()
        session.repUntilDone()
        session.skipIntro()                                     // bei "Prima": nicht zur naechsten Uebung
        assertThat(session.state.value.phase).isEqualTo(Phase.DONE)
        assertThat(session.state.value.exercise).isEqualTo(ExerciseId.ABDUKTION)
        assertThat(speaker.stops).isEqualTo(stopsBefore)
    }

    @Test
    fun `Doppeltipp zweimal ist einmal, und wirkt bei der naechsten Einfuehrung wieder`() {
        val session = session(false, ExerciseId.ABDUKTION, ExerciseId.ROTATION)
        session.skipIntro()
        clock.now += 3.0
        session.skipIntro()                                     // zaehlt schon: bleibt so
        assertThat(session.state.value.phase).isEqualTo(Phase.COUNTING)
        session.rep()
        session.repUntilDone()
        session.at(clock.now + TrainingSession.DONE_SECONDS)
        assertThat(session.state.value.exercise).isEqualTo(ExerciseId.ROTATION)
        assertThat(session.state.value.phase).isEqualTo(Phase.INTRO)
        session.skipIntro()
        assertThat(session.state.value.phase).isEqualTo(Phase.COUNTING)
    }

    @Test
    fun `Doppeltipp in der Pause - Einfuehrung vorbei, gezaehlt wird erst nach der Pause`() {
        val session = session()
        session.setPaused(true)
        session.skipIntro()
        assertThat(session.state.value.phase).isEqualTo(Phase.COUNTING)
        session.rep()
        assertThat(session.current!!.total).isEqualTo(0)
        session.setPaused(false)
        session.rep()
        assertThat(session.current!!.total).isEqualTo(1)
    }

    @Test
    fun `Doppeltipp nach dem Ende tut nichts`() {
        val session = session(skipIntro = true)
        session.stop()
        session.skipIntro()
        assertThat(session.state.value.phase).isEqualTo(Phase.FINISHED)
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
        val session = session(skipIntro = true)
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
        val session = session(skipIntro = true)
        val stopsBefore = speaker.stops
        session.stop()
        assertThat(speaker.stops).isEqualTo(stopsBefore + 1)
        session.rep()
        assertThat(session.state.value.phase).isEqualTo(Phase.FINISHED)
        assertThat(session.current).isNull()
    }

    @Test
    fun `beim Zaehlen hat jede Seite ihren Balken`() {
        val session = session(skipIntro = true)
        session.onFrame(DOWN, 0.1)
        assertThat(session.state.value.bars).isNotNull()
        assertThat(session.state.value.bars!!.keys.toList()).containsExactly(Side.LEFT, Side.RIGHT)
    }
}
