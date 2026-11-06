package de.mhh.digimob.feature.training.domain

import de.mhh.digimob.core.domain.exercise.Landmark
import de.mhh.digimob.core.domain.exercise.Pose
import kotlin.math.cos
import kotlin.math.sin

class FakeSpeaker : Speaker {
    val said = mutableListOf<Announcement>()
    var stops = 0
    override var isSpeaking = false

    override fun say(announcement: Announcement) {
        said += announcement
    }

    override fun stop() {
        stops++
    }
}

class FakeClock(var now: Double = 0.0) : Clock {
    override fun nowSeconds(): Double = now
}

/** Pose mit beiden Armen [arm] Grad aus der Senkrechten (zur virtuellen Huefte rund 5 Grad mehr), ohne Huefte. */
fun arms(arm: Double): Pose {
    val points = MutableList(33) { 0.5 to 0.5 }
    listOf(11, 12).forEachIndexed { k, shoulderIndex ->
        val sx = if (k == 0) 0.4 else 0.6
        val sign = if (k == 0) -1 else 1
        val a = Math.toRadians(arm)
        points[shoulderIndex] = sx to 0.4
        points[shoulderIndex + 2] = (sx + 0.2 * sign * sin(a)) to (0.4 + 0.2 * cos(a))
        points[shoulderIndex + 4] = (sx + 0.4 * sign * sin(a)) to (0.4 + 0.4 * cos(a))
    }
    return Pose(points.map { (x, y) -> Landmark(x, y, 0.0, 1.0, 1.0) }, 640, 480)
}

val DOWN = arms(20.0)
val UP = arms(160.0)

/** Nase [noseAbove] Schulterbreiten ueber der Schulterlinie (Schultern 0.2 breit, 640x480). */
fun head(noseAbove: Double, visibility: Double = 1.0): Pose {
    val points = MutableList(33) { Landmark(0.5, 0.5, 0.0, 1.0, 1.0) }
    points[11] = Landmark(0.4, 0.6, 0.0, visibility, 1.0)
    points[12] = Landmark(0.6, 0.6, 0.0, visibility, 1.0)
    points[0] = Landmark(0.5, 0.6 - noseAbove * 0.2 * 640 / 480, 0.0, 1.0, 1.0)
    return Pose(points, 640, 480)
}
