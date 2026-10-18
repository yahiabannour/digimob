package de.mhh.digimob.core.domain.exercise

/**
 * Ein Pose-Landmark, wie MediaPipe es liefert: x, y relativ zur Bildgroesse (ausserhalb des Bildes
 * < 0 bzw. > 1), z etwa im Massstab von x. visibility/presence 0..1, null ohne Angabe.
 */
data class Landmark(
    val x: Double,
    val y: Double,
    val z: Double,
    val visibility: Double? = null,
    val presence: Double? = null
)

data class Point(val x: Double, val y: Double)

/** Landmark-Nummern je Seite, aus Sicht des Patienten (das Kamerabild ist ungespiegelt). */
object Landmarks {
    const val NASE = 0
    val SCHULTER = mapOf(Side.LEFT to 11, Side.RIGHT to 12)
    val ELLBOGEN = mapOf(Side.LEFT to 13, Side.RIGHT to 14)
    val HANDGELENK = mapOf(Side.LEFT to 15, Side.RIGHT to 16)
    val HUEFTE = mapOf(Side.LEFT to 23, Side.RIGHT to 24)
}

/**
 * Die Landmarks eines Bildes: roh (Pixelrechnung, z.B. Halswirbelsaeule) und normiert (Winkel).
 * width/height: Groesse des Bildes, in dem erkannt wurde.
 */
class Pose(val landmarks: List<Landmark>, val width: Int, val height: Int) {
    val norm: List<DoubleArray> = normalizePoseLandmarks(landmarks)

    /** Normierter 2D-Punkt, wie ExerciseMath.angle() ihn erwartet. */
    fun xy(index: Int): Point = norm[index].let { Point(it[0], it[1]) }
}

/**
 * Mittelpunkt aller Landmarks abziehen und durch die groesste Ausdehnung entlang einer Achse
 * teilen. Die Winkelgrenzen aller Uebungen sind auf genau diese Normierung eingestellt
 * (pose.py, in derselben Reihenfolge gerechnet).
 */
fun normalizePoseLandmarks(landmarks: List<Landmark>): List<DoubleArray> {
    val n = landmarks.size
    var sumX = 0.0
    var sumY = 0.0
    var sumZ = 0.0
    for (lm in landmarks) {
        sumX += lm.x
        sumY += lm.y
        sumZ += lm.z
    }
    val centerX = sumX / n
    val centerY = sumY / n
    val centerZ = sumZ / n

    // Skala = groesste Ausdehnung entlang einer Achse
    val scale = maxOf(
        landmarks.maxOf { it.x } - landmarks.minOf { it.x },
        landmarks.maxOf { it.y } - landmarks.minOf { it.y },
        landmarks.maxOf { it.z } - landmarks.minOf { it.z }
    ).let { if (it > 0) it else 1.0 }

    return landmarks.map {
        doubleArrayOf((it.x - centerX) / scale, (it.y - centerY) / scale, (it.z - centerZ) / scale)
    }
}
