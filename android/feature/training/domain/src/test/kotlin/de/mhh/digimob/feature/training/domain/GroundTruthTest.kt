package de.mhh.digimob.feature.training.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import de.mhh.digimob.core.domain.exercise.Side
import org.junit.jupiter.api.Test

class GroundTruthTest {

    @Test
    fun `eine Seite (Butterfly, Rotation) - nur die Zahl`() {
        assertThat(GroundTruth.of(5, null)).isEqualTo(GroundTruth(5, null))
    }

    @Test
    fun `beide Seiten gleich - ohne Angabe pro Seite, wie die Eingabe 5`() {
        assertThat(GroundTruth.of(5, 5)).isEqualTo(GroundTruth(5, null))
    }

    @Test
    fun `Seiten verschieden - reps ist die kleinere, wie Exercise total`() {
        assertThat(GroundTruth.of(5, 0)).isEqualTo(GroundTruth(0, mapOf(Side.LEFT to 5, Side.RIGHT to 0)))
        assertThat(GroundTruth.of(2, 4)).isEqualTo(GroundTruth(2, mapOf(Side.LEFT to 2, Side.RIGHT to 4)))
    }

    @Test
    fun `null Wiederholungen ist eine gueltige Ground Truth`() {
        // Eine Aufnahme, in der die App nichts zaehlen darf (z.B. Bewegung zu klein)
        assertThat(GroundTruth.of(0, 0)).isEqualTo(GroundTruth(0, null))
    }
}
