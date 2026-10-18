package de.mhh.digimob.feature.training.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import de.mhh.digimob.core.domain.exercise.ExerciseId
import de.mhh.digimob.core.domain.exercise.Preset
import de.mhh.digimob.core.domain.exercise.Side
import org.junit.jupiter.api.Test

class RecordingTest {

    private fun recording(perSide: Map<Side, Int>?, expected: Int? = 3) =
        Recording("abduktion/rec1", "/x/video.mp4", ExerciseId.ABDUKTION, expected, perSide, Preset.VOLL)

    @Test
    fun `ohne Angabe pro Seite gilt die Zahl fuer jede Seite`() {
        assertThat(recording(null).expectedFor(listOf(Side.LEFT, Side.RIGHT)))
            .isEqualTo(mapOf(Side.LEFT to 3, Side.RIGHT to 3))
        assertThat(recording(null).expectedFor(listOf(Side.BOTH))).isEqualTo(mapOf(Side.BOTH to 3))
    }

    @Test
    fun `mit Angabe pro Seite genau die`() {
        val perSide = mapOf(Side.LEFT to 3, Side.RIGHT to 5)
        assertThat(recording(perSide).expectedFor(listOf(Side.LEFT, Side.RIGHT))).isEqualTo(perSide)
    }

    @Test
    fun `ohne Ground Truth nichts zu vergleichen`() {
        assertThat(recording(null, expected = null).expectedFor(listOf(Side.LEFT, Side.RIGHT))).isNull()
    }
}
