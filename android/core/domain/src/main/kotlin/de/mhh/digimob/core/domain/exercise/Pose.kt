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

/**
 * Landmark-Nummern je Seite, aus Sicht des Patienten (das Kamerabild ist ungespiegelt). Die Huefte
 * (23/24) nutzt keine Uebung: sie liegt am Tablet oft unter dem Bildrand, siehe [Pose.hip].
 */
object Landmarks {
    const val NASE = 0
    val SCHULTER = mapOf(Side.LEFT to 11, Side.RIGHT to 12)
    val ELLBOGEN = mapOf(Side.LEFT to 13, Side.RIGHT to 14)
    val HANDGELENK = mapOf(Side.LEFT to 15, Side.RIGHT to 16)

    /** Nase und Augen (0-6), daraus rechnet Halswirbelsaeule.headRotation(). */
    val GESICHT = (0..6).toSet()
    val SCHULTERN_ELLBOGEN = (SCHULTER.values + ELLBOGEN.values).toSet()
    val ARME = SCHULTERN_ELLBOGEN + HANDGELENK.values
}

// Die virtuelle Huefte, in Schulterbreiten: so weit unter und innen neben der Schulter lag die echte
// Huefte in den 76 Aufnahmen (Median 1.5-2.1 unten, 0.13-0.16 innen). Fuer den Winkel zaehlt nur die
// Richtung, also das Verhaeltnis der beiden. Senkrecht darunter (innen 0) waere jeder Schulterwinkel
// 4-5 Grad kleiner, abduktion/rec1 (oben 148 Grad, Ziel 150) zaehlte links 1 statt 3
const val HIP_DOWN = 1.5
const val HIP_INWARD = 0.14

/**
 * Die Landmarks eines Bildes: roh (Pixelrechnung, z.B. Halswirbelsaeule) und normiert (Winkel).
 * width/height: Groesse des Bildes, in dem erkannt wurde.
 */
class Pose(val landmarks: List<Landmark>, val width: Int, val height: Int) {
    val norm: List<DoubleArray> = normalizePoseLandmarks(landmarks)

    /** Normierter 2D-Punkt, wie ExerciseMath.angle() ihn erwartet. */
    fun xy(index: Int): Point = norm[index].let { Point(it[0], it[1]) }

    /**
     * Virtuelle Huefte einer Seite (normiert) aus beiden Schultern (pose.py huefte): [HIP_DOWN]
     * Schulterbreiten darunter, [HIP_INWARD] zur Koerpermitte. Ersetzt die erkannte Huefte, die beim
     * Sitzen nah vor dem Tablet ausserhalb des Bildes liegt (Sichtbarkeit 0). Liegen beide Schultern
     * aufeinander, ist sie die Schulter selbst: der Winkel ist nicht bestimmbar, der Frame ungueltig.
     */
    fun hip(side: Side): Point {
        val shoulder = xy(Landmarks.SCHULTER.getValue(side))
        val other = xy(Landmarks.SCHULTER.getValue(if (side == Side.LEFT) Side.RIGHT else Side.LEFT))
        val width = ExerciseMath.distance(shoulder, other)
        return Point(shoulder.x + HIP_INWARD * (other.x - shoulder.x), shoulder.y + HIP_DOWN * width)
    }
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
