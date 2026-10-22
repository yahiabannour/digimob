package de.mhh.digimob.core.domain.exercise

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
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
    fun `virtuelle Huefte liegt unter und innen neben der Schulter`() {
        val pose = armPose()
        val left = pose.xy(Landmarks.SCHULTER.getValue(Side.LEFT))
        val right = pose.xy(Landmarks.SCHULTER.getValue(Side.RIGHT))
        val width = ExerciseMath.distance(left, right)
        assertThat(pose.hip(Side.LEFT).y - left.y).isCloseTo(HIP_DOWN * width, 1e-12)
        assertThat(pose.hip(Side.LEFT).x - left.x).isCloseTo(HIP_INWARD * (right.x - left.x), 1e-12)    // zur Mitte
        assertThat(pose.hip(Side.RIGHT).x - right.x).isCloseTo(HIP_INWARD * (left.x - right.x), 1e-12)
    }

    @Test
    fun `Schultern aufeinander machen den Frame ungueltig`() {
        // Ohne Schulterbreite liegt die virtuelle Huefte auf der Schulter: kein Winkel
        val pose = Pose(List(33) { Landmark(0.5, 0.5, 0.0, 1.0, 1.0) }, 640, 480)
        assertThat(pose.hip(Side.LEFT)).isEqualTo(pose.xy(Landmarks.SCHULTER.getValue(Side.LEFT)))
        val exercise = Abduktion(target = null)
        exercise.update(pose, 0.0)
        assertThat(exercise.valid).isFalse()
    }

    @Test
    fun `alle Punkte aufeinander teilt durch 1 statt durch 0`() {
        val pose = Pose(List(33) { Landmark(0.5, 0.5, 0.0) }, 640, 480)
        assertThat(pose.xy(11)).isEqualTo(Point(0.0, 0.0))
    }
}
