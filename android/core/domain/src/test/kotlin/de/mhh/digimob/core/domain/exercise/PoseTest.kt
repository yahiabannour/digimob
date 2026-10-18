package de.mhh.digimob.core.domain.exercise

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class PoseTest {

    @Test
    fun `Normierung zieht den Mittelpunkt ab und teilt durch die groesste Ausdehnung`() {
        val pose = Pose(
            listOf(Landmark(0.0, 0.0, 0.0), Landmark(0.4, 0.2, 0.1), Landmark(0.2, 0.1, -0.1)),
            640,
            480
        )
        // Mittelpunkt (0.2, 0.1, 0), groesste Ausdehnung x = 0.4
        assertThat(pose.xy(0).x).isCloseTo(-0.5, 1e-12)
        assertThat(pose.xy(0).y).isCloseTo(-0.25, 1e-12)
        assertThat(pose.norm[1][2]).isCloseTo(0.25, 1e-12)
    }

    @Test
    fun `alle Punkte aufeinander teilt durch 1 statt durch 0`() {
        val pose = Pose(List(33) { Landmark(0.5, 0.5, 0.0) }, 640, 480)
        assertThat(pose.xy(11)).isEqualTo(Point(0.0, 0.0))
    }
}
