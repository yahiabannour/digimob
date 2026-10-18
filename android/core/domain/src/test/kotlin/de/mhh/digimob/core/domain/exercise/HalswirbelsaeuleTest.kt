package de.mhh.digimob.core.domain.exercise

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test

class HalswirbelsaeuleTest {

    private fun rotation(degrees: Double) =
        facePose(degrees).let { Halswirbelsaeule.headRotation(it.landmarks, 640.0, 480.0) }

    @Test
    fun `Blick in die Kamera ist 0 Grad`() {
        assertThat(rotation(0.0)).isCloseTo(0.0, 1e-9)
    }

    @Test
    fun `Nase zur linken Seite des Patienten ist positiv`() {
        assertThat(rotation(40.0)).isCloseTo(40.0, 1e-6)
        assertThat(rotation(-40.0)).isCloseTo(-40.0, 1e-6)
    }

    @Test
    fun `beide Augen auf einem Punkt ist ein Fehler, und der Frame ungueltig`() {
        val pose = facePose(0.0, eyesApart = false)
        assertFailure { Halswirbelsaeule.headRotation(pose.landmarks, 640.0, 480.0) }
            .isInstanceOf<IllegalArgumentException>()
        val exercise = Halswirbelsaeule(null, Preset.VOLL).run(listOf(pose))
        assertThat(exercise.valid).isFalse()
    }

    @Test
    fun `jeder Wechsel zur Gegenseite zaehlt fuer diese Seite`() {
        val frames = facePose(40.0) * 3 + facePose(0.0) * 3 + facePose(-40.0) * 3 + facePose(40.0) * 3
        val exercise = Halswirbelsaeule(null, Preset.VOLL).run(frames)
        assertThat(exercise.counter).isEqualTo(mapOf(Side.LEFT to 2, Side.RIGHT to 1))
    }

    @Test
    fun `zweimal zur selben Seite zaehlt einmal`() {
        val frames = facePose(40.0) * 3 + facePose(0.0) * 3 + facePose(40.0) * 3
        assertThat(Halswirbelsaeule(null, Preset.VOLL).run(frames).counter)
            .isEqualTo(mapOf(Side.LEFT to 1, Side.RIGHT to 0))
    }

    @Test
    fun `Einfach zaehlt schon ab 28 Grad`() {
        val frames = facePose(30.0) * 3
        assertThat(Halswirbelsaeule(null, Preset.VOLL).run(frames).total).isEqualTo(0)
        assertThat(Halswirbelsaeule(null, Preset.EINFACH).run(frames).counter.getValue(Side.LEFT)).isEqualTo(1)
    }

    @Test
    fun `Balken waechst nur zur Drehrichtung`() {
        val bars = Halswirbelsaeule(5, Preset.VOLL).run(facePose(25.0) * 1).progress()
        assertThat(bars.getValue(Side.LEFT).fill!!).isCloseTo(0.5, 1e-6)    // 15..35 Grad
        assertThat(bars.getValue(Side.RIGHT).fill).isEqualTo(0.0)
    }
}
