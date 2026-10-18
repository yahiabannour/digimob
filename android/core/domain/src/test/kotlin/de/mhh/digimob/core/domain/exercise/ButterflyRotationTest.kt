package de.mhh.digimob.core.domain.exercise

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class ButterflyRotationTest {

    /** Oberarme seitlich gehoben (90 Grad), Unterarme senkrecht nach oben, Handgelenkabstand [wrists]. */
    private fun butterflyPose(wrists: Double, armUp: Boolean = true): Pose {
        val points = MutableList(33) { Point(0.5, 0.6) }
        val shoulderY = 0.4
        val handY = if (armUp) 0.2 else 0.55        // Hand ueber bzw. unter dem Ellbogen
        val elbowY = if (armUp) shoulderY else 0.5
        points[11] = Point(0.4, shoulderY); points[12] = Point(0.6, shoulderY)       // Schulterbreite 0.2
        points[23] = Point(0.4, 0.8); points[24] = Point(0.6, 0.8)
        points[13] = Point(0.5 - wrists / 2, elbowY); points[14] = Point(0.5 + wrists / 2, elbowY)
        points[15] = Point(0.5 - wrists / 2, handY); points[16] = Point(0.5 + wrists / 2, handY)
        return Pose(points.map { Landmark(it.x, it.y, 0.0, 1.0, 1.0) }, 640, 480)
    }

    @Test
    fun `Butterfly offen und wieder zusammen zaehlt`() {
        // Schulterbreite 0.2: offen ueber 0.36 (1.8x), zusammen unter 0.2 (1x)
        val exercise = Butterfly(null, Preset.VOLL)
            .run(butterflyPose(0.1) * 3 + butterflyPose(0.4) * 3 + butterflyPose(0.1) * 3)
        assertThat(exercise.counter).isEqualTo(mapOf(Side.BOTH to 1))
    }

    @Test
    fun `Butterfly zusammen ohne vorher offen zaehlt nicht`() {
        val exercise = Butterfly(null, Preset.VOLL).run(butterflyPose(0.1) * 3 + butterflyPose(0.3) * 3 + butterflyPose(0.1) * 3)
        assertThat(exercise.counter).isEqualTo(mapOf(Side.BOTH to 0))
    }

    @Test
    fun `Butterfly ausserhalb der Haltung zaehlt nicht und zeigt keinen Balken, bleibt aber gueltig`() {
        val exercise = Butterfly(null, Preset.VOLL)
            .run(butterflyPose(0.1, armUp = false) * 3 + butterflyPose(0.4, armUp = false) * 3 + butterflyPose(0.1, armUp = false) * 3)
        assertThat(exercise.counter).isEqualTo(mapOf(Side.BOTH to 0))
        assertThat(exercise.valid).isTrue()     // Meldung und Ansagen laufen weiter
        assertThat(exercise.progress().getValue(Side.BOTH).fill).isNull()
    }

    @Test
    fun `Butterfly Einfach zaehlt weniger weit geoeffnet`() {
        val frames = butterflyPose(0.1) * 3 + butterflyPose(0.33) * 3 + butterflyPose(0.1) * 3     // 1.65x
        assertThat(Butterfly(null, Preset.VOLL).run(frames).total).isEqualTo(0)
        assertThat(Butterfly(null, Preset.EINFACH).run(frames).total).isEqualTo(1)
    }

    /** Oberarme am Koerper, Ellbogen auf Abstand 0.2, Handgelenke auf Abstand [wrists] (Unterarme nach aussen). */
    private fun rotationPose(wrists: Double, shoulderAngle: Double = 10.0): Pose {
        val points = MutableList(33) { Point(0.5, 0.6) }
        points[11] = Point(0.4, 0.4); points[12] = Point(0.6, 0.4)
        points[23] = Point(0.4, 0.8); points[24] = Point(0.6, 0.8)
        val a = Math.toRadians(shoulderAngle)
        points[13] = Point(0.4 - 0.2 * kotlin.math.sin(a), 0.4 + 0.2 * kotlin.math.cos(a))
        points[14] = Point(0.6 + 0.2 * kotlin.math.sin(a), 0.4 + 0.2 * kotlin.math.cos(a))
        val elbowsDistance = points[14].x - points[13].x
        points[15] = Point(0.5 - wrists * elbowsDistance / 2, 0.65)
        points[16] = Point(0.5 + wrists * elbowsDistance / 2, 0.65)
        return Pose(points.map { Landmark(it.x, it.y, 0.0, 1.0, 1.0) }, 640, 480)
    }

    @Test
    fun `Rotation aussen und wieder vorne zaehlt`() {
        val exercise = Rotation(null, Preset.VOLL).run(rotationPose(1.0) * 3 + rotationPose(1.6) * 3 + rotationPose(1.0) * 3)
        assertThat(exercise.counter).isEqualTo(mapOf(Side.BOTH to 1))
    }

    @Test
    fun `Rotation mit gehobenen Oberarmen zaehlt nicht (Gate)`() {
        val exercise = Rotation(null, Preset.VOLL)
            .run(rotationPose(1.0, 80.0) * 3 + rotationPose(1.6, 80.0) * 3 + rotationPose(1.0, 80.0) * 3)
        assertThat(exercise.counter).isEqualTo(mapOf(Side.BOTH to 0))
    }
}
