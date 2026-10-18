package de.mhh.digimob.feature.training.domain

import de.mhh.digimob.core.domain.exercise.Landmark
import de.mhh.digimob.core.domain.exercise.Pose
import kotlin.math.cos
import kotlin.math.sin

class FakeSpeaker : Speaker {
    val said = mutableListOf<Announcement>()
    var stops = 0

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

/** Pose mit beiden Armen im Winkel [arm] (Huefte-Schulter-Ellbogen), wie in core:domain. */
fun arms(arm: Double): Pose {
    val points = MutableList(33) { 0.5 to 0.5 }
    listOf(11 to 23, 12 to 24).forEachIndexed { k, (shoulderIndex, hipIndex) ->
        val sx = if (k == 0) 0.4 else 0.6
        val sign = if (k == 0) -1 else 1
        val a = Math.toRadians(arm)
        points[hipIndex] = sx to 0.8
        points[shoulderIndex] = sx to 0.4
        points[shoulderIndex + 2] = (sx + 0.2 * sign * sin(a)) to (0.4 + 0.2 * cos(a))
        points[shoulderIndex + 4] = (sx + 0.4 * sign * sin(a)) to (0.4 + 0.4 * cos(a))
    }
    return Pose(points.map { (x, y) -> Landmark(x, y, 0.0, 1.0, 1.0) }, 640, 480)
}

val DOWN = arms(20.0)
val UP = arms(160.0)
