package de.mhh.digimob.core.domain.exercise

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

/** Der gemeinsame Ablauf (Exercise), am Beispiel Abduktion. */
class ExerciseTest {

    private val down = arms(20.0)
    private val up = arms(160.0)
    private val oneRep = down * 6 + up * 6

    @Test
    fun `ohne Pose ungueltig und leerer Balken, die Zahl bleibt`() {
        val exercise = Abduktion(target = null, preset = Preset.VOLL)
        exercise.update(null, 0.0)
        assertThat(exercise.valid).isFalse()
        assertThat(exercise.progress().values.toList())
            .containsOnly(Bar(fill = null, back = false, count = 0, countOnly = true))
    }

    @Test
    fun `Fehler beim Messen macht den Frame ungueltig statt abzustuerzen`() {
        // Huefte, Schulter und Ellbogen aufeinander: kein Winkel
        val broken = Pose(List(33) { Landmark(0.5, 0.5, 0.0, 1.0, 1.0) }, 640, 480)
        val exercise = Abduktion(target = 5, preset = Preset.VOLL)
        exercise.update(broken, 0.0)
        assertThat(exercise.valid).isFalse()
        assertThat(exercise.message).isEqualTo(ExerciseMessage.Start)
    }

    @Test
    fun `verbleibende Wiederholungen nie unter 0, die vordere Seite zaehlt weiter`() {
        val leftOnly = arms(20.0, 20.0) * 6 + arms(160.0, 20.0) * 6
        val exercise = Abduktion(target = 2, preset = Preset.VOLL).run(leftOnly + leftOnly + leftOnly + leftOnly + down * 6)
        assertThat(exercise.counter).isEqualTo(mapOf(Side.LEFT to 4, Side.RIGHT to 0))
        assertThat(exercise.progress().mapValues { it.value.count })
            .isEqualTo(mapOf(Side.LEFT to 0, Side.RIGHT to 2))
    }

    @Test
    fun `Ziel erreicht beendet die Uebung, danach wird nicht weiter gezaehlt`() {
        val exercise = Abduktion(target = 2, preset = Preset.VOLL).run(oneRep + oneRep + oneRep + down * 6)
        assertThat(exercise.done).isTrue()
        assertThat(exercise.total).isEqualTo(2)
        assertThat(exercise.message).isEqualTo(ExerciseMessage.Done)
    }

    @Test
    fun `nur zaehlen (target null) ist nie fertig`() {
        val exercise = Abduktion(target = null, preset = Preset.VOLL).run((oneRep) + oneRep + down * 6)
        assertThat(exercise.done).isFalse()
        assertThat(exercise.total).isEqualTo(2)
        assertThat(exercise.message).isEqualTo(ExerciseMessage.CountOnly)
        assertThat(exercise.progress().getValue(Side.LEFT).count).isEqualTo(2)
    }

    @Test
    fun `Meldung mit Ziel lobt nur, wo die Uebung es sagt`() {
        val abduktion = Abduktion(target = 5, preset = Preset.VOLL).run(down * 1)
        assertThat(abduktion.message).isEqualTo(ExerciseMessage.Target(5, praise = true))
        val rotation = Rotation(target = 5, preset = Preset.VOLL).run(down * 1)
        assertThat(rotation.message).isEqualTo(ExerciseMessage.Target(5, praise = false))
    }

    @Test
    fun `Ziel 0 ist sofort fertig`() {
        assertThat(Abduktion(target = 0, preset = Preset.VOLL).done).isTrue()
    }
}
