package de.mhh.digimob.core.domain.exercise

import kotlin.math.acos
import kotlin.math.sqrt

/** calculate_angle_distance.py: Winkel, Abstand und Fortschritt, der Kern jedes Zaehlers. */
object ExerciseMath {

    /** Winkel in Grad am Gelenk b zwischen a-b und c-b. */
    fun angle(a: Point, b: Point, c: Point): Double {
        val baX = a.x - b.x
        val baY = a.y - b.y
        val bcX = c.x - b.x
        val bcY = c.y - b.y
        val dot = baX * bcX + baY * bcY
        val magnitudeBa = sqrt(baX * baX + baY * baY)
        val magnitudeBc = sqrt(bcX * bcX + bcY * bcY)
        // Liegen zwei Punkte aufeinander, gibt es keinen Winkel (sonst NaN bis in den Balken).
        // Exercise.update() macht aus der Ausnahme einen ungueltigen Frame.
        require(magnitudeBa != 0.0 && magnitudeBc != 0.0) {
            "Winkel nicht bestimmbar: zwei Punkte liegen aufeinander"
        }
        val cosAngle = (dot / (magnitudeBa * magnitudeBc)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosAngle))
    }

    fun distance(a: Point, b: Point): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * 0 bei [start], 1 bei [target], begrenzt auf 0..[upper] (upper > 1: ueber das Ziel hinaus, fuer
     * den Balken mit der Ziellinie). [target] darf kleiner als [start] sein.
     */
    fun progress(value: Double, start: Double, target: Double, upper: Double = 1.0): Double {
        if (target == start || !value.isFinite()) return 0.0    // kein Bereich oder kein Messwert
        return ((value - start) / (target - start)).coerceIn(0.0, upper)
    }
}
